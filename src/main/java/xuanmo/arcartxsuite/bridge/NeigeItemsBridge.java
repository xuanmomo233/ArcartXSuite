package xuanmo.arcartxsuite.bridge;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import pers.neige.neigeitems.manager.ItemManager;

public final class NeigeItemsBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private ItemManager itemManager;

    public NeigeItemsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        itemManager = null;

        Plugin neigeItems = Bukkit.getPluginManager().getPlugin("NeigeItems");
        if (neigeItems == null) {
            return;
        }

        try {
            itemManager = ItemManager.INSTANCE;
            available = itemManager != null;
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 NeigeItems 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String neigeItemId(ItemStack itemStack) {
        if (!available || itemManager == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            String value = itemManager.getItemId(itemStack);
            return value != null && !value.isBlank() ? value : "";
        } catch (Exception | LinkageError exception) {
            return "";
        }
    }

    public ItemStack getItemStack(String itemId, int amount) {
        if (!available || itemManager == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            ItemStack itemStack = itemManager.getItemStack(itemId);
            if (itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (Exception | LinkageError exception) {
            return null;
        }
    }
}
