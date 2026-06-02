package xuanmo.arcartxsuite.entitytracker.dao;

import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardConfig;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 排行榜奖励配置DAO
 */
public class RankingRewardConfigDao {
    private final DataSource dataSource;
    private final JavaPlugin plugin;

    public RankingRewardConfigDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.plugin = plugin;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 插入新的奖励配置
     */
    public int insert(RankingRewardConfig config) throws SQLException {
        String sql = """
            INSERT INTO ranking_reward_configs 
            (reward_type, ranking_type, boss_id, rank_start, rank_end, 
             reward_name, reward_description, reward_items, reward_commands, 
             reward_money, reward_dkp, enabled, created_time, updated_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, config.getRewardType());
            stmt.setString(2, config.getRankingType());
            stmt.setString(3, config.getBossId());
            stmt.setInt(4, config.getRankStart());
            stmt.setInt(5, config.getRankEnd());
            stmt.setString(6, config.getRewardName());
            stmt.setString(7, config.getRewardDescription());
            stmt.setString(8, config.getRewardItems());
            stmt.setString(9, config.getRewardCommands());
            stmt.setInt(10, config.getRewardMoney() != null ? config.getRewardMoney() : 0);
            stmt.setInt(11, config.getRewardDkp() != null ? config.getRewardDkp() : 0);
            stmt.setBoolean(12, config.getEnabled() != null ? config.getEnabled() : true);
            stmt.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("插入奖励配置失败，没有行被影响");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("插入奖励配置失败，无法获取ID");
                }
            }
        }
    }

    /**
     * 更新奖励配置
     */
    public boolean update(RankingRewardConfig config) throws SQLException {
        String sql = """
            UPDATE ranking_reward_configs 
            SET reward_type = ?, ranking_type = ?, boss_id = ?, rank_start = ?, 
                rank_end = ?, reward_name = ?, reward_description = ?, 
                reward_items = ?, reward_commands = ?, reward_money = ?, 
                reward_dkp = ?, enabled = ?, updated_time = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, config.getRewardType());
            stmt.setString(2, config.getRankingType());
            stmt.setString(3, config.getBossId());
            stmt.setInt(4, config.getRankStart());
            stmt.setInt(5, config.getRankEnd());
            stmt.setString(6, config.getRewardName());
            stmt.setString(7, config.getRewardDescription());
            stmt.setString(8, config.getRewardItems());
            stmt.setString(9, config.getRewardCommands());
            stmt.setInt(10, config.getRewardMoney() != null ? config.getRewardMoney() : 0);
            stmt.setInt(11, config.getRewardDkp() != null ? config.getRewardDkp() : 0);
            stmt.setBoolean(12, config.getEnabled() != null ? config.getEnabled() : true);
            stmt.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(14, config.getId());
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * 根据ID查找奖励配置
     */
    public Optional<RankingRewardConfig> findById(Integer id) throws SQLException {
        String sql = """
            SELECT id, reward_type, ranking_type, boss_id, rank_start, rank_end, 
                   reward_name, reward_description, reward_items, reward_commands, 
                   reward_money, reward_dkp, enabled, created_time, updated_time
            FROM ranking_reward_configs 
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 根据奖励类型和排行类型查找配置
     */
    public List<RankingRewardConfig> findByRewardTypeAndRankingType(String rewardType, String rankingType) throws SQLException {
        String sql = """
            SELECT id, reward_type, ranking_type, boss_id, rank_start, rank_end, 
                   reward_name, reward_description, reward_items, reward_commands, 
                   reward_money, reward_dkp, enabled, created_time, updated_time
            FROM ranking_reward_configs 
            WHERE reward_type = ? AND ranking_type = ? AND enabled = true
            ORDER BY rank_start, rank_end
            """;

        List<RankingRewardConfig> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, rewardType);
            stmt.setString(2, rankingType);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 根据奖励类型查找所有配置
     */
    public List<RankingRewardConfig> findByRewardType(String rewardType) throws SQLException {
        String sql = """
            SELECT id, reward_type, ranking_type, boss_id, rank_start, rank_end, 
                   reward_name, reward_description, reward_items, reward_commands, 
                   reward_money, reward_dkp, enabled, created_time, updated_time
            FROM ranking_reward_configs 
            WHERE reward_type = ?
            ORDER BY ranking_type, rank_start, rank_end
            """;

        List<RankingRewardConfig> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, rewardType);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 获取所有奖励配置
     */
    public List<RankingRewardConfig> findAll() throws SQLException {
        String sql = """
            SELECT id, reward_type, ranking_type, boss_id, rank_start, rank_end, 
                   reward_name, reward_description, reward_items, reward_commands, 
                   reward_money, reward_dkp, enabled, created_time, updated_time
            FROM ranking_reward_configs 
            ORDER BY reward_type, ranking_type, rank_start, rank_end
            """;

        List<RankingRewardConfig> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 删除奖励配置
     */
    public boolean deleteById(Integer id) throws SQLException {
        String sql = "DELETE FROM ranking_reward_configs WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * 切换启用状态
     */
    public boolean toggleEnabled(Integer id) throws SQLException {
        String sql = """
            UPDATE ranking_reward_configs 
            SET enabled = NOT enabled, updated_time = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, id);
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * 检查排名范围是否冲突
     */
    public boolean hasRankingConflict(String rewardType, String rankingType, String bossId, 
                                     Integer rankStart, Integer rankEnd, Integer excludeId) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM ranking_reward_configs 
            WHERE reward_type = ? AND ranking_type = ? 
                  AND (boss_id = ? OR (boss_id IS NULL AND ? IS NULL))
                  AND enabled = true
                  AND NOT (rank_end < ? OR rank_start > ?)
            """;

        if (excludeId != null) {
            sql += " AND id != ?";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, rewardType);
            stmt.setString(2, rankingType);
            stmt.setString(3, bossId);
            stmt.setString(4, bossId);
            stmt.setInt(5, rankStart);
            stmt.setInt(6, rankEnd);
            
            if (excludeId != null) {
                stmt.setInt(7, excludeId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }

    /**
     * 将ResultSet映射为实体
     */
    private RankingRewardConfig mapResultSetToEntity(ResultSet rs) throws SQLException {
        RankingRewardConfig config = new RankingRewardConfig();
        config.setId(rs.getInt("id"));
        config.setRewardType(rs.getString("reward_type"));
        config.setRankingType(rs.getString("ranking_type"));
        config.setBossId(rs.getString("boss_id"));
        config.setRankStart(rs.getInt("rank_start"));
        config.setRankEnd(rs.getInt("rank_end"));
        config.setRewardName(rs.getString("reward_name"));
        config.setRewardDescription(rs.getString("reward_description"));
        config.setRewardItems(rs.getString("reward_items"));
        config.setRewardCommands(rs.getString("reward_commands"));
        config.setRewardMoney(rs.getInt("reward_money"));
        config.setRewardDkp(rs.getInt("reward_dkp"));
        config.setEnabled(rs.getBoolean("enabled"));
        
        Timestamp createdTime = rs.getTimestamp("created_time");
        if (createdTime != null) {
            config.setCreatedTime(createdTime.toLocalDateTime());
        }
        
        Timestamp updatedTime = rs.getTimestamp("updated_time");
        if (updatedTime != null) {
            config.setUpdatedTime(updatedTime.toLocalDateTime());
        }
        
        return config;
    }
}
