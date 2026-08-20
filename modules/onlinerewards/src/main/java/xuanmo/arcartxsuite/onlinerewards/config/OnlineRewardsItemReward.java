package xuanmo.arcartxsuite.onlinerewards.config;

import org.jetbrains.annotations.Nullable;

/**
 * 物品奖励定义，描述通过 {@link xuanmo.arcartxsuite.api.item.ItemSourceRegistry} 生成的物品。
 * <p>
 * 关联模块：onlinerewards。配置格式与 battlepass/fishing/mail/afkreward 模块统一，
 * 支持 minecraft 原版材质和 MythicMobs/NeigeItems/Overture/MMOItems 外部物品来源。
 *
 * @param source 物品来源（minecraft / mythic / neige / overture / mmoitems），默认 minecraft
 * @param id     物品 ID（原版材质名或外部物品库 ID；mmoitems 格式为 "类型ID;物品ID"）
 * @param amount 物品数量
 * @param nbt    自定义 NBT（SNBT 字符串，仅 minecraft 源有效），可为 null
 */
public record OnlineRewardsItemReward(
    String source,
    String id,
    int amount,
    @Nullable String nbt
) {
    public OnlineRewardsItemReward {
        source = (source == null || source.isBlank()) ? "minecraft" : source;
        id = id == null ? "" : id;
        amount = Math.max(1, amount);
    }
}
