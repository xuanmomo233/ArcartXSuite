package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsDayOfMonthReward(
    int day,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
