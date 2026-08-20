package xuanmo.arcartxsuite.eventpacket.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketPersistenceDialect;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketStorageConfiguration;

/**
 * 基于 JDBC 的事件包仓储实现。
 * <p>
 * 支持 SQLite 与 MySQL 两种方言，自动建表并提供击杀进度与
 * 已触发规则的增删改查能力。继承 {@link AbstractModuleRepository}
 * 以复用宿主统一的连接池与数据迁移基础设施。
 */
public final class JdbcEventPacketRepository extends AbstractModuleRepository implements EventPacketRepository {

    private final @Nullable EventPacketStorageConfiguration configuration;

    /**
     * 构造 JDBC 仓储实例。
     *
     * @param dataFolder     模块数据目录
     * @param configuration  存储配置
     * @param logger         日志记录器
     */
    public JdbcEventPacketRepository(File dataFolder, EventPacketStorageConfiguration configuration, Logger logger) {
        super("AXS-EventPacket", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    /**
     * 共享模式构造方法（1.4.0+）。
     * 使用本体统一数据源，模块只需提供表前缀和 SQLite 文件名。
     *
     * @param storageManager 本体数据源管理器
     * @param moduleId       模块 ID
     * @param tablePrefix    表前缀
     * @param sqliteFileName SQLite 文件名（SQLite 模式下使用）
     * @param logger         日志记录器
     * @since 1.4.0
     */
    public JdbcEventPacketRepository(StorageManager storageManager, String moduleId,
                                     String tablePrefix, String sqliteFileName, Logger logger) {
        super("AXS-EventPacket", storageManager, moduleId, tablePrefix, sqliteFileName, logger);
        this.configuration = null;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("eventpacket_kill_progress", "eventpacket_fired_rules");
    }

    // ─── Kill count ──────────────────────────────────────────

    /**
     * 查询指定玩家在某条规则下的累计击杀数。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 当前击杀数，无记录时返回 0
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public int getKillCount(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT kill_count FROM eventpacket_kill_progress WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("kill_count") : 0;
            }
        }
    }

    /**
     * 设置指定玩家在某条规则下的击杀数，使用 upsert 语义写入。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @param count      要设置的击杀数
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void setKillCount(UUID playerUuid, String ruleId, int count) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(killCountUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setInt(3, Math.max(0, count));
            statement.setLong(4, System.currentTimeMillis());
            if (isMysql()) {
                statement.setInt(5, Math.max(0, count));
                statement.setLong(6, System.currentTimeMillis());
            }
            statement.executeUpdate();
        }
    }

    /**
     * 将指定玩家在某条规则下的击杀数自增 1，按方言使用对应的 upsert 语句。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void incrementKillCount(UUID playerUuid, String ruleId) throws SQLException {
        if (!isMysql()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                     VALUES (?, ?, 1, ?)
                     ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                         kill_count = kill_count + 1,
                         updated_at = excluded.updated_at
                     """)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, ruleId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        } else {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                     VALUES (?, ?, 1, ?)
                     ON DUPLICATE KEY UPDATE
                         kill_count = kill_count + 1,
                         updated_at = VALUES(updated_at)
                     """)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, ruleId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    /**
     * 加载指定玩家所有规则的击杀进度。
     *
     * @param playerUuid 玩家 UUID
     * @return 规则 ID 到击杀数的映射
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public Map<String, Integer> loadAllKillCounts(UUID playerUuid) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT rule_id, kill_count FROM eventpacket_kill_progress WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString("rule_id"), resultSet.getInt("kill_count"));
                }
            }
        }
        return counts;
    }

    // ─── Fired rules ─────────────────────────────────────────

    /**
     * 判断指定玩家是否已触发过某条规则。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 已触发返回 {@code true}
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public boolean hasFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM eventpacket_fired_rules WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 标记指定玩家已触发某条规则，使用 upsert 语义写入。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void markFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(firedRuleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setLong(3, System.currentTimeMillis());
            if (isMysql()) {
                statement.setLong(4, System.currentTimeMillis());
            }
            statement.executeUpdate();
        }
    }

    /**
     * 移除指定玩家某条规则的已触发标记。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void removeFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM eventpacket_fired_rules WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.executeUpdate();
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────

    /**
     * 关闭仓储，释放底层连接池资源。
     */
    @Override
    public void close() {
        shutdown();
    }

    // ─── Internal ────────────────────────────────────────────

    private Connection connection() throws SQLException {
        return getConnection();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_kill_progress (
                        player_uuid TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        kill_count INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_fired_rules (
                        player_uuid TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        fired_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    )
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_kill_progress (
                        player_uuid VARCHAR(36) NOT NULL,
                        rule_id VARCHAR(128) NOT NULL,
                        kill_count INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_fired_rules (
                        player_uuid VARCHAR(36) NOT NULL,
                        rule_id VARCHAR(128) NOT NULL,
                        fired_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            }
        }
    }

    private String killCountUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                    kill_count = excluded.kill_count,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                kill_count = VALUES(kill_count),
                updated_at = VALUES(updated_at)
            """;
    }

    private String firedRuleUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO eventpacket_fired_rules (player_uuid, rule_id, fired_at)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                    fired_at = excluded.fired_at
                """;
        }
        return """
            INSERT INTO eventpacket_fired_rules (player_uuid, rule_id, fired_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                fired_at = VALUES(fired_at)
            """;
    }
}
