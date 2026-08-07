package xuanmo.arcartxsuite.lottery.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;

/**
 * Lottery 模块数据仓库接口。
 * <p>
 * 定义 GACHA 状态、CASE 状态、待领取奖励以及抽卡/开箱日志的持久化操作契约。
 */
public interface LotteryRepository {

    // ─── Gacha State ────────────────────────────────────────

    /**
     * 加载玩家在指定奖池的 GACHA 抽卡状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @return 抽卡状态，不存在时返回初始空状态
     */
    PlayerGachaState loadGachaState(@NotNull UUID playerUuid, @NotNull String poolId);

    /**
     * 保存玩家的 GACHA 抽卡状态（upsert）。
     *
     * @param state 抽卡状态
     */
    void saveGachaState(@NotNull PlayerGachaState state);

    /**
     * 删除玩家在指定奖池的 GACHA 抽卡状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     */
    void deleteGachaState(@NotNull UUID playerUuid, @NotNull String poolId);

    // ─── Case State ─────────────────────────────────────────

    /**
     * 加载玩家在指定奖池的 CASE 开箱状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @return 开箱状态，不存在时返回初始空状态
     */
    PlayerCaseState loadCaseState(@NotNull UUID playerUuid, @NotNull String poolId);

    /**
     * 保存玩家的 CASE 开箱状态（upsert）。
     *
     * @param state 开箱状态
     */
    void saveCaseState(@NotNull PlayerCaseState state);

    /**
     * 删除玩家在指定奖池的 CASE 开箱状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     */
    void deleteCaseState(@NotNull UUID playerUuid, @NotNull String poolId);

    /**
     * 将一条待领取奖励加入队列。
     *
     * @param playerUuid    玩家 UUID
     * @param poolId        奖池 ID
     * @param itemMetadata  奖品元数据字符串
     * @param itemData      序列化的物品数据，可为 {@code null}
     * @return 新建的待领取记录 ID，失败时返回 -1
     */
    long enqueuePendingClaim(@NotNull UUID playerUuid, @NotNull String poolId,
                              @NotNull String itemMetadata, @Nullable String itemData);

    /**
     * 查询玩家全部未领取的待领取奖励。
     *
     * @param playerUuid 玩家 UUID
     * @return 待领取奖励列表，按创建时间升序
     */
    @NotNull List<PendingClaim> getPendingClaims(@NotNull UUID playerUuid);

    /**
     * 将指定待领取奖励标记为已领取。
     *
     * @param claimId 待领取记录 ID
     */
    void markPendingClaimClaimed(long claimId);

    /**
     * 待领取奖励记录。
     *
     * @param id           记录 ID
     * @param playerUuid   玩家 UUID
     * @param poolId       奖池 ID
     * @param itemMetadata 奖品元数据字符串
     * @param itemData     序列化的物品数据
     * @param createdTime  创建时间戳（毫秒）
     * @param attempts     投递尝试次数
     */
    record PendingClaim(long id, UUID playerUuid, String poolId, String itemMetadata,
                        String itemData, long createdTime, int attempts) {}

    // ─── Logs ───────────────────────────────────────────────

    /**
     * 记录一次 GACHA 抽卡日志。
     *
     * @param playerUuid   玩家 UUID
     * @param poolId       奖池 ID
     * @param pullCount    本次抽卡次数
     * @param itemsJson    获得奖品的 JSON 字符串
     * @param pityAtPull   抽卡时的 5 星保底计数
     * @param guaranteed   本次是否触发大保底
     */
    void logGachaPull(@NotNull UUID playerUuid, @NotNull String poolId, int pullCount,
                      @NotNull String itemsJson, int pityAtPull, boolean guaranteed);

    /**
     * 记录一次 CASE 开箱日志。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @param itemId     奖品 ID
     * @param rarity     稀有度名称
     */
    void logCaseOpen(@NotNull UUID playerUuid, @NotNull String poolId,
                     @NotNull String itemId, @NotNull String rarity);

    /**
     * 查询玩家在指定奖池的 GACHA 抽卡历史。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @param limit      返回条数上限
     * @return 抽卡历史记录列表，按时间倒序
     */
    @NotNull List<GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit);

    /**
     * 查询玩家在指定奖池的 CASE 开箱历史。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @param limit      返回条数上限
     * @return 开箱历史记录列表，按时间倒序
     */
    @NotNull List<CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit);

    // ─── Log Records ──────────────────────────────────────

    /**
     * GACHA 抽卡日志记录。
     *
     * @param id         日志 ID
     * @param pullTime   抽卡时间戳（毫秒）
     * @param pullCount  本次抽卡次数
     * @param itemsJson  获得奖品的 JSON 字符串
     * @param pityAtPull 抽卡时的 5 星保底计数
     * @param guaranteed 本次是否触发大保底
     */
    record GachaLogEntry(
        long id,
        long pullTime,
        int pullCount,
        String itemsJson,
        int pityAtPull,
        boolean guaranteed
    ) {}

    /**
     * CASE 开箱日志记录。
     *
     * @param id       日志 ID
     * @param openTime 开箱时间戳（毫秒）
     * @param itemId   奖品 ID
     * @param rarity   稀有度名称
     */
    record CaseLogEntry(
        long id,
        long openTime,
        String itemId,
        String rarity
    ) {}
}
