package xuanmo.arcartxsuite.bridge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

/**
 * 宿主维护的全局物品来源注册表单例。
 * 持有所有外部物品插件桥接，统一初始化和生命周期。
 */
public final class DefaultItemSourceRegistry implements ItemSourceRegistry {

    private final MythicItemBridge mythicItemBridge;
    private final NeigeItemsBridge neigeItemsBridge;
    private final OvertureItemBridge overtureItemBridge;
    private final MmoItemsBridge mmoItemsBridge;

    public DefaultItemSourceRegistry(JavaPlugin plugin) {
        this.mythicItemBridge = safeCreate(() -> new MythicItemBridge(plugin), "MythicMobs");
        this.neigeItemsBridge = safeCreate(() -> new NeigeItemsBridge(plugin), "NeigeItems");
        this.overtureItemBridge = safeCreate(() -> new OvertureItemBridge(plugin), "Overture");
        this.mmoItemsBridge = safeCreate(() -> new MmoItemsBridge(plugin), "MMOItems");
    }

    private <T> T safeCreate(java.util.function.Supplier<T> supplier, String name) {
        try {
            return supplier.get();
        } catch (NoClassDefFoundError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(name + " 桥接类加载失败（插件未安装?）: " + e.getMessage());
            return null;
        }
    }

    public void initialize() {
        if (mythicItemBridge != null) mythicItemBridge.initialize();
        if (neigeItemsBridge != null) neigeItemsBridge.initialize();
        if (overtureItemBridge != null) overtureItemBridge.initialize();
        if (mmoItemsBridge != null) mmoItemsBridge.initialize();
    }

    public void shutdown() {
        // 当前桥接无需特殊清理，保留扩展点
    }

    // ─── 物品识别 ─────────────────────────────────────────────────

    @Override
    public String mythicItemId(ItemStack itemStack) {
        return mythicItemBridge != null ? mythicItemBridge.mythicItemId(itemStack) : "";
    }

    @Override
    public String neigeItemId(ItemStack itemStack) {
        return neigeItemsBridge != null ? neigeItemsBridge.neigeItemId(itemStack) : "";
    }

    @Override
    public String overtureItemId(ItemStack itemStack) {
        return overtureItemBridge != null ? overtureItemBridge.overtureItemId(itemStack) : "";
    }

    @Override
    public boolean isMythicItem(ItemStack itemStack) {
        return mythicItemBridge != null && mythicItemBridge.isMythicItem(itemStack);
    }

    @Override
    public boolean isOvertureItem(ItemStack itemStack) {
        return overtureItemBridge != null && overtureItemBridge.isOvertureItem(itemStack);
    }

    // ─── 物品生成 ─────────────────────────────────────────────────

    @Override
    public ItemStack generateMythicItem(String itemId, int amount) {
        return mythicItemBridge != null ? mythicItemBridge.getItemStack(itemId, amount) : null;
    }

    @Override
    public ItemStack generateNeigeItem(String itemId, int amount) {
        return neigeItemsBridge != null ? neigeItemsBridge.getItemStack(itemId, amount) : null;
    }

    @Override
    public ItemStack generateOvertureItem(String itemId, Player player, int amount) {
        return overtureItemBridge != null ? overtureItemBridge.generateItem(itemId, player, amount) : null;
    }

    @Override
    public ItemStack generateMmoItem(String typeId, String itemId, int amount) {
        return mmoItemsBridge != null ? mmoItemsBridge.getItemStack(typeId, itemId, amount) : null;
    }

    // ─── 可用性查询 ───────────────────────────────────────────────

    @Override
    public boolean mythicBridgeAvailable() {
        return mythicItemBridge != null && mythicItemBridge.isAvailable();
    }

    @Override
    public boolean neigeBridgeAvailable() {
        return neigeItemsBridge != null && neigeItemsBridge.isAvailable();
    }

    @Override
    public boolean overtureBridgeAvailable() {
        return overtureItemBridge != null && overtureItemBridge.isAvailable();
    }

    @Override
    public boolean mmoBridgeAvailable() {
        return mmoItemsBridge != null && mmoItemsBridge.isAvailable();
    }
}
