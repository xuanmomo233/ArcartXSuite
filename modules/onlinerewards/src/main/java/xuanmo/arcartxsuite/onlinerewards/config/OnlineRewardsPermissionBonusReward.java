package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 权限组签到加成奖励定义。
 * <p>
 * 属于 {@code onlinerewards} 模块签到子系统，拥有 {@link #permission} 权限的玩家签到时
 * 按 {@link #priority} 优先级匹配后额外发放的奖励。
 *
 * @param permission    触发该奖励所需的权限节点
 * @param priority      优先级，数值越大越优先匹配
 * @param rewardText    奖励描述文本，为空时由系统自动生成摘要
 * @param commands      触发时执行的命令列表
 * @param mailPresetIds 触发时发送的邮件预设 id 列表
 */
public record OnlineRewardsPermissionBonusReward(
    String permission,
    int priority,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
