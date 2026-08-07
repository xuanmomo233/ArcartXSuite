package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

/**
 * 在线时长阶段奖励定义。
 * <p>
 * 属于 {@code onlinerewards} 模块，描述当玩家当日在线时长达到 {@link #minutes} 分钟时触发的奖励：
 * 可执行命令列表、发送邮件预设，以及在 UI 中展示的奖励文本。
 *
 * @param minutes       触发该阶段奖励所需的在线分钟数
 * @param name          奖励阶段名称，用于 UI 展示
 * @param rewardText    奖励描述文本，为空时由系统自动生成摘要
 * @param commands      触发时执行的命令列表
 * @param mailPresetIds 触发时发送的邮件预设 id 列表
 */
public record OnlineRewardDefinition(
    int minutes,
    String name,
    String rewardText,
    List<String> commands,
    List<String> mailPresetIds
) {
}
