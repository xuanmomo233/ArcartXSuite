package xuanmo.arcartxsuite.essentials.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PoseRetirementTest {
    @Test void retirementWaitsForTheActiveMonitorAndRejectsFutureTicks() throws Exception {
        var delegate = new EssentialsCommandDelegate(null, null, null, null, null, () -> null,
            () -> null, () -> null, null, null, name -> null, null, () -> null,
            () -> CompletableFuture.completedFuture(null), new HashMap<>(), new HashMap<>(), null);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var runs = new AtomicInteger();
        var method = EssentialsCommandDelegate.class.getDeclaredMethod("poseCallback", Runnable.class);
        method.setAccessible(true);
        Runnable monitor = (Runnable) method.invoke(delegate, (Runnable) () -> {
            runs.incrementAndGet(); entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var active = executor.submit(monitor);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            delegate.shutdown();
            assertFalse(delegate.stopped().toCompletableFuture().isDone());
            monitor.run(); assertEquals(1, runs.get());
            release.countDown(); active.get(5, TimeUnit.SECONDS);
            delegate.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); delegate.shutdown(); executor.shutdownNow(); }
    }
}
