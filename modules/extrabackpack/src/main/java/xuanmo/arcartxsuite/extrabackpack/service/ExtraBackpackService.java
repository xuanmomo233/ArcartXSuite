package xuanmo.arcartxsuite.extrabackpack.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseAccess;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.RondoBridge;
import xuanmo.arcartxsuite.api.event.ModuleLifecycleEvent;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;

/**
 * 额外槽位背包服务：默认关闭。启用后与玩家原版背包共存，
 * 仓库面板负责界面内的存取、排序与分类。本服务保留背包入口、扩容与销毁。
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
    private final UiResourceExporter uiResourceExporter;
    private final ExtraBackpackConfiguration configuration;
    private final MessageProvider messages;
    private final CurrencyBridgeAPI currencyManager;
    private final RondoBridge rondoBridge;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;

    private static final String UI_RESOURCE_PATH = "arcartx/ui/extrabackpack.yml";
    private static final String UI_FILE_PATH = "ui/extrabackpack_v2.yml";
    private static final Pattern LAYOUT_BOOTSTRAP = Pattern.compile(
        "(?m)^([\\t ]*)var\\.whLayoutEnabled = (?:true|false)[\\t ]*$");
    private File layoutUiFile;
    private BukkitTask layoutRefreshTask;
    private boolean stopped;
    private String runtimeUiId = "";
    private String registeredUiId = "";
    private Supplier<EventBusCapability> eventBusProvider = () -> null;
    /** 共通存储入口：仓库模块缺席时为 null，全部功能降级关闭。 */
    private Supplier<WarehouseAccess> warehouseProvider = () -> null;
    private Supplier<WarehousePanelAccess> panelProvider = () -> null;

    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<UUID>> committedActionTokens =
        ThreadLocal.withInitial(java.util.HashSet::new);

    private final ConcurrentMap<UUID, String> passwordPanelModes = new ConcurrentHashMap<>();
    /** 销毁确认状态：true 表示玩家已发起销毁且待确认/待密码验证。 */
    private final ConcurrentMap<UUID, ItemStack> pendingDestroyCursor = new ConcurrentHashMap<>();
    /** Serializes state mutation and persistence for each player independently. */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public ExtraBackpackService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        UiResourceExporter uiResourceExporter,
        ExtraBackpackConfiguration configuration,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
        CurrencyBridgeAPI currencyManager,
        RondoBridge rondoBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.currencyManager = currencyManager;
        this.rondoBridge = rondoBridge;
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

    public void setPanelProvider(Supplier<WarehousePanelAccess> provider) {
        panelProvider = provider;
    }

    private WarehouseAccess warehouse() {
        WarehouseAccess access = warehouseProvider == null ? null : warehouseProvider.get();
        return access;
    }

    public boolean enabled() {
        return configuration.extraBackpack().enabled()
            && warehouse() != null && panelProvider.get() != null;
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
            WarehouseAccess.Snapshot warehouseSnapshot = warehouse().snapshot(player);
            for (String category : categoryIds()) {
                List<ExtraBackpackAccess.SlotSnapshot> slots = new ArrayList<>();
                int viewIndex = 0;
                for (WarehouseAccess.SlotItem item : warehouseSlots) {
                    if (!category.equals(item.categoryId())) {
                        continue;
                    }
                    slots.add(new ExtraBackpackAccess.SlotSnapshot(viewIndex++, item.itemStack().clone()));
                }
                categorySnapshots.add(new ExtraBackpackAccess.CategorySnapshot(
                    category,
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
                        "category", panelProvider.get().classify(input)
                    ));
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
        String category = normalizeId(categoryId);
        if (!categoryIds().contains(category)) {
            return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
        }
        synchronized (playerLock(player.getUniqueId())) {
            try {
                // categoryId+slot 是该分类过滤视图内的序号，映射到真实仓库槽位。
                int warehouseSlot = -1;
                int viewIndex = 0;
                for (WarehouseAccess.SlotItem item : warehouse().slots(player)) {
                    if (!category.equals(item.categoryId())) {
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
                        "category", category,
                        "slot", Integer.toString(warehouseSlot)
                    ));
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
        if (!configuration.extraBackpack().enabled()) {
            logger.warning("扩展背包功能未启用，跳过 UI 注册与事件监听。");
            return;
        }
        if (warehouse() == null) {
            logger.warning("仓库访问能力不可用（WarehouseAccess 缺失），扩展背包将保持不可用直至仓库模块就绪。");
        }
        bindUi();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        stopped = true;
        if (layoutRefreshTask != null) {
            layoutRefreshTask.cancel();
            layoutRefreshTask = null;
        }
        layoutUiFile = null;
        HandlerList.unregisterAll(this);
        WarehousePanelAccess panel = panelProvider.get();
        if (panel != null) panel.close(runtimeUiId);
        actionTokens.clear();
        passwordPanelModes.clear();
        pendingDestroyCursor.clear();
        playerLocks.clear();
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
        layoutUiFile = uiFile;
        prepareLayoutBootstrap(uiFile);
        registerUiFile(uiFile);
    }

    /** 在客户端收到 UI 前写入服务器级布局能力，不依赖玩家打开界面后的数据包。 */
    private boolean prepareLayoutBootstrap(File uiFile) throws IOException {
        String source = Files.readString(uiFile.toPath(), StandardCharsets.UTF_8);
        Matcher marker = LAYOUT_BOOTSTRAP.matcher(source);
        if (!marker.find()) {
            logger.warning("额外背包 UI 缺少 whLayoutEnabled 初始化标记，保留自定义文件；请合并新版 UI 初始化段。");
            return false;
        }
        boolean warehouseLayout = warehouse() != null && panelProvider.get() != null;
        String prepared = marker.replaceFirst(Matcher.quoteReplacement(
            marker.group(1) + "var.whLayoutEnabled = " + warehouseLayout));
        if (source.equals(prepared)) return false;
        Files.writeString(uiFile.toPath(), prepared, StandardCharsets.UTF_8);
        return true;
    }

    /** 仓库热加载/卸载后重发正确布局；合并同一轮生命周期事件，避免中间状态反复下发。 */
    @EventHandler
    public void onWarehouseLifecycle(ModuleLifecycleEvent event) {
        if (stopped || !"warehouse".equals(event.moduleId())) return;
        switch (event.phase()) {
            case ENABLED, DISABLED, RELOADED, ENABLE_FAILED -> { }
            default -> { return; }
        }
        if (layoutRefreshTask != null) layoutRefreshTask.cancel();
        layoutRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            layoutRefreshTask = null;
            if (stopped || layoutUiFile == null) return;
            try {
                if (prepareLayoutBootstrap(layoutUiFile)) registerUiFile(layoutUiFile);
            } catch (Exception exception) {
                logger.warning("更新额外背包仓库布局失败: " + exception.getMessage());
            }
        });
    }

    private void registerUiFile(File uiFile) throws Exception {
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized (playerLock(uuid)) {
            passwordPanelModes.remove(uuid);
            pendingDestroyCursor.remove(uuid);
            actionTokens.remove(uuid);
        }
        playerLocks.remove(uuid);
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
        if ("warehouse_panel".equals(action)) {
            WarehousePanelAccess panel = panelProvider.get();
            if (panel != null) panel.handle(player, runtimeUiId, data.subList(1, data.size()));
            return true;
        }
        // 写操作要求 UI 处于打开状态。
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
                    case "open", "refresh" -> refresh(player);
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
        pendingDestroyCursor.put(player.getUniqueId(), carried.clone());
        refresh(player);
    }

    private void cancelDestroy(Player player) throws Exception {
        pendingDestroyCursor.remove(player.getUniqueId());
        refresh(player);
    }

    private void confirmDestroy(Player player, SecondaryPasswordAccess passwordAccess) throws Exception {
        if (!destroyTargetMatches(player)) {
            cancelDestroy(player);
            return;
        }
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
        if (!destroyTargetMatches(player)) {
            passwordPanelModes.remove(player.getUniqueId());
            cancelDestroy(player);
            return;
        }
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
        if (pendingDestroyCursor.containsKey(player.getUniqueId())) {
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
        if (pendingDestroyCursor.containsKey(player.getUniqueId())) {
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
        for (int i = 0; i < Math.min(amount, 64); i++) {
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
                "category", "all",
                "capacity", Long.toString(newUnlocked),
                "slots", Integer.toString(purchased)
            ));
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
    }

    // ---------------------------------------------------------------------
    // 管理命令 / 占位符查询支撑
    // ---------------------------------------------------------------------

    public int cachedPlayerCount() {
        return actionTokens.size();
    }

    public int dirtyPlayerCount() {
        return 0;
    }

    public List<String> categoryIds() {
        WarehousePanelAccess panel = panelProvider.get();
        return panel == null ? List.of() : panel.categoryIds();
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
        String category = normalizeId(categoryId);
        if (online == null || access == null || !categoryIds().contains(category)) {
            return 0L;
        }
        long used = 0L;
        for (WarehouseAccess.SlotItem item : access.slots(online)) {
            if (category.equals(item.categoryId())) {
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
        Map<String, Object> packet = buildPacket(player);
        String goldCurrency = currencyManager.definition("gold") == null ? "money" : "gold";
        String gold = formattedBalance(player, goldCurrency);
        String points = formattedBalance(player, "points");
        packet.put("goldBalance", gold);
        packet.put("pointsBalance", points);
        packetBridge.sendPacket(player, runtimeUiId, "update", packet);

        boolean loadGold = usesRondo(goldCurrency) && "—".equals(gold);
        boolean loadPoints = usesRondo("points") && "—".equals(points);
        if ((loadGold || loadPoints) && rondoBridge.available()) {
            UUID uuid = player.getUniqueId();
            String token = actionTokens.get(uuid);
            rondoBridge.economySnapshot(uuid).thenAccept(snapshot -> {
                if (snapshot == null || !plugin.isEnabled()) return;
                Map<String, Object> balances = new LinkedHashMap<>();
                BigDecimal goldBalance = snapshot.balance(goldCurrency);
                BigDecimal pointsBalance = snapshot.balance("points");
                if (loadGold && goldBalance != null) {
                    balances.put("goldBalance", currencyManager.format(goldCurrency, goldBalance));
                }
                if (loadPoints && pointsBalance != null) {
                    balances.put("pointsBalance", currencyManager.format("points", pointsBalance));
                }
                if (!balances.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (token.equals(actionTokens.get(uuid)) && isViewing(player)) {
                            packetBridge.sendPacket(player, runtimeUiId, "balances", balances);
                        }
                    });
                }
            });
        }
    }

    private Map<String, Object> buildPacket(Player player) throws Exception {
        UUID uuid = player.getUniqueId();
        // 操作令牌懒签发：UI 经 match 打开时不走 open()，必须保证首个 update 包即携带有效 token。
        String token = actionTokens.computeIfAbsent(uuid, ignored -> UUID.randomUUID().toString());

        WarehouseAccess access = warehouse();
        WarehouseAccess.Snapshot snapshot = access == null
            ? new WarehouseAccess.Snapshot(false, 0L, 0L, 0L, false, new WarehouseAccess.SlotPrice("", BigDecimal.ZERO))
            : access.snapshot(player);

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", "AXS_BACKPACK");
        packet.put("action_token", token);
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
        destroyPanel.put("visible", pendingDestroyCursor.containsKey(uuid));
        packet.put("destroyPanel", destroyPanel);
        return packet;
    }

    // ---------------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------------

    private String formattedBalance(Player player, String currencyId) {
        if (usesRondo(currencyId) && rondoBridge.available()) {
            BigDecimal balance = rondoBridge.peekBalance(player.getUniqueId(), currencyId);
            return balance == null ? "—" : currencyManager.format(currencyId, balance);
        }
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) {
            return "—";
        }
        BigDecimal balance = bridge.balance(player);
        return balance == null ? "—" : currencyManager.format(currencyId, balance);
    }

    private boolean usesRondo(String currencyId) {
        var definition = currencyManager.definition(currencyId);
        return definition != null && "rondo".equalsIgnoreCase(definition.provider());
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

    private boolean destroyTargetMatches(Player player) {
        ItemStack expected = pendingDestroyCursor.get(player.getUniqueId());
        return expected != null && expected.equals(player.getItemOnCursor());
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
