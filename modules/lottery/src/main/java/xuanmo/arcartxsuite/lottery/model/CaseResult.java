package xuanmo.arcartxsuite.lottery.model;

import org.jetbrains.annotations.NotNull;

public record CaseResult(
    @NotNull PoolItem item,
    @NotNull String rarity,
    boolean stattrak,
    @NotNull String wearTier,
    double wearValue
) {
}
