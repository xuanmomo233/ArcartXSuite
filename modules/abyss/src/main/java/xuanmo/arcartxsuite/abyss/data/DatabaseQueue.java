package xuanmo.arcartxsuite.abyss.data;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Serial writes with bounded admission, weighted maintenance, and an independent bounded read lane. */
final class DatabaseQueue implements AutoCloseable {
    private record Job<T>(Callable<T> task, CompletableFuture<T> result) {
        void run() {
            try { result.complete(task.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }
    }
    private final ArrayDeque<Job<?>> writes = new ArrayDeque<>();
    private final ArrayDeque<Job<?>> maintenance = new ArrayDeque<>();
    private final int writeCapacity, maintenanceCapacity;
    // At most one drain is queued, even when the current drain is handing over to its successor.
    private final ModuleExecutor writer = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(), task -> thread(task, "Abyss-DB"));
    private final ModuleExecutor reader;
    private boolean draining;
    private volatile boolean closed;
    private int consecutiveWrites;

    DatabaseQueue() { this(4096, 32, 128); }

    DatabaseQueue(int writeCapacity, int maintenanceCapacity, int readCapacity) {
        this.writeCapacity = writeCapacity;
        this.maintenanceCapacity = maintenanceCapacity;
        reader = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(readCapacity), task -> thread(task, "Abyss-DB-Read"));
    }

    <T> CompletableFuture<T> write(Callable<T> task) { return enqueue(task, false); }
    <T> CompletableFuture<T> maintenance(Callable<T> task) { return enqueue(task, true); }

    <T> CompletableFuture<T> read(Callable<T> task) {
        var future = new CompletableFuture<T>();
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("Abyss database stopped"));
            try { reader.execute(() -> {
                if (closed) future.completeExceptionally(new RejectedExecutionException("Abyss database stopped"));
                else new Job<>(task, future).run();
            }); }
            catch (RejectedExecutionException failure) { future.completeExceptionally(failure); }
        }
        return future;
    }

    private synchronized <T> CompletableFuture<T> enqueue(Callable<T> task, boolean background) {
        var queue = background ? maintenance : writes;
        int capacity = background ? maintenanceCapacity : writeCapacity;
        if (closed || queue.size() >= capacity) return CompletableFuture.failedFuture(
            new RejectedExecutionException(closed ? "Abyss database stopped" : "Abyss database queue full"));
        var future = new CompletableFuture<T>();
        queue.addLast(new Job<>(task, future));
        if (!draining) {
            draining = true;
            writer.execute(this::drain);
        }
        return future;
    }

    private void drain() {
        for (;;) {
            Job<?> job;
            synchronized (this) {
                if (!maintenance.isEmpty() && (writes.isEmpty() || consecutiveWrites >= 8)) {
                    job = maintenance.removeFirst();
                    consecutiveWrites = 0;
                } else if (!writes.isEmpty()) {
                    job = writes.removeFirst();
                    consecutiveWrites++;
                } else {
                    draining = false;
                    return;
                }
            }
            job.run();
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        writer.shutdown();
        reader.shutdown();
    }

    CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(writer.stopped().toCompletableFuture(), reader.stopped().toCompletableFuture());
    }

    private static Thread thread(Runnable task, String name) {
        var thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
