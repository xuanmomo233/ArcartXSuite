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
 * </ul>
 */
public final class JdbcAbyssRepository extends AbstractModuleRepository {

    private boolean sqlite = true;
    private String tPlayer, tCooldowns, tRecords, tPending;

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

        try (Statement st = conn.createStatement()) {
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
        return playerDataTables();
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

    /** 读取并删除待恢复位置。无记录 null。 */
    public @Nullable PendingLocation takePendingLocation(UUID player) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + tPending + " WHERE player_uuid = ?";
        PendingLocation loc = null;
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loc = new PendingLocation(rs.getString(1),
                        rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getFloat(5), rs.getFloat(6));
                }
            }
        }
        if (loc != null) {
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + tPending + " WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
        }
        return loc;
    }

    public record PendingLocation(String world, double x, double y, double z,
                                  float yaw, float pitch) { }
}
