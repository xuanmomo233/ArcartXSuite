package xuanmo.arcartxsuite.battlepass.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

/**
 * 战令数据仓储接口，定义玩家进度、奖励领取记录及玩家任务的持久化操作。
 * <p>
 * 由 {@link JdbcBattlePassRepository} 提供 JDBC 实现，支持 SQLite 和 MySQL 两种存储模式。
 */
public interface BattlePassRepository {
    @FunctionalInterface interface Write<T> { T run() throws SQLException; }
    /** Worker-only serialization of cache snapshots and authentication receipts for a player. */
    <T> T withPlayerWrite(UUID player,Write<T> work) throws SQLException;
    /** Whether other server instances can concurrently write this progress store. */
    default boolean sharedProgress() { return false; }
    /** Serialize progress mutations for this player and season on a single database connection. */
    default <T> T withProgressWrite(UUID player, String season, Write<T> work) throws SQLException { return work.run(); }
    boolean receivedAuthFact(UUID player,String season,String fact) throws SQLException;
    void saveAuthFact(String fact,BattlePassPlayerProgress progress,List<PlayerTaskInstance> tasks,boolean changed) throws SQLException;
    long loadAbyssCursor(String season) throws SQLException;
    void advanceAbyssCursor(String season, long sequence) throws SQLException;

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
     * 保存玩家进度。领取记录由独立的奖励确认操作写入。
     *
     * @param progress 玩家进度实例
     * @throws SQLException 数据库操作失败时抛出
     */
    void saveProgress(BattlePassPlayerProgress progress) throws SQLException;

    /** Commit progress, task changes and any period replacements together. */
    void saveProgressAndTasks(BattlePassPlayerProgress progress, List<PlayerTaskInstance> tasks,
                              Set<BattlePassTask.TaskCategory> replacedCategories) throws SQLException;

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

    record RewardRequest(String id, String payload) {}

    /** Select the original reward payload and delivery key when a request is retried. */
    RewardRequest stageReward(UUID player, String season, String rewardId, String payload) throws SQLException;

    /** Record durable host acceptance only for the current request. */
    boolean completeReward(UUID player, String season, String rewardId, String requestId) throws SQLException;

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
     * 批量保存玩家任务实例。
     *
     * @param playerUuid 玩家 UUID
     * @param seasonId   赛季 ID
     * @param tasks      要保存的任务实例
     * @throws SQLException 数据库操作失败
     */
    void savePlayerTasks(UUID playerUuid, String seasonId, List<PlayerTaskInstance> tasks) throws SQLException;

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

    /**
     * 查询指定赛季是否已完成结算。
     *
     * @param seasonId 赛季 ID
     * @return {@code true} 表示已结算
     * @throws SQLException 数据库操作失败时抛出
     */
    boolean isSeasonSettled(String seasonId) throws SQLException;

    /**
     * 标记指定赛季已完成结算。
     *
     * @param seasonId 赛季 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    void markSeasonSettled(String seasonId) throws SQLException;

    /**
     * 加载指定赛季所有玩家的进度（用于赛季结算）。
     * <p>
     * 仅返回 player_uuid 与 current_level、pass_tier 字段，不加载任务进度和领取记录。
     *
     * @param seasonId 赛季 ID
     * @return 玩家 UUID → 简化进度（level, tier）的映射
     * @throws SQLException 数据库操作失败时抛出
     */
    java.util.List<PlayerSeasonSummary> loadSeasonSummaries(String seasonId) throws SQLException;

    /**
     * 玩家赛季进度摘要，用于结算。
     *
     * @param playerUuid 玩家 UUID
     * @param level      最终等级
     * @param tier       战令档位
     */
    record PlayerSeasonSummary(UUID playerUuid, int level, String tier) {}
}
