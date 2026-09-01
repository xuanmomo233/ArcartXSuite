package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

/**
 * 时刻奖励定义。
 * <p>
 * 在指定时刻（如每天 20:00）给所有在线玩家发放奖励。
 *
 * @param id            奖励唯一 ID
 * @param enabled       是否启用
 * @param schedule      调度规格（hourly/daily/weekly/interval）
 * @param name          奖励名称（用于日志和广播占位）
 * @param rewardText    奖励文本（用于客户端显示）
 * @param commands      控制台命令列表（支持 {player} 占位）
 * @param mailPresetIds 邮件预设 ID 列表
 * @param itemRewards   物品奖励列表
 * @param broadcastMessage 触发时全服广播消息（空则不广播）
 *
 * @since 1.6.0
 */
public record OnlineRewardsTimeBasedReward(
    String id,
    boolean enabled,
    ScheduleSpec schedule,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    List<OnlineRewardsItemReward> itemRewards,
    String broadcastMessage
) {}
