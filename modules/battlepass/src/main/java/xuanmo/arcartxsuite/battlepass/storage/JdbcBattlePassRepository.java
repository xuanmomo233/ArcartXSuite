package xuanmo.arcartxsuite.battlepass.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

/**
 * 战令 JDBC 仓储实现，继承 {@link AbstractModuleRepository} 并实现 {@link BattlePassRepository}。
 * <p>
 * 管理战令进度、玩家任务、奖励和业务回执，
 * 支持 SQLite 和 MySQL 两种模式的建表、索引及 CRUD 操作，
 * 同时实现数据库迁移和玩家数据清理能力。
 */
public final class JdbcBattlePassRepository extends AbstractModuleRepository implements BattlePassRepository {
    private final java.util.concurrent.locks.ReentrantLock[] playerWrites=new java.util.concurrent.locks.ReentrantLock[256];
    { java.util.Arrays.setAll(playerWrites,index->new java.util.concurrent.locks.ReentrantLock()); }
    @Override public <T> T withPlayerWrite(UUID player,Write<T> work) throws SQLException {
        var lock=playerWrites[Math.floorMod(player.hashCode(),playerWrites.length)];
        lock.lock(); try { return work.run(); } finally { lock.unlock(); }
    }

    private record ProgressWrite(UUID player, String season, Connection connection) {}
    private final ThreadLocal<ProgressWrite> progressWrite = BuildEdition.CROSS_SERVER_INCLUDED ? new ThreadLocal<>() : null;

    @Override public boolean sharedProgress() { return BuildEdition.CROSS_SERVER_INCLUDED && isMysql(); }

