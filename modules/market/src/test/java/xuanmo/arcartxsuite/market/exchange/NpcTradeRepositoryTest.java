package xuanmo.arcartxsuite.market.exchange;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

import static org.junit.jupiter.api.Assertions.*;

class NpcTradeRepositoryTest {
    @TempDir Path directory;

    @Test
    void restartKeepsBlockingClaimsVisibleAndPreventsReplay() throws Exception {
        var repository = open();
        UUID player = UUID.randomUUID();
        String pending = UUID.randomUUID().toString();
        repository.begin(pending, player, "{}");
        assertTrue(repository.state(pending, "PREPARED", "DEBIT_INTENT", "{}"));

        for (int i = 0; i < 50; i++) {
            UUID otherPlayer = UUID.randomUUID();
            String completed = UUID.randomUUID().toString();
            repository.begin(completed, otherPlayer, "{}");
            assertTrue(repository.state(completed, "PREPARED", "APPLIED", "{}"));
        }

        var recovered = open();
        assertEquals(pending, recovered.list(null).get(0).id());
        String next = UUID.randomUUID().toString();
        assertThrows(SQLException.class, () -> recovered.begin(next, player, "{}"));
        assertTrue(recovered.state(pending, "REVIEW", "CONFIRMED", "{}"));
        recovered.begin(next, player, "{}");
        assertEquals("PREPARED", recovered.get(next).state());
    }

    private NpcTradeRepository open() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("market.db"));
        var repository = new NpcTradeRepository(source, StorageDescriptor.sqlite("market.db"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        return repository;
    }
}
