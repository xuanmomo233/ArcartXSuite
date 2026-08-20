package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 签到子系统配置。
 * <p>
 * 属于 {@code onlinerewards} 模块，聚合签到基础奖励、补签、连续/累计里程碑、
 * 每月指定日期、节日、权限组加成等全部签到相关配置。
 *
 * @param reminderOnJoin       玩家进服时是否提醒签到
 * @param signInSuccessMessage 签到成功提示消息
 * @param signInRepeatMessage  重复签到提示消息
 * @param signInReminderMessage 进服签到提醒消息
 * @param baseCommands         每次签到基础执行的命令列表
 * @param baseMailPresetIds    每次签到基础发送的邮件预设 id 列表
 * @param baseRewardText       基础签到奖励描述文本
 * @param baseItemRewards      每次签到基础通过 ItemSourceRegistry 发放的物品奖励列表
 * @param makeup               补签功能配置
 * @param streakRewards        连续签到里程碑奖励列表
 * @param totalRewards         累计签到里程碑奖励列表
 * @param dayOfMonthRewards    每月指定日期奖励列表
 * @param holidayRewards       节日奖励列表
 * @param permissionBonusGroups 权限组加成奖励列表
 */
public record OnlineRewardsSignInConfiguration(
    boolean reminderOnJoin,
    String signInSuccessMessage,
    String signInRepeatMessage,
    String signInReminderMessage,
    List<String> baseCommands,
    List<String> baseMailPresetIds,
    String baseRewardText,
    List<OnlineRewardsItemReward> baseItemRewards,
    OnlineRewardsMakeupConfiguration makeup,
    List<OnlineRewardsMilestoneReward> streakRewards,
    List<OnlineRewardsMilestoneReward> totalRewards,
    List<OnlineRewardsDayOfMonthReward> dayOfMonthRewards,
    List<OnlineRewardsHolidayReward> holidayRewards,
    List<OnlineRewardsPermissionBonusReward> permissionBonusGroups
) {
}
