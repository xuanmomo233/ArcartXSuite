package xuanmo.arcartxsuite.fishing.model;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

public record BaitDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull FishingItemRef itemRef,
    boolean isDefault,
    @NotNull Map<String, Double> fishAttractModifiers,
    double treasureChanceBoost,
    int maxDurabilityBonus
) {

    public double attractModifier(@NotNull String fishId) {
        return fishAttractModifiers.getOrDefault(fishId, 1.0);
    }
}