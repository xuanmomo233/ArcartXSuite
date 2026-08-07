package xuanmo.arcartxsuite.onlinerewards.service;

/**
 * 在线奖励操作结果。
 * <p>
 * 属于 {@code onlinerewards} 模块，表示一次管理或玩家操作（如调整时长、签到、补签）的成败与提示消息。
 *
 * @param success 操作是否成功
 * @param message 面向玩家的提示消息
 */
public record OnlineRewardsOperationResult(
    boolean success,
    String message
) {
}
