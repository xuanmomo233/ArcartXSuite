package xuanmo.arcartxsuite.market.shop;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

/** 市场商品的统一物品来源解析器。 */
public final class ShopItemFactory {
    private ShopItemFactory() {
    }

    /**
     * 根据结构化物品定义生成物品。
     *
     * @param registry 物品来源注册表
     * @param definition 物品定义
     * @param amount 数量
     * @param logger 日志记录器
     * @return 生成的物品；来源无效时返回 {@code null}
     */
    public static @Nullable ItemStack create(
            @Nullable ItemSourceRegistry registry,
            @Nullable ShopService.ItemDefinition definition,
            int amount,
            Logger logger
    ) {
        if (definition == null || definition.id() == null || definition.id().isBlank() || amount <= 0) {
            return null;
        }
        try {
            String source = definition.source().toLowerCase(java.util.Locale.ROOT);
            ItemStack item = switch (source) {
                case "mythic", "mythicmobs" -> registry == null ? null
                    : registry.generateMythicItem(definition.id(), amount);
                case "neige", "neigeitems" -> registry == null ? null
                    : registry.generateNeigeItem(definition.id(), amount);
                case "overture" -> registry == null ? null
                    : registry.generateOvertureItem(definition.id(), null, amount);
                case "mmoitems", "mmo" -> {
                    if (registry == null) yield null;
                    String[] parts = definition.id().split(";", 2);
                    yield parts.length == 2
                        ? registry.generateMmoItem(parts[0], parts[1], amount) : null;
                }
                default -> {
                    Material material = Material.matchMaterial(definition.id(), true);
                    ItemStack base = material == null ? null : new ItemStack(material, amount);
                    yield applyItemNbt(base, definition.nbt());
                }
            };
            return item;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Market-Shop] 商品物品生成失败: "
                + definition.source() + ":" + definition.id(), exception);
            return null;
        }
    }

    private static @Nullable ItemStack applyItemNbt(@Nullable ItemStack item, @Nullable String nbt) {
        if (item == null || nbt == null || nbt.isBlank()) return item;
        try {
            return org.bukkit.Bukkit.getUnsafe().modifyItemStack(item.clone(), nbt);
        } catch (Exception ignored) {
            return item;
        }
    }
}
