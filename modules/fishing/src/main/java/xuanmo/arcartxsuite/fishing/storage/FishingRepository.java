package xuanmo.arcartxsuite.fishing.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.function.BooleanSupplier;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance;

/**
 * 钓鱼模块的数据仓储接口，定义赛季数据、永久数据、图鉴条目与待发放奖励的持久化操作。
 * <p>
 * 关联模块：{@code fishing}。默认实现为 {@link JdbcFishingRepository}。
 */
public interface FishingRepository {

    void initialize() throws Exception;

    void shutdown();

    // ─── 赛季数据（按 seasonId 隔离，赛季重置） ────────────────────

    @NotNull FishingSeasonData loadSeasonData(@NotNull UUID uuid, @NotNull String seasonId);

    record DisplayData(int level, int currentXp, int talentPoints, int unspentTalentPoints, int fatigue,
                       int totalCaught, int perfectCatches, int treasureCaught,
                       Map<String, Integer> catchCounts) {}

    /** Fetch the read-only placeholder fields in one query; distinguish a failed read from an empty profile. */
    DisplayData loadDisplayData(UUID uuid, String seasonId) throws SQLException;


    void saveSeasonData(@NotNull FishingSeasonData data);
    record SeasonChange(FishingSeasonData before, FishingSeasonData after) {}

    /** Apply a pure mutation to the current row and return only after commit. */
    SeasonChange updateSeason(UUID uuid, String seasonId, UnaryOperator<FishingSeasonData> update) throws SQLException;

    /** 仅更新恢复时读取的疲惫值，保留其他赛季字段。 */
    void recoverFatigue(UUID uuid, String seasonId, int expectedFatigue, int newFatigue) throws java.sql.SQLException;

    /** 删除指定赛季的所有玩家数据（赛季重置时调用）。 */
    void clearSeasonData(@NotNull String seasonId);

    enum RewardReservation { ACQUIRED, DELIVERED, IN_DOUBT }

    /** Freezes the operation's entire ordered plan and returns it only to its original delivery store. */
    String reserveRewardPlan(UUID uuid, String seasonId, String rewardKey, String owner, String payload) throws java.sql.SQLException;

    RewardReservation reserveReward(UUID uuid, String seasonId, String rewardKey, int step) throws java.sql.SQLException;

    void finishReward(UUID uuid, String seasonId, String rewardKey, int step, boolean delivered) throws java.sql.SQLException;

    void markRewardComplete(UUID uuid, String seasonId, String rewardKey) throws java.sql.SQLException;

    Set<String> completedRewardKeys(UUID uuid, String seasonId);

    // The player UUID permits at most one unresolved sale, including across shared-storage nodes.
    record FishSale(UUID player, UUID request, String owner, String node, long boot, UUID sourceSession,
                    String currency, String amount, List<PendingReward> fish) {}

    boolean reserveFishSale(FishSale sale) throws SQLException;
    boolean beginFishSalePayment(FishSale sale) throws SQLException;
    void cancelUnstartedFishSale(FishSale sale) throws SQLException;
    void finishFishSale(FishSale sale, boolean paid) throws SQLException;
    List<FishSale> reservedFishSales(String node, String after, int limit) throws SQLException;
    boolean recoverUnpaidFishSale(FishSale sale, boolean removedFromInventory) throws SQLException;

    // ─── 永久数据（跨赛季保留） ────────────────────────────────────

    @NotNull FishingPermanentData loadPermanentData(@NotNull UUID uuid);

    void savePermanentData(@NotNull FishingPermanentData data);

    // ─── 图鉴（跨赛季保留） ────────────────────────────────────────

    @Nullable FishCollectionEntry loadCollectionEntry(@NotNull UUID playerUuid, @NotNull String fishId);

    void saveCollectionEntry(@NotNull FishCollectionEntry entry);

    @NotNull List<FishCollectionEntry> loadCollection(@NotNull UUID playerUuid);
    int countCollection(UUID playerUuid);

    // ─── 捕获记录（跨赛季保留，持久化存储） ────────────────────

    /** 保存一条捕获记录。 */
    void saveCatchRecord(@NotNull FishingCatchRecord record);

    /** Returns true only after the record or its existing event-id has been committed. */
    boolean saveCatchRecordConfirmed(@NotNull FishingCatchRecord record);
    record CatchSettlement(SeasonChange season, FishingPermanentData permanent, boolean firstCatch,
                           @Nullable CatchDelivery delivery) {}

