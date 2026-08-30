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
    private final java.util.logging.Logger logger;

    public DefaultItemSourceRegistry(JavaPlugin plugin) {
        this.mythicItemBridge = safeCreate(() -> new MythicItemBridge(plugin), "MythicMobs");
        this.neigeItemsBridge = safeCreate(() -> new NeigeItemsBridge(plugin), "NeigeItems");
        this.overtureItemBridge = safeCreate(() -> new OvertureItemBridge(plugin), "Overture");
        this.mmoItemsBridge = safeCreate(() -> new MmoItemsBridge(plugin), "MMOItems");
        this.logger = plugin.getLogger();
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
    public String mmoItemId(ItemStack itemStack) {
        return mmoItemsBridge != null ? mmoItemsBridge.mmoItemId(itemStack) : "";
    }

    @Override
    public boolean isMythicItem(ItemStack itemStack) {
        return mythicItemBridge != null && mythicItemBridge.isMythicItem(itemStack);
    }

    @Override
    public boolean isNeigeItem(ItemStack itemStack) {
        return neigeItemsBridge != null && neigeItemsBridge.isNeigeItem(itemStack);
    }

    @Override
    public boolean isOvertureItem(ItemStack itemStack) {
        return overtureItemBridge != null && overtureItemBridge.isOvertureItem(itemStack);
    }

    @Override
    public boolean isMmoItem(ItemStack itemStack) {
        return mmoItemsBridge != null && mmoItemsBridge.isMmoItem(itemStack);
    }

    // ─── 物品生成 ─────────────────────────────────────────────────

    @Override
    public ItemStack generateItem(@org.jetbrains.annotations.NotNull String source,
                                  @org.jetbrains.annotations.NotNull String id, int amount,
                                  @org.jetbrains.annotations.Nullable String nbt,
                                  @org.jetbrains.annotations.Nullable Player player) {
        if (source == null || id == null || id.isBlank() || amount < 1) return null;
        String sourceLower = source.toLowerCase(java.util.Locale.ROOT);
        ItemStack item;
        switch (sourceLower) {
            case "minecraft", "plain", "vanilla" -> {
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id);
                if (material == null) return null;
                // 不截断 amount：Bukkit 的 inventory.addItem 会自动拆分超 maxStackSize 的物品到多个槽位
                item = new ItemStack(material, amount);
            }
            case "mythic", "mythicmobs" -> {
                item = mythicItemBridge != null ? mythicItemBridge.getItemStack(id, amount) : null;
            }
            case "neige", "neigeitems" -> {
                item = neigeItemsBridge != null ? neigeItemsBridge.getItemStack(id, amount) : null;
            }
            case "overture" -> {
                item = overtureItemBridge != null ? overtureItemBridge.generateItem(id, player, amount) : null;
            }
            case "mmoitems", "mmo" -> {
                // id 格式: "类型ID;物品ID"
                int sep = id.indexOf(';');
                if (sep <= 0 || sep >= id.length() - 1) return null;
                String typeId = id.substring(0, sep);
                String itemId = id.substring(sep + 1);
                item = mmoItemsBridge != null ? mmoItemsBridge.getItemStack(typeId, itemId, amount) : null;
            }
            default -> {
                return null;
            }
        }
        // 仅对 minecraft 源物品应用自定义 NBT，避免破坏外部物品库的内部 NBT 结构
        if ("minecraft".equals(sourceLower) || "plain".equals(sourceLower) || "vanilla".equals(sourceLower)) {
            return applyNbt(item, nbt);
        }
        return item;
    }

    /**
     * 应用 SNBT 格式的 NBT 标签到物品上。
     * <p>
     * 使用 {@link org.bukkit.UnsafeValues#modifyItemStack} 解析 SNBT 字符串，
     * 失败时保留原物品并记录警告。
     *
     * @param item 原始物品，为 null 时直接返回 null
     * @param nbt  SNBT 字符串（如 "{display:{Name:'{\"text\":\"测试\"}'}}""），为 null/空时跳过
     * @return 应用了 NBT 的物品克隆，或原物品
     */
    private ItemStack applyNbt(@org.jetbrains.annotations.Nullable ItemStack item,
                               @org.jetbrains.annotations.Nullable String nbt) {
        if (item == null) return null;
        if (nbt == null || nbt.isBlank()) return item;
        try {
            ItemStack modified = org.bukkit.Bukkit.getUnsafe().modifyItemStack(item.clone(), nbt);
            return modified != null ? modified : item;
        } catch (Exception e) {
            logger.warning("应用 NBT 失败: " + nbt + " | " + e.getMessage());
            return item;
        }
    }

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

    // ─── Overture 扩展查询 ────────────────────────────────────────

    @Override
    public String overtureItemDisplayName(String itemId) {
        return overtureItemBridge != null ? overtureItemBridge.itemDisplayName(itemId) : null;
    }

    @Override
    public java.util.List<String> overtureItemDisplayLore(String itemId) {
        return overtureItemBridge != null ? overtureItemBridge.itemDisplayLore(itemId) : null;
    }

    @Override
    public ItemStack overtureTemplateItem(String itemId) {
        return overtureItemBridge != null ? overtureItemBridge.templateItem(itemId) : null;
    }

    @Override
    public java.util.List<String> overtureItemIds() {
        return overtureItemBridge != null ? overtureItemBridge.getItemIds() : java.util.List.of();
    }

    // ─── Overture 序列化 ──────────────────────────────────────────

    @Override
    public String overtureSerialize(ItemStack item) {
        return overtureItemBridge != null ? overtureItemBridge.serializeItem(item) : null;
    }

    @Override
    public ItemStack overtureDeserialize(String json) {
        return overtureItemBridge != null ? overtureItemBridge.deserializeItem(json) : null;
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
