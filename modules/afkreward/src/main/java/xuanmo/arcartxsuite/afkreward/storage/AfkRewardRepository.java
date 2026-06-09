package xuanmo.arcartxsuite.afkreward.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;

public final class AfkRewardRepository extends AbstractModuleRepository {

    private final AfkRewardConfiguration.StorageConfig configuration;

    public AfkRewardRepository(File dataFolder, AfkRewardConfiguration.StorageConfig configuration, Logger logger) {
        super("AXS-AfkReward", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createStatsTable(conn);
        createSessionsTable(conn);
        ensureIndexes(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(configuration.tablePrefix() + "stats", configuration.tablePrefix() + "sessions");
    }

    private void createStatsTable(Connection conn) throws SQLException {
        String table = configuration.tablePrefix() + "stats";
        try (Statement stmt = conn.createStatement()) {
            if (configuration.dialect() == AfkRewardConfiguration.StorageConfig.Dialect.SQLITE) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL DEFAULT '',
                        today_date TEXT NOT NULL DEFAULT '',
                        today_count INTEGER NOT NULL DEFAULT 0,
                        total_count INTEGER NOT NULL DEFAULT 0,
                        total_seconds INTEGER NOT NULL DEFAULT 0
                    );
                    """.formatted(table));
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        today_date VARCHAR(32) NOT NULL DEFAULT '',
                        today_count INT NOT NULL DEFAULT 0,
                        total_count INT NOT NULL DEFAULT 0,
                        total_seconds INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """.formatted(table));
            }
        }
    }

    private void createSessionsTable(Connection conn) throws SQLException {
        String table = configuration.tablePrefix() + "sessions";
        try (Statement stmt = conn.createStatement()) {
            if (configuration.dialect() == AfkRewardConfiguration.StorageConfig.Dialect.SQLITE) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL DEFAULT '',
                        area_name TEXT NOT NULL DEFAULT '',
                        reward_type TEXT NOT NULL DEFAULT '',
                        mode TEXT NOT NULL DEFAULT 'MANUAL',
                        start_seconds INTEGER NOT NULL DEFAULT 0,
                        start_time INTEGER NOT NULL DEFAULT 0,
                        today_count INTEGER NOT NULL DEFAULT 0,
                        total_count INTEGER NOT NULL DEFAULT 0,
                        today_date TEXT NOT NULL DEFAULT '',
                        total_seconds INTEGER NOT NULL DEFAULT 0
                    );
                    """.formatted(table));
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        area_name VARCHAR(64) NOT NULL DEFAULT '',
                        reward_type VARCHAR(64) NOT NULL DEFAULT '',
                        mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
                        start_seconds INT NOT NULL DEFAULT 0,
                        start_time BIGINT NOT NULL DEFAULT 0,
                        today_count INT NOT NULL DEFAULT 0,
                        total_count INT NOT NULL DEFAULT 0,
                        today_date VARCHAR(32) NOT NULL DEFAULT '',
                        total_seconds INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """.formatted(table));
            }
        }
    }

    private void ensureIndexes(Connection conn) throws SQLException {
        String table = configuration.tablePrefix() + "stats";
        try (Statement stmt = conn.createStatement()) {
            Set<String> indexes = loadIndexes(conn, table);
            String idxName = configuration.tablePrefix() + "stats_date";
            if (!indexes.contains(idxName.toLowerCase())) {
                stmt.execute("CREATE INDEX " + idxName + " ON " + table + " (today_date)");
            }
        }
    }

    private Set<String> loadIndexes(Connection conn, String table) throws SQLException {
        Set<String> set = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null) set.add(name.toLowerCase());
            }
        }
        return set;
    }

    public PlayerStats loadStats(UUID playerUuid) throws SQLException {
        String table = configuration.tablePrefix() + "stats";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_name, today_date, today_count, total_count, total_seconds FROM " + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(
                        nullToEmpty(rs.getString("player_name")),
                        nullToEmpty(rs.getString("today_date")),
                        rs.getInt("today_count"),
                        rs.getInt("total_count"),
                        rs.getInt("total_seconds")
                    );
                }
            }
        }
        return new PlayerStats("", "", 0, 0, 0);
    }

    // ── Sessions ──

    public void saveSession(SessionRecord session) throws SQLException {
        String table = configuration.tablePrefix() + "sessions";
        String sql;
        if (configuration.dialect() == AfkRewardConfiguration.StorageConfig.Dialect.SQLITE) {
            sql = """
                INSERT INTO %s (player_uuid, player_name, area_name, reward_type, mode, start_seconds, start_time,
                    today_count, total_count, today_date, total_seconds)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    area_name = excluded.area_name,
                    reward_type = excluded.reward_type,
                    mode = excluded.mode,
                    start_seconds = excluded.start_seconds,
                    start_time = excluded.start_time,
                    today_count = excluded.today_count,
                    total_count = excluded.total_count,
                    today_date = excluded.today_date,
                    total_seconds = excluded.total_seconds
                """.formatted(table);
        } else {
            sql = """
                INSERT INTO %s (player_uuid, player_name, area_name, reward_type, mode, start_seconds, start_time,
                    today_count, total_count, today_date, total_seconds)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    area_name = VALUES(area_name),
                    reward_type = VALUES(reward_type),
                    mode = VALUES(mode),
                    start_seconds = VALUES(start_seconds),
                    start_time = VALUES(start_time),
                    today_count = VALUES(today_count),
                    total_count = VALUES(total_count),
                    today_date = VALUES(today_date),
                    total_seconds = VALUES(total_seconds)
                """.formatted(table);
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.playerUuid().toString());
            ps.setString(2, session.playerName());
            ps.setString(3, session.areaName());
            ps.setString(4, session.rewardType());
            ps.setString(5, session.mode());
            ps.setInt(6, session.startSeconds());
            ps.setLong(7, session.startTime());
            ps.setInt(8, session.todayCount());
            ps.setInt(9, session.totalCount());
            ps.setString(10, session.todayDate());
            ps.setInt(11, session.totalSeconds());
            ps.executeUpdate();
        }
    }

    public SessionRecord loadSession(UUID playerUuid) throws SQLException {
        String table = configuration.tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_name, area_name, reward_type, mode, start_seconds, start_time, " +
                 "today_count, total_count, today_date, total_seconds FROM " + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SessionRecord(
                        playerUuid,
                        nullToEmpty(rs.getString("player_name")),
                        nullToEmpty(rs.getString("area_name")),
                        nullToEmpty(rs.getString("reward_type")),
                        nullToEmpty(rs.getString("mode")),
                        rs.getInt("start_seconds"),
                        rs.getLong("start_time"),
                        rs.getInt("today_count"),
                        rs.getInt("total_count"),
                        nullToEmpty(rs.getString("today_date")),
                        rs.getInt("total_seconds")
                    );
                }
            }
        }
        return null;
    }

    public void deleteSession(UUID playerUuid) throws SQLException {
        String table = configuration.tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        }
    }

    public void clearAllSessions() throws SQLException {
        String table = configuration.tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM " + table);
        }
    }

    // ── Leaderboard ──

    public List<PlayerStats> loadLeaderboard(int limit) throws SQLException {
        String table = configuration.tablePrefix() + "stats";
        List<PlayerStats> list = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_name, today_date, today_count, total_count, total_seconds FROM " +
                 table + " ORDER BY total_seconds DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PlayerStats(
                        nullToEmpty(rs.getString("player_name")),
                        nullToEmpty(rs.getString("today_date")),
                        rs.getInt("today_count"),
                        rs.getInt("total_count"),
                        rs.getInt("total_seconds")
                    ));
                }
            }
        }
        return list;
    }

    public void saveStats(UUID playerUuid, PlayerStats stats) throws SQLException {
        String table = configuration.tablePrefix() + "stats";
        String sql;
        if (configuration.dialect() == AfkRewardConfiguration.StorageConfig.Dialect.SQLITE) {
            sql = """
                INSERT INTO %s (player_uuid, player_name, today_date, today_count, total_count, total_seconds)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    today_date = excluded.today_date,
                    today_count = excluded.today_count,
                    total_count = excluded.total_count,
                    total_seconds = excluded.total_seconds
                """.formatted(table);
        } else {
            sql = """
                INSERT INTO %s (player_uuid, player_name, today_date, today_count, total_count, total_seconds)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    today_date = VALUES(today_date),
                    today_count = VALUES(today_count),
                    total_count = VALUES(total_count),
                    total_seconds = VALUES(total_seconds)
                """.formatted(table);
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, stats.playerName());
            ps.setString(3, stats.todayDate());
            ps.setInt(4, stats.todayCount());
            ps.setInt(5, stats.totalCount());
            ps.setInt(6, stats.totalSeconds());
            ps.executeUpdate();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record PlayerStats(
        String playerName,
        String todayDate,
        int todayCount,
        int totalCount,
        int totalSeconds
    ) {}

    public record SessionRecord(
        UUID playerUuid,
        String playerName,
        String areaName,
        String rewardType,
        String mode,
        int startSeconds,
        long startTime,
        int todayCount,
        int totalCount,
        String todayDate,
        int totalSeconds
    ) {}
}
