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

    @Test
    void onlyTheOriginalInventoryNodeCanResumeAnInterruptedItemDelivery() {
        repository.insertOperation(new MarketOperation("interrupted", "trade", player, "ITEM", "test",
            "READY", "", "0", "item", "", "", 2, 2));
        repository.insertOperation(new MarketOperation("new", "trade", player, "ITEM", "test",
            "READY", "", "0", "other item", "", "", 1, 1));
        assertTrue(repository.transitionOperation("interrupted", "READY", "ITEM_SAVING", "claimed", "node-a"));
        assertEquals("interrupted", repository.pendingDeliveries(player, "node-a", 1).get(0).id());
        assertEquals("new", repository.pendingDeliveries(player, "node-b", 1).get(0).id());
        assertFalse(repository.transitionOperation("interrupted", "ITEM_SAVING", "DONE", "wrong node", "node-b"));
        assertTrue(repository.transitionOperation("interrupted", "ITEM_SAVING", "DONE", "verified", "node-a"));
    }

    @Test
    void currencyRetryCannotReuseEvidenceFromThePreviousReview() {
        repository.insertOperation(new MarketOperation("credit", "trade", player, "CURRENCY", "test",
            "REVIEW", "money", "19", "", "", "node-a", 1, 1));
        assertFalse(repository.resolveOperation("credit", "retry-confirmed", "no review version"));
        assertTrue(repository.resolveOperation("credit@1", "retry-confirmed", "verified missing"));
        assertTrue(repository.transitionOperation("credit", "READY", "RUNNING", "claimed", "node-b"));
        assertTrue(repository.transitionOperation("credit", "RUNNING", "REVIEW", "needs new evidence", "node-b"));
        assertFalse(repository.resolveOperation("credit@1", "retry-confirmed", "stale evidence"));
        assertFalse(repository.resolveOperation("credit", "delivered", "no review version"));
        long version = repository.operations("REVIEW", player, 0, 10).get(0).updatedAt();
        assertTrue(repository.resolveOperation("credit@" + version, "delivered", "verified committed"));
        assertFalse(repository.resolveOperation("credit@" + version, "delivered", "duplicate confirmation"));
    }
}
