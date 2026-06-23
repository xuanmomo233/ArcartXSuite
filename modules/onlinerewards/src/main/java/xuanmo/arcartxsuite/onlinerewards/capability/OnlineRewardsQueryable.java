package xuanmo.arcartxsuite.onlinerewards.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * OnlineRewards 对外暴露的查询/操作能力。
 * <p>
 * 由 OnlineRewards 模块实现，供其他模块跨模块查询签到状态、在线时长，
 * 或触发签到/补签/发放奖励。
 */
public interface OnlineRewardsQueryable {

    /**
     * 查询玩家今日是否已签到。
     */
    boolean hasSignedToday(@NotNull UUID playerUuid);

    /**
     * 查询玩家今日已在线分钟数。
     */
    int todayOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 查询玩家本周已在线分钟数。
     */
    int weeklyOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 查询玩家本月已在线分钟数。
     */
    int monthlyOnlineMinutes(@NotNull UUID playerUuid);

    /**
     * 为玩家执行签到（如果当天未签到）。
     *
     * @return 是否成功签到（已签到返回 false）
     */
    boolean signIn(@NotNull Player player);

    /**
     * 为玩家执行补签（指定日期必须是本月今天之前）。
     *
     * @return 是否成功补签
     */
    boolean makeupSignIn(@NotNull Player player, @NotNull String date);

    /**
     * 为玩家增加补签卡数量。
     */
    boolean addMakeupCards(@NotNull Player player, int amount);

    /**
     * 为玩家发放今日在线阶段奖励（如果已达到阶段）。
     */
    void grantDailyRewards(@NotNull Player player);

    /**
     * 给玩家增加在线时长（可用于联动活动或 GM 补偿）。
     */
    boolean addOnlineMinutes(@NotNull Player player, int minutes);
}
