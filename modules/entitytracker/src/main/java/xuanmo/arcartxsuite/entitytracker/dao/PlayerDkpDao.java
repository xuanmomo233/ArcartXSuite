package xuanmo.arcartxsuite.entitytracker.dao;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * 玩家DKP积分DAO
 */
public class PlayerDkpDao {
    private final DataSource dataSource;
    private final Logger logger;

    public PlayerDkpDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.logger = AxsLog.logger();
    }

    /**
     * 添加DKP积分
     */
    public void addPoints(String playerUuid, String playerName, int points, String reason) throws SQLException {
        String upsertSql = """
            INSERT INTO player_dkp (player_uuid, player_name, total_points, earned_points, spent_points, last_update, server_name)
            VALUES (?, ?, ?, ?, 0, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET 
                player_name = excluded.player_name,
                total_points = total_points + excluded.total_points,
                earned_points = earned_points + excluded.earned_points,
                last_update = excluded.last_update
            """;

        String transactionSql = """
            INSERT INTO dkp_transaction_records 
            (player_uuid, player_name, transaction_type, points, reason, transaction_time)
            VALUES (?, ?, 'earn', ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 更新DKP余额
                try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                    stmt.setString(1, playerUuid);
                    stmt.setString(2, playerName);
                    stmt.setInt(3, points);
                    stmt.setInt(4, points);
                    stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(6, ""); // server_name 后续填充
                    stmt.executeUpdate();
                }

                // 记录交易
                try (PreparedStatement stmt = conn.prepareStatement(transactionSql)) {
                    stmt.setString(1, playerUuid);
                    stmt.setString(2, playerName);
                    stmt.setInt(3, points);
                    stmt.setString(4, reason);
                    stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * 扣除DKP积分
     */
    public boolean deductPoints(String playerUuid, String playerName, int points, String reason) throws SQLException {
        String checkSql = "SELECT total_points FROM player_dkp WHERE player_uuid = ?";
        String updateSql = """
            UPDATE player_dkp 
            SET total_points = total_points - ?, spent_points = spent_points + ?, last_update = ?
            WHERE player_uuid = ? AND total_points >= ?
            """;
        String transactionSql = """
            INSERT INTO dkp_transaction_records 
            (player_uuid, player_name, transaction_type, points, reason, transaction_time)
            VALUES (?, ?, 'spend', ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 检查余额
                int currentPoints = 0;
                try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setString(1, playerUuid);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            currentPoints = rs.getInt("total_points");
                        }
                    }
                }

                if (currentPoints < points) {
                    conn.rollback();
                    return false;
                }

                // 扣除积分
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, points);
                    stmt.setInt(2, points);
                    stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(4, playerUuid);
                    stmt.setInt(5, points);
                    int affected = stmt.executeUpdate();
                    if (affected == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // 记录交易
                try (PreparedStatement stmt = conn.prepareStatement(transactionSql)) {
                    stmt.setString(1, playerUuid);
                    stmt.setString(2, playerName);
                    stmt.setInt(3, points);
                    stmt.setString(4, reason);
                    stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * 获取玩家DKP积分
     */
    public int getPoints(String playerUuid) throws SQLException {
        String sql = "SELECT total_points FROM player_dkp WHERE player_uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_points");
                }
            }
        }
        return 0;
    }
}
