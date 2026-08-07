package xuanmo.arcartxsuite.title.storage;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;

/**
 * 称号模块仓储接口。
 * <p>
 * 定义玩家称号数据的持久化操作契约，包括加载/保存状态、
 * 装备分组管理、展示称号管理、过期称号清理等。
 * 属于 title 模块存储层。
 */
public interface TitleRepository {

    /**
     * 初始化数据库表结构。
     *
     * @throws SQLException 数据库异常
     */
    void initialize() throws SQLException;

    /**
     * 加载玩家的完整称号状态。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家称号状态
     * @throws SQLException 数据库异常
     */
    PlayerTitleState loadState(UUID playerUuid) throws SQLException;

    /**
     * 保存玩家在指定分组的装备称号。
     *
     * @param playerUuid 玩家 UUID
     * @param groupId    分组 ID
     * @param titleId    称号 ID
     * @param updatedAt  更新时间
     * @throws SQLException 数据库异常
     */
    void saveEquippedTitle(UUID playerUuid, String groupId, String titleId, Instant updatedAt) throws SQLException;

    /**
     * 删除玩家在指定分组的装备记录。
     *
     * @param playerUuid 玩家 UUID
     * @param groupId    分组 ID
     * @throws SQLException 数据库异常
     */
    void deleteEquippedGroup(UUID playerUuid, String groupId) throws SQLException;

    /**
     * 删除玩家所有分组中装备了指定称号的装备记录。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @throws SQLException 数据库异常
     */
    void deleteEquippedTitle(UUID playerUuid, String titleId) throws SQLException;

    /**
     * 删除玩家的全部装备分组记录。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException 数据库异常
     */
    void deleteAllEquippedGroups(UUID playerUuid) throws SQLException;

    /**
     * 保存玩家拥有的称号记录。
     *
     * @param playerUuid 玩家 UUID
     * @param ownedTitle 拥有的称号记录
     * @throws SQLException 数据库异常
     */
    void saveOwnedTitle(UUID playerUuid, PlayerOwnedTitle ownedTitle) throws SQLException;

    /**
     * 删除玩家拥有的指定称号记录。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @throws SQLException 数据库异常
     */
    void deleteOwnedTitle(UUID playerUuid, String titleId) throws SQLException;

    /**
     * 保存玩家的展示称号。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @param updatedAt  更新时间
     * @throws SQLException 数据库异常
     */
    void saveDisplayTitle(UUID playerUuid, String titleId, Instant updatedAt) throws SQLException;

    /**
     * 删除玩家的展示称号记录。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException 数据库异常
     */
    void deleteDisplayTitle(UUID playerUuid) throws SQLException;

    /**
     * 批量删除已过期的称号记录。
     *
     * @param now 当前时间
     * @return 删除的记录数
     * @throws SQLException 数据库异常
     */
    int deleteExpiredTitles(Instant now) throws SQLException;

    /**
     * 关闭仓储，释放数据库连接等资源。
     */
    void close();
}
