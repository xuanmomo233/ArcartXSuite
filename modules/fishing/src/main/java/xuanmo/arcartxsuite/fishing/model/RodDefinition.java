package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;

/**
 * 自定义钓竿定义。
 * <p>
 * 钓竿属性通过 PDC 标签写入鱼竿物品，影响小游戏参数。
 */
public record RodDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull FishingItemRef itemRef,
    double treasureChanceBonus,
    int greenBarHeightBonus,
    int catchDurationBonus,
    double expMultiplier,
    int minPlayerLevel
) {

    public static final RodDefinition DEFAULT = new RodDefinition(
        "default", "默认钓竿", FishingItemRef.EMPTY,
        0.0, 0, 0, 1.0, 0
    );
}