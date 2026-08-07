package xuanmo.arcartxsuite.bridge;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.nms.ItemBridge;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;

/**
 * {@link ItemBridgeAPI} 的默认实现，桥接 ArcartX 的 {@code ItemBridge}，
 * 提供物品 NBT 读写与序列化能力。
 * <p>
 * ArcartX 为必需依赖，运行时通过 {@link #initialize()} 检测可用性。
 */
public final class ArcartXItemStackBridge implements ItemBridgeAPI {

    private final JavaPlugin plugin;
    private boolean available;
    private ItemBridge itemBridge;
    private boolean failureWarned;

    public ArcartXItemStackBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        itemBridge = null;
        failureWarned = false;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            itemBridge = ItemBridge.INSTANCE;
            if (itemBridge == null) {
                throw new IllegalStateException("ItemBridge.INSTANCE 不可用。");
            }
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 ArcartX ItemStack 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        itemBridge = null;
        failureWarned = false;
    }

    public boolean isAvailable() {
        return available;
    }

    public Optional<String> itemToJson(ItemStack itemStack) {
        if (!available || itemBridge == null || itemStack == null) {
            return Optional.empty();
        }
        try {
            String json = itemBridge.item2json(itemStack);
            if (json != null && !json.isBlank()) {
                return Optional.of(json);
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            if (!failureWarned) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                    "ArcartX ItemStack item2json 调用失败，将回退为纯文本拾取提示: " + exception.getMessage()
                );
                failureWarned = true;
            }
            return Optional.empty();
        }
    }

    @Override
    public ItemStack putStringTag(ItemStack itemStack, String key, String value) {
        if (!available || itemBridge == null || itemStack == null || key == null || value == null) {
            return itemStack;
        }
        try {
            ItemStack result = itemBridge.putDeepTag(itemStack, key, value);
            return result != null ? result : itemStack;
        } catch (RuntimeException exception) {
            return itemStack;
        }
    }
}