    @Override public <T> T withProgressWrite(UUID player, String season, Write<T> work) throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED || !sharedProgress()) return work.run();
        var current = progressWrite.get();
        if (current != null) {
            if (!current.player().equals(player) || !current.season().equals(season)) {
                throw new IllegalStateException("BattlePass progress transaction is scoped to one player and season");
            }
            return work.run();
        }
        return progressTransaction(connection -> {
            try (var fence = connection.prepareStatement("""
                    INSERT INTO bp_progress_writers (player_uuid, season_id) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE season_id = bp_progress_writers.season_id
                    """)) {
                fence.setString(1, player.toString());
                fence.setString(2, season);
                fence.executeUpdate();
            }
            progressWrite.set(new ProgressWrite(player, season, connection));
            try {
                return work.run();
            } finally {
                progressWrite.remove();
            }
        });
    }

    @FunctionalInterface
    private interface ProgressQuery<T> { T run(Connection connection) throws SQLException; }

    private <T> T progressQuery(ProgressQuery<T> query) throws SQLException {
        if (BuildEdition.CROSS_SERVER_INCLUDED) {
            var current = progressWrite.get();
            if (current != null) return query.run(current.connection());
        }
        try (Connection connection = getConnection()) {
            return query.run(connection);
        }
    }

    private <T> T progressTransaction(ProgressQuery<T> query) throws SQLException {
        if (BuildEdition.CROSS_SERVER_INCLUDED) {
            var current = progressWrite.get();
            if (current != null) return query.run(current.connection());
        }
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = query.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
    }

    public JdbcBattlePassRepository(DataSource dataSource, StorageDescriptor descriptor,
                                    File dataFolder, Logger logger) {
        super("AXS-BattlePass", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        ensureIndexes(conn);
    }

    @Override
    protected java.util.List<String> playerDataTables() {
        var tables = new ArrayList<>(List.of("bp_player_progress", "bp_claimed_rewards", "bp_player_tasks", "bp_auth_receipts", "bp_reward_requests"));
        if (BuildEdition.CROSS_SERVER_INCLUDED && sharedProgress()) tables.add("bp_progress_writers");
        return tables;
    }

    @Override
    protected java.util.List<String> allTables() {
        return java.util.List.of("bp_player_progress", "bp_claimed_rewards", "bp_player_tasks", "bp_season_settlement", "bp_auth_receipts", "bp_reward_requests", "bp_abyss_cursor");
    }

    private void createTables(Connection conn) throws SQLException {
        try(Statement statement=conn.createStatement()) {
            if (BuildEdition.CROSS_SERVER_INCLUDED && sharedProgress()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bp_progress_writers (
                        player_uuid VARCHAR(36) NOT NULL,
                        season_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (player_uuid, season_id)
                    ) ENGINE=InnoDB
                    """);
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bp_auth_receipts (player_uuid VARCHAR(36) NOT NULL,season_id VARCHAR(64) NOT NULL,fact_id VARCHAR(36) NOT NULL,PRIMARY KEY(player_uuid,season_id,fact_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bp_abyss_cursor (season_id VARCHAR(64) PRIMARY KEY, seq BIGINT NOT NULL)");
            String payloadType = isMysql() ? "LONGTEXT" : "TEXT";
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bp_reward_requests (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(128) NOT NULL,
                    request_id VARCHAR(36) NOT NULL,
                    payload %s NOT NULL,
                    PRIMARY KEY (player_uuid, season_id, reward_id)
                )
                """.formatted(payloadType));
        }
        String progressTable;
        if (!isMysql()) {
            progressTable = """
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
        } else {
            progressTable = """
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
        }

        String rewardTable;
        if (!isMysql()) {
            rewardTable = """
                CREATE TABLE IF NOT EXISTS bp_claimed_rewards (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    reward_id TEXT NOT NULL,
                    claimed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, season_id, reward_id)
                )
                """;
        } else {
            rewardTable = """
                CREATE TABLE IF NOT EXISTS bp_claimed_rewards (
                    player_uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, season_id, reward_id)
                )
                """;
        }

        String playerTaskTable;
        if (!isMysql()) {
            playerTaskTable = """
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
        } else {
            playerTaskTable = """
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
        }

        String settlementTable;
        if (!isMysql()) {
            settlementTable = """
                CREATE TABLE IF NOT EXISTS bp_season_settlement (
                    season_id TEXT NOT NULL PRIMARY KEY,
                    settled_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """;
        } else {
            settlementTable = """
                CREATE TABLE IF NOT EXISTS bp_season_settlement (
                    season_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    settled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(progressTable);
            stmt.execute(rewardTable);
            stmt.execute(playerTaskTable);
            stmt.execute(settlementTable);
        }
    }

    @Override
    public long loadAbyssCursor(String season) throws SQLException {
        try (var connection = getConnection();
             var statement = connection.prepareStatement("SELECT seq FROM bp_abyss_cursor WHERE season_id=?")) {
            statement.setString(1, season);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    @Override
    public void advanceAbyssCursor(String season, long sequence) throws SQLException {
        String upsert = isMysql()
            ? " ON DUPLICATE KEY UPDATE seq=GREATEST(seq,VALUES(seq))"
            : " ON CONFLICT(season_id) DO UPDATE SET seq=MAX(seq,excluded.seq)";
        try (var connection = getConnection();
             var statement = connection.prepareStatement("INSERT INTO bp_abyss_cursor (season_id,seq) VALUES (?,?)" + upsert)) {
            statement.setString(1, season);
            statement.setLong(2, sequence);
            statement.executeUpdate();
        }
    }

    private void ensureIndexes(Connection conn) {
        // MySQL 不支持 CREATE INDEX IF NOT EXISTS，重复创建时静默忽略
        String prefix = !isMysql() ? "CREATE INDEX IF NOT EXISTS " : "CREATE INDEX ";
        tryExecute(conn, prefix + "idx_bp_progress_season ON bp_player_progress(season_id)");
        tryExecute(conn, prefix + "idx_bp_reward_season ON bp_claimed_rewards(season_id)");
        tryExecute(conn, prefix + "idx_bp_ptask_season ON bp_player_tasks(season_id)");
        tryExecute(conn, prefix + "idx_bp_ptask_template ON bp_player_tasks(template_id)");
    }

    @Override
    public BattlePassPlayerProgress loadProgress(UUID playerUuid, String seasonId) throws SQLException {
        BattlePassPlayerProgress row = progressQuery(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
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
                            Set.of(),
                            parseDate(rs.getString("last_daily_reset_date")),
                            parseDate(rs.getString("last_weekly_reset_date")),
                            rs.getInt("current_week_number")
                        );
                    }
                }
            }
            return null;
        });
        if (row == null) return new BattlePassPlayerProgress(playerUuid, seasonId);
        return row.withClaimedRewards(loadClaimedRewards(playerUuid, seasonId));
    }

    @Override
    public void saveProgress(BattlePassPlayerProgress progress) throws SQLException {
        progressQuery(connection -> { saveProgressRow(connection, progress); return null; });
    }

    @Override
    public void saveProgressAndTasks(BattlePassPlayerProgress progress, List<PlayerTaskInstance> tasks,
                                     Set<BattlePassTask.TaskCategory> replacedCategories) throws SQLException {
        progressTransaction(connection -> {
            saveProgressRow(connection, progress);
            deletePlayerTasksByCategories(connection, progress.playerUuid(), progress.seasonId(), replacedCategories);
            savePlayerTaskBatch(connection, progress.playerUuid(), progress.seasonId(), tasks);
            return null;
        });
    }

    private void saveProgressRow(Connection connection,BattlePassPlayerProgress progress) throws SQLException {
        String sql;
        if (!isMysql()) {
            sql = """
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
        } else {
            sql = """
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
        }
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
    }
    @Override public boolean receivedAuthFact(UUID player,String season,String fact) throws SQLException {
        // A primary lookup waits for an earlier attempt whose commit acknowledgement was lost.
        ProgressQuery<Boolean> query = connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM bp_auth_receipts WHERE player_uuid=? AND season_id=? AND fact_id=?"
                    + (isMysql() ? " FOR UPDATE" : ""))) {
                statement.setString(1, player.toString());
                statement.setString(2, season);
                statement.setString(3, fact);
                try (var result = statement.executeQuery()) { return result.next(); }
            }
        };
        return isMysql() ? progressTransaction(query) : progressQuery(query);
    }
    @Override public void saveAuthFact(String fact,BattlePassPlayerProgress progress,List<PlayerTaskInstance> tasks,boolean changed) throws SQLException {
        progressTransaction(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO bp_auth_receipts (player_uuid,season_id,fact_id) VALUES (?,?,?)")) {
                statement.setString(1, progress.playerUuid().toString());
                statement.setString(2, progress.seasonId());
                statement.setString(3, fact);
                statement.executeUpdate();
            }
            if (changed) {
                saveProgressRow(connection, progress);
                savePlayerTaskBatch(connection, progress.playerUuid(), progress.seasonId(), tasks);
            }
            return null;
        });
    }

    @Override
    public Set<String> loadClaimedRewards(UUID playerUuid, String seasonId) throws SQLException {
        Set<String> result = new HashSet<>();
        return progressQuery(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
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
        });
    }

    @Override
    public void saveClaimedReward(UUID playerUuid, String seasonId, String rewardId) throws SQLException {
        String sql;
        if (!isMysql()) {
            sql = """
                INSERT INTO bp_claimed_rewards (player_uuid, season_id, reward_id, claimed_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id, reward_id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT IGNORE INTO bp_claimed_rewards (player_uuid, season_id, reward_id, claimed_at)
                VALUES (?, ?, ?, ?)
                """;
        }
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
    public RewardRequest stageReward(UUID player, String season, String rewardId, String payload) throws SQLException {
        String insert = isMysql() ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (Connection connection = getConnection()) {
            if (payload != null) {
                try (PreparedStatement statement = connection.prepareStatement(insert + """
                     INTO bp_reward_requests (player_uuid, season_id, reward_id, request_id, payload)
                     VALUES (?, ?, ?, ?, ?)
                    """)) {
                    bindRewardKey(statement, player, season, rewardId);
                    statement.setString(4, UUID.randomUUID().toString());
                    statement.setString(5, payload);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT request_id, payload FROM bp_reward_requests
                WHERE player_uuid = ? AND season_id = ? AND reward_id = ?
                """)) {
                bindRewardKey(statement, player, season, rewardId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? new RewardRequest(result.getString(1), result.getString(2)) : null;
                }
            }
        }
    }

    @Override
    public boolean completeReward(UUID player, String season, String rewardId, String requestId) throws SQLException {
        String insert = isMysql() ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(insert + """
                  INTO bp_claimed_rewards (player_uuid, season_id, reward_id, claimed_at)
                  SELECT player_uuid, season_id, reward_id, ? FROM bp_reward_requests
                  WHERE player_uuid = ? AND season_id = ? AND reward_id = ? AND request_id = ?
                 """)) {
            statement.setString(1, LocalDate.now().toString());
            statement.setString(2, player.toString());
            statement.setString(3, season);
            statement.setString(4, rewardId);
            statement.setString(5, requestId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void bindRewardKey(PreparedStatement statement, UUID player, String season, String rewardId)
            throws SQLException {
        statement.setString(1, player.toString());
        statement.setString(2, season);
        statement.setString(3, rewardId);
    }

    @Override
    public void resetPlayerProgress(UUID playerUuid, String seasonId) throws SQLException {
        progressTransaction(connection -> {
            for (String table : List.of("bp_player_progress", "bp_claimed_rewards",
                    "bp_player_tasks", "bp_reward_requests")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE player_uuid = ? AND season_id = ?")) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, seasonId);
                    statement.executeUpdate();
                }
            }
            return null;
        });
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
        return progressQuery(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
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
        });
    }

    private String playerTaskUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO bp_player_tasks (player_uuid, season_id, instance_id, template_id, category, target_count, current_progress, completed, assigned_date, week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, season_id, instance_id) DO UPDATE SET
                target_count = excluded.target_count,
                current_progress = excluded.current_progress,
                completed = excluded.completed,
                week_number = excluded.week_number
                """;
        } else {
            return """
                INSERT INTO bp_player_tasks (player_uuid, season_id, instance_id, template_id, category, target_count, current_progress, completed, assigned_date, week_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                target_count = VALUES(target_count),
                current_progress = VALUES(current_progress),
                completed = VALUES(completed),
                week_number = VALUES(week_number)
                """;
        }
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
        progressTransaction(connection -> {
            savePlayerTaskBatch(connection, playerUuid, seasonId, tasks);
            return null;
        });
    }

    private void savePlayerTaskBatch(Connection connection, UUID playerUuid, String seasonId,
                                     List<PlayerTaskInstance> tasks) throws SQLException {
        if (tasks.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(playerTaskUpsertSql())) {
            for (PlayerTaskInstance task : tasks) {
                bindPlayerTask(statement, playerUuid, seasonId, task);
                statement.addBatch();
            }
            executeCheckedBatch(statement);
        }
    }

    private static void executeCheckedBatch(PreparedStatement statement) throws SQLException {
        for (int count : statement.executeBatch()) {
            if (count == Statement.EXECUTE_FAILED) throw new SQLException("BattlePass task batch did not complete");
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
        try (Connection connection = getConnection()) {
            deletePlayerTasksByCategories(connection, playerUuid, seasonId, Set.of(category));
        }
    }

    private static void deletePlayerTasksByCategories(Connection connection, UUID playerUuid, String seasonId,
                                                      Set<BattlePassTask.TaskCategory> categories) throws SQLException {
        if (categories.isEmpty()) return;
        String keys = "?" + ",?".repeat(categories.size() - 1);
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM bp_player_tasks WHERE player_uuid = ? AND season_id = ? AND category IN (" + keys + ")")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seasonId);
            int parameter = 3;
            for (BattlePassTask.TaskCategory category : categories) stmt.setString(parameter++, category.name());
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
        return progressQuery(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                     "SELECT 1 FROM bp_season_settlement WHERE season_id = ?"
                 )) {
                stmt.setString(1, seasonId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public void markSeasonSettled(String seasonId) throws SQLException {
        String sql;
        if (!isMysql()) {
            sql = """
                INSERT INTO bp_season_settlement (season_id) VALUES (?)
                ON CONFLICT(season_id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT IGNORE INTO bp_season_settlement (season_id) VALUES (?)
                """;
        }
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
