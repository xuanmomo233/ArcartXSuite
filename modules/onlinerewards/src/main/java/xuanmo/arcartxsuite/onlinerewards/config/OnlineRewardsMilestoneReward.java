package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 签到里程碑奖励定义。
 * <p>
 * 属于 {@code onlinerewards} 模块签到子系统，当玩家连续签到天数或累计签到天数达到 {@link #days} 时
 * 额外发放的里程碑奖励。
 *
 * @param days          触发该奖励所需的天数阈值
 * @param rewardText    奖励描述文本，为空时由系统自动生成摘要
 * @param commands      触发时执行的命令列表
 * @param mailPresetIds 触发时发送的邮件预设 id 列表
 * @param itemRewards   触发时通过 ItemSourceRegistry 发放的物品奖励列表
 */
public record OnlineRewardsMilestoneReward(
    int days,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    List<OnlineRewardsItemReward> itemRewards
) {
}
