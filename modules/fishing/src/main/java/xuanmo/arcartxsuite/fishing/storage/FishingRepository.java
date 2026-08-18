package xuanmo.arcartxsuite.fishing.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
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

    /** 保存一条待发放奖励。 */
    void savePendingReward(@NotNull FishingPendingReward reward);

    /**除指定的待发放奖励（发放成功后调用）。 */
    void deletePendingReward(long id);
}
