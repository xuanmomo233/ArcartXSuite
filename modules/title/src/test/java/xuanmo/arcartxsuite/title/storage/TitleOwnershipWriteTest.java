package xuanmo.arcartxsuite.title.storage;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitleOwnershipWriteTest {
    @TempDir Path folder;

    @Test void lateEquipmentAndDisplayWritesCannotRestoreRevokedOwnership() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("titles.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("titles.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.now();
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", false, now, null, null, now, "test"), false);
        repository.setEquippedTitle(id, "main", "hero", now);
        repository.setDisplayTitle(id, "hero", now);
        assertEquals("hero", repository.loadState(id).equippedTitleIdsByGroup().get("main"));
        assertEquals("hero", repository.loadState(id).displayTitleId());
        repository.deleteOwnedTitle(id, "hero");
        repository.setEquippedTitle(id, "main", "hero", now.plusMillis(1));
        repository.setDisplayTitle(id, "hero", now.plusMillis(1));
        var state = repository.loadState(id);
        assertFalse(state.hasOwnedTitle("hero"));
        try (var connection = source.getConnection(); var query = connection.createStatement()) {
            for (var table : java.util.List.of("title_player_equipped_groups", "title_player_display_title")) {
                try (var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            }
        }
    }
    @Test void clearingEquipmentRollsBackWhenClearingDisplayFails() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("atomic.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("atomic.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.now();
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", false, now, null, null, now, "test"), false);
        repository.setEquippedTitle(id, "main", "hero", now);
        repository.setDisplayTitle(id, "hero", now);
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                CREATE TRIGGER reject_display_delete BEFORE DELETE ON title_player_display_title
                BEGIN SELECT RAISE(ABORT, 'test display failure'); END
                """);
        }
        assertThrows(java.sql.SQLException.class, () -> repository.clearEquippedTitles(id));
        var unchanged = repository.loadState(id);
        assertEquals("hero", unchanged.equippedTitleIdsByGroup().get("main"));
        assertEquals("hero", unchanged.displayTitleId());
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("DROP TRIGGER reject_display_delete");
        }
        var cleared = repository.clearEquippedTitles(id);
        assertTrue(cleared.equippedTitleIdsByGroup().isEmpty());
        assertEquals("", cleared.displayTitleId());
    }

    @Test void expiryRollsBackTogetherAndPreservesCurrentEquipment() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("cleanup.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("cleanup.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.now();
        var expires = now.plusSeconds(30);
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", false, now, null, expires, now, "test"), false);
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("other", false, now, null, null, now, "test"), false);
        assertEquals("", repository.setDisplayTitle(id, "hero", now).displayTitleId());
        repository.setEquippedTitle(id, "main", "other", now);
        repository.setEquippedTitle(id, "secondary", "hero", now);
        repository.setDisplayTitle(id, "hero", now);
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                CREATE TRIGGER block_expiry BEFORE DELETE ON title_player_titles
                BEGIN SELECT RAISE(ABORT, 'test ownership failure'); END
                """);
        }
        assertEquals(0, repository.deleteExpiredTitles(expires.minusMillis(1)));
        assertThrows(java.sql.SQLException.class, () -> repository.deleteExpiredTitles(expires));
        var unchanged = repository.loadState(id);
        assertTrue(unchanged.hasOwnedTitle("hero"));
        assertEquals("hero", unchanged.equippedTitleIdsByGroup().get("secondary"));
        assertEquals("hero", unchanged.displayTitleId());
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("DROP TRIGGER block_expiry");
        }
        assertEquals(1, repository.deleteExpiredTitles(expires));
        var cleaned = repository.loadState(id);
        assertFalse(cleaned.hasOwnedTitle("hero"));
        assertEquals(java.util.Map.of("main", "other"), cleaned.equippedTitleIdsByGroup());
        assertEquals("", cleaned.displayTitleId());
    }

    @Test void regrantClearsExpiredAndPreviouslyOrphanedPresentation() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("regrant.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("regrant.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var given = Instant.parse("2026-11-19T04:00:00Z");
        var expires = given.plusSeconds(60);
        for (boolean ownershipAlreadyRemoved : new boolean[]{false, true}) {
            var id = UUID.randomUUID();
            repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", true, given, null, expires, given, "test"), false);
            repository.setEquippedTitle(id, "main", "hero", given);
            repository.setDisplayTitle(id, "hero", given);
            if (ownershipAlreadyRemoved) {
                try (var connection = source.getConnection(); var sql = connection.prepareStatement(
                    "DELETE FROM title_player_titles WHERE player_uuid = ?")) {
                    sql.setString(1, id.toString());
                    assertEquals(1, sql.executeUpdate());
                }
            }
            var grant = repository.grantOwnedTitle(id,
                new PlayerOwnedTitle("hero", false, expires, null, null, expires, "test"), false);
            assertTrue(grant.clearedPresentation());
            assertEquals(!ownershipAlreadyRemoved, grant.title().hidden());
            var state = repository.loadState(id);
            assertTrue(state.hasOwnedTitle("hero"));
            assertTrue(state.equippedTitleIdsByGroup().isEmpty());
            assertEquals("", state.displayTitleId());
        }
    }

    @Test void oneCleanupTransactionHasABoundedBatch() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("batch.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("batch.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.parse("2026-11-19T04:00:00Z");
        try (var connection = source.getConnection(); var sql = connection.prepareStatement(
            "INSERT INTO title_player_titles VALUES (?, ?, 0, ?, NULL, ?, ?, 'test')")) {
            connection.setAutoCommit(false);
            sql.setString(1, id.toString());
            sql.setLong(3, now.minusSeconds(1).toEpochMilli());
            sql.setLong(4, now.toEpochMilli());
            sql.setLong(5, now.toEpochMilli());
            for (int i = 0; i < 300; i++) {
                sql.setString(2, "expired-" + i);
                sql.addBatch();
            }
            sql.executeBatch();
            connection.commit();
        }
        assertEquals(256, repository.deleteExpiredTitles(now));
        assertEquals(44, repository.deleteExpiredTitles(now));
        assertEquals(0, repository.deleteExpiredTitles(now));
    }
    @Test void permanentGrantsAndRenewalsPreserveTheHiddenFlag() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("grant.db")));
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("grant.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.now();
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", true, now, null, null, now, "first"), false);
        var before = repository.loadState(id).ownedTitles().get("hero");
        var proposed = new PlayerOwnedTitle("hero", false, now.plusSeconds(1), null,
            now.plusSeconds(60), now.plusSeconds(1), "renew");
        repository.setEquippedTitle(id, "main", "hero", now);
        repository.setDisplayTitle(id, "hero", now);
        assertEquals(before, repository.grantOwnedTitle(id, proposed, true).title());
        var renewed = repository.grantOwnedTitle(id, proposed, false);
        assertFalse(renewed.clearedPresentation());
        assertTrue(renewed.title().hidden());
        assertEquals(proposed.expiresAt().toEpochMilli(), renewed.title().expiresAt().toEpochMilli());
        assertEquals("hero", repository.loadState(id).displayTitleId());
        assertEquals(1, repository.loadState(id).ownedTitles().size());
    }
    @Test void anInterruptedWriteCannotCommitPresentationChanges() throws Exception {
        var source = mock(DataSource.class);
        var interrupt = new java.util.concurrent.atomic.AtomicBoolean();
        when(source.getConnection()).thenAnswer(call -> {
            var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("interrupt.db"));
            if (interrupt.getAndSet(false)) Thread.currentThread().interrupt();
            return connection;
        });
        var repository = new JdbcTitleRepository(source, StorageDescriptor.sqlite("interrupt.db"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var id = UUID.randomUUID();
        var now = Instant.now();
        repository.grantOwnedTitle(id, new PlayerOwnedTitle("hero", false, now, null, null, now, "test"), false);
        interrupt.set(true);
        try {
            assertThrows(java.sql.SQLException.class, () -> repository.setEquippedTitle(id, "main", "hero", now));
        } finally {
            Thread.interrupted();
        }
        assertTrue(repository.loadState(id).equippedTitleIdsByGroup().isEmpty());
    }
}
