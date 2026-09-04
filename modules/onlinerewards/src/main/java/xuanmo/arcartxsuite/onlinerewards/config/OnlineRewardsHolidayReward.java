package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 节日签到奖励定义。
 * <p>
 * 属于 {@code onlinerewards} 模块签到子系统，当玩家在指定 {@link #month} 月 {@link #day} 日签到时
 * 额外发放的节日奖励。
 *
 * @param month        节日所在月份（1-12）
 * @param day          节日所在日期（1-31）
 * @param name         节日名称，用于 UI 展示
 * @param rewardText   奖励描述文本，为空时由系统自动生成摘要
 * @param commands     触发时执行的命令列表
 * @param mailPresetIds 触发时发送的邮件预设 id 列表
 * @param itemRewards   触发时通过 ItemSourceRegistry 发放的物品奖励列表
 * @param icon          UI 展示图标定义，为 null 时使用默认图标
 */
public record OnlineRewardsHolidayReward(
    int month,
    int day,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds,
    List<OnlineRewardsItemReward> itemRewards,
    @Nullable IconDefinition icon
) {
}
