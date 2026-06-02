package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsPermissionBonusReward(
    String permission,
    int priority,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
