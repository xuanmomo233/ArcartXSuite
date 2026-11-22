package xuanmo.arcartxsuite.entitytracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.config.CrossServerRankingSettings;
import xuanmo.arcartxsuite.entitytracker.dao.CrossServerBossRankingDao;
import xuanmo.arcartxsuite.entitytracker.dao.CrossServerBossRankingDao.Snapshot;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class CrossServerRankingCacheServiceTest {
    @TempDir Path folder;

    @Test void publishingFailureLeavesAllPreviousRankingsVisible() throws Exception {
        var dao = new CrossServerBossRankingDao(database(), null);
        dao.replaceAll(List.of(new Snapshot("best_damage", null, "[1]"),
            new Snapshot("boss_damage", "dragon", "[2]")), null);
        assertThrows(SQLException.class, () -> dao.replaceAll(List.of(new Snapshot("best_damage", null, "[3]"),
            new Snapshot("boss_damage", "dragon", null)), null));
        assertEquals("[1]", dao.findRankingData("best_damage", null).orElseThrow());
        assertEquals("[2]", dao.findRankingData("boss_damage", "dragon").orElseThrow());
    }

    @Test void periodicAndManualRefreshesCoalesceAndChangesDuringTheQueryGetOneFollowUp() throws Exception {
        var database = database();
        var storage = mock(DataSource.class);
        var serviceRef = new AtomicReference<CrossServerRankingCacheService>();
        var connections = new AtomicInteger();
        when(storage.getConnection()).thenAnswer(call -> {
            if (connections.incrementAndGet() == 1) {
                for (int i = 0; i < 20; i++) serviceRef.get().requestRefresh();
            }
            return database.getConnection();
        });
        var scheduled = new ArrayDeque<Runnable>();
        var periodic = new AtomicReference<Runnable>();
        var timer = mock(BukkitTask.class);
        var oneShot = mock(BukkitTask.class);
        var scheduler = mock(BukkitScheduler.class);
        var plugin = mock(JavaPlugin.class);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(1200L), eq(1200L))).thenAnswer(call -> {
            periodic.set(call.getArgument(1));
            return timer;
        });
        when(scheduler.runTaskLaterAsynchronously(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return oneShot;
        });
        var service = new CrossServerRankingCacheService(plugin, Logger.getAnonymousLogger(),
            new CrossServerRankingSettings(true, 60, List.of("best_damage"), 50), storage, () -> null, () -> "local");
        serviceRef.set(service);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.start();
            periodic.get().run();
            for (int i = 0; i < 50; i++) service.requestRefresh();
            assertEquals(1, scheduled.size());

            scheduled.removeFirst().run();
            assertEquals(2, connections.get());
            assertEquals(1, scheduled.size());
            scheduled.removeFirst().run();
            assertEquals(4, connections.get());
            assertTrue(scheduled.isEmpty());

            service.requestRefresh();
            service.shutdown();
            scheduled.removeFirst().run();
            service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(timer).cancel();
            verify(oneShot).cancel();
            assertEquals(4, connections.get());
        }
    }

    @Test void eventRefreshReadsOnlyItsRankingTypesAndTheChangedBoss() throws Exception {
        var database = database();
        var dao = new CrossServerBossRankingDao(database, null);
        dao.replaceAll(List.of(new Snapshot("kills", null, "[12]"),
            new Snapshot("boss_damage", "beta", "[34]")), null);
        var storage = mock(DataSource.class);
        var connections = new AtomicInteger();
        when(storage.getConnection()).thenAnswer(call -> {
            connections.incrementAndGet();
            return database.getConnection();
        });
        var config = mock(PluginConfiguration.class);
        var alpha = mock(BossDefinition.class);
        var beta = mock(BossDefinition.class);
        when(alpha.enabled()).thenReturn(true);
        when(beta.enabled()).thenReturn(true);
        when(alpha.mythicMobId()).thenReturn("alpha");
        when(beta.mythicMobId()).thenReturn("beta");
        when(config.findBoss("alpha")).thenReturn(alpha);
        when(config.bosses()).thenReturn(Map.of("alpha", alpha, "beta", beta));
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        var scheduled = new ArrayDeque<Runnable>();
        when(scheduler.runTaskLaterAsynchronously(eq(plugin), any(Runnable.class), eq(20L))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var service = new CrossServerRankingCacheService(plugin, Logger.getAnonymousLogger(),
            new CrossServerRankingSettings(true, 60,
                List.of("best_damage", "boss_damage", "server", "kills", "participate"), 50),
            storage, () -> config, () -> "local");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.requestDamageRefresh("alpha");
            service.requestDamageRefresh("alpha");
            assertEquals(1, scheduled.size());
            scheduled.removeFirst().run();
            assertEquals(4, connections.get()); // Three affected boards and one batch write.
            assertEquals("[]", dao.findRankingData("boss_damage", "alpha").orElseThrow());
            assertEquals("[34]", dao.findRankingData("boss_damage", "beta").orElseThrow());
            assertEquals("[12]", dao.findRankingData("kills", null).orElseThrow());

            insertActivity(database);
            service.requestKillRefresh();
            scheduled.removeFirst().run();
            assertEquals(6, connections.get()); // One shared history read and one batch write.
            var kills = JsonParser.parseString(dao.findRankingData("kills", null).orElseThrow()).getAsJsonArray();
            var participation = JsonParser.parseString(dao.findRankingData("participate", null).orElseThrow()).getAsJsonArray();
            assertEquals(2, kills.size());
            assertEquals("Alice", kills.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(3, kills.get(0).getAsJsonObject().get("score").getAsInt());
            assertEquals(2, participation.size());
            assertEquals("Bob", participation.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(2, participation.get(0).getAsJsonObject().get("score").getAsInt());
            assertEquals(1, participation.get(1).getAsJsonObject().get("score").getAsInt());
            assertEquals("[34]", dao.findRankingData("boss_damage", "beta").orElseThrow());

            service.requestDamageRefresh("alpha");
            service.requestRefresh();
            scheduled.removeFirst().run();
            assertEquals(12, connections.get()); // The full pass supersedes the pending single-Boss refresh.
            assertEquals("[]", dao.findRankingData("boss_damage", "beta").orElseThrow());
            service.shutdown();
        }
    }

    private static void insertActivity(DataSource database) throws SQLException {
        var now = LocalDateTime.now();
        String alice = "[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alice\"}]";
        String bob = "[{\"uuid\":\"00000000-0000-0000-0000-000000000002\",\"name\":\"Bob\"}]";
        var bossIds = List.of("alpha", "alpha", "alpha", "alpha", "beta", "old");
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO boss_kill_records(boss_id, kill_time, participants) VALUES (?, ?, ?)")) {
            for (int index = 0; index < bossIds.size(); index++) {
                statement.setString(1, bossIds.get(index));
                statement.setTimestamp(2, Timestamp.valueOf(now.minusDays(index == 5 ? 31 : 1)));
                statement.setString(3, index == 3 || index == 4 ? bob : alice);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private DataSource database() throws SQLException {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve("rankings.db"));
        new JdbcEntityTrackerRepository(source, StorageDescriptor.sqlite("rankings.db"), folder.toFile(),
            Logger.getAnonymousLogger()).initialize();
        return source;
    }
}
