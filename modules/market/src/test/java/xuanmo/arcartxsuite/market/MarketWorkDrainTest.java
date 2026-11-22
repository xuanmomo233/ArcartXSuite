package xuanmo.arcartxsuite.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MarketWorkDrainTest {
    @Test void drainAcceptsAnAcceptedTasksReceiptButRejectsNewExternalWork() throws Exception {
        var work = new MarketWork(mock(Plugin.class));
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var receiptRunning = new CountDownLatch(1);
        var receiptRelease = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        work.async(() -> {
            running.countDown();
            await(release);
            try {
                work.async(() -> {
                    receiptRunning.countDown();
                    await(receiptRelease);
                    completed.incrementAndGet();
                });
            } catch (Throwable error) { failure.set(error); }
        });
        try {
            assertTrue(running.await(5, TimeUnit.SECONDS));
            var stopped = work.drain().toCompletableFuture();
            assertFalse(stopped.isDone());
            assertThrows(RejectedExecutionException.class, () -> work.async(() -> fail("New work ran")));
            release.countDown();
            assertTrue(receiptRunning.await(5, TimeUnit.SECONDS), "The accepted task could not submit its receipt");
            assertFalse(stopped.isDone(), "Stopped before its receipt was saved");
            receiptRelease.countDown();
            stopped.get(5, TimeUnit.SECONDS);
            assertNull(failure.get());
            assertEquals(1, completed.get());
            assertThrows(RejectedExecutionException.class, () -> work.async(() -> {}));
        } finally {
            release.countDown(); receiptRelease.countDown();
            work.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void aRunningCallbackCanSubmitItsLastDatabaseTaskDuringDrain() throws Exception {
        var work = new MarketWork(mock(Plugin.class));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var callbacks = new AtomicInteger();
        var saved = new AtomicInteger();
        var callback = work.callback(() -> {
            callbacks.incrementAndGet();
            started.countDown();
            await(release);
            work.async(saved::incrementAndGet);
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(callback);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var stopped = work.drain().toCompletableFuture();
            assertFalse(stopped.isDone());
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            stopped.get(5, TimeUnit.SECONDS);
            callback.run();
            assertEquals(1, callbacks.get());
            assertEquals(1, saved.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            work.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void repeatedDrainAndRetiredCallbacksCannotStartMoreWork() throws Exception {
        var work = new MarketWork(mock(Plugin.class));
        var called = new AtomicInteger();
        var callback = work.callback(called::incrementAndGet);
        work.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
        work.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
        callback.run();
        work.callback(called::incrementAndGet).run();
        assertEquals(0, called.get());
        assertThrows(RejectedExecutionException.class, () -> work.async(() -> {}));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test task timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
