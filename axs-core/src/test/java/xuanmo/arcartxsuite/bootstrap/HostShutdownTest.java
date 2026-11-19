package xuanmo.arcartxsuite.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class HostShutdownTest {
    @Test
    void aStoppedLoggerCannotLeaveFailureCompletionPending() {
        var logger = new Logger("isolated-stopped-logger", null) {
            @Override public void log(java.util.logging.Level level, String message, Throwable failure) {
                throw new IllegalStateException("logger already closed");
            }
        };
        var shutdown = new HostShutdown(logger, Duration.ofSeconds(1), hooks(new AtomicInteger()));
        var failure = new IllegalArgumentException("actual close failed");
        shutdown.retire(CompletableFuture.failedFuture(failure), () -> {});
        var observed = assertThrows(java.util.concurrent.CompletionException.class,
            () -> shutdown.completion().toCompletableFuture().join());
        assertSame(failure, observed.getCause());
    }

    @Test
    void protectionIsReleasedOnlyAfterCoreRetiresAndPublicCancellationDoesNotSkipIt() throws Exception {
        var removed = new AtomicInteger();
        var released = new AtomicInteger();
        var shutdown = new HostShutdown(Logger.getAnonymousLogger(), Duration.ofSeconds(2), hooks(removed));
        var core = new CompletableFuture<Void>();
        shutdown.retire(core, released::incrementAndGet);
        shutdown.completion().toCompletableFuture().cancel(false);
        assertFalse(core.isDone());
        assertEquals(0, released.get());
        core.complete(null);
        shutdown.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        shutdown.retire(CompletableFuture.completedFuture(null), released::incrementAndGet);
        assertEquals(1, released.get());
        assertEquals(1, removed.get());
    }

    @Test
    void exitWaitRunsOnItsOwnThreadAndEndsAfterRetirement() throws Exception {
        var shutdown = new HostShutdown(Logger.getAnonymousLogger(), Duration.ofSeconds(2), hooks(new AtomicInteger()));
        var core = new CompletableFuture<Void>();
        shutdown.retire(core, () -> {});
        var entered = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        Thread hook = new Thread(() -> { entered.countDown(); shutdown.awaitExit(); exited.countDown(); });
        hook.start();
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertFalse(exited.await(20, TimeUnit.MILLISECONDS));
        } finally { core.complete(null); }
        assertTrue(exited.await(1, TimeUnit.SECONDS));
        hook.join(1000);
    }

    @Test
    void timeoutDoesNotForgeCompletionOrReleaseProtection() {
        var released = new AtomicInteger();
        var core = new CompletableFuture<Void>();
        var shutdown = new HostShutdown(Logger.getAnonymousLogger(), Duration.ofMillis(20), hooks(new AtomicInteger()));
        shutdown.retire(core, released::incrementAndGet);
        shutdown.awaitExit();
        assertFalse(shutdown.completion().toCompletableFuture().isDone());
        assertFalse(core.isCancelled());
        assertEquals(0, released.get());
        core.complete(null);
        shutdown.completion().toCompletableFuture().join();
        assertEquals(1, released.get());
    }

    @Test
    void failureStillReleasesProtectionAndRemovesTheHook() {
        var removed = new AtomicInteger();
        var released = new AtomicInteger();
        var shutdown = new HostShutdown(Logger.getAnonymousLogger(), Duration.ofSeconds(1), hooks(removed));
        shutdown.retire(CompletableFuture.failedFuture(new IllegalStateException("isolated failure")), released::incrementAndGet);
        assertThrows(java.util.concurrent.CompletionException.class, () -> shutdown.completion().toCompletableFuture().join());
        assertEquals(1, released.get());
        assertEquals(1, removed.get());
    }

    private HostShutdown.Hooks hooks(AtomicInteger removed) {
        return new HostShutdown.Hooks() {
            @Override public void add(Thread hook) { }
            @Override public void remove(Thread hook) { removed.incrementAndGet(); }
        };
    }
}
