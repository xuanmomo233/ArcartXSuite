package xuanmo.arcartxsuite.scheduler;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

import xuanmo.arcartxsuite.crossserver.CrossServerService;
import org.bukkit.Bukkit;

/**
 * {@link SchedulerAPI} 的默认实现。
 * <p>
 * 固定间隔调度委托 {@code Bukkit.getScheduler()}；
 * 日历触发调度通过一个每分钟 tick 的守护任务检查所有注册的日历规格；
 * 选主由发行版提供，单服构建的任务在本机运行。
 *
 * @since 1.6.0
 */
public final class SchedulerServiceImpl implements SchedulerAPI {

    private final Plugin plugin;
    private final Logger logger;
    private final String nodeId;
    private final SchedulerLeaderElection leaderElection;

    // 日历任务注册表：taskKey -> CalendarEntry
    private final ConcurrentHashMap<String, CalendarEntry> calendarTasks = new ConcurrentHashMap<>();
    // 跨服单点任务注册表：taskKey -> LeaderEntry
    private final ConcurrentHashMap<String, LeaderEntry> leaderTasks = new ConcurrentHashMap<>();

    private org.bukkit.scheduler.BukkitTask minuteDaemonTask;
    private org.bukkit.scheduler.BukkitTask leaderHeartbeatTask;
    private final Object daemonLock = new Object();
    private boolean started;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback leaderHeartbeat =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::heartbeatLeaders);

    // 选主心跳间隔（毫秒）
    private static final long LEADER_HEARTBEAT_INTERVAL_MS = 30_000L;

    public SchedulerServiceImpl(
        @NotNull Plugin plugin,
        @NotNull Logger logger,
        @NotNull String nodeId,
        @Nullable CrossServerService crossServerService,
        @Nullable DataSource dataSource,
        @NotNull String leaderTable
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.nodeId = nodeId;
        this.leaderElection = new SchedulerLeaderElection(logger, nodeId, crossServerService, dataSource, leaderTable);
    }

    /** 启动调度服务（由宿主在 onEnable 时调用）。 */
    public void start() {
        synchronized (daemonLock) {
            if (started) return;
            started = true;
            reconcileDaemons();
        }

        logger.fine("[Scheduler] 调度服务已启动 (nodeId=" + nodeId
            + ", redis=" + (leaderElection.isRedisAvailable() ? "ON" : "OFF")
            + ", db=" + (leaderElection.hasDatabase() ? "ON" : "OFF") + ")");
    }

    /** 关闭调度服务（由宿主在 onDisable 时调用）。 */
    public java.util.concurrent.CompletionStage<Void> shutdown() {
        synchronized (daemonLock) {
            started = false;
            reconcileDaemons();
        }
        leaderHeartbeat.close();
        calendarTasks.values().forEach(CalendarEntry::cancel);
        calendarTasks.clear();
        leaderTasks.values().forEach(LeaderEntry::cancel);
        leaderTasks.clear();
        return leaderHeartbeat.stopped();
    }

    /** Own timers only while a registered task needs them. Registration before start remains valid. */
    private void reconcileDaemons() {
        synchronized (daemonLock) {
            if (!started || calendarTasks.isEmpty()) {
                if (minuteDaemonTask != null) { minuteDaemonTask.cancel(); minuteDaemonTask = null; }
            } else if (minuteDaemonTask == null || minuteDaemonTask.isCancelled()) {
                // Check the current minute on first registration; keep the normal minute cadence.
                minuteDaemonTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCalendarTasks, 0L, 1200L);
            }
            if (!started || leaderTasks.isEmpty() || !leaderElection.requiresHeartbeat()) {
                if (leaderHeartbeatTask != null) { leaderHeartbeatTask.cancel(); leaderHeartbeatTask = null; }
            } else if (leaderHeartbeatTask == null || leaderHeartbeatTask.isCancelled()) {
                leaderHeartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, leaderHeartbeat,
                    0L, LEADER_HEARTBEAT_INTERVAL_MS / 50);
            }
        }
    }

    // ── 固定间隔调度 ──────────────────────────────────────

    @Override
    public @NotNull ScheduledTask scheduleAtFixedRate(@NotNull Plugin p, @NotNull Runnable task,
                                                      long delayTicks, long periodTicks) {
        org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimer(p, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bt);
    }

    @Override
    public @NotNull ScheduledTask scheduleAsyncAtFixedRate(@NotNull Plugin p, @NotNull Runnable task,
                                                           long delayTicks, long periodTicks) {
        org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(p, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bt);
    }

    // ── 一次性延迟调度 ──────────────────────────────────────

    @Override
    public @NotNull ScheduledTask scheduleOnce(@NotNull Plugin p, @NotNull Runnable task,
                                               long delayTicks) {
        org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskLater(p, task, delayTicks);
        return new BukkitTaskWrapper(bt);
    }

    @Override
    public @NotNull ScheduledTask scheduleOnceAsync(@NotNull Plugin p, @NotNull Runnable task,
                                                    long delayTicks) {
        org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskLaterAsynchronously(p, task, delayTicks);
        return new BukkitTaskWrapper(bt);
    }

    // ── 日历触发调度 ──────────────────────────────────────

    @Override
    public @NotNull ScheduledTask scheduleCalendar(@NotNull Plugin p, @NotNull Runnable task,
                                                   @NotNull ScheduleSpec spec) {
        if (spec.mode() == ScheduleSpec.Mode.INTERVAL) {
            // INTERVAL 模式直接用固定间隔调度
            return scheduleAtFixedRate(p, task, spec.intervalTicks(), spec.intervalTicks());
        }
        String key = "calendar:" + UUID.randomUUID();
        CalendarEntry entry = new CalendarEntry(key, p, task, spec, false, null);
        calendarTasks.put(key, entry);
        reconcileDaemons();
        return entry;
    }

    @Override
    public @NotNull ScheduledTask scheduleCalendarAsync(@NotNull Plugin p, @NotNull Runnable task,
                                                        @NotNull ScheduleSpec spec) {
        if (spec.mode() == ScheduleSpec.Mode.INTERVAL) {
            return scheduleAsyncAtFixedRate(p, task, spec.intervalTicks(), spec.intervalTicks());
        }
        String key = "calendar:" + UUID.randomUUID();
        CalendarEntry entry = new CalendarEntry(key, p, task, spec, true, null);
        calendarTasks.put(key, entry);
        reconcileDaemons();
        return entry;
    }

    // ── 跨服单点调度 ──────────────────────────────────────

    @Override
    public @NotNull ScheduledTask scheduleCalendarLeaderOnly(@NotNull String taskKey, @NotNull Plugin p,
                                                             @NotNull Runnable task, @NotNull ScheduleSpec spec) {
        if (spec.mode() == ScheduleSpec.Mode.INTERVAL) {
            // INTERVAL 模式的跨服单点：用固定间隔 + 选主检查
            String leaderKey = "leader:" + taskKey;
            LeaderEntry entry = new LeaderEntry(leaderKey, p, task, spec, null);
            // 固定间隔调度，每次执行前检查是否为主
            org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimer(p,
                () -> executeIfLeader(entry), spec.intervalTicks(), spec.intervalTicks());
            entry.setUnderlyingTask(bt);
            leaderTasks.put(leaderKey, entry);
            reconcileDaemons();
            return entry;
        }
        // HOURLY/DAILY/WEEKLY 模式：加入日历守护，tick 时检查选主
        String leaderKey = "leader:" + taskKey;
        LeaderEntry entry = new LeaderEntry(leaderKey, p, task, spec, taskKey);
        calendarTasks.put(leaderKey, entry);
        leaderTasks.put(leaderKey, entry);
        reconcileDaemons();
        return entry;
    }

    // ── 内部：日历 tick ────────────────────────────────────

    private void tickCalendarTasks() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        for (CalendarEntry entry : calendarTasks.values()) {
            if (entry.isCancelled()) {
                calendarTasks.remove(entry.key, entry);
                continue;
            }
            if (entry.shouldExecute(now)) {
                try {
                    entry.execute();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[Scheduler] 日历任务执行异常: " + entry.key, t);
                }
            }
        }
    }

    // ── 内部：选主心跳 ────────────────────────────────────

    private void heartbeatLeaders() {
        for (LeaderEntry entry : leaderTasks.values()) {
            if (entry.isCancelled()) {
                leaderTasks.remove(entry.leaderKey, entry);
                continue;
            }
            try {
                leaderElection.refreshHeartbeat(entry.leaderKey);
            } catch (Exception e) {
                logger.log(Level.FINE, "[Scheduler] 选主心跳失败: " + entry.leaderKey, e);
            }
        }
        reconcileDaemons();
    }

    private void executeIfLeader(LeaderEntry entry) {
        if (leaderElection.isLeader(entry.leaderKey)) {
            try {
                entry.execute();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[Scheduler] 跨服单点任务执行异常: " + entry.leaderKey, t);
            }
        }
    }

    // ── 内部类 ────────────────────────────────────────────

    /** 日历任务条目。 */
    private class CalendarEntry implements ScheduledTask {
        final String key;
        final Plugin plugin;
        final Runnable task;
        final ScheduleSpec spec;
        final boolean async;
        final @Nullable String leaderTaskKey; // 非空表示需要选主检查
        private volatile boolean cancelled;
        private @Nullable ZonedDateTime lastExecution;

        CalendarEntry(String key, Plugin plugin, Runnable task, ScheduleSpec spec,
                      boolean async, @Nullable String leaderTaskKey) {
            this.key = key;
            this.plugin = plugin;
            this.task = task;
            this.spec = spec;
            this.async = async;
            this.leaderTaskKey = leaderTaskKey;
        }

        boolean shouldExecute(ZonedDateTime now) {
            ZonedDateTime next = spec.nextExecutionAfter(lastExecution != null ? lastExecution : now.minusMinutes(1));
            if (next == null) return false;
            // 如果当前时间已过下次执行时间（允许 1 分钟内的误差）
            return !now.isBefore(next) && (lastExecution == null || next.isAfter(lastExecution));
        }

        void execute() {
            if (leaderTaskKey != null && !leaderElection.isLeader(leaderTaskKey)) {
                return; // 非主节点，跳过
            }
            lastExecution = ZonedDateTime.now(ZoneId.systemDefault());
            if (async) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                if (org.bukkit.Bukkit.isPrimaryThread()) {
                    task.run();
                } else {
                    Bukkit.getScheduler().runTask(plugin, task);
                }
            }
        }

        @Override public void cancel() {
            cancelled = true;
            calendarTasks.remove(key, this);
            reconcileDaemons();
        }
        @Override public boolean isCancelled() { return cancelled; }
    }

    /** 跨服单点任务条目（继承 CalendarEntry，复用日历 tick + 选主检查逻辑）。 */
    private class LeaderEntry extends CalendarEntry {
        final String leaderKey;
        final @Nullable String taskKey; // 用于日历模式选主检查
        private volatile org.bukkit.scheduler.BukkitTask underlyingTask;

        LeaderEntry(String leaderKey, Plugin plugin, Runnable task, ScheduleSpec spec,
                    @Nullable String taskKey) {
            // Calendar execution and heartbeat must refer to the same lease key.
            super(leaderKey, plugin, task, spec, false, taskKey == null ? null : leaderKey);
            this.leaderKey = leaderKey;
            this.taskKey = taskKey;
        }

        void setUnderlyingTask(org.bukkit.scheduler.BukkitTask task) {
            this.underlyingTask = task;
        }

        @Override public void cancel() {
            super.cancel();
            leaderTasks.remove(leaderKey, this);
            if (underlyingTask != null) underlyingTask.cancel();
            reconcileDaemons();
        }
    }

    /** BukkitTask 包装。 */
    private static class BukkitTaskWrapper implements ScheduledTask {
        private final org.bukkit.scheduler.BukkitTask task;
        BukkitTaskWrapper(org.bukkit.scheduler.BukkitTask task) { this.task = task; }
        @Override public void cancel() { task.cancel(); }
        @Override public boolean isCancelled() { return task.isCancelled(); }
    }
}
