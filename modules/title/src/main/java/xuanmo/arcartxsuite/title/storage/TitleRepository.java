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
     * titleId 为空时卸下该组，装备和展示变更一起提交。
     *
     * @param playerUuid 玩家 UUID
     * @param groupId    分组 ID
     * @param titleId    称号 ID
     * @param updatedAt  更新时间
     * @throws SQLException 数据库异常
     */
    PlayerTitleState setEquippedTitle(UUID playerUuid, String groupId, String titleId, Instant updatedAt) throws SQLException;

    /**
     * 原子清空装备和展示，返回提交后的状态。
     */
    PlayerTitleState clearEquippedTitles(UUID playerUuid) throws SQLException;

    /**
     * 原子授予称号，保留已有隐藏设置，清理旧授予留下的装备和展示。
     *
     * @param playerUuid 玩家 UUID
     * @param proposal 本次授予的称号记录
     * @param keepPermanent 是否保留已有永久持有记录
     * @throws SQLException 数据库异常
     */
    GrantedTitle grantOwnedTitle(UUID playerUuid, PlayerOwnedTitle proposal, boolean keepPermanent) throws SQLException;

    record GrantedTitle(PlayerOwnedTitle title, boolean clearedPresentation) {}

    PlayerTitleState setOwnedTitleHidden(UUID playerUuid, String titleId, boolean hidden, Instant updatedAt) throws SQLException;

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
    PlayerTitleState setDisplayTitle(UUID playerUuid, String titleId, Instant updatedAt) throws SQLException;

    /**
     * 在一个有上限的事务中清理已过期的持有记录及关联的装备和展示。
     *
     * @param now 当前时间
     * @return 删除的记录数
     * @throws SQLException 数据库异常
     */
    int deleteExpiredTitles(Instant now) throws SQLException;

}
