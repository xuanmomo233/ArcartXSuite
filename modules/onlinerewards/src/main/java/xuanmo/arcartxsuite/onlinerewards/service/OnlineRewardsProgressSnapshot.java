package xuanmo.arcartxsuite.onlinerewards.service;

public record OnlineRewardsProgressSnapshot(
    float progress,
    String title,
    boolean completed
) {
}
