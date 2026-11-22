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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * Essentials 模块数据仓储。
 * <p>
 * 继承 {@link AbstractModuleRepository}，支持 SQLite 与 MySQL 双方言，
 * 负责家、传送点收藏、出生点、封禁、警告、昵称与玩家数据的持久化读写。
 * 实现了玩家数据清除（{@code playerDataTables}）与全表枚举（{@code allTables}）
 * 以支持宿主的统一数据管理与数据库迁移能力。
 */
public final class EssentialsRepository extends AbstractModuleRepository implements AutoCloseable {

    public EssentialsRepository(DataSource dataSource, StorageDescriptor descriptor,
                                File dataFolder, Logger logger) {
        super("AXS-Essentials", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            initTables(stmt);
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tablePrefix() + "homes", tablePrefix() + "bans", tablePrefix() + "warnings", tablePrefix() + "nicknames",
            tablePrefix() + "player_data", tablePrefix() + "warp_favorites", tablePrefix() + "tpa_preferences",
            tablePrefix() + "known_players", tablePrefix() + "moderation_history");
    }

    @Override
    protected List<String> allTables() {
        return List.of(
            tablePrefix() + "homes",
            tablePrefix() + "bans",
            tablePrefix() + "warnings",
            tablePrefix() + "nicknames",
            tablePrefix() + "player_data",
            tablePrefix() + "warp_favorites",
            tablePrefix() + "tpa_preferences",
            tablePrefix() + "known_players",
            tablePrefix() + "moderation_history",
            tablePrefix() + "spawn"
        );
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    private void initTables(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            warp_favorites (
                uuid VARCHAR(36) NOT NULL, warp_name VARCHAR(64) NOT NULL,
                PRIMARY KEY (uuid, warp_name)
            )
            """);
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            tpa_preferences (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                receiving INTEGER NOT NULL
            )
            """);
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            known_players (
                uuid VARCHAR(36) NOT NULL, lookup_name VARCHAR(32) NOT NULL,
                player_name VARCHAR(32) NOT NULL, seen_at BIGINT NOT NULL,
                PRIMARY KEY (lookup_name, uuid)
            )
            """);
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            moderation_history (
                id INTEGER PRIMARY KEY""" + " " + (isMysql() ? "AUTO_INCREMENT" : "AUTOINCREMENT") + """
                , uuid VARCHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL, action VARCHAR(16) NOT NULL,
                reason TEXT, operator VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL
            """ + (isMysql() ? ", INDEX (uuid, id)" : "") + """
            )
            """);
        if (!isMysql()) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS " + tablePrefix() + "moderation_history_uuid ON "
                + tablePrefix() + "moderation_history (uuid,id)");
        }
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            homes (
                uuid VARCHAR(36) NOT NULL, name VARCHAR(64) NOT NULL,
                world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL, pitch FLOAT NOT NULL,
                PRIMARY KEY (uuid, name)
            )
            """);

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            spawn (
                id INT NOT NULL PRIMARY KEY,
                world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL, pitch FLOAT NOT NULL
            )
            """);

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            bans (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                player_name VARCHAR(32) NOT NULL,
                reason TEXT, operator VARCHAR(32) NOT NULL,
                created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
                ip VARCHAR(45)
            )
            """);

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            warnings (
                id INTEGER PRIMARY KEY""" + " " + (isMysql() ? "AUTO_INCREMENT" : "AUTOINCREMENT") + """
                , uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(32) NOT NULL,
                reason TEXT, operator VARCHAR(32) NOT NULL,
                created_at BIGINT NOT NULL
            )
            """);

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            nicknames (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                nickname VARCHAR(128) NOT NULL
            )
            """);

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix() + """
            player_data (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                last_seen BIGINT NOT NULL DEFAULT 0,
                last_ip VARCHAR(45),
                first_join BIGINT NOT NULL DEFAULT 0
            )
            """);
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
    public HomeSave setHome(UUID uuid, String name, Position loc, int maximum) throws SQLException {
        for (int attempt = 0; ; attempt++) {
            try { return saveHome(uuid, name, loc, maximum); }
            catch (SQLException failure) {
                // InnoDB can abort one of two empty-range inserts. Retry only a confirmed transaction rollback.
                if (!isMysql() || !"40001".equals(failure.getSQLState()) || attempt >= 2) throw failure;
            }
        }
    }

    private HomeSave saveHome(UUID uuid, String name, Position loc, int maximum) throws SQLException {
        String normalized = name.toLowerCase(Locale.ROOT);
        String sql = "REPLACE INTO " + tablePrefix() + "homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            int isolation = conn.getTransactionIsolation();
            // Lock this owner's indexed range, including an empty range, across shared-storage nodes.
            if (isMysql()) conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try {
                List<String> names = new ArrayList<>();
                String existingName = null;
                try (var query = conn.prepareStatement("SELECT name, name=? AS matched FROM " + tablePrefix() + "homes WHERE uuid=?"
                    + (isMysql() ? " FOR UPDATE" : ""))) {
                    query.setString(1, normalized);
                    query.setString(2, uuid.toString());
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            names.add(rows.getString(1));
                            if (rows.getBoolean(2)) existingName = rows.getString(1);
                        }
                    }
                }
                if (existingName == null && names.size() >= maximum) {
                    conn.commit();
                    return new HomeSave(false, List.copyOf(names));
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, normalized);
                    ps.setString(3, loc.world());
                    ps.setDouble(4, loc.x());
                    ps.setDouble(5, loc.y());
                    ps.setDouble(6, loc.z());
                    ps.setFloat(7, loc.yaw());
                    ps.setFloat(8, loc.pitch());
                    ps.executeUpdate();
                }
                conn.commit();
                if (existingName != null) names.remove(existingName);
                names.add(normalized);
                names.sort(String::compareTo);
                return new HomeSave(true, List.copyOf(names));
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally {
                conn.setAutoCommit(autoCommit);
                if (isMysql()) conn.setTransactionIsolation(isolation);
            }
        }
    }

    public record HomeSave(boolean saved, List<String> names) {}

    /** Immutable database coordinates; world lookup and Bukkit location creation belong to the caller's main thread. */
    public record Position(String world, double x, double y, double z, float yaw, float pitch) {}

    /**
     * 删除玩家的指定家。
     *
     * @param uuid 玩家 UUID
     * @param name 家名称
     * @throws SQLException 数据库操作失败时抛出
     */
    public boolean deleteHome(UUID uuid, String name) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix() + "homes WHERE uuid=? AND name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
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
    public Position getHome(UUID uuid, String name) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + tablePrefix() + "homes WHERE uuid=? AND name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return toPosition(rs);
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
    public List<String> listHomeNames(UUID uuid) throws SQLException {
        List<String> homes = new ArrayList<>();
        String sql = "SELECT name FROM " + tablePrefix() + "homes WHERE uuid=? ORDER BY name";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    homes.add(rs.getString("name"));
                }
            }
        }
        return List.copyOf(homes);
    }

    /**
     * 统计玩家已设置的家数量。
     *
     * @param uuid 玩家 UUID
     * @return 家数量
     * @throws SQLException 数据库操作失败时抛出
     */
    public int homeCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix() + "homes WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<HomeSummary> listHomes(UUID uuid, int limit, int offset) throws SQLException {
        String sql = "SELECT name, world, x, y, z FROM " + tablePrefix() + "homes"
            + " WHERE uuid=? ORDER BY name LIMIT ? OFFSET ?";
        List<HomeSummary> homes = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    homes.add(new HomeSummary(rs.getString("name"), rs.getString("world"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
                }
            }
        }
        return homes;
    }

    public record HomeSummary(String name, String world, int x, int y, int z) {}

    // ─── Warp favorites ───

    public java.util.Set<String> warpFavorites(UUID id) throws SQLException {
        java.util.Set<String> names = new java.util.HashSet<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
            "SELECT warp_name FROM " + tablePrefix() + "warp_favorites WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    public void setWarpFavorite(UUID id, String name, boolean favorite) throws SQLException {
        String sql = favorite
            ? "REPLACE INTO " + tablePrefix() + "warp_favorites (uuid,warp_name) VALUES (?,?)"
            : "DELETE FROM " + tablePrefix() + "warp_favorites WHERE uuid=? AND warp_name=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public boolean receivesTpa(UUID id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
            "SELECT receiving FROM " + tablePrefix() + "tpa_preferences WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() || rs.getInt(1) != 0;
            }
        }
    }

    public void setReceivesTpa(UUID id, boolean receiving) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
            "REPLACE INTO " + tablePrefix() + "tpa_preferences (uuid,receiving) VALUES (?,?)")) {
            ps.setString(1, id.toString());
            ps.setInt(2, receiving ? 1 : 0);
            ps.executeUpdate();
        }
    }

    // ─── Spawn ───

    /**
     * 设置服务器出生点位置。
     *
     * @param loc 出生点位置
     * @throws SQLException 数据库操作失败时抛出
     */
    public void setSpawn(Position loc) throws SQLException {
        String sql = "REPLACE INTO " + tablePrefix() + "spawn (id, world, x, y, z, yaw, pitch) VALUES (1,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.world());
            ps.setDouble(2, loc.x());
            ps.setDouble(3, loc.y());
            ps.setDouble(4, loc.z());
            ps.setFloat(5, loc.yaw());
            ps.setFloat(6, loc.pitch());
            ps.executeUpdate();
        }
    }

    /**
     * 查询服务器出生点位置。
     *
     * @return 出生点位置，未设置时返回 null
     * @throws SQLException 数据库操作失败时抛出
     */
    public Position getSpawn() throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + tablePrefix() + "spawn WHERE id=1";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return toPosition(rs);
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
    public BanRecord ban(UUID uuid, String playerName, String reason, String operator, long expiresAt, String ip) throws SQLException {
        long now = System.currentTimeMillis();
        return transaction(conn -> ban(conn, uuid, playerName, reason, operator, now, expiresAt, ip));
    }

    private BanRecord ban(Connection conn, UUID uuid, String playerName, String reason, String operator,
                           long now, long expiresAt, String ip) throws SQLException {
        String sql = "REPLACE INTO " + tablePrefix() + "bans (uuid, player_name, reason, operator, created_at, expires_at, ip) VALUES (?,?,?,?,?,?,?)";
            String expire = "INSERT INTO " + tablePrefix() + "moderation_history"
                + " (uuid,player_name,action,reason,operator,created_at,expires_at)"
                + " SELECT uuid,player_name,'EXPIRE',reason,?,?,expires_at FROM " + tablePrefix() + "bans"
                + " WHERE uuid=? AND expires_at>0 AND expires_at<=?";
            try (PreparedStatement ps = conn.prepareStatement(expire)) {
                ps.setString(1, "系统");
                ps.setLong(2, now);
                ps.setString(3, uuid.toString());
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, reason);
                ps.setString(4, operator);
                ps.setLong(5, now);
                ps.setLong(6, expiresAt);
                ps.setString(7, ip);
                ps.executeUpdate();
            }
            String action = "系统".equals(operator) ? "AUTO_BAN" : expiresAt > 0 ? "TEMPBAN" : "BAN";
            insertHistory(conn, uuid, playerName, action, reason, operator, now, expiresAt);
            return new BanRecord(uuid, playerName, reason, operator, now, expiresAt, ip);
    }

    /**
     * 解除指定玩家的封禁。
     *
     * @param uuid 玩家 UUID
     * @throws SQLException 数据库操作失败时抛出
     */
    public boolean unban(UUID uuid, String operator) throws SQLException {
        BanRecord record = getBan(uuid);
        if (record == null) return false;
        return removeBan(record, record.isExpired() ? "EXPIRE" : "UNBAN", operator);
    }

    public void expireBans(int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix() + "bans WHERE expires_at>0 AND expires_at<=?"
            + " ORDER BY expires_at LIMIT ?";
        List<BanRecord> expired = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) expired.add(BanRecord.from(rs));
            }
        }
        for (BanRecord record : expired) removeBan(record, "EXPIRE", "系统");
    }

    private boolean removeBan(BanRecord record, String action, String operator) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix() + "bans WHERE uuid=? AND created_at=? AND expires_at=?";
        return transaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.uuid().toString());
                ps.setLong(2, record.createdAt());
                ps.setLong(3, record.expiresAt());
                if (ps.executeUpdate() == 0) return false;
            }
            insertHistory(conn, record.uuid(), record.playerName(), action, record.reason(),
                operator, System.currentTimeMillis(), record.expiresAt());
            return true;
        });
    }

    public void rememberPlayer(UUID id, String name) throws SQLException {
        String sql = "REPLACE INTO " + tablePrefix() + "known_players (uuid,lookup_name,player_name,seen_at) VALUES (?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, name.toLowerCase(java.util.Locale.ROOT));
            ps.setString(3, name);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<UUID> findKnownPlayers(String name) throws SQLException {
        String sql = "SELECT uuid FROM " + tablePrefix() + "known_players WHERE lookup_name=? UNION"
            + " SELECT uuid FROM " + tablePrefix() + "bans WHERE LOWER(player_name)=? UNION"
            + " SELECT uuid FROM " + tablePrefix() + "moderation_history WHERE LOWER(player_name)=? LIMIT 2";
        String lookup = name.toLowerCase(java.util.Locale.ROOT);
        List<UUID> ids = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lookup);
            ps.setString(2, lookup);
            ps.setString(3, lookup);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
            }
        }
        return ids;
    }

    public String knownPlayerName(UUID id) throws SQLException {
        String sql = "SELECT player_name FROM " + tablePrefix() + "known_players WHERE uuid=? ORDER BY seen_at DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        BanRecord ban = getBan(id);
        if (ban != null) return ban.playerName();
        var history = history(id, 1, 0);
        return history.isEmpty() ? null : history.get(0).playerName();
    }

    public List<ModerationEntry> history(UUID id, int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix() + "moderation_history WHERE uuid=? ORDER BY id DESC LIMIT ? OFFSET ?";
        List<ModerationEntry> entries = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new ModerationEntry(rs.getString("player_name"), rs.getString("action"), rs.getString("reason"),
                        rs.getString("operator"), rs.getLong("created_at"), rs.getLong("expires_at")));
                }
            }
        }
        return entries;
    }

    private void insertHistory(Connection conn, UUID id, String playerName, String action, String reason,
                               String operator, long now, long expiry) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix() + "moderation_history"
            + " (uuid,player_name,action,reason,operator,created_at,expires_at) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, playerName);
            ps.setString(3, action);
            ps.setString(4, reason);
            ps.setString(5, operator);
            ps.setLong(6, now);
            ps.setLong(7, expiry);
            ps.executeUpdate();
        }
    }

    private <T> T transaction(SqlOperation<T> operation) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operation.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    public record ModerationEntry(String playerName, String action, String reason, String operator,
                                  long createdAt, long expiresAt) {}

    /**
     * 查询全部封禁记录（按创建时间降序）。
     *
     * @return 封禁记录列表
     * @throws SQLException 数据库操作失败时抛出
     */
    public List<BanRecord> listActiveBans(int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix() + "bans WHERE expires_at<=0 OR expires_at>?"
            + " ORDER BY created_at DESC, uuid LIMIT ? OFFSET ?";
        List<BanRecord> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(BanRecord.from(rs));
            }
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
        String sql = "SELECT * FROM " + tablePrefix() + "bans WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return BanRecord.from(rs);
            }
        }
        return null;
    }

    // ─── Warnings ───

    public record WarningResult(int count, BanRecord ban) {}

    /** One committed warning/history/optional automatic-ban unit. */
    public WarningResult warn(UUID uuid, String playerName, String reason, String operator, int maximum, long since) throws SQLException {
        return transaction(conn -> {
            long now = System.currentTimeMillis();
            addWarning(conn, uuid, playerName, reason, operator, now);
            int count = 0;
            if (maximum > 0) {
                try (var query = conn.prepareStatement("SELECT COUNT(*) FROM " + tablePrefix() + "warnings WHERE uuid=? AND created_at>=?")) {
                    query.setString(1, uuid.toString()); query.setLong(2, since);
                    try (var rows = query.executeQuery()) { if (rows.next()) count = rows.getInt(1); }
                }
            }
            BanRecord automatic = maximum > 0 && count >= maximum
                ? ban(conn, uuid, playerName, "警告次数达到上限 (" + count + ")", "系统", now, -1, null) : null;
            return new WarningResult(count, automatic);
        });
    }

    private void addWarning(Connection conn, UUID uuid, String playerName, String reason, String operator, long now) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix() + "warnings (uuid, player_name, reason, operator, created_at) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, reason);
                ps.setString(4, operator);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            insertHistory(conn, uuid, playerName, "WARN", reason, operator, now, -1);
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
        String sql = "REPLACE INTO " + tablePrefix() + "nicknames (uuid, nickname) VALUES (?,?)";
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
        String sql = "DELETE FROM " + tablePrefix() + "nicknames WHERE uuid=?";
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
        String sql = "SELECT nickname FROM " + tablePrefix() + "nicknames WHERE uuid=?";
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
            String insert = isMysql()
                ? "INSERT IGNORE INTO " + tablePrefix() + "player_data (uuid, last_seen, last_ip, first_join) VALUES (?,?,?,?)"
                : "INSERT OR IGNORE INTO " + tablePrefix() + "player_data (uuid, last_seen, last_ip, first_join) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, now);
                ps.setString(3, ip);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            // 更新最后在线时间和 IP
            String update = "UPDATE " + tablePrefix() + "player_data SET last_seen=?, last_ip=? WHERE uuid=?";
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
    private static Position toPosition(ResultSet rs) throws SQLException {
        return new Position(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
            rs.getFloat("yaw"), rs.getFloat("pitch"));
    }

}
