package xuanmo.arcartxsuite.extrabackpack.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.arcartx.ArcartX;
import priv.seventeen.artist.arcartx.core.config.slot.SlotElement;
import priv.seventeen.artist.arcartx.event.client.ClientExtraSlotClickEvent;
import priv.seventeen.artist.arcartx.event.player.PlayerExtraSlotUpdateEvent;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.ExtraBackpackCategory;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.UpgradeCost;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository.ExtraBackpackSlotRecord;

/**
 * 额外槽位背包服务：默认关闭。启用后与玩家原版背包共存，
 * 由多个分类页（装备/材料/杂物…）组成，每页独立配置槽位数量、上限与扩容价格。
 * <p>
 * 物品分类依据 {@link ExtraBackpackCategory} 的 NBT/lore 匹配规则，由本模块自行判断；
 * 手动存入（光标拖入扩容槽位）与 API 存入统一走「匹配分类 → 兜底分类」路由。
 * 数据存于 {@code extra_backpack_slots} / {@code extra_backpack_capacity} 两张表：
 * 槽位改动采用脏标记 + 周期异步落盘，玩家退出/关闭 UI/模块停用时同步落盘。
 */
public final class ExtraBackpackService implements Listener, ExtraBackpackAccess {

    /** EventBus 主题：物品存入额外背包。 */
    public static final String TOPIC_DEPOSITED = "axs.extrabackpack.deposited";
    /** EventBus 主题：物品从额外背包取出。 */
    public static final String TOPIC_WITHDRAWN = "axs.extrabackpack.withdrawn";
    /** EventBus 主题：分类页付费扩容。 */
    public static final String TOPIC_UPGRADED = "axs.extrabackpack.upgraded";
    /** EventBus 主题：光标物品经二级密码验证后被销毁。 */
    public static final String TOPIC_DESTROYED = "axs.extrabackpack.destroyed";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final PropBridgeAPI propBridge;
    private final UiResourceExporter uiResourceExporter;
    private final ExtraBackpackConfiguration configuration;
    private final ExtraBackpackRepository repository;
    private final CurrencyBridgeAPI currencyBridge;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final SchedulerAPI scheduler;

    private static final String UI_RESOURCE_PATH = "arcartx/ui/extrabackpack.yml";
    private static final String UI_FILE_PATH = "ui/extrabackpack.yml";
    /** ArcartX 扩展槽位 ID 前缀：{@code axs_eb_<分类ID>_<槽位序号>}。 */
    private static final String SLOT_ID_PREFIX = "axs_eb_";
    /** ArcartX extra_slot 配置文件名（写入 plugins/ArcartX/extra_slot/ 下）。 */
    private static final String SLOT_CONFIG_FILE = "axs_extrabackpack.yml";

    private String runtimeUiId = "";
    private String registeredUiId = "";
    private ScheduledTask flushTask;
    private Supplier<EventBusCapability> eventBusProvider = () -> null;

    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<UUID>> committedActionTokens =
        ThreadLocal.withInitial(java.util.HashSet::new);

    /** 玩家内存态背包：分类 ID → 该页槽位物品数组（数组长度=当前容量）。 */
    private final ConcurrentMap<UUID, Map<String, ItemStack[]>> backpacks = new ConcurrentHashMap<>();
    /** 玩家内存态容量：分类 ID → 当前槽位上限（与容量表同步，避免刷新路径反复读库）。 */
    private final ConcurrentMap<UUID, Map<String, Long>> capacities = new ConcurrentHashMap<>();
    /** 玩家当前查看的分类页。 */
    private final ConcurrentMap<UUID, String> activeCategory = new ConcurrentHashMap<>();
    /** 有未落盘槽位改动的玩家集合，由周期异步任务批量 flush。 */
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, String> passwordPanelModes = new ConcurrentHashMap<>();
    /** 销毁确认状态：true 表示玩家已发起销毁且待确认/待密码验证。 */
    private final Set<UUID> pendingDestroyCursor = ConcurrentHashMap.newKeySet();
    /** Serializes state mutation and persistence for each player independently. */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    /** 本次会话已推送到 ArcartX 扩展槽位缓存的分类集合（槽位事件只认已绑定分类）。 */
    private final ConcurrentMap<UUID, Set<String>> boundCategories = new ConcurrentHashMap<>();
    /** 推送槽位期间抑制 {@link PlayerExtraSlotUpdateEvent} 回写（事件同线程同步触发）。 */
    private final Set<UUID> slotSyncSuppressed = ConcurrentHashMap.newKeySet();
    /** 当前打开着额外背包 UI 的玩家（match 打开不会被 PacketBridgeAPI.isUiOpen 记录）。 */
    private final Set<UUID> viewingPlayers = ConcurrentHashMap.newKeySet();
    /** 本模块注入 {@code SlotFolder.setting} 的槽位 ID，停用时回收。 */
    private final Set<String> injectedSlotIds = ConcurrentHashMap.newKeySet();
    /** 分类匹配拒绝提示节流（避免拖动刷屏）。 */
    private final ConcurrentMap<UUID, Long> denyMessageCooldown = new ConcurrentHashMap<>();

