package xuanmo.arcartxsuite.market.storage;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

import static org.junit.jupiter.api.Assertions.*;

class MarketOperationOwnershipTest {
    @TempDir Path directory;
    private JdbcMarketRepository repository;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void initialize() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("market.db"));
        repository = new JdbcMarketRepository(source, StorageDescriptor.sqlite("market.db"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
    }

    @Test
    void aPreviousWorkerCannotCompleteAnotherWorkersClaim() {
        repository.insertOperation(new MarketOperation("delivery", "trade", player, "CURRENCY", "test",
            "READY", "money", "10", "", "", "", 1, 1));
        assertTrue(repository.transitionOperation("delivery", "READY", "RUNNING", "claimed", "worker-b"));
        assertFalse(repository.transitionOperation("delivery", "RUNNING", "DONE", "late reply", "worker-a"));
        MarketOperation pending = repository.operations("RUNNING", player, 0, 10).get(0);
        assertEquals("worker-b", pending.owner());
        assertTrue(repository.transitionOperation("delivery", "RUNNING", "DONE", "complete", "worker-b"));
    }

    @Test
    void onlyThePreparingWorkerCanActivateARewardSnapshot() {
        repository.insertOperation(new MarketOperation("prepared", "trade", player, "ITEM", "test",
            "PREPARED", "", "0", "item", "", "worker-a", 1, 1));
        assertFalse(repository.transitionOperation("prepared", "PREPARED", "READY", "wrong worker", "worker-b"));
        assertTrue(repository.transitionOperation("prepared", "PREPARED", "READY", "paid", "worker-a"));
    }
}
