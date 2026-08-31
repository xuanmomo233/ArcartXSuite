package xuanmo.arcartxsuite.fishing.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPendingReward;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;

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

    void saveSeasonData(@NotNull FishingSeasonData data);

    /** 删除指定赛季的所有玩家数据（赛季重置时调用）。 */
    void clearSeasonData(@NotNull String seasonId);

    // ─── 永久数据（跨赛季保留） ────────────────────────────────────

    @NotNull FishingPermanentData loadPermanentData(@NotNull UUID uuid);

    void savePermanentData(@NotNull FishingPermanentData data);

    // ─── 图鉴（跨赛季保留） ────────────────────────────────────────

    @Nullable FishCollectionEntry loadCollectionEntry(@NotNull UUID playerUuid, @NotNull String fishId);

    void saveCollectionEntry(@NotNull FishCollectionEntry entry);

    @NotNull List<FishCollectionEntry> loadCollection(@NotNull UUID playerUuid);

    // ─── 待发放奖励（离线玩家补发） ────────────────────────────────

    /** 加载玩家所有待发放奖励。 */
    @NotNull List<FishingPendingReward> loadPendingRewards(@NotNull UUID uuid);

    /** 加载所有有待发放奖励的玩家 UUID（用于旧数据迁移）。 */
    @NotNull java.util.List<java.util.UUID> loadAllPendingPlayerUuids();

    /** 保存一条待发放奖励。 */
    void savePendingReward(@NotNull FishingPendingReward reward);

    /**删除指定的待发放奖励（发放成功后调用）。 */
    void deletePendingReward(long id);

    // ─── 捕获记录（跨赛季保留，持久化存储） ────────────────────

    /** 保存一条捕获记录。 */
    void saveCatchRecord(@NotNull FishingCatchRecord record);

    /** 加载玩家最近的 N 条捕获记录（按时间倒序）。 */
    @NotNull List<FishingCatchRecord> loadCatchRecords(@NotNull UUID uuid, int limit);

    /** 删除玩家所有捕获记录（数据迁移/清理时调用）。 */
    void clearCatchRecords(@NotNull UUID uuid);

    // ─── 饵料队列（按玩家存储，鱼咬钩时按 slot_index 升序消耗） ──────

    /** 加载玩家饵料队列（按 slot_index 升序）。 */
    @NotNull List<BaitQueueEntry> loadBaitQueue(@NotNull UUID uuid);

    /** 保存/更新饵料队列中的单个槽位（amount=0 时删除该槽位）。 */
    void saveBaitSlot(@NotNull BaitQueueEntry entry);

    /** 清空玩家饵料队列。 */
    void clearBaitQueue(@NotNull UUID uuid);
}
