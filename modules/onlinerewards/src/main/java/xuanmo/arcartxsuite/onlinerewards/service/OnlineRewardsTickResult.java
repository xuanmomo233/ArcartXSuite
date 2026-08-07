package xuanmo.arcartxsuite.onlinerewards.service;

import java.util.List;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;

/**
 * 在线时长 tick 推进结果。
 * <p>
 * 属于 {@code onlinerewards} 模块，记录一次时长累计后状态是否变更、触发的阶段奖励列表
 * 与最新的进度快照。
 *
 * @param stateChanged      状态是否发生变更
 * @param triggeredRewards  本次触发的阶段奖励列表
 * @param snapshot          推进后的进度快照
 */
public record OnlineRewardsTickResult(
    boolean stateChanged,
    List<OnlineRewardDefinition> triggeredRewards,
    OnlineRewardsProgressSnapshot snapshot
) {
}
