package xuanmo.arcartxsuite.map.model;

public record MapOperationResult(boolean success, String message) {

    public static MapOperationResult success(String message) {
        return new MapOperationResult(true, message);
    }

    public static MapOperationResult failure(String message) {
        return new MapOperationResult(false, message);
    }
}
