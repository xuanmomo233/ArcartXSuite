package xuanmo.arcartxsuite.essentials.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration.StorageConfiguration;

public final class EssentialsRepository extends AbstractModuleRepository implements AutoCloseable {

    private final Logger logger;
    private final String prefix;
    private final boolean mysql;

    public EssentialsRepository(File pluginDataFolder, StorageConfiguration config, Logger logger) {
        super("AXS-Essentials", pluginDataFolder, config.toDescriptor(), logger);
        this.logger = logger;
        this.prefix = config.tablePrefix();
        this.mysql = config.dialect() == StorageConfiguration.Dialect.MYSQL;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            initTables(stmt);
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(prefix + "homes", prefix + "bans", prefix + "warnings", prefix + "nicknames", prefix + "player_data");
    }

    @Override
    protected List<String> allTables() {
        return List.of(
            prefix + "homes",
            prefix + "bans",
            prefix + "warnings",
            prefix + "nicknames",
            prefix + "player_data",
            prefix + "warps",
            prefix + "spawn"
        );
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    private void initTables(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "homes ("
            + "uuid VARCHAR(36) NOT NULL, name VARCHAR(64) NOT NULL, "
            + "world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
            + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, "
            + "PRIMARY KEY (uuid, name))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "warps ("
            + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
            + "world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
            + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "spawn ("
            + "id INT NOT NULL PRIMARY KEY, "
            + "world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
            + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "bans ("
            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "player_name VARCHAR(32) NOT NULL, "
            + "reason TEXT, operator VARCHAR(32) NOT NULL, "
            + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, "
            + "ip VARCHAR(45))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "warnings ("
            + "id INTEGER PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", "
            + "uuid VARCHAR(36) NOT NULL, "
            + "player_name VARCHAR(32) NOT NULL, "
            + "reason TEXT, operator VARCHAR(32) NOT NULL, "
            + "created_at BIGINT NOT NULL)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "nicknames ("
            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "nickname VARCHAR(128) NOT NULL)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "player_data ("
            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "last_seen BIGINT NOT NULL DEFAULT 0, "
            + "last_ip VARCHAR(45), "
            + "first_join BIGINT NOT NULL DEFAULT 0)");
    }

    // ─── Homes ───

    public void setHome(UUID uuid, String name, Location loc) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public void deleteHome(UUID uuid, String name) throws SQLException {
        String sql = "DELETE FROM " + prefix + "homes WHERE uuid=? AND name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
        }
    }

    public Location getHome(UUID uuid, String name) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + prefix + "homes WHERE uuid=? AND name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return toLocation(rs);
            }
        }
        return null;
    }

    public Map<String, Location> getHomes(UUID uuid) throws SQLException {
        Map<String, Location> homes = new HashMap<>();
        String sql = "SELECT name, world, x, y, z, yaw, pitch FROM " + prefix + "homes WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Location loc = toLocation(rs);
                    if (loc != null) homes.put(rs.getString("name"), loc);
                }
            }
        }
        return homes;
    }

    public int homeCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + prefix + "homes WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ─── Warps ───

    public void setWarp(String name, Location loc) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "warps (name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public void deleteWarp(String name) throws SQLException {
        String sql = "DELETE FROM " + prefix + "warps WHERE name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.executeUpdate();
        }
    }

    public Location getWarp(String name) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + prefix + "warps WHERE name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return toLocation(rs);
            }
        }
        return null;
    }

    public List<String> getWarpNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM " + prefix + "warps ORDER BY name";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) names.add(rs.getString("name"));
        }
        return names;
    }

    // ─── Spawn ───

    public void setSpawn(Location loc) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "spawn (id, world, x, y, z, yaw, pitch) VALUES (1,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public Location getSpawn() throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + prefix + "spawn WHERE id=1";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return toLocation(rs);
        }
        return null;
    }

    // ─── Bans ───

    public void ban(UUID uuid, String playerName, String reason, String operator, long expiresAt, String ip) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "bans (uuid, player_name, reason, operator, created_at, expires_at, ip) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setString(4, operator);
            ps.setLong(5, System.currentTimeMillis());
            ps.setLong(6, expiresAt);
            ps.setString(7, ip);
            ps.executeUpdate();
        }
    }

    public void unban(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + prefix + "bans WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public List<BanRecord> getAllBans() throws SQLException {
        String sql = "SELECT * FROM " + prefix + "bans ORDER BY created_at DESC";
        List<BanRecord> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(BanRecord.from(rs));
        }
        return list;
    }

    public BanRecord getBan(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM " + prefix + "bans WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return BanRecord.from(rs);
            }
        }
        return null;
    }

    // ─── Warnings ───

    public void addWarning(UUID uuid, String playerName, String reason, String operator) throws SQLException {
        String sql = "INSERT INTO " + prefix + "warnings (uuid, player_name, reason, operator, created_at) VALUES (?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setString(4, operator);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public int getWarningCount(UUID uuid, long since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + prefix + "warnings WHERE uuid=? AND created_at>=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<WarnRecord> getWarnings(UUID uuid) throws SQLException {
        List<WarnRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "warnings WHERE uuid=? ORDER BY created_at DESC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(WarnRecord.from(rs));
            }
        }
        return list;
    }

    // ─── Nicknames ───

    public void setNickname(UUID uuid, String nickname) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "nicknames (uuid, nickname) VALUES (?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, nickname);
            ps.executeUpdate();
        }
    }

    public void removeNickname(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + prefix + "nicknames WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public String getNickname(UUID uuid) throws SQLException {
        String sql = "SELECT nickname FROM " + prefix + "nicknames WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("nickname") : null;
            }
        }
    }

    // ─── Player Data ───

    public void updatePlayerData(UUID uuid, String ip) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            // 首次加入则插入
            String insert = mysql
                ? "INSERT IGNORE INTO " + prefix + "player_data (uuid, last_seen, last_ip, first_join) VALUES (?,?,?,?)"
                : "INSERT OR IGNORE INTO " + prefix + "player_data (uuid, last_seen, last_ip, first_join) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, now);
                ps.setString(3, ip);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            // 更新最后在线时间和 IP
            String update = "UPDATE " + prefix + "player_data SET last_seen=?, last_ip=? WHERE uuid=?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setLong(1, now);
                ps.setString(2, ip);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    // ─── Records ───

    public record BanRecord(UUID uuid, String playerName, String reason, String operator, long createdAt, long expiresAt, String ip) {
        static BanRecord from(ResultSet rs) throws SQLException {
            return new BanRecord(
                UUID.fromString(rs.getString("uuid")), rs.getString("player_name"),
                rs.getString("reason"), rs.getString("operator"),
                rs.getLong("created_at"), rs.getLong("expires_at"), rs.getString("ip"));
        }
        public boolean isExpired() { return expiresAt > 0 && System.currentTimeMillis() >= expiresAt; }
        public boolean isPermanent() { return expiresAt <= 0; }
    }

    public record WarnRecord(int id, UUID uuid, String playerName, String reason, String operator, long createdAt) {
        static WarnRecord from(ResultSet rs) throws SQLException {
            return new WarnRecord(
                rs.getInt("id"), UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"), rs.getString("reason"),
                rs.getString("operator"), rs.getLong("created_at"));
        }
    }

    // ─── Helper ───
    private static Location toLocation(ResultSet rs) throws SQLException {
        World world = Bukkit.getWorld(rs.getString("world"));
        if (world == null) return null;
        return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
            rs.getFloat("yaw"), rs.getFloat("pitch"));
    }
}
