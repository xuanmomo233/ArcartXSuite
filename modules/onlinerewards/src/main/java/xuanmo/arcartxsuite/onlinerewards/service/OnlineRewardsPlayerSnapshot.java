package xuanmo.arcartxsuite.onlinerewards.service;

import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

/**
 * 玩家在线奖励快照。
 * <p>
 * 属于 {@code onlinerewards} 模块，聚合玩家运行时状态、当前阶段进度与今日是否已签到，
 * 用于命令展示与菜单数据构建。
 *
 * @param state      玩家运行时状态
 * @param progress   当前阶段进度快照
 * @param signedToday 今日是否已签到
 */
public record OnlineRewardsPlayerSnapshot(
    OnlineRewardsPlayerState state,
    OnlineRewardsProgressSnapshot progress,
    boolean signedToday
) {
}
