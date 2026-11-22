package xuanmo.arcartxsuite.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ShutdownSequenceTest {
    @Test
    void allProducersExitBeforeOffThreadPoolCloseAndTheActualCloseIsAwaited() throws Exception {
        var sequence = new ShutdownSequence(Logger.getAnonymousLogger());
        var first = new CompletableFuture<Void>();
        var second = new CompletableFuture<Void>();
        var poolClosed = new CompletableFuture<Void>();
        var closeStarted = new CountDownLatch(1);
        var closes = new AtomicInteger();
        sequence.await("module one", () -> first);
        sequence.await("module two", () -> second);
        var retired = sequence.finish(() -> {
            assertEquals("AXS-Host-Retire", Thread.currentThread().getName());
            closes.incrementAndGet(); closeStarted.countDown(); return poolClosed;
        }).toCompletableFuture();
        first.completeExceptionally(new IllegalStateException("one receipt failed"));
        assertEquals(0, closes.get());
        assertFalse(retired.isDone());
        second.complete(null);
        assertTrue(closeStarted.await(2, TimeUnit.SECONDS));
        assertFalse(retired.isDone());
        poolClosed.complete(null);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> retired.get(2, TimeUnit.SECONDS));
        assertEquals(1, closes.get());
    }

    @Test
    void aDetachErrorDoesNotSkipOtherComponentsOrStorageClosure() throws Exception {
        var sequence = new ShutdownSequence(Logger.getAnonymousLogger());
        var detached = new AtomicInteger();
        var closed = new AtomicInteger();
        sequence.stop("failed UI", () -> { throw new NoClassDefFoundError("optional bridge"); });
        sequence.stop("other UI", detached::incrementAndGet);
        var retired = sequence.finish(() -> {
            closed.incrementAndGet(); return CompletableFuture.completedFuture(null);
        }).toCompletableFuture();
        assertThrows(java.util.concurrent.ExecutionException.class, () -> retired.get(2, TimeUnit.SECONDS));
        assertEquals(1, detached.get());
        assertEquals(1, closed.get());
    }
}
