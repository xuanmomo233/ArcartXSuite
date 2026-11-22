package xuanmo.arcartxsuite.regions.service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** 在主线程准备和发布变更，在专用后台线程提交文件。 */
final class RegionWriteQueue {
    private static final int MAX_PENDING = 128;

    private final Executor primary;
    private final Executor writer;
    private final Queue<Request<?>> waiting = new ArrayDeque<>();
    private Active<?> active;
    private boolean draining;
    private boolean closed;
    private boolean suspended;

    RegionWriteQueue(Executor primary, Executor writer) {
        this.primary = primary;
        this.writer = writer;
    }

    <T> CompletableFuture<T> submit(Supplier<Transaction<T>> prepare) {
        if (closed || waiting.size() >= MAX_PENDING) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                closed ? "区域模块正在停止。" : "区域保存队列已满，请稍后再试。"));
        }
        var request = new Request<>(prepare, new CompletableFuture<T>());
        waiting.add(request);
        advance();
        return request.result();
    }

    private void advance() {
        if (draining) return;
        draining = true;
        try {
            while (!closed && !suspended && active == null && !waiting.isEmpty()) start(waiting.remove());
        } finally {
            draining = false;
        }
    }

    private <T> void start(Request<T> request) {
        try {
            Transaction<T> transaction = request.prepare().get();
            if (transaction.save() == null) {
                request.result().complete(transaction.result());
                return;
            }
            var write = CompletableFuture.runAsync(() -> {
                try {
                    transaction.save().run();
                } catch (IOException error) {
                    throw new CompletionException(error);
                }
            }, writer);
            var job = new Active<>(request, transaction);
            active = job;
            write.whenComplete((ignored, error) -> primary.execute(() -> finish(job, error)));
        } catch (RuntimeException error) {
            request.result().completeExceptionally(error);
        }
    }

    private <T> void finish(Active<T> job, Throwable failure) {
        if (active != job) return;
        try {
            if (failure == null) {
                if (!closed) job.transaction().publish().run();
                job.request().result().complete(job.transaction().result());
            } else {
                job.request().result().completeExceptionally(
                    failure instanceof CompletionException ? failure.getCause() : failure);
            }
        } catch (RuntimeException error) {
            job.request().result().completeExceptionally(error instanceof CompletionException ? error.getCause() : error);
        } finally {
            active = null;
            advance();
        }
    }

    void suspend() { suspended = true; }

    void resume() {
        suspended = false;
        advance();
    }

    void close() {
        closed = true;
        while (!waiting.isEmpty()) {
            waiting.remove().result().completeExceptionally(new IllegalStateException("区域模块正在停止，请重试。"));
        }
    }

    record Transaction<T>(T result, Save save, Runnable publish) {
        static <T> Transaction<T> unchanged(T result) {
            return new Transaction<>(result, null, null);
        }
    }

    @FunctionalInterface
    interface Save { void run() throws IOException; }

    private record Request<T>(Supplier<Transaction<T>> prepare, CompletableFuture<T> result) {}
    private record Active<T>(Request<T> request, Transaction<T> transaction) {}
}