    public ExtraBackpackService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        PropBridgeAPI propBridge,
        UiResourceExporter uiResourceExporter,
        ExtraBackpackConfiguration configuration,
        ExtraBackpackRepository repository,
        CurrencyBridgeAPI currencyBridge,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
        SchedulerAPI scheduler
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.propBridge = propBridge;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.repository = repository;
        this.currencyBridge = currencyBridge;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.scheduler = scheduler;
    }

    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    /** 注入事件总线提供者（由 Module 延迟查找 capability）。 */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider == null ? () -> null : eventBusProvider;
    }

    public boolean enabled() {
        return configuration.extraBackpack().enabled() && !configuration.extraBackpack().categories().isEmpty();
    }

    private Object playerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, ignored -> new Object());
    }

    private void publishEvent(String topic, Player player, Map<String, String> payload) {
        EventBusCapability bus = eventBusProvider == null ? null : eventBusProvider.get();
        if (bus != null) {
            bus.publish(topic, player, payload);
        }
    }

    @Override
    public boolean isAvailable(@NotNull Player player) {
        return enabled() && player.isOnline();
    }

    @Override
    public @NotNull ExtraBackpackAccess.Snapshot snapshot(@NotNull Player player) {
        if (!isAvailable(player)) {
            return new ExtraBackpackAccess.Snapshot(false, List.of());
        }
        try {
            Map<String, ItemStack[]> pages = pages(player);
            List<ExtraBackpackAccess.CategorySnapshot> categorySnapshots = new ArrayList<>();
            for (ExtraBackpackCategory category : categories()) {
                ItemStack[] page = pages.get(category.id());
                if (page == null) {
                    continue;
                }
                List<ExtraBackpackAccess.SlotSnapshot> slots = new ArrayList<>();
                long used = 0L;
                for (int slot = 0; slot < page.length; slot++) {
                    ItemStack stack = page[slot];
                    if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                        continue;
                    }
                    used++;
                    slots.add(new ExtraBackpackAccess.SlotSnapshot(slot, stack.clone()));
                }
                categorySnapshots.add(new ExtraBackpackAccess.CategorySnapshot(
                    category.id(),
                    page.length,
                    used,
                    slots
                ));
            }
            return new ExtraBackpackAccess.Snapshot(true, categorySnapshots);
        } catch (Exception exception) {
            logger.warning("读取额外背包快照失败(" + player.getName() + "): " + exception.getMessage());
            return new ExtraBackpackAccess.Snapshot(false, List.of());
        }
    }

    @Override
    public @NotNull ExtraBackpackAccess.DepositResult deposit(
        @NotNull Player player,
        @NotNull ItemStack itemStack
    ) {
        if (!isAvailable(player)) {
            return new ExtraBackpackAccess.DepositResult(
                false,
                0L,
                Math.max(0, itemStack.getAmount()),
                message("extra-backpack.disabled")
            );
        }
        ItemStack input = itemStack.clone();
        int originalAmount = input.getAmount();
        if (originalAmount <= 0 || input.getType().isAir()) {
            return new ExtraBackpackAccess.DepositResult(false, 0L, 0, "");
        }
        synchronized (playerLock(player.getUniqueId())) {
            try {
                String targetCategory = resolveRouteCategory(input);
                ItemStack leftover = storeIntoBackpackWithFallback(player, input);
                int remaining = leftover == null ? 0 : Math.max(0, leftover.getAmount());
                long stored = originalAmount - remaining;
                if (stored > 0) {
                    markDirty(player.getUniqueId());
                    publishEvent(TOPIC_DEPOSITED, player, Map.of(
                        "amount", Long.toString(stored),
                        "category", targetCategory
                    ));
                    syncCategoriesForStore(player, input);
                }
                return new ExtraBackpackAccess.DepositResult(
                    stored > 0L,
                    stored,
                    remaining,
                    remaining > 0 ? message("extra-backpack.page-full") : ""
                );
            } catch (Exception exception) {
                logger.warning("额外背包存入失败(" + player.getName() + "): " + exception.getMessage());
                return new ExtraBackpackAccess.DepositResult(
                    false,
                    0L,
                    originalAmount,
                    message("extra-backpack.operation-failed")
                );
            }
        }
    }

    @Override
    public @NotNull ExtraBackpackAccess.WithdrawResult withdraw(
        @NotNull Player player,
        @NotNull String categoryId,
        int slot,
        int amount
    ) {
        if (!isAvailable(player) || amount <= 0) {
            return new ExtraBackpackAccess.WithdrawResult(
                false,
                0,
                null,
                message("extra-backpack.operation-failed")
            );
        }
        ExtraBackpackCategory category = category(categoryId);
        if (category == null) {
            return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
        }
        synchronized (playerLock(player.getUniqueId())) {
            try {
                ItemStack[] page = pages(player).get(category.id());
                if (page == null || slot < 0 || slot >= page.length) {
                    return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
                }
                ItemStack stored = page[slot];
                if (stored == null || stored.getType().isAir() || stored.getAmount() <= 0) {
                    return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
                }
                int taken = Math.min(amount, stored.getAmount());
                ItemStack result = stored.clone();
                result.setAmount(taken);
                if (taken == stored.getAmount()) {
                    page[slot] = null;
                } else {
                    stored.setAmount(stored.getAmount() - taken);
                }
                markDirty(player.getUniqueId());
                publishEvent(TOPIC_WITHDRAWN, player, Map.of(
                    "amount", Integer.toString(taken),
                    "category", category.id(),
                    "slot", Integer.toString(slot)
                ));
                syncCategorySlotsIfBound(player, category.id());
                return new ExtraBackpackAccess.WithdrawResult(true, taken, result, "");
            } catch (Exception exception) {
                logger.warning("额外背包取出失败(" + player.getName() + "): " + exception.getMessage());
                return new ExtraBackpackAccess.WithdrawResult(
                    false,
                    0,
                    null,
                    message("extra-backpack.operation-failed")
                );
            }
        }
    }

    public void start() throws Exception {
        if (!enabled()) {
            return;
        }
        if (propBridge == null || !propBridge.isAvailable()) {
            throw new IllegalStateException("ExtraBackpack 需要 ArcartX 扩展槽位桥接，当前不可用");
        }
        bindUi();
        registerExtraSlots();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayer(online);
        }
        long interval = Math.max(20L, configuration.flushIntervalTicks());
        if (scheduler != null) {
            flushTask = scheduler.scheduleAsyncAtFixedRate(plugin, this::flushDirtyPlayers, interval, interval);
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        HandlerList.unregisterAll(this);
        for (UUID uuid : new ArrayList<>(backpacks.keySet())) {
            synchronized (playerLock(uuid)) {
                try {
                    persist(uuid);
                } catch (Exception exception) {
                    logger.warning("保存额外背包失败(" + uuid + "): " + exception.getMessage());
                }
            }
        }
        dirtyPlayers.clear();
        backpacks.clear();
        capacities.clear();
        activeCategory.clear();
        boundCategories.clear();
        slotSyncSuppressed.clear();
        viewingPlayers.clear();
        denyMessageCooldown.clear();
        passwordPanelModes.clear();
        pendingDestroyCursor.clear();
        playerLocks.clear();
        unregisterExtraSlots();
        if (packetBridge != null) {
            packetBridge.unregisterUiCloseCallback(runtimeUiId);
            if (!registeredUiId.isBlank()) {
                packetBridge.unregisterUi(registeredUiId);
            }
        }
    }

    /**
     * 返回已注册到 ArcartX 的 UI ID 列表（供 Module 记录到基类生命周期管理）。
     *
     * @return 已注册的 UI ID 列表，未注册时返回空列表
     */
    public java.util.List<String> registeredUiIds() {
        return !registeredUiId.isBlank() ? java.util.List.of(registeredUiId) : java.util.List.of();
    }

    private void bindUi() throws Exception {
        File uiFile = uiResourceExporter.export(UI_RESOURCE_PATH, UI_FILE_PATH, configuration.ui().overwriteUiFiles());
        if (packetBridge == null || !configuration.ui().registerUiOnEnable()) {
            runtimeUiId = PacketBridgeAPI.normalizeUiId("AXS:extrabackpack", uiFile);
            return;
        }
        PacketBridgeAPI.UiRegistrationResult registration = packetBridge.registerOrReloadUi("AXS:extrabackpack", uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("注册额外背包 UI 失败: " + registration.message());
        }
        registeredUiId = registration.registeredUiId() == null ? "" : registration.registeredUiId();
        runtimeUiId = registration.runtimeUiId();
        packetBridge.registerUiCloseCallback(runtimeUiId, this::handleUiClosed);
    }

    // ---------------------------------------------------------------------
    // ArcartX 扩展槽位注册与同步
    // ---------------------------------------------------------------------

    /** 扩容槽位 ID 引用：{@code axs_eb_<分类ID>_<序号>} 的解析结果。 */
    private record SlotRef(String categoryId, int index) {}

    private static String slotId(String categoryId, int index) {
        return SLOT_ID_PREFIX + normalizeId(categoryId) + "_" + index;
    }

    private static SlotRef parseSlotId(String rawId) {
        if (rawId == null || !rawId.startsWith(SLOT_ID_PREFIX)) {
            return null;
        }
        String rest = rawId.substring(SLOT_ID_PREFIX.length());
        int sep = rest.lastIndexOf('_');
        if (sep <= 0 || sep >= rest.length() - 1) {
            return null;
        }
        try {
            return new SlotRef(normalizeId(rest.substring(0, sep)), Integer.parseInt(rest.substring(sep + 1)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 注册全部扩容槽位：写入 {@code plugins/ArcartX/extra_slot/axs_extrabackpack.yml}
     * （保证 ArcartX 重载/重启后槽位仍被识别），同时直接注入 {@code SlotFolder.setting}
     * 使槽位立即生效而无需重载 ArcartX。
     */
    private void registerExtraSlots() {
        List<String> ids = new ArrayList<>();
        for (ExtraBackpackCategory category : categories()) {
            for (int i = 0; i < category.maxSlots(); i++) {
                ids.add(slotId(category.id(), i));
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        writeExtraSlotConfig(ids);
        try {
            Map<String, SlotElement> setting =
                ArcartX.INSTANCE.getConfigs().getSlotFolder().getSetting();
            for (String id : ids) {
                if (setting.containsKey(id)) {
                    continue;
                }
                SlotElement element = new SlotElement();
                element.setId(id);
                setting.put(id, element);
                injectedSlotIds.add(id);
            }
        } catch (Throwable throwable) {
            logger.warning("注入 ArcartX 扩展槽位失败: " + throwable.getMessage());
        }
    }

    /** 生成/更新 ArcartX extra_slot 配置文件（内容一致时跳过，避免无谓写盘）。 */
    private void writeExtraSlotConfig(List<String> ids) {
        try {
            org.bukkit.plugin.Plugin arcartx = Bukkit.getPluginManager().getPlugin("ArcartX");
            if (arcartx == null) {
                return;
            }
            File dir = new File(arcartx.getDataFolder(), "extra_slot");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            File file = new File(dir, SLOT_CONFIG_FILE);
            StringBuilder content = new StringBuilder();
            content.append("# 由 ArcartXSuite-ExtraBackpack 自动生成，请勿手动修改\n");
            content.append("# 每个键为扩容背包的一个真实槽位（axs_eb_<分类ID>_<槽位序号>）\n");
            for (String id : ids) {
                content.append(id).append(":\n  attribute: \"none\"\n");
            }
            String text = content.toString();
            if (file.exists()
                && text.equals(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8))) {
                return;
            }
            java.nio.file.Files.writeString(file.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            logger.warning("写入 ArcartX extra_slot 配置失败: " + throwable.getMessage());
        }
    }

    /** 停用回收：移除注入的槽位并删除自动生成的 extra_slot 配置，避免残留死槽位。 */
    private void unregisterExtraSlots() {
        try {
            Map<String, SlotElement> setting =
                ArcartX.INSTANCE.getConfigs().getSlotFolder().getSetting();
            for (String id : injectedSlotIds) {
                setting.remove(id);
            }
        } catch (Throwable throwable) {
            logger.warning("移除 ArcartX 扩展槽位失败: " + throwable.getMessage());
        }
        injectedSlotIds.clear();
        try {
            org.bukkit.plugin.Plugin arcartx = Bukkit.getPluginManager().getPlugin("ArcartX");
            if (arcartx != null) {
                java.nio.file.Files.deleteIfExists(
                    new File(arcartx.getDataFolder(), "extra_slot/" + SLOT_CONFIG_FILE).toPath());
            }
        } catch (Throwable throwable) {
            logger.warning("删除 ArcartX extra_slot 配置失败: " + throwable.getMessage());
        }
    }

    /**
     * 把指定分类页的内存态物品推送到 ArcartX 扩展槽位缓存（槽位 {@code 0..maxSlots-1}，
     * 容量之外的槽位强制清空）。推送后该分类标记为已绑定，其槽位事件才会回写内存态。
     */
    private void pushCategorySlots(Player player, String categoryId) {
        if (propBridge == null) {
            return;
        }
        ExtraBackpackCategory category = category(categoryId);
        PropPlayerHandle handle = propBridge.resolvePlayerHandle(player).orElse(null);
        if (category == null || handle == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        ItemStack[] page = pages == null ? null : pages.get(category.id());
        slotSyncSuppressed.add(uuid);
        try {
            for (int i = 0; i < category.maxSlots(); i++) {
                ItemStack stack = page != null && i < page.length ? page[i] : null;
                handle.setSlotItemStack(
                    slotId(category.id(), i),
                    stack == null || stack.getType().isAir() ? new ItemStack(Material.AIR) : stack);
            }
        } finally {
            slotSyncSuppressed.remove(uuid);
        }
        boundCategories.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(category.id());
    }

    /** 内存态被外部途径（存入/拾取/扩容/API）修改后，若该分类已绑定且 UI 打开则同步到客户端槽位。 */
    private void syncCategorySlotsIfBound(Player player, String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return;
        }
        Set<String> bound = boundCategories.get(player.getUniqueId());
        if (bound == null || !bound.contains(categoryId) || !viewingPlayers.contains(player.getUniqueId())) {
            return;
        }
        pushCategorySlots(player, categoryId);
    }

    /** 物品存入路径可能波及匹配分类与兜底分类，两者已绑定时都需要回刷槽位。 */
    private void syncCategoriesForStore(Player player, ItemStack stack) {
        String matched = matchCategory(stack);
        if (!matched.isBlank()) {
            syncCategorySlotsIfBound(player, matched);
        }
        syncCategorySlotsIfBound(player, fallbackCategoryId());
    }

    // ---------------------------------------------------------------------
    // 分类与容量
    // ---------------------------------------------------------------------

    private List<ExtraBackpackCategory> categories() {
        List<ExtraBackpackCategory> list = new ArrayList<>(configuration.extraBackpack().categories().values());
        list.sort(Comparator.comparingInt(ExtraBackpackCategory::priority));
        return list;
    }

    private ExtraBackpackCategory category(String id) {
        return configuration.extraBackpack().categories().get(normalizeId(id));
    }

    private ExtraBackpackCategory fallbackCategory() {
        for (ExtraBackpackCategory category : categories()) {
            if (category.fallback()) {
                return category;
            }
        }
        List<ExtraBackpackCategory> list = categories();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private boolean matches(ItemStack stack, ExtraBackpackCategory category) {
        String path = category.nbtPath().toLowerCase(Locale.ROOT);
        if ("lore".equals(path)) {
            for (String line : loreLines(stack)) {
                String normalized = normalizeId(line);
                for (String value : category.values()) {
                    if (normalized.contains(value)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String value = normalizeId(resolvePath(stack, category.nbtPath()));
        return !value.isBlank() && category.values().contains(value);
    }

    /** 读取该分类当前容量：优先内存缓存，未加载时回源数据库。 */
    private long resolveCapacity(UUID uuid, ExtraBackpackCategory category) throws Exception {
        Map<String, Long> playerCapacities = capacities.get(uuid);
        if (playerCapacities != null) {
            Long cached = playerCapacities.get(category.id());
            if (cached != null) {
                return cached;
            }
        }
        long stored = repository.loadExtraBackpackCapacity(uuid, category.id());
        long capacity = stored < 0 ? category.initialSlots() : stored;
        long resolved = Math.max(category.initialSlots(), Math.min(capacity, category.maxSlots()));
        if (playerCapacities != null) {
            playerCapacities.put(category.id(), resolved);
        }
        return resolved;
    }

    // ---------------------------------------------------------------------
    // 加载 / 保存
    // ---------------------------------------------------------------------

    private void loadPlayer(Player player) throws Exception {
        UUID uuid = player.getUniqueId();
        synchronized (playerLock(uuid)) {
            // 重新加载时解除槽位绑定：ArcartX 缓存中的旧值不得再回写新模型，
            // 直到下次打开 UI 推送最新数据后重新绑定。
            boundCategories.remove(uuid);
            Map<String, Long> playerCapacities = new LinkedHashMap<>();
            Map<String, ItemStack[]> pages = new LinkedHashMap<>();
            for (ExtraBackpackCategory category : categories()) {
                long stored = repository.loadExtraBackpackCapacity(uuid, category.id());
                long capacity = stored < 0 ? category.initialSlots() : stored;
                long resolved = Math.max(category.initialSlots(), Math.min(capacity, category.maxSlots()));
                playerCapacities.put(category.id(), resolved);
                pages.put(category.id(), new ItemStack[(int) resolved]);
            }
            for (ExtraBackpackSlotRecord record : repository.loadExtraBackpackSlots(uuid)) {
                try {
                    ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(record.itemData()));
                    reclassifyIntoPages(player, pages, stack);
                } catch (RuntimeException ignored) {
                    // 损坏的记录跳过，避免整份背包加载失败。
                }
            }
            capacities.put(uuid, playerCapacities);
            backpacks.put(uuid, pages);
            activeCategory.putIfAbsent(uuid, categories().isEmpty() ? "" : categories().get(0).id());
            // 重分类结果（容量收缩溢出/分类调整）标脏，由周期 flush 落盘，避免进服时主线程同步写库。
            markDirty(uuid);
        }
    }

    private Map<String, ItemStack[]> pages(Player player) throws Exception {
        Map<String, ItemStack[]> pages = backpacks.get(player.getUniqueId());
        if (pages == null) {
            loadPlayer(player);
            pages = backpacks.get(player.getUniqueId());
        }
        return pages;
    }

    private void markDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    /** 序列化当前内存态为落库记录（必须在 playerLock 内调用）。 */
    private List<ExtraBackpackSlotRecord> buildSlotRecords(UUID uuid) {
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        if (pages == null) {
            return List.of();
        }
        List<ExtraBackpackSlotRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ItemStack[]> entry : pages.entrySet()) {
            ItemStack[] page = entry.getValue();
            for (int slot = 0; slot < page.length; slot++) {
                ItemStack stack = page[slot];
                if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                    continue;
                }
                String data = Base64.getEncoder().encodeToString(ItemSerializer.serialize(stack));
                records.add(new ExtraBackpackSlotRecord(uuid, entry.getKey(), slot, data, now));
            }
        }
        return records;
    }

    private void persist(UUID uuid) throws Exception {
        synchronized (playerLock(uuid)) {
            List<ExtraBackpackSlotRecord> records = buildSlotRecords(uuid);
            if (backpacks.get(uuid) == null) {
                return;
            }
            repository.saveExtraBackpackSlots(uuid, records);
        }
    }

    /** 周期异步落盘：把脏玩家的槽位快照写入数据库，失败的玩家保留脏标记等待下轮重试。 */
    private void flushDirtyPlayers() {
        for (UUID uuid : new ArrayList<>(dirtyPlayers)) {
            if (!dirtyPlayers.remove(uuid)) {
                continue;
            }
            try {
                persist(uuid);
            } catch (Exception exception) {
                dirtyPlayers.add(uuid);
                logger.warning("异步保存额外背包失败(" + uuid + "): " + exception.getMessage());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            loadPlayer(player);
        } catch (Exception exception) {
            logger.warning("加载额外背包失败(" + player.getName() + "): " + exception.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized (playerLock(uuid)) {
            try {
                // 退出时同步落盘：保证切服/关服场景下数据已完整写入后再清理内存态。
                persist(uuid);
            } catch (Exception exception) {
                logger.warning("保存额外背包失败(" + uuid + "): " + exception.getMessage());
            }
            backpacks.remove(uuid);
            capacities.remove(uuid);
            activeCategory.remove(uuid);
            boundCategories.remove(uuid);
            slotSyncSuppressed.remove(uuid);
            viewingPlayers.remove(uuid);
            denyMessageCooldown.remove(uuid);
            passwordPanelModes.remove(uuid);
            pendingDestroyCursor.remove(uuid);
            actionTokens.remove(uuid);
            dirtyPlayers.remove(uuid);
        }
        playerLocks.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        synchronized (playerLock(player.getUniqueId())) {
            String categoryId = matchCategory(stack);
            if (categoryId.isBlank()) {
                return;
            }
            try {
                ItemStack leftover = storeIntoBackpack(player, stack);
                if (leftover == null || leftover.getAmount() <= 0) {
                    event.getItem().remove();
                    event.setCancelled(true);
                    markDirty(player.getUniqueId());
                    syncCategorySlotsIfBound(player, categoryId);
                    return;
                }
                if (leftover.getAmount() != stack.getAmount()) {
                    event.getItem().setItemStack(leftover);
                    markDirty(player.getUniqueId());
                    syncCategorySlotsIfBound(player, categoryId);
                }
            } catch (Exception exception) {
                logger.warning("拾取自动分类额外背包失败(" + player.getName() + "): " + exception.getMessage());
            }
        }
    }

    private String matchCategory(ItemStack stack) {
        for (ExtraBackpackCategory category : categories()) {
            if (category.fallback() || category.nbtPath().isBlank() || category.values().isEmpty()) {
                continue;
            }
            if (matches(stack, category)) {
                return category.id();
            }
        }
        return "";
    }

    private String fallbackCategoryId() {
        ExtraBackpackCategory fallback = fallbackCategory();
        return fallback == null ? "" : fallback.id();
    }

    /** 计算物品按匹配规则应进入的分类页：命中匹配分类，否则落入兜底分类。 */
    private String resolveRouteCategory(ItemStack stack) {
        String matched = matchCategory(stack);
        return matched.isBlank() ? fallbackCategoryId() : matched;
    }

    private void reclassifyIntoPages(
        Player player,
        Map<String, ItemStack[]> pages,
        ItemStack stack
    ) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        ItemStack leftover = storeIntoPage(pages.get(matchCategory(stack)), stack);
        if (leftover != null && leftover.getAmount() > 0) {
            String fallbackId = fallbackCategoryId();
            if (!fallbackId.isBlank() && !fallbackId.equals(matchCategory(stack))) {
                leftover = storeIntoPage(pages.get(fallbackId), leftover);
            }
        }
        if (leftover != null && leftover.getAmount() > 0) {
            returnToVanilla(player, leftover);
        }
    }

    private void returnToVanilla(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            if (leftover != null && leftover.getAmount() > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private ItemStack storeIntoPage(ItemStack[] page, ItemStack stack) {
        if (page == null) {
            return stack;
        }
        ItemStack working = stack.clone();
        for (int slot = 0; slot < page.length && working.getAmount() > 0; slot++) {
            ItemStack existing = page[slot];
            if (existing != null && existing.isSimilar(working)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int move = Math.min(space, working.getAmount());
                    existing.setAmount(existing.getAmount() + move);
                    working.setAmount(working.getAmount() - move);
                }
            }
        }
        for (int slot = 0; slot < page.length && working.getAmount() > 0; slot++) {
            if (page[slot] == null || page[slot].getType().isAir()) {
                int move = Math.min(working.getMaxStackSize(), working.getAmount());
                ItemStack placed = working.clone();
                placed.setAmount(move);
                page[slot] = placed;
                working.setAmount(working.getAmount() - move);
            }
        }
        return working.getAmount() > 0 ? working : null;
    }

    /** 把物品尽量放入玩家背包（主库存+快捷栏），放不下的部分掉落在地上。 */
    private void giveToPlayer(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            if (leftover != null && leftover.getAmount() > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    /** 把物品存入匹配分类页（不做兜底），返回放不下的剩余部分（可能为 null）。 */
    private ItemStack storeIntoBackpack(Player player, ItemStack stack) throws Exception {
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = matchCategory(stack);
        ItemStack[] page = pages.get(categoryId);
        return storeIntoPage(page, stack);
    }

    /** 把物品按「匹配分类 → 兜底分类」路由存入额外背包，返回剩余部分（可能为 null）。 */
    private ItemStack storeIntoBackpackWithFallback(Player player, ItemStack stack) throws Exception {
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = matchCategory(stack);
        ItemStack leftover = storeIntoPage(pages.get(categoryId), stack);
        if (leftover != null && leftover.getAmount() > 0) {
            String fallbackId = fallbackCategoryId();
            if (!fallbackId.isBlank() && !fallbackId.equals(categoryId)) {
                leftover = storeIntoPage(pages.get(fallbackId), leftover);
            }
        }
        return leftover;
    }

    // ---------------------------------------------------------------------
    // 客户端包处理
    // ---------------------------------------------------------------------

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return handleClientPacket(player, packetId, data, null);
    }

    public boolean handleClientPacket(
        Player player,
        String packetId,
        List<String> data,
        SecondaryPasswordAccess passwordAccess
    ) {
        if (!enabled() || player == null || !player.isOnline() || packetId == null) {
            return false;
        }
        if (!("AXS_BACKPACK".equalsIgnoreCase(packetId))) {
            return false;
        }
        // 能收到该 UI 的包说明客户端界面处于打开状态（match 打开不会被 isUiOpen 记录，
        // 故用自有 viewing 集合判定；客户端每次发包都刷新该标记，关闭回调/退出时清除）。
        viewingPlayers.add(player.getUniqueId());
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (requiresOpenUi(action) && !viewingPlayers.contains(player.getUniqueId())) {
            sendMessage(player, false, message("extra-backpack.ui-required"));
            return true;
        }
        synchronized (playerLock(player.getUniqueId())) {
            boolean tokenized = requiresActionToken(action);
            String suppliedToken = data == null || data.isEmpty()
                ? "" : safe(data.get(data.size() - 1));
            if (tokenized && (suppliedToken.isBlank()
                || !suppliedToken.equals(actionTokens.get(player.getUniqueId())))) {
                sendMessage(player, false, message("extra-backpack.action-token-invalid"));
                try {
                    refresh(player);
                } catch (Exception exception) {
                    logger.warning("刷新额外背包失败(" + player.getName() + "): " + exception.getMessage());
                }
                return true;
            }
            try {
                switch (action) {
                    case "open", "refresh" -> {
                        pushCategorySlots(player, currentCategory(player));
                        refresh(player);
                    }
                    case "category" -> setCategory(player, value(data, 1, ""));
                    case "buy_slots" -> buySlots(player, parseInt(value(data, 1, "1"), 1));
                    case "destroy_request" -> requestDestroyCarried(player);
                    case "destroy_cancel" -> cancelDestroy(player);
                    case "destroy_confirm" -> confirmDestroy(player, passwordAccess);
                    case "password_set" -> passwordSet(
                        player, value(data, 1, ""), value(data, 2, ""), passwordAccess);
                    case "password_unlock" -> passwordUnlock(
                        player, value(data, 1, ""), passwordAccess);
                    case "password_clear" -> passwordClear(
                        player, value(data, 1, ""), passwordAccess);
                    case "password_panel_close" -> closePasswordPanel(player);
                    case "close" -> handleUiClosed(player);
                    default -> refresh(player);
                }
                if (tokenized) {
                    committedActionTokens.get().remove(player.getUniqueId());
                }
            } catch (Exception exception) {
                logger.warning("处理额外背包客户端包失败: " + exception.getMessage());
                sendMessage(player, false, message("extra-backpack.operation-failed"));
            }
        }
        return true;
    }

    public void open(Player player) throws Exception {
        if (!enabled() || packetBridge == null) {
            return;
        }
        pages(player);
        if (packetBridge.openUi(player, runtimeUiId)) {
            actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        }
    }

    private void setCategory(Player player, String categoryId) throws Exception {
        ExtraBackpackCategory category = category(categoryId);
        if (category != null) {
            activeCategory.put(player.getUniqueId(), category.id());
            pushCategorySlots(player, category.id());
        }
        refresh(player);
    }

    private String currentCategory(Player player) {
        String id = activeCategory.get(player.getUniqueId());
        if (id != null && category(id) != null) {
            return id;
        }
        List<ExtraBackpackCategory> list = categories();
        return list.isEmpty() ? "" : list.get(0).id();
    }

    // ---------------------------------------------------------------------
    // ArcartX 扩展槽位事件（真实槽位交互由 ArcartX 原生处理，这里只做校验与回写）
    // ---------------------------------------------------------------------

    /**
     * 扩容槽位点击守卫：仅允许已打开 UI 的玩家操作已绑定分类的已购槽位；
     * 携物点击时执行严格分类匹配——命中匹配规则的物品只能进对应分类，
     * 无命中的物品只能进兜底分类，否则取消点击并提示。
     */
    @EventHandler
    public void onExtraSlotClick(ClientExtraSlotClickEvent event) {
        SlotRef ref = parseSlotId(event.getSlotID());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        synchronized (playerLock(uuid)) {
            Set<String> bound = boundCategories.get(uuid);
            if (!enabled()
                || !viewingPlayers.contains(uuid)
                || bound == null
                || !bound.contains(ref.categoryId())) {
                event.setCancelled(true);
                return;
            }
            ExtraBackpackCategory category = category(ref.categoryId());
            Map<String, ItemStack[]> pages = backpacks.get(uuid);
            ItemStack[] page = pages == null ? null : pages.get(ref.categoryId());
            if (category == null || page == null || ref.index() < 0 || ref.index() >= page.length) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0) {
                return;
            }
            String matched = matchCategory(cursor);
            boolean allowed = ref.categoryId().equals(matched)
                || (matched.isBlank() && category.fallback());
            if (!allowed) {
                event.setCancelled(true);
                long now = System.currentTimeMillis();
                Long last = denyMessageCooldown.get(uuid);
                if (last == null || now - last > 1000L) {
                    denyMessageCooldown.put(uuid, now);
                    sendMessage(player, false, message("extra-backpack.category-mismatch"));
                }
            }
        }
    }

    /**
     * 槽位内容回写：ArcartX 完成原生点击处理后同步到模块内存态并标脏。
     * 未绑定分类的事件（进服时 ArcartX 自身缓存回放的旧值）一律忽略，防止脏数据覆盖。
     */
    @EventHandler
    public void onExtraSlotUpdate(PlayerExtraSlotUpdateEvent event) {
        SlotRef ref = parseSlotId(event.getSlotID());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (slotSyncSuppressed.contains(uuid)) {
            return;
        }
        synchronized (playerLock(uuid)) {
            Set<String> bound = boundCategories.get(uuid);
            if (bound == null || !bound.contains(ref.categoryId())) {
                return;
            }
            Map<String, ItemStack[]> pages = backpacks.get(uuid);
            ItemStack[] page = pages == null ? null : pages.get(ref.categoryId());
            if (page == null || ref.index() < 0 || ref.index() >= page.length) {
                return;
            }
            ItemStack previous = page[ref.index()];
            ItemStack next = event.getItemStack();
            boolean nextEmpty = next == null || next.getType().isAir() || next.getAmount() <= 0;
            page[ref.index()] = nextEmpty ? null : next.clone();
            markDirty(uuid);
            int oldAmount = previous == null || previous.getType().isAir() ? 0 : previous.getAmount();
            int newAmount = nextEmpty ? 0 : next.getAmount();
            boolean swapped = oldAmount > 0 && newAmount > 0 && !previous.isSimilar(next);
            if (swapped) {
                publishEvent(TOPIC_WITHDRAWN, player, Map.of(
                    "amount", Integer.toString(oldAmount),
                    "category", ref.categoryId(),
                    "slot", Integer.toString(ref.index())
                ));
                publishEvent(TOPIC_DEPOSITED, player, Map.of(
                    "amount", Integer.toString(newAmount),
                    "category", ref.categoryId(),
                    "slot", Integer.toString(ref.index())
                ));
            } else if (newAmount > oldAmount) {
                publishEvent(TOPIC_DEPOSITED, player, Map.of(
                    "amount", Integer.toString(newAmount - oldAmount),
                    "category", ref.categoryId(),
                    "slot", Integer.toString(ref.index())
                ));
            } else if (oldAmount > newAmount) {
                publishEvent(TOPIC_WITHDRAWN, player, Map.of(
                    "amount", Integer.toString(oldAmount - newAmount),
                    "category", ref.categoryId(),
                    "slot", Integer.toString(ref.index())
                ));
            }
            if (viewingPlayers.contains(uuid)) {
                try {
                    refresh(player);
                } catch (Exception exception) {
                    logger.warning("槽位变更后刷新额外背包失败(" + player.getName() + "): " + exception.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 销毁（光标物品 + 二级密码）
    // ---------------------------------------------------------------------

    private void requestDestroyCarried(Player player) throws Exception {
        ItemStack carried = player.getItemOnCursor();
        if (carried == null || carried.getType().isAir() || carried.getAmount() <= 0) {
            sendMessage(player, false, message("extra-backpack.cursor-empty"));
            refresh(player);
            return;
        }
        pendingDestroyCursor.add(player.getUniqueId());
        refresh(player);
    }

    private void cancelDestroy(Player player) throws Exception {
        pendingDestroyCursor.remove(player.getUniqueId());
        refresh(player);
    }

    private void confirmDestroy(Player player, SecondaryPasswordAccess passwordAccess) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        ItemStack carried = player.getItemOnCursor();
        if (carried == null || carried.getType().isAir() || carried.getAmount() <= 0) {
            pendingDestroyCursor.remove(player.getUniqueId());
            sendMessage(player, false, message("extra-backpack.cursor-empty"));
            refresh(player);
            return;
        }
        if (!passwordAccess.isPasswordSet(player)) {
            passwordPanelModes.put(player.getUniqueId(), "set");
            sendMessage(player, false, message("extra-backpack.password-set-required"));
            refresh(player);
            return;
        }
        if (!passwordAccess.isUnlocked(player)) {
            passwordPanelModes.put(player.getUniqueId(), "unlock");
            sendMessage(player, false, message("extra-backpack.destroy-password-required"));
            refresh(player);
            return;
        }
        destroyCursorNow(player);
    }

    private void destroyCursorNow(Player player) throws Exception {
        ItemStack carried = player.getItemOnCursor();
        if (carried == null || carried.getType().isAir() || carried.getAmount() <= 0) {
            pendingDestroyCursor.remove(player.getUniqueId());
            sendMessage(player, false, message("extra-backpack.cursor-empty"));
            refresh(player);
            return;
        }
        String destroyedName = resolveDisplayName(carried);
        int destroyedAmount = carried.getAmount();
        player.setItemOnCursor(null);
        player.updateInventory();
        pendingDestroyCursor.remove(player.getUniqueId());
        passwordPanelModes.remove(player.getUniqueId());
        sendMessage(player, true, message("extra-backpack.destroy-success"));
        markActionCommitted(player);
        publishEvent(TOPIC_DESTROYED, player, Map.of(
            "item", destroyedName,
            "amount", Integer.toString(destroyedAmount)
        ));
        refresh(player);
    }

    private void passwordSet(
        Player player,
        String oldPassword,
        String newPassword,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.set(player, oldPassword, newPassword)) {
            sendMessage(player, false, message("extra-backpack.password-set-failed"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-set-success"));
        markActionCommitted(player);
        passwordPanelModes.remove(player.getUniqueId());
        if (pendingDestroyCursor.contains(player.getUniqueId())) {
            destroyCursorNow(player);
        } else {
            refresh(player);
        }
    }

    private void passwordUnlock(
        Player player,
        String password,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.verify(player, password)) {
            sendMessage(player, false, message("extra-backpack.password-wrong"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-unlocked"));
        passwordPanelModes.remove(player.getUniqueId());
        if (pendingDestroyCursor.contains(player.getUniqueId())) {
            destroyCursorNow(player);
        } else {
            refresh(player);
        }
    }

    private void passwordClear(
        Player player,
        String password,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.clear(player, password)) {
            sendMessage(player, false, message("extra-backpack.password-wrong"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-cleared"));
        markActionCommitted(player);
        passwordPanelModes.remove(player.getUniqueId());
        refresh(player);
    }

    private void closePasswordPanel(Player player) throws Exception {
        passwordPanelModes.remove(player.getUniqueId());
        pendingDestroyCursor.remove(player.getUniqueId());
        refresh(player);
    }

    private void buySlots(Player player, int amount) throws Exception {
        String categoryId = currentCategory(player);
        ExtraBackpackCategory category = category(categoryId);
        if (category == null) {
            refresh(player);
            return;
        }
        if (amount <= 0) {
            sendMessage(player, false, message("extra-backpack.buy-invalid"));
            refresh(player);
            return;
        }
        UpgradeCost price = category.pricePerSlot();
        if (price == null) {
            sendMessage(player, false, message("extra-backpack.buy-not-configured"));
            refresh(player);
            return;
        }
        long current = resolveCapacity(player.getUniqueId(), category);
        if (current >= category.maxSlots()) {
            sendMessage(player, false, message("extra-backpack.buy-maxed"));
            refresh(player);
            return;
        }
        long actual = Math.min((long) amount, category.maxSlots() - current);
        UpgradeCost cost = new UpgradeCost(price.currencyId(), price.amount().multiply(BigDecimal.valueOf(actual)));
        if (!withdrawCost(player, cost)) {
            refresh(player);
            return;
        }
        long newCapacity = current + actual;
        try {
            repository.setExtraBackpackCapacity(player.getUniqueId(), category.id(), newCapacity, System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        applyCapacity(player, category, newCapacity);
        sendMessage(player, true, message("extra-backpack.buy-success", actual, newCapacity));
        markActionCommitted(player);
        publishEvent(TOPIC_UPGRADED, player, Map.of(
            "category", category.id(),
            "capacity", Long.toString(newCapacity),
            "cost", cost.amount().toPlainString(),
            "currency", cost.currencyId()
        ));
        refresh(player);
    }

    /** 扩容/缩容后按新容量重建该页数组（保留已有物品，溢出物品返还玩家背包）。 */
    private void applyCapacity(Player player, ExtraBackpackCategory category, long newCapacity) throws Exception {
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCapacities = capacities.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
        playerCapacities.put(category.id(), newCapacity);
        Map<String, ItemStack[]> pages = pages(player);
        ItemStack[] oldPage = pages.get(category.id());
        ItemStack[] newPage = new ItemStack[(int) newCapacity];
        if (oldPage != null) {
            int keep = Math.min(oldPage.length, newPage.length);
            System.arraycopy(oldPage, 0, newPage, 0, keep);
            for (int slot = keep; slot < oldPage.length; slot++) {
                returnToVanilla(player, oldPage[slot]);
            }
        }
        pages.put(category.id(), newPage);
        markDirty(uuid);
        syncCategorySlotsIfBound(player, category.id());
    }

    private boolean requiresOpenUi(String action) {
        return "buy_slots".equals(action)
            || "destroy_request".equals(action)
            || "destroy_confirm".equals(action)
            || "password_set".equals(action)
            || "password_unlock".equals(action)
            || "password_clear".equals(action);
    }

    private boolean requiresActionToken(String action) {
        return "buy_slots".equals(action)
            || "destroy_confirm".equals(action)
            || "password_set".equals(action)
            || "password_clear".equals(action);
    }

    private void markActionCommitted(Player player) {
        if (player != null) {
            UUID uuid = player.getUniqueId();
            committedActionTokens.get().add(uuid);
            actionTokens.put(uuid, UUID.randomUUID().toString());
        }
    }

    private void handleUiClosed(Player player) {
        actionTokens.remove(player.getUniqueId());
        viewingPlayers.remove(player.getUniqueId());
        pendingDestroyCursor.remove(player.getUniqueId());
        passwordPanelModes.remove(player.getUniqueId());
        try {
            persist(player.getUniqueId());
        } catch (Exception exception) {
            logger.warning("关闭额外背包保存失败(" + player.getName() + "): " + exception.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 管理命令 / 占位符查询支撑
    // ---------------------------------------------------------------------

    public int cachedPlayerCount() {
        return backpacks.size();
    }

    public int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    public List<String> categoryIds() {
        List<String> ids = new ArrayList<>();
        for (ExtraBackpackCategory category : categories()) {
            ids.add(category.id());
        }
        return ids;
    }

    /** 全部分类页已占用槽位合计（内存态，未加载玩家返回 0）。 */
    public long totalUsed(UUID uuid) {
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        if (pages == null) {
            return 0L;
        }
        long used = 0L;
        for (ItemStack[] page : pages.values()) {
            used += usedSlots(page);
        }
        return used;
    }

    /** 全部分类页容量合计（内存态，未加载玩家返回 0）。 */
    public long totalCapacity(UUID uuid) {
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        if (pages == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack[] page : pages.values()) {
            total += page.length;
        }
        return total;
    }

    public long categoryUsed(UUID uuid, String categoryId) {
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        ItemStack[] page = pages == null ? null : pages.get(normalizeId(categoryId));
        return page == null ? 0L : usedSlots(page);
    }

    public long categoryCapacity(UUID uuid, String categoryId) {
        Map<String, ItemStack[]> pages = backpacks.get(uuid);
        ItemStack[] page = pages == null ? null : pages.get(normalizeId(categoryId));
        return page == null ? 0L : page.length;
    }

    private long usedSlots(ItemStack[] page) {
        long used = 0L;
        for (ItemStack stack : page) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                used++;
            }
        }
        return used;
    }

    /**
     * 管理员为玩家赠送分类页槽位。在线玩家同步内存态并刷新 UI；离线玩家只写容量表。
     *
     * @return 实际增加的槽位数；分类不存在或参数非法时返回 -1
     */
    public long grantCapacity(OfflinePlayer target, String categoryId, int amount) throws Exception {
        ExtraBackpackCategory category = category(categoryId);
        if (target == null || category == null || amount <= 0) {
            return -1L;
        }
        UUID uuid = target.getUniqueId();
        synchronized (playerLock(uuid)) {
            long stored = repository.loadExtraBackpackCapacity(uuid, category.id());
            long current = stored < 0 ? category.initialSlots() : stored;
            long newCapacity = Math.max(category.initialSlots(),
                Math.min(current + amount, category.maxSlots()));
            if (newCapacity <= current) {
                return 0L;
            }
            repository.setExtraBackpackCapacity(uuid, category.id(), newCapacity, System.currentTimeMillis());
            Player online = target.getPlayer();
            if (online != null && online.isOnline() && backpacks.containsKey(uuid)) {
                applyCapacity(online, category, newCapacity);
                refresh(online);
            }
            return newCapacity - current;
        }
    }

    /**
     * 管理员把玩家分类页容量重置为初始值。在线玩家内存态同步重建（溢出物品返还背包）。
     *
     * @return 是否成功；分类不存在时返回 false
     */
    public boolean resetCapacity(OfflinePlayer target, String categoryId) throws Exception {
        ExtraBackpackCategory category = category(categoryId);
        if (target == null || category == null) {
            return false;
        }
        UUID uuid = target.getUniqueId();
        synchronized (playerLock(uuid)) {
            repository.setExtraBackpackCapacity(uuid, category.id(), category.initialSlots(), System.currentTimeMillis());
            Player online = target.getPlayer();
            if (online != null && online.isOnline() && backpacks.containsKey(uuid)) {
                applyCapacity(online, category, category.initialSlots());
                refresh(online);
            }
            return true;
        }
    }

    // ---------------------------------------------------------------------
    // 数据包构建
    // ---------------------------------------------------------------------

    private void refresh(Player player) throws Exception {
        if (packetBridge == null || runtimeUiId.isBlank()) {
            return;
        }
        packetBridge.sendPacket(player, runtimeUiId, "update", buildPacket(player));
    }

    private Map<String, Object> buildPacket(Player player) throws Exception {
        UUID uuid = player.getUniqueId();
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = currentCategory(player);
        ExtraBackpackCategory category = category(categoryId);
        // 操作令牌懒签发：UI 经 match 打开时不走 open()，必须保证首个 update 包即携带有效 token。
        String token = actionTokens.computeIfAbsent(uuid, ignored -> UUID.randomUUID().toString());

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", "AXS_BACKPACK");
        packet.put("action_token", token);
        packet.put("activeCategory", categoryId);

        Map<String, Object> categoryMap = new LinkedHashMap<>();
        int index = 0;
        for (ExtraBackpackCategory definition : categories()) {
            ItemStack[] catPage = pages.get(definition.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", definition.displayName());
            row.put("active", definition.id().equals(categoryId));
            row.put("used", catPage == null ? 0 : usedSlots(catPage));
            row.put("capacity", catPage == null ? 0 : catPage.length);
            categoryMap.put(Integer.toString(index++), row);
        }
        packet.put("categories", categoryMap);
        packet.put("categoryCount", categoryMap.size());

        ItemStack[] page = category == null ? new ItemStack[0] : pages.getOrDefault(category.id(), new ItemStack[0]);
        int used = (int) usedSlots(page);
        packet.put("slotCount", page.length);
        packet.put("used", used);
        packet.put("capacityText", used + "/" + page.length);

        if (category != null) {
            long capacity = resolveCapacity(uuid, category);
            long max = category.maxSlots();
            long remaining = Math.max(0, max - capacity);
            UpgradeCost price = category.pricePerSlot();
            boolean canBuy = price != null && capacity < max;
            packet.put("maxCapacity", max);
            packet.put("initialCapacity", category.initialSlots());
            packet.put("canBuySlots", canBuy);
            packet.put("remainingSlots", remaining);
            if (price != null) {
                packet.put("slotPriceAmount", price.amount().toPlainString());
                packet.put("slotPriceCurrency", price.currencyId());
                packet.put("buySlotsText", message("extra-backpack.buy-hint", price.amount().toPlainString(), remaining));
            } else {
                packet.put("buySlotsText", message("extra-backpack.buy-not-configured"));
            }
        } else {
            packet.put("maxCapacity", 0);
            packet.put("canBuySlots", false);
            packet.put("remainingSlots", 0);
            packet.put("buySlotsText", "");
        }

        Map<String, Object> passwordPanel = new LinkedHashMap<>();
        String passwordMode = passwordPanelModes.getOrDefault(uuid, "");
        passwordPanel.put("visible", !passwordMode.isBlank());
        passwordPanel.put("mode", passwordMode);
        packet.put("passwordPanel", passwordPanel);

        Map<String, Object> destroyPanel = new LinkedHashMap<>();
        destroyPanel.put("visible", pendingDestroyCursor.contains(uuid));
        packet.put("destroyPanel", destroyPanel);
        return packet;
    }

    // ---------------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------------

    private boolean withdrawCost(Player player, UpgradeCost cost) {
        if (cost == null) {
            return true;
        }
        var bridge = currencyBridge == null ? null : currencyBridge.bridge(cost.currencyId());
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? message("extra-backpack.unknown-currency") : bridge.unavailableReason());
            return false;
        }
        var result = bridge.withdraw(player, cost.amount());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            return false;
        }
        return true;
    }

    private void refundCost(Player player, UpgradeCost cost) {
        if (cost == null || currencyBridge == null) {
            return;
        }
        var bridge = currencyBridge.bridge(cost.currencyId());
        if (bridge != null && bridge.available()) {
            bridge.deposit(player, cost.amount());
        }
    }

    private String resolveDisplayName(ItemStack itemStack) {
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(itemStack);
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private List<String> loreLines(ItemStack stack) {
        if (stack == null) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return List.of();
        }
        return meta.getLore();
    }

    private String resolvePath(ItemStack stack, String rawPath) {
        String path = safe(rawPath).toLowerCase(Locale.ROOT);
        ItemMeta meta = stack.getItemMeta();
        if ("material".equals(path)) {
            return vanillaItemNameBridge != null ? vanillaItemNameBridge.translate(stack.getType()) : stack.getType().name();
        }
        if ("display-name".equals(path) || "name".equals(path)) {
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
            return vanillaItemNameBridge != null ? vanillaItemNameBridge.resolveDisplayName(stack) : stack.getType().name();
        }
        if ("custom-model-data".equals(path) && meta != null && meta.hasCustomModelData()) {
            return Integer.toString(meta.getCustomModelData());
        }
        if (meta == null || !path.startsWith("pdc:")) {
            return "";
        }
        String keyText = rawPath.substring(4);
        NamespacedKey key;
        if (keyText.contains(":")) {
            String[] parts = keyText.split(":", 2);
            key = new NamespacedKey(parts[0], parts[1]);
        } else {
            key = new NamespacedKey(plugin, keyText);
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String string = container.get(key, PersistentDataType.STRING);
        return string == null ? "" : string;
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    private void sendMessage(Player player, boolean success, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        player.sendMessage((success ? ChatColor.GREEN : ChatColor.RED) + text);
    }

    private int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(safe(rawValue));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String value(List<String> data, int index, String defaultValue) {
        return data != null && data.size() > index ? safe(data.get(index)) : defaultValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }
}
