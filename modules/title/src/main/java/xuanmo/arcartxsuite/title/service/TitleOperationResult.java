package xuanmo.arcartxsuite.title.service;

public record TitleOperationResult(boolean success, String message) {
    public static TitleOperationResult success(String message) {
        return new TitleOperationResult(true, message);
    }

    public static TitleOperationResult failure(String message) {
        return new TitleOperationResult(false, message);
    }
}
