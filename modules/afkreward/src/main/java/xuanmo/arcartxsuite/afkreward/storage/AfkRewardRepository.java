package xuanmo.arcartxsuite.afkreward.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * 挂机奖励模块的数据仓储，继承 {@link AbstractModuleRepository}，
 * 负责玩家统计、区域统计与奖励记录的持久化 CRUD。
 */
public final class AfkRewardRepository extends AbstractModuleRepository {
    private static final int QUIT_ROWS_PER_STATEMENT = 128;

    public AfkRewardRepository(DataSource dataSource, StorageDescriptor descriptor,
                               File dataFolder, Logger logger) {
        super("AXS-AfkReward", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createStatsTable(conn);
        createSessionsTable(conn);
        ensureMovementColumns(conn);
        createManualSettlementsTable(conn);
        createAreaStatsTable(conn);
        ensureIndexes(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(
            tablePrefix() + "stats",
            tablePrefix() + "sessions",
            tablePrefix() + "manual_settlements",
            tablePrefix() + "area_stats"
        );
    }

    @Override
    protected List<String> allTables() {
        return List.of(
            tablePrefix() + "stats",
            tablePrefix() + "sessions",
            tablePrefix() + "manual_settlements",
            tablePrefix() + "area_stats"
        );
    }

    private void createStatsTable(Connection conn) throws SQLException {
        String table = tablePrefix() + "stats";
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL DEFAULT '',
                        today_date TEXT NOT NULL DEFAULT '',
                        today_count INTEGER NOT NULL DEFAULT 0,
                        total_count INTEGER NOT NULL DEFAULT 0,
                        total_seconds INTEGER NOT NULL DEFAULT 0
                    );
                    """);
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        today_date VARCHAR(32) NOT NULL DEFAULT '',
                        today_count INT NOT NULL DEFAULT 0,
                        total_count INT NOT NULL DEFAULT 0,
                        total_seconds INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private void createSessionsTable(Connection conn) throws SQLException {
        String table = tablePrefix() + "sessions";
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
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
                    """);
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
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
                    """);
            }
        }
    }

    private void createAreaStatsTable(Connection conn) throws SQLException {
        String table = tablePrefix() + "area_stats";
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
                        player_uuid TEXT NOT NULL,
                        area_name TEXT NOT NULL,
                        total_seconds INTEGER NOT NULL DEFAULT 0,
                        today_seconds INTEGER NOT NULL DEFAULT 0,
                        today_date TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY (player_uuid, area_name)
                    );
                    """);
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS""" + " " + table + """
                    (
                        player_uuid VARCHAR(36) NOT NULL,
                        area_name VARCHAR(64) NOT NULL,
                        total_seconds INT NOT NULL DEFAULT 0,
                        today_seconds INT NOT NULL DEFAULT 0,
                        today_date VARCHAR(32) NOT NULL DEFAULT '',
                        PRIMARY KEY (player_uuid, area_name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private void createManualSettlementsTable(Connection conn) throws SQLException {
        String table = tablePrefix() + "manual_settlements";
        String textType = isMysql() ? "VARCHAR(64)" : "TEXT";
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid VARCHAR(36) NOT NULL,
                    start_time BIGINT NOT NULL,
                    player_name %s NOT NULL,
                    area_name %s NOT NULL,
                    reward_type %s NOT NULL,
                    start_seconds INTEGER NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    completed_rounds INTEGER NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL,
                    claim_id VARCHAR(36),
                    PRIMARY KEY (player_uuid, start_time)
                ) %s
                """.formatted(table, textType, textType, textType,
                    isMysql() ? "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
        }
        addNullableColumn(conn, table, "claim_id", "VARCHAR(36)");
    }

    private void ensureMovementColumns(Connection conn) throws SQLException {
        String table = tablePrefix() + "sessions";
        addNullableColumn(conn, table, "original_walk_speed", isMysql() ? "DOUBLE" : "REAL");
        addNullableColumn(conn, table, "original_collidable", "BOOLEAN");
    }

    private static void addNullableColumn(Connection conn, String table, String column, String type) throws SQLException {
        if (hasColumn(conn, table, column)) return;
        try (Statement statement = conn.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException failure) {
            if (!hasColumn(conn, table, column)) throw failure;
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (ResultSet columns = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    private void ensureIndexes(Connection conn) throws SQLException {
        String table = tablePrefix() + "stats";
        try (Statement stmt = conn.createStatement()) {
            Set<String> indexes = loadIndexes(conn, table);
            String idxName = tablePrefix() + "stats_date";
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
        String table = tablePrefix() + "stats";
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
        writeSession(session, true);
    }

    public void startManualSession(SessionRecord session) throws SQLException {
        writeSession(session, false);
    }

    private void writeSession(SessionRecord session, boolean replace) throws SQLException {
        try (Connection conn = getConnection()) {
            writeSession(conn, session, replace);
        }
    }

    private void writeSession(Connection conn, SessionRecord session, boolean replace) throws SQLException {
        String table = tablePrefix() + "sessions";
        String sql;
        if (!replace) {
            sql = """
                INSERT INTO %s (player_uuid, player_name, area_name, reward_type, mode, start_seconds, start_time,
                    today_count, total_count, today_date, total_seconds, original_walk_speed, original_collidable)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(table);
        } else if (!isMysql()) {
            sql = """
                INSERT INTO %s (player_uuid, player_name, area_name, reward_type, mode, start_seconds, start_time,
                    today_count, total_count, today_date, total_seconds, original_walk_speed, original_collidable)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                    total_seconds = excluded.total_seconds,
                    original_walk_speed = excluded.original_walk_speed,
                    original_collidable = excluded.original_collidable
                """.formatted(table);
        } else {
            sql = """
                INSERT INTO %s (player_uuid, player_name, area_name, reward_type, mode, start_seconds, start_time,
                    today_count, total_count, today_date, total_seconds, original_walk_speed, original_collidable)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                    total_seconds = VALUES(total_seconds),
                    original_walk_speed = VALUES(original_walk_speed),
                    original_collidable = VALUES(original_collidable)
                """.formatted(table);
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            if (session.originalWalkSpeed() == null) ps.setNull(12, java.sql.Types.REAL);
            else ps.setFloat(12, session.originalWalkSpeed());
            if (session.originalCollidable() == null) ps.setNull(13, java.sql.Types.BOOLEAN);
            else ps.setBoolean(13, session.originalCollidable());
            ps.executeUpdate();
        }
    }

