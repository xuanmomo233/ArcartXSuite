package xuanmo.arcartxsuite.onlinerewards.config;

/**
 * 在线时长倍率权限组定义。
 * <p>
 * 属于 {@code onlinerewards} 模块，拥有 {@link #permission} 权限的玩家在线时长累计时
 * 按 {@link #multiplier} 倍率加成，{@link #priority} 决定多组匹配时的优先级。
 *
 * @param permission 触发该倍率所需的权限节点
 * @param multiplier 在线时长倍率（不小于 1.0）
 * @param priority   优先级，数值越大越优先匹配
 */
public record OnlineRewardsTimeBonusGroup(
    String permission,
    double multiplier,
    int priority
) {
}
