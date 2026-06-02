package xuanmo.arcartxsuite.mail.model;

public record MailOperationResult(boolean success, String message) {
    public static MailOperationResult success(String message) {
        return new MailOperationResult(true, message);
    }

    public static MailOperationResult failure(String message) {
        return new MailOperationResult(false, message);
    }
}
