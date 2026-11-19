package xuanmo.arcartxsuite.fishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.api.storage.*;
import xuanmo.arcartxsuite.fishing.config.*;
import xuanmo.arcartxsuite.fishing.fatigue.FatigueEngine;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.storage.*;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.task.*;

class FishingShutdownTest {
    @TempDir Path directory;
    private final Logger logger=Logger.getAnonymousLogger();

    @Test
    void catchInboxIsBoundedDrainsBeforeStopAndCallerCancellationDoesNotCancelTheWrite() throws Exception {
        var repository=mock(FishingRepository.class);var service=service(mock(JavaPlugin.class),repository,null,null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var saved=ConcurrentHashMap.<UUID>newKeySet();Thread primary=Thread.currentThread();
        when(repository.saveCatchRecordConfirmed(any())).thenAnswer(call->{assertNotSame(primary,Thread.currentThread());entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));saved.add(call.<FishingCatchRecord>getArgument(0).eventId());return true;});
        var admit=FishingService.class.getDeclaredMethod("admitCrossServerMessage",String.class);admit.setAccessible(true);
        var results=new ArrayList<CompletableFuture<Boolean>>();UUID owner=UUID.randomUUID();
        try {
            results.add(((CompletionStage<Boolean>)admit.invoke(service,catchRecord(owner).encodeCrossServer())).toCompletableFuture());
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            for(int i=0;i<128;i++)results.add(((CompletionStage<Boolean>)admit.invoke(service,catchRecord(owner).encodeCrossServer())).toCompletableFuture());
            assertNull(admit.invoke(service,catchRecord(owner).encodeCrossServer()));
            results.get(0).cancel(true);service.shutdown();assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            assertNull(admit.invoke(service,catchRecord(owner).encodeCrossServer()));
            release.countDown();service.databaseStopped().toCompletableFuture().get(6,TimeUnit.SECONDS);
            for(int i=1;i<results.size();i++)assertTrue(results.get(i).get(1,TimeUnit.SECONDS));
            assertEquals(129,saved.size());
        }finally{release.countDown();service.shutdown();}
    }

    @Test
    void failedCatchCommitIsNotAcknowledgedAndRetryUsesTheSameEventId() throws Exception {
        var repository=spy(repository());var service=service(mock(JavaPlugin.class),repository,null,null);
        var record=catchRecord(UUID.randomUUID());
        doReturn(false).doCallRealMethod().when(repository).saveCatchRecordConfirmed(record);
        var admit=FishingService.class.getDeclaredMethod("admitCrossServerMessage",String.class);admit.setAccessible(true);
        try {
            assertFalse(((CompletionStage<Boolean>)admit.invoke(service,record.encodeCrossServer())).toCompletableFuture().get(2,TimeUnit.SECONDS));
            assertTrue(((CompletionStage<Boolean>)admit.invoke(service,record.encodeCrossServer())).toCompletableFuture().get(2,TimeUnit.SECONDS));
            assertTrue(((CompletionStage<Boolean>)admit.invoke(service,record.encodeCrossServer())).toCompletableFuture().get(2,TimeUnit.SECONDS));
            assertEquals(1,repository.loadCatchRecords(record.playerUuid(),10).size());
        }finally{service.shutdown();service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);}
    }

    @Test
    void queuedWritesSurviveBukkitCancellationAndModuleRetirementWaitsForRealCommit() throws Exception {
        var repository=spy(repository());var scheduler=mock(BukkitScheduler.class);var queued=new ArrayDeque<Runnable>();
        when(scheduler.runTaskAsynchronously(any(Plugin.class),any(Runnable.class))).thenAnswer(call->{queued.add(call.getArgument(1));return mock(BukkitTask.class);});
        var engine=engine(repository);var service=service(mock(JavaPlugin.class),repository,engine,null);
        var module=new FishingModule();var scope=mock(ModuleStorage.class);when(scope.retired()).thenReturn(CompletableFuture.completedFuture(null));
        set(module,"service",service);set(module,"storageScope",scope);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);Thread primary=Thread.currentThread();
        doAnswer(call->{assertNotSame(primary,Thread.currentThread());entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return call.callRealMethod();}).when(repository).saveTaskProgress(any(),eq("test"));
        UUID owner=UUID.randomUUID();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var write=TaskEngine.class.getDeclaredMethod("persistAsync",PlayerTaskInstance.class);write.setAccessible(true);
            for(int i=0;i<32;i++)write.invoke(engine,new PlayerTaskInstance(owner,"task-"+i,FishingTask.TaskCategory.SEASON,100,LocalDate.now()).withProgress(i+1));
            assertEquals(32,queued.size());
            var stop=FishingModule.class.getDeclaredMethod("stopService");stop.setAccessible(true);
            long start=System.nanoTime();stop.invoke(module);assertTrue(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500));
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
            verify(scope).retireAfter(any());
            while(!queued.isEmpty())queued.remove().run();
            release.countDown();module.shutdownCompletion().toCompletableFuture().get(8,TimeUnit.SECONDS);
        }finally{release.countDown();}
        var rows=repository.loadTaskProgress(owner,"test");assertEquals(32,rows.size());
        assertEquals(528,rows.stream().mapToInt(PlayerTaskInstance::currentProgress).sum());
        verify(repository,times(32)).saveTaskProgress(any(),eq("test"));
    }

    @Test
    void rescuingAnOlderPartialWriteCannotUndoCompletionOrRepeatXp() throws Exception {
        var repository=repository();var scheduler=mock(BukkitScheduler.class);
        var engine=engine(repository);UUID owner=UUID.randomUUID();
        var partial=new PlayerTaskInstance(owner,"partial",FishingTask.TaskCategory.SEASON,10,LocalDate.now()).withProgress(5);
        var curve=new XpCurve(List.of(new XpCurve.Breakpoint(1,"100")),new xuanmo.arcartxsuite.api.script.AriaBridge(){
            public boolean available(){return true;}public String version(){return "test";}public Object eval(String code,Map<String,Object> vars){return 100;}
        });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var write=TaskEngine.class.getDeclaredMethod("persistAsync",PlayerTaskInstance.class);write.setAccessible(true);write.invoke(engine,partial);
            assertTrue(repository.completeTask(partial.withProgress(10),"test",37,curve,100).awarded());
            engine.shutdown();engine.databaseStopped().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }
        assertTrue(repository.loadTaskProgress(owner,"test").get(0).completed());
        assertEquals(37,repository.loadSeasonData(owner,"test").currentXp());
        assertFalse(repository.completeTask(partial.withProgress(10),"test",37,curve,100).awarded());
    }

    @Test
    void failedQueuedWriteDoesNotPreventTheOtherAcceptedWriteFromDraining() throws Exception {
        var repository=spy(repository());var engine=engine(repository);UUID owner=UUID.randomUUID();
        var first=new PlayerTaskInstance(owner,"failed",FishingTask.TaskCategory.SEASON,10,LocalDate.now());
        var second=new PlayerTaskInstance(owner,"saved",FishingTask.TaskCategory.SEASON,10,LocalDate.now()).withProgress(4);
        doThrow(new IllegalStateException("isolated failure")).when(repository).saveTaskProgress(eq(first),eq("test"));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            var write=TaskEngine.class.getDeclaredMethod("persistAsync",PlayerTaskInstance.class);write.setAccessible(true);write.invoke(engine,first);write.invoke(engine,second);
            engine.shutdown();engine.databaseStopped().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }
        assertEquals(List.of(second),repository.loadTaskProgress(owner,"test"));
    }

    @Test
    void fatigueReadAlreadyInFlightKeepsRetirementOpenAndCannotWriteAfterStop() throws Exception {
        var repository=mock(FishingRepository.class);var scheduler=mock(BukkitScheduler.class);
        var timer=new AtomicReference<Runnable>();var worker=new AtomicReference<Runnable>();
        when(scheduler.runTaskTimer(any(Plugin.class),any(Runnable.class),anyLong(),anyLong())).thenAnswer(call->{timer.set(call.getArgument(1));return mock(BukkitTask.class);});
        when(scheduler.runTaskAsynchronously(any(Plugin.class),any(Runnable.class))).thenAnswer(call->{worker.set(call.getArgument(1));return mock(BukkitTask.class);});
        UUID owner=UUID.randomUUID();var player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);Thread primary=Thread.currentThread();
        when(repository.loadSeasonData(owner,"test")).thenAnswer(call->{assertNotSame(primary,Thread.currentThread());entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return FishingSeasonData.empty(owner,"test").withFatigueAdded(80,100);});
        var fatigue=new FatigueEngine(mock(JavaPlugin.class),FatigueConfig.load(new YamlConfiguration()),repository,logger,"test",null,null);
        Thread background=null;
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            fatigue.start(id->false);timer.get().run();background=new Thread(worker.get());background.start();assertTrue(entered.await(1,TimeUnit.SECONDS));
            fatigue.shutdown();assertFalse(fatigue.databaseStopped().toCompletableFuture().isDone());
            release.countDown();fatigue.databaseStopped().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();if(background!=null)background.join(2000);}
        verify(repository,never()).recoverFatigue(any(),anyString(),anyInt(),anyInt());
    }

    @Test
    void everyReloadCancelsBothMainThreadTimersAndNoAsyncHashMapCleanupRemains() throws Exception {
        var scheduler=mock(BukkitScheduler.class);var timers=new ArrayList<BukkitTask>();var plugin=mock(JavaPlugin.class);var server=mock(Server.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(call->{var task=mock(BukkitTask.class);timers.add(task);return task;});
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            for(int i=0;i<5;i++){var service=service(plugin,mock(FishingRepository.class),null,null);service.start();service.shutdown();service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);}
        }
        assertEquals(10,timers.size());for(var timer:timers)verify(timer).cancel();
        verify(scheduler,never()).runTaskTimerAsynchronously(any(Plugin.class),any(Runnable.class),anyLong(),anyLong());
    }

    @Test
    void oldResultCloseCannotCloseTheUiOfANewService() throws Exception {
        Path fish=directory.resolve("fishes/legendary_carp.yml");Files.createDirectories(fish.getParent());
        try(var input=getClass().getResourceAsStream("/fishes/legendary_carp.yml")){assertNotNull(input);Files.copy(input,fish);}
        Path water=directory.resolve("waters/water-dragon_lake.yml");Files.createDirectories(water.getParent());
        try(var input=getClass().getResourceAsStream("/waters/water-dragon_lake.yml")){assertNotNull(input);Files.copy(input,water);}
        var repository=mock(FishingRepository.class);var bridge=mock(PacketBridgeAPI.class);var scheduler=mock(BukkitScheduler.class);
        var delayed=new HashMap<Long,List<Runnable>>();var plugin=mock(JavaPlugin.class);var player=mock(Player.class);UUID owner=UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        var inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(repository.loadSeasonData(eq(owner),anyString())).thenAnswer(call->FishingSeasonData.empty(owner,call.getArgument(1)));when(repository.loadBaitQueue(owner)).thenReturn(List.of());
        when(bridge.isAvailable()).thenReturn(true);when(bridge.openUi(player,"test-ui")).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(call->{delayed.computeIfAbsent(call.getArgument(2),key->new ArrayList<>()).add(call.getArgument(1));return mock(BukkitTask.class);});
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);bukkit.when(()->Bukkit.getPlayer(owner)).thenReturn(player);
            var service=service(plugin,repository,null,bridge);
            service.startTrial(player,"legendary_carp",80,false,null,"normal",FishingService.TrialHook.NONE);
            assertNotNull(service.getActiveMinigame(owner));service.getActiveMinigame(owner).giveUp(player);
            assertEquals(1,delayed.get(30L).size());service.shutdown();service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
            var replacement=service(plugin,repository,null,bridge);
            replacement.startTrial(player,"legendary_carp",80,false,null,"normal",FishingService.TrialHook.NONE);
            assertNotNull(replacement.getActiveMinigame(owner));
            clearInvocations(bridge);delayed.get(30L).get(0).run();verify(bridge,never()).closeUi(any(),anyString());
            replacement.shutdown();replacement.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        }
    }

    @Test
    void placeholdersShareOneBackgroundReadAndRetirementWaitsForAnActiveRead() throws Exception {
        var repository = mock(FishingRepository.class);
        var service = service(mock(JavaPlugin.class), repository, null, null);
        UUID owner = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        var loading = new CountDownLatch(1);
        var retiring = new CountDownLatch(1);
        var release = new java.util.concurrent.Semaphore(0);
        Thread caller = Thread.currentThread();
        doAnswer(invocation -> {
            assertNotSame(caller, Thread.currentThread());
            loading.countDown();
            assertTrue(release.tryAcquire(3, TimeUnit.SECONDS));
            return new FishingRepository.DisplayData(6, 12, 5, 3, 0, 8, 5, 0, Map.of());
        }).when(repository).loadDisplayData(eq(owner), anyString());
        doAnswer(invocation -> {
            retiring.countDown();
            release.acquireUninterruptibly();
            return new FishingRepository.DisplayData(8, 12, 5, 3, 0, 8, 5, 0, Map.of());
        }).when(repository).loadDisplayData(eq(pending), anyString());

        try {
            assertNull(service.displayPlaceholder(owner, "unknown"));
            assertEquals("0", service.displayPlaceholder(owner, "level"));
            assertTrue(loading.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) {
                assertEquals("0", service.displayPlaceholder(owner, "xp"));
                assertEquals("0", service.displayPlaceholder(owner, "total_caught"));
            }
            release.release();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!"6".equals(service.displayPlaceholder(owner, "level")) && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals("6", service.displayPlaceholder(owner, "level"));
            assertEquals("12", service.displayPlaceholder(owner, "xp"));
            assertEquals("8", service.displayPlaceholder(owner, "total_caught"));
            verify(repository, times(1)).loadDisplayData(eq(owner), anyString());
            verify(repository, never()).loadSeasonData(any(), anyString());
            verify(repository, never()).loadPermanentData(any());

            assertEquals("0", service.displayPlaceholder(pending, "level"));
            assertTrue(retiring.await(2, TimeUnit.SECONDS));
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        } finally {
            release.release();
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
        assertEquals("0", service.displayPlaceholder(pending, "level"));
    }

    @Test
    void baitPageCoalescesReadsDropsClosedViewsAndConsumesTokensBeforeRefresh() throws Exception {
        var repository = mock(FishingRepository.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var bridge = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        var main = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var allowRead = new CountDownLatch(1);
        var uuid = UUID.randomUUID();
        var session = new AuthSnapshot(uuid, UUID.randomUUID(), 1, uuid, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var sent = new ArrayList<Map<?, ?>>();
        String uiId = "baits";
        Thread caller = Thread.currentThread();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.isUiOpen(player, uiId)).thenReturn(true);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            main.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(bridge.sendPacket(eq(player), eq(uiId), eq("init"), any())).thenAnswer(call -> {
            sent.add(call.getArgument(3));
            return true;
        });
        when(repository.loadSeasonData(eq(uuid), anyString())).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            entered.countDown();
            assertTrue(allowRead.await(2, TimeUnit.SECONDS));
            return FishingSeasonData.empty(uuid, call.getArgument(1));
        });
        var service = service(plugin, repository, null, bridge);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(uuid)).thenReturn(session);
            auth.when(() -> AuthAccess.admitted(uuid)).thenReturn(true);
            service.pushBaitsData(player, uiId);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) service.pushBaitsData(player, uiId);
            assertTrue(sent.isEmpty());

            allowRead.countDown();
            Runnable response = main.poll(2, TimeUnit.SECONDS);
            assertNotNull(response);
            when(bridge.isUiOpen(player, uiId)).thenReturn(false);
            response.run();
            assertTrue(sent.isEmpty());
            verify(repository, times(1)).loadSeasonData(eq(uuid), anyString());
            verify(repository, times(1)).loadBaitQueue(uuid);

            when(bridge.isUiOpen(player, uiId)).thenReturn(true);
            service.pushBaitsData(player, uiId);
            response = main.poll(2, TimeUnit.SECONDS);
            assertNotNull(response);
            response.run();
            assertEquals(1, sent.size());
            assertEquals(1, sent.get(0).get("baitSlots"));
            String token = (String) sent.get(0).get("action_token");
            assertTrue(service.beginWriteAction(player, uiId, token));
            service.finishWriteAction(player);
            assertFalse(service.beginWriteAction(player, uiId, token));
        } finally {
            allowRead.countDown();
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void acceptedTalentWriteRunsOffThreadAndDrainsBeforeRetirement() throws Exception {
        var repository = mock(FishingRepository.class);
        var player = mock(Player.class);
        var bridge = mock(PacketBridgeAPI.class);
        var service = service(mock(JavaPlugin.class), repository, null, bridge);
        var uuid = UUID.randomUUID();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var auth = new AuthSnapshot(uuid, UUID.randomUUID(), 1, uuid, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        String uiId = "talents";
        var field = FishingService.class.getDeclaredField("tokenGuard");
        field.setAccessible(true);
        var tokens = (xuanmo.arcartxsuite.api.security.ActionTokenGuard) field.get(service);
        String token = tokens.issue(uuid, uiId);
        Thread caller = Thread.currentThread();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.isUiOpen(player, uiId)).thenReturn(true);
        when(repository.updateSeason(eq(uuid), anyString(), any())).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            started.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            var before = new FishingSeasonData(uuid, call.getArgument(1), 2, 20, 2, 0, Map.of("skill", 2), 0, false);
            java.util.function.UnaryOperator<FishingSeasonData> mutation = call.getArgument(2);
            return new FishingRepository.SeasonChange(before, mutation.apply(before));
        });

        try (var bukkit = mockStatic(Bukkit.class); var access = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            access.when(() -> AuthAccess.admitted(uuid)).thenReturn(true);
            access.when(() -> AuthAccess.snapshot(uuid)).thenReturn(auth);
            service.submitTalentWrite(player, uiId, token, true, null);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            service.submitTalentWrite(player, uiId, token, true, null);
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        } finally {
            service.shutdown();
            release.countDown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
        verify(repository, times(1)).updateSeason(eq(uuid), anyString(), any());
        verify(bridge, never()).sendPacket(any(), anyString(), anyString(), any());
    }

    private JdbcFishingRepository repository() throws Exception {
        var source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+directory.resolve(UUID.randomUUID()+".db"));
        var repository=new JdbcFishingRepository(source,StorageDescriptor.sqlite("fishing.db"),directory.toFile(),logger);repository.initialize();return repository;
    }
    private FishingCatchRecord catchRecord(UUID owner){return new FishingCatchRecord(UUID.randomUUID(),owner,"carp","鲤鱼","rare",80,false,System.currentTimeMillis());}
    private TaskEngine engine(FishingRepository repository){return new TaskEngine(List.of(),List.of(),List.of(),0,0,()->null,()->null,logger,mock(JavaPlugin.class),repository,"test");}
    private FishingService service(JavaPlugin plugin,FishingRepository repository,TaskEngine tasks,PacketBridgeAPI bridge) throws Exception {
        var config=FishingModuleConfiguration.load(new YamlConfiguration(),directory.toFile(),logger);
        return new FishingService(plugin,config,repository,bridge,mock(IconResolver.class),logger,"test-ui",new TalentEngine(Map.of(),List.of()),null,List.of(),null,null,tasks,null,null,null);
    }
    private static void set(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
}
