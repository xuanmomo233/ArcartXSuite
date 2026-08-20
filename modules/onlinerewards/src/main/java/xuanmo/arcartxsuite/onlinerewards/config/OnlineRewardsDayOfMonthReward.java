package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 每月指定日期签到奖励定义。
 * <p>
 * 属于 {@code onlinerewards} 模块签到子系统，当玩家在当月 {@link #day} 日签到时额外发放的奖励。
 *
 * @param day          触发该奖励的当月日期（1-31）
 * @param rewardText   奖励描述文本，为空时由系统自动生成摘要
 * @param commands     触发时执行的命令列表
 * @param mailPresetIds 触发时发送的邮件预设 id 列表
 * @param itemRewards   触发时通过 ItemSourceRegistry 发放的物品奖励列表
 */
public record OnlineRewardsDayOfMonthReward(
    int day,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    List<OnlineRewardsItemReward> itemRewards
) {
}
