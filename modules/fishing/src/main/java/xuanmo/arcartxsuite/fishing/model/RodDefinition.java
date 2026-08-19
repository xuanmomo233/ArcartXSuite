package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;

/**
 * 自定义钓竿定义。
 * <p>
 * 钓竿属性通过 PDC 标签写入鱼竿物品，影响小游戏参数。
 * 鱼竿识别仅依赖 PDC 标签 axs_fishing_rod_id，不绑定具体物品来源。
 * minPlayerLevel / maxPlayerLevel 用于限制玩家使用该鱼竿的赛季等级区间。
 */
public record RodDefinition(
    @NotNull String id,
    double treasureChanceBonus,
    int greenBarHeightBonus,
    int catchDurationBonus,
    double expMultiplier,
    int minPlayerLevel,
    int maxPlayerLevel
) {

    public static final RodDefinition DEFAULT = new RodDefinition(
        "default",
        0.0, 0, 0, 1.0, 0, 0
    );
}