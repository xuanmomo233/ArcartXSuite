package xuanmo.arcartxsuite.eventpacket.storage;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * 事件包持久化仓储接口。
 * <p>
 * 定义玩家击杀进度与规则已触发状态的读写契约，
 * 由 {@code JdbcEventPacketRepository} 提供 JDBC 实现。
 */
public interface EventPacketRepository {

    /**
     * 初始化仓储，创建所需的数据表结构。
     *
     * @throws SQLException 数据库操作失败时抛出
     */
    void initialize() throws SQLException;

    /**
     * 查询指定玩家在某条规则下的累计击杀数。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 当前击杀数，无记录时返回 0
     * @throws SQLException 数据库操作失败时抛出
     */
    int getKillCount(UUID playerUuid, String ruleId) throws SQLException;

    /**
     * 设置指定玩家在某条规则下的击杀数。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @param count      要设置的击杀数
     * @throws SQLException 数据库操作失败时抛出
     */
    void setKillCount(UUID playerUuid, String ruleId, int count) throws SQLException;

    /**
     * 将指定玩家在某条规则下的击杀数自增 1。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void incrementKillCount(UUID playerUuid, String ruleId) throws SQLException;

    /**
     * 加载指定玩家所有规则的击杀进度。
     *
     * @param playerUuid 玩家 UUID
     * @return 规则 ID 到击杀数的映射
     * @throws SQLException 数据库操作失败时抛出
     */
    Map<String, Integer> loadAllKillCounts(UUID playerUuid) throws SQLException;

    /**
     * 判断指定玩家是否已触发过某条规则。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 已触发返回 {@code true}
     * @throws SQLException 数据库操作失败时抛出
     */
    boolean hasFired(UUID playerUuid, String ruleId) throws SQLException;

    /**
     * 标记指定玩家已触发某条规则。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void markFired(UUID playerUuid, String ruleId) throws SQLException;

    /**
     * 移除指定玩家某条规则的已触发标记。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void removeFired(UUID playerUuid, String ruleId) throws SQLException;

    /**
     * 关闭仓储，释放数据库连接等资源。
     */
    void close();
}
