package xuanmo.arcartxsuite.abyss.data;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * Abyss 数据仓库（MySQL/SQLite 双方言）。
 * <p>
 * 数据源与表前缀由 {@code StorageManager.resolveModuleDataSource} 统一解析；
 * 共享模式用宿主池+模块前缀，模块配置独立 storage 时由 resolveModuleDataSource 建模块池。
 *
 * <p>表：
 * <ul>
 *   <li>{@code <prefix>player} —— 体力值、上次回复时间</li>
 *   <li>{@code <prefix>cooldowns} —— 进本冷却（player_uuid + dungeon_id）</li>
 *   <li>{@code <prefix>records} —— 通关次数、最佳用时</li>
 *   <li>{@code <prefix>pending_location} —— 断线待恢复位置（重进服拉回）</li>
 *   <li>{@code <prefix>globals} —— 全服共享计数器（跨副本/跨实例，如全服收集进度）</li>
 * </ul>
 */
public final class JdbcAbyssRepository extends AbstractModuleRepository {

    private boolean sqlite = true;
    private String tPlayer, tCooldowns, tRecords, tPending, tGlobals, tAdmission, tRunResults;
    private String tResultSequence, tResultFeed, tAdmissionArchive;

    public JdbcAbyssRepository(DataSource dataSource, StorageDescriptor descriptor,
                             File dataFolder, Logger logger) {
        super("AXS-Abyss", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        sqlite = !isMysql();
        String p = tablePrefix();
        tPlayer = p + "player";
        tCooldowns = p + "cooldowns";
        tRecords = p + "records";
        tPending = p + "pending_location";
        tGlobals = p + "globals";
        tAdmission = p + "admission_requests";
        tRunResults = p + "run_results";
        tResultSequence = p + "result_sequence";
        tResultFeed = p + "result_feed";
        tAdmissionArchive = p + "admission_archive";

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmission
                + " (owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL,"
                + " fingerprint VARCHAR(64) NOT NULL, snapshot TEXT NOT NULL, PRIMARY KEY (owner, request_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRunResults
                + " (run_id VARCHAR(36) PRIMARY KEY, owner VARCHAR(96) NOT NULL,"
                + " ended_at BIGINT NOT NULL, snapshot TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tResultSequence + " (id INTEGER PRIMARY KEY, seq BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tResultFeed
                + " (seq BIGINT PRIMARY KEY, run_id VARCHAR(36) NOT NULL UNIQUE)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmissionArchive
                + " (owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL, snapshot TEXT NOT NULL,"
                + " archived_at BIGINT NOT NULL, PRIMARY KEY (owner, request_id))");
            st.executeUpdate(sqlite ? "INSERT INTO " + tResultSequence + " VALUES (1,0) ON CONFLICT(id) DO NOTHING"
                : "INSERT INTO " + tResultSequence + " VALUES (1,0) ON DUPLICATE KEY UPDATE id=id");
            if (sqlite) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayer + " ("
                    + "player_uuid TEXT PRIMARY KEY,"
                    + "stamina INTEGER NOT NULL DEFAULT 0,"
                    + "stamina_last_regen INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCooldowns + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "dungeon_id TEXT NOT NULL,"
                    + "expiry_millis INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecords + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "dungeon_id TEXT NOT NULL,"
                    + "completions INTEGER NOT NULL DEFAULT 0,"
                    + "best_time_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "last_time_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
                    + "player_uuid TEXT PRIMARY KEY,"
                    + "world TEXT NOT NULL,"
                    + "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,"
                    + "yaw REAL NOT NULL DEFAULT 0, pitch REAL NOT NULL DEFAULT 0,"
                    + "saved_at INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobals + " ("
                    + "gkey TEXT PRIMARY KEY,"
                    + "gvalue INTEGER NOT NULL DEFAULT 0)");
            } else {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayer + " ("
                    + "player_uuid CHAR(36) PRIMARY KEY,"
                    + "stamina INT NOT NULL DEFAULT 0,"
                    + "stamina_last_regen BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCooldowns + " ("
                    + "player_uuid CHAR(36) NOT NULL,"
                    + "dungeon_id VARCHAR(64) NOT NULL,"
                    + "expiry_millis BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecords + " ("
                    + "player_uuid CHAR(36) NOT NULL,"
                    + "dungeon_id VARCHAR(64) NOT NULL,"
                    + "completions INT NOT NULL DEFAULT 0,"
                    + "best_time_seconds BIGINT NOT NULL DEFAULT 0,"
                    + "last_time_seconds BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
                    + "player_uuid CHAR(36) PRIMARY KEY,"
                    + "world VARCHAR(64) NOT NULL,"
                    + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                    + "yaw FLOAT NOT NULL DEFAULT 0, pitch FLOAT NOT NULL DEFAULT 0,"
                    + "saved_at BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobals + " ("
                    + "gkey VARCHAR(64) PRIMARY KEY,"
                    + "gvalue BIGINT NOT NULL DEFAULT 0)");
            }
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_abyss_records_dungeon ON "
            + tRecords + "(dungeon_id, best_time_seconds)");
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tPlayer, tCooldowns, tRecords, tPending);
    }

