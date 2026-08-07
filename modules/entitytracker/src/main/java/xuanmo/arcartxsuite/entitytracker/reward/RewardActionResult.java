package xuanmo.arcartxsuite.entitytracker.reward;

/**
 * 奖励动作执行结果，属于 entitytracker 模块的奖励子系统。
 * <p>
 * 记录奖励动作类型、执行是否成功以及结果消息。
 *
 * @param type    奖励动作类型名称
 * @param success 是否执行成功
 * @param message 结果消息
 */
public record RewardActionResult(
    String type,
    boolean success,
    String message
) {
    /**
     * 构造成功结果。
     *
     * @param type    奖励动作类型名称
     * @param message 成功消息
     * @return 成功结果实例
     */
    public static RewardActionResult ok(String type, String message) {
        return new RewardActionResult(type, true, message);
    }

    /**
     * 构造失败结果。
     *
     * @param type    奖励动作类型名称
     * @param message 失败消息
     * @return 失败结果实例
     */
    public static RewardActionResult fail(String type, String message) {
        return new RewardActionResult(type, false, message);
    }
}