    public SessionRecord loadSession(UUID playerUuid) throws SQLException {
        String table = tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_name, area_name, reward_type, mode, start_seconds, start_time, " +
                  "today_count, total_count, today_date, total_seconds, original_walk_speed, original_collidable FROM "
                      + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    float walkSpeed = rs.getFloat("original_walk_speed");
                    Float originalWalkSpeed = rs.wasNull() ? null : walkSpeed;
                    boolean collidable = rs.getBoolean("original_collidable");
                    Boolean originalCollidable = rs.wasNull() ? null : collidable;
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
                        rs.getInt("total_seconds"),
                        originalWalkSpeed,
                        originalCollidable
                    );
                }
            }
        }
        return null;
    }

    public void deleteSession(UUID playerUuid) throws SQLException {
        String table = tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        }
    }

    public void removeUnstartedManualSession(SessionRecord session) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement("DELETE FROM " + tablePrefix()
                 + "sessions WHERE player_uuid=? AND mode='MANUAL' AND start_time=? AND start_seconds=0")) {
            statement.setString(1, session.playerUuid().toString());
            statement.setLong(2, session.startTime());
            statement.executeUpdate();
        }
    }

    /** Update the latest time without reviving a session another server has consumed. */
    public boolean refreshManualCheckpoint(SessionRecord session) throws SQLException {
        String table = tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement("UPDATE " + table
                 + " SET start_seconds=?,today_count=?,total_count=?,today_date=?,total_seconds=?"
                 + " WHERE player_uuid=? AND mode='MANUAL' AND start_time=? AND start_seconds<=?")) {
            statement.setInt(1, session.startSeconds());
            statement.setInt(2, session.todayCount());
            statement.setInt(3, session.totalCount());
            statement.setString(4, session.todayDate());
            statement.setInt(5, session.totalSeconds());
            statement.setString(6, session.playerUuid().toString());
            statement.setLong(7, session.startTime());
            statement.setInt(8, session.startSeconds());
            if (statement.executeUpdate() > 0) return true;
            try (PreparedStatement current = conn.prepareStatement("SELECT 1 FROM " + table
                     + " WHERE player_uuid=? AND mode='MANUAL' AND start_time=? AND start_seconds=?")) {
                current.setString(1, session.playerUuid().toString());
                current.setLong(2, session.startTime());
                current.setInt(3, session.startSeconds());
                try (ResultSet result = current.executeQuery()) { return result.next(); }
            }
        }
    }

    /** Claims the exact checkpoint and leaves a durable reconciliation record before external effects run. */
    public boolean beginManualSettlement(SessionRecord session, UUID claim) throws SQLException {
        String sessions = tablePrefix() + "sessions";
        String settlements = tablePrefix() + "manual_settlements";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement consume = conn.prepareStatement("DELETE FROM " + sessions
                     + " WHERE player_uuid = ? AND start_time = ? AND start_seconds = ? AND mode = 'MANUAL'")) {
                consume.setString(1, session.playerUuid().toString());
                consume.setLong(2, session.startTime());
                consume.setInt(3, session.startSeconds());
                if (consume.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement journal = conn.prepareStatement("INSERT INTO " + settlements + " "
                     + "(player_uuid, start_time, player_name, area_name, reward_type, start_seconds, status, updated_at, claim_id) "
                     + "VALUES (?, ?, ?, ?, ?, ?, 'DISPATCHING', ?, ?)")) {
                    journal.setString(1, session.playerUuid().toString());
                    journal.setLong(2, session.startTime());
                    journal.setString(3, session.playerName());
                    journal.setString(4, session.areaName());
                    journal.setString(5, session.rewardType());
                    journal.setInt(6, session.startSeconds());
                    journal.setLong(7, System.currentTimeMillis());
                    journal.setString(8, claim.toString());
                    journal.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    /** Returns the exact reserved checkpoint when the owning callback confirms no rewards started. */
    public void restoreUnstartedManualSettlement(SessionRecord session, UUID claim) throws SQLException {
        String settlements = tablePrefix() + "manual_settlements";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement release = conn.prepareStatement("DELETE FROM " + settlements
                    + " WHERE player_uuid = ? AND start_time = ? AND claim_id = ? AND status = 'DISPATCHING'")) {
                release.setString(1, session.playerUuid().toString());
                release.setLong(2, session.startTime());
                release.setString(3, claim.toString());
                if (release.executeUpdate() == 1) writeSession(conn, session, false);
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    public void completeManualSettlement(SessionRecord session, int completedRounds) throws SQLException {
        updateManualSettlement(session, "DONE", completedRounds);
    }

    public void flagManualSettlementForReview(SessionRecord session) throws SQLException {
        updateManualSettlement(session, "REVIEW", 0);
    }

    private void updateManualSettlement(SessionRecord session, String status, int completedRounds) throws SQLException {
        String table = tablePrefix() + "manual_settlements";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement("UPDATE " + table
                 + " SET status = ?, completed_rounds = ?, updated_at = ?"
                 + " WHERE player_uuid = ? AND start_time = ? AND status = 'DISPATCHING'")) {
            statement.setString(1, status);
            statement.setInt(2, completedRounds);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, session.playerUuid().toString());
            statement.setLong(5, session.startTime());
            if (statement.executeUpdate() == 1) return;
            try (PreparedStatement lookup = conn.prepareStatement("SELECT status, completed_rounds FROM " + table
                    + " WHERE player_uuid = ? AND start_time = ?")) {
                lookup.setString(1, session.playerUuid().toString());
                lookup.setLong(2, session.startTime());
                try (ResultSet row = lookup.executeQuery()) {
                    if (row.next() && status.equals(row.getString("status"))
                            && completedRounds == row.getInt("completed_rounds")) return;
                }
            }
            throw new SQLException("Missing or conflicting manual settlement receipt");
        }
    }

    public List<ManualSettlementReview> findManualSettlementsForReview(int limit) throws SQLException {
        String table = tablePrefix() + "manual_settlements";
        List<ManualSettlementReview> reviews = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement("SELECT player_uuid, start_time, player_name, area_name,"
                 + " reward_type, start_seconds, status, completed_rounds, updated_at FROM " + table
                 + " WHERE status IN ('DISPATCHING', 'REVIEW') ORDER BY updated_at DESC, player_uuid, start_time LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    reviews.add(new ManualSettlementReview(UUID.fromString(rows.getString("player_uuid")),
                        rows.getLong("start_time"), rows.getString("player_name"), rows.getString("area_name"),
                        rows.getString("reward_type"), rows.getInt("start_seconds"), rows.getString("status"),
                        rows.getInt("completed_rounds"), rows.getLong("updated_at")));
                }
            }
        }
        return reviews;
    }

    public void clearAllSessions() throws SQLException {
        String table = tablePrefix() + "sessions";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM " + table);
        }
    }

    // ── Area Stats ──

    public void saveAreaStats(UUID playerUuid, String areaName, int totalSeconds,
                               int todaySeconds, String todayDate) throws SQLException {
        String table = tablePrefix() + "area_stats";
        String sql;
        if (!isMysql()) {
            sql = """
                INSERT INTO %s (player_uuid, area_name, total_seconds, today_seconds, today_date)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, area_name) DO UPDATE SET
                    total_seconds = excluded.total_seconds,
                    today_seconds = excluded.today_seconds,
                    today_date = excluded.today_date
                """.formatted(table);
        } else {
            sql = mysqlSaveAreaStatsSql(1);
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindAreaStats(ps, playerUuid.toString(), areaName, totalSeconds, todaySeconds, todayDate);
            ps.executeUpdate();
        }
    }

    private String mysqlSaveAreaStatsSql(int rows) {
        return "INSERT INTO " + tablePrefix() + "area_stats"
            + " (player_uuid, area_name, total_seconds, today_seconds, today_date) VALUES "
            + String.join(",", Collections.nCopies(rows, "(?, ?, ?, ?, ?)"))
            + " ON DUPLICATE KEY UPDATE total_seconds = GREATEST(total_seconds, VALUES(total_seconds)),"
            + " today_seconds = CASE WHEN VALUES(today_date) > today_date THEN VALUES(today_seconds)"
            + " WHEN VALUES(today_date) = today_date THEN GREATEST(today_seconds, VALUES(today_seconds))"
            + " ELSE today_seconds END, today_date = GREATEST(today_date, VALUES(today_date))";
    }

    private static void bindAreaStats(PreparedStatement ps, String uuid, String areaName, int totalSeconds,
                                      int todaySeconds, String todayDate) throws SQLException {
        bindAreaStats(ps, 1, uuid, areaName, totalSeconds, todaySeconds, todayDate);
    }

    private static void bindAreaStats(PreparedStatement ps, int first, String uuid, String areaName, int totalSeconds,
                                      int todaySeconds, String todayDate) throws SQLException {
        ps.setString(first, uuid);
        ps.setString(first + 1, areaName);
        ps.setInt(first + 2, totalSeconds);
        ps.setInt(first + 3, todaySeconds);
        ps.setString(first + 4, todayDate);
    }

    public AreaStats loadAreaStats(UUID playerUuid, String areaName) throws SQLException {
        String table = tablePrefix() + "area_stats";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT total_seconds, today_seconds, today_date FROM " + table + " WHERE player_uuid = ? AND area_name = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, areaName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AreaStats(
                        areaName,
                        rs.getInt("total_seconds"),
                        rs.getInt("today_seconds"),
                        nullToEmpty(rs.getString("today_date"))
                    );
                }
            }
        }
        return new AreaStats(areaName, 0, 0, "");
    }

    public List<AreaStats> loadAllAreaStats(UUID playerUuid) throws SQLException {
        String table = tablePrefix() + "area_stats";
        List<AreaStats> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT area_name, total_seconds, today_seconds, today_date FROM " + table + " WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AreaStats(
                        nullToEmpty(rs.getString("area_name")),
                        rs.getInt("total_seconds"),
                        rs.getInt("today_seconds"),
                        nullToEmpty(rs.getString("today_date"))
                    ));
                }
            }
        }
        return list;
    }

    // ── Leaderboard ──

    public List<PlayerStats> loadLeaderboard(int limit) throws SQLException {
        String table = tablePrefix() + "stats";
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
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(saveStatsSql())) {
            bindStats(ps, playerUuid, stats);
            ps.executeUpdate();
        }
    }

    /** Writes one periodic checkpoint of trusted player counters in a single transaction. */
    public void saveStatsBatch(Map<UUID, PlayerStats> entries) throws SQLException {
        saveStatsBatch(entries, List.of());
    }

    public void saveStatsBatch(Map<UUID, PlayerStats> entries, List<SessionRecord> sessions) throws SQLException {
        if (entries.isEmpty() && sessions.isEmpty()) return;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!entries.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(saveStatsSql())) {
                        for (var entry : entries.entrySet()) {
                            bindStats(ps, entry.getKey(), entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!sessions.isEmpty()) advanceSessionCheckpoints(conn, sessions);
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    /** Saves elapsed counters and advances existing MANUAL sessions after logout. */
    public void saveQuitCheckpoints(List<QuitCheckpoint> checkpoints) throws SQLException {
        var players = new ArrayList<QuitCheckpoint>();
        var areas = new ArrayList<QuitArea>();
        var sessions = new ArrayList<SessionRecord>();
        for (QuitCheckpoint checkpoint : checkpoints) {
            if (checkpoint.stats() != null) players.add(checkpoint);
            for (AreaStats area : checkpoint.areas()) areas.add(new QuitArea(checkpoint.uuid(), area));
            if (checkpoint.session() != null) sessions.add(checkpoint.session());
        }
        if (players.isEmpty() && areas.isEmpty() && sessions.isEmpty()) return;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveQuitPlayerStats(conn, players);
                saveQuitAreaStats(conn, areas);
                if (!sessions.isEmpty()) advanceSessionCheckpoints(conn, sessions);
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private void saveQuitPlayerStats(Connection conn, List<QuitCheckpoint> players) throws SQLException {
        // Connector/J can send JDBC batches as separate queries. Use bounded multi-row statements
        // so a full queue does not need one MySQL round trip per player.
        for (int start = 0; start < players.size(); start += QUIT_ROWS_PER_STATEMENT) {
            int end = Math.min(start + QUIT_ROWS_PER_STATEMENT, players.size());
            String table = tablePrefix() + "stats";
            String merge = isMysql()
                ? " ON DUPLICATE KEY UPDATE total_seconds = GREATEST(total_seconds, VALUES(total_seconds))"
                : " ON CONFLICT(player_uuid) DO UPDATE SET total_seconds = MAX(" + table + ".total_seconds, excluded.total_seconds)";
            String sql = "INSERT INTO " + table + " (player_uuid, player_name, total_seconds) VALUES "
                + String.join(",", Collections.nCopies(end - start, "(?, ?, ?)"))
                + merge;
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                int index = 1;
                for (QuitCheckpoint checkpoint : players.subList(start, end)) {
                    statement.setString(index++, checkpoint.uuid().toString());
                    statement.setString(index++, checkpoint.stats().playerName());
                    statement.setInt(index++, checkpoint.stats().totalSeconds());
                }
                statement.executeUpdate();
            }
        }
    }

    private void saveQuitAreaStats(Connection conn, List<QuitArea> areas) throws SQLException {
        for (int start = 0; start < areas.size(); start += QUIT_ROWS_PER_STATEMENT) {
            int end = Math.min(start + QUIT_ROWS_PER_STATEMENT, areas.size());
            String sql = isMysql() ? mysqlSaveAreaStatsSql(end - start) : sqliteSaveQuitAreaStatsSql(end - start);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                int index = 1;
                for (QuitArea entry : areas.subList(start, end)) {
                    AreaStats row = entry.stats();
                    bindAreaStats(statement, index, entry.uuid().toString(), row.areaName(), row.totalSeconds(),
                        row.todaySeconds(), row.todayDate());
                    index += 5;
                }
                statement.executeUpdate();
            }
        }
    }

    private String sqliteSaveQuitAreaStatsSql(int rows) {
        String table = tablePrefix() + "area_stats";
        return "INSERT INTO " + table
            + " (player_uuid, area_name, total_seconds, today_seconds, today_date) VALUES "
            + String.join(",", Collections.nCopies(rows, "(?, ?, ?, ?, ?)"))
            + " ON CONFLICT(player_uuid, area_name) DO UPDATE SET total_seconds = MAX(" + table + ".total_seconds, excluded.total_seconds),"
            + " today_seconds = CASE WHEN excluded.today_date > " + table + ".today_date THEN excluded.today_seconds"
            + " WHEN excluded.today_date = " + table + ".today_date THEN MAX(" + table + ".today_seconds, excluded.today_seconds)"
            + " ELSE " + table + ".today_seconds END, today_date = MAX(" + table + ".today_date, excluded.today_date)";
    }

    private record QuitArea(UUID uuid, AreaStats stats) {}

    private void advanceSessionCheckpoints(Connection conn, List<SessionRecord> sessions) throws SQLException {
        // A delayed checkpoint can advance only its own still-active session.
        String sql = "UPDATE " + tablePrefix() + "sessions"
            + " SET start_seconds = ?, today_count = ?, total_count = ?, today_date = ?, total_seconds = ?"
            + " WHERE player_uuid = ? AND start_time = ? AND start_seconds < ? AND mode = 'MANUAL'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SessionRecord session : sessions) {
                ps.setInt(1, session.startSeconds());
                ps.setInt(2, session.todayCount());
                ps.setInt(3, session.totalCount());
                ps.setString(4, session.todayDate());
                ps.setInt(5, session.totalSeconds());
                ps.setString(6, session.playerUuid().toString());
                ps.setLong(7, session.startTime());
                ps.setInt(8, session.startSeconds());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String saveStatsSql() {
        String table = tablePrefix() + "stats";
        if (!isMysql()) {
            return """
                INSERT INTO %s (player_uuid, player_name, today_date, today_count, total_count, total_seconds)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    today_date = excluded.today_date,
                    today_count = excluded.today_count,
                    total_count = excluded.total_count,
                    total_seconds = excluded.total_seconds
                """.formatted(table);
        }
        return """
            INSERT INTO %s (player_uuid, player_name, today_date, today_count, total_count, total_seconds)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                today_date = VALUES(today_date),
                today_count = VALUES(today_count),
                total_count = VALUES(total_count),
                total_seconds = GREATEST(total_seconds, VALUES(total_seconds))
            """.formatted(table);
    }

    private static void bindStats(PreparedStatement ps, UUID playerUuid, PlayerStats stats) throws SQLException {
        ps.setString(1, playerUuid.toString());
        ps.setString(2, stats.playerName());
        ps.setString(3, stats.todayDate());
        ps.setInt(4, stats.todayCount());
        ps.setInt(5, stats.totalCount());
        ps.setInt(6, stats.totalSeconds());
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

    public record QuitCheckpoint(UUID uuid, PlayerStats stats, List<AreaStats> areas, SessionRecord session) {}

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
        int totalSeconds,
        Float originalWalkSpeed,
        Boolean originalCollidable
    ) {}

    public record ManualSettlementReview(UUID playerUuid, long startTime, String playerName, String areaName,
                                         String rewardType, int seconds, String status, int completedRounds,
                                         long updatedAt) {}

    public record AreaStats(
        String areaName,
        int totalSeconds,
        int todaySeconds,
        String todayDate
    ) {}
}
