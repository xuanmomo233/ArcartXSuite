package xuanmo.arcartxsuite.onlinerewards.service;

import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public record OnlineRewardsPlayerSnapshot(
    OnlineRewardsPlayerState state,
    OnlineRewardsProgressSnapshot progress,
    boolean signedToday
) {
}
