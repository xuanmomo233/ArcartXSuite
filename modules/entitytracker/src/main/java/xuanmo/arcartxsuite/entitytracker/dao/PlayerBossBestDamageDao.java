package xuanmo.arcartxsuite.entitytracker.dao;

import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 玩家Boss最高伤害记录DAO
 */
public class PlayerBossBestDamageDao {
    private final DataSource dataSource;
    private final JavaPlugin plugin;

    public PlayerBossBestDamageDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.plugin = plugin;
    }

    /**
     * 插入或更新玩家Boss最高伤害记录
     */
    public void insertOrUpdate(PlayerBossBestDamage record) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO player_boss_best_damage 
            (player_uuid, player_name, boss_id, boss_display_name, best_damage, 
             damage_time, server_name, world_name, location_x, location_y, location_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
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
            
            stmt.executeUpdate();
        }
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
        entity.setLocationX(rs.getObject("location_x", Double.class));
        entity.setLocationY(rs.getObject("location_y", Double.class));
        entity.setLocationZ(rs.getObject("location_z", Double.class));
        
        return entity;
    }
}
