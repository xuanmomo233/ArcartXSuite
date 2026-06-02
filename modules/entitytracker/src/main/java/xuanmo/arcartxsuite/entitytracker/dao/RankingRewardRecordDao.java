package xuanmo.arcartxsuite.entitytracker.dao;

import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 排行榜奖励发放记录DAO
 */
public class RankingRewardRecordDao {
    private final DataSource dataSource;
    private final JavaPlugin plugin;

    public RankingRewardRecordDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.plugin = plugin;
    }

    /**
     * 插入奖励发放记录
     */
    public int insert(RankingRewardRecord record) throws SQLException {
        String sql = """
            INSERT INTO ranking_reward_records 
            (reward_config_id, reward_type, ranking_type, boss_id, 
             period_start, period_end, player_uuid, player_name, 
             rank, score, reward_items, reward_commands, 
             reward_money, reward_dkp, status, issued_time, 
             failure_reason, retry_count, server_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, record.getRewardConfigId());
            stmt.setString(2, record.getRewardType());
            stmt.setString(3, record.getRankingType());
            stmt.setString(4, record.getBossId());
            stmt.setTimestamp(5, Timestamp.valueOf(record.getPeriodStart()));
            stmt.setTimestamp(6, Timestamp.valueOf(record.getPeriodEnd()));
            stmt.setString(7, record.getPlayerUuid());
            stmt.setString(8, record.getPlayerName());
            stmt.setInt(9, record.getRank());
            stmt.setInt(10, record.getScore());
            stmt.setString(11, record.getRewardItems());
            stmt.setString(12, record.getRewardCommands());
            stmt.setInt(13, record.getRewardMoney() != null ? record.getRewardMoney() : 0);
            stmt.setInt(14, record.getRewardDkp() != null ? record.getRewardDkp() : 0);
            stmt.setString(15, record.getStatus());
            stmt.setTimestamp(16, record.getIssuedTime() != null ? Timestamp.valueOf(record.getIssuedTime()) : null);
            stmt.setString(17, record.getFailureReason());
            stmt.setInt(18, record.getRetryCount() != null ? record.getRetryCount() : 0);
            stmt.setString(19, record.getServerName());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("插入奖励记录失败");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("插入奖励记录失败，无法获取ID");
                }
            }
        }
    }

    /**
     * 更新奖励记录
     */
    public boolean update(RankingRewardRecord record) throws SQLException {
        String sql = """
            UPDATE ranking_reward_records 
            SET status = ?, issued_time = ?, failure_reason = ?, retry_count = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, record.getStatus());
            stmt.setTimestamp(2, record.getIssuedTime() != null ? Timestamp.valueOf(record.getIssuedTime()) : null);
            stmt.setString(3, record.getFailureReason());
            stmt.setInt(4, record.getRetryCount() != null ? record.getRetryCount() : 0);
            stmt.setInt(5, record.getId());
            
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * 根据ID查找记录
     */
    public Optional<RankingRewardRecord> findById(Integer id) throws SQLException {
        String sql = """
            SELECT id, reward_config_id, reward_type, ranking_type, boss_id, 
                   period_start, period_end, player_uuid, player_name, 
                   rank, score, reward_items, reward_commands, 
                   reward_money, reward_dkp, status, issued_time, 
                   failure_reason, server_name
            FROM ranking_reward_records 
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
     * 根据玩家UUID查找记录
     */
    public List<RankingRewardRecord> findByPlayerUuid(String playerUuid) throws SQLException {
        String sql = """
            SELECT id, reward_config_id, reward_type, ranking_type, boss_id, 
                   period_start, period_end, player_uuid, player_name, 
                   rank, score, reward_items, reward_commands, 
                   reward_money, reward_dkp, status, issued_time, 
                   failure_reason, server_name
            FROM ranking_reward_records 
            WHERE player_uuid = ?
            ORDER BY issued_time DESC
            """;

        List<RankingRewardRecord> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 根据筛选条件查找记录
     */
    public List<RankingRewardRecord> findByFilters(String rewardType, String status, 
                                                   LocalDateTime startTime) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, reward_config_id, reward_type, ranking_type, boss_id, 
                   period_start, period_end, player_uuid, player_name, 
                   rank, score, reward_items, reward_commands, 
                   reward_money, reward_dkp, status, issued_time, 
                   failure_reason, server_name
            FROM ranking_reward_records 
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (rewardType != null && !rewardType.isEmpty()) {
            sql.append(" AND reward_type = ?");
            params.add(rewardType);
        }

        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        if (startTime != null) {
            sql.append(" AND issued_time >= ?");
            params.add(Timestamp.valueOf(startTime));
        }

        sql.append(" ORDER BY issued_time DESC LIMIT 100");

        List<RankingRewardRecord> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else if (param instanceof Timestamp) {
                    stmt.setTimestamp(i + 1, (Timestamp) param);
                }
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        }
        return results;
    }

    /**
     * 统计指定状态的记录数
     */
    public int countByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ranking_reward_records WHERE status = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 获取失败且未超过重试次数的记录
     */
    public List<RankingRewardRecord> findFailedRecordsForRetry(int maxRetryCount) throws SQLException {
        String sql = """
            SELECT id, reward_config_id, reward_type, ranking_type, boss_id, 
                   period_start, period_end, player_uuid, player_name, 
                   rank, score, reward_items, reward_commands, 
                   reward_money, reward_dkp, status, issued_time, 
                   failure_reason, server_name
            FROM ranking_reward_records 
            WHERE status = 'failed'
            ORDER BY issued_time DESC
            LIMIT 50
            """;

        List<RankingRewardRecord> results = new ArrayList<>();
        
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
     * 将ResultSet映射为实体
     */
    private RankingRewardRecord mapResultSetToEntity(ResultSet rs) throws SQLException {
        RankingRewardRecord record = new RankingRewardRecord();
        record.setId(rs.getInt("id"));
        record.setRewardConfigId(rs.getInt("reward_config_id"));
        record.setRewardType(rs.getString("reward_type"));
        record.setRankingType(rs.getString("ranking_type"));
        record.setBossId(rs.getString("boss_id"));
        
        Timestamp periodStart = rs.getTimestamp("period_start");
        if (periodStart != null) {
            record.setPeriodStart(periodStart.toLocalDateTime());
        }
        
        Timestamp periodEnd = rs.getTimestamp("period_end");
        if (periodEnd != null) {
            record.setPeriodEnd(periodEnd.toLocalDateTime());
        }
        
        record.setPlayerUuid(rs.getString("player_uuid"));
        record.setPlayerName(rs.getString("player_name"));
        record.setRank(rs.getInt("rank"));
        record.setScore(rs.getInt("score"));
        record.setRewardItems(rs.getString("reward_items"));
        record.setRewardCommands(rs.getString("reward_commands"));
        record.setRewardMoney(rs.getInt("reward_money"));
        record.setRewardDkp(rs.getInt("reward_dkp"));
        record.setStatus(rs.getString("status"));
        
        Timestamp issuedTime = rs.getTimestamp("issued_time");
        if (issuedTime != null) {
            record.setIssuedTime(issuedTime.toLocalDateTime());
        }
        
        record.setFailureReason(rs.getString("failure_reason"));
        record.setRetryCount(rs.getInt("retry_count"));
        record.setServerName(rs.getString("server_name"));
        
        return record;
    }
}
