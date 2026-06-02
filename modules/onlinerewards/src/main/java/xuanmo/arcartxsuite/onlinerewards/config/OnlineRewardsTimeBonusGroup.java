package xuanmo.arcartxsuite.onlinerewards.config;

public record OnlineRewardsTimeBonusGroup(
    String permission,
    double multiplier,
    int priority
) {
}
