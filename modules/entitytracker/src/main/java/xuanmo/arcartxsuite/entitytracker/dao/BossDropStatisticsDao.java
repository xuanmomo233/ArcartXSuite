package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Boss 掉落统计数据访问对象，属于 entitytracker 模块的数据持久层。
 * <p>
 * 提供 Boss 掉落物品记录、击杀计数递增和掉率刷新等数据库操作。
 */
public class BossDropStatisticsDao {

    private final DataSource dataSource;

    public BossDropStatisticsDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    /**
     * 记录一次 Boss 掉落物品（存在则递增计数）。
     *
     * @param bossId      Boss ID
     * @param itemId      物品 ID
     * @param itemName    物品名称
     * @param serverName  服务器名称
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 递增指定 Boss 的击杀计数并更新掉率。
     *
     * @param bossId     Boss ID
     * @param serverName 服务器名称
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 刷新指定 Boss 的所有物品掉率。
     *
     * @param bossId     Boss ID
     * @param serverName 服务器名称
     * @throws SQLException 数据库操作失败时抛出
     */
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
