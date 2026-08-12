package xuanmo.arcartxsuite.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.overture.api.OvertureAPI;

/**
 * Overture 物品桥接，提供 Overture 物品的按 id 构建与解析。
 * <p>
 * Overture 为可选依赖，运行时通过插件管理器检测可用性。
 */
public final class OvertureItemBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private OvertureAPI overtureApi;

    public OvertureItemBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        overtureApi = null;

        Plugin overture = Bukkit.getPluginManager().getPlugin("Overture");
        if (overture == null) {
            return;
        }

        try {
            overtureApi = OvertureAPI.INSTANCE;
            available = overtureApi != null;
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 Overture 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String overtureItemId(ItemStack itemStack) {
        if (!available || overtureApi == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            String value = overtureApi.getOvertureId(itemStack);
            return value != null && !value.isBlank() ? value : "";
        } catch (Exception | LinkageError exception) {
            return "";
        }
    }

    public boolean isOvertureItem(ItemStack itemStack) {
        if (!available || overtureApi == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            return overtureApi.isOvertureItem(itemStack);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public ItemStack generateItem(String itemId, Player player, int amount) {
        if (!available || overtureApi == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            ItemStack itemStack = overtureApi.generateItem(itemId, player);
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

    public java.util.List<String> getItemIds() {
        if (!available || overtureApi == null) {
            return java.util.List.of();
        }
        try {
            return overtureApi.getItemIds();
        } catch (Exception | LinkageError exception) {
            return java.util.List.of();
        }
    }

    // ─── Overture 扩展查询 ────────────────────────────────────

    public String itemDisplayName(String itemId) {
        if (!available || overtureApi == null || itemId == null || itemId.isBlank()) return null;
        try {
            return overtureApi.getItemName(itemId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    public java.util.List<String> itemDisplayLore(String itemId) {
        if (!available || overtureApi == null || itemId == null || itemId.isBlank()) return null;
        try {
            return overtureApi.getItemLore(itemId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    public org.bukkit.inventory.ItemStack templateItem(String itemId) {
        if (!available || overtureApi == null || itemId == null || itemId.isBlank()) return null;
        try {
            return overtureApi.getTemplateItem(itemId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ─── Overture 序列化 ──────────────────────────────────────

    public String serializeItem(org.bukkit.inventory.ItemStack item) {
        if (!available || overtureApi == null || item == null) return null;
        try {
            return overtureApi.serialize(item);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    public org.bukkit.inventory.ItemStack deserializeItem(String json) {
        if (!available || overtureApi == null || json == null || json.isBlank()) return null;
        try {
            return overtureApi.deserialize(json);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}
