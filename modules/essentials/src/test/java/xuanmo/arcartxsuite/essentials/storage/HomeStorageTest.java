package xuanmo.arcartxsuite.essentials.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class HomeStorageTest {
    @TempDir Path directory;

    @Test void coordinatesDoNotRequireALoadedBukkitWorldAndUpdatingAtTheLimitIsAllowed() throws Exception {
        var repository = repository();
        UUID id = UUID.randomUUID();
        var initial = new EssentialsRepository.Position("offline-world", 1.25, 72, -4, 90, -10);
        assertTrue(repository.setHome(id, "Home", initial, 1).saved());
        assertEquals(initial, repository.getHome(id, "HOME"));
        assertFalse(repository.setHome(id, "other", initial, 1).saved());
        var replacement = new EssentialsRepository.Position("other-world", 100, 80, 200, 0, 0);
        var changed = repository.setHome(id, "HOME", replacement, 1);
        assertTrue(changed.saved());
        assertEquals(java.util.List.of("home"), changed.names());
        assertEquals(replacement, repository.getHome(id, "home"));
        assertEquals(1, repository.homeCount(id));
        assertTrue(repository.deleteHome(id, "HOME"));
        assertFalse(repository.deleteHome(id, "HOME"));
        assertTrue(repository.listHomeNames(id).isEmpty());
    }

    @Test void aFailedWriteRollsBackWithoutDeletingTheOldHome() throws Exception {
        var repository = repository();
        UUID id = UUID.randomUUID();
        var position = new EssentialsRepository.Position("world", 1, 2, 3, 0, 0);
        repository.setHome(id, "home", position, 1);
        try (var connection = repository.dataSource().getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_home BEFORE INSERT ON " + repository.tablePrefix()
                + "homes BEGIN SELECT RAISE(ABORT, 'fixture'); END");
        }
        assertThrows(SQLException.class, () -> repository.setHome(id, "home",
            new EssentialsRepository.Position("world", 8, 9, 10, 0, 0), 1));
        assertEquals(position, repository.getHome(id, "home"));
    }

    @Test void homeKeysDoNotDependOnTheHostsLocale() throws Exception {
        var repository = repository();
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            UUID id = UUID.randomUUID();
            var position = new EssentialsRepository.Position("world", 1, 2, 3, 0, 0);
            repository.setHome(id, "ISLAND", position, 1);
            assertEquals(position, repository.getHome(id, "island"));
            assertTrue(repository.deleteHome(id, "ISLAND"));
        } finally { Locale.setDefault(previous); }
    }

    private EssentialsRepository repository() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("homes.sqlite"));
        var repository = new EssentialsRepository(source, StorageDescriptor.sqlite("homes.sqlite"), directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        return repository;
    }
}
