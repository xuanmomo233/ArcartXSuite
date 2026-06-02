package xuanmo.arcartxsuite.entitytracker.reward;

public record RewardActionResult(
    String type,
    boolean success,
    String message
) {
    public static RewardActionResult ok(String type, String message) {
        return new RewardActionResult(type, true, message);
    }

    public static RewardActionResult fail(String type, String message) {
        return new RewardActionResult(type, false, message);
    }
}
