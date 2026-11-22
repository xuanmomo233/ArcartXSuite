package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 钓鱼公开能力接口（公开 capability）。
 * <p>
 * 由 Fishing 模块实现，供外部插件查询钓鱼状态、操控小游戏、
 * 调整经验/疲劳值与领奖。所有方法请在 Bukkit 主线程调用。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(FishingAccess.class)} 获取；
 * 钓鱼事件可订阅 EventBus 主题 {@code axs.fishing.*}
 * （success / perfect / collection_unlock / treasure / level_up）。
 */
@PublicCapability
public interface FishingAccess {

    /** 查询玩家是否正处于钓鱼小游戏中。 */
    boolean isInMinigame(@NotNull UUID playerUuid);

    /**
     * 让玩家开始钓鱼小游戏。
     *
     * @return 是否成功启动（鱼竿/条件不满足时返回 false）
     */
    boolean startMinigame(@NotNull Player player);

    /** 玩家图鉴已解锁的鱼类数量。 */
    int collectionCount(@NotNull UUID playerUuid);

    /** 图鉴鱼类总数。 */
    int totalFishTypes();

    /** 为玩家增加钓鱼经验。 */
    void giveXp(@NotNull UUID playerUuid, int amount);

    /** 查询玩家当前疲劳值。 */
    int fatigue(@NotNull UUID playerUuid);

    /** 设置玩家疲劳值。 */
    void setFatigue(@NotNull UUID playerUuid, int value);

    /**
     * 在服务端主线程提交赛季奖励领取请求，完成结果确认宿主已持久接收奖励。
     *
     * @return 持久交接结果
     */
    @NotNull java.util.concurrent.CompletableFuture<Boolean> claimReward(@NotNull Player player, @NotNull String rewardId);

    /**
     * 卖出玩家背包内全部鱼获。
     *
     * @return 结果提示消息
     */
    @NotNull String sellAllFish(@NotNull Player player);
}
