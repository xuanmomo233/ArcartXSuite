package xuanmo.arcartxsuite.lottery;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** SQL and journals run on bounded workers. Only platform effects cross to the server thread. */
final class LotteryWork implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});
    private final Set<CompletableFuture<?>> pendingMain = new HashSet<>();
    private final ModuleExecutor worker = new ModuleExecutor(0, 2, 10, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "AXS-Lottery-Operations");
            thread.setDaemon(true);
            return thread;
        });
    private volatile boolean closed;

    LotteryWork(JavaPlugin plugin) { this.plugin = plugin; }

    <T> CompletableFuture<T> submit(Callable<T> job, boolean drain) {
        if (closed) return CompletableFuture.failedFuture(stopping());
        var result = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try {
                    if (closed && !drain) throw stopping();
                    result.complete(job.call());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    /** Only storage workers wait for platform effects, and shutdown releases work not yet started on Main. */
    <T> T main(Supplier<T> action) {
        if (Bukkit.isPrimaryThread()) throw new IllegalStateException("Lottery effects must be requested by a worker");
        var result = new CompletableFuture<T>();
        synchronized (pendingMain) {
            if (closed) throw stopping();
            pendingMain.add(result);
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> callbacks.call(() -> {
                synchronized (pendingMain) {
                    if (!pendingMain.remove(result) || closed) {
                        result.completeExceptionally(stopping());
                        return null;
                    }
                }
                try { result.complete(action.get()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
                return null;
            }, null));
            return result.join();
        } finally {
            synchronized (pendingMain) { pendingMain.remove(result); }
        }
    }

    void reply(Runnable action) {
        if (closed) return;
        Runnable guarded = () -> callbacks.call(() -> {
            if (!closed) action.run();
            return null;
        }, null);
        if (Bukkit.isPrimaryThread()) guarded.run();
        else try {
            Bukkit.getScheduler().runTask(plugin, guarded);
        } catch (org.bukkit.plugin.IllegalPluginAccessException stopping) {
            // The host already stopped accepting callbacks.
        }
    }

    static final class BeforeEffect extends java.util.concurrent.CancellationException {
        BeforeEffect(String message) { super(message); }
    }

    private static BeforeEffect stopping() { return new BeforeEffect("Lottery is stopping"); }

    @Override public void close() {
        Set<CompletableFuture<?>> abandoned;
        synchronized (pendingMain) {
            if (closed) return;
            closed = true;
            abandoned = Set.copyOf(pendingMain);
            pendingMain.clear();
        }
        callbacks.close();
        for (var result : abandoned) result.completeExceptionally(stopping());
        worker.shutdown();
    }

    CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(worker.stopped().toCompletableFuture(), callbacks.stopped().toCompletableFuture());
    }
}
