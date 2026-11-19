package xuanmo.arcartxsuite.entitytracker.dao;

import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 玩家Boss最高伤害记录DAO
 */
public class PlayerBossBestDamageDao {
    private static final String INSERT = """
        INSERT INTO player_boss_best_damage
        (player_uuid, player_name, boss_id, boss_display_name, best_damage,
         damage_time, server_name, world_name, location_x, location_y, location_z)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String SQLITE_UPSERT = INSERT + """
        ON CONFLICT (player_uuid, boss_id) DO UPDATE SET
            player_name=excluded.player_name, boss_display_name=excluded.boss_display_name,
            best_damage=excluded.best_damage, damage_time=excluded.damage_time,
            server_name=excluded.server_name, world_name=excluded.world_name,
            location_x=excluded.location_x, location_y=excluded.location_y, location_z=excluded.location_z
        WHERE excluded.best_damage > player_boss_best_damage.best_damage
        """;
    private static final String UPDATE = """
        UPDATE player_boss_best_damage SET player_name=?, boss_display_name=?, best_damage=?,
            damage_time=?, server_name=?, world_name=?, location_x=?, location_y=?, location_z=?
        WHERE player_uuid=? AND boss_id=? AND best_damage<?
        """;
    public static final int WRITE_BATCH_SIZE = 64;
    private final DataSource dataSource;

    public PlayerBossBestDamageDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    /**
     * 插入或更新；若已有记录且伤害不低于新值则跳过并返回 false。
     */
    public boolean insertOrUpdateIfBetter(PlayerBossBestDamage record) throws SQLException {
        if (record == null || record.getPlayerUuid() == null || record.getBossId() == null
            || record.getBestDamage() == null) {
            return false;
        }
        return !saveBetterBatch(record.getBossId(), List.of(record)).isEmpty();
    }

    /** Commits one bounded group and returns exactly the records that improved or were inserted. */
    public List<PlayerBossBestDamage> saveBetterBatch(String bossId, List<PlayerBossBestDamage> records) throws SQLException {
        if (records.isEmpty()) return List.of();
        if (records.size() > WRITE_BATCH_SIZE || records.stream().anyMatch(record ->
                record == null || record.getPlayerUuid() == null || record.getBestDamage() == null
                    || !bossId.equals(record.getBossId()))) {
            throw new IllegalArgumentException("Invalid best damage batch");
        }
        var ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparing(PlayerBossBestDamage::getPlayerUuid));
        try {
            return writeBatch(bossId, ordered);
        } catch (SQLException failure) {
            if (!"40001".equals(failure.getSQLState()) && failure.getErrorCode() != 1213) throw failure;
            return writeBatch(bossId, ordered);
        }
    }

    private List<PlayerBossBestDamage> writeBatch(String bossId, List<PlayerBossBestDamage> records) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            boolean sqlite = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                List<PlayerBossBestDamage> improved = sqlite ? writeSqlite(conn, records) : writeMysql(conn, bossId, records);
                conn.commit();
                return improved;
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    private static List<PlayerBossBestDamage> writeSqlite(Connection conn, List<PlayerBossBestDamage> records)
        throws SQLException {
        var improved = new ArrayList<PlayerBossBestDamage>();
        try (var statement = conn.prepareStatement(SQLITE_UPSERT)) {
            for (var record : records) {
                bindInsert(statement, record);
                if (statement.executeUpdate() == 1) improved.add(record);
            }
        }
        return improved;
    }

    private static List<PlayerBossBestDamage> writeMysql(Connection conn, String bossId, List<PlayerBossBestDamage> records)
        throws SQLException {
        var existing = new HashMap<String, Integer>();
        String keys = String.join(",", java.util.Collections.nCopies(records.size(), "?"));
        try (var select = conn.prepareStatement("SELECT player_uuid,best_damage FROM player_boss_best_damage"
            + " WHERE boss_id=? AND player_uuid IN (" + keys + ") ORDER BY player_uuid FOR UPDATE")) {
            select.setString(1, bossId);
            for (int i = 0; i < records.size(); i++) select.setString(i + 2, records.get(i).getPlayerUuid());
            try (var rows = select.executeQuery()) {
                while (rows.next()) existing.put(rows.getString(1).toLowerCase(Locale.ROOT), rows.getInt(2));
            }
        }
        var improved = new ArrayList<PlayerBossBestDamage>();
        try (var insert = conn.prepareStatement(INSERT); var update = conn.prepareStatement(UPDATE)) {
            for (var record : records) {
                String key = record.getPlayerUuid().toLowerCase(Locale.ROOT);
                Integer previous = existing.get(key);
                if (previous != null && record.getBestDamage() <= previous) continue;
                boolean changed = previous == null ? insertOrUpdateConcurrent(insert, update, record) : update(update, record);
                if (changed) {
                    improved.add(record);
                    existing.put(key, record.getBestDamage());
                }
            }
        }
        return improved;
    }

    private static boolean insertOrUpdateConcurrent(PreparedStatement insert, PreparedStatement update,
                                                      PlayerBossBestDamage record) throws SQLException {
        try {
            bindInsert(insert, record);
            return insert.executeUpdate() == 1;
        } catch (SQLException failure) {
            // A missing record can be inserted by another node under READ COMMITTED.
            if (failure.getErrorCode() != 1062) throw failure;
            return update(update, record);
        }
    }

    private static void bindInsert(PreparedStatement stmt, PlayerBossBestDamage record) throws SQLException {
        stmt.setString(1, record.getPlayerUuid());
        stmt.setString(2, record.getPlayerName());
        stmt.setString(3, record.getBossId());
        stmt.setString(4, record.getBossDisplayName());
        stmt.setInt(5, record.getBestDamage());
        stmt.setTimestamp(6, Timestamp.valueOf(record.getDamageTime()));
        stmt.setString(7, record.getServerName());
        stmt.setString(8, record.getWorldName());
        stmt.setObject(9, record.getLocationX());
        stmt.setObject(10, record.getLocationY());
        stmt.setObject(11, record.getLocationZ());
    }

    private static boolean update(PreparedStatement stmt, PlayerBossBestDamage record) throws SQLException {
        stmt.setString(1, record.getPlayerName());
        stmt.setString(2, record.getBossDisplayName());
        stmt.setInt(3, record.getBestDamage());
        stmt.setTimestamp(4, Timestamp.valueOf(record.getDamageTime()));
        stmt.setString(5, record.getServerName());
        stmt.setString(6, record.getWorldName());
        stmt.setObject(7, record.getLocationX());
        stmt.setObject(8, record.getLocationY());
        stmt.setObject(9, record.getLocationZ());
        stmt.setString(10, record.getPlayerUuid());
        stmt.setString(11, record.getBossId());
        stmt.setInt(12, record.getBestDamage());
        return stmt.executeUpdate() == 1;
    }

    /**
     * 获取玩家对指定Boss的最高伤害记录
     */
    public Optional<PlayerBossBestDamage> findByPlayerAndBoss(String playerUuid, String bossId) throws SQLException {
        String sql = """
            SELECT id, player_uuid, player_name, boss_id, boss_display_name, 
                   best_damage, damage_time, server_name, world_name, 
                   location_x, location_y, location_z
            FROM player_boss_best_damage 
            WHERE player_uuid = ? AND boss_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            stmt.setString(2, bossId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定Boss的最高伤害排行
     */
    public List<PlayerBossBestDamage> findTopDamageByBoss(String bossId, int limit) throws SQLException {
        String sql = """
            SELECT id, player_uuid, player_name, boss_id, boss_display_name, 
                   best_damage, damage_time, server_name, world_name, 
                   location_x, location_y, location_z
            FROM player_boss_best_damage 
            WHERE boss_id = ?
            ORDER BY best_damage DESC, damage_time ASC
            LIMIT ?
            """;

        List<PlayerBossBestDamage> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, bossId);
            stmt.setInt(2, limit);
            
            int rank = 1;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerBossBestDamage entity = mapResultSetToEntity(rs);
                    entity.setRank(rank++);
                    results.add(entity);
                }
            }
        }
        return results;
    }

    /**
     * 获取跨服最高伤害排行
     */
    public List<PlayerBossBestDamage> findCrossServerTopDamage(int limit) throws SQLException {
        String sql = """
            SELECT id, player_uuid, player_name, boss_id, boss_display_name, 
                   best_damage, damage_time, server_name, world_name, 
                   location_x, location_y, location_z
            FROM player_boss_best_damage 
            ORDER BY best_damage DESC, damage_time ASC
            LIMIT ?
            """;

        List<PlayerBossBestDamage> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            
            int rank = 1;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerBossBestDamage entity = mapResultSetToEntity(rs);
                    entity.setRank(rank++);
                    results.add(entity);
                }
            }
        }
        return results;
    }

    /**
     * 获取玩家在指定时间范围内的最高伤害记录
     */
    public List<PlayerBossBestDamage> findByPlayerAndTimeRange(String playerUuid, 
                                                              LocalDateTime startTime, 
                                                              LocalDateTime endTime) throws SQLException {
        String sql = """
            SELECT id, player_uuid, player_name, boss_id, boss_display_name, 
                   best_damage, damage_time, server_name, world_name, 
                   location_x, location_y, location_z
            FROM player_boss_best_damage 
            WHERE player_uuid = ? AND damage_time BETWEEN ? AND ?
            ORDER BY best_damage DESC
            """;

        List<PlayerBossBestDamage> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            stmt.setTimestamp(2, Timestamp.valueOf(startTime));
            stmt.setTimestamp(3, Timestamp.valueOf(endTime));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 获取指定服务器的最高伤害排行
     */
    public List<PlayerBossBestDamage> findServerTopDamage(String serverName, int limit) throws SQLException {
        String sql = """
            SELECT id, player_uuid, player_name, boss_id, boss_display_name, 
                   best_damage, damage_time, server_name, world_name, 
                   location_x, location_y, location_z
            FROM player_boss_best_damage 
            WHERE server_name = ?
            ORDER BY best_damage DESC, damage_time ASC
            LIMIT ?
            """;

        List<PlayerBossBestDamage> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, serverName);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 清理指定天数之前的记录
     */
    public int deleteOldRecords(int daysToKeep) throws SQLException {
        String sql = """
            DELETE FROM player_boss_best_damage 
            WHERE damage_time < datetime('now', '-' || ? || ' days')
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, daysToKeep);
            return stmt.executeUpdate();
        }
    }

    /**
     * 统计玩家参与Boss数量
     */
    public int countDistinctBossesByPlayer(String playerUuid) throws SQLException {
        String sql = """
            SELECT COUNT(DISTINCT boss_id)
            FROM player_boss_best_damage 
            WHERE player_uuid = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 将ResultSet映射为实体
     */
    private PlayerBossBestDamage mapResultSetToEntity(ResultSet rs) throws SQLException {
        PlayerBossBestDamage entity = new PlayerBossBestDamage();
        entity.setId(rs.getInt("id"));
        entity.setPlayerUuid(rs.getString("player_uuid"));
        entity.setPlayerName(rs.getString("player_name"));
        entity.setBossId(rs.getString("boss_id"));
        entity.setBossDisplayName(rs.getString("boss_display_name"));
        entity.setBestDamage(rs.getInt("best_damage"));
        
        Timestamp damageTime = rs.getTimestamp("damage_time");
        if (damageTime != null) {
            entity.setDamageTime(damageTime.toLocalDateTime());
        }
        
        entity.setServerName(rs.getString("server_name"));
        entity.setWorldName(rs.getString("world_name"));
        entity.setLocationX(nullableCoordinate(rs, "location_x"));
        entity.setLocationY(nullableCoordinate(rs, "location_y"));
        entity.setLocationZ(nullableCoordinate(rs, "location_z"));
        
        return entity;
    }

    private static Double nullableCoordinate(ResultSet rows, String column) throws SQLException {
        double coordinate = rows.getDouble(column);
        return rows.wasNull() ? null : coordinate;
    }
}
