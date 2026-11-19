package xuanmo.arcartxsuite.scheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
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
 * 跨服单点调度通过 Redis 分布式锁（优先）或数据库租约表（兜底）实现选主。
 *
 * @since 1.6.0
 */
public final class SchedulerServiceImpl implements SchedulerAPI {

    private final Plugin plugin;
    private final Logger logger;
    private final String nodeId;
    private final @Nullable CrossServerService crossServerService;
    private final @Nullable DataSource dataSource;
    private final String leaderTable;

    // 日历任务注册表：taskKey -> CalendarEntry
    private final ConcurrentHashMap<String, CalendarEntry> calendarTasks = new ConcurrentHashMap<>();
    // 跨服单点任务注册表：taskKey -> LeaderEntry
    private final ConcurrentHashMap<String, LeaderEntry> leaderTasks = new ConcurrentHashMap<>();

    private org.bukkit.scheduler.BukkitTask minuteDaemonTask;
    private org.bukkit.scheduler.BukkitTask leaderHeartbeatTask;
    private final Object daemonLock = new Object();
    private boolean started;
    private final Object leaderTableLock = new Object();
    private boolean leaderTableInitialized;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback leaderHeartbeat =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::heartbeatLeaders);

    // 选主租约过期时间（毫秒）
    private static final long LEADER_LEASE_TTL_MS = 90_000L;
    // 选主心跳间隔（毫秒）
    private static final long LEADER_HEARTBEAT_INTERVAL_MS = 30_000L;
    // Redis 选主 key 前缀
    private static final String REDIS_LEADER_KEY_PREFIX = "axs:scheduler:leader:";

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
        this.crossServerService = crossServerService;
        this.dataSource = dataSource;
        this.leaderTable = leaderTable;
    }

    /** 启动调度服务（由宿主在 onEnable 时调用）。 */
    public void start() {
        synchronized (daemonLock) {
            if (started) return;
            started = true;
            reconcileDaemons();
        }

        logger.fine("[Scheduler] 调度服务已启动 (nodeId=" + nodeId
            + ", redis=" + (isRedisAvailable() ? "ON" : "OFF")
            + ", db=" + (dataSource != null ? "ON" : "OFF") + ")");
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
            if (!started || leaderTasks.isEmpty()) {
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
                refreshLeaderHeartbeat(entry.leaderKey);
            } catch (Exception e) {
                logger.log(Level.FINE, "[Scheduler] 选主心跳失败: " + entry.leaderKey, e);
            }
        }
        reconcileDaemons();
    }

    private void executeIfLeader(LeaderEntry entry) {
        if (isLeader(entry.leaderKey)) {
            try {
                entry.execute();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[Scheduler] 跨服单点任务执行异常: " + entry.leaderKey, t);
            }
        }
    }

    // ── 选主实现：Redis 优先 + DB 兜底 ────────────────────

    private boolean isRedisAvailable() {
        return crossServerService != null && crossServerService.isRedisAvailable();
    }

    private boolean isLeader(String leaderKey) {
        // 单服无跨服：本节点即主
        if (!isRedisAvailable() && dataSource == null) {
            return true;
        }
        if (isRedisAvailable()) {
            return isLeaderRedis(leaderKey);
        }
        return isLeaderDb(leaderKey);
    }

    private void refreshLeaderHeartbeat(String leaderKey) {
        if (!isRedisAvailable() && dataSource == null) return;
        if (isRedisAvailable()) {
            refreshLeaderRedis(leaderKey);
        } else if (dataSource != null) {
            refreshLeaderDb(leaderKey);
        }
    }

    // Redis 选主：SET NX + 过期时间
    private boolean isLeaderRedis(String leaderKey) {
        JedisPool pool = crossServerService.getJedisPoolInternal();
        if (pool == null) return false;
        String key = REDIS_LEADER_KEY_PREFIX + leaderKey;
        try (Jedis jedis = pool.getResource()) {
            String owner = jedis.get(key);
            if (owner == null) {
                // 无主，尝试抢占
                SetParams params = SetParams.setParams().nx().px(LEADER_LEASE_TTL_MS);
                String result = jedis.set(key, nodeId, params);
                return "OK".equals(result);
            }
            return nodeId.equals(owner);
        } catch (Exception e) {
            logger.log(Level.FINE, "[Scheduler] Redis isLeader 失败，降级到 DB", e);
            return isLeaderDb(leaderKey);
        }
    }

    private void refreshLeaderRedis(String leaderKey) {
        JedisPool pool = crossServerService.getJedisPoolInternal();
        if (pool == null) return;
        String key = REDIS_LEADER_KEY_PREFIX + leaderKey;
        try (Jedis jedis = pool.getResource()) {
            String owner = jedis.get(key);
            if (nodeId.equals(owner)) {
                // 续期
                jedis.pexpire(key, LEADER_LEASE_TTL_MS);
            } else if (owner == null) {
                // 无主，尝试抢占
                SetParams params = SetParams.setParams().nx().px(LEADER_LEASE_TTL_MS);
                jedis.set(key, nodeId, params);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "[Scheduler] Redis heartbeat 失败，降级到 DB", e);
            if (dataSource != null) refreshLeaderDb(leaderKey);
        }
    }

    // DB 选主：租约表
    private boolean isLeaderDb(String leaderKey) {
        if (dataSource == null) return true;
        try {
            String owner = readLeaderOwner(leaderKey);
            if (owner == null) {
                // A just-registered calendar can be due before the first async heartbeat.
                // Match Redis's first-use acquisition without holding a second pool connection.
                refreshLeaderDb(leaderKey);
                owner = readLeaderOwner(leaderKey);
            }
            return nodeId.equals(owner);
        } catch (SQLException e) {
            logger.log(Level.FINE, "[Scheduler] DB isLeader 失败: " + leaderKey, e);
            return true; // 保留原有 DB 不可用时的本地降级策略。
        }
    }

    private String readLeaderOwner(String leaderKey) throws SQLException {
        try (Connection conn = openLeaderConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT node_id FROM " + leaderTable + " WHERE task_key = ?")) {
            ps.setString(1, leaderKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("node_id");
                }
                return null;
            }
        }
    }

    private void refreshLeaderDb(String leaderKey) {
        if (dataSource == null) return;
        long now = System.currentTimeMillis();
        // 尝试续期自己的租约，或抢占过期租约
        try (Connection conn = openLeaderConnection()) {
            // 先查当前主
            String currentOwner = null;
            long currentHeartbeat = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT node_id, heartbeat_at FROM " + leaderTable + " WHERE task_key = ?")) {
                ps.setString(1, leaderKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentOwner = rs.getString("node_id");
                        currentHeartbeat = rs.getLong("heartbeat_at");
                    }
                }
            }
            if (nodeId.equals(currentOwner)) {
                // 续期
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + leaderTable + " SET heartbeat_at = ? WHERE task_key = ? AND node_id = ?")) {
                    ps.setLong(1, now);
                    ps.setString(2, leaderKey);
                    ps.setString(3, nodeId);
                    ps.executeUpdate();
                }
            } else if (currentOwner == null || (now - currentHeartbeat) > LEADER_LEASE_TTL_MS) {
                // 无主或租约过期，尝试抢占（UPSERT）
                try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + leaderTable + " WHERE task_key = ? AND (node_id = ? OR heartbeat_at < ?)")) {
                    ps.setString(1, leaderKey);
                    ps.setString(2, currentOwner == null ? "" : currentOwner);
                    ps.setLong(3, now - LEADER_LEASE_TTL_MS);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + leaderTable + " (task_key, node_id, heartbeat_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, leaderKey);
                    ps.setString(2, nodeId);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "[Scheduler] DB heartbeat 失败: " + leaderKey, e);
        }
    }

    private Connection openLeaderConnection() throws SQLException {
        synchronized (leaderTableLock) {
            if (!leaderTableInitialized) {
                initLeaderTable();
                leaderTableInitialized = true;
            }
        }
        return dataSource.getConnection();
    }

    private void initLeaderTable() throws SQLException {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 防注入校验
            if (!leaderTable.matches("[a-zA-Z0-9_]*")) {
                throw new IllegalStateException("非法选主表名: " + leaderTable);
            }
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + leaderTable + " (" +
                "task_key VARCHAR(128) PRIMARY KEY, " +
                "node_id VARCHAR(64) NOT NULL, " +
                "heartbeat_at BIGINT NOT NULL)");
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
            if (leaderTaskKey != null && !isLeader(leaderTaskKey)) {
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
