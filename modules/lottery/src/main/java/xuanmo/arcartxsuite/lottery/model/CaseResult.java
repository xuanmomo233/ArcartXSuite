package xuanmo.arcartxsuite.lottery.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CaseResult(
    @Nullable PoolItem item,
    @NotNull String rarity,
    boolean stattrak,
    @NotNull String wearTier,
    double wearValue
) {
}
