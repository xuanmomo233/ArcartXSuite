package xuanmo.arcartxsuite.abyss.data;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> thread(task, "Abyss-DB"));
    private final ThreadPoolExecutor reader;
    private boolean draining, closed;
    private int consecutiveWrites;

    DatabaseQueue() { this(4096, 32, 128); }

    DatabaseQueue(int writeCapacity, int maintenanceCapacity, int readCapacity) {
        this.writeCapacity = writeCapacity;
        this.maintenanceCapacity = maintenanceCapacity;
        reader = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(readCapacity), task -> thread(task, "Abyss-DB-Read"),
            new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> write(Callable<T> task) { return enqueue(task, false); }
    <T> CompletableFuture<T> maintenance(Callable<T> task) { return enqueue(task, true); }

    <T> CompletableFuture<T> read(Callable<T> task) {
        var future = new CompletableFuture<T>();
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("Abyss database stopped"));
            try { reader.execute(() -> new Job<>(task, future).run()); }
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

    @Override public void close() {
        synchronized (this) {
            closed = true;
            writer.shutdown();
            reader.shutdown();
        }
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
            reader.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread thread(Runnable task, String name) {
        var thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
