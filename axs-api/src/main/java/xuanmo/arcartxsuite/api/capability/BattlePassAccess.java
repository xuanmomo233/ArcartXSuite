package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 战斗通行证公开能力接口（公开 capability）。
 * <p>
 * 由 BattlePass 模块实现，供外部插件调整玩家战令经验/等级/档位、
 * 推进任务进度或触发领奖。写操作在后台执行，成功结果在对应存储提交后完成。
 * 回调线程不保证是 Bukkit 主线程；涉及 Bukkit 的后续操作请回主线程。
 * 同一玩家的并发请求按入队顺序应用，允许合并提交；结果在合并后的状态保存后完成。
 * 初始化未完成或队列满时会返回失败结果，此时不会执行该请求。
 * 取消或等待超时不撤销已接收的写入；增减经验等操作不能在结果不明时盲目重试。
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
    CompletionStage<Integer> addXp(@NotNull Player player, int xp);

    /** 直接设置玩家战令经验。 */
    CompletionStage<Void> setXp(@NotNull Player player, int xp);

    /** 扣减玩家战令经验。 */
    CompletionStage<Void> removeXp(@NotNull Player player, int xp);

    /** 直接设置玩家战令等级。 */
    CompletionStage<Void> setLevel(@NotNull Player player, int level);

    /**
     * 解锁玩家战令档位。
     *
     * @param tier 档位名：{@code "PREMIUM"} / {@code "DELUXE"}（大小写不敏感）
     * @return 是否成功
     */
    CompletionStage<Boolean> unlockTier(@NotNull Player player, @NotNull String tier);

    /**
     * 一键领取玩家全部可领奖励。
     *
     * @return 已确认写入宿主持久待交付队列的奖励份数，实际发放由宿主处理；
     *         尚未确认的条目会使结果失败，已入队条目按持久请求号去重
     */
    CompletionStage<Integer> claimAllRewards(@NotNull Player player);

    /**
     * 为玩家推进指定任务进度。
     *
     * @param taskId    任务 id
     * @param increment 进度增量
     */
    CompletionStage<Void> updateTaskProgress(@NotNull Player player, @NotNull String taskId, int increment);

    /** 重置玩家本赛季进度（等级/经验/任务/领取记录）。 */
    CompletionStage<Void> resetProgress(@NotNull UUID playerUuid);
}
