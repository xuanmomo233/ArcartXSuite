package xuanmo.arcartxsuite.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Main-thread detachment followed by background resource closure after every producer stops. */
public final class ShutdownSequence {
    private final Logger logger;
    private final List<CompletableFuture<?>> pending = new ArrayList<>();

    public ShutdownSequence(Logger logger) { this.logger = logger; }

    public void stop(String name, Runnable action) {
        await(name, () -> { action.run(); return CompletableFuture.completedFuture(null); });
    }

    public void await(String name, Supplier<? extends CompletionStage<?>> action) {
        try {
            pending.add(action.get().whenComplete((ignored, failure) -> {
                if (failure != null) log(name, failure);
            }).toCompletableFuture());
        } catch (Throwable failure) {
            log(name, failure);
            pending.add(CompletableFuture.failedFuture(failure));
        }
    }

    private void log(String name, Throwable failure) {
        try { logger.log(Level.WARNING, "宿主收尾失败: " + name, failure); }
        catch (Throwable ignoredLogging) { }
    }

    public CompletionStage<Void> finish(Supplier<? extends CompletionStage<Void>> closeStorage) {
        var worker = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "AXS-Host-Retire");
            thread.setDaemon(true);
            return thread;
        });
        var finalized = new CompletableFuture<Void>();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((ignored, priorFailure) ->
            worker.execute(() -> {
                try {
                    closeStorage.get().whenComplete((unused, storageFailure) -> {
                        Throwable failure = priorFailure;
                        if (failure == null) failure = storageFailure;
                        else if (storageFailure != null && storageFailure != failure) failure.addSuppressed(storageFailure);
                        if (failure == null) finalized.complete(null);
                        else finalized.completeExceptionally(failure);
                    });
                } catch (Throwable failure) {
                    if (priorFailure != null && priorFailure != failure) failure.addSuppressed(priorFailure);
                    finalized.completeExceptionally(failure);
                } finally { worker.shutdown(); }
            }));
        return CompletableFuture.allOf(finalized, worker.stopped().toCompletableFuture());
    }
}
