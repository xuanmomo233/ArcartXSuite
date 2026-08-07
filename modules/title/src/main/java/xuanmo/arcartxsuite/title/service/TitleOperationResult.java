package xuanmo.arcartxsuite.title.service;

/**
 * 称号操作结果。
 * <p>
 * 封装称号授予/回收等操作的执行状态与反馈消息。
 *
 * @param success 操作是否成功
 * @param message 反馈消息
 */
public record TitleOperationResult(boolean success, String message) {
    /**
     * @param message 成功消息
     * @return 表示成功的操作结果
     */
    public static TitleOperationResult success(String message) {
        return new TitleOperationResult(true, message);
    }

    /**
     * @param message 失败消息
     * @return 表示失败的操作结果
     */
    public static TitleOperationResult failure(String message) {
        return new TitleOperationResult(false, message);
    }
}