    @Override
    protected List<String> allTables() {
        // globals 是全服共享计数（非玩家数据），不列入 playerDataTables 防清理误删
        List<String> all = new ArrayList<>(playerDataTables());
        all.add(tGlobals);
        all.add(tAdmission);
        all.add(tRunResults);
        all.add(tResultSequence);
        all.add(tResultFeed);
        all.add(tAdmissionArchive);
        return all;
    }

    public record AdmissionReceipt(String owner, String requestId, String fingerprint, String snapshot) { }
    public record CommittedSnapshot(long sequence, String snapshot) { }

    public boolean insertRunResult(UUID run, String owner, long endedAt, String snapshot) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tRunResults + " (run_id, owner, ended_at, snapshot) VALUES (?, ?, ?, ?) ON CONFLICT(run_id) DO NOTHING"
            : "INSERT INTO " + tRunResults + " (run_id, owner, ended_at, snapshot) VALUES (?, ?, ?, ?)";
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                lockResultSequence(c);
                boolean inserted;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, run.toString()); ps.setString(2, owner);
                    ps.setLong(3, endedAt); ps.setString(4, snapshot);
                    inserted = ps.executeUpdate() == 1;
                }
                if (inserted) appendResultFeed(c, run.toString());
                c.commit();
                return inserted;
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            if (!sqlite && failure.getErrorCode() == 1062) return false;
            throw failure;
        }
    }

    private void lockResultSequence(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // Serialize publishers until commit, including when multiple servers share MySQL.
            st.executeUpdate("UPDATE " + tResultSequence + " SET seq=seq+1 WHERE id=1");
        }
    }

    private void appendResultFeed(Connection c, String run) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tResultFeed
                + " (seq, run_id) SELECT seq, ? FROM " + tResultSequence + " WHERE id=1")) {
            ps.setString(1, run);
            ps.executeUpdate();
        }
    }

    public void backfillResultFeed() throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                lockResultSequence(c);
                var pending = new ArrayList<String>();
                try (Statement st = c.createStatement(); ResultSet rows = st.executeQuery(
                        "SELECT r.run_id FROM " + tRunResults + " r LEFT JOIN " + tResultFeed
                        + " f ON f.run_id=r.run_id WHERE f.run_id IS NULL ORDER BY r.ended_at,r.run_id")) {
                    while (rows.next()) pending.add(rows.getString(1));
                }
                for (String run : pending) {
                    appendResultFeed(c, run);
                    lockResultSequence(c);
                }
                c.commit();
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    public List<CommittedSnapshot> committedResults(String owner, long after, int limit) throws SQLException {
        var results = new ArrayList<CommittedSnapshot>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT f.seq,r.snapshot FROM " + tResultFeed + " f JOIN " + tRunResults
                + " r ON r.run_id=f.run_id WHERE f.seq>? AND r.owner=? ORDER BY f.seq LIMIT ?")) {
            ps.setLong(1, after); ps.setString(2, owner); ps.setInt(3, limit);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) results.add(new CommittedSnapshot(rows.getLong(1), rows.getString(2)));
            }
        }
        return results;
    }

    public @Nullable String loadRunResult(UUID run) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT snapshot FROM " + tRunResults + " WHERE run_id=?")) {
            ps.setString(1, run.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    public List<String> loadRunResults(String owner, long afterTime, String afterRun, int limit) throws SQLException {
        var results = new ArrayList<String>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT snapshot FROM " + tRunResults
                + " WHERE owner=? AND (ended_at>? OR (ended_at=? AND run_id>?))"
                + " ORDER BY ended_at, run_id LIMIT ?")) {
            ps.setString(1, owner); ps.setLong(2, afterTime); ps.setLong(3, afterTime);
            ps.setString(4, afterRun); ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) results.add(rs.getString(1)); }
        }
        return results;
    }

    public List<AdmissionReceipt> loadAdmissions() throws SQLException {
        var receipts = new ArrayList<AdmissionReceipt>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT owner, request_id, fingerprint, snapshot FROM " + tAdmission + " LIMIT 32769");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) receipts.add(new AdmissionReceipt(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        return receipts;
    }

    public @Nullable AdmissionReceipt loadAdmission(String owner, UUID request) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT a.fingerprint,COALESCE(b.snapshot,a.snapshot) FROM " + tAdmission + " a LEFT JOIN "
                + tAdmissionArchive + " b ON a.owner=b.owner AND a.request_id=b.request_id"
                + " WHERE a.owner=? AND a.request_id=?")) {
            ps.setString(1, owner); ps.setString(2, request.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AdmissionReceipt(owner, request.toString(), rs.getString(1), rs.getString(2)) : null;
            }
        }
    }

    public boolean archiveAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + tAdmission
                        + " SET snapshot='' WHERE owner=? AND request_id=? AND snapshot=?")) {
                    ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId()); ps.setString(3, receipt.snapshot());
                    if (ps.executeUpdate() != 1) { c.rollback(); return false; }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tAdmissionArchive
                        + " (owner,request_id,snapshot,archived_at) VALUES (?,?,?,?)")) {
                    ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId());
                    ps.setString(3, receipt.snapshot()); ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    public void insertAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO " + tAdmission
                + " (owner, request_id, fingerprint, snapshot) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId());
            ps.setString(3, receipt.fingerprint()); ps.setString(4, receipt.snapshot());
            ps.executeUpdate();
        }
    }

    public void updateAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE " + tAdmission
                + " SET snapshot=? WHERE owner=? AND request_id=? AND fingerprint=? AND snapshot<>''")) {
            ps.setString(1, receipt.snapshot()); ps.setString(2, receipt.owner());
            ps.setString(3, receipt.requestId()); ps.setString(4, receipt.fingerprint());
            if (ps.executeUpdate() != 1) throw new SQLException("Admission receipt not found");
        }
    }

    // ═══ 体力 ═══

    public void saveStamina(UUID player, int stamina, long lastRegen) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tPlayer + " (player_uuid, stamina, stamina_last_regen)"
              + " VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET"
              + " stamina=excluded.stamina, stamina_last_regen=excluded.stamina_last_regen"
            : "INSERT INTO " + tPlayer + " (player_uuid, stamina, stamina_last_regen)"
              + " VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE"
              + " stamina=VALUES(stamina), stamina_last_regen=VALUES(stamina_last_regen)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setInt(2, stamina);
            ps.setLong(3, lastRegen);
            ps.executeUpdate();
        }
    }

    /** @return [stamina, lastRegenMillis]；无记录返回 null。 */
    public @Nullable long[] loadStamina(UUID player) throws SQLException {
        String sql = "SELECT stamina, stamina_last_regen FROM " + tPlayer + " WHERE player_uuid = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new long[]{rs.getInt(1), rs.getLong(2)};
                }
            }
        }
        return null;
    }

    // ═══ 冷却 ═══

    public void saveCooldown(UUID player, String dungeonId, long expiryMillis) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tCooldowns + " (player_uuid, dungeon_id, expiry_millis)"
              + " VALUES (?, ?, ?) ON CONFLICT(player_uuid, dungeon_id) DO UPDATE SET"
              + " expiry_millis=excluded.expiry_millis"
            : "INSERT INTO " + tCooldowns + " (player_uuid, dungeon_id, expiry_millis)"
              + " VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE expiry_millis=VALUES(expiry_millis)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            ps.setLong(3, expiryMillis);
            ps.executeUpdate();
        }
    }

    public long loadCooldown(UUID player, String dungeonId) throws SQLException {
        String sql = "SELECT expiry_millis FROM " + tCooldowns
            + " WHERE player_uuid = ? AND dungeon_id = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public Map<String, Long> loadAllCooldowns(UUID player) throws SQLException {
        String sql = "SELECT dungeon_id, expiry_millis FROM " + tCooldowns + " WHERE player_uuid = ?";
        Map<String, Long> m = new LinkedHashMap<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) m.put(rs.getString(1), rs.getLong(2));
            }
        }
        return m;
    }

    // ═══ 通关记录 ═══

    public void saveRecord(UUID player, String dungeonId, long timeSeconds) throws SQLException {
        // completions+1，best_time 取最小（0 视为无记录）
        String sql = sqlite
            ? "INSERT INTO " + tRecords + " (player_uuid, dungeon_id, completions, best_time_seconds, last_time_seconds)"
              + " VALUES (?, ?, 1, ?, ?) ON CONFLICT(player_uuid, dungeon_id) DO UPDATE SET"
              + " completions=completions+1,"
              + " best_time_seconds=CASE WHEN best_time_seconds=0 OR excluded.best_time_seconds<best_time_seconds"
              + " THEN excluded.best_time_seconds ELSE best_time_seconds END,"
              + " last_time_seconds=excluded.last_time_seconds"
            : "INSERT INTO " + tRecords + " (player_uuid, dungeon_id, completions, best_time_seconds, last_time_seconds)"
              + " VALUES (?, ?, 1, ?, ?) ON DUPLICATE KEY UPDATE"
              + " completions=completions+1,"
              + " best_time_seconds=IF(best_time_seconds=0 OR VALUES(best_time_seconds)<best_time_seconds,"
              + " VALUES(best_time_seconds), best_time_seconds),"
              + " last_time_seconds=VALUES(last_time_seconds)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            ps.setLong(3, timeSeconds);
            ps.setLong(4, timeSeconds);
            ps.executeUpdate();
        }
    }

    /** 单玩家某地牢记录：[completions, bestTimeSeconds]。无记录 null。 */
    public @Nullable long[] loadRecord(UUID player, String dungeonId) throws SQLException {
        String sql = "SELECT completions, best_time_seconds FROM " + tRecords
            + " WHERE player_uuid = ? AND dungeon_id = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new long[]{rs.getLong(1), rs.getLong(2)};
            }
        }
        return null;
    }

    /** 地牢排行榜：最佳用时升序。 */
    public List<LeaderboardEntry> loadLeaderboard(String dungeonId, int limit) throws SQLException {
        String sql = "SELECT player_uuid, best_time_seconds, completions FROM " + tRecords
            + " WHERE dungeon_id = ? AND best_time_seconds > 0"
            + " ORDER BY best_time_seconds ASC LIMIT ?";
        List<LeaderboardEntry> list = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    list.add(new LeaderboardEntry(rank++,
                        UUID.fromString(rs.getString(1)),
                        rs.getLong(2), rs.getLong(3)));
                }
            }
        }
        return list;
    }

    public record LeaderboardEntry(int rank, UUID playerUuid, long bestTimeSeconds, long completions) { }

    // ═══ 断线待恢复位置 ═══

    public void savePendingLocation(UUID player, String world,
                                    double x, double y, double z,
                                    float yaw, float pitch) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tPending + " (player_uuid, world, x, y, z, yaw, pitch, saved_at)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET"
              + " world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,"
              + " yaw=excluded.yaw, pitch=excluded.pitch, saved_at=excluded.saved_at"
            : "INSERT INTO " + tPending + " (player_uuid, world, x, y, z, yaw, pitch, saved_at)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
              + " world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),"
              + " yaw=VALUES(yaw), pitch=VALUES(pitch), saved_at=VALUES(saved_at)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat(6, yaw);
            ps.setFloat(7, pitch);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    /** 只读恢复位置，传送成功后才确认删除。 */
    public @Nullable PendingLocation loadPendingLocation(UUID player) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch, saved_at FROM " + tPending + " WHERE player_uuid = ?";
        PendingLocation loc = null;
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loc = new PendingLocation(rs.getString(1),
                        rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getFloat(5), rs.getFloat(6),rs.getLong(7));
                }
            }
        }
        return loc;
    }

    public boolean confirmPendingLocation(UUID player,PendingLocation expected)throws SQLException {
        try(Connection c=getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM "+tPending
            +" WHERE player_uuid=? AND saved_at=? AND world=? AND x=? AND y=? AND z=? AND yaw=? AND pitch=?")) {
            ps.setString(1,player.toString());ps.setLong(2,expected.savedAt());ps.setString(3,expected.world());
            ps.setDouble(4,expected.x());ps.setDouble(5,expected.y());ps.setDouble(6,expected.z());
            ps.setFloat(7,expected.yaw());ps.setFloat(8,expected.pitch());return ps.executeUpdate()==1;
        }
    }

    public record PendingLocation(String world, double x, double y, double z,
                                  float yaw, float pitch,long savedAt) { }

    // ═══ 全服共享计数器 ═══

    /** 启动时全量加载（计数器通常个位数，全表读无碍）。 */
    public Map<String, Long> loadAllGlobals() throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT gkey, gvalue FROM " + tGlobals);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString(1), rs.getLong(2));
        }
        return m;
    }

    public void saveGlobal(String key, long value) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tGlobals + " (gkey, gvalue) VALUES (?, ?)"
              + " ON CONFLICT(gkey) DO UPDATE SET gvalue=excluded.gvalue"
            : "INSERT INTO " + tGlobals + " (gkey, gvalue) VALUES (?, ?)"
              + " ON DUPLICATE KEY UPDATE gvalue=VALUES(gvalue)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setLong(2, value);
            ps.executeUpdate();
        }
    }
}
