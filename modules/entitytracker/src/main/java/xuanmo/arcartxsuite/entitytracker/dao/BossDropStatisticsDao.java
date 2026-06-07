package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

public class BossDropStatisticsDao {

    private final DataSource dataSource;

    public BossDropStatisticsDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    public void recordDrop(String bossId, String itemId, String itemName, String serverName) throws SQLException {
        String sql = """
            INSERT INTO boss_drop_statistics
            (boss_id, item_id, item_name, drop_count, kill_count, drop_rate, last_drop_time, server_name)
            VALUES (?, ?, ?, 1, 0, 0, ?, ?)
            ON CONFLICT(boss_id, item_id, server_name) DO UPDATE SET
                item_name = excluded.item_name,
                drop_count = drop_count + 1,
                last_drop_time = excluded.last_drop_time
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bossId);
            stmt.setString(2, itemId);
            stmt.setString(3, itemName);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(5, serverName == null ? "" : serverName);
            stmt.executeUpdate();
        }
    }

    public void incrementKillCount(String bossId, String serverName) throws SQLException {
        String sql = """
            UPDATE boss_drop_statistics
            SET kill_count = kill_count + 1,
                drop_rate = CASE WHEN kill_count + 1 > 0
                    THEN CAST(drop_count AS REAL) / (kill_count + 1) ELSE 0 END
            WHERE boss_id = ? AND server_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bossId);
            stmt.setString(2, serverName == null ? "" : serverName);
            stmt.executeUpdate();
        }
    }

    public void refreshDropRates(String bossId, String serverName) throws SQLException {
        String sql = """
            UPDATE boss_drop_statistics
            SET drop_rate = CASE WHEN kill_count > 0
                THEN CAST(drop_count AS REAL) / kill_count ELSE 0 END
            WHERE boss_id = ? AND server_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bossId);
            stmt.setString(2, serverName == null ? "" : serverName);
            stmt.executeUpdate();
        }
    }
}
