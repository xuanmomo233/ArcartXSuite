package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * OnlineRewards 对外暴露的查询/操作能力（公开 capability）。
 * <p>
 * 由 OnlineRewards 模块实现，供其他模块跨模块查询签到状态、在线时长，
 * 或触发签到/补签/发放奖励。
 * <p>
 * 公开 capability：外部插件可经 {@code AxsCapabilities.get(OnlineRewardsQueryable.class)} 获取。
 * <p>
 * UUID 查询从后台读取已提交的数据。模块停用、读取队列已满或存储失败时异常完成。
 * 可在主线程提交查询，通过回调处理结果。回调需要操作 Bukkit 对象时应切回主线程。
 */
@PublicCapability
public interface OnlineRewardsQueryable {

    /**
     * 查询玩家今日是否有已提交的签到记录。
     */
    @NotNull CompletionStage<Boolean> hasSignedToday(@NotNull UUID playerUuid);

    /**
     * 查询玩家今日已保存的在线分钟数。
     */
    @NotNull CompletionStage<Integer> todayOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 查询玩家本周已保存的在线分钟数。
     */
    @NotNull CompletionStage<Integer> weeklyOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 查询玩家本月已保存的在线分钟数。
     */
    @NotNull CompletionStage<Integer> monthlyOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 为玩家执行签到（如果当天未签到）。
     *
     * 在主线程提交，事务确认后完成。已签到返回 false。
     */
    @NotNull CompletionStage<Boolean> signIn(@NotNull Player player);

    /**
     * 为玩家执行补签（指定日期必须是本月今天之前）。
     *
     * 在主线程提交，补签记录、扣卡和奖励计划一同提交后返回 true。
     */
    @NotNull CompletionStage<Boolean> makeupSignIn(@NotNull Player player, @NotNull String date);

    /**
     * 为玩家增加补签卡数量。
     * 在主线程提交，数量已保存时返回 true。
     */
    @NotNull CompletionStage<Boolean> addMakeupCards(@NotNull Player player, int amount);

    /**
     * 为玩家发放今日在线阶段奖励（如果已达到阶段）。
     * 在主线程提交。阶段进度和待发计划确认后返回 true，无可领取的阶段时返回 false。
     */
    @NotNull CompletionStage<Boolean> grantDailyRewards(@NotNull Player player);

    /**
     * 给玩家增加在线时长（可用于联动活动或 GM 补偿）。
     * 在主线程提交。在线时长和待发奖励提交后完成，存储结果不明时异常完成。
     */
    @NotNull CompletionStage<Boolean> addOnlineMinutes(@NotNull Player player, int minutes);
}
