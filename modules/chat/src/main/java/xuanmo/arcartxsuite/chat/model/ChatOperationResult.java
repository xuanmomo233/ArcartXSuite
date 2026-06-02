package xuanmo.arcartxsuite.chat.model;

public record ChatOperationResult(
    boolean success,
    String message,
    boolean cardNotified
) {
    public static ChatOperationResult success(String message) {
        return new ChatOperationResult(true, message, false);
    }

    public static ChatOperationResult failure(String message) {
        return new ChatOperationResult(false, message, false);
    }

    public static ChatOperationResult failureCardNotified(String message) {
        return new ChatOperationResult(false, message, true);
    }
}
