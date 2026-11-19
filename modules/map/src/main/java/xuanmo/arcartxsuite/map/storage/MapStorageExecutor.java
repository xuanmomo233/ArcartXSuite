package xuanmo.arcartxsuite.map.storage;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Serial SQL worker. While active, business callbacks run on the server thread. */
public final class MapStorageExecutor implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(256), task -> {
            Thread thread = new Thread(task, "axs-map-storage");
            thread.setDaemon(true);
            return thread;
        });
    private static final int MAX_PENDING = 128;
    private record Completion(Runnable primary, Runnable retired) {}
    private final ConcurrentLinkedQueue<Completion> completions = new ConcurrentLinkedQueue<>();
    private final Set<CompletableFuture<?>> pendingQueries = new HashSet<>(); // owner thread
    private final java.util.concurrent.Semaphore normalSlots = new java.util.concurrent.Semaphore(MAX_PENDING);
    private final AtomicReference<Throwable> retirementFailure = new AtomicReference<>();
    private volatile boolean closing;
    private boolean pumping;
    private final java.util.concurrent.atomic.LongAdder completedJobs = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder queueWaitNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder sqlNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.AtomicLong longestQueueWaitNanos = new java.util.concurrent.atomic.AtomicLong();

    public record Diagnostics(long completedJobs, int queuedJobs, int pendingJobs,
                              long totalQueueWaitNanos, long totalWorkNanos, long maxQueueWaitNanos) { }
    public Diagnostics diagnostics() {
        return new Diagnostics(completedJobs.sum(), worker.getQueue().size(), MAX_PENDING - normalSlots.availablePermits(),
            queueWaitNanos.sum(), sqlNanos.sum(), longestQueueWaitNanos.get());
    }

    public boolean isClosing() { return closing; }

    public <T> CompletableFuture<T> query(Callable<T> work) {
        return submit(work, false);
    }

    /** Already accepted standalone writes drain at shutdown. */
    public <T> CompletableFuture<T> write(Callable<T> work) {
        return submit(work, true);
    }

    private <T> CompletableFuture<T> submit(Callable<T> work, boolean drain) {
        var result = new CompletableFuture<T>();
        if (!admit(result)) return result;
        if (!drain) pendingQueries.add(result);
        enqueue(() -> {
            if (closing && !drain) return;
            try {
                T value = runSql(work);
                dispatch(() -> finishQuery(result, value, null), drain ? () -> result.complete(value) : null);
            } catch (Throwable failure) {
                dispatch(() -> finishQuery(result, null, failure), drain ? () -> result.completeExceptionally(failure) : null);
            }
        });
        return admittedView(result);
    }

    /** A reservation holds its admission slot until settlement or its rollback has finished. */
    <T> CompletableFuture<T> reserve(Callable<Boolean> reserve, Supplier<T> settle, Predicate<T> accepted,
                                     Callable<Void> publish, Callable<Void> rollback, Callable<T> duplicate, T cancelled) {
        var result = new CompletableFuture<T>();
        if (!admit(result)) return result;
        enqueue(() -> {
            try {
                if (closing) {
                    result.complete(cancelled);
                } else if (!runSql(reserve)) {
                    T existing = runSql(duplicate);
                    dispatch(() -> result.complete(existing), () -> result.complete(cancelled));
                } else {
                    dispatch(() -> settle(result, settle, accepted, publish, rollback),
                        () -> rollBack(result, rollback, cancelled, null));
                }
            } catch (Throwable failure) {
                completeReservation(result, null, failure, false);
            }
        });
        return admittedView(result);
    }

    private boolean admit(CompletableFuture<?> result) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Submit map SQL from the server thread");
        if (closing) {
            result.completeExceptionally(new CancellationException("Map storage is closed"));
            return false;
        }
        if (!normalSlots.tryAcquire()) {
            result.completeExceptionally(new RejectedExecutionException("Map storage queue is full"));
            return false;
        }
        return true;
    }

    private <T> CompletableFuture<T> admittedView(CompletableFuture<T> result) {
        return result.whenComplete((value, failure) -> normalSlots.release()).copy();
    }

    private void enqueue(Runnable job) {
        long queuedAt = System.nanoTime();
        worker.execute(() -> {
            long wait = System.nanoTime() - queuedAt;
            queueWaitNanos.add(wait);
            longestQueueWaitNanos.accumulateAndGet(wait, Math::max);
            job.run();
        });
    }

    private <T> T runSql(Callable<T> work) throws Exception {
        long startedAt = System.nanoTime();
        try { return work.call(); }
        finally {
            sqlNanos.add(System.nanoTime() - startedAt);
            completedJobs.increment();
        }
    }

    private void dispatch(Runnable primary, Runnable retired) {
        synchronized (completions) {
            if (!closing) {
                completions.offer(new Completion(primary, retired));
                return;
            }
        }
        if (retired != null) retired.run();
    }

    private <T> void finishQuery(CompletableFuture<T> result, T value, Throwable error) {
        pendingQueries.remove(result);
        if (error == null) result.complete(value);
        else result.completeExceptionally(error);
    }

    private <T> void settle(CompletableFuture<T> result, Supplier<T> settle, Predicate<T> accepted,
                            Callable<Void> publish, Callable<Void> rollback) {
        T value;
        boolean confirmed;
        try {
            value = settle.get();
            confirmed = accepted.test(value);
        } catch (Throwable failure) {
            enqueue(() -> rollBack(result, rollback, null, failure));
            return;
        }
        if (!confirmed) {
            enqueue(() -> rollBack(result, rollback, value, null));
            return;
        }
        enqueue(() -> {
            try {
                runSql(publish);
                completeReservation(result, value, null, false);
            } catch (Throwable failure) {
                // Payment has already succeeded. A publication failure must not release the reservation.
                completeReservation(result, null, failure, true);
            }
        });
    }

    private <T> void rollBack(CompletableFuture<T> result, Callable<Void> rollback, T value, Throwable settlementFailure) {
        try {
            runSql(rollback);
        } catch (Throwable failure) {
            if (settlementFailure != null && settlementFailure != failure) failure.addSuppressed(settlementFailure);
            completeReservation(result, null, failure, true);
            return;
        }
        completeReservation(result, value, settlementFailure, false);
    }

    private <T> void completeReservation(CompletableFuture<T> result, T value, Throwable failure, boolean rollbackFailed) {
        Runnable finish = () -> {
            if (failure == null) result.complete(value);
            else result.completeExceptionally(failure);
        };
        dispatch(finish, () -> {
            if (rollbackFailed) retirementFailure.compareAndSet(null, failure);
            finish.run();
        });
    }

    public void pump() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Map completions require the server thread");
        if (closing || pumping) return;
        pumping = true;
        long deadline = System.nanoTime() + 1_000_000L;
        try {
            Completion completion;
            while (!closing && (completion = completions.poll()) != null) {
                completion.primary().run();
                if (System.nanoTime() >= deadline) break;
            }
        } finally {
            pumping = false;
            // A Bukkit callback can disable Map before finishing its own settlement.
            if (closing) worker.shutdown();
        }
    }

    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Map shutdown requires the server thread");
        synchronized (completions) {
            if (closing) return;
            closing = true;
        }
        for (Completion completion; (completion = completions.poll()) != null; ) {
            if (completion.retired() != null) enqueue(completion.retired());
        }
        for (var result : pendingQueries) result.completeExceptionally(new CancellationException("Map storage is stopping"));
        pendingQueries.clear();
        if (!pumping) worker.shutdown();
    }

    public CompletionStage<Void> stopped() {
        return worker.stopped().thenCompose(ignored -> {
            Throwable failure = retirementFailure.get();
            return failure == null ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(failure);
        });
    }
}
