package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

public class DropAllocationRecordDao {

    private final DataSource dataSource;

    public DropAllocationRecordDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    public long insert(
        long bossKillId,
        String itemId,
        String itemName,
        int amount,
        String allocationType,
        String winnerUuid,
        String winnerName,
        int pointsCost,
        Integer rollValue,
        Integer priorityScore,
        String serverName
    ) throws SQLException {
        String sql = """
            INSERT INTO drop_allocation_records
            (boss_kill_id, item_id, item_name, item_amount, allocation_type,
             winner_uuid, winner_name, points_cost, roll_value, priority_score, server_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, bossKillId);
            stmt.setString(2, itemId);
            stmt.setString(3, itemName);
            stmt.setInt(4, amount);
            stmt.setString(5, allocationType);
            stmt.setString(6, winnerUuid);
            stmt.setString(7, winnerName);
            stmt.setInt(8, pointsCost);
            if (rollValue == null) {
                stmt.setNull(9, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(9, rollValue);
            }
            if (priorityScore == null) {
                stmt.setNull(10, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(10, priorityScore);
            }
            stmt.setString(11, serverName);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public void insertRollParticipation(
        long allocationRecordId,
        String playerUuid,
        String playerName,
        String rollType,
        Integer rollValue,
        String serverName
    ) throws SQLException {
        String sql = """
            INSERT INTO roll_participation_records
            (allocation_record_id, player_uuid, player_name, roll_type, roll_value, server_name)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, allocationRecordId);
            stmt.setString(2, playerUuid);
            stmt.setString(3, playerName);
            stmt.setString(4, rollType);
            if (rollValue == null) {
                stmt.setNull(5, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(5, rollValue);
            }
            stmt.setString(6, serverName);
            stmt.executeUpdate();
        }
    }
}
