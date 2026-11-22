package xuanmo.arcartxsuite.entitytracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.dao.PeriodRankingDao;
import xuanmo.arcartxsuite.entitytracker.dao.RankingRewardRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import xuanmo.arcartxsuite.entitytracker.reward.BossRewardItems;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionExecutor;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionResult;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class RankingRewardShutdownTest {
    @TempDir Path directory;
    final BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    SQLiteDataSource source;
    JavaPlugin plugin;
    RewardActionExecutor effects;
    BossRewardItems itemRewards;
    AxsMailService mail;
    RankingRewardService service;
    Server previous;
    Field serverField;
    int rewardId;

    @BeforeEach void setUp() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previous = (Server) serverField.get(null);
        Server server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        plugin = mock(JavaPlugin.class);
        effects = mock(RewardActionExecutor.class);
        itemRewards = mock(BossRewardItems.class);
        mail = mock(AxsMailService.class);
        Thread owner = Thread.currentThread();
        when(server.isPrimaryThread()).thenAnswer(call -> Thread.currentThread() == owner);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getOfflinePlayer(any(UUID.class))).thenReturn(mock(OfflinePlayer.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            main.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        serverField.set(null, server);
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("reward.sqlite"));
        new JdbcEntityTrackerRepository(source, StorageDescriptor.sqlite("reward.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger()).initialize();
        var records = new RankingRewardRecordDao(source, plugin);
        var periods = new PeriodRankingDao(source, records);
        periods.recordScores(kill(UUID.randomUUID(), 1000));
        periods.freeze("boss", "weekly", LocalDateTime.of(2026, 11, 1, 0, 0), 0, 2000, 1, standing -> {
            var record = new RankingRewardRecord(-1, "weekly", "boss_damage",
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 8, 0, 0),
                standing.playerId(), standing.playerName(), standing.rank(), standing.score());
            record.setBossId("boss");
            record.setRewardPlan("actions:\n  - type: command\n    command: say reward\n");
            return record;
        });
        rewardId = records.pendingPeriod("boss", "weekly", 0).get(0).getId();
        service = create(source);
    }

    @AfterEach void tearDown() throws Exception {
        if (service != null) {
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        serverField.set(null, previous);
    }

    @Test void queuedUnstartedEffectReturnsToPendingWithoutWaitingForMainThread() throws Exception {
        var result = service.retryDistribution(rewardId);
        Runnable queued = main.poll(5, TimeUnit.SECONDS);
        assertNotNull(queued);
        assertEquals("issuing", record().getStatus());
        service.shutdown();
        result.get(5, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        queued.run();
        assertEquals("pending", record().getStatus());
        assertEquals(0, record().getNextAction());
        assertNull(record().getClaimAttempt());
        verifyNoInteractions(effects);
    }

    @Test void startedEffectCompletesReceiptBeforeRetiringAndCannotRunTwice() throws Exception {
        when(effects.executeAction(any(), any(), anyMap())).thenAnswer(call -> {
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            return RewardActionResult.ok("command", "ok");
        });
        var result = service.retryDistribution(rewardId);
        Runnable queued = main.poll(5, TimeUnit.SECONDS);
        assertNotNull(queued);
        queued.run();
        result.get(5, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        queued.run();
        assertEquals("success", record().getStatus());
        assertEquals(1, record().getNextAction());
        verify(effects, times(1)).executeAction(any(), any(), anyMap());
    }

    @Test void runningSqlAndAcceptedScoresDrainAfterShutdownWithoutFutureCancellation() throws Exception {
        service.shutdown();
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        DataSource delayed = mock(DataSource.class);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(delayed.getConnection()).thenAnswer(call -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return source.getConnection();
        });
        service = create(delayed);
        var read = service.getRewardRecords(null, null, null);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 32; i++) service.recordPeriodScores(kill(UUID.randomUUID(), 3000 + i));
        service.shutdown();
        var detached = service.databaseStopped().toCompletableFuture();
        detached.cancel(false);
        assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        release.countDown();
        assertEquals(1, read.get(5, TimeUnit.SECONDS).size());
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        try (var conn = source.getConnection(); var statement = conn.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM entitytracker_period_scores")) {
            assertTrue(rows.next()); assertEquals(33, rows.getInt(1));
        }
        assertThrows(ExecutionException.class,
            () -> service.getRewardRecords(null, null, null).get(5, TimeUnit.SECONDS));
    }

    @Test void lateUiCallbackCannotUseStoppedInstance() throws Exception {
        var called = new java.util.concurrent.atomic.AtomicInteger();
        service.onMainIfActive(called::incrementAndGet);
        Runnable queued = main.poll(5, TimeUnit.SECONDS);
        assertNotNull(queued);
        service.shutdown();
        queued.run();
        service.onMainIfActive(called::incrementAndGet);
        assertEquals(0, called.get());
        assertTrue(main.isEmpty());
    }

    @Test void committedMailResumesWithItsOriginalRequestWhenTheRankingReceiptFails() throws Exception {
        configureReward("actions:\n  - type: mail\n    preset-id: weekly\n", true);
        when(mail.usesSharedHostStorage()).thenReturn(true);
        try (var conn = source.getConnection(); var sql = conn.createStatement()) {
            sql.execute("""
                CREATE TRIGGER reject_ranking_receipt BEFORE UPDATE ON ranking_reward_records
                WHEN NEW.status = 'success' BEGIN SELECT RAISE(ABORT, 'ranking receipt lost'); END
                """);
        }
        var accepted = new LinkedBlockingQueue<String>();
        var firstReceipt = new CompletableFuture<AxsDeliveryReceipt>();
        Thread primary = Thread.currentThread();
        when(mail.sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), anyString())).thenAnswer(call -> {
            assertNotSame(primary, Thread.currentThread());
            accepted.add(call.getArgument(3));
            return firstReceipt;
        });

        var first = service.retryDistribution(rewardId);
        String requestKey = accepted.poll(5, TimeUnit.SECONDS);
        assertNotNull(requestKey);
        assertTrue(main.isEmpty());
        var inflight = record();
        assertEquals("mail_issuing", inflight.getStatus());
        assertEquals("period-ranking-mail:" + inflight.getClaimAttempt(), requestKey);
        service.shutdown();
        assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        firstReceipt.complete(AxsDeliveryReceipt.ok(31, 1));
        first.get(5, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("mail_issuing", record().getStatus());
        assertEquals(1, new RankingRewardRecordDao(source, plugin).pendingPeriod("boss", "weekly", 0).size());

        try (var conn = source.getConnection(); var sql = conn.createStatement()) {
            sql.execute("DROP TRIGGER reject_ranking_receipt");
        }
        service = create(source, true);
        when(mail.usesSharedHostStorage()).thenReturn(false);
        service.retryDistribution(rewardId).get(5, TimeUnit.SECONDS);
        assertEquals(inflight.getClaimAttempt(), record().getClaimAttempt());
        verify(mail).sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), eq(requestKey));

        when(mail.usesSharedHostStorage()).thenReturn(true);
        when(mail.sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), eq(requestKey)))
            .thenReturn(CompletableFuture.completedFuture(AxsDeliveryReceipt.duplicate(31)));
        service.retryDistribution(rewardId).get(5, TimeUnit.SECONDS);
        assertEquals("success", record().getStatus());
        assertEquals(1, record().getNextAction());
        verify(mail, times(2)).sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), eq(requestKey));
        verifyNoInteractions(effects);
        assertTrue(main.isEmpty());
    }

    @Test void separateMailStorageUsesManualReviewForAnUnconfirmedSend() throws Exception {
        configureReward("actions:\n  - type: mail\n    preset-id: weekly\n", true);
        when(mail.sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("receipt lost")));
        service.retryDistribution(rewardId).get(5, TimeUnit.SECONDS);
        assertEquals("review", record().getStatus());
        service.retryDistribution(rewardId).get(5, TimeUnit.SECONDS);
        verify(mail).sendPresetDurable(any(UUID.class), eq("weekly"), eq("entitytracker"), anyString());
        verifyNoInteractions(effects);
        assertTrue(main.isEmpty());
    }

    @Test void configuredItemOverflowCommitsOffMainBeforeAdvancingTheRankingStep() throws Exception {
        configureReward("actions:\n  - type: item\n    id: DIAMOND\n", false);
        UUID recipient = UUID.fromString(record().getPlayerUuid());
        var player = mock(Player.class);
        when(Bukkit.getServer().getPlayer(recipient)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        var frozen = PendingReward.itemSerialized(recipient, "entitytracker", "AQID", "ranking:boss");
        Thread primary = Thread.currentThread();
        var started = new LinkedBlockingQueue<String>();
        var release = new CountDownLatch(1);
        when(itemRewards.prepareOnline(eq(player), any(), eq("ranking:boss"))).thenAnswer(call -> {
            assertSame(primary, Thread.currentThread());
            return new BossRewardItems.Prepared(null, frozen);
        });
        when(itemRewards.persist(anyString(), eq(frozen))).thenAnswer(call -> {
            assertNotSame(primary, Thread.currentThread());
            started.add(call.getArgument(0));
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return RewardActionResult.ok("item", "stored");
        });
        try {
            var reply = service.retryDistribution(rewardId);
            for (int callback = 0; callback < 2; callback++) {
                var task = main.poll(5, TimeUnit.SECONDS);
                assertNotNull(task);
                task.run();
            }
            var request = started.poll(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("issuing", record().getStatus());
            assertEquals("period-ranking-item:" + record().getClaimAttempt(), request);
            assertFalse(reply.isDone());
            service.shutdown();
            release.countDown();
            reply.get(5, TimeUnit.SECONDS);
            assertEquals("success", record().getStatus());
            assertEquals(1, record().getNextAction());
            verifyNoInteractions(effects, mail);
        } finally { release.countDown(); }
    }

    @Test void togglingARegisteredRewardPublishesTheActualPersistedState() throws Exception {
        service.shutdown();
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        var bossFile = directory.resolve("boss.yml");
        Files.writeString(bossFile, """
            enabled: true
            damage-ranking:
              enabled: true
              ranking-rewards:
                weekly:
                  enabled: true
                  ranks:
                    '1':
                      actions:
                        - type: command
                          command: say fixture
            """);
        var configuration = PluginConfiguration.from(null, null, directory.toFile());
        var loadingThread = new AtomicReference<Thread>();
        service = new RankingRewardService(source, effects, itemRewards, () -> mail, false, plugin, Logger.getAnonymousLogger(),
            () -> { loadingThread.set(Thread.currentThread()); return configuration; }, directory.toFile());
        String enabledPath = "damage-ranking.ranking-rewards.weekly.enabled";
        assertEquals(Boolean.TRUE, service.getConfigsForPeriod("weekly").get(0).get("enabled"));
        assertTrue(service.toggleConfig("boss-1", "weekly").get(5, TimeUnit.SECONDS).contains("已停用"));
        assertNotSame(Thread.currentThread(), loadingThread.get());
        assertFalse(YamlConfiguration.loadConfiguration(bossFile.toFile()).getBoolean(enabledPath, true));
        assertEquals(Boolean.FALSE, service.getConfigsForPeriod("weekly").get(0).get("enabled"));
        assertTrue(service.toggleConfig("boss-2", "weekly").get(5, TimeUnit.SECONDS).contains("无效"));
        assertEquals(Boolean.FALSE, service.getConfigsForPeriod("weekly").get(0).get("enabled"));
    }

    private void configureReward(String plan, boolean sharedRankingStorage) throws Exception {
        service.shutdown();
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        try (var conn = source.getConnection(); var update = conn.prepareStatement(
                "UPDATE ranking_reward_records SET reward_plan=? WHERE id=?")) {
            update.setString(1, plan);
            update.setInt(2, rewardId);
            update.executeUpdate();
        }
        service = create(source, sharedRankingStorage);
    }

    private RankingRewardService create(DataSource data) {
        return create(data, false);
    }

    private RankingRewardService create(DataSource data, boolean sharedRankingStorage) {
        return new RankingRewardService(data, effects, itemRewards, () -> mail, sharedRankingStorage, plugin, Logger.getAnonymousLogger(),
            () -> null, directory.toFile());
    }
    private RankingRewardRecord record() throws Exception {
        return new RankingRewardRecordDao(source, plugin).findById(rewardId).orElseThrow();
    }
    private static BossDamageSettlementRecord kill(UUID player, long time) {
        var entry = new BossDamageSettlementEntry(player, "Player", 1, true, 100, 100, 0, false, "", List.of());
        return new BossDamageSettlementRecord(UUID.randomUUID().toString(), time, "boss", "Boss",
            UUID.randomUUID().toString(), 1, 1, 100, BossDamageRankingRewardsSettings.defaults(),
            List.of(entry), Map.of(player, entry));
    }
}
