package xuanmo.arcartxsuite.chat.model;

/**
 * 聊天操作结果，封装操作是否成功、反馈消息及是否已通过聊天卡片通知玩家。
 * <p>
 * 提供工厂方法 {@link #success}、{@link #failure}、{@link #failureCardNotified}
 * 供 {@link ChatService} 统一构造结果。
 *
 * @param success       操作是否成功
 * @param message       反馈消息文本
 * @param cardNotified  失败时是否已通过聊天卡片通知玩家（为 true 时命令端不再重复发送文本提示）
 */
public record ChatOperationResult(
    boolean success,
    String message,
    boolean cardNotified
) {
    /**
     * 构造成功结果。
     *
     * @param message 成功反馈消息
     * @return 成功结果实例
     */
    public static ChatOperationResult success(String message) {
        return new ChatOperationResult(true, message, false);
    }

    /**
     * 构造失败结果（未通过卡片通知）。
     *
     * @param message 失败反馈消息
     * @return 失败结果实例
     */
    public static ChatOperationResult failure(String message) {
        return new ChatOperationResult(false, message, false);
    }

    /**
     * 构造失败结果（已通过聊天卡片通知玩家，命令端无需重复提示）。
     *
     * @param message 失败反馈消息
     * @return 失败结果实例（cardNotified = true）
     */
    public static ChatOperationResult failureCardNotified(String message) {
        return new ChatOperationResult(false, message, true);
    }
}
