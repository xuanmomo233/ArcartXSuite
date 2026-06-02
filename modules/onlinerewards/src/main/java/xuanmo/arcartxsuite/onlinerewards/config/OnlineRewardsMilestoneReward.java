package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsMilestoneReward(
    int days,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
