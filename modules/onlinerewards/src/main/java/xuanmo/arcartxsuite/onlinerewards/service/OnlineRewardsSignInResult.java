package xuanmo.arcartxsuite.onlinerewards.service;

public record OnlineRewardsSignInResult(
    boolean success,
    boolean repeated,
    int streak,
    int total,
    String signInDate,
    int dayOfMonth
) {
}
