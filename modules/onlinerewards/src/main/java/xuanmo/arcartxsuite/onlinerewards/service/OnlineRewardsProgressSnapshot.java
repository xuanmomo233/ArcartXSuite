package xuanmo.arcartxsuite.onlinerewards.service;

/**
 * 在线时长阶段进度快照。
 * <p>
 * 属于 {@code onlinerewards} 模块，描述当前阶段奖励的进度比例、名称与是否全部完成。
 *
 * @param progress  进度比例（0.0-1.0）
 * @param title     当前阶段名称，全部完成时为完成提示
 * @param completed 是否已领取全部阶段奖励
 */
public record OnlineRewardsProgressSnapshot(
    float progress,
    String title,
    boolean completed
) {
}
