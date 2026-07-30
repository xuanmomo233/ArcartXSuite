package xuanmo.arcartxsuite.bridge;

import java.util.Locale;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.manager.ItemManager;
import net.Indyuce.mmoitems.manager.TypeManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoItemsBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private TypeManager typeManager;
    private ItemManager itemManager;

    public MmoItemsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        typeManager = null;
        itemManager = null;

        Plugin mmoItems = plugin.getServer().getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) {
            return;
        }

        try {
            MMOItems mmoPlugin = MMOItems.plugin;
            if (mmoPlugin == null) {
                return;
            }
            typeManager = mmoPlugin.getTypes();
            itemManager = mmoPlugin.getItems();
            available = typeManager != null && itemManager != null;
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 MMOItems 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack getItemStack(String typeId, String itemId, int amount) {
        if (!available || typeManager == null || itemManager == null || blank(typeId) || blank(itemId)) {
            return null;
        }
        try {
            Type type = typeManager.get(normalize(typeId));
            if (type == null) {
                type = typeManager.get(typeId.trim());
            }
            if (type == null) {
                return null;
            }
            // 先尝试直接获取 ItemStack
            ItemStack direct = itemManager.getItem(type, itemId);
            if (direct != null && !direct.getType().isAir()) {
                ItemStack cloned = direct.clone();
                cloned.setAmount(Math.max(1, amount));
                return cloned;
            }
            // 回退到 MMOItem -> builder -> build
            MMOItem mmoItem = itemManager.getMMOItem(type, itemId);
            if (mmoItem == null) {
                return null;
            }
            ItemStackBuilder builder = mmoItem.newBuilder();
            ItemStack built = builder.build();
            if (built == null || built.getType().isAir()) {
                return null;
            }
            ItemStack cloned = built.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (Exception | LinkageError exception) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
