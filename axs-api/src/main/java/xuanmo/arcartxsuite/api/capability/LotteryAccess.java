package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 抽奖公开能力接口（公开 capability）。
 * <p>
 * 由 Lottery 模块实现，供外部插件查询/调整抽奖积分、
 * 执行兑换与开箱。兑换和开箱由 Bukkit 主线程提交，数据库工作在后台完成。
 * 结果可能在后台线程完成，调用方不得在主线程阻塞等待。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(LotteryAccess.class)} 获取。
 */
@PublicCapability
public interface LotteryAccess {

    /** 查询玩家指定积分余额。 */
    @NotNull CompletionStage<Long> getPoint(@NotNull UUID playerUuid, @NotNull String pointId);

    /** 查询玩家全部积分余额（pointId -> 数量）。 */
    @NotNull CompletionStage<Map<String, Long>> getPoints(@NotNull UUID playerUuid);

    /**
     * 为玩家增加抽奖积分。
     *
     * @return 是否成功
     */
    @NotNull CompletionStage<Boolean> givePoints(@NotNull UUID playerUuid, @NotNull String pointId, long amount);

    /**
     * 为玩家执行一次兑换商店兑换。
     *
     * @return 是否成功
     */
    @NotNull CompletionStage<Boolean> exchange(@NotNull Player player, @NotNull String itemId);

    /**
     * 为玩家批量开启 CASE 奖池。界面会话由模块的客户端入口管理。
     *
     * @param poolId 奖池 id
     * @param count  开启次数（1–10）
     * @return 每次开启的抽取结果
     */
    @NotNull CompletionStage<List<CaseDrawResult>> openCase(@NotNull Player player, @NotNull String poolId, int count);

    /**
     * CASE 单次开启结果视图。
     *
     * @param rarity    命中稀有度名称
     * @param poolIndex 命中奖品在奖池中的 1 基索引，无奖品为 -1
     * @param itemKey   奖品标识，无奖品为 null
     * @param itemName  奖品显示名，无奖品为 null
     * @param amount    发放数量，无奖品为 0
     */
    record CaseDrawResult(
        @NotNull String rarity,
        int poolIndex,
        @Nullable String itemKey,
        @Nullable String itemName,
        int amount
    ) {
    }
}
