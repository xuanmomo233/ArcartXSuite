package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 战斗通行证公开能力接口（公开 capability）。
 * <p>
 * 由 BattlePass 模块实现，供外部插件调整玩家战令经验/等级/档位、
 * 推进任务进度或触发领奖。所有方法请在 Bukkit 主线程调用。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(BattlePassAccess.class)} 获取。
 */
@PublicCapability
public interface BattlePassAccess {

    /** 当前赛季是否进行中。 */
    boolean isSeasonActive();

    /**
     * 为玩家增加战令经验。
     *
     * @return 实际增加后的总经验
     */
    int addXp(@NotNull Player player, int xp);

    /** 直接设置玩家战令经验。 */
    void setXp(@NotNull Player player, int xp);

    /** 扣减玩家战令经验。 */
    void removeXp(@NotNull Player player, int xp);

    /** 直接设置玩家战令等级。 */
    void setLevel(@NotNull Player player, int level);

    /**
     * 解锁玩家战令档位。
     *
     * @param tier 档位名：{@code "PREMIUM"} / {@code "DELUXE"}（大小写不敏感）
     * @return 是否成功
     */
    boolean unlockTier(@NotNull Player player, @NotNull String tier);

    /**
     * 一键领取玩家全部可领奖励。
     *
     * @return 领取的奖励份数
     */
    int claimAllRewards(@NotNull Player player);

    /**
     * 为玩家推进指定任务进度。
     *
     * @param taskId    任务 id
     * @param increment 进度增量
     */
    void updateTaskProgress(@NotNull Player player, @NotNull String taskId, int increment);

    /** 重置玩家本赛季进度（等级/经验/任务/领取记录）。 */
    void resetProgress(@NotNull UUID playerUuid);
}
