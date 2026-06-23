package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 周期累计在线时长奖励定义（周/月）。
 */
public record OnlineRewardsPeriodicReward(
    String id,
    int minutes,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    boolean repeat
) {
}
