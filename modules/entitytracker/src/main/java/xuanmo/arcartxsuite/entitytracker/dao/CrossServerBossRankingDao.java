package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 跨服 Boss 排名数据访问对象，属于 entitytracker 模块的数据持久层。
 * <p>
 * 提供跨服排名快照的事务写入和按排名类型/Boss ID 查询功能。
 */
public class CrossServerBossRankingDao {

    private final DataSource dataSource;
    public record Snapshot(String rankingType, String bossId, String rankingDataJson) {}
    private record RankingKey(String type, String boss) {}

    public CrossServerBossRankingDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    /**
     * 在同一个事务内替换指定排名。
     *
     * @param rankingType     排名类型
     * @param bossId          Boss ID，为空时表示全局排名
     * @param rankingDataJson 排名数据 JSON
     * @param expireTime      过期时间，为 {@code null} 时不过期
     * @throws SQLException 数据库操作失败时抛出
     */
    public void upsert(String rankingType, String bossId, String rankingDataJson, LocalDateTime expireTime)
        throws SQLException {
        replaceAll(List.of(new Snapshot(rankingType, bossId, rankingDataJson)), expireTime);
    }

    public void replaceAll(List<Snapshot> snapshots, LocalDateTime expireTime) throws SQLException {
        if (snapshots.isEmpty()) return;
        var latest = new LinkedHashMap<RankingKey, Snapshot>();
        for (Snapshot snapshot : snapshots) {
            String boss = snapshot.bossId();
            latest.put(new RankingKey(snapshot.rankingType(), boss == null || boss.isBlank() ? null : boss), snapshot);
        }
        String sql = """
            INSERT INTO cross_server_boss_rankings
            (ranking_type, boss_id, ranking_data, last_update, expire_time)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var deleteGlobal = conn.prepareStatement(
                    "DELETE FROM cross_server_boss_rankings WHERE ranking_type=? AND boss_id IS NULL");
                 var deleteBoss = conn.prepareStatement(
                    "DELETE FROM cross_server_boss_rankings WHERE ranking_type=? AND boss_id=?");
                 var insert = conn.prepareStatement(sql)) {
                Timestamp updated = Timestamp.valueOf(LocalDateTime.now());
                Timestamp expiry = expireTime == null ? null : Timestamp.valueOf(expireTime);
                boolean global = false;
                boolean bosses = false;
                for (var entry : latest.entrySet()) {
                    Snapshot snapshot = entry.getValue();
                    String boss = entry.getKey().boss();
                    boolean globalRanking = boss == null;
                    var delete = globalRanking ? deleteGlobal : deleteBoss;
                    delete.setString(1, snapshot.rankingType());
                    if (!globalRanking) delete.setString(2, boss);
                    delete.addBatch();
                    if (globalRanking) global = true;
                    else bosses = true;

                    insert.setString(1, snapshot.rankingType());
                    insert.setString(2, globalRanking ? null : boss);
                    insert.setString(3, snapshot.rankingDataJson());
                    insert.setTimestamp(4, updated);
                    insert.setTimestamp(5, expiry);
                    insert.addBatch();
                }
                if (global) deleteGlobal.executeBatch();
                if (bosses) deleteBoss.executeBatch();
                insert.executeBatch();
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
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

}
