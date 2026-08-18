package xuanmo.arcartxsuite.fishing.settlement;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 赛季结算条目定义。
 *
 * @param id          条目唯一标识
 * @param condition   ARIA 表达式条件
 * @param description 描述
 * @param rewards     奖励列表（复用 SeasonRewardDefinition 结构）
 */
public record SettlementDefinition(
    @NotNull String id,
    @NotNull String condition,
    @NotNull String description,
    @NotNull List<xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition> rewards
) {
}