    record ConditionalReward(PendingReward reward, boolean firstOnly) {}

    record CatchRewards(String owner, boolean treasure, List<ConditionalReward> rewards) {}

    record CatchDelivery(UUID eventId, UUID player, String owner, boolean treasure, List<PendingReward> rewards) {}

    /** Commit the capture receipt and all intrinsic progress exactly once for this event ID. */
    default CatchSettlement settleCatch(FishingCatchRecord record, String seasonId,
                                        UnaryOperator<FishingSeasonData> updateSeason) throws SQLException {
        return settleCatch(record, seasonId, updateSeason, null);
    }

    CatchSettlement settleCatch(FishingCatchRecord record, String seasonId, UnaryOperator<FishingSeasonData> updateSeason,
                                @Nullable CatchRewards rewards) throws SQLException;
    List<CatchDelivery> pendingCatchDeliveries(String owner, long now, int limit) throws SQLException;
    void deferCatchDelivery(CatchDelivery delivery, long retryAt) throws SQLException;
    void finishCatchDelivery(CatchDelivery delivery) throws SQLException;

    /** 加载玩家最近的 N 条捕获记录（按时间倒序）。 */
    @NotNull List<FishingCatchRecord> loadCatchRecords(@NotNull UUID uuid, int limit);

    /** 删除玩家所有捕获记录（数据迁移/清理时调用）。 */
    void clearCatchRecords(@NotNull UUID uuid);

    // ─── 饵料队列（按玩家存储，鱼咬钩时按 slot_index 升序消耗） ──────

    record BiteProfile(FishingSeasonData season, @Nullable BaitQueueEntry baitHead) {}

    BiteProfile loadBiteProfile(UUID uuid, String seasonId) throws SQLException;
    boolean confirmBite(BiteProfile expected, boolean consumeBait, BooleanSupplier stillOpen) throws SQLException;

    record BaitEdit(FishingSeasonData season, BaitQueueEntry first, BaitQueueEntry second) {}
    record BaitDeposit(UUID player, UUID request, String node, long boot, int slot, String bait, int amount) {}

    BaitEdit loadBaitEdit(UUID uuid, String seasonId, int first, int second) throws SQLException;
    boolean swapBaitEdit(BaitEdit expected) throws SQLException;
    boolean withdrawBait(BaitEdit expected, int amount, UUID request, String owner, PendingReward reward) throws SQLException;
    boolean reserveBaitDeposit(BaitEdit expected, BaitDeposit deposit) throws SQLException;
    List<BaitDeposit> pendingBaitDeposits(String node, String after, int limit) throws SQLException;
    boolean publishBaitDeposit(BaitDeposit deposit) throws SQLException;
    boolean cancelBaitDeposit(BaitDeposit deposit) throws SQLException;

    /** 加载玩家饵料队列（按 slot_index 升序）。 */
    @NotNull List<BaitQueueEntry> loadBaitQueue(@NotNull UUID uuid);

    /** 仅当槽位仍与读取时一致才更新，amount=0 表示删除。 */
    boolean replaceBaitSlot(@Nullable BaitQueueEntry expected, @NotNull BaitQueueEntry replacement);

    /** 原子交换两个槽位，amount=0 表示原槽位为空。 */
    boolean swapBaitSlots(@NotNull BaitQueueEntry first, @NotNull BaitQueueEntry second);

    /** 清空玩家饵料队列。 */
    void clearBaitQueue(@NotNull UUID uuid);

    // ─── 任务进度（每日/每周/赛季任务实例持久化） ──────────────────

    /** 加载玩家全部任务实例（含进度与完成态）。 */
    @NotNull List<PlayerTaskInstance> loadTaskProgress(@NotNull UUID uuid, String seasonId);

    /** 保存/更新一条任务实例进度（upsert）。 */
    void saveTaskProgress(@NotNull PlayerTaskInstance instance, String seasonId);

    record TaskRewardResult(boolean awarded, int previousLevel, int level) {}

    /** 任务完成标记与经验在同一事务内提交。失败返回 null。 */
    @Nullable TaskRewardResult completeTask(PlayerTaskInstance instance, String seasonId, int xp,
                                            XpCurve curve, int maxLevel);

    /**
     * 清理某分类下 assigned_date 早于指定日期的全部过期任务行（全局，含离线玩家）。
     * <p>daily 重置传今日日期；weekly 重置传本周一日期。
     */
    void purgeTaskProgressBefore(@NotNull String category, @NotNull String assignedDateBefore);

}
