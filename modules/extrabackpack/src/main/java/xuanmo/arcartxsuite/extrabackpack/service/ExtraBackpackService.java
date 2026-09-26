package xuanmo.arcartxsuite.extrabackpack.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
import xuanmo.arcartxsuite.api.capability.WarehouseAccess;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.ExtraBackpackCategory;

/**
 * 额外槽位背包服务：默认关闭。启用后与玩家原版背包共存，
 * 由多个分类页（装备/材料/杂物…）组成——分类页是仓库内容的过滤器而非独立存储。
 * <p>
 * 存储共通：本模块不持有任何背包数据，所有槽位直接读写玩家个人仓库
 * （{@link WarehouseAccess}），仓库 UI 与本 UI 是同一存储的两个入口。
 * 网格 = 「匹配当前页签的仓库槽位」+「剩余空格」，空格写入经仓库自动分配。
 * <p>
 * 物品分类依据 {@link ExtraBackpackCategory} 的 NBT/lore 匹配规则，由本模块自行判断；
 * 携物点击空槽时执行严格分类匹配（命中规则的只能进对应页签，无命中只能进兜底页签）。
 */
public final class ExtraBackpackService implements Listener, ExtraBackpackAccess {

    /** EventBus 主题：物品存入额外背包。 */
    public static final String TOPIC_DEPOSITED = "axs.extrabackpack.deposited";
    /** EventBus 主题：物品从额外背包取出。 */
    public static final String TOPIC_WITHDRAWN = "axs.extrabackpack.withdrawn";
    /** EventBus 主题：槽位付费解锁。 */
    public static final String TOPIC_UPGRADED = "axs.extrabackpack.upgraded";
    /** EventBus 主题：光标物品经二级密码验证后被销毁。 */
    public static final String TOPIC_DESTROYED = "axs.extrabackpack.destroyed";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final PropBridgeAPI propBridge;
    private final UiResourceExporter uiResourceExporter;
    private final ExtraBackpackConfiguration configuration;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;

    private static final String UI_RESOURCE_PATH = "arcartx/ui/extrabackpack.yml";
    private static final String UI_FILE_PATH = "ui/extrabackpack.yml";
    /** ArcartX 扩展槽位 ID 前缀：{@code axs_eb_<分类ID>_<槽位序号>}。 */
    private static final String SLOT_ID_PREFIX = "axs_eb_";
    /** ArcartX extra_slot 配置文件名（写入 plugins/ArcartX/extra_slot/ 下）。 */
    private static final String SLOT_CONFIG_FILE = "axs_extrabackpack.yml";

    private String runtimeUiId = "";
    private String registeredUiId = "";
    private Supplier<EventBusCapability> eventBusProvider = () -> null;
    /** 共通存储入口：仓库模块缺席时为 null，全部功能降级关闭。 */
    private Supplier<WarehouseAccess> warehouseProvider = () -> null;

    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<UUID>> committedActionTokens =
        ThreadLocal.withInitial(java.util.HashSet::new);

    /** 玩家当前查看的分类页（过滤器）。 */
    private final ConcurrentMap<UUID, String> activeCategory = new ConcurrentHashMap<>();
    /** 当前网格渲染内容：格子序号 → ItemStack（含空格为 null）。 */
    private final ConcurrentMap<UUID, ItemStack[]> gridItems = new ConcurrentHashMap<>();
    /** 当前网格映射：格子序号 → 仓库槽位索引（-1 = 空格，写入时经仓库自动分配）。 */
    private final ConcurrentMap<UUID, int[]> gridMapping = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> passwordPanelModes = new ConcurrentHashMap<>();
    /** 销毁确认状态：true 表示玩家已发起销毁且待确认/待密码验证。 */
    private final Set<UUID> pendingDestroyCursor = ConcurrentHashMap.newKeySet();
    /** Serializes state mutation and persistence for each player independently. */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    /** 本次会话已推送到 ArcartX 扩展槽位缓存的分类集合（槽位事件只认已绑定分类）。 */
    private final ConcurrentMap<UUID, Set<String>> boundCategories = new ConcurrentHashMap<>();
    /** 推送槽位期间抑制 {@link PlayerExtraSlotUpdateEvent} 回写（事件同线程同步触发）。 */
    private final Set<UUID> slotSyncSuppressed = ConcurrentHashMap.newKeySet();
    /** 本模块注入 {@code SlotFolder.setting} 的槽位 ID，停用时回收。 */
    private final Set<String> injectedSlotIds = ConcurrentHashMap.newKeySet();
    /** 分类匹配拒绝提示节流（避免拖动刷屏）。 */
    private final ConcurrentMap<UUID, Long> denyMessageCooldown = new ConcurrentHashMap<>();

