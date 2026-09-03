package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.item.ItemMatcher;

/**
 * 自定义钓竿定义。
 * <p>
 * 钓竿物品由外部物品库（NeigeItems/MythicMobs 等）生成，配置文件仅用于识别物品和设置效果。
 * 识别方式：优先用 {@link #matcher()}（ItemMatcher）匹配玩家手持物品，未配置 matcher 时回退到 PDC 标签。
 * minPlayerLevel / maxPlayerLevel 用于限制玩家使用该鱼竿的赛季等级区间。
 */
public record RodDefinition(
    @NotNull String id,
    double treasureChanceBonus,
    int greenBarHeightBonus,
    int catchDurationBonus,
    double expMultiplier,
    int minPlayerLevel,
    int maxPlayerLevel,
    @Nullable ItemMatcher matcher
) {

    public static final RodDefinition DEFAULT = new RodDefinition(
        "default",
        0.0, 0, 0, 1.0, 0, 0, null
    );
}