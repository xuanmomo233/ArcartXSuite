package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 全服签到目标单项定义。
 */
public record OnlineRewardsServerSignInGoalTarget(
    String id,
    int required,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    List<String> chatCardIds,
    List<String> subtitleGroupIds,
    List<String> titleIds,
    String broadcastMessage
) {
}