    public ExtraBackpackService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        PropBridgeAPI propBridge,
        UiResourceExporter uiResourceExporter,
        ExtraBackpackConfiguration configuration,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.propBridge = propBridge;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
    }

    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    /** 注入事件总线提供者（由 Module 延迟查找 capability）。 */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider == null ? () -> null : eventBusProvider;
    }

    /** 注入仓库访问能力提供者（共通存储入口，模块缺席时为 null）。 */
    public void setWarehouseProvider(Supplier<WarehouseAccess> warehouseProvider) {
        this.warehouseProvider = warehouseProvider == null ? () -> null : warehouseProvider;
    }

    private WarehouseAccess warehouse() {
        WarehouseAccess access = warehouseProvider == null ? null : warehouseProvider.get();
        return access;
    }

    public boolean enabled() {
        return configuration.extraBackpack().enabled()
            && !configuration.extraBackpack().categories().isEmpty()
            && warehouse() != null;
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
            List<WarehouseAccess.SlotItem> warehouseSlots = warehouse().slots(player);
            List<ExtraBackpackAccess.CategorySnapshot> categorySnapshots = new ArrayList<>();
            for (ExtraBackpackCategory category : categories()) {
                List<ExtraBackpackAccess.SlotSnapshot> slots = new ArrayList<>();
                int viewIndex = 0;
                for (WarehouseAccess.SlotItem item : warehouseSlots) {
                    if (!belongsTo(item.itemStack(), category)) {
                        continue;
                    }
                    slots.add(new ExtraBackpackAccess.SlotSnapshot(viewIndex++, item.itemStack().clone()));
                }
                WarehouseAccess.Snapshot warehouseSnapshot = warehouse().snapshot(player);
                categorySnapshots.add(new ExtraBackpackAccess.CategorySnapshot(
                    category.id(),
                    (int) Math.min(Integer.MAX_VALUE, warehouseSnapshot.unlockedSlots()),
                    slots.size(),
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
                WarehouseAccess.DepositResult result = warehouse().deposit(player, input);
                long stored = result.storedAmount();
                int remaining = result.remainingAmount();
                if (stored > 0) {
                    publishEvent(TOPIC_DEPOSITED, player, Map.of(
                        "amount", Long.toString(stored),
                        "category", resolveRouteCategory(input)
                    ));
                    resyncView(player);
                }
                return new ExtraBackpackAccess.DepositResult(
                    result.success(),
                    stored,
                    remaining,
                    remaining > 0 ? message("extra-backpack.page-full") : result.message()
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
                // categoryId+slot 是该分类过滤视图内的序号，映射到真实仓库槽位。
                int warehouseSlot = -1;
                int viewIndex = 0;
                for (WarehouseAccess.SlotItem item : warehouse().slots(player)) {
                    if (!belongsTo(item.itemStack(), category)) {
                        continue;
                    }
                    if (viewIndex++ == slot) {
                        warehouseSlot = item.slot();
                        break;
                    }
                }
                if (warehouseSlot < 0) {
                    return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
                }
                WarehouseAccess.WithdrawResult result = warehouse().withdraw(player, warehouseSlot, amount);
                if (result.success()) {
                    publishEvent(TOPIC_WITHDRAWN, player, Map.of(
                        "amount", Integer.toString(result.takenAmount()),
                        "category", category.id(),
                        "slot", Integer.toString(warehouseSlot)
                    ));
                    resyncView(player);
                }
                return new ExtraBackpackAccess.WithdrawResult(
                    result.success(), result.takenAmount(), result.itemStack(), result.message());
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
        if (!configuration.extraBackpack().enabled() || configuration.extraBackpack().categories().isEmpty()) {
            logger.warning("扩展背包功能未启用（extra-backpack.enabled=false 或分类为空），跳过 UI 注册与事件监听。");
            return;
        }
        if (propBridge == null || !propBridge.isAvailable()) {
            throw new IllegalStateException("ExtraBackpack 需要 ArcartX 扩展槽位桥接，当前不可用");
        }
        if (warehouse() == null) {
            logger.warning("仓库访问能力不可用（WarehouseAccess 缺失），扩展背包将保持不可用直至仓库模块就绪。");
        }
        bindUi();
        registerExtraSlots();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        gridItems.clear();
        gridMapping.clear();
        activeCategory.clear();
        boundCategories.clear();
        slotSyncSuppressed.clear();
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
        } else {
            PacketBridgeAPI.UiRegistrationResult registration = packetBridge.registerOrReloadUi("AXS:extrabackpack", uiFile);
            if (!registration.success()) {
                throw new IllegalStateException("注册额外背包 UI 失败: " + registration.message());
            }
            registeredUiId = registration.registeredUiId() == null ? "" : registration.registeredUiId();
            runtimeUiId = registration.runtimeUiId();
        }
        if (packetBridge != null && !runtimeUiId.isBlank()) {
            // 即使不经桥接注册也尝试挂关闭回调：ArcartX 已自行注册该 UI 时，
            // 桥接能取到适配器并补上 OPEN/CLOSE 跟踪，isUiOpen 对 match 打开同样生效。
            packetBridge.registerUiCloseCallback(runtimeUiId, this::handleUiClosed);
        }
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
     * 构建当前页签的过滤视图：匹配页签的仓库槽位（按槽位序）+ 剩余空格（写入时自动分配）。
     * 同时填充 {@link #gridItems} 与 {@link #gridMapping}。
     */
    private ItemStack[] buildView(Player player, String categoryId) {
        ExtraBackpackCategory category = category(categoryId);
        WarehouseAccess access = warehouse();
        if (category == null || access == null) {
            return new ItemStack[0];
        }
        List<WarehouseAccess.SlotItem> warehouseSlots = access.slots(player);
        WarehouseAccess.Snapshot snapshot = access.snapshot(player);
        long free = Math.max(0L, snapshot.unlockedSlots() - warehouseSlots.size());
        List<ItemStack> items = new ArrayList<>();
        List<Integer> mapping = new ArrayList<>();
        for (WarehouseAccess.SlotItem item : warehouseSlots) {
            if (items.size() >= category.maxSlots()) {
                break;
            }
            if (!belongsTo(item.itemStack(), category)) {
                continue;
            }
            items.add(item.itemStack().clone());
            mapping.add(item.slot());
        }
        for (long i = 0; i < free && items.size() < category.maxSlots(); i++) {
            items.add(null);
            mapping.add(-1);
        }
        ItemStack[] grid = items.toArray(new ItemStack[0]);
        int[] map = mapping.stream().mapToInt(Integer::intValue).toArray();
        gridItems.put(player.getUniqueId(), grid);
        gridMapping.put(player.getUniqueId(), map);
        return grid;
    }

    /** 物品是否属于该页签的过滤范围：命中该页匹配规则，或页签为兜底且无其他命中。 */
    private boolean belongsTo(ItemStack stack, ExtraBackpackCategory category) {
        String matched = matchCategory(stack);
        return category.id().equals(matched)
            || (matched.isBlank() && category.fallback());
    }

    /**
     * 把指定分类页的过滤视图推送到 ArcartX 扩展槽位缓存（槽位 {@code 0..maxSlots-1}，
     * 视图之外的槽位强制清空）。推送后该分类标记为已绑定，其槽位事件才会回写。
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
        ItemStack[] grid = buildView(player, category.id());
        slotSyncSuppressed.add(uuid);
        try {
            for (int i = 0; i < category.maxSlots(); i++) {
                ItemStack stack = i < grid.length ? grid[i] : null;
                handle.setSlotItemStack(
                    slotId(category.id(), i),
                    stack == null || stack.getType().isAir() ? new ItemStack(Material.AIR) : stack);
            }
        } finally {
            slotSyncSuppressed.remove(uuid);
        }
        boundCategories.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(category.id());
    }

    /** 仓库内容被外部途径（存入/拾取/扩容/API）修改后，若 UI 打开则重推当前页签视图。 */
    private void resyncView(Player player) {
        String current = currentCategory(player);
        Set<String> bound = boundCategories.get(player.getUniqueId());
        if (current.isBlank() || bound == null || !bound.contains(current) || !isViewing(player)) {
            return;
        }
        pushCategorySlots(player, current);
    }

    // ---------------------------------------------------------------------
    // 分类（过滤器语义）
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized (playerLock(uuid)) {
            gridItems.remove(uuid);
            gridMapping.remove(uuid);
            activeCategory.remove(uuid);
            boundCategories.remove(uuid);
            slotSyncSuppressed.remove(uuid);
            denyMessageCooldown.remove(uuid);
            passwordPanelModes.remove(uuid);
            pendingDestroyCursor.remove(uuid);
            actionTokens.remove(uuid);
        }
        playerLocks.remove(uuid);
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

    /** 计算物品按匹配规则应归入的页签：命中匹配分类，否则落入兜底分类。 */
    private String resolveRouteCategory(ItemStack stack) {
        String matched = matchCategory(stack);
        if (!matched.isBlank()) {
            return matched;
        }
        ExtraBackpackCategory fallback = fallbackCategory();
        return fallback == null ? "" : fallback.id();
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
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // isUiOpen 由桥接 OPEN/CLOSE 回调跟踪，match 打开同样记录；写操作要求 UI 处于打开状态。
        if (requiresOpenUi(action) && !isViewing(player)) {
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
        String resolved = list.isEmpty() ? "" : list.get(0).id();
        activeCategory.put(player.getUniqueId(), resolved);
        return resolved;
    }

    // ---------------------------------------------------------------------
    // ArcartX 扩展槽位事件（真实槽位交互由 ArcartX 原生处理，这里只做校验与回写）
    // ---------------------------------------------------------------------

    /**
     * 扩容槽位点击守卫：仅允许已打开 UI 的玩家操作当前页签的网格；
     * 携物点击时执行严格分类匹配——命中匹配规则的物品只能进对应页签，
     * 无命中的物品只能进兜底页签；空格还需仓库存在剩余槽位。
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
                || !isViewing(player)
                || bound == null
                || !bound.contains(ref.categoryId())
                || !ref.categoryId().equals(currentCategory(player))) {
                event.setCancelled(true);
                return;
            }
            ExtraBackpackCategory category = category(ref.categoryId());
            int[] mapping = gridMapping.get(uuid);
            if (category == null || mapping == null || ref.index() < 0 || ref.index() >= mapping.length) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0) {
                return;
            }
            // 空格（映射 -1）携物点击还需仓库仍有剩余槽位，否则点击放行但写库失败。
            if (mapping[ref.index()] < 0) {
                WarehouseAccess.Snapshot snapshot = warehouse().snapshot(player);
                if (snapshot.usedSlots() >= snapshot.unlockedSlots()) {
                    event.setCancelled(true);
                    sendMessage(player, false, message("extra-backpack.page-full"));
                    return;
                }
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
     * 槽位内容回写：ArcartX 完成原生点击处理后翻译为仓库写入——
     * 已占用格直接覆写/清除对应仓库槽位；空格经仓库自动分配写入。
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
            if (bound == null
                || !bound.contains(ref.categoryId())
                || !ref.categoryId().equals(currentCategory(player))) {
                return;
            }
            int[] mapping = gridMapping.get(uuid);
            if (mapping == null || ref.index() < 0 || ref.index() >= mapping.length) {
                return;
            }
            ItemStack next = event.getItemStack();
            boolean nextEmpty = next == null || next.getType().isAir() || next.getAmount() <= 0;
            int warehouseSlot = mapping[ref.index()];
            WarehouseAccess access = warehouse();
            if (access == null) {
                return;
            }
            try {
                if (warehouseSlot >= 0) {
                    access.setSlot(player, warehouseSlot, nextEmpty ? null : next.clone());
                } else if (!nextEmpty) {
                    WarehouseAccess.DepositResult result = access.deposit(player, next.clone());
                    if (result.remainingAmount() > 0) {
                        ItemStack leftover = next.clone();
                        leftover.setAmount(result.remainingAmount());
                        giveToPlayer(player, leftover);
                    }
                }
                int oldAmount = 0;
                ItemStack[] grid = gridItems.get(uuid);
                if (grid != null && ref.index() < grid.length) {
                    ItemStack previous = grid[ref.index()];
                    oldAmount = previous == null || previous.getType().isAir() ? 0 : previous.getAmount();
                }
                int newAmount = nextEmpty ? 0 : next.getAmount();
                if (newAmount > oldAmount) {
                    publishEvent(TOPIC_DEPOSITED, player, Map.of(
                        "amount", Integer.toString(newAmount - oldAmount),
                        "category", ref.categoryId(),
                        "slot", Integer.toString(warehouseSlot)
                    ));
                } else if (oldAmount > newAmount) {
                    publishEvent(TOPIC_WITHDRAWN, player, Map.of(
                        "amount", Integer.toString(oldAmount - newAmount),
                        "category", ref.categoryId(),
                        "slot", Integer.toString(warehouseSlot)
                    ));
                }
                // 回写后重建视图：分配/合并可能改变了槽位归属。
                pushCategorySlots(player, ref.categoryId());
                if (isViewing(player)) {
                    refresh(player);
                }
            } catch (Exception exception) {
                logger.warning("槽位变更后同步仓库失败(" + player.getName() + "): " + exception.getMessage());
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
        String trimmed = newPassword == null ? "" : newPassword.trim();
        int minLen = passwordAccess.minLength();
        int maxLen = passwordAccess.maxLength();
        if (trimmed.length() < minLen || trimmed.length() > maxLen) {
            sendMessage(player, false, message("extra-backpack.password-length", minLen, maxLen));
            refresh(player);
            return;
        }
        if (!passwordAccess.set(player, oldPassword, trimmed)) {
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

    /** 逐格购买仓库槽位：每次 purchaseSlot 解锁一格，价格由仓库配置决定。 */
    private void buySlots(Player player, int amount) throws Exception {
        if (amount <= 0) {
            sendMessage(player, false, message("extra-backpack.buy-invalid"));
            refresh(player);
            return;
        }
        WarehouseAccess access = warehouse();
        if (access == null) {
            sendMessage(player, false, message("extra-backpack.buy-not-configured"));
            refresh(player);
            return;
        }
        int purchased = 0;
        long newUnlocked = 0L;
        for (int i = 0; i < amount; i++) {
            WarehouseAccess.PurchaseResult result = access.purchaseSlot(player);
            if (!result.success()) {
                if (purchased == 0 && !result.message().isBlank()) {
                    sendMessage(player, false, result.message());
                }
                break;
            }
            purchased++;
            newUnlocked = result.newUnlockedSlots();
        }
        if (purchased > 0) {
            sendMessage(player, true, message("extra-backpack.buy-success", purchased, newUnlocked));
            markActionCommitted(player);
            publishEvent(TOPIC_UPGRADED, player, Map.of(
                "category", currentCategory(player),
                "capacity", Long.toString(newUnlocked),
                "slots", Integer.toString(purchased)
            ));
            resyncView(player);
        }
        refresh(player);
    }

    /** 玩家当前是否打开着额外背包 UI（桥接 OPEN/CLOSE 回调跟踪，match 打开同样记录）。 */
    private boolean isViewing(Player player) {
        return packetBridge != null && !runtimeUiId.isBlank()
            && packetBridge.isUiOpen(player, runtimeUiId);
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
        UUID uuid = player.getUniqueId();
        actionTokens.remove(uuid);
        pendingDestroyCursor.remove(uuid);
        passwordPanelModes.remove(uuid);
        gridItems.remove(uuid);
        gridMapping.remove(uuid);
        boundCategories.remove(uuid);
    }

    // ---------------------------------------------------------------------
    // 管理命令 / 占位符查询支撑
    // ---------------------------------------------------------------------

    public int cachedPlayerCount() {
        return boundCategories.size();
    }

    public int dirtyPlayerCount() {
        return 0;
    }

    public List<String> categoryIds() {
        List<String> ids = new ArrayList<>();
        for (ExtraBackpackCategory category : categories()) {
            ids.add(category.id());
        }
        return ids;
    }

    /** 全部仓库槽位已占用合计（共通存储：= 个人仓库 used）。 */
    public long totalUsed(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        WarehouseAccess access = warehouse();
        if (online == null || access == null) {
            return 0L;
        }
        return access.snapshot(online).usedSlots();
    }

    /** 仓库已解锁槽位总数（共通存储：= 个人仓库 unlocked）。 */
    public long totalCapacity(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        WarehouseAccess access = warehouse();
        if (online == null || access == null) {
            return 0L;
        }
        return access.snapshot(online).unlockedSlots();
    }

    /** 某分类页签下的占用槽位数（过滤视图）。 */
    public long categoryUsed(UUID uuid, String categoryId) {
        Player online = Bukkit.getPlayer(uuid);
        WarehouseAccess access = warehouse();
        ExtraBackpackCategory category = category(categoryId);
        if (online == null || access == null || category == null) {
            return 0L;
        }
        long used = 0L;
        for (WarehouseAccess.SlotItem item : access.slots(online)) {
            if (belongsTo(item.itemStack(), category)) {
                used++;
            }
        }
        return used;
    }

    /** 页签容量 = 仓库已解锁槽位（共通存储，各页签共享同一容量）。 */
    public long categoryCapacity(UUID uuid, String categoryId) {
        return totalCapacity(uuid);
    }

    /**
     * 管理员为玩家赠送仓库槽位（共通存储：直接作用于个人仓库 extra_slots）。
     *
     * @return 实际增加的槽位数；参数非法时返回 -1
     */
    public long grantCapacity(OfflinePlayer target, String categoryId, int amount) throws Exception {
        WarehouseAccess access = warehouse();
        if (target == null || access == null || amount <= 0) {
            return -1L;
        }
        return access.grantSlots(target, amount);
    }

    /**
     * 管理员把玩家已购槽位重置为 0（解锁数回落到仓库 base-slots）。
     *
     * @return 是否成功
     */
    public boolean resetCapacity(OfflinePlayer target, String categoryId) throws Exception {
        WarehouseAccess access = warehouse();
        if (target == null || access == null) {
            return false;
        }
        return access.resetSlots(target);
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
        String categoryId = currentCategory(player);
        // 操作令牌懒签发：UI 经 match 打开时不走 open()，必须保证首个 update 包即携带有效 token。
        String token = actionTokens.computeIfAbsent(uuid, ignored -> UUID.randomUUID().toString());

        WarehouseAccess access = warehouse();
        WarehouseAccess.Snapshot snapshot = access == null
            ? new WarehouseAccess.Snapshot(false, 0L, 0L, 0L, false, new WarehouseAccess.SlotPrice("", BigDecimal.ZERO))
            : access.snapshot(player);
        List<WarehouseAccess.SlotItem> warehouseSlots = access == null ? List.of() : access.slots(player);

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", "AXS_BACKPACK");
        packet.put("action_token", token);
        packet.put("activeCategory", categoryId);

        Map<String, Object> categoryMap = new LinkedHashMap<>();
        int index = 0;
        for (ExtraBackpackCategory definition : categories()) {
            long used = 0L;
            for (WarehouseAccess.SlotItem item : warehouseSlots) {
                if (belongsTo(item.itemStack(), definition)) {
                    used++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", definition.displayName());
            row.put("active", definition.id().equals(categoryId));
            row.put("used", used);
            row.put("capacity", snapshot.unlockedSlots());
            categoryMap.put(Integer.toString(index++), row);
        }
        packet.put("categories", categoryMap);
        packet.put("categoryCount", categoryMap.size());

        ItemStack[] grid = gridItems.getOrDefault(uuid, new ItemStack[0]);
        packet.put("slotCount", grid.length);
        packet.put("used", snapshot.usedSlots());
        packet.put("capacityText", snapshot.usedSlots() + "/" + snapshot.unlockedSlots());

        long remaining = Math.max(0L, snapshot.maxSlots() - snapshot.unlockedSlots());
        packet.put("maxCapacity", snapshot.maxSlots());
        packet.put("initialCapacity", snapshot.unlockedSlots());
        packet.put("canBuySlots", snapshot.canPurchase());
        packet.put("remainingSlots", remaining);
        WarehouseAccess.SlotPrice price = snapshot.nextSlotPrice();
        if (price != null && !price.currencyId().isBlank()) {
            packet.put("slotPriceAmount", price.amount().stripTrailingZeros().toPlainString());
            packet.put("slotPriceCurrency", price.currencyId());
            packet.put("buySlotsText",
                message("extra-backpack.buy-hint", price.amount().stripTrailingZeros().toPlainString(), remaining));
        } else {
            packet.put("buySlotsText", message("extra-backpack.buy-not-configured"));
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
