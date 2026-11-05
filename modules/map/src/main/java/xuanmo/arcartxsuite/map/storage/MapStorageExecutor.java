package xuanmo.arcartxsuite.map.storage;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Serial SQL worker. All future continuations run on the owning server thread. */
public final class MapStorageExecutor implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(256), task -> {
            Thread thread = new Thread(task, "axs-map-storage");
            thread.setDaemon(true);
            return thread;
        });
    private final ConcurrentLinkedQueue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.Semaphore normalSlots = new java.util.concurrent.Semaphore(128);
    private volatile boolean closing;
    private final java.util.concurrent.atomic.LongAdder completedJobs = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder queueWaitNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder sqlNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.AtomicLong longestQueueWaitNanos = new java.util.concurrent.atomic.AtomicLong();

    public record Diagnostics(long completedJobs, int queuedJobs, int pendingJobs,
                              long totalQueueWaitNanos, long totalWorkNanos, long maxQueueWaitNanos) { }
    public Diagnostics diagnostics() {
        return new Diagnostics(completedJobs.sum(), worker.getQueue().size(), pending.size(),
            queueWaitNanos.sum(), sqlNanos.sum(), longestQueueWaitNanos.get());
    }

    public boolean isClosing() { return closing; }

    public <T> CompletableFuture<T> query(Callable<T> work) { return submit(work, false); }

    /** Compensation must still run while the module is stopping. */
    public <T> CompletableFuture<T> compensate(Callable<T> work) { return submit(work, true); }

    private <T> CompletableFuture<T> submit(Callable<T> work, boolean compensation) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Submit map SQL from the server thread");
        CompletableFuture<T> result = new CompletableFuture<>();
        if (worker.isShutdown()) {
            result.completeExceptionally(new CancellationException("Map storage is closed"));
            return result;
        }
        if (!compensation && !normalSlots.tryAcquire()) {
            result.completeExceptionally(new java.util.concurrent.RejectedExecutionException("Map storage queue is full"));
            return result;
        }
        pending.add(result);
        long queuedAt = System.nanoTime();
        try {
            worker.execute(() -> {
                long startedAt = System.nanoTime();
                long wait = startedAt - queuedAt;
                queueWaitNanos.add(wait);
                longestQueueWaitNanos.accumulateAndGet(wait, Math::max);
                try {
                    if (closing && !compensation) throw new CancellationException("Map storage is stopping");
                    T value = work.call();
                    recordWork(startedAt);
                    completions.offer(() -> finish(result, value, null, compensation));
                } catch (Throwable error) {
                    recordWork(startedAt);
                    completions.offer(() -> finish(result, null, error, compensation));
                }
            });
        } catch (RuntimeException error) {
            completions.offer(() -> finish(result, null, error, compensation));
        }
        return result;
    }

    private void recordWork(long startedAt) {
        sqlNanos.add(System.nanoTime() - startedAt);
        completedJobs.increment();
    }

    private <T> void finish(CompletableFuture<T> result, T value, Throwable error, boolean compensation) {
        // Remove before invoking user continuations: a continuation may reload this module.
        pending.remove(result);
        if (!compensation) normalSlots.release();
        if (error == null) result.complete(value);
        else result.completeExceptionally(error);
    }

    public void pump() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Map completions require the server thread");
        long deadline = System.nanoTime() + 1_000_000L;
        Runnable completion;
        int count = 0;
        while (count++ < 256 && (completion = completions.poll()) != null) {
            completion.run();
            if (!closing && System.nanoTime() >= deadline) break;
        }
    }

    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Map shutdown requires the server thread");
        closing = true;
        // Drain our own continuations so a committed unlock can be compensated before the pool closes.
        // Waiting on futures here without pumping would deadlock the server-thread settlement phase.
        while (!pending.isEmpty() || !completions.isEmpty()) {
            pump();
            if (!pending.isEmpty()) LockSupport.parkNanos(1_000_000L);
        }
        worker.shutdown();
    }
}
