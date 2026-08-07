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

/**
 * Essentials 模块数据仓储。
 * <p>
 * 继承 {@link AbstractModuleRepository}，支持 SQLite 与 MySQL 双方言，
 * 负责家、传送点、出生点、封禁、警告、昵称与玩家数据的持久化读写。
 * 实现了玩家数据清除（{@code playerDataTables}）与全表枚举（{@code allTables}）
 * 以支持宿主的统一数据管理与数据库迁移能力。
 */
public final class EssentialsRepository extends AbstractModuleRepository implements AutoCloseable {

    private final Logger logger;
    private final String prefix;
    private final boolean mysql;

    /**
     * 构造数据仓储。
     *
     * @param pluginDataFolder 插件数据目录
     * @param config           存储配置（方言、连接参数、表前缀）
     * @param logger           日志器
     */
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

    /**
     * 新增或更新玩家的家。
     *
     * @param uuid 玩家 UUID
     * @param name 家名称
     * @param loc  家位置
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 删除玩家的指定家。
     *
     * @param uuid 玩家 UUID
     * @param name 家名称
     * @throws SQLException 数据库操作失败时抛出
     */
    public void deleteHome(UUID uuid, String name) throws SQLException {
        String sql = "DELETE FROM " + prefix + "homes WHERE uuid=? AND name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
        }
    }

    /**
     * 查询玩家的指定家位置。
     *
     * @param uuid 玩家 UUID
     * @param name 家名称
     * @return 家位置，不存在时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 查询玩家全部家及其位置。
     *
     * @param uuid 玩家 UUID
     * @return 家名称到位置的映射，无记录时返回空映射
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 统计玩家已设置的家数量。
     *
     * @param uuid 玩家 UUID
     * @return 家数量
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 新增或更新公共传送点。
     *
     * @param name 传送点名称
     * @param loc  传送点位置
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 删除指定公共传送点。
     *
     * @param name 传送点名称
     * @throws SQLException 数据库操作失败时抛出
     */
    public void deleteWarp(String name) throws SQLException {
        String sql = "DELETE FROM " + prefix + "warps WHERE name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.executeUpdate();
        }
    }

    /**
     * 查询指定公共传送点的位置。
     *
     * @param name 传送点名称
     * @return 传送点位置，不存在时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 返回全部公共传送点名称列表（按名称排序）。
     *
     * @return 传送点名称列表
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 设置服务器出生点位置。
     *
     * @param loc 出生点位置
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 查询服务器出生点位置。
     *
     * @return 出生点位置，未设置时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 新增或更新封禁记录。
     *
     * @param uuid      玩家 UUID
     * @param playerName 玩家名称
     * @param reason    封禁原因
     * @param operator  操作者名称
     * @param expiresAt 过期时间戳（毫秒），{@code <=0} 表示永久封禁
     * @param ip        玩家 IP 地址，可为 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 解除指定玩家的封禁。
     *
     * @param uuid 玩家 UUID
     * @throws SQLException 数据库操作失败时抛出
     */
    public void unban(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + prefix + "bans WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * 查询全部封禁记录（按创建时间降序）。
     *
     * @return 封禁记录列表
     * @throws SQLException 数据库操作失败时抛出
     */
    public List<BanRecord> getAllBans() throws SQLException {
        String sql = "SELECT * FROM " + prefix + "bans ORDER BY created_at DESC";
        List<BanRecord> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(BanRecord.from(rs));
        }
        return list;
    }

    /**
     * 查询指定玩家的封禁记录。
     *
     * @param uuid 玩家 UUID
     * @return 封禁记录，不存在时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 新增一条警告记录。
     *
     * @param uuid      玩家 UUID
     * @param playerName 玩家名称
     * @param reason    警告原因
     * @param operator  操作者名称
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 统计玩家自指定时间以来的警告次数。
     *
     * @param uuid  玩家 UUID
     * @param since 起始时间戳（毫秒）
     * @return 警告次数
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 查询玩家全部警告记录（按创建时间降序）。
     *
     * @param uuid 玩家 UUID
     * @return 警告记录列表
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 新增或更新玩家昵称。
     *
     * @param uuid     玩家 UUID
     * @param nickname 昵称
     * @throws SQLException 数据库操作失败时抛出
     */
    public void setNickname(UUID uuid, String nickname) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "nicknames (uuid, nickname) VALUES (?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, nickname);
            ps.executeUpdate();
        }
    }

    /**
     * 删除玩家昵称。
     *
     * @param uuid 玩家 UUID
     * @throws SQLException 数据库操作失败时抛出
     */
    public void removeNickname(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + prefix + "nicknames WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * 查询玩家昵称。
     *
     * @param uuid 玩家 UUID
     * @return 昵称，未设置时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 更新玩家数据（最后在线时间与 IP），首次加入时自动插入记录。
     *
     * @param uuid 玩家 UUID
     * @param ip   玩家 IP 地址，可为 null
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 关闭仓储，释放数据库连接池等资源。
     */
    @Override
    public void close() {
        shutdown();
    }

    // ─── Records ───

    /**
     * 封禁记录。
     *
     * @param uuid       玩家 UUID
     * @param playerName 玩家名称
     * @param reason     封禁原因
     * @param operator   操作者名称
     * @param createdAt  创建时间戳（毫秒）
     * @param expiresAt  过期时间戳（毫秒），{@code <=0} 表示永久封禁
     * @param ip         玩家 IP 地址，可为 null
     */
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

    /**
     * 警告记录。
     *
     * @param id         记录自增 ID
     * @param uuid       玩家 UUID
     * @param playerName 玩家名称
     * @param reason     警告原因
     * @param operator   操作者名称
     * @param createdAt  创建时间戳（毫秒）
     */
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
