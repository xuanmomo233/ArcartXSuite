package xuanmo.arcartxsuite.module;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/** 一次模块启用期间的任务集合，不创建线程或轮询任务。 */
final class ModuleScheduler implements SchedulerAPI {
    private final SchedulerAPI delegate;
    private final Logger logger;
    private final Set<Task> tasks = new HashSet<>();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private boolean closed;
    private boolean cancelledAll;
    private int running;
    private int registering;

    ModuleScheduler(SchedulerAPI delegate, Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    private ScheduledTask register(Runnable action, boolean once, Function<Runnable, ScheduledTask> schedule) {
        Task task = new Task(action, once);
        synchronized (this) {
            if (closed) throw new RejectedExecutionException("模块定时任务已停止");
            tasks.add(task);
            registering++;
        }
        try {
            ScheduledTask handle = schedule.apply(task);
            boolean cancel;
            synchronized (this) {
                task.handle = handle;
                if (closed || task.finished) {
                    task.cancelled = true;
                    tasks.remove(task);
                }
                cancel = task.cancelled;
            }
            if (cancel) handle.cancel();
            return task;
        } catch (RuntimeException | Error failure) {
            task.cancel();
            throw failure;
        } finally {
            boolean complete;
            synchronized (this) {
                registering--;
                complete = idleAfterClose();
            }
            if (complete) stopped.complete(null);
        }
    }

    CompletionStage<Void> close() {
        Task[] snapshot;
        synchronized (this) {
            if (closed) return stopped.copy();
            closed = true;
            snapshot = tasks.toArray(Task[]::new);
            tasks.clear();
        }
        for (Task task : snapshot) {
            try { task.cancel(); }
            catch (RuntimeException | LinkageError failure) { logger.warning("取消模块定时任务失败: " + failure); }
        }
        boolean complete;
        synchronized (this) {
            cancelledAll = true;
            complete = idleAfterClose();
        }
        if (complete) stopped.complete(null);
        return stopped.copy();
    }

    private boolean idleAfterClose() {
        return closed && cancelledAll && running == 0 && registering == 0;
    }

    int taskCount() { synchronized (this) { return tasks.size(); } }

    @Override public ScheduledTask scheduleAtFixedRate(Plugin plugin, Runnable task, long delay, long period) {
        return register(task, false, run -> delegate.scheduleAtFixedRate(plugin, run, delay, period));
    }
    @Override public ScheduledTask scheduleAsyncAtFixedRate(Plugin plugin, Runnable task, long delay, long period) {
        return register(task, false, run -> delegate.scheduleAsyncAtFixedRate(plugin, run, delay, period));
    }
    @Override public ScheduledTask scheduleOnce(Plugin plugin, Runnable task, long delay) {
        return register(task, true, run -> delegate.scheduleOnce(plugin, run, delay));
    }
    @Override public ScheduledTask scheduleOnceAsync(Plugin plugin, Runnable task, long delay) {
        return register(task, true, run -> delegate.scheduleOnceAsync(plugin, run, delay));
    }
    @Override public ScheduledTask scheduleCalendar(Plugin plugin, Runnable task, ScheduleSpec spec) {
        return register(task, false, run -> delegate.scheduleCalendar(plugin, run, spec));
    }
    @Override public ScheduledTask scheduleCalendarAsync(Plugin plugin, Runnable task, ScheduleSpec spec) {
        return register(task, false, run -> delegate.scheduleCalendarAsync(plugin, run, spec));
    }
    @Override public ScheduledTask scheduleCalendarLeaderOnly(String key, Plugin plugin, Runnable task, ScheduleSpec spec) {
        return register(task, false, run -> delegate.scheduleCalendarLeaderOnly(key, plugin, run, spec));
    }

    private final class Task implements Runnable, ScheduledTask {
        private final Runnable action;
        private final boolean once;
        private volatile boolean cancelled;
        private boolean finished;
        private ScheduledTask handle;

        Task(Runnable action, boolean once) { this.action = action; this.once = once; }

        @Override public void run() {
            synchronized (ModuleScheduler.this) {
                if (closed || cancelled || finished) return;
                if (once) {
                    finished = true;
                    tasks.remove(this);
                }
                running++;
            }
            try { action.run(); }
            finally {
                boolean complete;
                synchronized (ModuleScheduler.this) {
                    running--;
                    complete = idleAfterClose();
                }
                if (complete) stopped.complete(null);
            }
        }

        @Override public void cancel() {
            ScheduledTask current;
            synchronized (ModuleScheduler.this) {
                if (cancelled) return;
                cancelled = true;
                tasks.remove(this);
                current = handle;
            }
            if (current != null) current.cancel();
        }

        @Override public boolean isCancelled() { return cancelled; }
    }
}
