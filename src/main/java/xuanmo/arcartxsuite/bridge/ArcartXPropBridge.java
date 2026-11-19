package xuanmo.arcartxsuite.bridge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import priv.seventeen.artist.arcartx.core.keybind.ArcartXKeyBindRegistry;
import priv.seventeen.artist.arcartx.event.player.PlayerExtraSlotUpdateEvent;
import priv.seventeen.artist.arcartx.util.ItemStackUtils;
import priv.seventeen.artist.arcartx.util.collections.KeyCallBack;

/**
 * {@link xuanmo.arcartxsuite.api.bridge.PropBridgeAPI} 的默认实现，
 * 桥接 ArcartX 快捷道具栏，提供道具绑定、按键回调与 PDC 持久化。
 * <p>
 * 通过 PDC 在物品上记录 prop id，配合 KeyBindRegistry 注册按键回调。
 */
public final class ArcartXPropBridge implements xuanmo.arcartxsuite.api.bridge.PropBridgeAPI,
        xuanmo.arcartxsuite.api.bridge.PropSlotUpdates {

    private static final String PDC_BACKEND_KEY = "pdc";

    private final JavaPlugin plugin;
    private final NamespacedKey persistentPropIdKey;
    private final Map<String, KeyCallBack> keyCallbackKeepAlive = new ConcurrentHashMap<>();

    private boolean available;
    private ArcartXKeyBindRegistry keyBindRegistry;
    private ArcartXEntityManager entityManager;

    public ArcartXPropBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.persistentPropIdKey = new NamespacedKey(plugin, "prop_id");
    }

    public boolean initialize() {
        available = false;
        keyBindRegistry = null;
        entityManager = null;
        keyCallbackKeepAlive.clear();

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            keyBindRegistry = ArcartXAPI.getKeyBindRegistry();
            entityManager = ArcartXAPI.getEntityManager();
            if (keyBindRegistry == null || entityManager == null) {
                throw new IllegalStateException("ArcartX KeyBindRegistry 或 EntityManager 不可用。");
            }
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 ArcartX Prop ArcartX API 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        keyBindRegistry = null;
        entityManager = null;
        keyCallbackKeepAlive.clear();
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public Optional<PotionDrink> preparePotionDrink(Player player, ItemStack item) {
        try {
            if (mmoItemsAvailable() && MmoItemsQuickUse.usesHandForRemainingDoses(item)) {
                return Optional.empty();
            }
            return Optional.of(xuanmo.arcartxsuite.bridge.potion.NativePotionDrink.prepare(player, item));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("当前服务端原版饮用入口不可用: " + exception);
            return Optional.empty();
        }
    }

    @Override
    public MmoClickResult useMmoItem(Player player, ItemStack item, BooleanSupplier takeOne, Runnable refundOne) {
        if (!mmoItemsAvailable()) {
            return MmoClickResult.UNAVAILABLE;
        }
        try {
            return MmoItemsQuickUse.use(player, item, takeOne, refundOne);
        } catch (LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("MMOItems 快捷使用接口不兼容: " + exception);
            return MmoClickResult.UNAVAILABLE;
        }
    }

    private boolean mmoItemsAvailable() {
        var plugins = plugin.getServer().getPluginManager();
        return plugins.isPluginEnabled("MMOItems") && plugins.isPluginEnabled("MythicLib");
    }

    @Override
    public Runnable registerMmoItemsAccessories(java.util.Set<String> slotIds) {
        if (!available || !mmoItemsAvailable() || slotIds.isEmpty()) return null;
        try {
            return MmoItemsAccessories.register(plugin, this, slotIds);
        } catch (RuntimeException | LinkageError failure) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("MMOItems 饰品槽接入失败: " + failure);
            return null;
        }
    }

    @Override
    public Runnable observeSlots(BiConsumer<Player, String> changed) {
        if (!available) return null;
        Listener listener = null;
        try {
            listener = new SlotListener(changed);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            Listener registered = listener;
            return () -> HandlerList.unregisterAll(registered);
        } catch (RuntimeException | LinkageError failure) {
            if (listener != null) HandlerList.unregisterAll(listener);
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("ArcartX 额外槽位事件暂不可用: " + failure.getMessage());
            return null;
        }
    }

    private record SlotListener(BiConsumer<Player, String> changed) implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void changed(PlayerExtraSlotUpdateEvent event) {
            changed.accept(event.getPlayer(), event.getSlotID());
        }
    }

    public String propIdWriterBackendKey() {
        return PDC_BACKEND_KEY;
    }

    public boolean registerClientKeyBind(String bindingId, String category, String defaultKey, Consumer<Player> onPress) {
        if (!available || keyBindRegistry == null || bindingId == null || bindingId.isBlank() || onPress == null) {
            return false;
        }

        KeyCallBack callback = new KeyCallBack() {
            @Override
            public void onPress(Player player) {
                onPress.accept(player);
            }
        };

        try {
            keyBindRegistry.registerClientKeyBind(bindingId, safe(category), safe(defaultKey), callback);
            keyCallbackKeepAlive.put(bindingId, callback);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("注册 ArcartX 客户端按键失败(" + bindingId + "): " + exception.getMessage());
            return false;
        }
    }

    public void unregisterClientKeyBind(String bindingId) {
        if (!available || keyBindRegistry == null || bindingId == null || bindingId.isBlank()) {
            keyCallbackKeepAlive.remove(bindingId);
            return;
        }
        try {
            keyBindRegistry.unregisterClientKeyBind(bindingId);
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("注销 ArcartX 客户端按键失败(" + bindingId + "): " + exception.getMessage());
        } finally {
            keyCallbackKeepAlive.remove(bindingId);
        }
    }

    @Override
    public Optional<xuanmo.arcartxsuite.api.bridge.PropPlayerHandle> resolvePlayerHandle(Player player) {
        if (!available || player == null || entityManager == null) {
            return Optional.empty();
        }
        try {
            ArcartXPlayer handle = entityManager.getPlayer(player);
            return handle == null ? Optional.empty() : Optional.of(new PlayerHandle(handle));
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("获取 ArcartXPlayer 失败(" + player.getName() + "): " + exception.getMessage());
            return Optional.empty();
        }
    }

    public String getItemTag(ItemStack itemStack, String key) {
        if (!available || itemStack == null || key == null || key.isBlank()) {
            return "";
        }
        try {
            String value = ItemStackUtils.getTag(itemStack, key);
            return value == null ? "" : value.trim();
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("读取 ArcartX ItemStack 标签失败(" + key + "): " + exception.getMessage());
            return "";
        }
    }

    public void setCooldownTag(ItemStack itemStack, String coolDownGroup) {
        if (!available || itemStack == null) {
            return;
        }
        try {
            ItemStackUtils.setCoolDownTag(itemStack, safe(coolDownGroup));
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("写入 ArcartX 冷却标签失败: " + exception.getMessage());
        }
    }

    public String getCooldownTag(ItemStack itemStack) {
        if (!available || itemStack == null) {
            return "";
        }
        try {
            String value = ItemStackUtils.getCooldownTag(itemStack);
            return value == null ? "" : value.trim();
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("读取 ArcartX 冷却标签失败: " + exception.getMessage());
            return "";
        }
    }

    public ItemStack writePropId(ItemStack itemStack, String propId) {
        if (itemStack == null) {
            return null;
        }
        return writePersistentPropId(itemStack, propId);
    }

    public String getPersistentPropId(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        String value = container.get(persistentPropIdKey, PersistentDataType.STRING);
        return value == null ? "" : value.trim();
    }

    private ItemStack writePersistentPropId(ItemStack itemStack, String propId) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.getPersistentDataContainer().set(persistentPropIdKey, PersistentDataType.STRING, safe(propId));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public final class PlayerHandle implements xuanmo.arcartxsuite.api.bridge.PropPlayerHandle {

        private final ArcartXPlayer handle;

        private PlayerHandle(ArcartXPlayer handle) {
            this.handle = Objects.requireNonNull(handle, "handle");
        }

        public ItemStack getSlotItemStack(String slotId) {
            try {
                return handle.getSlotItemStack(safe(slotId));
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("读取 ArcartX 额外槽位失败(" + slotId + "): " + exception.getMessage());
                return null;
            }
        }

        public void setSlotItemStack(String slotId, ItemStack itemStack) {
            try {
                handle.setSlotItemStack(safe(slotId), itemStack);
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("写入 ArcartX 额外槽位失败(" + slotId + "): " + exception.getMessage());
            }
        }

        @Override
        public boolean trySetSlotItemStack(String slotId, ItemStack itemStack) {
            String key = safe(slotId);
            setSlotItemStack(key, itemStack);
            try {
                ItemStack stored = handle.getSlotItemStack(key);
                if (itemStack.getType().isAir() || itemStack.getAmount() <= 0) {
                    return stored == null || stored.getType().isAir() || stored.getAmount() <= 0;
                }
                return itemStack.equals(stored);
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("确认 ArcartX 额外槽位写入失败(" + slotId + "): " + exception.getMessage());
                return false;
            }
        }

        @Override
        public void syncSlotToClient(String slotId) {
            try {
                String key = safe(slotId);
                ItemStack stored = handle.getSlotItemStack(key);
                if (stored == null) {
                    handle.removeSlotItemStackOnlyClient(key, false);
                } else {
                    handle.setSlotItemStackOnlyClient(key, stored);
                }
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("同步 ArcartX 额外槽位失败(" + slotId + "): " + exception.getMessage());
            }
        }

        public void removeSlotItemStackOnlyClient(String slotPrefix, boolean recursive) {
            try {
                handle.removeSlotItemStackOnlyClient(safe(slotPrefix), recursive);
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("清理 ArcartX 客户端额外槽位失败(" + slotPrefix + "): " + exception.getMessage());
            }
        }

        public void setTagCooldown(String tag, long cooldownMillis) {
            try {
                handle.setTagCooldown(safe(tag), Math.max(0L, cooldownMillis));
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("设置 ArcartX 冷却失败(" + tag + "): " + exception.getMessage());
            }
        }

        public long getTagCooldown(String tag) {
            try {
                return Math.max(0L, handle.getTagCooldown(safe(tag)));
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("读取 ArcartX 冷却失败(" + tag + "): " + exception.getMessage());
                return 0L;
            }
        }

        public void syncSlotCacheToClient() {
            try {
                handle.syncSlotCacheToClient();
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("同步 ArcartX 槽位缓存失败: " + exception.getMessage());
            }
        }
    }

}
