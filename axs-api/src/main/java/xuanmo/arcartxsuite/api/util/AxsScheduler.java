package xuanmo.arcartxsuite.api.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 统一调度器，兼容 Folia / Paper / Spigot。
 * <p>
 * 参考 Asteroid 的 FoliaScheduler 设计，在 AXS 内部实现，不引入外部依赖。
 * Folia 环境下通过反射调用 Folia 的各调度器；非 Folia 环境直接委托 Bukkit.getScheduler()。
 * 所有 runTask* 方法返回 BukkitTask，Folia 环境下用 FoliaTaskWrapper 包装。
 */
public final class AxsScheduler {

    private AxsScheduler() {}

    // ── 同步任务 ──────────────────────────────────────────────

    public static BukkitTask runTask(Plugin plugin, Runnable task) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaGlobalRun(plugin, task));
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaGlobalRunDelayed(plugin, task, delayTicks));
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaGlobalRunAtFixedRate(plugin, task, delayTicks, periodTicks));
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    // ── 实体/区域相关同步任务 ─────────────────────────────────

    public static BukkitTask runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaEntityRun(plugin, entity, task));
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public static BukkitTask runLocationTask(Plugin plugin, Location location, Runnable task) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaRegionRun(plugin, location, task));
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    // ── 异步任务 ──────────────────────────────────────────────

    public static BukkitTask runAsync(Plugin plugin, Runnable task) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaAsyncRunNow(plugin, task));
        }
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static BukkitTask runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaAsyncRunDelayed(plugin, task, delayTicks * 50, TimeUnit.MILLISECONDS));
        }
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    public static BukkitTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (PlatformCompat.isFolia()) {
            return wrap(foliaAsyncRunAtFixedRate(plugin, task, delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS));
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    // ── 取消任务 ──────────────────────────────────────────────

    public static void cancelTask(Object taskHandle) {
        if (taskHandle == null) return;
        if (taskHandle instanceof BukkitTask) {
            ((BukkitTask) taskHandle).cancel();
        } else {
            try {
                Method cancelMethod = taskHandle.getClass().getMethod("cancel");
                cancelMethod.invoke(taskHandle);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[AXS] Failed to cancel task", e);
            }
        }
    }

    public static void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
    }

    public static <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
        if (PlatformCompat.isFolia()) {
            return foliaCallSyncMethod(plugin, task);
        }
        return Bukkit.getScheduler().callSyncMethod(plugin, task);
    }

    // ── Folia 包装 ────────────────────────────────────────────

    private static BukkitTask wrap(Object foliaHandle) {
        if (foliaHandle == null) return null;
        if (foliaHandle instanceof BukkitTask) return (BukkitTask) foliaHandle;
        return new FoliaTaskWrapper(foliaHandle);
    }

    private static final class FoliaTaskWrapper implements BukkitTask {
        private final Object handle;
        private final Method cancelMethod;
        private final Method getTaskIdMethod;
        private final Method getOwnerMethod;
        private volatile boolean cancelled;

        FoliaTaskWrapper(Object handle) {
            this.handle = handle;
            Method cancel = null, getTaskId = null, getOwner = null;
            try { cancel = handle.getClass().getMethod("cancel"); } catch (Exception ignored) {}
            try { getTaskId = handle.getClass().getMethod("getTaskId"); } catch (Exception ignored) {}
            try { getOwner = handle.getClass().getMethod("getOwner"); } catch (Exception ignored) {}
            this.cancelMethod = cancel;
            this.getTaskIdMethod = getTaskId;
            this.getOwnerMethod = getOwner;
        }

        @Override public int getTaskId() {
            if (getTaskIdMethod != null) {
                try { return (int) getTaskIdMethod.invoke(handle); } catch (Exception ignored) {}
            }
            return -1;
        }

        @Override public Plugin getOwner() {
            if (getOwnerMethod != null) {
                try { return (Plugin) getOwnerMethod.invoke(handle); } catch (Exception ignored) {}
            }
            return null;
        }

        @Override public void cancel() {
            if (cancelled) return;
            cancelled = true;
            if (cancelMethod != null) {
                try { cancelMethod.invoke(handle); } catch (Exception ignored) {}
            }
        }

        @Override public boolean isCancelled() {
            return cancelled;
        }

        @Override public boolean isSync() {
            return false;
        }
    }

    // ── Folia 反射调用 ────────────────────────────────────────

    private static Object foliaGlobalRun(Plugin plugin, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            return runMethod.invoke(scheduler, plugin, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia GlobalRegionScheduler.run", e);
            return null;
        }
    }

    private static Object foliaGlobalRunDelayed(Plugin plugin, Runnable task, long delayTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delayTicks));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia GlobalRegionScheduler.runDelayed", e);
            return null;
        }
    }

    private static Object foliaGlobalRunAtFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delayTicks), Math.max(1, periodTicks));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia GlobalRegionScheduler.runAtFixedRate", e);
            return null;
        }
    }

    private static Object foliaEntityRun(Plugin plugin, Entity entity, Runnable task) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Consumer<Object> consumer = t -> task.run();
            Runnable retired = () -> {};
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            return method.invoke(scheduler, plugin, consumer, retired);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia EntityScheduler.run", e);
            return null;
        }
    }

    private static Object foliaRegionRun(Plugin plugin, Location location, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            return method.invoke(scheduler, plugin, location, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia RegionScheduler.run", e);
            return null;
        }
    }

    private static Object foliaAsyncRunNow(Plugin plugin, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            return method.invoke(scheduler, plugin, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia AsyncScheduler.runNow", e);
            return null;
        }
    }

    private static Object foliaAsyncRunDelayed(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
            return method.invoke(scheduler, plugin, consumer, delay, unit);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia AsyncScheduler.runDelayed", e);
            return null;
        }
    }

    private static Object foliaAsyncRunAtFixedRate(Plugin plugin, Runnable task, long delay, long period, TimeUnit unit) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = t -> task.run();
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delay), Math.max(1, period), unit);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia AsyncScheduler.runAtFixedRate", e);
            return null;
        }
    }

    private static <T> Future<T> foliaCallSyncMethod(Plugin plugin, Callable<T> task) {
        java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = t -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            };
            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            runMethod.invoke(scheduler, plugin, consumer);
            return future;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AXS] Failed to invoke Folia callSyncMethod", e);
            future.completeExceptionally(e);
            return future;
        }
    }
}
