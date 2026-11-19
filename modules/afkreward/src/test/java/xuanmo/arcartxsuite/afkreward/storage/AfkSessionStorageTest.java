package xuanmo.arcartxsuite.afkreward.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.AreaStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.QuitCheckpoint;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.SessionRecord;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class AfkSessionStorageTest {
    @Test
    void delayedPeriodicCheckpointCannotRewindOrResurrectManualSession(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("afk.db");
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("afk.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        UUID id = UUID.randomUUID();
        var current = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 60, 10, 1, 2, "2026-11-14",
            100, 0.35f, true);
        var later = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 90, 10, 1, 2, "2026-11-14",
            130, 0.35f, true);
        var replacement = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 0, 20, 1, 2, "2026-11-14",
            130, 0.35f, true);

        repository.saveSession(current);
        repository.saveStatsBatch(Map.of(), List.of(later));
        assertEquals(later, repository.loadSession(id));
        repository.saveStatsBatch(Map.of(), List.of(current));
        assertEquals(later, repository.loadSession(id));

        assertTrue(repository.beginManualSettlement(later, UUID.randomUUID()));
        repository.saveStatsBatch(Map.of(), List.of(later));
        assertNull(repository.loadSession(id));
        repository.saveSession(replacement);
        repository.saveStatsBatch(Map.of(), List.of(later));
        assertEquals(replacement, repository.loadSession(id));
    }

    @Test
    void deferredSettlementRetryCannotReleaseANewerClaim(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("afk.db");
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("afk.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var session = new SessionRecord(player, "Steve", "lounge", "lounge", "MANUAL",
            60, 10, 0, 0, "", 60, 0.35f, true);
        repository.startManualSession(session);
        assertTrue(repository.beginManualSettlement(session, first));
        repository.restoreUnstartedManualSettlement(session, first);
        assertEquals(session, repository.loadSession(player));
        assertTrue(repository.beginManualSettlement(session, second));
        repository.restoreUnstartedManualSettlement(session, first);
        assertNull(repository.loadSession(player));
        assertEquals("DISPATCHING", repository.findManualSettlementsForReview(25).get(0).status());
        repository.completeManualSettlement(session, 1);
        assertTrue(repository.findManualSettlementsForReview(25).isEmpty());
    }

    @Test
    void delayedSqliteQuitCannotRewindElapsedCountersOrTheCurrentAreaDay(@TempDir Path directory) throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("afk.db")));
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("afk.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        UUID id = UUID.randomUUID();
        var current = new PlayerStats("Steve", "2026-11-15", 1, 4, 280);
        repository.saveStats(id, current);
        var area = new AreaStats("lounge", 160, 10, "2026-11-15");
        repository.saveQuitCheckpoints(List.of(new QuitCheckpoint(id, current, List.of(area), null)));

        var stale = new PlayerStats("Steve", "2026-11-14", 3, 3, 275);
        var oldArea = new AreaStats("lounge", 151, 45, "2026-11-14");
        repository.saveQuitCheckpoints(List.of(new QuitCheckpoint(id, stale, List.of(oldArea), null)));
        assertEquals(current, repository.loadStats(id));
        assertEquals(area, repository.loadAreaStats(id, "lounge"));
    }

    @Test
    void periodicBatchRollsBackAllCountersWhenOneRowFails(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("afk.db");
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("afk.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var initial = new PlayerStats("Steve", "2026-11-14", 1, 2, 51);
        var updated = new PlayerStats("Steve", "2026-11-14", 1, 2, 81);
        repository.saveStats(first, initial);

        var rows = new LinkedHashMap<UUID, PlayerStats>();
        rows.put(first, updated);
        rows.put(second, new PlayerStats(null, "2026-11-14", 0, 0, 7));
        assertThrows(SQLException.class, () -> repository.saveStatsBatch(rows));
        assertEquals(initial, repository.loadStats(first));
        assertEquals(0, repository.loadStats(second).totalSeconds());

        var secondStats = new PlayerStats("Alex", "2026-11-14", 0, 0, 7);
        rows.put(second, secondStats);
        repository.saveStatsBatch(rows);
        assertEquals(updated, repository.loadStats(first));
        assertEquals(secondStats, repository.loadStats(second));
    }

    @Test
    void existingSessionKeepsProgressAndNullableMovementDuringMigration(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("afk.db");
        UUID id = UUID.randomUUID();
        try (var conn = DriverManager.getConnection(url); var statement = conn.createStatement()) {
            statement.execute("""
                CREATE TABLE afk_sessions (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL DEFAULT '',
                    area_name TEXT NOT NULL DEFAULT '',
                    reward_type TEXT NOT NULL DEFAULT '',
                    mode TEXT NOT NULL DEFAULT 'MANUAL',
                    start_seconds INTEGER NOT NULL DEFAULT 0,
                    start_time INTEGER NOT NULL DEFAULT 0,
                    today_count INTEGER NOT NULL DEFAULT 0,
                    total_count INTEGER NOT NULL DEFAULT 0,
                    today_date TEXT NOT NULL DEFAULT '',
                    total_seconds INTEGER NOT NULL DEFAULT 0
                )
                """);
            try (var insert = conn.prepareStatement("""
                INSERT INTO afk_sessions (player_uuid, player_name, start_seconds, start_time) VALUES (?, 'Steve', 73, 10)
                """)) {
                insert.setString(1, id.toString());
                insert.executeUpdate();
            }
        }
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("afk.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var old = repository.loadSession(id);
        assertEquals(73, old.startSeconds());
        assertNull(old.originalWalkSpeed());
        assertNull(old.originalCollidable());
        var latest = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 125, 10, 0, 0, "",
            125, 0.35f, false);
        repository.saveSession(latest);
        assertEquals(latest, repository.loadSession(id));
        assertFalse(repository.beginManualSettlement(old, UUID.randomUUID()));
        try (var conn = DriverManager.getConnection(url); var statement = conn.createStatement()) {
            statement.execute("""
                CREATE TRIGGER reject_manual_settlement BEFORE INSERT ON afk_manual_settlements
                BEGIN SELECT RAISE(ABORT, 'injected write failure'); END
                """);
        }
        assertThrows(SQLException.class, () -> repository.beginManualSettlement(latest, UUID.randomUUID()));
        assertEquals(latest, repository.loadSession(id));
        try (var conn = DriverManager.getConnection(url); var statement = conn.createStatement()) {
            statement.execute("DROP TRIGGER reject_manual_settlement");
        }
        assertTrue(repository.beginManualSettlement(latest, UUID.randomUUID()));
        assertNull(repository.loadSession(id));
        assertFalse(repository.beginManualSettlement(latest, UUID.randomUUID()));
        var review = repository.findManualSettlementsForReview(25).get(0);
        assertEquals(id, review.playerUuid());
        assertEquals(125, review.seconds());
        assertEquals("DISPATCHING", review.status());
        repository.completeManualSettlement(latest, 2);
        repository.completeManualSettlement(latest, 2);
        assertThrows(java.sql.SQLException.class, () -> repository.completeManualSettlement(latest, 3));
        assertTrue(repository.findManualSettlementsForReview(25).isEmpty());
    }
}
