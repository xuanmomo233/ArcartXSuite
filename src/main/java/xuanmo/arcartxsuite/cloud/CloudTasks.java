package xuanmo.arcartxsuite.cloud;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Tracks operations and queued main-thread work; buffer cleanup uses one lazy worker. */
final class CloudTasks {
    @FunctionalInterface interface Dispatcher {
        Runnable schedule(Runnable action, long delayTicks, boolean async);
    }

    private final Dispatcher dispatcher;
    private final Set<CompletableFuture<Void>> pending = new HashSet<>();
    private final Set<Queued<?>> queued = new HashSet<>();
    private final Set<CompletableFuture<?>> transfers = new HashSet<>();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor cleanup =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(128), action -> {
                Thread thread = new Thread(action, "AXS-Cloud-Cleanup"); thread.setDaemon(true); return thread;
            });
    private volatile boolean closed;
    private Throwable cleanupFailure;
    private int requests;

    CloudTasks(Dispatcher dispatcher) { this.dispatcher = dispatcher; }

    boolean isClosed() { return closed; }

    <T> CompletableFuture<T> request(Supplier<CompletableFuture<T>> operation) {
        var ticket = new CompletableFuture<Void>();
        synchronized (this) {
            if (closed) return rejected();
            if (requests >= 128) return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("云端任务繁忙"));
            requests++;
            pending.add(ticket);
        }
        try {
            var actual = operation.get();
            actual.whenComplete((value, failure) -> { synchronized (this) { requests--; } finish(ticket); });
            return actual.copy();
        } catch (Throwable failure) {
            synchronized (this) { requests--; }
            finish(ticket);
            return CompletableFuture.failedFuture(failure);
        }
    }

    <T> CompletableFuture<T> transfer(CompletableFuture<T> actual) {
        var ticket = new CompletableFuture<Void>();
        boolean cancel;
        synchronized (this) { pending.add(ticket); transfers.add(actual); cancel = closed; }
        actual.whenComplete((value, failure) -> {
            synchronized (this) { transfers.remove(actual); }
            finish(ticket);
        });
        if (cancel) actual.cancel(true);
        return actual;
    }

    <T> CompletableFuture<T> withCleanup(Supplier<CompletableFuture<T>> action, Runnable release) {
        CompletableFuture<T> actual;
        try { actual = action.get(); }
        catch (Throwable failure) { actual = CompletableFuture.failedFuture(failure); }
        return actual.handle((value, failure) -> clean(release).handle((ignored, cleanupError) -> {
            if (failure != null) {
                if (cleanupError != null && cleanupError != failure) failure.addSuppressed(cleanupError);
                throw new java.util.concurrent.CompletionException(failure);
            }
            if (cleanupError != null) throw new java.util.concurrent.CompletionException(cleanupError);
            return value;
        })).thenCompose(stage -> stage);
    }

    private CompletableFuture<Void> clean(Runnable release) {
        var ticket = new CompletableFuture<Void>();
        var result = new CompletableFuture<Void>();
        synchronized (this) { pending.add(ticket); }
        Runnable action = () -> {
            try { release.run(); result.complete(null); }
            catch (Throwable failure) { failedCleanup(failure); result.completeExceptionally(failure); }
            finally { finish(ticket); }
        };
        try { cleanup.execute(action); }
        catch (java.util.concurrent.RejectedExecutionException stopped) { action.run(); }
        return result;
    }

    <T> CompletableFuture<T> main(Callable<T> action) { return schedule(action, 0, false); }

    CompletableFuture<Void> delay(long ticks) { return schedule(() -> null, ticks, true); }

    private <T> CompletableFuture<T> schedule(Callable<T> action, long delay, boolean async) {
        Queued<T> task;
        boolean rejected;
        synchronized (this) {
            task = new Queued<>(action);
            rejected = closed;
            if (!rejected) { pending.add(task.ticket); queued.add(task); }
        }
        if (rejected) { task.cancel(); return task.result.copy(); }
        try { task.handle(dispatcher.schedule(task, delay, async)); }
        catch (Throwable failure) { task.reject(failure); }
        return task.result.copy();
    }

    CompletionStage<Void> shutdown() {
        List<Queued<?>> tasks;
        List<CompletableFuture<?>> network;
        CompletableFuture<?>[] tickets;
        synchronized (this) {
            if (closed) return stopped.copy();
            closed = true;
            tasks = List.copyOf(queued);
            network = List.copyOf(transfers);
            tickets = pending.toArray(CompletableFuture[]::new);
        }
        tasks.forEach(Queued::cancel);
        network.forEach(transfer -> transfer.cancel(true));
        CompletableFuture.allOf(tickets).whenComplete((ignored, failure) -> {
            cleanup.shutdown();
            cleanup.stopped().whenComplete((unused, cleanupError) -> {
                synchronized (this) {
                    if (cleanupFailure == null) stopped.complete(null);
                    else stopped.completeExceptionally(cleanupFailure);
                }
            });
        });
        return stopped.copy();
    }

    private synchronized void failedCleanup(Throwable failure) {
        if (cleanupFailure == null) cleanupFailure = failure;
        else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
    }

    private void finish(CompletableFuture<Void> ticket) {
        synchronized (this) { pending.remove(ticket); }
        ticket.complete(null);
    }

    private static <T> CompletableFuture<T> rejected() {
        return CompletableFuture.failedFuture(new CancellationException("云端模块服务正在停止"));
    }

    private final class Queued<T> extends AtomicInteger implements Runnable {
        private final Callable<T> action;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final CompletableFuture<Void> ticket = new CompletableFuture<>();
        private Runnable cancelHandle;

        Queued(Callable<T> action) { this.action = action; }

        @Override public void run() {
            if (!compareAndSet(0, 1)) return;
            T value = null;
            Throwable failure = null;
            try { value = action.call(); }
            catch (Throwable error) { failure = error; }
            complete(value, failure);
        }

        void cancel() { reject(new CancellationException("云端模块服务正在停止")); }

        void reject(Throwable failure) {
            if (!compareAndSet(0, 1)) return;
            Runnable handle;
            synchronized (this) { handle = cancelHandle; }
            try { if (handle != null) handle.run(); }
            catch (Throwable cancelFailure) { if (cancelFailure != failure) failure.addSuppressed(cancelFailure); }
            finally { complete(null, failure); }
        }

        void handle(Runnable handle) {
            synchronized (this) { cancelHandle = handle; }
            if (get() == 2 && handle != null) handle.run();
        }

        private void complete(T value, Throwable failure) {
            set(2);
            try {
                if (failure == null) result.complete(value); else result.completeExceptionally(failure);
            } finally {
                synchronized (CloudTasks.this) { queued.remove(this); }
                finish(ticket);
            }
        }
    }
}
