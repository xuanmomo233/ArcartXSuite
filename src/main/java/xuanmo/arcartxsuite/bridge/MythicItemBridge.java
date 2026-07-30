package xuanmo.arcartxsuite.bridge;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MythicItemBridge {

    private volatile boolean available;

    public MythicItemBridge(JavaPlugin plugin) {
    }

    public void initialize() {
        available = false;
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("MythicBukkit") == null
                && org.bukkit.Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                return;
            }
            // 触发类加载验证可用性
            MythicBukkit.inst().getItemManager();
            available = true;
        } catch (Exception | LinkageError ignored) {
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String mythicItemId(ItemStack itemStack) {
        if (!available || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            var itemManager = MythicBukkit.inst().getItemManager();
            var mythicItem = itemManager.getMythicTypeFromItem(itemStack);
            return mythicItem != null && !mythicItem.isBlank() ? mythicItem : "";
        } catch (Exception | LinkageError ignored) {
            return "";
        }
    }

    public boolean isMythicItem(ItemStack itemStack) {
        if (!available || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            return MythicBukkit.inst().getItemManager().isMythicItem(itemStack);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public ItemStack getItemStack(String itemId, int amount) {
        if (!available || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            var itemManager = MythicBukkit.inst().getItemManager();
            ItemStack raw = itemManager.getItemStack(itemId, Math.max(1, amount));
            if (raw == null || raw.getType().isAir()) {
                return null;
            }
            ItemStack cloned = raw.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }
}
