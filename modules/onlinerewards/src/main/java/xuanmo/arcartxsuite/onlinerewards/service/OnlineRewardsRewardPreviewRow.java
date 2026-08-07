package xuanmo.arcartxsuite.onlinerewards.service;

/**
 * 奖励预览行。
 * <p>
 * 属于 {@code onlinerewards} 模块，用于在签到日历选中日期的奖励预览列表中展示单行奖励信息。
 *
 * @param id         奖励标识
 * @param title      奖励标题
 * @param trigger    触发条件描述
 * @param rewardText 奖励描述文本
 */
public record OnlineRewardsRewardPreviewRow(
    String id,
    String title,
    String trigger,
    String rewardText
) {
}
