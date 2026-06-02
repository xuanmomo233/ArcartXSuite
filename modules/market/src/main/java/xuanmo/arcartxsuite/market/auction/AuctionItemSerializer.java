package xuanmo.arcartxsuite.market.auction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 物品序列化/反序列化接口。
 * 由模块入口注入具体实现（使用 ItemBridgeAPI）。
 */
public interface AuctionItemSerializer {

    /**
     * 序列化 ItemStack 为字符串（Base64/JSON/NBT）。
     */
    String serialize(ItemStack item);

    /**
     * 反序列化字符串为 ItemStack。
     */
    @Nullable ItemStack deserialize(String data);
}
