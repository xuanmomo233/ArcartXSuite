package xuanmo.arcartxsuite.battlepass.model;

import org.jetbrains.annotations.Nullable;

/**
 * 物品定义 record，描述战令奖励中 type: item 的物品来源。
 * <p>
 * 与 market 模块的 {@code ShopService.ItemDefinition} 结构一致，
 * 支持 minecraft 原版材质和 MythicMobs/NeigeItems/Overture/MMOItems 外部物品来源。
 *
 * @param source 物品来源（minecraft / mythic / neige / overture / mmoitems），默认 minecraft
 * @param id     物品 ID（原版材质名或外部物品库 ID）
 * @param amount 物品数量
 * @param nbt    自定义 NBT（SNBT 字符串），可为 null
 */
public record BattlePassItemDefinition(
    String source,
    String id,
    int amount,
    @Nullable String nbt
) {
    public BattlePassItemDefinition {
        source = (source == null || source.isBlank()) ? "minecraft" : source;
        id = id == null ? "" : id;
        amount = Math.max(1, amount);
    }
}
