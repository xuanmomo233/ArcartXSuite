package xuanmo.arcartxsuite.onlinerewards.service;

import java.util.List;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;

public record OnlineRewardsTickResult(
    boolean stateChanged,
    List<OnlineRewardDefinition> triggeredRewards,
    OnlineRewardsProgressSnapshot snapshot
) {
}
