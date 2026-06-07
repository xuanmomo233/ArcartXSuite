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
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPersistenceDialect;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsStorageConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public final class JdbcOnlineRewardsRepository extends AbstractModuleRepository implements OnlineRewardsRepository {

    private final OnlineRewardsStorageConfiguration configuration;

    public JdbcOnlineRewardsRepository(File dataFolder, OnlineRewardsStorageConfiguration configuration, Logger logger) {
        super("AXS-OnlineRewards", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createOrMigrateTable(conn);
        ensureIndexes(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("online_rewards_players", "online_rewards_sign_ins");
    }

    @Override
    public OnlineRewardsPlayerState loadState(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT player_name, reward_date, online_minutes, reward_stage, week_key, week_minutes,
                        month_key, month_minutes, total_minutes, last_sign_in_date, sign_in_streak, sign_in_total,
                        makeup_cards, time_bonus_remainder
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
                        resultSet.getDouble("time_bonus_remainder")
                    );
                }
            }
        }
        return new OnlineRewardsPlayerState();
    }

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
            statement.executeUpdate();
        }
    }

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

    @Override
    public boolean tryInsertSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException {
        String sql = configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE
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

    @Override
    public void close() {
        shutdown();
    }

    private void createOrMigrateTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE) {
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
                        time_bonus_remainder REAL NOT NULL DEFAULT 0
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
                        time_bonus_remainder DOUBLE NOT NULL DEFAULT 0
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
            }
        }
        ensureColumns(connection);
        migrateLastSignInRecords(connection);
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
            if (configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE) {
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

    private String upsertSql() {
        if (configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE) {
            return """
                INSERT INTO online_rewards_players (
                    player_uuid, player_name, reward_date, online_minutes, reward_stage,
                    week_key, week_minutes, month_key, month_minutes, total_minutes,
                    last_sign_in_date, sign_in_streak, sign_in_total, makeup_cards, time_bonus_remainder
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    time_bonus_remainder = excluded.time_bonus_remainder
                """;
        }
        return """
            INSERT INTO online_rewards_players (
                player_uuid, player_name, reward_date, online_minutes, reward_stage,
                week_key, week_minutes, month_key, month_minutes, total_minutes,
                last_sign_in_date, sign_in_streak, sign_in_total, makeup_cards, time_bonus_remainder
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                time_bonus_remainder = VALUES(time_bonus_remainder)
            """;
    }

    private String signInUpsertSql() {
        if (configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE) {
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
        return configuration.dialect() == OnlineRewardsPersistenceDialect.SQLITE ? sqliteDefinition : mysqlDefinition;
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
