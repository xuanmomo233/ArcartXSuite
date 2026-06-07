package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

public class CrossServerBossRankingDao {

    private final DataSource dataSource;

    public CrossServerBossRankingDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    public void upsert(String rankingType, String bossId, String rankingDataJson, LocalDateTime expireTime)
        throws SQLException {
        deleteExisting(rankingType, bossId);
        String sql = """
            INSERT INTO cross_server_boss_rankings
            (ranking_type, boss_id, ranking_data, last_update, expire_time)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rankingType);
            if (bossId == null || bossId.isBlank()) {
                stmt.setNull(2, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(2, bossId);
            }
            stmt.setString(3, rankingDataJson);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            if (expireTime == null) {
                stmt.setNull(5, java.sql.Types.TIMESTAMP);
            } else {
                stmt.setTimestamp(5, Timestamp.valueOf(expireTime));
            }
            stmt.executeUpdate();
        }
    }

    public Optional<String> findRankingData(String rankingType, String bossId) throws SQLException {
        String sql = bossId == null || bossId.isBlank()
            ? """
                SELECT ranking_data FROM cross_server_boss_rankings
                WHERE ranking_type = ? AND boss_id IS NULL
                ORDER BY last_update DESC LIMIT 1
                """
            : """
                SELECT ranking_data FROM cross_server_boss_rankings
                WHERE ranking_type = ? AND boss_id = ?
                ORDER BY last_update DESC LIMIT 1
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rankingType);
            if (bossId != null && !bossId.isBlank()) {
                stmt.setString(2, bossId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("ranking_data"));
                }
            }
        }
        return Optional.empty();
    }

    private void deleteExisting(String rankingType, String bossId) throws SQLException {
        String sql = bossId == null || bossId.isBlank()
            ? "DELETE FROM cross_server_boss_rankings WHERE ranking_type = ? AND boss_id IS NULL"
            : "DELETE FROM cross_server_boss_rankings WHERE ranking_type = ? AND boss_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rankingType);
            if (bossId != null && !bossId.isBlank()) {
                stmt.setString(2, bossId);
            }
            stmt.executeUpdate();
        }
    }
}
