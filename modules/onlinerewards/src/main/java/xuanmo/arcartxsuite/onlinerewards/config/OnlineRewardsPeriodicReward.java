package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

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
    boolean repeat,
    List<OnlineRewardsItemReward> itemRewards,
    @Nullable IconDefinition icon
) {
}
