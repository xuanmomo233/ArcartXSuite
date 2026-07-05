package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;

public record TreasureDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull FishingItemRef itemRef,
    double chance
) {
}