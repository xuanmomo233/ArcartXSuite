package xuanmo.arcartxsuite.onlinerewards.storage;

import java.io.File;
import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPersistenceDialect;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsStorageConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

/**
 * 在线奖励 JDBC 存储仓库。
 * <p>
 * 属于 {@code onlinerewards} 模块，继承 {@link AbstractModuleRepository} 实现 {@link OnlineRewardsRepository}，
 * 负责玩家状态、签到记录、全服签到目标的持久化与排行榜查询，支持 SQLite 与 MySQL 双方言。
 */
public final class JdbcOnlineRewardsRepository extends AbstractModuleRepository implements OnlineRewardsRepository {

    private final OnlineRewardsStorageConfiguration configuration;

    /**
     * 构造 JDBC 存储仓库。
     *
     * @param dataFolder    模块数据目录
     * @param configuration 存储配置
     * @param logger        日志器
     */
    public JdbcOnlineRewardsRepository(File dataFolder, OnlineRewardsStorageConfiguration configuration, Logger logger) {
        super("AXS-OnlineRewards", dataFolder, configuration.toDescriptor(), logger);
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
     * @param logger         日志器
     * @since 1.4.0
     */
    public JdbcOnlineRewardsRepository(StorageManager storageManager, String moduleId,
                                       String tablePrefix, String sqliteFileName, Logger logger) {
        super("AXS-OnlineRewards", storageManager, moduleId, tablePrefix, sqliteFileName, logger);
        this.configuration = null;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createOrMigrateTable(conn);
        ensureIndexes(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("online_rewards_players", "online_rewards_sign_ins", "online_rewards_server_goals");
    }

    /**
     * 加载玩家运行时状态，不存在时返回默认状态。
     *
     * @param playerUuid 玩家唯一 id
     * @return 玩家状态
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public OnlineRewardsPlayerState loadState(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT player_name, reward_date, online_minutes, reward_stage, week_key, week_minutes,
                        month_key, month_minutes, total_minutes, last_sign_in_date, sign_in_streak, sign_in_total,
                        makeup_cards, time_bonus_remainder, offline_savings_minutes,
                        claimed_weekly_reward_ids, claimed_monthly_reward_ids
                 FROM online_rewards_players
                 WHERE player_uuid = ?
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new OnlineRewardsPlayerState(
                        nullToEmpty(resultSet.getString("player_name")),
                        nullToEmpty(resultSet.getString("reward_date")),
                        resultSet.getInt("online_minutes"),
                        resultSet.getInt("reward_stage"),
                        nullToEmpty(resultSet.getString("week_key")),
                        resultSet.getInt("week_minutes"),
                        nullToEmpty(resultSet.getString("month_key")),
                        resultSet.getInt("month_minutes"),
                        resultSet.getInt("total_minutes"),
                        nullToEmpty(resultSet.getString("last_sign_in_date")),
                        resultSet.getInt("sign_in_streak"),
                        resultSet.getInt("sign_in_total"),
                        resultSet.getInt("makeup_cards"),
                        resultSet.getDouble("time_bonus_remainder"),
                        resultSet.getInt("offline_savings_minutes"),
                        parseIdSet(nullToEmpty(resultSet.getString("claimed_weekly_reward_ids"))),
                        parseIdSet(nullToEmpty(resultSet.getString("claimed_monthly_reward_ids")))
                    );
                }
            }
        }
        return new OnlineRewardsPlayerState();
    }

    /**
     * 保存或更新玩家运行时状态。
     *
     * @param playerUuid 玩家唯一 id
     * @param state      玩家状态
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void saveState(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, state.playerName());
            statement.setString(3, state.rewardDate());
            statement.setInt(4, state.onlineMinutes());
            statement.setInt(5, state.rewardStage());
            statement.setString(6, state.weekKey());
            statement.setInt(7, state.weekMinutes());
            statement.setString(8, state.monthKey());
            statement.setInt(9, state.monthMinutes());
            statement.setInt(10, state.totalMinutes());
            statement.setString(11, state.lastSignInDate());
            statement.setInt(12, state.signInStreak());
            statement.setInt(13, state.signInTotal());
            statement.setInt(14, state.makeupCards());
            statement.setDouble(15, state.timeBonusRemainder());
            statement.setInt(16, state.offlineSavingsMinutes());
            statement.setString(17, String.join(",", state.claimedWeeklyRewardIds()));
            statement.setString(18, String.join(",", state.claimedMonthlyRewardIds()));
            statement.executeUpdate();
        }
    }

    /**
     * 加载玩家在指定日期范围内的签到日期集合。
     *
     * @param playerUuid 玩家唯一 id
     * @param fromDate   起始日期（含）
     * @param toDate     结束日期（含）
     * @return 签到日期字符串集合
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Set<String> loadSignInDates(UUID playerUuid, String fromDate, String toDate) throws SQLException {
        LinkedHashSet<String> dates = new LinkedHashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT sign_in_date
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ? AND sign_in_date >= ? AND sign_in_date <= ?
                 ORDER BY sign_in_date ASC
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, fromDate == null ? "" : fromDate);
            statement.setString(3, toDate == null ? "" : toDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dates.add(resultSet.getString("sign_in_date"));
                }
            }
        }
        return Set.copyOf(dates);
    }

    /**
     * 加载玩家全部签到日期集合。
     *
     * @param playerUuid 玩家唯一 id
     * @return 签到日期字符串集合
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Set<String> loadAllSignInDates(UUID playerUuid) throws SQLException {
        LinkedHashSet<String> dates = new LinkedHashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT sign_in_date
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ?
                 ORDER BY sign_in_date ASC
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dates.add(resultSet.getString("sign_in_date"));
                }
            }
        }
        return Set.copyOf(dates);
    }

    /**
     * 判断玩家在指定日期是否已有签到记录。
     *
     * @param playerUuid 玩家唯一 id
     * @param date       日期字符串
     * @return 是否已签到
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public boolean hasSignInRecord(UUID playerUuid, String date) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT 1
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ? AND sign_in_date = ?
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, date == null ? "" : date);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 保存签到记录（覆盖式写入）。
     *
     * @param playerUuid 玩家唯一 id
     * @param playerName 玩家名称
     * @param date       签到日期
     * @param makeup     是否为补签
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void saveSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(signInUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setString(3, date == null ? "" : date);
            statement.setInt(4, makeup ? 1 : 0);
            statement.executeUpdate();
        }
    }

    /**
     * 尝试插入签到记录，仅当该日期不存在记录时才写入。
     *
     * @param playerUuid 玩家唯一 id
     * @param playerName 玩家名称
     * @param date       签到日期
     * @param makeup     是否为补签
     * @return 插入成功返回 {@code true}，已存在记录返回 {@code false}
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public boolean tryInsertSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException {
        String sql = isSqliteDialect()
            ? """
                INSERT OR IGNORE INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
                VALUES (?, ?, ?, ?)
                """
            : """
                INSERT IGNORE INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setString(3, date == null ? "" : date);
            statement.setInt(4, makeup ? 1 : 0);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * 分页加载排行榜条目。
     *
     * @param scope    排行榜范围
     * @param periodKey 周期键
     * @param offset   偏移量
     * @param limit    条数上限
     * @return 排行榜条目列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<OnlineRewardsLeaderboardEntry> loadLeaderboard(
        OnlineRewardsLeaderboardScope scope,
        String periodKey,
        int offset,
        int limit
    ) throws SQLException {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        String sql = switch (scope) {
            case DAILY -> """
                SELECT player_uuid, player_name, online_minutes AS minutes
                FROM online_rewards_players
                WHERE reward_date = ? AND online_minutes > 0
                ORDER BY online_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case WEEKLY -> """
                SELECT player_uuid, player_name, week_minutes AS minutes
                FROM online_rewards_players
                WHERE week_key = ? AND week_minutes > 0
                ORDER BY week_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case MONTHLY -> """
                SELECT player_uuid, player_name, month_minutes AS minutes
                FROM online_rewards_players
                WHERE month_key = ? AND month_minutes > 0
                ORDER BY month_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case TOTAL -> """
                SELECT player_uuid, player_name, total_minutes AS minutes
                FROM online_rewards_players
                WHERE total_minutes > 0
                ORDER BY total_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
        };

        List<OnlineRewardsLeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (scope != OnlineRewardsLeaderboardScope.TOTAL) {
                statement.setString(parameterIndex++, periodKey == null ? "" : periodKey);
            }
            statement.setInt(parameterIndex++, safeLimit);
            statement.setInt(parameterIndex, safeOffset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    String playerName = nullToEmpty(resultSet.getString("player_name"));
                    if (playerName.isBlank()) {
                        playerName = playerUuid.toString();
                    }
                    entries.add(new OnlineRewardsLeaderboardEntry(playerUuid, playerName, resultSet.getInt("minutes")));
                }
            }
        }
        return List.copyOf(entries);
    }

    /**
     * 统计指定日期的签到记录数。
     *
     * @param date 日期字符串（精确匹配）
     * @return 签到记录数
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public int countSignInRecords(String date) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(DISTINCT player_uuid) FROM online_rewards_sign_ins WHERE sign_in_date = ?"
             )) {
            statement.setString(1, date == null ? "" : date);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * 判断指定日期的全服签到目标是否已触发。
     *
     * @param date   日期
     * @param goalId 目标 id
     * @return 是否已触发
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public boolean isServerGoalTriggered(String date, String goalId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM online_rewards_server_goals WHERE goal_date = ? AND goal_id = ? AND triggered = 1"
             )) {
            statement.setString(1, date == null ? "" : date);
            statement.setString(2, goalId == null ? "" : goalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 记录指定日期的全服签到目标已触发。
     *
     * @param date   日期
     * @param goalId 目标 id
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void saveServerGoalTriggered(String date, String goalId) throws SQLException {
        String sql = isSqliteDialect()
            ? """
                INSERT INTO online_rewards_server_goals (goal_date, goal_id, triggered)
                VALUES (?, ?, 1)
                ON CONFLICT(goal_date, goal_id) DO UPDATE SET triggered = 1
                """
            : """
                INSERT INTO online_rewards_server_goals (goal_date, goal_id, triggered)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE triggered = 1
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date == null ? "" : date);
            statement.setString(2, goalId == null ? "" : goalId);
            statement.executeUpdate();
        }
    }

    private Set<String> parseIdSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return Set.copyOf(ids);
    }

    private void createOrMigrateTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isSqliteDialect()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_players (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL DEFAULT '',
                        reward_date TEXT NOT NULL DEFAULT '',
                        online_minutes INTEGER NOT NULL DEFAULT 0,
                        reward_stage INTEGER NOT NULL DEFAULT 0,
                        week_key TEXT NOT NULL DEFAULT '',
                        week_minutes INTEGER NOT NULL DEFAULT 0,
                        month_key TEXT NOT NULL DEFAULT '',
                        month_minutes INTEGER NOT NULL DEFAULT 0,
                        total_minutes INTEGER NOT NULL DEFAULT 0,
                        last_sign_in_date TEXT NOT NULL DEFAULT '',
                        sign_in_streak INTEGER NOT NULL DEFAULT 0,
                        sign_in_total INTEGER NOT NULL DEFAULT 0,
                        makeup_cards INTEGER NOT NULL DEFAULT 0,
                        time_bonus_remainder REAL NOT NULL DEFAULT 0,
                        offline_savings_minutes INTEGER NOT NULL DEFAULT 0,
                        claimed_weekly_reward_ids TEXT NOT NULL DEFAULT '',
                        claimed_monthly_reward_ids TEXT NOT NULL DEFAULT ''
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_sign_ins (
                        player_uuid TEXT NOT NULL,
                        sign_in_date TEXT NOT NULL,
                        player_name TEXT NOT NULL DEFAULT '',
                        makeup INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, sign_in_date)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_server_goals (
                        goal_date TEXT NOT NULL,
                        goal_id TEXT NOT NULL,
                        triggered INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (goal_date, goal_id)
                    );
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_players (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        reward_date VARCHAR(32) NOT NULL DEFAULT '',
                        online_minutes INT NOT NULL DEFAULT 0,
                        reward_stage INT NOT NULL DEFAULT 0,
                        week_key VARCHAR(32) NOT NULL DEFAULT '',
                        week_minutes INT NOT NULL DEFAULT 0,
                        month_key VARCHAR(32) NOT NULL DEFAULT '',
                        month_minutes INT NOT NULL DEFAULT 0,
                        total_minutes INT NOT NULL DEFAULT 0,
                        last_sign_in_date VARCHAR(32) NOT NULL DEFAULT '',
                        sign_in_streak INT NOT NULL DEFAULT 0,
                        sign_in_total INT NOT NULL DEFAULT 0,
                        makeup_cards INT NOT NULL DEFAULT 0,
                        time_bonus_remainder DOUBLE NOT NULL DEFAULT 0,
                        offline_savings_minutes INT NOT NULL DEFAULT 0,
                        claimed_weekly_reward_ids TEXT NOT NULL,
                        claimed_monthly_reward_ids TEXT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_sign_ins (
                        player_uuid VARCHAR(36) NOT NULL,
                        sign_in_date VARCHAR(32) NOT NULL,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        makeup TINYINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, sign_in_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_server_goals (
                        goal_date VARCHAR(32) NOT NULL,
                        goal_id VARCHAR(64) NOT NULL,
                        triggered TINYINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (goal_date, goal_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
        ensureColumns(connection);
        // 仅在未完成迁移时执行，避免每次启动全表扫描
        if (!isMigrationCompleted(connection, "migrate_last_sign_in_records")) {
            migrateLastSignInRecords(connection);
            markMigrationCompleted(connection, "migrate_last_sign_in_records");
        }
    }

    private static final String MIGRATION_TABLE = "online_rewards_schema_meta";

    private boolean isMigrationCompleted(Connection connection, String migrationId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isSqliteDialect()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        migration_id TEXT PRIMARY KEY,
                        completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(MIGRATION_TABLE));
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        migration_id VARCHAR(64) PRIMARY KEY,
                        completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(MIGRATION_TABLE));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM %s WHERE migration_id = ?".formatted(MIGRATION_TABLE)
        )) {
            statement.setString(1, migrationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void markMigrationCompleted(Connection connection, String migrationId) throws SQLException {
        String sql = isSqliteDialect()
            ? "INSERT OR IGNORE INTO %s (migration_id) VALUES (?)".formatted(MIGRATION_TABLE)
            : "INSERT IGNORE INTO %s (migration_id) VALUES (?)".formatted(MIGRATION_TABLE);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, migrationId);
            statement.executeUpdate();
        }
    }

    private void ensureColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Set<String> columns = loadColumns(connection);
            ensureColumn(statement, columns, "player_name", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(64) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "reward_date", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "online_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "reward_stage", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "week_key", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "week_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "month_key", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "month_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "total_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "last_sign_in_date", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "sign_in_streak", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "sign_in_total", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "makeup_cards", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "time_bonus_remainder", sqliteType("REAL NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "offline_savings_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "claimed_weekly_reward_ids", sqliteType("TEXT NOT NULL DEFAULT ''", "TEXT NOT NULL"));
            ensureColumn(statement, columns, "claimed_monthly_reward_ids", sqliteType("TEXT NOT NULL DEFAULT ''", "TEXT NOT NULL"));
        }
    }

    private void ensureIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Set<String> indexes = loadIndexes(connection);
            ensureIndex(statement, indexes, "idx_online_rewards_daily_rank", "online_rewards_players", "reward_date, online_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_weekly_rank", "online_rewards_players", "week_key, week_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_monthly_rank", "online_rewards_players", "month_key, month_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_total_rank", "online_rewards_players", "total_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_sign_ins_date", "online_rewards_sign_ins", "sign_in_date");
        }
    }

    private void migrateLastSignInRecords(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isSqliteDialect()) {
                statement.execute("""
                    INSERT OR IGNORE INTO online_rewards_sign_ins (player_uuid, sign_in_date, player_name, makeup)
                    SELECT player_uuid, last_sign_in_date, player_name, 0
                    FROM online_rewards_players
                    WHERE last_sign_in_date IS NOT NULL AND last_sign_in_date <> ''
                    """);
            } else {
                statement.execute("""
                    INSERT IGNORE INTO online_rewards_sign_ins (player_uuid, sign_in_date, player_name, makeup)
                    SELECT player_uuid, last_sign_in_date, player_name, 0
                    FROM online_rewards_players
                    WHERE last_sign_in_date IS NOT NULL AND last_sign_in_date <> ''
                    """);
            }
        }
    }

    private Connection connection() throws SQLException {
        return getConnection();
    }

    /**
     * 判断当前是否为 SQLite 方言。
     * <p>
     * 共享模式下通过 {@link #isMysql()} 判断（基于本体全局描述符）；
     * 自建模式下通过 {@code descriptor} 判断（来自模块配置的 {@code dialect}）。
     *
     * @return {@code true} 表示当前使用 SQLite 方言
     * @since 1.4.0
     */
    private boolean isSqliteDialect() {
        return !isMysql();
    }

    private String upsertSql() {
        if (isSqliteDialect()) {
            return """
                INSERT INTO online_rewards_players (
                    player_uuid, player_name, reward_date, online_minutes, reward_stage,
                    week_key, week_minutes, month_key, month_minutes, total_minutes,
                    last_sign_in_date, sign_in_streak, sign_in_total, makeup_cards, time_bonus_remainder,
                    offline_savings_minutes, claimed_weekly_reward_ids, claimed_monthly_reward_ids
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    reward_date = excluded.reward_date,
                    online_minutes = excluded.online_minutes,
                    reward_stage = excluded.reward_stage,
                    week_key = excluded.week_key,
                    week_minutes = excluded.week_minutes,
                    month_key = excluded.month_key,
                    month_minutes = excluded.month_minutes,
                    total_minutes = excluded.total_minutes,
                    last_sign_in_date = excluded.last_sign_in_date,
                    sign_in_streak = excluded.sign_in_streak,
                    sign_in_total = excluded.sign_in_total,
                    makeup_cards = excluded.makeup_cards,
                    time_bonus_remainder = excluded.time_bonus_remainder,
                    offline_savings_minutes = excluded.offline_savings_minutes,
                    claimed_weekly_reward_ids = excluded.claimed_weekly_reward_ids,
                    claimed_monthly_reward_ids = excluded.claimed_monthly_reward_ids
                """;
        }
        return """
            INSERT INTO online_rewards_players (
                player_uuid, player_name, reward_date, online_minutes, reward_stage,
                week_key, week_minutes, month_key, month_minutes, total_minutes,
                last_sign_in_date, sign_in_streak, sign_in_total, makeup_cards, time_bonus_remainder,
                offline_savings_minutes, claimed_weekly_reward_ids, claimed_monthly_reward_ids
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                reward_date = VALUES(reward_date),
                online_minutes = VALUES(online_minutes),
                reward_stage = VALUES(reward_stage),
                week_key = VALUES(week_key),
                week_minutes = VALUES(week_minutes),
                month_key = VALUES(month_key),
                month_minutes = VALUES(month_minutes),
                total_minutes = VALUES(total_minutes),
                last_sign_in_date = VALUES(last_sign_in_date),
                sign_in_streak = VALUES(sign_in_streak),
                sign_in_total = VALUES(sign_in_total),
                makeup_cards = VALUES(makeup_cards),
                time_bonus_remainder = VALUES(time_bonus_remainder),
                offline_savings_minutes = VALUES(offline_savings_minutes),
                claimed_weekly_reward_ids = VALUES(claimed_weekly_reward_ids),
                claimed_monthly_reward_ids = VALUES(claimed_monthly_reward_ids)
            """;
    }

    private String signInUpsertSql() {
        if (isSqliteDialect()) {
            return """
                INSERT INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, sign_in_date) DO UPDATE SET
                    player_name = excluded.player_name,
                    makeup = excluded.makeup
                """;
        }
        return """
            INSERT INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                makeup = VALUES(makeup)
            """;
    }

    private void ensureColumn(Statement statement, Set<String> columns, String columnName, String definition) throws SQLException {
        if (columns.contains(columnName)) {
            return;
        }
        statement.execute("ALTER TABLE online_rewards_players ADD COLUMN " + columnName + " " + definition);
        columns.add(columnName);
    }

    private void ensureIndex(Statement statement, Set<String> indexes, String indexName, String tableName, String columns) throws SQLException {
        if (indexes.contains(indexName.toLowerCase())) {
            return;
        }
        statement.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
        indexes.add(indexName.toLowerCase());
    }

    private String sqliteType(String sqliteDefinition, String mysqlDefinition) {
        return isSqliteDialect() ? sqliteDefinition : mysqlDefinition;
    }

    private Set<String> loadColumns(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = metaData.getColumns(null, null, "online_rewards_players", null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> loadIndexes(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> indexes = new HashSet<>();
        loadIndexes(metaData, indexes, "online_rewards_players");
        loadIndexes(metaData, indexes, "online_rewards_sign_ins");
        return indexes;
    }

    private void loadIndexes(DatabaseMetaData metaData, Set<String> indexes, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null && !indexName.isBlank()) {
                    indexes.add(indexName.toLowerCase());
                }
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
