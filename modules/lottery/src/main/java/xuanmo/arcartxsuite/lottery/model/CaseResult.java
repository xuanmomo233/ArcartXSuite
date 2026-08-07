package xuanmo.arcartxsuite.lottery.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * CASE 开箱单次结果。
 *
 * @param item   选中的奖品，无奖品时为 {@code null}
 * @param rarity 选中稀有度名称
 */
public record CaseResult(
    @Nullable PoolItem item,
    @NotNull String rarity
) {
}
