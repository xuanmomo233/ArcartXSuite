package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardDefinition(
    int minutes,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
