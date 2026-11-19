package xuanmo.arcartxsuite.market;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** One runtime owns its database tasks; closing the UI does not interrupt an accepted receipt. */
public final class MarketWork {
    private final Plugin plugin;
    // Match Bukkit's cached async dispatch. Business queues keep their own admission limits;
    // retirement must not introduce a new rejection point after a payment was already armed.
    private final ModuleExecutor worker = new ModuleExecutor(0, Integer.MAX_VALUE, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "AXS-Market-IO");
            thread.setDaemon(true);
            return thread;
        });
    private final List<ModuleCallback> callbacks = new ArrayList<>();
    private final List<org.bukkit.scheduler.BukkitTask> timers = new ArrayList<>();
    private volatile boolean stopping;
    private boolean draining;
    private boolean callbacksStopped;
    private int operations;
    private final ThreadLocal<Boolean> executing = ThreadLocal.withInitial(() -> false);

    public MarketWork(Plugin plugin) { this.plugin = plugin; }

    public synchronized void async(Runnable action) {
        if (worker.isShutdown() || (draining && callbacksStopped && !executing.get())) {
            throw new java.util.concurrent.RejectedExecutionException("Market 正在停用");
        }
        operations++;
        try {
            worker.execute(() -> {
                executing.set(true);
                try { action.run(); }
                finally {
                    executing.remove();
                    synchronized (MarketWork.this) {
                        operations--;
                        stopWorkerWhenDrained();
                    }
                }
            });
        } catch (RuntimeException | Error failure) {
            operations--;
            stopWorkerWhenDrained();
            throw failure;
        }
    }

    private void stopWorkerWhenDrained() {
        if (draining && callbacksStopped && operations == 0) worker.shutdown();
    }

    /** The wrapper is allocated once per periodic task, not once per tick. */
    public synchronized Runnable callback(Runnable action) {
        var callback = new ModuleCallback(action);
        if (stopping) callback.close();
        else callbacks.add(callback);
        return callback;
    }

    public void main(Runnable action) {
        if (stopping || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (!stopping) action.run(); });
    }

    public boolean isStopping() { return stopping; }

    public synchronized org.bukkit.scheduler.BukkitTask repeat(Runnable action, long delay, long period) {
        return timer(action, delay, period, false);
    }

    public synchronized org.bukkit.scheduler.BukkitTask repeatAsync(Runnable action, long delay, long period) {
        return timer(action, delay, period, true);
    }

    private org.bukkit.scheduler.BukkitTask timer(Runnable action, long delay, long period, boolean async) {
        if (stopping) throw new java.util.concurrent.RejectedExecutionException("Market 正在停用");
        var callback = new ModuleCallback(action);
        var scheduler = Bukkit.getScheduler();
        var task = async ? scheduler.runTaskTimerAsynchronously(plugin, callback, delay, period)
            : scheduler.runTaskTimer(plugin, callback, delay, period);
        org.bukkit.scheduler.BukkitTask owned = new org.bukkit.scheduler.BukkitTask() {
            @Override public int getTaskId() { return task.getTaskId(); }
            @Override public Plugin getOwner() { return task.getOwner(); }
            @Override public boolean isSync() { return task.isSync(); }
            @Override public boolean isCancelled() { return task.isCancelled(); }
            @Override public void cancel() {
                task.cancel();
                callback.close();
                callback.stopped().whenComplete((ignored, failure) -> {
                    synchronized (MarketWork.this) { callbacks.remove(callback); timers.remove(this); }
                });
            }
        };
        callbacks.add(callback);
        timers.add(owned);
        return owned;
    }

    public synchronized void stopCallbacks() {
        stopping = true;
        List.copyOf(timers).forEach(org.bukkit.scheduler.BukkitTask::cancel);
        List.copyOf(callbacks).forEach(ModuleCallback::close);
    }

    public synchronized CompletionStage<Void> drain() {
        if (draining) return worker.stopped();
        draining = true;
        stopCallbacks();
        CompletableFuture.allOf(callbacks.stream().map(callback -> callback.stopped().toCompletableFuture())
            .toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
                synchronized (MarketWork.this) {
                    callbacksStopped = true;
                    stopWorkerWhenDrained();
                }
            });
        return worker.stopped();
    }
}
