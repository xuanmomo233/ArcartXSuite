package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.ConditionalReward;
import xuanmo.arcartxsuite.fishing.storage.JdbcFishingRepository;

class FishingCatchWorkTest {
    @TempDir Path directory;
    private final Logger logger = Logger.getAnonymousLogger();

    @Test
    void restartReusesExactHostReceiptIfFishingAcknowledgementFailed() throws Exception {
        var repository = spy(repository());
        var host = mock(PendingRewardService.class);
        var deposited = new CountDownLatch(1);
        UUID player = UUID.randomUUID();
        var record = new FishingCatchRecord(UUID.randomUUID(), player, "carp", "鲤鱼", "common", 12, false, 2);
        String key = "capture:" + record.eventId();
        var fish = PendingReward.itemSerialized(player, "fishing", "YmF0Y2g=", "catch");
        var firstOnly = PendingReward.command(player, "fishing", List.of("say first"), "first");
        var batch = List.of(fish, firstOnly);
        when(host.savePendingBatchConfirmed(key, batch)).thenReturn(true);
        doThrow(new SQLException("completion write interrupted")).when(repository).finishCatchDelivery(any());
        var first = new FishingCatchWork(repository, host, null, directory, logger, ignored -> deposited.countDown());
        xuanmo.arcartxsuite.fishing.storage.FishingRepository.CatchSettlement result;
        try {
            result = first.settle(record, "test", season -> season.withFatigueAdded(2, 100), true,
                List.of(new ConditionalReward(fish, false), new ConditionalReward(firstOnly, true))).get(3, TimeUnit.SECONDS);
            assertTrue(deposited.await(3, TimeUnit.SECONDS));
        } finally {
            first.shutdown();
            first.stopped().get(3, TimeUnit.SECONDS);
        }

        var fresh = repository();
        var delivery = result.delivery();
        assertEquals(1, fresh.loadPermanentData(player).totalCaught());
        assertEquals(0, fresh.loadPermanentData(player).treasureCaught());
        assertTrue(fresh.pendingCatchDeliveries("local:another-host", Long.MAX_VALUE, 5).isEmpty());
        assertEquals(List.of(delivery), fresh.pendingCatchDeliveries(delivery.owner(), Long.MAX_VALUE, 5));
        fresh.deferCatchDelivery(delivery, 0);

        var recovered = new CountDownLatch(1);
        var restarted = new FishingCatchWork(fresh, host, null, directory, logger, ignored -> recovered.countDown());
        try {
            restarted.requestSweep();
            assertTrue(recovered.await(3, TimeUnit.SECONDS));
        } finally {
            restarted.shutdown();
            restarted.stopped().get(3, TimeUnit.SECONDS);
        }
        verify(host, times(2)).savePendingBatchConfirmed(key, batch);
        assertTrue(fresh.pendingCatchDeliveries(delivery.owner(), Long.MAX_VALUE, 5).isEmpty());
        fresh.finishCatchDelivery(delivery);
        assertEquals(1, fresh.loadPermanentData(player).totalCaught());
        assertEquals(1, fresh.loadPermanentData(player).treasureCaught());
        assertEquals(2, fresh.loadSeasonData(player, "test").fatigue());
        assertEquals(1, fresh.loadCatchRecords(player, 10).size());
    }

    private JdbcFishingRepository repository() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("fish.db"));
        var result = new JdbcFishingRepository(source, StorageDescriptor.sqlite("fish.db"), directory.toFile(), logger);
        result.initialize();
        return result;
    }
}
