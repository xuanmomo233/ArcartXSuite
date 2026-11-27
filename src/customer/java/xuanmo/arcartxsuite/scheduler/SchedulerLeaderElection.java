package xuanmo.arcartxsuite.scheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import xuanmo.arcartxsuite.crossserver.CrossServerService;

/** Distributed leader election for the customer edition. */
final class SchedulerLeaderElection {
    private static final long LEADER_LEASE_TTL_MS = 90_000L;
    private static final String REDIS_LEADER_KEY_PREFIX = "axs:scheduler:leader:";

    private final Logger logger;
    private final String nodeId;
    private final CrossServerService crossServerService;
    private final DataSource dataSource;
    private final String leaderTable;
    private final Object leaderTableLock = new Object();
    private boolean leaderTableInitialized;

    SchedulerLeaderElection(Logger logger, String nodeId, CrossServerService crossServerService,
                            DataSource dataSource, String leaderTable) {
        this.logger = logger;
        this.nodeId = nodeId;
        this.crossServerService = crossServerService;
        this.dataSource = dataSource;
        this.leaderTable = leaderTable;
    }

    boolean requiresHeartbeat() { return true; }
    boolean hasDatabase() { return dataSource != null; }

    private boolean allowsDistributedScheduling() {
        return crossServerService == null || crossServerService.allowsDistributedScheduling();
    }

    // ── 选主实现：Redis 优先 + DB 兜底 ────────────────────

    boolean isRedisAvailable() {
        return crossServerService != null && crossServerService.isRedisAvailable();
    }

    boolean isLeader(String leaderKey) {
        if (!allowsDistributedScheduling()) return false;
        // 单服无跨服：本节点即主
        if (!isRedisAvailable() && dataSource == null) {
            return true;
        }
        if (isRedisAvailable()) {
            return isLeaderRedis(leaderKey);
        }
        return isLeaderDb(leaderKey);
    }

    void refreshHeartbeat(String leaderKey) {
        if (!allowsDistributedScheduling()) return;
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
        if (!allowsDistributedScheduling()) return false;
        if (dataSource == null) return true;
        try {
            String owner = readLeaderOwner(leaderKey);
            if (owner == null) {
                // A just-registered calendar can be due before the first async heartbeat.
                // Match Redis's first-use acquisition without holding a second pool connection.
                refreshLeaderDb(leaderKey);
                owner = readLeaderOwner(leaderKey);
            }
            return allowsDistributedScheduling() && nodeId.equals(owner);
        } catch (SQLException e) {
            logger.log(Level.FINE, "[Scheduler] DB isLeader 失败: " + leaderKey, e);
            return allowsDistributedScheduling(); // 获得授权时保留 DB 不可用的本地降级。
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
        if (!allowsDistributedScheduling() || dataSource == null) return;
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

}
