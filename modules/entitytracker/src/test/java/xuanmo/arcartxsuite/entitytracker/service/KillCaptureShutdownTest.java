package xuanmo.arcartxsuite.entitytracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.entitytracker.boss.config.*;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.*;
import xuanmo.arcartxsuite.entitytracker.config.*;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class KillCaptureShutdownTest {
    @TempDir Path directory;
    SQLiteDataSource source;
    JavaPlugin plugin;
    Server previous;
    Field serverField;
    final List<BossKillRecordingService> services=new ArrayList<>();
    final List<Runnable> delayed=new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previous=(Server)serverField.get(null);
        var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);plugin=mock(JavaPlugin.class);
        when(server.getScheduler()).thenReturn(scheduler);when(server.getOnlinePlayers()).thenReturn(List.of());
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(call->{delayed.add(call.getArgument(1));return mock(BukkitTask.class);});
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        serverField.set(null,server);
        source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+directory.resolve("capture.sqlite"));
        new JdbcEntityTrackerRepository(source,StorageDescriptor.sqlite("capture.sqlite"),directory.toFile(),java.util.logging.Logger.getAnonymousLogger()).initialize();
    }

    @AfterEach void teardown() throws Exception {
        try{for(var service:services){service.shutdown();service.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);}}
        finally{serverField.set(null,previous);}
    }

    @Test void finalTwoTickCapturesDrainOffMainBehindAnAlreadyRunningQuery() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        var primary=Thread.currentThread();var wrongThread=new java.util.concurrent.atomic.AtomicBoolean();
        DataSource slow=mock(DataSource.class);
        when(slow.getConnection()).thenAnswer(call->{
            if(Thread.currentThread()==primary)wrongThread.set(true);
            if(first.compareAndSet(true,false)){entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}
            return source.getConnection();
        });
        var service=create(slow,false,null);service.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        service.purgeOldRecords();
        var tracked = spy(settlement(0));
        doAnswer(call -> {
            if (Thread.currentThread() == primary) wrongThread.set(true);
            return call.callRealMethod();
        }).when(tracked).entriesByPlayer();
        for(int i=0;i<32;i++){var entity=entity();service.recordBossDeath(session(entity),entity,i==0?tracked:settlement(i),null);}
        assertEquals(1,delayed.size(),"One capture drain for the whole tick batch");
        service.shutdown();assertFalse(service.databaseStopped().toCompletableFuture().isDone());assertFalse(wrongThread.get());
        release.countDown();service.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
        delayed.forEach(Runnable::run);
        assertEquals(32,count("boss_kill_records"));assertEquals(32,count("entitytracker_kill_receipts"));assertFalse(wrongThread.get());
        verify(tracked, atLeastOnce()).entriesByPlayer();
        try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_kill_retention'")) {
            assertTrue(rows.next());assertEquals(0,rows.getInt(1),"Unstarted optional cleanup must not delay retiring business writes");
        }
    }

    @Test void failedSqlIsJournaledOffMainAndReplayedAfterRestart() throws Exception {
        DataSource offline=mock(DataSource.class);when(offline.getConnection()).thenThrow(new SQLException("offline"));
        var first=create(offline,false,null);var entity=entity();first.recordBossDeath(session(entity),entity,settlement(1),null);
        first.shutdown();first.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
        try(var files=Files.list(directory.resolve("outbox"))){assertEquals(1,files.count());}
        var second=create(source,false,null);second.start();second.shutdown();second.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
        assertEquals(1,count("boss_kill_records"));
        try(var files=Files.list(directory.resolve("outbox"))){assertEquals(0,files.count());}
    }

    @Test void automaticCaptureOwnsOnlyTheOriginalSerializedItemsAndManualModeKeepsGroundLoot() throws Exception {
        var host=mock(PendingRewardService.class);when(host.savePendingBatchConfirmed(anyString(),anyList())).thenReturn(true);
        var service=create(source,true,host);var entity=entity();service.recordBossDeath(session(entity),entity,settlement(2),null);
        var item=mock(ItemStack.class);when(item.getType()).thenReturn(Material.DIAMOND);when(item.getAmount()).thenReturn(3);
        var loot=new ArrayList<ItemStack>(List.of(item));var event=new EntityDeathEvent(entity,loot);
        try(var serializer=mockStatic(ItemSerializer.class)) {
            serializer.when(()->ItemSerializer.serialize(item)).thenReturn(new byte[]{1,2,3,4});
            service.onEntityDeath(event);
        }
        assertTrue(loot.isEmpty());
        var journal = directory.resolve("outbox").resolve(settlement(2).entityUuid() + ".drops.json");
        var stored = com.google.gson.JsonParser.parseString(Files.readString(journal)).getAsJsonObject();
        assertEquals("AQIDBA==", stored.getAsJsonArray("drops").get(0).getAsJsonObject().get("itemData").getAsString());
        assertEquals(0, count("boss_kill_records"));
        service.shutdown();service.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
        assertFalse(Files.exists(journal));
        var batch=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(host).savePendingBatchConfirmed(anyString(),batch.capture());
        var reward=(PendingRewardService.PendingReward)batch.getValue().get(0);
        assertTrue(reward.payload().contains("AQIDBA=="));assertEquals(1,count("drop_allocation_records"));
        var recorder=create(source,false,null);var other=entity();recorder.recordBossDeath(session(other),other,settlement(3),null);
        var ground=new ArrayList<ItemStack>(List.of(item));recorder.onEntityDeath(new EntityDeathEvent(other,ground));
        assertEquals(1,ground.size());
    }

    @Test void autoDropJournalReplaysBeforeAnyOriginalDatabaseWorkStarts() throws Exception {
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        var unavailable = mock(DataSource.class);
        when(unavailable.getConnection()).thenThrow(new SQLException("old process storage unavailable"));
        var first = create(unavailable, true, host);
        var boss = entity();
        var settled = settlement(4);
        first.recordBossDeath(session(boss), boss, settled, null);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(3);
        var drops = new ArrayList<>(List.of(item));
        try (var serializer = mockStatic(ItemSerializer.class)) {
            serializer.when(() -> ItemSerializer.serialize(item)).thenReturn(new byte[]{1, 2, 3});
            first.onEntityDeath(new EntityDeathEvent(boss, drops));
        }
        assertTrue(drops.isEmpty());
        var journal = directory.resolve("outbox").resolve(settled.entityUuid() + ".drops.json");
        assertTrue(Files.exists(journal));
        assertEquals(0, count("boss_kill_records"));
        verifyNoInteractions(host);

        var replacement = create(source, true, host);
        replacement.start();
        replacement.shutdown();
        replacement.databaseStopped().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, count("boss_kill_records"));
        assertEquals(1, count("drop_allocation_records"));
        verify(host).savePendingBatchConfirmed(startsWith("kill:" + settled.entityUuid()), anyList());
        assertFalse(Files.exists(journal));
    }

    @Test void failedAutoDropJournalKeepsTheVanillaDropsAndDoesNotSendASecondCopy() throws Exception {
        Files.writeString(directory.resolve("outbox"), "fixture prevents journal creation");
        var host = mock(PendingRewardService.class);
        var service = create(source, true, host);
        var boss = entity();
        service.recordBossDeath(session(boss), boss, settlement(5), null);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(2);
        var drops = new ArrayList<>(List.of(item));
        try (var serializer = mockStatic(ItemSerializer.class)) {
            serializer.when(() -> ItemSerializer.serialize(item)).thenReturn(new byte[]{4, 5, 6});
            service.onEntityDeath(new EntityDeathEvent(boss, drops));
        }
        assertEquals(List.of(item), drops);
        service.shutdown();
        service.databaseStopped().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, count("boss_kill_records"));
        verifyNoInteractions(host);
    }

    @Test void resetUiSequenceDoesNotSuppressANewBossAndReplayedEntityRemainsDeduplicated() throws Exception {
        var first=settlement(20);var next=settlement(21);
        next=new BossDamageSettlementRecord(first.settlementId(),next.settledAtMillis(),next.mythicMobId(),next.bossDisplayName(),
            next.entityUuid(),next.participantCount(),next.trackedPlayerCount(),next.totalDamage(),next.rewardsSettings(),next.trackedEntries(),next.entriesByPlayer());
        for(var record:List.of(first,next,first,next)) {
            var service=create(source,false,null);var entity=entity();service.recordBossDeath(session(entity),entity,record,null);
            service.shutdown();service.databaseStopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
        }
        assertEquals(2,count("boss_kill_records"));assertEquals(2,count("entitytracker_kill_receipts"));
    }

    @Test void olderRejectedPlansDoNotStarveLaterRecipientsOrWakeSpectators() throws Exception {
        UUID winnerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        var gson = new com.google.gson.Gson();
        try (var connection = source.getConnection(); var insert = connection.prepareStatement(
            "INSERT INTO entitytracker_kill_receipts(kill_key,kill_id,delivery_plan,handed_off) VALUES (?,?,?,0)")) {
            for (int id = 1; id <= 65; id++) {
                boolean later = id == 65;
                insert.setString(1, later ? "accepted" : "blocked-" + id);
                insert.setInt(2, id);
                insert.setString(3, gson.toJson(List.of(
                    new KillAllocation.Delivery(later ? winnerId : blockedId, "Player", "AQID", "Diamond", "roll", 10))));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        var scheduler = Bukkit.getScheduler();
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var server = Bukkit.getServer();
        var winner = mock(Player.class);
        var spectator = mock(Player.class);
        when(server.getPlayer(winnerId)).thenReturn(winner);
        var host = mock(PendingRewardService.class);
        when(host.savePendingBatchConfirmed(startsWith("kill:accepted:"), anyList())).thenReturn(true);
        var service = create(source, false, host);
        service.start();

        var startup = callbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(startup);
        startup.run();
        doReturn(List.of(winner, spectator)).when(server).getOnlinePlayers();
        var wake = callbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(wake, "The accepted record after the first recovery page was not reached");
        wake.run();
        verify(host, times(64)).savePendingBatchConfirmed(startsWith("kill:blocked-"), anyList());
        verify(host).savePendingBatchConfirmed(eq("kill:accepted:" + winnerId), anyList());
        verify(host).flushPlayer(winner);
        verify(host, never()).flushPlayer(spectator);
        verify(server).getOnlinePlayers();
    }

    private BossKillRecordingService create(DataSource data,boolean automatic,PendingRewardService rewards) {
        var allocation=automatic?new DropAllocationSettings(true,"roll",60,DropAllocationSettings.DkpSettings.disabled(),DropAllocationSettings.RollSettings.defaults(),DropAllocationSettings.PrioritySettings.defaults()):DropAllocationSettings.disabled();
        var settings=new EntityTrackerNewFeaturesSettings(new DropRecordingSettings(true,30,true,300),allocation,CrossServerRankingSettings.disabled());
        var service=new BossKillRecordingService(plugin,java.util.logging.Logger.getAnonymousLogger(),settings,data,rewards,null,null,()->"test",directory.resolve("outbox"),null);
        services.add(service);return service;
    }
    LivingEntity entity(){var entity=mock(LivingEntity.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());return entity;}
    BossSession session(LivingEntity entity){return new BossSession(entity.getUniqueId(),new BossDefinition("boss",true,0,32,"","","","","",BossDamageRankingSettings.defaults()),"boss","Boss",1);}
    BossDamageSettlementRecord settlement(int i){
        var uuid=UUID.randomUUID();var entry=new BossDamageSettlementEntry(uuid,"Player",1,true,100,100,0,false,"",List.of());
        return new BossDamageSettlementRecord("capture-"+i,System.currentTimeMillis(),"boss","Boss",UUID.nameUUIDFromBytes(("entity-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),1,1,100,BossDamageRankingRewardsSettings.defaults(),List.of(entry),Map.of(uuid,entry));
    }
    int count(String table)throws Exception{try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rows.next());return rows.getInt(1);}}
}
