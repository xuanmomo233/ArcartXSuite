package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 全服签到目标/集体奖励配置。
 */
public record OnlineRewardsServerSignInGoalConfiguration(
    boolean enabled,
    boolean broadcast,
    List<OnlineRewardsServerSignInGoalTarget> targets
) {
}
