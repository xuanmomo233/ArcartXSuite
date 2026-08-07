package xuanmo.arcartxsuite.map.model;

/**
 * 地图操作结果记录，封装单次操作的成败状态与反馈消息。
 * <p>
 * 关联模块：map。由 {@code MapService} 的各操作方法返回，
 * 供命令层和 UI 包处理器向玩家展示操作结果。
 *
 * @param success 操作是否成功
 * @param message 反馈消息键或文本
 */
public record MapOperationResult(boolean success, String message) {

    /**
     * 构造一个成功的操作结果。
     *
     * @param message 反馈消息
     * @return 成功结果实例
     */
    public static MapOperationResult success(String message) {
        return new MapOperationResult(true, message);
    }

    /**
     * 构造一个失败的操作结果。
     *
     * @param message 反馈消息
     * @return 失败结果实例
     */
    public static MapOperationResult failure(String message) {
        return new MapOperationResult(false, message);
    }
}
