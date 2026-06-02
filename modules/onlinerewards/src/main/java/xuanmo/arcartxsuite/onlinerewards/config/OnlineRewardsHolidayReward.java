package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsHolidayReward(
    int month,
    int day,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
