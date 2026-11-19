package xuanmo.arcartxsuite.battlepass.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.source.BukkitEventSourceListener;
import xuanmo.arcartxsuite.battlepass.placeholder.BattlePassPlaceholderExpansion;
import xuanmo.arcartxsuite.battlepass.storage.JdbcBattlePassRepository;
import xuanmo.arcartxsuite.module.SimpleEventBus;

class BattlePassTaskIntegrationTest {
    private static final String SEASON = "integration";
    private static final String CHAT = "axs.chat.chat_message_sent";
    private static final String QUEST = "axs.questgps.quest_completed";
    private static final String LOGIN = "season-login-30";
    private static final String DAILY_LOGIN = "daily-login";
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    @TempDir Path data;
    private final LocalDate today = LocalDate.now();
    private final UUID uuid = UUID.randomUUID();
    private final Queue<Runnable> asyncJobs = new ArrayDeque<>();
    private final SimpleEventBus bus = new SimpleEventBus(LOGGER);
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<AuthAccess> auth;
    private Player player;
    private JdbcBattlePassRepository repository;
    private BattlePassService service;

    @BeforeEach
    void setUp() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + data.resolve("battlepass.db").toAbsolutePath());
        repository = new JdbcBattlePassRepository(source, StorageDescriptor.sqlite("battlepass.db"),
            data.toFile(), LOGGER);
        repository.initialize();
        repository.saveProgress(new BattlePassPlayerProgress(uuid, SEASON));

        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> {
            asyncJobs.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(mock(BukkitTask.class));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        auth = mockStatic(AuthAccess.class);
        auth.when(() -> AuthAccess.admitted(uuid)).thenReturn(true);
        auth.when(() -> AuthAccess.snapshot(uuid)).thenReturn(taskSession());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("BattlePassTester");
        when(player.isOnline()).thenReturn(true);
    }

    private AuthSnapshot taskSession() {
        return new AuthSnapshot(uuid, UUID.randomUUID(), 1L, uuid, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", java.util.Set.of(), "");
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            stopService();
        } finally {
            if (auth != null) auth.close();
            if (bukkit != null) bukkit.close();
        }
    }

    @Test
    void preparedVariableCurveKeepsTheCorrectRequirementForEachLevel() throws Exception {
        var alive=new java.util.concurrent.atomic.AtomicBoolean(true);
        var bridge=new AriaBridge(){public boolean available(){return true;}public String version(){return "variable-curve";}public Object eval(String code,Map<String,Object> bindings){return alive.get()?((Number)bindings.get("level")).intValue()*100:null;}};
        startService(withCurveBridge(configure("",""),bridge));alive.set(false);
        assertEquals(200,service.xpRequiredForLevelOr(2,0));assertEquals(300,service.xpRequiredForLevelOr(3,0));
        service.addXpAsync(player,250).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(2,repository.loadProgress(uuid,SEASON).currentLevel());assertEquals(50,repository.loadProgress(uuid,SEASON).currentXp());
        service.addXpAsync(player,300).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(3,repository.loadProgress(uuid,SEASON).currentLevel());assertEquals(50,repository.loadProgress(uuid,SEASON).currentXp());
    }

    @Test
    void preparedXpDoesNotCallTheAriaProviderAgainAfterThatProviderBecomesUnavailable() throws Exception {
        var config=configure("","");var alive=new java.util.concurrent.atomic.AtomicBoolean(true);var evaluations=new java.util.concurrent.atomic.AtomicInteger();
        var bridge=new AriaBridge(){public boolean available(){return alive.get();}public String version(){return "lifetime-test";}public Object eval(String code,Map<String,Object> bindings){evaluations.incrementAndGet();return alive.get()?1000:null;}};
        config=withCurveBridge(config,bridge);
        startService(config);int prepared=evaluations.get();alive.set(false);
        assertEquals(37,service.addXpAsync(player,37).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertEquals(37,repository.loadProgress(uuid,SEASON).currentXp());assertEquals(prepared,evaluations.get());
        assertEquals(1000,service.xpRequiredForLevelOr(2,0));assertEquals(prepared,evaluations.get());
    }

    @Test
    void preparationRunsOffMainAndUnpreparedRequestsAreRejectedWithoutDatabaseWork() throws Exception {
        var config=configure("","");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);Thread primary=Thread.currentThread();
        var bridge=new AriaBridge(){public boolean available(){return true;}public String version(){return "blocked-prepare";}public Object eval(String code,Map<String,Object> bindings){
            assertTrue(Thread.currentThread()!=primary);entered.countDown();try{assertTrue(release.await(4,TimeUnit.SECONDS));}catch(InterruptedException error){throw new IllegalStateException(error);}return 1000;
        }};
        config=withCurveBridge(config,bridge);
        repository=spy(repository);service=new BattlePassService(mock(JavaPlugin.class),config,repository,LOGGER,null);service.start();
        try{
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.addXpAsync(player,1).toCompletableFuture().get());
            verify(repository,never()).loadProgress(any(),anyString());
            var stopped=service.shutdown();assertFalse(stopped.toCompletableFuture().isDone());release.countDown();stopped.toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();}
    }

    @Test
    void resetWithAnUnknownCommitOutcomeCannotRestoreOldCachedExperienceOnTheNextChange() throws Exception {
        var actual=repository;repository=spy(repository);startService(configure("",""));
        service.setXpAsync(player,100).toCompletableFuture().get(3,TimeUnit.SECONDS);
        doAnswer(call->{call.callRealMethod();throw new SQLException("reset committed but acknowledgement lost");}).when(repository).resetPlayerProgress(uuid,SEASON);
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.resetProgressAsync(uuid).toCompletableFuture().get(3,TimeUnit.SECONDS));
        service.addXpAsync(player,7).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(7,actual.loadProgress(uuid,SEASON).currentXp());
    }

    @Test
    void asyncUnconfirmedRewardRemainsRetryableWithTheOriginalDurableRequest() throws Exception {
        writeCurrencyReward(50);var pending=mock(PendingRewardService.class);
        when(pending.savePendingBatchConfirmed(anyString(),anyList())).thenReturn(false,true);
        startService(configure("",""),pending);
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.claimAllRewardsAsync(player).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertEquals(1,service.claimAllRewardsAsync(player).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertEquals(0,service.claimAllRewardsAsync(player).toCompletableFuture().get(3,TimeUnit.SECONDS));
        var keys=ArgumentCaptor.forClass(String.class);verify(pending,times(2)).savePendingBatchConfirmed(keys.capture(),anyList());
        assertEquals(keys.getAllValues().get(0),keys.getAllValues().get(1));
    }

    @Test
    void largeAsyncXpInputCannotOverflowIntoNegativeStoredExperience() throws Exception {
        startService(configure("",""));service.setXpAsync(player,999).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertTrue(service.addXpAsync(player,Integer.MAX_VALUE).toCompletableFuture().get(3,TimeUnit.SECONDS)>0);
        var stored=repository.loadProgress(uuid,SEASON);
        assertEquals(100,stored.currentLevel());assertEquals(999,stored.currentXp());
    }

    @Test
    void busyPlayerBatchesYieldBeforeDrainingAllTheirRemainingChanges() throws Exception {
        repository=spy(repository);var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var hotRelease=new CountDownLatch(1);
        var commits=new java.util.concurrent.ConcurrentHashMap<UUID,java.util.concurrent.atomic.AtomicInteger>();
        doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return call.callRealMethod();}).when(repository).loadProgress(any(UUID.class),eq(SEASON));
        startService(configure("",""));
        UUID otherId;do{otherId=UUID.randomUUID();}while(Math.floorMod(otherId.hashCode(),256)==Math.floorMod(uuid.hashCode(),256));
        var other=mock(Player.class);when(other.getUniqueId()).thenReturn(otherId);when(other.getName()).thenReturn("SecondBusy");
        UUID chosenCold;do{chosenCold=UUID.randomUUID();}while(Math.floorMod(chosenCold.hashCode(),256)==Math.floorMod(uuid.hashCode(),256)||Math.floorMod(chosenCold.hashCode(),256)==Math.floorMod(otherId.hashCode(),256));
        final UUID coldId=chosenCold;
        var cold=mock(Player.class);when(cold.getUniqueId()).thenReturn(coldId);when(cold.getName()).thenReturn("WaitingPlayer");
        doAnswer(call->{
            UUID id=call.<BattlePassPlayerProgress>getArgument(0).playerUuid();
            int count=commits.computeIfAbsent(id,key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            if(!id.equals(coldId)&&count>=2)assertTrue(hotRelease.await(5,TimeUnit.SECONDS));
            return call.callRealMethod();
        }).when(repository).saveProgressAndTasks(any(),anyList(),any());
        var calls=new java.util.ArrayList<java.util.concurrent.CompletionStage<Integer>>();
        calls.add(service.addXpAsync(player,1));calls.add(service.addXpAsync(other,1));
        try{
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            for(int i=1;i<64;i++){calls.add(service.addXpAsync(player,1));calls.add(service.addXpAsync(other,1));}
            var waiting=service.addXpAsync(cold,1);calls.add(waiting);release.countDown();
            assertEquals(1,waiting.toCompletableFuture().get(2,TimeUnit.SECONDS));
            hotRelease.countDown();
            for(var call:calls)call.toCompletableFuture().get(8,TimeUnit.SECONDS);
        }finally{release.countDown();hotRelease.countDown();}
    }

    @Test
    void coalescedConcurrentChangesKeepAdmissionOrderAndCommitOncePerBatch() throws Exception {
        var actual=repository;repository=spy(repository);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return call.callRealMethod();}).when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        var first=service.addXpAsync(player,1);assertTrue(entered.await(1,TimeUnit.SECONDS));
        var set=service.setXpAsync(player,50);var remove=service.removeXpAsync(player,7);var add=service.addXpAsync(player,3);
        try {
            release.countDown();first.toCompletableFuture().get(3,TimeUnit.SECONDS);set.toCompletableFuture().get(3,TimeUnit.SECONDS);remove.toCompletableFuture().get(3,TimeUnit.SECONDS);
            assertEquals(46,add.toCompletableFuture().get(3,TimeUnit.SECONDS));assertEquals(46,actual.loadProgress(uuid,SEASON).currentXp());
            verify(repository,org.mockito.Mockito.atMost(2)).saveProgressAndTasks(any(),anyList(),any());
        }finally{release.countDown();}
    }

    @Test
    void failedBatchCommitCannotCompleteAnyMutationWithSuccess() throws Exception {
        repository=spy(repository);doThrow(new SQLException("isolated commit failure")).when(repository).saveProgressAndTasks(any(),anyList(),any());
        startService(configure("",""));
        var failed=org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.addXpAsync(player,5).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertTrue(failed.getCause() instanceof SQLException);
    }

    @Test
    void taskLoadFailureIsNotReportedAsAnAppliedIncrement() throws Exception {
        String task="unavailable-task";repository=spy(repository);doThrow(new SQLException("isolated task read failure")).when(repository).loadPlayerTasks(uuid,SEASON);
        startService(configure("","unavailable-task:\n  event-topic: axs.test.unavailable\n  required-count: 10\n"));
        var failure=org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.updateTaskProgressAsync(player,task,2).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof SQLException);verify(repository,never()).saveProgressAndTasks(any(),anyList(),any());
    }

    @Test
    void asyncColdMutationReturnsImmediatelyAndCompletesOnlyAfterItsRealCommit() throws Exception {
        var actual=repository;repository=spy(repository);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);Thread primary=Thread.currentThread();
        doAnswer(call->{assertTrue(Thread.currentThread()!=primary);entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return call.callRealMethod();})
            .when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        long start=System.nanoTime();var result=service.addXpAsync(player,37);
        assertTrue(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500));
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertFalse(result.toCompletableFuture().isDone());
            release.countDown();assertEquals(37,result.toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertEquals(37,actual.loadProgress(uuid,SEASON).currentXp());
            assertEquals(0,asyncJobs.size(),"business commit must not enqueue a duplicate Bukkit write");
            verify(repository,times(1)).saveProgressAndTasks(any(),anyList(),any());
        }finally{release.countDown();}
    }

    @Test
    void asyncMutationRejectsFailedColdDataInsteadOfApplyingToAFallbackObject() throws Exception {
        var actual=repository;actual.saveProgress(new BattlePassPlayerProgress(uuid,SEASON).withXp(55));
        repository=spy(repository);doThrow(new SQLException("isolated cold failure")).when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        var failure=org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.addXpAsync(player,10).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof SQLException);verify(repository,never()).saveProgressAndTasks(any(),anyList(),any());
        assertEquals(55,actual.loadProgress(uuid,SEASON).currentXp());
        org.mockito.Mockito.doCallRealMethod().when(repository).loadProgress(uuid,SEASON);
        service.setLevelAsync(player,3).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(3,actual.loadProgress(uuid,SEASON).currentLevel());
    }

    @Test
    void queuedBusinessMutationsDrainAcrossStopEvenWhenTheCallerCancelsItsView() throws Exception {
        repository=spy(repository);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return call.callRealMethod();}).when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        var results=new java.util.ArrayList<java.util.concurrent.CompletionStage<Integer>>();
        for(int i=0;i<32;i++)results.add(service.addXpAsync(player,1));
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));results.get(0).toCompletableFuture().cancel(true);
            var stopped=service.shutdown();assertFalse(stopped.toCompletableFuture().isDone());
            release.countDown();stopped.toCompletableFuture().get(8,TimeUnit.SECONDS);
            for(int i=1;i<results.size();i++)results.get(i).toCompletableFuture().get(1,TimeUnit.SECONDS);
            assertEquals(32,repository.loadProgress(uuid,SEASON).currentXp());
            verify(repository,org.mockito.Mockito.atMost(2)).saveProgressAndTasks(any(),anyList(),any());
            assertEquals(0,asyncJobs.size());
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.addXpAsync(player,1).toCompletableFuture().get());
        }finally{release.countDown();}
    }

    @Test
    void businessQueueIsBoundedAndOverflowIsAnExplicitFailure() throws Exception {
        repository=spy(repository);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return call.callRealMethod();}).when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        var results=new java.util.ArrayList<java.util.concurrent.CompletionStage<Integer>>();
        results.add(service.addXpAsync(player,1));
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            for(int i=1;i<130;i++)results.add(service.addXpAsync(player,1));
            var overflow=org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,()->service.addXpAsync(player,1).toCompletableFuture().get());
            assertTrue(overflow.getCause() instanceof java.util.concurrent.RejectedExecutionException);
            release.countDown();for(var result:results)result.toCompletableFuture().get(8,TimeUnit.SECONDS);
            assertEquals(130,repository.loadProgress(uuid,SEASON).currentXp());
        }finally{release.countDown();}
    }

    @Test
    void coldProgressReadAndBusinessWriteShareTheSameLoad() throws Exception {
        repository = spy(repository);
        var reads = new AtomicInteger();
        var selected = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            Object loaded = call.callRealMethod();
            if (reads.incrementAndGet() == 1) {
                selected.countDown();
                assertTrue(release.await(4, TimeUnit.SECONDS));
            }
            return loaded;
        }).when(repository).loadProgress(uuid, SEASON);
        startService(configure("", ""));
        var read = CompletableFuture.supplyAsync(() -> service.getProgress(player));
        try {
            assertTrue(selected.await(2, TimeUnit.SECONDS));
            var write = service.setLevelAsync(player, 5).toCompletableFuture();
            assertThrows(TimeoutException.class, () -> write.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(1, read.get(3, TimeUnit.SECONDS).currentLevel());
            write.get(3, TimeUnit.SECONDS);
            assertEquals(1, reads.get());
            assertEquals(5, service.getProgress(player).currentLevel());
            assertEquals(5, repository.loadProgress(uuid, SEASON).currentLevel());
        } finally {
            release.countDown();
        }
    }

    @Test
    void coldTaskLoadDoesNotHoldTheGameplayCacheMonitor() throws Exception {
        repository = spy(repository);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertTrue(release.await(4, TimeUnit.SECONDS));
            return call.callRealMethod();
        }).when(repository).loadPlayerTasks(uuid, SEASON);
        startService(configure("", ""));
        var load = CompletableFuture.supplyAsync(() -> service.getTaskInstances(player));
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var lockMethod = BattlePassService.class.getDeclaredMethod("playerLock", UUID.class);
            lockMethod.setAccessible(true);
            Object cacheMonitor = lockMethod.invoke(service, uuid);
            var localUpdate = CompletableFuture.runAsync(() -> {
                synchronized (cacheMonitor) {
                    assertFalse(load.isDone());
                }
            });
            localUpdate.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        load.get(3, TimeUnit.SECONDS);
    }

    @Test
    void allAsyncMutationKindsPersistAndDoNotLeaveBukkitWriteJobs() throws Exception {
        String task="async-task";saveTask(task,BattlePassTask.TaskCategory.SEASON,10,0);
        startService(configure("","async-task:\n  event-topic: axs.test.async\n  required-count: 10\n"));
        assertEquals(10,service.addXpAsync(player,10).toCompletableFuture().get(3,TimeUnit.SECONDS));
        service.setXpAsync(player,50).toCompletableFuture().get(3,TimeUnit.SECONDS);
        service.removeXpAsync(player,7).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(43,repository.loadProgress(uuid,SEASON).currentXp());
        assertTrue(service.unlockTierAsync(player,BattlePassPlayerProgress.PassTier.PREMIUM).toCompletableFuture().get(3,TimeUnit.SECONDS));
        assertFalse(service.unlockTierAsync(player,BattlePassPlayerProgress.PassTier.PREMIUM).toCompletableFuture().get(3,TimeUnit.SECONDS));
        service.setLevelAsync(player,5).toCompletableFuture().get(3,TimeUnit.SECONDS);
        service.updateTaskProgressAsync(player,task,2).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertProgress(task,2);assertEquals(5,repository.loadProgress(uuid,SEASON).currentLevel());
        assertEquals(2,repository.loadPlayerTasks(uuid,SEASON).stream().filter(row->row.templateId().equals(task)).findFirst().orElseThrow().currentProgress());
        service.resetProgressAsync(uuid).toCompletableFuture().get(3,TimeUnit.SECONDS);
        assertEquals(1,repository.loadProgress(uuid,SEASON).currentLevel());assertTrue(repository.loadPlayerTasks(uuid,SEASON).isEmpty());
        assertEquals(0,asyncJobs.size());
    }

    @Test
    void asyncClaimFinishesItsReceiptWhileStoppingAndReloadDoesNotStageAgain() throws Exception {
        writeCurrencyReward(50);var config=configure("","");var pending=mock(PendingRewardService.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(pending.savePendingBatchConfirmed(anyString(),anyList())).thenAnswer(call->{entered.countDown();return release.await(4,TimeUnit.SECONDS);});
        startService(config,pending);var claim=service.claimAllRewardsAsync(player);
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));var stopped=service.shutdown();assertFalse(stopped.toCompletableFuture().isDone());
            release.countDown();assertEquals(1,claim.toCompletableFuture().get(3,TimeUnit.SECONDS));stopped.toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertEquals(1,repository.loadClaimedRewards(uuid,SEASON).size());
        startService(config,pending);assertEquals(0,service.claimAllRewardsAsync(player).toCompletableFuture().get(3,TimeUnit.SECONDS));
        verify(pending).savePendingBatchConfirmed(anyString(),anyList());
    }

    @Test
    void shutdownDrainsEveryAcceptedFactAndReplayAfterReloadDoesNotAdvanceAgain() throws Exception {
        String task="auth-queue";
        saveTask(task,BattlePassTask.TaskCategory.SEASON,100,0);
        var config=configure("","""
            auth-queue:
              event-topic: axs.auth.profile_first_admitted
              required-count: 100
            """);
        repository=spy(repository);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return call.callRealMethod();})
            .when(repository).saveAuthFact(anyString(),any(),anyList(),org.mockito.ArgumentMatchers.anyBoolean());
        startService(config);
        var receipts=new java.util.ArrayList<java.util.concurrent.CompletionStage<Void>>();
        for(int i=0;i<32;i++)receipts.add(service.acceptAuthFact(player,task,1,"queued-"+i,today));
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            long start=System.nanoTime();var stopped=service.shutdown();
            assertTrue(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(stopped.toCompletableFuture().isDone());
            assertTrue(receipts.stream().noneMatch(receipt->receipt.toCompletableFuture().isDone()));
            release.countDown();stopped.toCompletableFuture().get(5,TimeUnit.SECONDS);
            for(var receipt:receipts)receipt.toCompletableFuture().get(1,TimeUnit.SECONDS);
        }finally{release.countDown();}
        startService(config);
        for(int i=0;i<32;i++)service.acceptAuthFact(player,task,1,"queued-"+i,today).toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertProgress(task,32);
    }

    @Test
    void cancelledCallerViewCannotCancelTheAcceptedFactOrItsShutdownDrain() throws Exception {
        String task="auth-cancel";saveTask(task,BattlePassTask.TaskCategory.SEASON,5,0);
        var config=configure("","auth-cancel:\n  event-topic: axs.auth.profile_first_admitted\n  required-count: 5\n");
        repository=spy(repository);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return call.callRealMethod();})
            .when(repository).saveAuthFact(anyString(),any(),anyList(),org.mockito.ArgumentMatchers.anyBoolean());
        startService(config);
        var receipt=service.acceptAuthFact(player,task,1,"cancelled-view",today);
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));receipt.toCompletableFuture().cancel(true);
            var stopped=service.shutdown();assertFalse(stopped.toCompletableFuture().isDone());
            release.countDown();stopped.toCompletableFuture().get(5,TimeUnit.SECONDS);
        }finally{release.countDown();}
        startService(config);
        service.acceptAuthFact(player,task,1,"cancelled-view",today).toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertProgress(task,1);
    }

    @Test
    void runningCallbackRetiresBeforeTheFinalFlushAndQueuedCallbacksCannotStartLate() throws Exception {
        startService(configure("",""));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var late=new java.util.concurrent.atomic.AtomicInteger();
        service.async(()->{entered.countDown();try{release.await();}catch(InterruptedException error){Thread.currentThread().interrupt();}});
        service.async(late::incrementAndGet);
        var worker=new Thread(asyncJobs.remove());worker.start();
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));var stopped=service.shutdown();
            assertFalse(stopped.toCompletableFuture().isDone());
            asyncJobs.remove().run();assertEquals(0,late.get());
            release.countDown();stopped.toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();worker.join(2000);}
    }

    @Test
    void papiColdPollingCoalescesLoadingAndStaticOrUnknownFieldsDoNotQuery() throws Exception {
        repository=spy(repository);startService(configure("",""));
        var expansion=new BattlePassPlaceholderExpansion(mock(JavaPlugin.class),()->service);
        assertEquals(SEASON,expansion.onPlaceholderRequest(player,"season"));
        assertEquals("",expansion.onPlaceholderRequest(player,"unknown"));assertEquals(0,asyncJobs.size());
        for(int i=0;i<200;i++)assertEquals("",expansion.onPlaceholderRequest(player,"level"));
        assertEquals(1,asyncJobs.size());verify(repository,never()).loadProgress(uuid,SEASON);
        runAsyncJobs();assertEquals("1",expansion.onPlaceholderRequest(player,"level"));
        verify(repository).loadProgress(uuid,SEASON);assertEquals(0,asyncJobs.size());
    }

    @Test
    void papiDatabaseOutageKeepsUntrustedProgressOutOfCacheAndBacksOff() throws Exception {
        repository=spy(repository);doThrow(new SQLException("isolated outage")).when(repository).loadProgress(uuid,SEASON);
        startService(configure("",""));
        var expansion=new BattlePassPlaceholderExpansion(mock(JavaPlugin.class),()->service);
        assertEquals("",expansion.onPlaceholderRequest(player,"xp"));runAsyncJobs();
        // Historical synchronous callers may have retained a fallback object after a failed load.
        var cache=BattlePassService.class.getDeclaredField("cachedProgress");cache.setAccessible(true);
        ((Map<UUID,BattlePassPlayerProgress>)cache.get(service)).put(uuid,new BattlePassPlayerProgress(uuid,SEASON).withXp(99));
        for(int i=0;i<200;i++)assertEquals("",expansion.onPlaceholderRequest(player,"xp"));
        assertEquals(0,asyncJobs.size());verify(repository,times(2)).loadProgress(uuid,SEASON);
        verify(repository,never()).saveProgress(any());
    }

    @Test
    void rewardAlreadyAcceptedDuringStopStillWritesItsReceiptAndDoesNotEnqueueTwice() throws Exception {
        writeCurrencyReward(50);var config=configure("","");var pending=mock(PendingRewardService.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(pending.savePendingBatchConfirmed(anyString(),anyList())).thenAnswer(call->{entered.countDown();return release.await(5,TimeUnit.SECONDS);});
        startService(config,pending);
        service.async(()->service.claimRewards(player,1));
        var worker=new Thread(asyncJobs.remove());worker.start();
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));var stopped=service.shutdown();
            assertFalse(stopped.toCompletableFuture().isDone());release.countDown();
            stopped.toCompletableFuture().get(5,TimeUnit.SECONDS);
        }finally{release.countDown();worker.join(2000);}
        assertEquals(1,repository.loadClaimedRewards(uuid,SEASON).size());
        startService(config,pending);assertEquals(new BattlePassService.ClaimBatch(0,false),service.claimAll(player));
        verify(pending).savePendingBatchConfirmed(anyString(),anyList());
    }

    @Test
    void latePublishersAndReloadAdvanceExistingTaskInstances() throws Exception {
        String chatId = "daily-chat";
        String questId = "season-quest-200";
        saveTask(chatId, BattlePassTask.TaskCategory.DAILY, 5, 1);
        saveTask(questId, BattlePassTask.TaskCategory.SEASON, 200, 4);
        var configuration = configure("""
            daily-chat:
              event-topic: axs.chat.chat_message_sent
              required-count: 5
            """, """
            season-quest-200:
              event-topic: axs.questgps.quest_completed
              required-count: 200
              conditions:
                - type: event_payload
                  key: quest_id
                  operator: equals
                  value: mainline
            """);
        startService(configuration);
        assertFalse(bus.hasPublisher(CHAT));
        assertFalse(bus.hasPublisher(QUEST));
        assertTrue(bus.hasSubscribers(CHAT));
        assertTrue(bus.hasSubscribers(QUEST));

        bus.registerPublisher(CHAT);
        bus.publish(CHAT, player, Map.of());
        bus.registerPublisher(QUEST);
        bus.publish(QUEST, player, Map.of("quest_id", "mainline"));
        runAsyncJobs();
        assertProgress(chatId, 2);
        assertProgress(questId, 5);

        // Publisher registration is idempotent across producer reloads.
        bus.registerPublisher(CHAT);
        bus.registerPublisher(QUEST);
        bus.publish(CHAT, player, Map.of());
        bus.publish(QUEST, player, Map.of("quest_id", "other"));
        bus.publish(QUEST, player, Map.of("quest_id", "mainline"));
        assertProgress(chatId, 3);
        assertProgress(questId, 6);

        stopService();
        assertFalse(bus.hasSubscribers(CHAT));
        assertFalse(bus.hasSubscribers(QUEST));
        startService(configuration);
        bus.publish(CHAT, player, Map.of());
        bus.publish(QUEST, player, Map.of("quest_id", "mainline"));
        runAsyncJobs();
        assertProgress(chatId, 4);
        assertProgress(questId, 7);
        assertTrue(bus.hasSubscribers(EventBusCapability.MARKET_AUCTION_PURCHASED));
        assertEquals(3, bus.subscriptionCount());
    }

    @Test
    void countsPresenceOnceAcrossReconnectAndRestart() throws Exception {
        saveTask(DAILY_LOGIN, BattlePassTask.TaskCategory.DAILY, 1, 0);
        saveTask(LOGIN, BattlePassTask.TaskCategory.SEASON, 30, 4);
        var configuration = configure(presenceTask(DAILY_LOGIN, 1), presenceTask(LOGIN, 30));
        assertEquals(List.of(DAILY_LOGIN, LOGIN), configuration.tasks().all().stream()
            .map(BattlePassTask::taskId).toList());
        assertTrue(configuration.tasks().all().stream()
            .allMatch(task -> BattlePassTask.PRESENCE_TOPIC.equals(task.eventTopic())));
        startService(configuration);
        assertTrue(bus.hasSubscribers(EventBusCapability.MARKET_AUCTION_PURCHASED));
        assertEquals(3, bus.subscriptionCount());

        var serverFact = fact("server:" + uuid + ":" + today, today);
        bus.publish(BattlePassTask.PRESENCE_TOPIC, player, serverFact);
        // The legacy void publish used by the host must itself reach the confirmed subscribers.
        awaitStoredProgress(DAILY_LOGIN, 1);
        awaitStoredProgress(LOGIN, 5);
        confirm(serverFact);
        assertProgress(DAILY_LOGIN, 1);
        service.handleQuit(uuid);
        confirm(serverFact);
        confirm(fact("authentication-provider-changed", today));
        assertProgress(LOGIN, 5);

        stopService();
        startService(configuration);
        confirm(fact("after-restart", today));
        assertProgress(DAILY_LOGIN, 1);
        assertProgress(LOGIN, 5);
    }

    @Test
    void aColdGameplayBurstLoadsProgressOnceForAllMatchingTasks() throws Exception {
        String topic = "axs.test.shared_gameplay";
        saveTask("first-match", BattlePassTask.TaskCategory.SEASON, 64, 0);
        saveTask("second-match", BattlePassTask.TaskCategory.SEASON, 64, 0);
        startService(configure("", """
            first-match:
              event-topic: axs.test.shared_gameplay
              required-count: 64
            second-match:
              event-topic: axs.test.shared_gameplay
              required-count: 64
            """));
        int queued = asyncJobs.size();
        for (int i = 0; i < 20; i++) bus.publish(topic, player, Map.of());
        assertEquals(queued + 1, asyncJobs.size());
        runAsyncJobs();
        assertProgress("first-match", 20);
        assertProgress("second-match", 20);

        bus.publish(topic, player, Map.of());
        assertProgress("first-match", 21);
        assertProgress("second-match", 21);
    }

    @Test
    void coldProgressFromTheRetiredConnectionCannotBeAppliedAfterRejoin() throws Exception {
        String topic = "axs.test.cold_rejoin";
        saveTask("rejoin-task", BattlePassTask.TaskCategory.SEASON, 10, 0);
        startService(configure("", """
            rejoin-task:
              event-topic: axs.test.cold_rejoin
              required-count: 10
            """));
        bus.publish(topic, player, Map.of());
        var newSession = taskSession();
        auth.when(() -> AuthAccess.snapshot(uuid)).thenReturn(newSession);
        bus.publish(topic, player, Map.of());
        runAsyncJobs();
        assertProgress("rejoin-task", 1);
    }

    @Test
    void completionsQuitAndPeriodicSaveShareOneQueuedWrite() throws Exception {
        repository = spy(repository);
        startSavingFixture();
        bus.publish("axs.test.save_first", player, Map.of());
        bus.publish("axs.test.save_second", player, Map.of());
        service.handleQuit(uuid);
        assertEquals(1, asyncJobs.size());

        var periodic = BattlePassService.class.getDeclaredMethod("flushDirtyProgress");
        periodic.setAccessible(true);
        periodic.invoke(service);
        verify(repository, never()).saveProgressAndTasks(any(), anyList(), anySet());
        runAsyncJobs();
        verify(repository).saveProgressAndTasks(any(), anyList(), anySet());
        assertPersistedCompletions(2);
    }

    @Test
    void completionDuringSavingQueuesExactlyOneFollowUp() throws Exception {
        repository = spy(repository);
        startSavingFixture();
        var entered = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            if (entered.compareAndSet(false, true)) bus.publish("axs.test.save_second", player, Map.of());
            return call.callRealMethod();
        }).when(repository).saveProgressAndTasks(any(), anyList(), anySet());

        bus.publish("axs.test.save_first", player, Map.of());
        assertEquals(1, asyncJobs.size());
        asyncJobs.remove().run();
        assertPersistedCompletions(1);
        assertEquals(1, asyncJobs.size());
        runAsyncJobs();
        verify(repository, times(2)).saveProgressAndTasks(any(), anyList(), anySet());
        assertPersistedCompletions(2);
    }

    private void startSavingFixture() throws Exception {
        saveTask("save-first", BattlePassTask.TaskCategory.SEASON, 1, 0);
        saveTask("save-second", BattlePassTask.TaskCategory.SEASON, 1, 0);
        startService(configure("", """
            save-first:
              event-topic: axs.test.save_first
              required-count: 1
              base-xp-reward: 11
            save-second:
              event-topic: axs.test.save_second
              required-count: 1
              base-xp-reward: 13
            """));
        service.preload(player);
        runAsyncJobs();
    }

    private void assertPersistedCompletions(int count) throws Exception {
        assertEquals(count, repository.loadPlayerTasks(uuid, SEASON).stream()
            .filter(task -> task.templateId().startsWith("save-") && task.completed()).count());
    }

    @Test
    void bukkitFallbackStopsCountingWhenThePublisherBecomesAvailable() throws Exception {
        String taskId = "daily-blocks";
        var pluginManager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        saveTask(taskId, BattlePassTask.TaskCategory.DAILY, 5, 0);
        startService(configure("""
            daily-blocks:
              event-topic: axs.test.block_broken
              required-count: 5
              bukkit-event:
                event-class: org.bukkit.event.block.BlockBreakEvent
            """, ""));
        var listener = ArgumentCaptor.forClass(Listener.class);
        verify(pluginManager).registerEvents(listener.capture(), any(Plugin.class));
        var block = mock(Block.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(block.getType()).thenReturn(Material.STONE);
        var event = new BlockBreakEvent(block, player);
        var bukkitSource = (BukkitEventSourceListener) listener.getValue();

        bukkitSource.onBlockBreak(event);
        runAsyncJobs();
        assertProgress(taskId, 1);
        bus.registerPublisher("axs.test.block_broken");
        bukkitSource.onBlockBreak(event);
        bus.publish("axs.test.block_broken", player, Map.of());
        assertProgress(taskId, 2);
    }

    @Test
    void interactiveClaimRechecksPermissionAfterWaitingForThePlayerFence() throws Exception {
        writeCurrencyReward(50);
        var pending = mock(PendingRewardService.class);
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        startService(configure("", ""), pending);
        when(player.hasPermission("arcartxsuite.battlepass.use")).thenReturn(true);
        var result = repository.withPlayerWrite(uuid, () -> {
            var queued = service.claimLevelAsync(player, 1, () -> true);
            assertFalse(queued.toCompletableFuture().isDone());
            when(player.hasPermission("arcartxsuite.battlepass.use")).thenReturn(false);
            return queued;
        });
        assertEquals(BattlePassService.ClaimResult.RETRY, result.toCompletableFuture().get(5, TimeUnit.SECONDS));
        verify(pending, never()).savePendingBatchConfirmed(anyString(), anyList());
        assertTrue(repository.loadClaimedRewards(uuid, SEASON).isEmpty());

        when(player.hasPermission("arcartxsuite.battlepass.use")).thenReturn(true);
        auth.close();
        auth = null;
        var runtime = mock(xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability.class);
        when(runtime.snapshot(uuid)).thenReturn(taskSession());
        AuthAccess.install(() -> runtime);
        try {
            assertEquals(BattlePassService.ClaimResult.SUCCESS,
                service.claimLevelAsync(player, 1, () -> true).toCompletableFuture().get(5, TimeUnit.SECONDS));
            verify(pending).savePendingBatchConfirmed(anyString(), anyList());
        } finally {
            AuthAccess.install(() -> null);
        }
    }

    @Test
    void simultaneousClaimsOnlyReportOneAcceptedReward() throws Exception {
        writeCurrencyReward(50);
        var pending = mock(PendingRewardService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            entered.countDown();
            return release.await(5, TimeUnit.SECONDS);
        });
        startService(configure("", ""), pending);

        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> service.claimRewards(player, 1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> service.claimAll(player));
            assertFalse(first.isDone());
            release.countDown();
            assertEquals(BattlePassService.ClaimResult.SUCCESS, first.get(5, TimeUnit.SECONDS));
            assertEquals(new BattlePassService.ClaimBatch(0, false), second.get(5, TimeUnit.SECONDS));
            verify(pending).savePendingBatchConfirmed(anyString(), anyList());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void settlementRetriesOnlyTheUnacceptedRewardWithTheOriginalRequest() throws Exception {
        writeCurrencyReward(50);
        Files.writeString(data.resolve("season-rewards.yml"), """
            milestone:
              min-level: 1
              free:
                type: mail
                mail-subject: Season settlement
                mail-body: Your reward
                item:
                  material: DIAMOND
                  custom-model-data: 42
            """);
        var pending = mock(PendingRewardService.class);
        var mailKeys = new ArrayDeque<String>();
        var mailRewards = new ArrayDeque<PendingReward>();
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            PendingReward reward = call.<List<PendingReward>>getArgument(1).get(0);
            if (reward.type() == PendingReward.Type.CURRENCY) return true;
            assertEquals(PendingReward.Type.ITEM_SPEC, reward.type());
            assertTrue(reward.payload().contains("Season settlement"));
            assertTrue(reward.payload().contains("\"customModelData\":42"));
            mailKeys.add(call.getArgument(0));
            mailRewards.add(reward);
            return mailKeys.size() == 2;
        });
        startService(configure("", ""), pending);
        assertEquals(SeasonSettlementService.Result.RETRY, service.settlementService().settle(true));
        assertFalse(repository.isSeasonSettled(SEASON));
        assertEquals(java.util.Set.of("1_free"), repository.loadClaimedRewards(uuid, SEASON));

        stopService();
        Files.writeString(data.resolve("season-rewards.yml"), """
            milestone:
              min-level: 1
              free:
                type: currency
                currency: changed
                amount: 800
            """);
        startService(configure("", ""), pending);
        assertEquals(SeasonSettlementService.Result.COMPLETED, service.settlementService().settle(true));
        assertTrue(repository.isSeasonSettled(SEASON));
        assertEquals(java.util.Set.of("1_free", "settlement_milestone_free"), repository.loadClaimedRewards(uuid, SEASON));
        assertEquals(mailKeys.remove(), mailKeys.remove());
        assertEquals(mailRewards.remove(), mailRewards.remove());
        assertEquals(SeasonSettlementService.Result.ALREADY_SETTLED, service.settlementService().settle(true));
        verify(pending, times(3)).savePendingBatchConfirmed(anyString(), anyList());
    }

    @Test
    void confirmedHostRequestIsReusedWhenTheLocalClaimWriteFails() throws Exception {
        writeCurrencyReward(50);
        var pending = mock(PendingRewardService.class);
        var accepted = new java.util.HashMap<String, List<PendingReward>>();
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            List<PendingReward> batch = call.getArgument(1);
            var previous = accepted.putIfAbsent(call.getArgument(0), batch);
            return previous == null || previous.equals(batch);
        });
        repository = spy(repository);
        doThrow(new SQLException("local write interrupted")).doCallRealMethod()
            .when(repository).completeReward(eq(uuid), eq(SEASON), eq("1_free"), anyString());

        startService(configure("", ""), pending);
        assertEquals(BattlePassService.ClaimResult.RETRY, service.claimRewards(player, 1));
        assertTrue(repository.loadClaimedRewards(uuid, SEASON).isEmpty());
        stopService();
        writeCurrencyReward(100);
        startService(configure("", ""), pending);
        assertEquals(BattlePassService.ClaimResult.SUCCESS, service.claimRewards(player, 1));
        assertEquals(1, accepted.size());
        assertTrue(accepted.values().iterator().next().get(0).payload().contains("\"amount\":50.0"));
        assertEquals(java.util.Set.of("1_free"), repository.loadClaimedRewards(uuid, SEASON));
    }

    @Test
    void ordinaryTaskEventsDoNotAdvanceAnExpiredSeason() throws Exception {
        saveTask("daily-chat", BattlePassTask.TaskCategory.DAILY, 5, 1);
        var source = configure("""
            daily-chat:
              event-topic: axs.chat.chat_message_sent
              required-count: 5
            """, "");
        var season = source.season();
        var expired = new BattlePassModuleConfiguration.BattlePassSeasonConfiguration(
            season.seasonId(), season.displayName(), season.maxLevel(), season.xpCurve(), season.startDate(), today.minusDays(1));
        startService(new BattlePassModuleConfiguration(source.storage(), expired, source.tasks(),
            source.rewardsFile(), source.rewards(), source.seasonRewardsFile(), source.seasonRewards(),
            source.ui()));

        bus.publish(CHAT, player, Map.of());
        runAsyncJobs();
        assertProgress("daily-chat", 1);
    }

    @Test
    void offlineAuctionEventsUseTheBuyerAndDeduplicateByListing() throws Exception {
        String task = "auction";
        saveTask(task, BattlePassTask.TaskCategory.SEASON, 5, 0);
        startService(configure("", """
            auction:
              event-topic: axs.market.auction_purchased
              required-count: 5
            """));
        String topic = "axs.market.auction_purchased";
        var first = Map.of("buyer_uuid", uuid.toString(), "listing_id", "101", "event_id", "auction-101");
        bus.publishConfirmed(topic, null, first);
        awaitStoredProgress(task, 1);
        bus.publishConfirmed(topic, null, first);
        bus.publishConfirmed(topic, null, Map.of("buyer_uuid", uuid.toString(), "listing_id", "102", "event_id", "auction-102"));
        awaitStoredProgress(task, 2);
        assertProgress(task, 2);
    }

    @Test
    void regionExplorationDeduplicatesStableIdsAcrossReloadsPerAssignment() throws Exception {
        String task = "weekly-region-explore";
        String topic = "axs.regions.region_change";
        repository.saveProgress(repository.loadProgress(uuid, SEASON).withWeeklyReset(today, 1));
        saveTask(task, BattlePassTask.TaskCategory.WEEKLY, 10, 0);
        var config = configure("", """
            weekly-region-explore:
              event-topic: axs.regions.region_change
              required-count: 10
            """);
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        startService(config);
        bus.publish(topic, player, Map.of("type", "enter", "region_uuid", first, "region_id", "original"));
        awaitStoredProgress(task, 1);
        stopService();

        startService(config);
        bus.publish(topic, player, Map.of("type", "enter", "region_uuid", first, "region_id", "renamed"));
        bus.publish(topic, player, Map.of("type", "leave", "region_uuid", UUID.randomUUID().toString()));
        bus.publish(topic, player, Map.of("type", "enter", "region_uuid", second, "region_id", "renamed"));
        awaitStoredProgress(task, 2);
        stopService();
        assertEquals(2, repository.loadPlayerTasks(uuid, SEASON).get(0).currentProgress());

        repository.deletePlayerTasksByCategory(uuid, SEASON, BattlePassTask.TaskCategory.WEEKLY);
        repository.savePlayerTasks(uuid, SEASON, List.of(new PlayerTaskInstance(uuid, "new-assignment", task,
            BattlePassTask.TaskCategory.WEEKLY, 10, 0, false, today, 0)));
        startService(config);
        bus.publish(topic, player, Map.of("type", "enter", "region_uuid", first));
        awaitStoredProgress(task, 1);
    }

    @Test
    void signInAndNpcExchangeDeduplicateTheirBusinessEvents() throws Exception {
        saveTask("signin", BattlePassTask.TaskCategory.SEASON, 7, 0);
        saveTask("exchange", BattlePassTask.TaskCategory.SEASON, 7, 0);
        startService(configure("", """
            signin:
              event-topic: axs.onlinerewards.signin_completed
              required-count: 7
            exchange:
              event-topic: axs.market.npc_exchange_completed
              required-count: 7
              conditions:
                - { type: event_payload, key: shop, operator: equals, value: fish }
            """));
        String date = today.toString();
        String signin = "axs.onlinerewards.signin_completed";
        bus.publish(signin, player, Map.of("event_id", date, "business_date", date));
        bus.publish(signin, player, Map.of("event_id", date, "business_date", date));
        String exchange = "axs.market.npc_exchange_completed";
        bus.publish(exchange, player, Map.of("event_id", "ignored", "shop", "other"));
        bus.publish(exchange, player, Map.of("event_id", "first", "shop", "fish"));
        bus.publish(exchange, player, Map.of("event_id", "first", "shop", "fish"));
        bus.publish(exchange, player, Map.of("event_id", "second", "shop", "fish"));
        awaitStoredProgress("signin", 1);
        awaitStoredProgress("exchange", 2);
        assertProgress("signin", 1);
        assertProgress("exchange", 2);
    }

    @Test
    void permanentTitleAndAnchorRewardsUseTheConfirmedQueue() throws Exception {
        Files.writeString(data.resolve("rewards.yml"), """
            title-reward:
              level: 1
              free:
                type: title
                target-id: season_veteran
            anchor-reward:
              level: 2
              free:
                type: map-anchor
                target-id: season_hall
            """);
        repository.saveProgress(new BattlePassPlayerProgress(uuid, SEASON).withLevel(2));
        var pending = mock(PendingRewardService.class);
        var rewards = new ArrayDeque<PendingReward>();
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            rewards.addAll(call.<List<PendingReward>>getArgument(1));
            return true;
        });
        startService(configure("", ""), pending);
        assertEquals(new BattlePassService.ClaimBatch(2, false), service.claimAll(player));
        PendingReward title = rewards.remove();
        PendingReward anchor = rewards.remove();
        assertEquals(PendingReward.Type.TITLE, title.type());
        assertTrue(title.payload().contains("season_veteran"));
        assertEquals(PendingReward.Type.MAP_ANCHOR, anchor.type());
        assertTrue(anchor.payload().contains("season_hall"));
        assertEquals(new BattlePassService.ClaimBatch(0, false), service.claimAll(player));
    }

    @Test
    void committedAbyssRunsResumeFromCursorAndCreditDisconnectedMembers() throws Exception {
        String task = "abyss-clear";
        saveTask(task, BattlePassTask.TaskCategory.SEASON, 3, 0);
        var config = configure("", """
            abyss-clear:
              event-topic: axs.abyss.result_committed
              required-count: 3
              conditions:
                - { type: event_payload, key: dungeon, operator: equals, value: example }
            """);
        var results = mock(AbyssRunResultAccess.class);
        UUID run = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        long end = System.currentTimeMillis();
        var result = new AbyssRunResultAccess.Result(run, run, run, "abyss", "example", null, "normal", "",
            uuid, end - 10_000, end - 9_000, end, AbyssRunResultAccess.Outcome.COMPLETED, "COMPLETED", List.of(
                new AbyssRunResultAccess.Member(uuid, AbyssRunResultAccess.MemberState.DISCONNECTED, 0, null, end),
                new AbyssRunResultAccess.Member(left, AbyssRunResultAccess.MemberState.LEFT, 0, "LEFT", end)));
        when(results.committedResults("abyss", 0, 50)).thenReturn(
            CompletableFuture.completedFuture(List.of(new AbyssRunResultAccess.Committed(17, result))));
        when(results.committedResults("abyss", 17, 50)).thenReturn(CompletableFuture.completedFuture(List.of()));

        service = new BattlePassService(mock(JavaPlugin.class), config, repository, LOGGER, null);
        service.setAbyssResultProvider(() -> results, "test");
        service.start();
        service.preparationCompleted().toCompletableFuture().get(5,TimeUnit.SECONDS);
        assertTrue(service.catchUpAbyssTasks());
        assertProgress(task, 1);
        assertTrue(repository.loadPlayerTasks(left, SEASON).isEmpty());
        stopService();

        service = new BattlePassService(mock(JavaPlugin.class), config, repository, LOGGER, null);
        service.setAbyssResultProvider(() -> results, "test");
        service.start();
        service.preparationCompleted().toCompletableFuture().get(5,TimeUnit.SECONDS);
        assertTrue(service.catchUpAbyssTasks());
        assertProgress(task, 1);
        verify(results).committedResults("abyss", 0, 50);
        verify(results).committedResults("abyss", 17, 50);
    }

    @Test
    void abyssCheckpointCommitsOnlyTheCompletedPrefixThenRetriesTheRest() throws Exception {
        String task = "abyss-batch";
        saveTask(task, BattlePassTask.TaskCategory.SEASON, 8, 0);
        var config = configure("", """
            abyss-batch:
              event-topic: axs.abyss.result_committed
              required-count: 8
            """);
        repository = spy(repository);
        var writes = new AtomicInteger();
        doAnswer(call -> {
            if (writes.incrementAndGet() == 2) throw new SQLException("failed second result");
            return call.callRealMethod();
        }).when(repository).saveAuthFact(anyString(), any(), anyList(), org.mockito.ArgumentMatchers.anyBoolean());
        var results = mock(AbyssRunResultAccess.class);
        long ended = System.currentTimeMillis();
        var page = new java.util.ArrayList<AbyssRunResultAccess.Committed>();
        for (long sequence = 17; sequence <= 19; sequence++) {
            UUID run = UUID.randomUUID();
            var result = new AbyssRunResultAccess.Result(run, run, run, "abyss", "example", null, "normal", "",
                uuid, ended - 10_000, ended - 9_000, ended, AbyssRunResultAccess.Outcome.COMPLETED, "COMPLETED",
                List.of(new AbyssRunResultAccess.Member(uuid, AbyssRunResultAccess.MemberState.DISCONNECTED, 0, null, ended)));
            page.add(new AbyssRunResultAccess.Committed(sequence, result));
        }
        when(results.committedResults("abyss", 0, 50)).thenReturn(CompletableFuture.completedFuture(List.copyOf(page)));
        when(results.committedResults("abyss", 17, 50)).thenReturn(CompletableFuture.completedFuture(List.copyOf(page.subList(1, 3))));
        when(results.committedResults("abyss", 19, 50)).thenReturn(CompletableFuture.completedFuture(List.of()));
        service = new BattlePassService(mock(JavaPlugin.class), config, repository, LOGGER, null);
        service.setAbyssResultProvider(() -> results, "test");
        service.start();
        service.preparationCompleted().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertFalse(service.catchUpAbyssTasks());
        assertProgress(task, 1);
        verify(repository).advanceAbyssCursor(anyString(), eq(17L));
        assertTrue(service.catchUpAbyssTasks());
        assertProgress(task, 3);
        verify(repository).advanceAbyssCursor(anyString(), eq(19L));
        assertTrue(service.catchUpAbyssTasks());
        verify(results).committedResults("abyss", 19, 50);
        verify(repository, times(2)).advanceAbyssCursor(anyString(), anyLong());
    }

    @Test
    void nearbyPickupCountsDeliveredQuantityAndIgnoresOtherSources() throws Exception {
        String task = "nearby";
        saveTask(task, BattlePassTask.TaskCategory.SEASON, 100, 0);
        startService(configure("", """
            nearby:
              event-topic: axs.pickup.item_pickup
              required-count: 100
              conditions:
                - { type: event_payload, key: source, operator: equals, value: nearby }
              increment-strategy: { type: payload_value, payload-key: amount }
            """));
        bus.publish("axs.pickup.item_pickup", player, Map.of("amount", "12"));
        bus.publish("axs.pickup.item_pickup", player, Map.of("source", "nearby", "amount", "34"));
        runAsyncJobs();
        assertProgress(task, 34);
    }

    private void writeCurrencyReward(int amount) throws Exception {
        Files.writeString(data.resolve("rewards.yml"), """
            first:
              level: 1
              free:
                type: currency
                currency: money
                amount: %d
            """.formatted(amount));
    }

    private BattlePassModuleConfiguration configure(String daily, String seasonal) throws Exception {
        Path taskDirectory = Files.createDirectories(data.resolve("custom-tasks"));
        Files.writeString(taskDirectory.resolve("daily.yml"), daily);
        Files.writeString(taskDirectory.resolve("weekly.yml"), "");
        Files.writeString(taskDirectory.resolve("season.yml"), seasonal);
        var yaml = new YamlConfiguration();
        yaml.set("season.season-id", SEASON);
        yaml.set("season.start-date", today.minusDays(7).toString());
        yaml.set("season.end-date", today.plusDays(7).toString());
        yaml.set("tasks.tasks-directory", "custom-tasks");
        yaml.set("tasks.daily-count", 1);
        yaml.set("tasks.weekly-count", 0);
        return BattlePassModuleConfiguration.load(yaml, data.toFile(), LOGGER, new AriaBridge(){
            @Override public boolean available(){return true;}
            @Override public String version(){return "constant-test-curve";}
            @Override public Object eval(String code,Map<String,Object> bindings){return 1000;}
        });
    }

    private static String presenceTask(String taskId, int target) {
        return """
            %s:
              event-topic: axs.auth.presence_day_started
              required-count: %d
              bukkit-event:
                event-class: org.bukkit.event.player.PlayerJoinEvent
              limits:
                max-per-day: 1
            """.formatted(taskId, target);
    }

    private BattlePassModuleConfiguration withCurveBridge(BattlePassModuleConfiguration source,AriaBridge bridge) {
        var season=source.season();
        var replacement=new BattlePassModuleConfiguration.BattlePassSeasonConfiguration(season.seasonId(),season.displayName(),season.maxLevel(),
            new XpCurve(season.xpCurve().breakpoints(),bridge),season.startDate(),season.endDate());
        return new BattlePassModuleConfiguration(source.storage(),replacement,source.tasks(),source.rewardsFile(),source.rewards(),source.seasonRewardsFile(),source.seasonRewards(),source.ui());
    }

    private void startService(BattlePassModuleConfiguration configuration) throws Exception {
        startService(configuration, null);
    }

    private void startService(BattlePassModuleConfiguration configuration, PendingRewardService pending) throws Exception {
        service = new BattlePassService(mock(JavaPlugin.class), configuration, repository, LOGGER, pending);
        service.setEventBusProvider(() -> bus);
        service.start();
        service.preparationCompleted().toCompletableFuture().get(5,TimeUnit.SECONDS);
    }

    private void stopService() throws Exception {
        if (service == null) return;
        service.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        service = null;
    }

    private void saveTask(String id, BattlePassTask.TaskCategory category, int target, int progress) throws Exception {
        var instance = new PlayerTaskInstance(uuid, "existing-" + id, id, category, target, progress, false, today, 0);
        repository.savePlayerTasks(uuid, SEASON, List.of(instance));
    }

    private void assertProgress(String id, int expected) {
        var task = service.getTaskInstances(player).stream().filter(item -> item.templateId().equals(id))
            .findFirst().orElseThrow();
        assertEquals("existing-" + id, task.instanceId());
        assertEquals(expected, task.currentProgress(), id);
    }

    private void runAsyncJobs() {
        while (!asyncJobs.isEmpty()) asyncJobs.remove().run();
    }

    private static Map<String, String> fact(String eventId, LocalDate date) {
        return Map.of("event_id", eventId, "business_date", date.toString(), "provider", "server");
    }

    private void confirm(Map<String, String> payload) throws Exception {
        assertTrue(bus.publishConfirmed(BattlePassTask.PRESENCE_TOPIC, player, payload)
            .toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    private void awaitStoredProgress(String id, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var tasks = repository.loadPlayerTasks(uuid, SEASON);
            if (tasks.stream().anyMatch(task -> id.equals(task.templateId()) && task.currentProgress() == expected)) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No persisted progress for " + id);
    }
}
