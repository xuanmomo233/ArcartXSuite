package xuanmo.arcartxsuite.market.shop;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
     * @param player 玩家上下文（Overture 等需要），可为 null
     * @param logger 日志记录器
     * @return 生成的物品；来源无效时返回 {@code null}
     */
    public static @Nullable ItemStack create(
            @Nullable ItemSourceRegistry registry,
            @Nullable ShopService.ItemDefinition definition,
            int amount,
            @Nullable Player player,
            Logger logger
    ) {
        if (definition == null || definition.id() == null || definition.id().isBlank() || amount <= 0) {
            return null;
        }
        if (registry == null) {
            logger.warning("[Market-Shop] ItemSourceRegistry 不可用，无法生成物品: "
                + definition.source() + ":" + definition.id());
            return null;
        }
        try {
            return registry.generateItem(definition.source(), definition.id(), amount, definition.nbt(), player);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Market-Shop] 商品物品生成失败: "
                + definition.source() + ":" + definition.id(), exception);
            return null;
        }
    }
}
