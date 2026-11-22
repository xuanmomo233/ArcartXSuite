package xuanmo.arcartxsuite.market.exchange;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class NpcExchangeShutdownTest {
    @TempDir Path directory;

    @Test
    @SuppressWarnings("unchecked")
    void aFullQueueDrainsAndTheFinalReviewIsSavedBeforeRetirementCompletes() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("npc.db"));
        var repository = new NpcTradeRepository(source, StorageDescriptor.sqlite("npc.db"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        String operation = UUID.randomUUID().toString();
        UUID player = UUID.randomUUID();
        repository.begin(operation, player, "{}");
        var plugin = mock(Plugin.class);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var exchange = new NpcExchange(plugin, repository, null, Logger.getAnonymousLogger(), directory);
            var workerField = NpcExchange.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            var worker = (ExecutorService) workerField.get(exchange);
            var bufferField = NpcExchange.class.getDeclaredField("reviewBuffer");
            bufferField.setAccessible(true);
            var buffer = (Map<String, String>) bufferField.get(exchange);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var accepted = new AtomicInteger();
            worker.execute(() -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new AssertionError(error); }
            });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 127; i++) exchange.query(() -> accepted.incrementAndGet(), ignored -> {}, error -> fail(error));
                buffer.put(operation, "{\"review\":\"closing\"}");
                var review = NpcExchange.class.getDeclaredMethod("queueReviews");
                review.setAccessible(true); review.invoke(exchange);
                long started = System.nanoTime();
                exchange.close();
                assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(500));
                assertFalse(exchange.stopped().toCompletableFuture().isDone());
            } finally { release.countDown(); exchange.close(); }
            exchange.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(127, accepted.get());
            assertTrue(buffer.isEmpty());
            assertEquals("REVIEW", repository.get(operation).state());
            assertThrows(java.sql.SQLException.class, () -> repository.begin(UUID.randomUUID().toString(), player, "{}"));
        } finally { repository.shutdown(); }
    }
}
