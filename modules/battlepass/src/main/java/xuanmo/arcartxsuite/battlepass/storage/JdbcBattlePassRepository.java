package xuanmo.arcartxsuite.battlepass.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

/**
 * 战令 JDBC 仓储实现，继承 {@link AbstractModuleRepository} 并实现 {@link BattlePassRepository}。
 * <p>
 * 管理 4 张数据表（玩家进度、任务进度、奖励领取、玩家任务），
 * 支持 SQLite 和 MySQL 两种模式的建表、索引及 CRUD 操作，
 * 同时实现数据库迁移和玩家数据清理能力。
 */
public final class JdbcBattlePassRepository extends AbstractModuleRepository implements BattlePassRepository {

    private final BattlePassModuleConfiguration.BattlePassStorageConfiguration configuration;

    /**
     * 构造 JDBC 战令仓储实例。
     *
     * @param dataFolder    模块数据目录
     * @param configuration 存储配置
     * @param logger        日志记录器
     */
    public JdbcBattlePassRepository(File dataFolder, BattlePassModuleConfiguration.BattlePassStorageConfiguration configuration, Logger logger) {
        super("AXS-BattlePass", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    /**
     * 共享模式构造方法（1.4.0+）。
     *
     * @param storageManager 本体数据源管理器
     * @param moduleId       模块 ID
     * @param sqliteFileName SQLite 文件名
     * @param logger         日志记录器
     * @since 1.4.0
     */
    public JdbcBattlePassRepository(StorageManager storageManager, String moduleId,
                                    String sqliteFileName, Logger logger) {
        super("AXS-BattlePass", storageManager, moduleId, "", sqliteFileName, logger);
        this.configuration = null;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        ensureIndexes(conn);
    }

    @Override
    protected java.util.List<String> playerDataTables() {
        return java.util.List.of("bp_player_progress", "bp_task_progress", "bp_claimed_rewards", "bp_player_tasks");
    }

    @Override
    protected java.util.List<String> allTables() {
        return java.util.List.of("bp_player_progress", "bp_task_progress", "bp_claimed_rewards", "bp_player_tasks", "bp_season_settlement");
    }

    /** 获取当前方言标识，兼容共享模式和自建模式。 */
    private String dialect() {
        if (isSharedMode()) {
            return isMysql() ? "mysql" : "sqlite";
        }
        return configuration.mode();
    }

    private void createTables(Connection conn) throws SQLException {
        String progressTable = switch (dialect()) {
            case "sqlite" -> """
                CREATE TABLE IF NOT EXISTS bp_player_progress (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    current_level INTEGER DEFAULT 1,
                    current_xp INTEGER DEFAULT 0,
                    pass_tier TEXT DEFAULT 'FREE',
                    unlocked_premium INTEGER DEFAULT 0,
                    last_daily_reset_date TEXT,
                    last_weekly_reset_date TEXT,
                    current_week_number INTEGER DEFAULT 1,
                    PRIMARY KEY (player_uuid, season_id)
                )
                """;
            default -> """
                CREATE TABLE IF NOT EXISTS bp_player_progress (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    current_level INT DEFAULT 1,
                    current_xp INT DEFAULT 0,
                    pass_tier VARCHAR(16) DEFAULT 'FREE',
                    unlocked_premium TINYINT(1) DEFAULT 0,
                    last_daily_reset_date DATE,
                    last_weekly_reset_date DATE,
                    current_week_number INT DEFAULT 1,
                    PRIMARY KEY (player_uuid, season_id)
                )
                """;
        };

        String taskTable = switch (dialect()) {
            case "sqlite" -> """
                CREATE TABLE IF NOT EXISTS bp_task_progress (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    completed_count INTEGER DEFAULT 0,
                    last_update TEXT,
                    PRIMARY KEY (player_uuid, season_id, task_id)
                )
                """;
            default -> """
                CREATE TABLE IF NOT EXISTS bp_task_progress (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    task_id VARCHAR(64) NOT NULL,
                    completed_count INT DEFAULT 0,
                    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, season_id, task_id)
                )
                """;
        };

        String rewardTable = switch (dialect()) {
            case "sqlite" -> """
                CREATE TABLE IF NOT EXISTS bp_claimed_rewards (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    reward_id TEXT NOT NULL,
                    claimed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, season_id, reward_id)
                )
                """;
            default -> """
                CREATE TABLE IF NOT EXISTS bp_claimed_rewards (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, season_id, reward_id)
                )
                """;
        };

        String playerTaskTable = switch (dialect()) {
            case "sqlite" -> """
                CREATE TABLE IF NOT EXISTS bp_player_tasks (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    instance_id TEXT NOT NULL,
                    template_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    target_count INTEGER DEFAULT 1,
                    current_progress INTEGER DEFAULT 0,
                    completed INTEGER DEFAULT 0,
                    assigned_date TEXT,
                    week_number INTEGER DEFAULT 0,
                    PRIMARY KEY (player_uuid, season_id, instance_id)
                )
                """;
            default -> """
                CREATE TABLE IF NOT EXISTS bp_player_tasks (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    instance_id VARCHAR(128) NOT NULL,
                    template_id VARCHAR(64) NOT NULL,
                    category VARCHAR(16) NOT NULL,
                    target_count INT DEFAULT 1,
                    current_progress INT DEFAULT 0,
                    completed TINYINT(1) DEFAULT 0,
                    assigned_date DATE,
                    week_number INT DEFAULT 0,
                    PRIMARY KEY (player_uuid, season_id, instance_id)
                )
                """;
        };

        String settlementTable = switch (dialect()) {
            case "sqlite" -> """
                CREATE TABLE IF NOT EXISTS bp_season_settlement (
                    season_id TEXT NOT NULL PRIMARY KEY,
                    settled_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """;
            default -> """
                CREATE TABLE IF NOT EXISTS bp_season_settlement (
                    season_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    settled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        };

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(progressTable);
            stmt.execute(taskTable);
            stmt.execute(rewardTable);
            stmt.execute(playerTaskTable);
            stmt.execute(settlementTable);
        }
    }

    private void ensureIndexes(Connection conn) {
        // MySQL 不支持 CREATE INDEX IF NOT EXISTS，重复创建时静默忽略
        String prefix = "sqlite".equalsIgnoreCase(dialect()) ? "CREATE INDEX IF NOT EXISTS " : "CREATE INDEX ";
        tryExecute(conn, prefix + "idx_bp_progress_season ON bp_player_progress(season_id)");
        tryExecute(conn, prefix + "idx_bp_task_season ON bp_task_progress(season_id)");
        tryExecute(conn, prefix + "idx_bp_reward_season ON bp_claimed_rewards(season_id)");
        tryExecute(conn, prefix + "idx_bp_ptask_season ON bp_player_tasks(season_id)");
        tryExecute(conn, prefix + "idx_bp_ptask_template ON bp_player_tasks(template_id)");
    }

    @Override
    public BattlePassPlayerProgress loadProgress(UUID playerUuid, String seasonId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT current_level, current_xp, pass_tier, unlocked_premium, last_daily_reset_date, last_weekly_reset_date, current_week_number FROM bp_player_progress WHERE player_uuid = ? AND season_id = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String tierStr = rs.getString("pass_tier");
                    BattlePassPlayerProgress.PassTier tier = BattlePassPlayerProgress.PassTier.FREE;
                    try {
                        if (tierStr != null && !tierStr.isEmpty()) {
                            tier = BattlePassPlayerProgress.PassTier.valueOf(tierStr);
                        }
                    } catch (IllegalArgumentException ignored) {}

                    return new BattlePassPlayerProgress(
                        playerUuid,
                        seasonId,
                        rs.getInt("current_level"),
                        rs.getInt("current_xp"),
                        tier,
                        loadTaskProgresses(playerUuid, seasonId),
                        loadClaimedRewards(playerUuid, seasonId),
                        parseDate(rs.getString("last_daily_reset_date")),
                        parseDate(rs.getString("last_weekly_reset_date")),
                        rs.getInt("current_week_number")
                    );
                }
            }
        }
        return new BattlePassPlayerProgress(playerUuid, seasonId);
    }

    @Override
    public void saveProgress(BattlePassPlayerProgress progress) throws SQLException {
        String sql = switch (dialect()) {
            case "sqlite" -> """
                INSERT INTO bp_player_progress (player_uuid, season_id, current_level, current_xp, pass_tier, unlocked_premium, last_daily_reset_date, last_weekly_reset_date, current_week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id) DO UPDATE SET
                current_level = excluded.current_level,
                current_xp = excluded.current_xp,
                pass_tier = excluded.pass_tier,
                unlocked_premium = excluded.unlocked_premium,
                last_daily_reset_date = excluded.last_daily_reset_date,
                last_weekly_reset_date = excluded.last_weekly_reset_date,
                current_week_number = excluded.current_week_number
                """;
            default -> """
                INSERT INTO bp_player_progress (player_uuid, season_id, current_level, current_xp, pass_tier, unlocked_premium, last_daily_reset_date, last_weekly_reset_date, current_week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                current_level = VALUES(current_level),
                current_xp = VALUES(current_xp),
                pass_tier = VALUES(pass_tier),
                unlocked_premium = VALUES(unlocked_premium),
                last_daily_reset_date = VALUES(last_daily_reset_date),
                last_weekly_reset_date = VALUES(last_weekly_reset_date),
                current_week_number = VALUES(current_week_number)
                """;
        };
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, progress.playerUuid().toString());
            stmt.setString(2, progress.seasonId());
            stmt.setInt(3, progress.currentLevel());
            stmt.setInt(4, progress.currentXp());
            stmt.setString(5, progress.passTier().name());
            stmt.setInt(6, progress.unlockedPremium() ? 1 : 0);
            stmt.setString(7, progress.lastDailyResetDate() != null ? progress.lastDailyResetDate().toString() : LocalDate.now().toString());
            stmt.setString(8, progress.lastWeeklyResetDate() != null ? progress.lastWeeklyResetDate().toString() : LocalDate.now().toString());
            stmt.setInt(9, progress.currentWeekNumber());
            stmt.executeUpdate();
        }
        for (Map.Entry<String, Integer> entry : progress.taskProgresses().entrySet()) {
            saveTaskProgress(progress.playerUuid(), progress.seasonId(), entry.getKey(), entry.getValue());
        }
        for (String rewardId : progress.claimedRewards()) {
            saveClaimedReward(progress.playerUuid(), progress.seasonId(), rewardId);
        }
    }

    @Override
    public Map<String, Integer> loadTaskProgresses(UUID playerUuid, String seasonId) throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT task_id, completed_count FROM bp_task_progress WHERE player_uuid = ? AND season_id = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("task_id"), rs.getInt("completed_count"));
                }
            }
        }
        return result;
    }

    @Override
    public void saveTaskProgress(UUID playerUuid, String seasonId, String taskId, int count) throws SQLException {
        String sql = switch (dialect()) {
            case "sqlite" -> """
                INSERT INTO bp_task_progress (player_uuid, season_id, task_id, completed_count, last_update)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id, task_id) DO UPDATE SET
                completed_count = excluded.completed_count,
                last_update = excluded.last_update
                """;
            default -> """
                INSERT INTO bp_task_progress (player_uuid, season_id, task_id, completed_count, last_update)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                completed_count = VALUES(completed_count),
                last_update = VALUES(last_update)
                """;
        };
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            stmt.setString(3, taskId);
            stmt.setInt(4, count);
            stmt.setString(5, LocalDate.now().toString());
            stmt.executeUpdate();
        }
    }

    @Override
    public Set<String> loadClaimedRewards(UUID playerUuid, String seasonId) throws SQLException {
        Set<String> result = new HashSet<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT reward_id FROM bp_claimed_rewards WHERE player_uuid = ? AND season_id = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("reward_id"));
                }
            }
        }
        return result;
    }

    @Override
    public void saveClaimedReward(UUID playerUuid, String seasonId, String rewardId) throws SQLException {
        String sql = switch (dialect()) {
            case "sqlite" -> """
                INSERT INTO bp_claimed_rewards (player_uuid, season_id, reward_id, claimed_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id, reward_id) DO NOTHING
                """;
            default -> """
                INSERT IGNORE INTO bp_claimed_rewards (player_uuid, season_id, reward_id, claimed_at)
                VALUES (?, ?, ?, ?)
                """;
        };
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            stmt.setString(3, rewardId);
            stmt.setString(4, LocalDate.now().toString());
            stmt.executeUpdate();
        }
    }

    @Override
    public void resetPlayerProgress(UUID playerUuid, String seasonId) throws SQLException {
        try (Connection connection = getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM bp_player_progress WHERE player_uuid = ? AND season_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, seasonId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM bp_task_progress WHERE player_uuid = ? AND season_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, seasonId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM bp_claimed_rewards WHERE player_uuid = ? AND season_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, seasonId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM bp_player_tasks WHERE player_uuid = ? AND season_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, seasonId);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    public int countActivePlayers(String seasonId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT COUNT(DISTINCT player_uuid) FROM bp_player_progress WHERE season_id = ?"
             )) {
            stmt.setString(1, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public List<PlayerTaskInstance> loadPlayerTasks(UUID playerUuid, String seasonId) throws SQLException {
        List<PlayerTaskInstance> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT instance_id, template_id, category, target_count, current_progress, completed, assigned_date, week_number FROM bp_player_tasks WHERE player_uuid = ? AND season_id = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String catStr = rs.getString("category");
                    BattlePassTask.TaskCategory category = BattlePassTask.TaskCategory.SEASON;
                    try {
                        if (catStr != null && !catStr.isEmpty()) category = BattlePassTask.TaskCategory.valueOf(catStr);
                    } catch (IllegalArgumentException ignored) {}
                    result.add(new PlayerTaskInstance(
                        playerUuid,
                        rs.getString("instance_id"),
                        rs.getString("template_id"),
                        category,
                        rs.getInt("target_count"),
                        rs.getInt("current_progress"),
                        rs.getInt("completed") == 1,
                        parseDate(rs.getString("assigned_date")),
                        rs.getInt("week_number")
                    ));
                }
            }
        }
        return result;
    }

    private String playerTaskUpsertSql() {
        return switch (dialect()) {
            case "sqlite" -> """
                INSERT INTO bp_player_tasks (player_uuid, season_id, instance_id, template_id, category, target_count, current_progress, completed, assigned_date, week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id, instance_id) DO UPDATE SET
                target_count = excluded.target_count,
                current_progress = excluded.current_progress,
                completed = excluded.completed,
                week_number = excluded.week_number
                """;
            default -> """
                INSERT INTO bp_player_tasks (player_uuid, season_id, instance_id, template_id, category, target_count, current_progress, completed, assigned_date, week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                target_count = VALUES(target_count),
                current_progress = VALUES(current_progress),
                completed = VALUES(completed),
                week_number = VALUES(week_number)
                """;
        };
    }

    @Override
    public void savePlayerTask(UUID playerUuid, String seasonId, PlayerTaskInstance task) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(playerTaskUpsertSql())) {
            bindPlayerTask(stmt, playerUuid, seasonId, task);
            stmt.executeUpdate();
        }
    }

    @Override
    public void savePlayerTasks(UUID playerUuid, String seasonId, List<PlayerTaskInstance> tasks)
        throws SQLException {
        if (tasks == null || tasks.isEmpty()) return;
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(playerTaskUpsertSql())) {
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                for (PlayerTaskInstance task : tasks) {
                    bindPlayerTask(stmt, playerUuid, seasonId, task);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                if (connection.getAutoCommit() != autoCommit) {
                    connection.setAutoCommit(autoCommit);
                }
            }
        }
    }

    private void bindPlayerTask(
        PreparedStatement stmt, UUID playerUuid, String seasonId, PlayerTaskInstance task)
        throws SQLException {
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, seasonId);
        stmt.setString(3, task.instanceId());
        stmt.setString(4, task.templateId());
        stmt.setString(5, task.category().name());
        stmt.setInt(6, task.targetCount());
        stmt.setInt(7, task.currentProgress());
        stmt.setInt(8, task.completed() ? 1 : 0);
        stmt.setString(9, task.assignedDate() != null
            ? task.assignedDate().toString() : LocalDate.now().toString());
        stmt.setInt(10, task.weekNumber());
    }

    @Override
    public void deletePlayerTasksByCategory(UUID playerUuid, String seasonId, BattlePassTask.TaskCategory category) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "DELETE FROM bp_player_tasks WHERE player_uuid = ? AND season_id = ? AND category = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            stmt.setString(3, category.name());
            stmt.executeUpdate();
        }
    }

    @Override
    public void deletePlayerTasks(UUID playerUuid, String seasonId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "DELETE FROM bp_player_tasks WHERE player_uuid = ? AND season_id = ?"
             )) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            stmt.executeUpdate();
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return LocalDate.now();
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    // ── 赛季结算 ──────────────────────────────────────────

    @Override
    public boolean isSeasonSettled(String seasonId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT 1 FROM bp_season_settlement WHERE season_id = ?"
             )) {
            stmt.setString(1, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void markSeasonSettled(String seasonId) throws SQLException {
        String sql = switch (dialect()) {
            case "sqlite" -> """
                INSERT INTO bp_season_settlement (season_id) VALUES (?)
                ON CONFLICT(season_id) DO NOTHING
                """;
            default -> """
                INSERT IGNORE INTO bp_season_settlement (season_id) VALUES (?)
                """;
        };
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, seasonId);
            stmt.executeUpdate();
        }
    }

    @Override
    public java.util.List<PlayerSeasonSummary> loadSeasonSummaries(String seasonId) throws SQLException {
        java.util.List<PlayerSeasonSummary> result = new java.util.ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT player_uuid, current_level, pass_tier FROM bp_player_progress WHERE season_id = ?"
             )) {
            stmt.setString(1, seasonId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    int level = rs.getInt("current_level");
                    String tier = rs.getString("pass_tier");
                    result.add(new PlayerSeasonSummary(uuid, level, tier != null ? tier : "FREE"));
                }
            }
        }
        return result;
    }
}
