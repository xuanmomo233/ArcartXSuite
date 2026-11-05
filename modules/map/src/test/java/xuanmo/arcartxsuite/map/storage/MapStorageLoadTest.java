package xuanmo.arcartxsuite.map.storage;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapStorageLoadTest {
    @Test void measuresQueueingWithoutRunningSlowWorkOnOwnerThread() {
        Thread owner = Thread.currentThread();
        try (var storage = new MapStorageExecutor()) {
            var jobs = new ArrayList<CompletableFuture<Integer>>();
            for (int i = 0; i < 32; i++) jobs.add(storage.query(() -> {
                assertNotSame(owner, Thread.currentThread());
                LockSupport.parkNanos(2_000_000);
                return 1;
            }));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (jobs.stream().anyMatch(job -> !job.isDone()) && System.nanoTime() < deadline) {
                storage.pump(); LockSupport.parkNanos(100_000);
            }
            assertTrue(jobs.stream().allMatch(job -> job.join() == 1));
            var metrics = storage.diagnostics();
            assertEquals(32, metrics.completedJobs());
            assertEquals(0, metrics.pendingJobs());
            assertTrue(metrics.maxQueueWaitNanos() > 0);
            System.out.printf(java.util.Locale.ROOT,
                "SIMULATED_SQL jobs=%d workMeanMs=%.3f queueMeanMs=%.3f queueMaxMs=%.3f%n",
                metrics.completedJobs(), metrics.totalWorkNanos()/32e6,
                metrics.totalQueueWaitNanos()/32e6, metrics.maxQueueWaitNanos()/1e6);
        }
    }
}
