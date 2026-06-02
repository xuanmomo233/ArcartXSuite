package xuanmo.arcartxsuite.entitytracker.boss.tracker;

public record BossDamageRewardDispatchResult(
    boolean success,
    String message
) {

    public static BossDamageRewardDispatchResult success(String message) {
        return new BossDamageRewardDispatchResult(true, message);
    }

    public static BossDamageRewardDispatchResult failure(String message) {
        return new BossDamageRewardDispatchResult(false, message);
    }
}

