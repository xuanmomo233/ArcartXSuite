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

/**
 * 跨服 Boss 排名数据访问对象，属于 entitytracker 模块的数据持久层。
 * <p>
 * 提供跨服排名数据的写入（先删后插）和按排名类型/Boss ID 查询功能。
 */
public class CrossServerBossRankingDao {

    private final DataSource dataSource;

    public CrossServerBossRankingDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    /**
     * 写入或更新跨服排名数据（先删除旧记录再插入新记录）。
     *
     * @param rankingType     排名类型
     * @param bossId          Boss ID，为空时表示全局排名
     * @param rankingDataJson 排名数据 JSON
     * @param expireTime      过期时间，为 {@code null} 时不过期
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 查询指定排名类型和 Boss ID 的最新排名数据。
     *
     * @param rankingType 排名类型
     * @param bossId      Boss ID，为空时查询全局排名
     * @return 排名数据 JSON 的 Optional，未找到时为空
     * @throws SQLException 数据库操作失败时抛出
     */
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
