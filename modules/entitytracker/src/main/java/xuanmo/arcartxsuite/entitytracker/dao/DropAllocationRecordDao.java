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

/**
 * 掉落分配记录数据访问对象，属于 entitytracker 模块的数据持久层。
 * <p>
 * 提供掉落分配记录和 Roll 参与记录的数据库写入功能。
 */
public class DropAllocationRecordDao {

    private final DataSource dataSource;

    public DropAllocationRecordDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
    }

    /**
     * 插入一条掉落分配记录。
     *
     * @param bossKillId      关联的 Boss 击杀记录 ID
     * @param itemId          物品 ID
     * @param itemName        物品名称
     * @param amount          物品数量
     * @param allocationType  分配类型
     * @param winnerUuid      获得者 UUID
     * @param winnerName      获得者名称
     * @param pointsCost      消耗积分
     * @param rollValue       Roll 值（可为 {@code null}）
     * @param priorityScore   优先级分数（可为 {@code null}）
     * @param serverName      服务器名称
     * @return 新记录的自增 ID，失败时返回 -1
     * @throws SQLException 数据库操作失败时抛出
     */
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

    /**
     * 插入一条 Roll 参与记录。
     *
     * @param allocationRecordId 关联的分配记录 ID
     * @param playerUuid         玩家 UUID
     * @param playerName         玩家名称
     * @param rollType           Roll 类型
     * @param rollValue          Roll 值（可为 {@code null}）
     * @param serverName         服务器名称
     * @throws SQLException 数据库操作失败时抛出
     */
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
