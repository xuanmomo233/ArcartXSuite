package xuanmo.arcartxsuite.battlepass.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

/**
 * 战令数据仓储接口，定义玩家进度、任务进度、奖励领取记录及玩家任务的持久化操作。
 * <p>
 * 由 {@link JdbcBattlePassRepository} 提供 JDBC 实现，支持 SQLite 和 MySQL 两种存储模式。
 */
public interface BattlePassRepository {

    /**
     * 加载玩家在指定赛季的进度数据。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @return 玩家进度实例，无记录时返回默认初始进度
     * @throws SQLException 数据库操作失败时抛出
     */
    BattlePassPlayerProgress loadProgress(UUID playerUuid, String seasonId) throws SQLException;

    /**
     * 保存玩家进度数据（含任务进度与领取记录）。
     *
     * @param progress 玩家进度实例
     * @throws SQLException 数据库操作失败时抛出
     */
    void saveProgress(BattlePassPlayerProgress progress) throws SQLException;

    /**
     * 加载玩家在指定赛季的所有任务进度。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @return 任务 ID → 完成次数的映射
     * @throws SQLException 数据库操作失败时抛出
     */
    Map<String, Integer> loadTaskProgresses(UUID playerUuid, String seasonId) throws SQLException;

    /**
     * 保存单条任务进度。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @param taskId     任务 ID
     * @param count      完成次数
     * @throws SQLException 数据库操作失败时抛出
     */
    void saveTaskProgress(UUID playerUuid, String seasonId, String taskId, int count) throws SQLException;

    /**
     * 加载玩家在指定赛季已领取的奖励 ID 集合。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @return 已领取奖励 ID 集合
     * @throws SQLException 数据库操作失败时抛出
     */
    Set<String> loadClaimedRewards(UUID playerUuid, String seasonId) throws SQLException;

    /**
     * 记录一条奖励领取记录。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @param rewardId   奖励 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void saveClaimedReward(UUID playerUuid, String seasonId, String rewardId) throws SQLException;

    /**
     * 重置玩家在指定赛季的全部数据（进度、任务、奖励、玩家任务）。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void resetPlayerProgress(UUID playerUuid, String seasonId) throws SQLException;

    /**
     * 统计指定赛季的活跃玩家数量。
     *
     * @param seasonId 赛季 ID
     * @return 活跃玩家数
     * @throws SQLException 数据库操作失败时抛出
     */
    int countActivePlayers(String seasonId) throws SQLException;

    /**
     * 加载玩家在指定赛季的所有任务实例。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @return 任务实例列表
     * @throws SQLException 数据库操作失败时抛出
     */
    List<PlayerTaskInstance> loadPlayerTasks(UUID playerUuid, String seasonId) throws SQLException;

    /**
     * 保存单个玩家任务实例（插入或更新）。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @param task       任务实例
     * @throws SQLException 数据库操作失败时抛出
     */
    void savePlayerTask(UUID playerUuid, String seasonId, PlayerTaskInstance task) throws SQLException;

    /**
     * 删除玩家在指定赛季中某分类的所有任务实例。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @param category   任务分类
     * @throws SQLException 数据库操作失败时抛出
     */
    void deletePlayerTasksByCategory(UUID playerUuid, String seasonId, BattlePassTask.TaskCategory category) throws SQLException;

    /**
     * 删除玩家在指定赛季的所有任务实例。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void deletePlayerTasks(UUID playerUuid, String seasonId) throws SQLException;
}
