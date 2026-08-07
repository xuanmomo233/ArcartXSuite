package xuanmo.arcartxsuite.entitytracker.boss.tracker;

/**
 * 单个奖励动作的执行结果，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 记录奖励动作类型、执行是否成功以及结果消息。
 *
 * @param type    奖励动作类型名称
 * @param success 是否执行成功
 * @param message 结果消息
 */
public record BossDamageSettlementActionResult(
    String type,
    boolean success,
    String message
) {
}

