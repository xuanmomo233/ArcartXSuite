package xuanmo.arcartxsuite.warehouse.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.warehouse.crossserver.SharedEditLock;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerLockService;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerPayloadCodec;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.CategoryDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.DepositProductDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.InterestTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SharedPermissionTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseLevelDefinition;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.FixedDepositRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SecurityRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedMemberRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;
import java.util.logging.Logger;

/**
 * Warehouse 核心业务服务，统筹个人仓库、共享仓库、多货币银行、二级密码与自动拾取逻辑。
 * <p>
 * 通过 {@link PacketBridgeAPI} 与客户端 AXUI 通信，所有状态变更后回发更新包刷新界面。
 * 共享仓库使用 {@link #sharedEditLocks} 实现编辑互斥锁；启用 cross-server 时经 SDK 同步至其他子服。
 */
public final class WarehouseService implements Listener {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final String OWNER_PERSONAL = "personal";
    private static final String OWNER_SHARED = "shared";
    private static final String STORAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_menu.yml";
    private static final String STORAGE_UI_FILE_PATH = "ui/warehouse_menu.yml";
    private static final String MANAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_manage.yml";
    private static final String MANAGE_UI_FILE_PATH = "ui/warehouse_manage.yml";
    private static final String BANK_UI_RESOURCE_PATH = "arcartx/ui/warehouse_bank.yml";
    private static final String BANK_UI_FILE_PATH = "ui/warehouse_bank.yml";
    private static final int SLOT_COUNT = 54;
    private static final long MAX_AGGREGATED_AMOUNT = Integer.MAX_VALUE;
    private static final int PASSWORD_HASH_ITERATIONS = 120000;
    private static final int PASSWORD_HASH_BITS = 256;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /**
     * UI 资源导出函数。
     */
    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge;
    private final PacketGuardAPI packetGuard;
    private final UiResourceExporter uiResourceExporter;
    private final WarehouseModuleConfiguration configuration;
    private final WarehouseRepository repository;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemMatcherAPI itemMatcherSupport;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final Supplier<PickupNotifiable> pickupNotifiableSupplier;
    private final SecureRandom secureRandom = new SecureRandom();
    /** 玩家当前 UI 视图状态（ownerType / warehouseId / page / search 等）。 */
    private final ConcurrentMap<UUID, ViewState> viewStates = new ConcurrentHashMap<>();
    /** 玩家二级密码解锁过期时间戳（毫秒）。 */
    private final ConcurrentMap<UUID, Long> unlockedUntil = new ConcurrentHashMap<>();
    /** 共享仓库编辑互斥锁：共享仓库 ID → 当前编辑者（含子服 nodeId）。 */
    private final ConcurrentMap<String, SharedEditLock> sharedEditLocks = new ConcurrentHashMap<>();
    private final CrossServerAPI crossServerApi;
    private final CrossServerChannelConfig crossServerChannelConfig;
    private WarehouseCrossServerLockService crossServerLockService;
    private Supplier<EventBusCapability> eventBusProvider;
    /** 玩家上次展示仓库的时间戳（毫秒），用于冷却控制。 */
    private final ConcurrentMap<UUID, Long> showcaseCooldowns = new ConcurrentHashMap<>();
    private String storageRuntimeUiId = "";
    private String manageRuntimeUiId = "";
    private String bankRuntimeUiId = "";
    private String storageRegisteredUiId = "";
    private String manageRegisteredUiId = "";
    private String bankRegisteredUiId = "";

    public WarehouseService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        WarehouseModuleConfiguration configuration,
        WarehouseRepository repository,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        Supplier<PickupNotifiable> pickupNotifiableSupplier,
        CrossServerAPI crossServerApi,
        CrossServerChannelConfig crossServerChannelConfig
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.packetGuard = packetGuard;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.repository = repository;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.currencyBridgeManager = currencyBridgeManager;
        this.pickupNotifiableSupplier = pickupNotifiableSupplier;
        this.crossServerApi = crossServerApi;
        this.crossServerChannelConfig = crossServerChannelConfig == null
            ? CrossServerChannelConfig.disabled() : crossServerChannelConfig;
    }

    public WarehouseService(
        JavaPlugin plugin,
        PacketBridgeAPI packetBridge,
        xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        WarehouseModuleConfiguration configuration,
        WarehouseRepository repository,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        Supplier<PickupNotifiable> pickupNotifiableSupplier
    ) {
        this(plugin, packetBridge, itemStackBridge, packetGuard, uiResourceExporter, configuration,
            repository, itemSourceRegistry, itemMatcherSupport, currencyBridgeManager,
            pickupNotifiableSupplier, null, CrossServerChannelConfig.disabled());
    }

    /**
     * 启动服务：初始化数据库、绑定三套 AXUI、注册 Bukkit 事件监听。
     */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void start() throws Exception {
        repository.initialize();
        bindUis();
        if (configuration.shared().enabled()
            && crossServerChannelConfig.enabled()
            && crossServerApi != null) {
            crossServerLockService = new WarehouseCrossServerLockService(
                plugin,
                crossServerApi,
                crossServerChannelConfig,
                this::applyRemoteSharedLock,
                (unlock, ignored) -> applyRemoteSharedUnlock(unlock)
            );
            crossServerLockService.start();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 关闭服务：注销事件监听、清理 UI 回调与玩家状态、关闭数据库连接。
     */
    public void shutdown() {
        if (crossServerLockService != null) {
            crossServerLockService.shutdown();
            crossServerLockService = null;
        }
        HandlerList.unregisterAll(this);
        if (packetBridge != null) {
            packetBridge.unregisterUiCloseCallback(storageRuntimeUiId);
            packetBridge.unregisterUiCloseCallback(manageRuntimeUiId);
            packetBridge.unregisterUiCloseCallback(bankRuntimeUiId);
            if (!storageRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(storageRegisteredUiId);
            }
            if (!manageRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(manageRegisteredUiId);
            }
            if (!bankRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(bankRegisteredUiId);
            }
        }
        viewStates.clear();
        unlockedUntil.clear();
        sharedEditLocks.clear();
        repository.close();
    }

    public int cachedPlayerCount() {
        return viewStates.size();
    }

    public int dirtyPlayerCount() {
        return 0;
    }

    public boolean mythicBridgeAvailable() {
        return itemSourceRegistry.mythicBridgeAvailable();
    }

    public boolean neigeBridgeAvailable() {
        return itemSourceRegistry.neigeBridgeAvailable();
    }

    public boolean crossServerActive() {
        return crossServerLockService != null && crossServerLockService.isActive();
    }

    public int activeSharedEditLockCount() {
        return sharedEditLocks.size();
    }

    public Set<String> currencyIds() {
        return currencyBridgeManager.currencyIds();
    }

    public String runtimeUiId() {
        return storageRuntimeUiId;
    }

    /**
     * 供外部模块（如 Pickup）调用的自动入库接口。
     * 将物品存入玩家第一个个人仓库，返回存入结果。
     *
     * @param player    目标玩家
     * @param itemStack 待存入物品（会被 clone，不会修改原对象）
     * @return 存入结果，包含成功状态、已存数量、剩余数量与提示消息
     */
    public WarehouseAutoDepositable.DepositResult depositToPersonalWarehouse(Player player, ItemStack itemStack) {
        if (player == null || !player.isOnline()) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "玩家不在线。");
        }
        ItemStack stack = itemStack == null ? null : itemStack.clone();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "没有可存入物品。");
        }
        try {
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), firstPersonalWarehouseId(), stack);
            if (result.success()) {
                publishDepositEvent(player, stack, result.storedAmount());
            }
            return new WarehouseAutoDepositable.DepositResult(
                result.success(),
                result.storedAmount(),
                result.remainingAmount(),
                result.message()
            );
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("外部自动入库失败: " + exception.getMessage());
            }
            return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "自动入库失败。");
        }
    }

    private void publishDepositEvent(Player player, ItemStack stack, long storedAmount) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("material", stack.getType().name());
        payload.put("amount", String.valueOf(storedAmount));
        eventBus.publish("axs.warehouse.item_deposited", player, payload);
    }

    /**
     * 为玩家打开仓库主界面（存取界面）。
     * 会自动初始化玩家权限仓库、确保当前仓库有效，并发送 storage 更新包。
     *
     * @param player 目标玩家
     * @return 操作结果
     */
    public ActionResult openMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return ActionResult.failure("玩家不在线。");
        }
        if (storageRuntimeUiId == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure("仓库存取 UI 尚未注册。");
        }
        try {
            ensureEntitlements(player);
            ViewState state = state(player);
            ensureCurrentWarehouse(player, state);
            openStorage(player, "init");
            return ActionResult.success("已打开仓库。");
        } catch (Exception exception) {
            this.logger.warning("打开仓库失败: " + exception.getMessage());
            return ActionResult.failure("打开仓库失败，请查看控制台。");
        }
    }

    /**
     * 以只读预览模式打开目标玩家的仓库。
     * 预览模式下仅支持翻页、分类、搜索、选择和刷新，禁止存取与切换仓库。
     *
     * @param viewer      预览者
     * @param targetUuid  被预览玩家 UUID
     * @param warehouseId 指定仓库 ID，空字符串则使用默认仓库
     * @return 操作结果
     */
    public ActionResult openPreview(Player viewer, UUID targetUuid, String warehouseId) {
        if (viewer == null || !viewer.isOnline()) {
            return ActionResult.failure("玩家不在线。");
        }
        if (storageRuntimeUiId == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure("仓库存取 UI 尚未注册。");
        }
        try {
            String previewOwnerType = OWNER_PERSONAL;
            if (warehouseId != null && !warehouseId.isBlank() && !configuration.warehouses().containsKey(warehouseId)) {
                previewOwnerType = OWNER_SHARED;
            }
            ViewState previewState = ViewState.preview(targetUuid, warehouseId, previewOwnerType);
            viewStates.put(viewer.getUniqueId(), previewState);
            if (packetBridge != null) {
                packetBridge.openUi(viewer, storageRuntimeUiId);
            }
            openStorage(viewer, "init");
            return ActionResult.success("已打开仓库预览。");
        } catch (Exception exception) {
            this.logger.warning("打开仓库预览失败: " + exception.getMessage());
            return ActionResult.failure("打开仓库预览失败，请查看控制台。");
        }
    }

    /**
     * 向全服展示玩家仓库。
     * 若配置了 {@code card-id} 则发送 ArcartX 聊天卡片，否则发送可点击聊天消息。
     * 受 {@code cooldown-seconds} 冷却控制。
     *
     * @param player 展示者
     * @return 操作结果
     */
    public ActionResult showcase(Player player) {
        if (player == null || !player.isOnline()) {
            return ActionResult.failure("玩家不在线。");
        }
        var showcaseConfig = configuration.showcase();
        if (!showcaseConfig.enabled()) {
            return ActionResult.failure("仓库展示功能未启用。");
        }
        if (!player.hasPermission(showcaseConfig.permission())) {
            return ActionResult.failure("你没有展示仓库的权限。");
        }
        UUID showcaseCooldownKey = player.getUniqueId();
        Long lastShowcase = showcaseCooldowns.get(showcaseCooldownKey);
        long now = System.currentTimeMillis();
        if (lastShowcase != null && (now - lastShowcase) < showcaseConfig.cooldownSeconds() * 1000L) {
            long remaining = (showcaseConfig.cooldownSeconds() * 1000L - (now - lastShowcase)) / 1000L;
            return ActionResult.failure("展示冷却中，剩余 " + remaining + " 秒。");
        }
        showcaseCooldowns.put(showcaseCooldownKey, now);

        UUID playerUuid = player.getUniqueId();
        String displayName = player.getName();

        // 收集可展示仓库：个人仓库设置了可展示 + 主人设置了可展示的共享仓库
        List<String[]> showcaseEntries = new ArrayList<>();
        try {
            Map<String, WarehouseRecord> personalRecords = personalWarehouseMap(playerUuid);
            for (WarehouseDefinition definition : configuration.warehouses().values()) {
                WarehouseRecord record = personalRecords.get(definition.id());
                if (record != null && record.showcaseEnabled()) {
                    String name = record.customName() == null || record.customName().isBlank()
                        ? ChatColor.translateAlternateColorCodes('&', definition.displayName())
                        : record.customName();
                    showcaseEntries.add(new String[]{name, playerUuid.toString(), definition.id()});
                }
            }
            for (SharedWarehouseRecord shared : repository.loadSharedWarehouses(playerUuid)) {
                if ("owner".equalsIgnoreCase(shared.viewerRole()) && shared.showcaseEnabled()) {
                    showcaseEntries.add(new String[]{shared.name(), playerUuid.toString(), shared.id()});
                }
            }
        } catch (Exception ignored) {
        }
        if (showcaseEntries.isEmpty()) {
            return ActionResult.failure("没有可展示的仓库，请至少开启一个仓库的展示设置。");
        }

        if (showcaseConfig.useCard()) {
            String previewCommand = "/wh spreview " + playerUuid + " " + showcaseEntries.get(0)[2];
            if (packetBridge != null) {
                packetBridge.sendChatCard(player, showcaseConfig.cardId(), Map.of(
                    "player_name", displayName,
                    "preview_command", previewCommand
                ));
            }
        } else {
            net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.GOLD + "[仓库展示] " + ChatColor.WHITE + displayName + " 正在展示仓库： "
            );
            for (String[] entry : showcaseEntries) {
                net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent(
                    ChatColor.AQUA + "[" + entry[0] + "]"
                );
                link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/wh spreview " + entry[1] + " " + entry[2]
                ));
                link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(ChatColor.GRAY + "点击预览 " + displayName + " 的 " + entry[0])
                ));
                prefix.addExtra(link);
                prefix.addExtra(" ");
            }
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.spigot().sendMessage(prefix);
            }
        }
        return ActionResult.success("已展示仓库。");
    }

    /**
     * 处理客户端 UI 发来的操作包。
     * <p>
     * 支持的操作（action）详见 wiki 文档「客户端包协议」章节。
     * 所有操作均经过 {@link PacketGuardAPI} 校验，异常时自动刷新 UI。
     *
     * @param player   发送包的玩家
     * @param packetId 包 ID（应为 {@code AXS_WAREHOUSE}）
     * @param data     包数据列表，第一项通常为 action
     * @return true 表示已处理（无论成功或失败）
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !configuration.ui().packetId().equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        debug("IN player=" + player.getName() + " packetId=" + packetId + " action=" + action + " data=" + data);
        // guard injected via field
        if (packetGuard != null && !packetGuard.allow(player, "warehouse", action, configuration.debug())) {
            debug("IN-BLOCKED player=" + player.getName() + " action=" + action + " reason=packet-guard");
            return true;
        }
        try {
            ViewState currentViewState = viewStates.get(player.getUniqueId());
            if (currentViewState != null && currentViewState.previewMode()) {
                switch (action) {
                    case "page" -> setPage(player, parseInt(value(data, 1, "1"), 1));
                    case "category" -> setCategory(player, value(data, 1, "all"));
                    case "search" -> setSearch(player, value(data, 1, ""));
                    case "select" -> selectSlot(player, parsePacketSlot(value(data, 1, "-1")));
                    case "refresh" -> refreshBoth(player);
                    case "warehouse" -> selectPersonalWarehouse(player, value(data, 1, firstPersonalWarehouseId()));
                    case "shared" -> selectPreviewSharedWarehouse(player, value(data, 1, ""));
                    case "close" -> handleUiClosed(player);
                    default -> sendMessage(player, false, "预览模式下无法执行此操作。");
                }
                return true;
            }
            switch (action) {
                case "open", "storage" -> openStorage(player, "init");
                case "manage" -> openManage(player, "init");
                case "bank" -> openBank(player, "init");
                case "refresh" -> refreshBoth(player);
                case "showcase" -> {
                    ActionResult result = showcase(player);
                    if (!result.success()) {
                        sendMessage(player, false, result.message());
                    }
                }
                case "page" -> setPage(player, parseInt(value(data, 1, "1"), 1));
                case "warehouse_upgrade" -> upgradeCurrentWarehouse(player);
                case "warehouse" -> selectPersonalWarehouse(player, value(data, 1, firstPersonalWarehouseId()));
                case "shared" -> selectSharedWarehouse(player, value(data, 1, ""));
                case "shared_mode" -> setSharedMode(player, value(data, 1, "readonly"));
                case "category" -> setCategory(player, value(data, 1, "all"));
                case "search" -> setSearch(player, value(data, 1, ""));
                case "select" -> selectSlot(player, parsePacketSlot(value(data, 1, "-1")));
                case "deposit_slot" -> depositSlot(player, value(data, 1, ""), parseLong(value(data, 2, "1"), 1L));
                case "deposit_all_backpack" -> depositAllBackpack(player);
                case "withdraw" -> withdraw(player, parsePacketSlot(value(data, 1, "-1")), parseLong(value(data, 2, "1"), 1L), false);
                case "withdraw_all" -> withdraw(player, parsePacketSlot(value(data, 1, "-1")), Long.MAX_VALUE, true);
                case "bank_deposit" -> bankDeposit(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "bank_withdraw" -> bankWithdraw(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "fixed_create" -> createFixedDeposit(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "fixed_claim" -> claimFixedDeposit(player, value(data, 1, ""));
                case "shared_create" -> createSharedWarehouse(player, value(data, 1, "共享仓库"));
                case "shared_rename" -> renameSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_showcase_toggle" -> toggleSharedWarehouseShowcase(player, value(data, 1, ""));
                case "personal_rename" -> renamePersonalWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "personal_showcase_toggle" -> togglePersonalWarehouseShowcase(player, value(data, 1, ""));
                case "shared_delete" -> deleteSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_invite" -> inviteSharedMember(player, value(data, 1, ""), value(data, 2, ""), value(data, 3, "member"));
                case "shared_remove" -> removeSharedMember(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_transfer" -> transferSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "password_set" -> setPassword(player, value(data, 1, ""));
                case "password_unlock" -> unlockPassword(player, value(data, 1, ""));
                case "password_lock" -> {
                    unlockedUntil.remove(player.getUniqueId());
                    sendMessage(player, true, "已锁定二级密码会话。");
                    refreshBoth(player);
                }
                case "password_clear" -> clearPassword(player, value(data, 1, ""));
                case "toggle_auto_pickup" -> toggleAutoPickup(player, "pickup");
                case "toggle_auto_mythic" -> toggleAutoPickup(player, "mythic");
                case "toggle_auto_notify" -> toggleAutoPickup(player, "notify");
                default -> refreshBoth(player);
            }
        } catch (Exception exception) {
            this.logger.warning("处理仓库客户端包失败: " + exception.getMessage());
            debug("IN-ERROR player=" + player.getName() + " action=" + action + " error=" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            sendMessage(player, false, "操作失败，请查看控制台。");
            try {
                refreshBoth(player);
            } catch (Exception refreshException) {
                this.logger.warning("刷新仓库 UI 失败: " + refreshException.getMessage());
            }
        }
        return true;
    }

    /**
     * 异步生成玩家仓库概览信息，用于管理员查询。
     *
     * @param playerUuid   玩家 UUID
     * @param playerName   玩家名称（用于显示）
     * @param callback     成功回调，接收格式化后的信息行列表
     * @param errorCallback 失败回调，接收错误信息
     */
    public void describePlayer(UUID playerUuid, String playerName, Consumer<List<String>> callback, Consumer<String> errorCallback) {
        try {
            long total = totalItems(playerUuid);
            long used = personalUsed(playerUuid);
            long capacity = personalCapacity(playerUuid);
            List<FixedDepositRecord> deposits = repository.loadFixedDeposits(playerUuid);
            List<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(playerUuid);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "玩家: " + ChatColor.WHITE + playerName);
            lines.add(ChatColor.GRAY + "个人仓库: " + ChatColor.WHITE + used + "/" + capacity + " 格，合计 " + total + " 件");
            lines.add(ChatColor.GRAY + "共享仓库: " + ChatColor.WHITE + shared.size() + " 个");
            lines.add(ChatColor.GRAY + "定期存款: " + ChatColor.WHITE + deposits.stream().filter(d -> !d.claimed()).count() + " 笔未领取");
            callback.accept(lines);
        } catch (Exception exception) {
            errorCallback.accept("读取仓库信息失败: " + exception.getMessage());
        }
    }

    /**
     * 管理员清除玩家二级密码。
     *
     * @param playerUuid 目标玩家 UUID
     * @param callback   操作结果回调
     */
    public void adminClearSecondaryPassword(UUID playerUuid, Consumer<ActionResult> callback) {
        try {
            repository.clearSecurity(playerUuid);
            unlockedUntil.remove(playerUuid);
            callback.accept(ActionResult.success("已清除该玩家的仓库二级密码。"));
        } catch (Exception exception) {
            callback.accept(ActionResult.failure("清除二级密码失败: " + exception.getMessage()));
        }
    }

    /**
     * 管理员调整玩家银行余额。支持 set / add / take 三种模式。
     *
     * @param playerUuid 目标玩家 UUID
     * @param currencyId 货币 ID
     * @param mode       操作模式：set / add / take
     * @param amountText 金额文本
     * @param callback   操作结果回调
     */
    public void adminAdjustWallet(UUID playerUuid, String currencyId, String mode, String amountText, Consumer<ActionResult> callback) {
        try {
            String normalizedCurrency = normalizeId(currencyId);
            if (!configuration.currencies().containsKey(normalizedCurrency)) {
                callback.accept(ActionResult.failure("未知货币: " + normalizedCurrency));
                return;
            }
            BigDecimal amount = parseDecimal(amountText);
            BigDecimal current = bankBalance(playerUuid, normalizedCurrency);
            BigDecimal updated = switch (normalizeId(mode)) {
                case "set" -> amount;
                case "add" -> current.add(amount);
                case "take" -> current.subtract(amount).max(BigDecimal.ZERO);
                default -> null;
            };
            if (updated == null) {
                callback.accept(ActionResult.failure("未知银行操作模式: " + mode));
                return;
            }
            repository.setBankBalance(playerUuid, normalizedCurrency, updated, System.currentTimeMillis());
            callback.accept(ActionResult.success("已更新玩家银行 " + normalizedCurrency + "=" + formatCurrency(normalizedCurrency, updated) + "。"));
        } catch (Exception exception) {
            callback.accept(ActionResult.failure("更新银行失败: " + exception.getMessage()));
        }
    }

    public long totalItems(UUID playerUuid) {
        try {
            return personalRecords(playerUuid).stream()
                .flatMap(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).stream())
                .mapToLong(SlotItemRecord::amount)
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalUsed(UUID playerUuid) {
        try {
            return personalRecords(playerUuid).stream()
                .mapToLong(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).size())
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalCapacity(UUID playerUuid) {
        try {
            long capacity = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                WarehouseDefinition definition = configuration.warehouse(record.warehouseId());
                WarehouseLevelDefinition level = definition == null ? null : definition.level(record.level());
                capacity += level == null ? SLOT_COUNT : Math.max(SLOT_COUNT, level.capacity());
            }
            return capacity;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long categoryAmount(UUID playerUuid, String categoryId) {
        try {
            String normalized = normalizeId(categoryId);
            return personalRecords(playerUuid).stream()
                .flatMap(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).stream())
                .filter(item -> normalized.equals(item.categoryId()))
                .mapToLong(SlotItemRecord::amount)
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public BigDecimal bankBalance(UUID playerUuid, String currencyId) {
        try {
            return repository.loadBankBalances(playerUuid).getOrDefault(normalizeId(currencyId), BigDecimal.ZERO);
        } catch (Exception exception) {
            return BigDecimal.ZERO;
        }
    }

    public long fixedActive(UUID playerUuid, String currencyId) {
        try {
            String normalized = normalizeId(currencyId);
            return repository.loadFixedDeposits(playerUuid).stream().filter(d -> !d.claimed() && normalized.equals(d.currencyId())).count();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long fixedMatured(UUID playerUuid, String currencyId) {
        try {
            long now = System.currentTimeMillis();
            String normalized = normalizeId(currencyId);
            return repository.loadFixedDeposits(playerUuid).stream().filter(d -> !d.claimed() && d.maturesAt() <= now && normalized.equals(d.currencyId())).count();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public int sharedOwnedCount(UUID playerUuid) {
        try {
            return repository.countOwnedSharedWarehouses(playerUuid);
        } catch (Exception exception) {
            return 0;
        }
    }

    public int sharedJoinedCount(UUID playerUuid) {
        try {
            return repository.loadSharedWarehouses(playerUuid).size();
        } catch (Exception exception) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        viewStates.remove(playerUuid);
        unlockedUntil.remove(playerUuid);
        releaseSharedLocks(playerUuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isOnline()) {
            return;
        }
        ViewState state = viewStates.get(player.getUniqueId());
        boolean pickup = state != null ? state.autoPickup() : configuration.pickup().autoStoreOnPickup();
        if (!pickup) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        try {
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), firstPersonalWarehouseId(), stack);
            if (!result.success()) {
                return;
            }
            stack.setAmount(result.remainingAmount());
            if (result.remainingAmount() <= 0) {
                event.setCancelled(true);
                event.getItem().remove();
            }
            boolean notify = state != null ? state.autoPickupNotify() : configuration.pickup().notifyOnAutoStore();
            if (notify) {
                // 如果 Pickup 通知模式已为该玩家提供 HUD 提示，则跳过聊天栏消息
                PickupNotifiable pickupNotifiable = pickupNotifiableSupplier.get();
                boolean hudActive = pickupNotifiable != null && pickupNotifiable.isNotificationActive(player.getUniqueId());
                if (!hudActive) {
                    player.sendMessage(PREFIX + ChatColor.GREEN + "已自动存入仓库 " + result.storedAmount() + " 件物品。");
                }
            }
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("自动入库失败: " + exception.getMessage());
            }
        }
    }

    /**
     * 导出并注册三套 AXUI 文件（storage / manage / bank），同时为每套 UI 注册关闭回调。
     */
    private void bindUis() throws Exception {
        storageRuntimeUiId = bindUi(
            configuration.ui().uiId(),
            configuration.ui().uiFile().isBlank() ? STORAGE_UI_RESOURCE_PATH : configuration.ui().uiFile(),
            STORAGE_UI_FILE_PATH,
            "storage"
        );
        manageRuntimeUiId = bindUi(
            configuration.ui().manageUiId(),
            configuration.ui().manageUiFile().isBlank() ? MANAGE_UI_RESOURCE_PATH : configuration.ui().manageUiFile(),
            MANAGE_UI_FILE_PATH,
            "manage"
        );
        bankRuntimeUiId = bindUi(
            configuration.ui().bankUiId(),
            configuration.ui().bankUiFile().isBlank() ? BANK_UI_RESOURCE_PATH : configuration.ui().bankUiFile(),
            BANK_UI_FILE_PATH,
            "bank"
        );
        if (packetBridge != null) {
            packetBridge.registerUiCloseCallback(storageRuntimeUiId, this::handleUiClosed);
            packetBridge.registerUiCloseCallback(manageRuntimeUiId, this::handleUiClosed);
            packetBridge.registerUiCloseCallback(bankRuntimeUiId, this::handleUiClosed);
        }
    }

    /**
     * 导出单个 UI 资源文件并注册到 PacketBridge。
     *
     * @param configuredId   配置中的 UI ID
     * @param resourcePath   jar 内资源路径
     * @param destinationPath 导出到磁盘的相对路径
     * @param uiKind         UI 类型标识（storage / manage / bank）
     * @return 运行时 UI ID
     */
    private String bindUi(String configuredId, String resourcePath, String destinationPath, String uiKind) throws Exception {
        PacketBridgeAPI bridge = packetBridge;
        File uiFile = uiResourceExporter.export(resourcePath, destinationPath, configuration.ui().overwriteUiFiles());
        if (bridge == null || !configuration.ui().registerUiOnEnable()) {
            String runtime = xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.normalizeUiId(configuredId, uiFile);
            if (bridge != null) {
                this.logger.fine("Warehouse UI 自动注册已关闭，将直接使用 UI 标识: " + runtime);
            }
            return runtime;
        }
        xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult registration = bridge.registerOrReloadUi(configuredId, uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("注册 Warehouse UI 失败: " + registration.message());
        }
        String registered = registration.registeredUiId() == null ? "" : registration.registeredUiId();
        switch (uiKind) {
            case "storage" -> storageRegisteredUiId = registered;
            case "bank" -> bankRegisteredUiId = registered;
            default -> manageRegisteredUiId = registered;
        }
        return registration.runtimeUiId();
    }

    /**
     * 打开仓库存取界面并发送初始化/更新数据包。
     * 延迟 2 ticks 发送，避免低性能客户端 UI 加载未完成时丢失 packet。
     */
    private void openStorage(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        if (state(player).sharedEditMode() && !acquireCurrentSharedLock(player, state(player))) {
            state(player).setSharedEditMode(false);
        }
        packetBridge.openUi(player, storageRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendStorage(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延迟发送仓库数据包失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * 打开共享管理界面并发送初始化/更新数据包。
     * 延迟 2 ticks 发送，避免低性能客户端 UI 加载未完成时丢失 packet。
     */
    private void openManage(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        packetBridge.openUi(player, manageRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendManage(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延迟发送管理数据包失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * 打开银行界面并发送初始化/更新数据包。
     * 延迟 2 ticks 发送，避免低性能客户端 UI 加载未完成时丢失 packet。
     */
    private void openBank(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        packetBridge.openUi(player, bankRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendBank(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延迟发送银行数据包失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * 同时刷新 storage、manage、bank 三个界面的数据包。
     */
    private void refreshBoth(Player player) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        sendStorage(player, "update");
        sendManage(player, "update");
        sendBank(player, "update");
    }

    private void sendStorage(Player player, String handler) throws Exception {
        if (packetBridge != null && !storageRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildStoragePacket(player, state(player));
            debug("OUT storage player=" + player.getName() + " uiId=" + storageRuntimeUiId + " handler=" + handler + " " + summarizeStoragePacket(packet));
            boolean success = packetBridge.sendPacket(player, storageRuntimeUiId, handler, packet);
            debug("OUT storage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void sendManage(Player player, String handler) throws Exception {
        if (packetBridge != null && !manageRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildManagePacket(player, state(player));
            debug("OUT manage player=" + player.getName() + " uiId=" + manageRuntimeUiId + " handler=" + handler + " " + summarizeManagePacket(packet));
            boolean success = packetBridge.sendPacket(player, manageRuntimeUiId, handler, packet);
            debug("OUT manage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void sendBank(Player player, String handler) throws Exception {
        if (packetBridge != null && !bankRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildBankPacket(player, state(player));
            debug("OUT bank player=" + player.getName() + " uiId=" + bankRuntimeUiId + " handler=" + handler + " balances=" + mapValue(packet.get("balances")).size());
            boolean success = packetBridge.sendPacket(player, bankRuntimeUiId, handler, packet);
            debug("OUT bank-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void handleUiClosed(Player player) {
        if (player == null) {
            return;
        }
        releaseCurrentSharedLock(player);
        // 预览模式下关闭 UI 即退出预览状态，避免玩家"卡"在预览中
        ViewState state = viewStates.get(player.getUniqueId());
        if (state != null && state.previewMode()) {
            viewStates.remove(player.getUniqueId());
        }
    }

    /**
     * 构建仓库存取界面的数据包，包含分页槽位、选中物品、背包信息、容量与权限状态。
     */
    private Map<String, Object> buildStoragePacket(Player player, ViewState state) throws Exception {
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        String slotOrder = visibleSlots.stream().map(i -> String.valueOf(i.slot())).collect(Collectors.joining(","));
        debug("buildStoragePacket visibleSlots order: " + slotOrder);
        Map<String, Object> slots = new LinkedHashMap<>();
        Map<String, Object> packet = basePacket(player, state);
        int pageSize = SLOT_COUNT;
        int pageTotal = Math.max(1, (visibleSlots.size() + pageSize - 1) / pageSize);
        state.setPage(Math.max(1, Math.min(state.page(), pageTotal)));
        int start = (state.page() - 1) * pageSize;
        for (int displaySlot = 0; displaySlot < SLOT_COUNT; displaySlot++) {
            int index = start + displaySlot;
            SlotItemRecord item = index >= 0 && index < visibleSlots.size()
                ? visibleSlots.get(index)
                : null;
            Map<String, Object> row = item != null
                ? slotPacket(item)
                : emptySlotPacket(displaySlot);
            String key = String.valueOf(displaySlot);
            row.put("key", key);
            slots.put(key, row);
            if (item != null) {
                String itemJson = displayItemJson(item);
                if (!itemJson.isBlank()) {
                    packet.put("slotItemJson" + key, itemJson);
                }
            }
        }
        String slotKeys = String.join(",", slots.keySet());
        debug("buildStoragePacket slots keys order: " + slotKeys);
        int selectedDisplaySlot = selectedDisplaySlot(state, visibleSlots);
        SlotItemRecord selected = validSlot(state.selectedSlot())
            ? repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot()).orElse(null)
            : null;
        packet.put("ui", "storage");
        packet.put("slots", slots);
        packet.put("slotCount", SLOT_COUNT);
        packet.put("selectedSlot", selectedDisplaySlot);
        packet.put("selectedActualSlot", state.selectedSlot());
        packet.put("selectedItem", selected == null ? emptySelectionPacket() : slotPacket(selected));
        packet.put("backpack", backpackPacket(player));
        packet.put("page", state.page());
        packet.put("pageTotal", pageTotal);
        packet.put("pageText", state.page() + "/" + pageTotal);
        packet.put("usedSlots", loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).size());
        packet.put("matchedSlots", visibleSlots.size());
        putCapacityFields(player, state, packet);
        packet.put("readOnly", !canModifyCurrent(player, state));
        packet.put("sharedEditMode", OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", sharedCanEdit(player, state));
        packet.put("lockOwner", lockOwnerName(state));
        packet.put("maxPersonalCount", ((java.util.Map<?,?>) packet.getOrDefault("personalWarehouses", java.util.Map.of())).size());
        packet.put("maxSharedCount", ((java.util.Map<?,?>) packet.getOrDefault("sharedWarehouses", java.util.Map.of())).size());
        packet.put("maxCategoryCount", ((java.util.Map<?,?>) packet.getOrDefault("categories", java.util.Map.of())).size());
        return packet;
    }

    private void putFlatSlotFields(Map<String, Object> packet, int slot, Map<String, Object> row) {
        packet.put("slotJson" + slot, row.getOrDefault("itemJson", ""));
        packet.put("slotAmount" + slot, row.getOrDefault("amount", 0L));
        packet.put("slotEmpty" + slot, row.getOrDefault("empty", true));
        packet.put("slotMatched" + slot, row.getOrDefault("matched", true));
        packet.put("slotActual" + slot, row.getOrDefault("slot", -1));
    }

    private List<SlotItemRecord> visibleSlots(ViewState state) {
        String normalizedSearch = normalizeToken(state.search());
        return loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).stream()
            .filter(item -> slotMatches(item, state.categoryId(), normalizedSearch))
            .sorted(Comparator.comparingInt(SlotItemRecord::slot))
            .toList();
    }

    private int selectedDisplaySlot(ViewState state, List<SlotItemRecord> visibleSlots) {
        if (!validSlot(state.selectedSlot())) {
            return -1;
        }
        int start = (Math.max(1, state.page()) - 1) * SLOT_COUNT;
        for (int index = start; index < Math.min(visibleSlots.size(), start + SLOT_COUNT); index++) {
            if (visibleSlots.get(index).slot() == state.selectedSlot()) {
                return index - start;
            }
        }
        return -1;
    }

    private int actualSlotFromDisplay(ViewState state, int displaySlot) {
        if (displaySlot < 0 || displaySlot >= SLOT_COUNT) {
            return -1;
        }
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        int index = (Math.max(1, state.page()) - 1) * SLOT_COUNT + displaySlot;
        return index >= 0 && index < visibleSlots.size() ? visibleSlots.get(index).slot() : -1;
    }

    /**
     * 构建共享管理界面的数据包，包含成员列表、搜索结果、自动拾取设置等。
     */
    private Map<String, Object> buildManagePacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> sharedMembers = sharedMemberPacket(player, state);
        Map<String, Object> searchResults = searchResultPacket(state);
        packet.put("ui", "manage");
        putCapacityFields(player, state, packet);
        packet.put("sharedMembers", sharedMembers);
        packet.put("sharedMemberTexts", fieldMap(sharedMembers, "text"));
        packet.put("sharedMemberNames", fieldMap(sharedMembers, "name"));
        packet.put("sharedMemberRoles", fieldMap(sharedMembers, "role"));
        packet.put("searchResults", searchResults);
        packet.put("searchResultTexts", fieldMap(searchResults, "text"));
        packet.put("searchResultSlots", fieldMap(searchResults, "slot"));
        packet.put("readOnly", !canModifyCurrent(player, state));
        packet.put("sharedEditMode", OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", sharedCanEdit(player, state));
        packet.put("lockOwner", lockOwnerName(state));
        packet.put("autoPickup", state.autoPickup());
        packet.put("autoPickupMythic", state.autoPickupMythic());
        packet.put("autoPickupNotify", state.autoPickupNotify());
        packet.put("showcaseEnabled", currentShowcaseEnabled(player, state));
        packet.put("maxManageCount", ((java.util.Map<?,?>) packet.getOrDefault("manageWarehouses", java.util.Map.of())).size());
        packet.put("maxMemberCount", sharedMembers.size());
        return packet;
    }

    /**
     * 构建银行界面的数据包，包含活期余额、定期产品和当前定期列表。
     */
    private Map<String, Object> buildBankPacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> balances = bankBalancePacket(player);
        Map<String, Object> products = depositProductPacket(player);
        Map<String, Object> fixedDeposits = fixedDepositPacket(player);
        packet.put("ui", "bank");
        packet.put("balances", balances);
        packet.put("balanceTexts", fieldMap(balances, "text"));
        packet.put("products", products);
        packet.put("productTexts", fieldMap(products, "text"));
        packet.put("fixedDeposits", fixedDeposits);
        packet.put("fixedDepositTexts", fieldMap(fixedDeposits, "text"));
        packet.put("maxBalanceCount", balances.size());
        packet.put("maxProductCount", products.size());
        packet.put("maxFixedCount", fixedDeposits.size());
        return packet;
    }

    private Map<String, Object> basePacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> categories = categoryPacket();
        Map<String, Object> personalWarehouses;
        Map<String, Object> sharedWarehouses;
        if (state.previewMode() && state.previewTargetUuid() != null) {
            personalWarehouses = previewPersonalWarehousePacket(state.previewTargetUuid());
            sharedWarehouses = previewSharedWarehousePacket(state.previewTargetUuid());
        } else {
            personalWarehouses = personalWarehousePacket(player);
            sharedWarehouses = sharedWarehousePacket(player);
        }
        Map<String, Object> manageWarehouses = manageWarehousePacket(personalWarehouses, sharedWarehouses, state);
        packet.put("packetId", configuration.ui().packetId());
        packet.put("ownerType", state.ownerType());
        packet.put("ownerId", state.ownerId());
        packet.put("warehouseId", state.warehouseId());
        packet.put("warehouseName", currentWarehouseName(player, state));
        packet.put("categoryId", state.categoryId());
        packet.put("search", state.search());
        packet.put("categories", categories);
        packet.put("categoryTexts", fieldMap(categories, "text"));
        packet.put("unlocked", isSecondaryUnlocked(player.getUniqueId()));
        packet.put("hasPassword", repository.loadSecurity(player.getUniqueId()).isPresent());
        packet.put("personalWarehouses", personalWarehouses);
        packet.put("personalWarehouseTexts", fieldMap(personalWarehouses, "text"));
        packet.put("sharedWarehouses", sharedWarehouses);
        packet.put("sharedWarehouseStorageTexts", fieldMap(sharedWarehouses, "storageText"));
        packet.put("sharedWarehouseManageTexts", fieldMap(sharedWarehouses, "manageText"));
        packet.put("manageWarehouses", manageWarehouses);
        packet.put("manageWarehouseTexts", fieldMap(manageWarehouses, "text"));
        packet.put("manageWarehouseTypes", fieldMap(manageWarehouses, "ownerType"));
        packet.put("manageWarehouseTargets", fieldMap(manageWarehouses, "target"));
        packet.put("manageWarehouseSelected", fieldMap(manageWarehouses, "selected"));
        packet.put("sharedCreateCostText", sharedCreateCostText());
        packet.put("sharedRoleOwnerName", sharedRoleName("owner"));
        packet.put("sharedRoleMemberName", sharedRoleName("member"));
        packet.put("sharedRoleViewerName", sharedRoleName("viewer"));
        packet.put("storageUiId", storageRuntimeUiId);
        packet.put("manageUiId", manageRuntimeUiId);
        packet.put("bankUiId", bankRuntimeUiId);
        return packet;
    }

    private Map<String, Object> fieldMap(Map<String, Object> rows, String field) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rows.entrySet()) {
            Object row = entry.getValue();
            if (row instanceof Map<?, ?> map) {
                Object value = map.get(field);
                result.put(entry.getKey(), value == null ? "" : value);
            } else {
                result.put(entry.getKey(), "");
            }
        }
        return result;
    }

    private void setCategory(Player player, String categoryId) throws Exception {
        ViewState state = state(player);
        state.setCategoryId(normalizeId(categoryId).isBlank() ? "all" : normalizeId(categoryId));
        state.setPage(1);
        clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void setSearch(Player player, String search) throws Exception {
        ViewState state = state(player);
        state.setSearch(crop(safe(search), 32));
        state.setPage(1);
        clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void setPage(Player player, int page) throws Exception {
        ViewState state = state(player);
        state.setPage(Math.max(1, page));
        clearFilteredSelection(state);
        sendStorage(player, "update");
    }

    private void selectSlot(Player player, int displaySlot) throws Exception {
        ViewState state = state(player);
        state.setSelectedSlot(actualSlotFromDisplay(state, displaySlot));
        sendStorage(player, "update");
    }

    private void selectPersonalWarehouse(Player player, String warehouseId) throws Exception {
        releaseCurrentSharedLock(player);
        ViewState state = state(player);
        state.setOwnerType(OWNER_PERSONAL);
        if (state.previewMode() && state.previewTargetUuid() != null) {
            state.setOwnerId(state.previewTargetUuid().toString());
        } else {
            state.setOwnerId(player.getUniqueId().toString());
        }
        state.setWarehouseId(normalizeId(warehouseId).isBlank() ? firstPersonalWarehouseId() : normalizeId(warehouseId));
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        ensureCurrentWarehouse(player, state);
        refreshBoth(player);
    }

    private void selectSharedWarehouse(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> selected = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(shared -> shared.id().equalsIgnoreCase(sharedId))
            .findFirst();
        if (selected.isEmpty()) {
            sendMessage(player, false, "你不在该共享仓库中。");
            refreshBoth(player);
            return;
        }
        releaseCurrentSharedLock(player);
        ViewState state = state(player);
        SharedWarehouseRecord shared = selected.get();
        state.setOwnerType(OWNER_SHARED);
        state.setOwnerId(shared.id());
        state.setWarehouseId(shared.id());
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        refreshBoth(player);
    }

    private void selectPreviewSharedWarehouse(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> selected = repository.loadSharedWarehousesByOwner(state(player).previewTargetUuid()).stream()
            .filter(shared -> shared.id().equalsIgnoreCase(sharedId) && shared.showcaseEnabled())
            .findFirst();
        if (selected.isEmpty()) {
            sendMessage(player, false, "该共享仓库不可预览。");
            refreshBoth(player);
            return;
        }
        ViewState state = state(player);
        SharedWarehouseRecord shared = selected.get();
        state.setOwnerType(OWNER_SHARED);
        state.setOwnerId(shared.id());
        state.setWarehouseId(shared.id());
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        refreshBoth(player);
    }

    private void setSharedMode(Player player, String mode) throws Exception {
        ViewState state = state(player);
        if (!OWNER_SHARED.equals(state.ownerType())) {
            sendMessage(player, false, "当前不是共享仓库。");
            refreshBoth(player);
            return;
        }
        if ("edit".equalsIgnoreCase(normalizeId(mode))) {
            if (!sharedCanEdit(player, state)) {
                state.setSharedEditMode(false);
                sendMessage(player, false, "你没有该共享仓库的存取权限。");
                refreshBoth(player);
                return;
            }
            state.setSharedEditMode(true);
            if (!acquireCurrentSharedLock(player, state)) {
                state.setSharedEditMode(false);
            }
            refreshBoth(player);
            return;
        }
        state.setSharedEditMode(false);
        releaseCurrentSharedLock(player);
        sendMessage(player, true, "已切换为共享仓库只读模式。");
        refreshBoth(player);
    }

    private void toggleAutoPickup(Player player, String kind) throws Exception {
        ViewState state = state(player);
        switch (kind) {
            case "pickup" -> {
                state.setAutoPickup(!state.autoPickup());
                sendMessage(player, true, "自动入库: " + (state.autoPickup() ? "已开启" : "已关闭"));
            }
            case "mythic" -> {
                state.setAutoPickupMythic(!state.autoPickupMythic());
                sendMessage(player, true, "自动存储怪物战利品: " + (state.autoPickupMythic() ? "已开启" : "已关闭"));
            }
            case "notify" -> {
                state.setAutoPickupNotify(!state.autoPickupNotify());
                sendMessage(player, true, "入库通知: " + (state.autoPickupNotify() ? "已开启" : "已关闭"));
            }
        }
        refreshBoth(player);
    }

    /**
     * 将玩家背包指定槽位的物品存入当前仓库。
     * 会检查只读权限、黑名单和容量上限，成功时扣除背包物品并刷新 UI。
     * 支持指定存入数量（requestedAmount），若大于实际堆叠数量则按实际数量存入。
     */
    private void depositSlot(Player player, String rawSlotValue, long requestedAmount) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT start player=" + player.getName() + " rawArg=" + safe(rawSlotValue)
            + " amount=" + requestedAmount + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, "当前仓库只读，无法存入。");
            refreshBoth(player);
            return;
        }
        int rawSlot = parseRawBackpackSlot(rawSlotValue);
        if (rawSlot < 9 || rawSlot > 44) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=invalid-raw rawArg=" + safe(rawSlotValue) + " parsed=" + rawSlot);
            sendMessage(player, false, "无效背包槽位: " + safe(rawSlotValue));
            refreshBoth(player);
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        ItemStack stack = player.getInventory().getItem(slot);
        debug("DEPOSIT slot player=" + player.getName() + " rawSlot=" + rawSlot + " bukkitSlot=" + slot + " stack=" + describeStack(stack));
        if (stack == null || stack.getType().isAir()) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=empty rawSlot=" + rawSlot + " bukkitSlot=" + slot);
            sendMessage(player, false, "该背包槽位没有可存入物品。");
            refreshBoth(player);
            return;
        }
        if (itemMatcherSupport.matches(configuration.blacklist(), stack)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=blacklist stack=" + describeStack(stack));
            sendMessage(player, false, "该物品禁止存入仓库。");
            refreshBoth(player);
            return;
        }
        int originalAmount = stack.getAmount();
        int depositAmount = (requestedAmount <= 0) ? originalAmount : (int) Math.min(originalAmount, requestedAmount);
        ItemStack depositStack = stack.clone();
        depositStack.setAmount(depositAmount);
        DepositResult result = depositStack(player, state.ownerType(), state.ownerId(), state.warehouseId(), depositStack);
        debug("DEPOSIT result player=" + player.getName() + " success=" + result.success()
            + " stored=" + result.storedAmount() + " remaining=" + result.remainingAmount() + " message=" + result.message());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        int remainingInSlot = originalAmount - (int) result.storedAmount();
        if (remainingInSlot <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack remaining = stack.clone();
            remaining.setAmount(remainingInSlot);
            player.getInventory().setItem(slot, remaining);
        }
        player.updateInventory();
        debug("DEPOSIT inventory-updated player=" + player.getName() + " bukkitSlot=" + slot + " newStack=" + describeStack(player.getInventory().getItem(slot)));
        sendMessage(player, true, "已存入 " + result.storedAmount() + " 件物品。");
        refreshBoth(player);
    }

    /**
     * 一键存入背包全部物品（主库存 9~44 槽）。
     * 逐件检查黑名单和容量，跳过不可存物品，统计存入结果。
     */
    private void depositAllBackpack(Player player) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT-ALL start player=" + player.getName()
            + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT-ALL reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, "当前仓库只读，无法存入。");
            refreshBoth(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        long totalStored = 0L;
        int changedSlots = 0;
        int skippedBlacklisted = 0;
        int failedSlots = 0;
        boolean foundItem = false;
        for (int rawSlot = 9; rawSlot <= 44; rawSlot++) {
            int slot = toBukkitBackpackSlot(rawSlot);
            ItemStack stack = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            foundItem = true;
            if (itemMatcherSupport.matches(configuration.blacklist(), stack)) {
                skippedBlacklisted++;
                continue;
            }
            DepositResult result = depositStack(player, state.ownerType(), state.ownerId(), state.warehouseId(), stack);
            if (!result.success()) {
                failedSlots++;
                continue;
            }
            totalStored += result.storedAmount();
            changedSlots++;
            if (result.remainingAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                ItemStack remaining = stack.clone();
                remaining.setAmount(result.remainingAmount());
                inventory.setItem(slot, remaining);
            }
        }
        if (totalStored > 0L) {
            player.updateInventory();
            String suffix = skippedBlacklisted > 0 || failedSlots > 0
                ? "，跳过 " + skippedBlacklisted + " 格禁存物品，" + failedSlots + " 格未能存入。"
                : "。";
            sendMessage(player, true, "已从背包存入 " + totalStored + " 件物品，共 " + changedSlots + " 格" + suffix);
        } else if (!foundItem) {
            sendMessage(player, false, "背包没有可存入物品。");
        } else if (skippedBlacklisted > 0 && failedSlots == 0) {
            sendMessage(player, false, "背包内物品均禁止存入仓库。");
        } else {
            sendMessage(player, false, "没有成功存入物品，仓库可能已满。");
        }
        refreshBoth(player);
    }

    /**
     * 核心存入逻辑：将物品堆栈存入指定仓库。
     * 优先尝试与已有同 hash 槽位合并（聚合上限 {@link #MAX_AGGREGATED_AMOUNT}），
     * 无法合并则占用新空槽。若仓库已满则返回失败。
     *
     * @param player      操作玩家（用于日志与 debug）
     * @param ownerType   {@code personal} 或 {@code shared}
     * @param ownerId     所有者标识（UUID 字符串或共享仓库 ID）
     * @param warehouseId 仓库 ID
     * @param stack       待存入物品（amount 可能部分存入）
     * @return 存入结果
     */
    private DepositResult depositStack(Player player, String ownerType, String ownerId, String warehouseId, ItemStack stack) throws Exception {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || itemMatcherSupport.matches(configuration.blacklist(), stack)) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=invalid-stack stack=" + describeStack(stack));
            return DepositResult.failure("该物品无法存入仓库。");
        }
        if (OWNER_SHARED.equals(ownerType) && !canModifyCurrent(player, state(player))) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=shared-read-only ownerId=" + ownerId);
            return DepositResult.failure("当前共享仓库只读或正在被他人编辑。");
        }
        ItemStack prototype = stack.clone();
        prototype.setAmount(1);
        byte[] bytes = ItemSerializer.serialize(prototype);
        String itemData = Base64.getEncoder().encodeToString(bytes);
        String itemHash = sha256Hex(bytes);
        long now = System.currentTimeMillis();
        int remaining = stack.getAmount();
        long stored = 0L;
        List<SlotItemRecord> currentSlots = repository.loadSlots(ownerType, ownerId, warehouseId);
        debug("DEPOSIT-STACK load player=" + player.getName() + " ownerType=" + ownerType + " ownerId=" + ownerId
            + " warehouseId=" + warehouseId + " currentSlots=" + currentSlots.size() + " hash=" + shortHash(itemHash)
            + " itemJsonEmpty=" + itemJson(prototype, 1L).isBlank());

        for (SlotItemRecord current : currentSlots) {
            if (remaining <= 0 || !itemHash.equals(current.itemHash())) {
                continue;
            }
            long room = Math.max(0L, MAX_AGGREGATED_AMOUNT - current.amount());
            int moved = (int) Math.min(remaining, room);
            if (moved > 0) {
                repository.upsertSlot(copySlot(current, current.amount() + moved, now));
                debug("DEPOSIT-STACK merge player=" + player.getName() + " slot=" + current.slot()
                    + " moved=" + moved + " oldAmount=" + current.amount() + " newAmount=" + (current.amount() + moved));
                remaining -= moved;
                stored += moved;
            }
        }

        if (remaining > 0) {
            int emptySlot = firstEmptySlot(currentSlots, currentWarehouseCapacity(player, ownerType, ownerId, warehouseId));
            if (emptySlot >= 0) {
                SearchTokens searchTokens = toSearchTokens(resolveDisplayName(prototype) + " " + prototype.getType().name() + " " + resolveCategory(prototype));
                String displayJson = itemJson(prototype, 1L);
                repository.upsertSlot(new SlotItemRecord(
                    ownerType,
                    ownerId,
                    warehouseId,
                    emptySlot,
                    itemHash,
                    resolveCategory(prototype),
                    resolveDisplayName(prototype),
                    prototype.getType().name(),
                    searchTokens.searchText(),
                    searchTokens.pinyin(),
                    searchTokens.initials(),
                    itemData,
                    displayJson,
                    remaining,
                    now,
                    now
                ));
                debug("DEPOSIT-STACK insert player=" + player.getName() + " slot=" + emptySlot + " amount=" + remaining
                    + " item=" + describeStack(prototype) + " itemJsonEmpty=" + displayJson.isBlank());
                stored += remaining;
                remaining = 0;
            } else {
                debug("DEPOSIT-STACK no-empty-slot player=" + player.getName() + " currentSlots=" + currentSlots.size());
            }
        }

        if (stored <= 0L) {
            debug("DEPOSIT-STACK failure player=" + player.getName() + " reason=no-stored remaining=" + remaining);
            return DepositResult.failure("仓库已满或该物品已达到聚合上限。");
        }
        return DepositResult.success(stored, remaining);
    }

    private SlotItemRecord copySlot(SlotItemRecord current, long amount, long updatedAt) {
        return new SlotItemRecord(
            current.ownerType(),
            current.ownerId(),
            current.warehouseId(),
            current.slot(),
            current.itemHash(),
            current.categoryId(),
            current.displayName(),
            current.materialId(),
            current.searchText(),
            current.pinyin(),
            current.initials(),
            current.itemData(),
            current.itemJson(),
            amount,
            current.createdAt(),
            updatedAt
        );
    }

    /**
     * 从当前仓库取出物品到玩家背包。
     * 需要二级密码已解锁，且当前仓库可写。按堆叠上限分批给予，背包满时停止。
     */
    private void withdraw(Player player, int slot, long requestedAmount, boolean all) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "取出物品前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        ViewState state = state(player);
        if (!canModifyCurrent(player, state)) {
            sendMessage(player, false, "当前仓库只读，无法取出。");
            refreshBoth(player);
            return;
        }
        int selectedSlot = slot >= 0 && slot < SLOT_COUNT ? actualSlotFromDisplay(state, slot) : -1;
        if (!validSlot(selectedSlot)) {
            sendMessage(player, false, "未选择可取出的槽位。");
            refreshBoth(player);
            return;
        }
        Optional<SlotItemRecord> optional = repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
        if (optional.isEmpty()) {
            sendMessage(player, false, "该槽位没有物品。");
            refreshBoth(player);
            return;
        }
        SlotItemRecord item = optional.get();
        ItemStack base = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
        if (base == null || base.getType().isAir()) {
            repository.deleteSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
            sendMessage(player, false, "该槽位物品数据损坏，已清理。");
            refreshBoth(player);
            return;
        }
        long remaining = all ? item.amount() : Math.max(1L, Math.min(requestedAmount, item.amount()));
        long delivered = 0L;
        while (remaining > 0L) {
            int give = (int) Math.min(remaining, Math.max(1, base.getMaxStackSize()));
            ItemStack clone = base.clone();
            clone.setAmount(give);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(clone);
            long left = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
            long moved = give - left;
            delivered += moved;
            remaining -= moved;
            if (left > 0L || moved <= 0L) {
                break;
            }
        }
        if (delivered <= 0L) {
            sendMessage(player, false, "背包空间不足。");
            refreshBoth(player);
            return;
        }
        long updatedAmount = item.amount() - delivered;
        if (updatedAmount <= 0L) {
            repository.deleteSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
            state.setSelectedSlot(-1);
        } else {
            repository.upsertSlot(copySlot(item, updatedAmount, System.currentTimeMillis()));
        }
        sendMessage(player, true, "已取出 " + delivered + " 件物品。");
        refreshBoth(player);
    }

    /**
     * 玩家将背包货币存入银行活期账户。
     * 先通过货币桥接扣款，再写入数据库；失败时自动回滚。
     */
    private void bankDeposit(Player player, String currencyId, BigDecimal amount) throws Exception {
        String normalized = normalizeId(currencyId);
        var bridge = currencyBridgeManager.bridge(normalized);
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? "未知货币。" : bridge.unavailableReason());
            refreshBoth(player);
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sendMessage(player, false, "金额必须大于 0。");
            refreshBoth(player);
            return;
        }
        var result = bridge.withdraw(player, amount);
        if (!result.success()) {
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        try {
            repository.creditBankBalance(player.getUniqueId(), normalized, amount, System.currentTimeMillis());
        } catch (Exception exception) {
            bridge.deposit(player, amount);
            throw exception;
        }
        sendMessage(player, true, "已存入银行 " + formatCurrency(normalized, amount) + "。");
        refreshBoth(player);
    }

    /**
     * 玩家从银行活期账户提现到背包。
     * 先原子扣减数据库余额，再通过货币桥接放款；失败时自动回滚。
     */
    private void bankWithdraw(Player player, String currencyId, BigDecimal amount) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "提现前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        String normalized = normalizeId(currencyId);
        var bridge = currencyBridgeManager.bridge(normalized);
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? "未知货币。" : bridge.unavailableReason());
            refreshBoth(player);
            return;
        }
        BigDecimal current = bankBalance(player.getUniqueId(), normalized);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || current.compareTo(amount) < 0) {
            sendMessage(player, false, "银行余额不足或金额无效。");
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (!repository.debitBankBalance(player.getUniqueId(), normalized, amount, now)) {
            sendMessage(player, false, "银行余额不足或金额无效。");
            refreshBoth(player);
            return;
        }
        var result = bridge.deposit(player, amount);
        if (!result.success()) {
            repository.creditBankBalance(player.getUniqueId(), normalized, amount, now);
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, "已从银行取出 " + formatCurrency(normalized, amount) + "。");
        refreshBoth(player);
    }

    /**
     * 购买定期存款产品。
     * 校验金额区间、匹配利率阶梯、原子扣减活期余额后创建定期记录。
     */
    private void createFixedDeposit(Player player, String productId, BigDecimal amount) throws Exception {
        DepositProductDefinition product = configuration.depositProduct(productId);
        if (product == null || !canUseProduct(player, product)) {
            sendMessage(player, false, "无法购买该定期产品。");
            refreshBoth(player);
            return;
        }
        if (amount.compareTo(product.minAmount()) < 0 || (product.maxAmount().compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(product.maxAmount()) > 0)) {
            sendMessage(player, false, "金额不在产品允许范围内。");
            refreshBoth(player);
            return;
        }
        InterestTier tier = product.tierFor(amount);
        if (tier == null) {
            sendMessage(player, false, "该金额没有匹配的利率阶梯。");
            refreshBoth(player);
            return;
        }
        BigDecimal current = bankBalance(player.getUniqueId(), product.currencyId());
        if (current.compareTo(amount) < 0) {
            sendMessage(player, false, "银行余额不足。");
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (!repository.debitBankBalance(player.getUniqueId(), product.currencyId(), amount, now)) {
            sendMessage(player, false, "银行余额不足。");
            refreshBoth(player);
            return;
        }
        try {
            repository.createFixedDeposit(new FixedDepositRecord(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                product.id(),
                product.currencyId(),
                amount,
                tier.rate(),
                now,
                now + product.durationSeconds() * 1000L,
                false,
                0L
            ));
        } catch (Exception exception) {
            repository.creditBankBalance(player.getUniqueId(), product.currencyId(), amount, now);
            throw exception;
        }
        sendMessage(player, true, "已创建定期存款。");
        refreshBoth(player);
    }

    /**
     * 领取到期定期存款本息。
     * 通过 {@link WarehouseRepository#claimFixedDepositAtomic} 原子标记 claimed 并计算本息入账，防止并发重复领取。
     */
    private void claimFixedDeposit(Player player, String depositId) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "领取定期前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        Optional<FixedDepositRecord> optional = repository.loadFixedDeposits(player.getUniqueId()).stream()
            .filter(deposit -> deposit.id().equals(depositId))
            .findFirst();
        if (optional.isEmpty()) {
            sendMessage(player, false, "该定期暂不可领取。");
            refreshBoth(player);
            return;
        }
        FixedDepositRecord deposit = optional.get();
        if (deposit.claimed() || deposit.maturesAt() > System.currentTimeMillis()) {
            sendMessage(player, false, "该定期暂不可领取。");
            refreshBoth(player);
            return;
        }
        Optional<BigDecimal> payout = repository.claimFixedDepositAtomic(depositId, player.getUniqueId(), System.currentTimeMillis());
        if (payout.isEmpty()) {
            sendMessage(player, false, "该定期暂不可领取。");
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, "已领取定期本息 " + formatCurrency(deposit.currencyId(), payout.get()) + "。");
        refreshBoth(player);
    }

    /**
     * 创建共享仓库。校验权限层级的 max-owned 限制，扣除创建费用后写入数据库。
     */
    private void createSharedWarehouse(Player player, String rawName) throws Exception {
        if (!configuration.shared().enabled()) {
            sendMessage(player, false, "共享仓库未启用。");
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = resolveSharedTier(player);
        if (repository.countOwnedSharedWarehouses(player.getUniqueId()) >= tier.maxOwned()) {
            sendMessage(player, false, "你已达到可创建共享仓库数量上限。");
            refreshBoth(player);
            return;
        }
        WarehouseLevelDefinition initialLevel = sharedInitialLevel();
        if (initialLevel == null) {
            sendMessage(player, false, "共享仓库未配置初始等级。");
            refreshBoth(player);
            return;
        }
        int level = initialLevel.level();
        long capacity = Math.max(SLOT_COUNT, initialLevel.capacity());
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        if (!withdrawCost(player, cost, "未知共享仓库创建货币。")) {
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        try {
            repository.createSharedWarehouse(new SharedWarehouseRecord(id, player.getUniqueId(), crop(rawName.isBlank() ? "共享仓库" : rawName, 64), level, capacity, now, now, "owner", true));
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, cost == null ? "已创建共享仓库。" : "已创建共享仓库，消耗 " + formatCurrency(cost.currencyId(), cost.amount()) + "。");
        refreshBoth(player);
    }

    /**
     * 删除共享仓库（需二级密码确认）。仅所有者可操作，删除后清理互斥锁并重置当前视图。
     */
    private void deleteSharedWarehouse(Player player, String sharedId, String password) throws Exception {
        if (!validatePassword(player.getUniqueId(), password)) {
            sendMessage(player, false, "请输入正确二级密码确认删除共享仓库。");
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以删除。");
            refreshBoth(player);
            return;
        }
        repository.deleteSharedWarehouse(sharedId);
        SharedEditLock lock = sharedEditLocks.remove(sharedId);
        if (lock != null && crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishUnlock(sharedId, lock.playerUuid());
        }
        ViewState state = state(player);
        if (OWNER_SHARED.equals(state.ownerType()) && sharedId.equals(state.ownerId())) {
            state.setOwnerType(OWNER_PERSONAL);
            state.setOwnerId(player.getUniqueId().toString());
            state.setWarehouseId(firstPersonalWarehouseId());
            state.setSelectedSlot(-1);
        }
        sendMessage(player, true, "已删除共享仓库。");
        refreshBoth(player);
    }

    private void renameSharedWarehouse(Player player, String sharedId, String rawName) throws Exception {
        String name = crop(safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, "共享仓库名称不能为空。");
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以修改名称。");
            refreshBoth(player);
            return;
        }
        repository.updateSharedWarehouseName(sharedId, name, System.currentTimeMillis());
        sendMessage(player, true, "共享仓库名称已修改为 " + name + "。");
        refreshBoth(player);
    }

    private void toggleSharedWarehouseShowcase(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以修改展示设置。");
            refreshBoth(player);
            return;
        }
        boolean newValue = !shared.get().showcaseEnabled();
        repository.updateSharedWarehouseShowcase(sharedId, newValue, System.currentTimeMillis());
        sendMessage(player, true, "共享仓库展示已" + (newValue ? "开启" : "关闭") + "。");
        refreshBoth(player);
    }

    private void renamePersonalWarehouse(Player player, String warehouseId, String rawName) throws Exception {
        String name = crop(safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, "仓库名称不能为空。");
            refreshBoth(player);
            return;
        }
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, "当前仓库不存在。");
            refreshBoth(player);
            return;
        }
        repository.updatePersonalWarehouseName(player.getUniqueId(), warehouseId, name, System.currentTimeMillis());
        sendMessage(player, true, "仓库名称已修改为 " + name + "。");
        refreshBoth(player);
    }

    private void togglePersonalWarehouseShowcase(Player player, String warehouseId) throws Exception {
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, "当前仓库不存在。");
            refreshBoth(player);
            return;
        }
        boolean newValue = !record.showcaseEnabled();
        repository.updatePersonalWarehouseShowcase(player.getUniqueId(), warehouseId, newValue, System.currentTimeMillis());
        sendMessage(player, true, "仓库展示已" + (newValue ? "开启" : "关闭") + "。");
        refreshBoth(player);
    }

    /**
     * 邀请玩家加入共享仓库，或修改现有成员角色。
     * 仅所有者可操作，校验成员数量上限，目标角色不可为 owner。
     */
    private void inviteSharedMember(Player player, String sharedId, String memberName, String role) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "增加成员前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以邀请成员。");
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (target.getUniqueId() == null) {
            sendMessage(player, false, "找不到玩家。");
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = resolveSharedTier(player);
        if (repository.countSharedMembers(sharedId) >= tier.maxMembers()) {
            sendMessage(player, false, "共享仓库成员已达上限。");
            refreshBoth(player);
            return;
        }
        String normalizedRole = switch (normalizeId(role)) {
            case "viewer" -> "viewer";
            case "owner" -> "member";
            default -> "member";
        };
        repository.upsertSharedMember(sharedId, target.getUniqueId(), normalizedRole, System.currentTimeMillis());
        sendMessage(player, true, "已更新共享成员。");
        refreshBoth(player);
    }

    private void removeSharedMember(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "移除成员前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以移除成员。");
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (target.getUniqueId() == null || target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, "无法移除该成员。");
            refreshBoth(player);
            return;
        }
        repository.removeSharedMember(sharedId, target.getUniqueId());
        releaseSharedLocks(target.getUniqueId());
        sendMessage(player, true, "已移除共享成员。");
        refreshBoth(player);
    }

    /**
     * 将共享仓库所有权转让给现有 member 角色成员。
     * 转让后原所有者变为 viewer，目标成员提升为 owner。
     */
    private void transferSharedWarehouse(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "转让共享仓库前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以转让。");
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (target.getUniqueId() == null || target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, "只能转让给其他成员。");
            refreshBoth(player);
            return;
        }
        Optional<SharedMemberRecord> member = repository.loadSharedMembers(sharedId).stream()
            .filter(record -> record.playerUuid().equals(target.getUniqueId()))
            .findFirst();
        if (member.isEmpty() || !"member".equalsIgnoreCase(member.get().role())) {
            sendMessage(player, false, "只能转让给当前权限为 " + sharedRoleName("member") + " 的成员。");
            refreshBoth(player);
            return;
        }
        repository.transferSharedWarehouse(sharedId, player.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
        SharedEditLock lock = sharedEditLocks.remove(sharedId);
        if (lock != null && crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishUnlock(sharedId, lock.playerUuid());
        }
        releaseSharedLocks(target.getUniqueId());
        ViewState state = state(player);
        if (OWNER_SHARED.equals(state.ownerType()) && sharedId.equals(state.ownerId())) {
            state.setSharedEditMode(false);
        }
        sendMessage(player, true, "已将共享仓库转让给 " + (target.getName() == null ? memberName : target.getName()) + "。");
        refreshBoth(player);
    }

    /**
     * 设置二级密码。使用 PBKDF2WithHmacSHA256 120,000 次迭代 hash，随机 salt。
     * 设置成功后自动解锁当前会话。
     */
    private void setPassword(Player player, String password) throws Exception {
        String normalized = safe(password);
        if (normalized.length() < configuration.security().minLength() || normalized.length() > configuration.security().maxLength()) {
            sendMessage(player, false, "密码长度必须在 " + configuration.security().minLength() + "-" + configuration.security().maxLength() + " 位之间。");
            refreshBoth(player);
            return;
        }
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] hash = derivePasswordHash(normalized.toCharArray(), salt);
        repository.saveSecurity(new SecurityRecord(
            player.getUniqueId(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
            encryptPasswordForInspection(normalized),
            System.currentTimeMillis()
        ));
        unlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + configuration.security().unlockSessionMs());
        sendMessage(player, true, "已设置二级密码并自动解锁。");
        refreshBoth(player);
    }

    private void unlockPassword(Player player, String password) throws Exception {
        if (validatePassword(player.getUniqueId(), password)) {
            unlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + configuration.security().unlockSessionMs());
            sendMessage(player, true, "二级密码已解锁。");
        } else {
            sendMessage(player, false, "二级密码错误。");
        }
        refreshBoth(player);
    }

    private void clearPassword(Player player, String password) throws Exception {
        if (!validatePassword(player.getUniqueId(), password)) {
            sendMessage(player, false, "二级密码错误。");
            refreshBoth(player);
            return;
        }
        repository.clearSecurity(player.getUniqueId());
        unlockedUntil.remove(player.getUniqueId());
        sendMessage(player, true, "已清除二级密码。");
        refreshBoth(player);
    }

    private void ensureEntitlements(Player player) throws Exception {
        long now = System.currentTimeMillis();
        Map<String, WarehouseRecord> existing = personalWarehouseMap(player.getUniqueId());
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!definition.defaultOwned()) {
                continue;
            }
            if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) {
                continue;
            }
            if (existing.containsKey(definition.id())) {
                continue;
            }
            repository.upsertPersonalWarehouse(player.getUniqueId(), definition.id(), definition.defaultLevel(), "", now);
        }
    }

    private void ensureCurrentWarehouse(Player player, ViewState state) throws Exception {
        if (OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
                .filter(record -> record.id().equals(state.ownerId()))
                .findFirst();
            if (shared.isPresent()) {
                return;
            }
        }
        state.setOwnerType(OWNER_PERSONAL);
        state.setOwnerId(player.getUniqueId().toString());
        if (state.warehouseId().isBlank() || !personalWarehouseMap(player.getUniqueId()).containsKey(state.warehouseId())) {
            state.setWarehouseId(firstPersonalWarehouseId());
        }
    }

    private ViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> ViewState.initial(player,
            configuration.pickup().autoStoreOnPickup(),
            configuration.pickup().autoStoreMythicLoot(),
            configuration.pickup().notifyOnAutoStore()));
    }

    private List<WarehouseRecord> personalRecords(UUID playerUuid) throws Exception {
        return repository.loadPersonalWarehouses(playerUuid);
    }

    private Map<String, WarehouseRecord> personalWarehouseMap(UUID playerUuid) throws Exception {
        Map<String, WarehouseRecord> records = new LinkedHashMap<>();
        for (WarehouseRecord record : repository.loadPersonalWarehouses(playerUuid)) {
            records.put(record.warehouseId(), record);
        }
        return records;
    }

    private List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) {
        try {
            return repository.loadSlots(ownerType, ownerId, warehouseId);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("读取仓库槽位失败: " + exception.getMessage());
            }
            return List.of();
        }
    }

    private int firstEmptySlot(List<SlotItemRecord> currentSlots, long capacity) {
        int cappedCapacity = (int) Math.max(1L, Math.min(Integer.MAX_VALUE - 8L, capacity));
        boolean[] occupied = new boolean[cappedCapacity];
        for (SlotItemRecord item : currentSlots) {
            if (item.slot() >= 0 && item.slot() < cappedCapacity) {
                occupied[item.slot()] = true;
            }
        }
        for (int slot = 0; slot < cappedCapacity; slot++) {
            if (!occupied[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canModifyCurrent(Player player, ViewState state) throws Exception {
        if (OWNER_PERSONAL.equals(state.ownerType())) {
            return player.getUniqueId().toString().equals(state.ownerId());
        }
        if (!state.sharedEditMode()) {
            return false;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        SharedEditLock lock = sharedEditLocks.get(state.ownerId());
        if (lock == null) {
            return false;
        }
        return lock.heldBy(player.getUniqueId());
    }

    private boolean sharedCanEdit(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return false;
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst()
            .map(shared -> canEditShared(shared.viewerRole()))
            .orElse(false);
    }

    private boolean currentShowcaseEnabled(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return personalWarehouseMap(player.getUniqueId()).getOrDefault(state.warehouseId(), new WarehouseRecord(player.getUniqueId(), state.warehouseId(), 1, "", true, 0L)).showcaseEnabled();
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst()
            .map(SharedWarehouseRecord::showcaseEnabled)
            .orElse(true);
    }

    private boolean acquireCurrentSharedLock(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return true;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        String sharedId = state.ownerId();
        SharedEditLock existing = sharedEditLocks.get(sharedId);
        if (existing != null) {
            if (existing.heldBy(player.getUniqueId())) {
                return true;
            }
            sendMessage(player, false, buildLockBusyMessage(existing));
            return false;
        }
        SharedEditLock newLock = new SharedEditLock(
            player.getUniqueId(),
            player.getName(),
            localNodeId()
        );
        SharedEditLock raced = sharedEditLocks.putIfAbsent(sharedId, newLock);
        if (raced != null && !raced.heldBy(player.getUniqueId())) {
            sendMessage(player, false, buildLockBusyMessage(raced));
            return false;
        }
        if (crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishLock(sharedId, newLock);
        }
        return true;
    }

    private boolean canEditShared(String role) {
        String normalized = normalizeId(role);
        return "owner".equals(normalized) || "member".equals(normalized);
    }

    private void releaseCurrentSharedLock(Player player) {
        ViewState state = viewStates.get(player.getUniqueId());
        if (state != null && OWNER_SHARED.equals(state.ownerType())) {
            releaseSharedLock(state.ownerId(), player.getUniqueId());
        }
    }

    private void releaseSharedLocks(UUID playerUuid) {
        List<String> sharedIds = new ArrayList<>();
        sharedEditLocks.forEach((sharedId, lock) -> {
            if (lock.heldBy(playerUuid) && localNodeId().equals(lock.nodeId())) {
                sharedIds.add(sharedId);
            }
        });
        for (String sharedId : sharedIds) {
            releaseSharedLock(sharedId, playerUuid);
        }
    }

    private void releaseSharedLock(String sharedId, UUID playerUuid) {
        SharedEditLock removed = sharedEditLocks.computeIfPresent(sharedId, (id, lock) ->
            lock.heldBy(playerUuid) && localNodeId().equals(lock.nodeId()) ? null : lock
        );
        if (removed != null && crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishUnlock(sharedId, playerUuid);
        }
    }

    private void applyRemoteSharedLock(String sharedId, SharedEditLock lock) {
        if (sharedId == null || sharedId.isBlank() || lock == null) {
            return;
        }
        SharedEditLock previous = sharedEditLocks.put(sharedId, lock);
        if (previous != null
            && previous.playerUuid().equals(lock.playerUuid())
            && previous.nodeId().equals(lock.nodeId())) {
            return;
        }
        for (Map.Entry<UUID, ViewState> entry : viewStates.entrySet()) {
            ViewState viewState = entry.getValue();
            if (!OWNER_SHARED.equals(viewState.ownerType())
                || !sharedId.equals(viewState.ownerId())
                || !viewState.sharedEditMode()
                || lock.heldBy(entry.getKey())) {
                continue;
            }
            viewState.setSharedEditMode(false);
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online == null || !online.isOnline()) {
                continue;
            }
            sendMessage(online, false, buildLockBusyMessage(lock));
            try {
                refreshBoth(online);
            } catch (Exception exception) {
                this.logger.warning("[Warehouse] 刷新共享仓库 UI 失败: " + exception.getMessage());
            }
        }
    }

    private void applyRemoteSharedUnlock(WarehouseCrossServerPayloadCodec.UnlockPayload unlock) {
        if (unlock == null || unlock.sharedId() == null || unlock.sharedId().isBlank()) {
            return;
        }
        sharedEditLocks.computeIfPresent(unlock.sharedId(), (sharedId, lock) ->
            lock.playerUuid().equals(unlock.playerUuid()) && lock.nodeId().equals(unlock.nodeId())
                ? null : lock
        );
    }

    private String buildLockBusyMessage(SharedEditLock lock) {
        if (lock == null) {
            return "该共享仓库正在被其他成员编辑，当前以只读方式打开。";
        }
        if (localNodeId().equals(lock.nodeId())) {
            return "该共享仓库正在被 " + lock.playerName() + " 编辑，当前以只读方式打开。";
        }
        return "该共享仓库正在被 " + lock.playerName() + "（" + lock.nodeId() + "）编辑，当前以只读方式打开。";
    }

    private String localNodeId() {
        return crossServerLockService != null ? crossServerLockService.nodeId() : "local";
    }

    private String lockOwnerName(ViewState state) {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return "";
        }
        SharedEditLock lock = sharedEditLocks.get(state.ownerId());
        if (lock == null) {
            return "";
        }
        if (localNodeId().equals(lock.nodeId())) {
            Player player = Bukkit.getPlayer(lock.playerUuid());
            return player == null ? lock.playerName() : player.getName();
        }
        return lock.playerName() + "@" + lock.nodeId();
    }

    private void clearFilteredSelection(ViewState state) throws Exception {
        if (!validSlot(state.selectedSlot())) {
            return;
        }
        Optional<SlotItemRecord> selected = repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot());
        if (selected.isEmpty() || !slotMatches(selected.get(), state.categoryId(), normalizeToken(state.search()))) {
            state.setSelectedSlot(-1);
        }
    }

    private long currentWarehouseCapacity(Player player, String ownerType, String ownerId, String warehouseId) throws Exception {
        if (OWNER_SHARED.equals(ownerType)) {
            return repository.loadSharedWarehouses(player.getUniqueId()).stream()
                .filter(shared -> shared.id().equals(ownerId))
                .map(SharedWarehouseRecord::capacity)
                .findFirst()
                .orElse((long) SLOT_COUNT);
        }
        WarehouseDefinition definition = configuration.warehouse(warehouseId);
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (definition == null || record == null) {
            return SLOT_COUNT;
        }
        WarehouseLevelDefinition level = definition.level(record.level());
        return level == null ? SLOT_COUNT : Math.max(SLOT_COUNT, level.capacity());
    }

    private void putCapacityFields(Player player, ViewState state, Map<String, Object> packet) throws Exception {
        long capacity = currentWarehouseCapacity(player, state.ownerType(), state.ownerId(), state.warehouseId());
        long used = loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).size();
        packet.put("capacity", capacity);
        packet.put("used", used);
        packet.put("capacityText", used + "/" + capacity);
        packet.put("level", 1);
        packet.put("canUpgrade", false);
        packet.put("nextUpgradeText", "无可用扩充");
        if (OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = currentSharedRecord(player, state);
            if (shared.isEmpty()) {
                packet.put("nextUpgradeText", "请选择一个共享仓库");
                return;
            }
            SharedWarehouseRecord record = shared.get();
            packet.put("level", record.level());
            Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
            if (levels.isEmpty()) {
                packet.put("nextUpgradeText", "共享仓库未配置扩充等级");
                return;
            }
            WarehouseLevelDefinition next = levels.get(record.level() + 1);
            if (next == null) {
                packet.put("nextUpgradeText", "已满级");
                return;
            }
            if (!"owner".equalsIgnoreCase(record.viewerRole())) {
                packet.put("nextUpgradeText", "只有共享仓库主人可扩充");
                return;
            }
            WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(levels, record.level());
            packet.put("canUpgrade", true);
            packet.put("nextLevel", next.level());
            packet.put("nextCapacity", next.capacity());
            packet.put("nextUpgradeText", cost == null
                ? "扩充到 Lv." + next.level() + " / " + next.capacity() + " 格"
                : "扩充到 Lv." + next.level() + " / " + next.capacity() + " 格，消耗 " + formatCurrencyWithName(cost.currencyId(), cost.amount()));
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            return;
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(state.warehouseId());
        if (definition == null || record == null) {
            return;
        }
        packet.put("level", record.level());
        WarehouseLevelDefinition next = definition.levels().get(record.level() + 1);
        if (next == null) {
            packet.put("nextUpgradeText", "已满级");
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(definition.levels(), record.level());
        packet.put("canUpgrade", true);
        packet.put("nextLevel", next.level());
        packet.put("nextCapacity", next.capacity());
        packet.put("nextUpgradeText", cost == null
            ? "扩充到 Lv." + next.level() + " / " + next.capacity() + " 格"
            : "扩充到 Lv." + next.level() + " / " + next.capacity() + " 格，消耗 " + formatCurrencyWithName(cost.currencyId(), cost.amount()));
    }

    /**
     * 扩充当前仓库到下一等级。
     * 个人仓库和共享仓库均支持，扣除升级费用后更新数据库。
     */
    private void upgradeCurrentWarehouse(Player player) throws Exception {
        ViewState state = state(player);
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, "扩充仓库前请先解锁二级密码。");
            refreshBoth(player);
            return;
        }
        if (OWNER_SHARED.equals(state.ownerType())) {
            upgradeCurrentSharedWarehouse(player, state);
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            sendMessage(player, false, "当前仓库不能在此扩充。");
            refreshBoth(player);
            return;
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(state.warehouseId());
        if (definition == null || record == null) {
            sendMessage(player, false, "当前仓库不可扩充。");
            refreshBoth(player);
            return;
        }
        WarehouseLevelDefinition next = definition.levels().get(record.level() + 1);
        if (next == null) {
            sendMessage(player, false, "当前仓库已满级。");
            refreshBoth(player);
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(definition.levels(), record.level());
        if (!withdrawCost(player, cost, "未知扩充货币。")) {
            refreshBoth(player);
            return;
        }
        try {
            repository.upsertPersonalWarehouse(player.getUniqueId(), state.warehouseId(), next.level(), record.customName(), System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, "仓库已扩充到 Lv." + next.level() + "，容量 " + next.capacity() + " 格。");
        refreshBoth(player);
    }

    private void upgradeCurrentSharedWarehouse(Player player, ViewState state) throws Exception {
        Optional<SharedWarehouseRecord> shared = currentSharedRecord(player, state);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, "只有共享仓库主人可以扩充。");
            refreshBoth(player);
            return;
        }
        Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
        if (levels.isEmpty()) {
            sendMessage(player, false, "共享仓库未配置扩充等级。");
            refreshBoth(player);
            return;
        }
        SharedWarehouseRecord record = shared.get();
        WarehouseLevelDefinition next = levels.get(record.level() + 1);
        if (next == null) {
            sendMessage(player, false, "共享仓库已满级。");
            refreshBoth(player);
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(levels, record.level());
        if (!withdrawCost(player, cost, "未知共享仓库扩充货币。")) {
            refreshBoth(player);
            return;
        }
        try {
            repository.updateSharedWarehouseLevel(record.id(), next.level(), Math.max(SLOT_COUNT, next.capacity()), System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, "共享仓库已扩充到 Lv." + next.level() + "，容量 " + next.capacity() + " 格。");
        refreshBoth(player);
    }

    private Map<String, Object> categoryPacket() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        Map<String, Object> allRow = new LinkedHashMap<>();
        allRow.put("id", "all");
        allRow.put("name", "全部");
        allRow.put("text", "&0全部");
        result.put(Integer.toString(idx), allRow);
        idx++;
        for (CategoryDefinition category : configuration.categories().values().stream()
                .sorted(Comparator.comparingInt(CategoryDefinition::priority))
                .toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.id());
            row.put("name", category.displayName());
            row.put("text", "&0" + category.displayName());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> personalWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = personalWarehouseMap(player.getUniqueId());
        int idx = 0;
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            WarehouseLevelDefinition level = definition.level(record.level());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", ChatColor.translateAlternateColorCodes('&', definition.displayName()));
            row.put("text", "&0" + ChatColor.translateAlternateColorCodes('&', definition.displayName()));
            row.put("level", record.level());
            row.put("capacity", level == null ? SLOT_COUNT : level.capacity());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> previewPersonalWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = personalWarehouseMap(targetUuid);
        int idx = 0;
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            if (!record.showcaseEnabled()) {
                continue;
            }
            WarehouseLevelDefinition level = definition.level(record.level());
            String name = record.customName() == null || record.customName().isBlank()
                ? ChatColor.translateAlternateColorCodes('&', definition.displayName())
                : record.customName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", name);
            row.put("text", "&0" + name);
            row.put("level", record.level());
            row.put("capacity", level == null ? SLOT_COUNT : level.capacity());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> previewSharedWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : repository.loadSharedWarehousesByOwner(targetUuid)) {
            if (!shared.showcaseEnabled()) {
                continue;
            }
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", sharedRoleName(shared.viewerRole()));
            rowMap.put("level", shared.level());
            rowMap.put("storageText", "&0共享 " + shared.name());
            rowMap.put("manageText", "&0" + shared.name() + " &8[" + sharedRoleName(shared.viewerRole()) + "]\n&7Lv." + shared.level() + "  " + shared.capacity() + " 格");
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", "");
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> manageWarehousePacket(Map<String, Object> personalWarehouses, Map<String, Object> sharedWarehouses, ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (Map.Entry<String, Object> entry : personalWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", OWNER_PERSONAL);
            rowMap.put("target", target);
            rowMap.put("selected", OWNER_PERSONAL.equals(state.ownerType()) && target.equals(state.warehouseId()));
            rowMap.put("text", "&0个人 " + safe(String.valueOf(row.get("name"))) + "\n&7Lv." + row.get("level") + "  " + row.get("capacity") + " 格");
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        for (Map.Entry<String, Object> entry : sharedWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", OWNER_SHARED);
            rowMap.put("target", target);
            rowMap.put("selected", OWNER_SHARED.equals(state.ownerType()) && target.equals(state.ownerId()));
            rowMap.put("text", safe(String.valueOf(row.get("manageText"))));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> sharedWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : repository.loadSharedWarehouses(player.getUniqueId())) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", sharedRoleName(shared.viewerRole()));
            rowMap.put("level", shared.level());
            rowMap.put("storageText", "&0共享 " + shared.name());
            rowMap.put("manageText", "&0" + shared.name() + " &8[" + sharedRoleName(shared.viewerRole()) + "]\n&7Lv." + shared.level() + "  " + shared.capacity() + " 格");
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", lockOwnerName(ViewState.shared(shared.id(), player, false, false, false)));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> sharedMemberPacket(Player player, ViewState state) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return result;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            return result;
        }
        int idx = 0;
        for (SharedMemberRecord member : repository.loadSharedMembers(state.ownerId())) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerUuid());
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("uuid", member.playerUuid().toString());
            rowMap.put("name", offline.getName() == null ? member.playerUuid().toString() : offline.getName());
            rowMap.put("role", member.role());
            rowMap.put("roleName", sharedRoleName(member.role()));
            rowMap.put("text", "&0" + (offline.getName() == null ? member.playerUuid().toString() : offline.getName()) + " &7" + sharedRoleName(member.role()));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> backpackPacket(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int raw = 9; raw <= 44; raw++) {
            int slot = toBukkitBackpackSlot(raw);
            ItemStack item = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (item == null || item.getType().isAir()) {
                result.put(Integer.toString(raw), emptyBackpackPacket(raw, slot));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", Integer.toString(raw));
            row.put("rawSlot", raw);
            row.put("slot", slot);
            row.put("empty", false);
            row.put("name", resolveDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("category", resolveCategory(item));
            row.put("material", item.getType().name());
            row.put("itemJson", itemJson(item, item.getAmount()));
            row.put("lore", loreLines(item));
            result.put(Integer.toString(raw), row);
        }
        return result;
    }

    private Map<String, Object> emptyBackpackPacket(int rawSlot, int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(rawSlot));
        row.put("rawSlot", rawSlot);
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("itemJson", "");
        row.put("lore", List.of());
        return row;
    }

    private Map<String, Object> slotPacket(SlotItemRecord item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(item.slot()));
        row.put("slot", item.slot());
        row.put("empty", false);
        row.put("name", item.displayName());
        row.put("amount", item.amount());
        row.put("category", item.categoryId());
        row.put("material", item.materialId());
        row.put("updated", TIME_FORMATTER.format(Instant.ofEpochMilli(item.updatedAt())));
        row.put("lore", storedLoreLines(item));
        return row;
    }

    private String displayItemJson(SlotItemRecord item) {
        try {
            ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
            String json = itemJson(stack, 1L);
            return json.isBlank() ? item.itemJson() : json;
        } catch (RuntimeException exception) {
            return item.itemJson();
        }
    }

    private Map<String, Object> emptySlotPacket(int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(slot));
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("updated", "");
        row.put("lore", List.of());
        return row;
    }

    private Map<String, Object> emptySelectionPacket() {
        Map<String, Object> row = emptySlotPacket(-1);
        row.put("name", "未选择物品");
        row.put("lore", List.of("&f点击仓库槽位选择物品。"));
        return row;
    }

    private Map<String, Object> bankBalancePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> balances = repository.loadBankBalances(player.getUniqueId());
        int idx = 0;
        for (String currencyId : configuration.currencies().keySet()) {
            BigDecimal balance = balances.getOrDefault(currencyId, BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", currencyId);
            row.put("name", configuration.currency(currencyId).displayName());
            row.put("balance", formatCurrency(currencyId, balance));
            row.put("text", "&0" + configuration.currency(currencyId).displayName() + "  &7" + formatCurrency(currencyId, balance));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> depositProductPacket(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (DepositProductDefinition product : configuration.depositProducts().values()) {
            if (!canUseProduct(player, product)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.id());
            row.put("name", product.displayName());
            row.put("description", product.description());
            row.put("currency", product.currencyId());
            row.put("duration", product.durationSeconds());
            row.put("min", formatCurrency(product.currencyId(), product.minAmount()));
            row.put("max", product.maxAmount().compareTo(BigDecimal.ZERO) <= 0 ? "不限" : formatCurrency(product.currencyId(), product.maxAmount()));
            row.put("text", "&0" + product.displayName() + "\n&7最低 " + formatCurrency(product.currencyId(), product.minAmount()) + "  " + product.description());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> fixedDepositPacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        int idx = 0;
        for (FixedDepositRecord deposit : repository.loadFixedDeposits(player.getUniqueId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", deposit.id());
            row.put("product", deposit.productId());
            row.put("currency", deposit.currencyId());
            row.put("principal", formatCurrency(deposit.currencyId(), deposit.principal()));
            row.put("rate", deposit.interestRate().toPlainString());
            row.put("maturesAt", TIME_FORMATTER.format(Instant.ofEpochMilli(deposit.maturesAt())));
            row.put("matured", deposit.maturesAt() <= now);
            row.put("text", "&0" + deposit.productId() + " &7本金 " + formatCurrency(deposit.currencyId(), deposit.principal()) + "\n&7到期 " + TIME_FORMATTER.format(Instant.ofEpochMilli(deposit.maturesAt())));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> searchResultPacket(ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        String keyword = normalizeToken(state.search());
        int index = 0;
        for (SlotItemRecord item : loadSlots(state.ownerType(), state.ownerId(), state.warehouseId())) {
            if (!slotMatches(item, state.categoryId(), keyword)) {
                continue;
            }
            result.put(Integer.toString(index++), Map.of(
                "slot", item.slot(),
                "name", item.displayName(),
                "amount", item.amount(),
                "category", item.categoryId(),
                "material", item.materialId(),
                "text", "&0#" + item.slot() + " " + item.displayName() + "\n&7x" + item.amount() + " " + item.categoryId()
            ));
        }
        return result;
    }

    private boolean slotMatches(SlotItemRecord item, String categoryId, String keyword) {
        String normalizedCategory = normalizeId(categoryId);
        if (!normalizedCategory.isBlank() && !"all".equals(normalizedCategory) && !normalizedCategory.equals(item.categoryId())) {
            return false;
        }
        return keyword == null
            || keyword.isBlank()
            || item.searchText().contains(keyword)
            || item.pinyin().contains(keyword)
            || item.initials().contains(keyword);
    }

    private String currentWarehouseName(Player player, ViewState state) throws Exception {
        UUID lookupUuid = state.previewMode() && state.previewTargetUuid() != null ? state.previewTargetUuid() : player.getUniqueId();
        if (OWNER_SHARED.equals(state.ownerType())) {
            return repository.loadSharedWarehousesByOwner(lookupUuid).stream()
                .filter(shared -> shared.id().equals(state.ownerId()))
                .map(SharedWarehouseRecord::name)
                .findFirst()
                .orElse("共享仓库");
        }
        WarehouseRecord record = personalWarehouseMap(lookupUuid).get(state.warehouseId());
        if (record != null && record.customName() != null && !record.customName().isBlank()) {
            return record.customName();
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        return definition == null ? "个人仓库" : ChatColor.translateAlternateColorCodes('&', definition.displayName());
    }

    private String firstPersonalWarehouseId() {
        return configuration.warehouses().isEmpty() ? "personal" : configuration.warehouses().keySet().iterator().next();
    }

    private int toBukkitBackpackSlot(int rawSlot) {
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }
        return rawSlot;
    }

    private int parseRawBackpackSlot(String rawValue) {
        String value = safe(rawValue);
        if (value.isBlank()) {
            return -1;
        }
        try {
            return new BigDecimal(value).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return -1;
        }
    }

    private int parsePacketSlot(String rawValue) {
        return parseRawBackpackSlot(rawValue);
    }

    private boolean validSlot(int slot) {
        return slot >= 0;
    }

    private String resolveDisplayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String itemJson(ItemStack itemStack, long displayAmount) {
        if (itemStackBridge == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        ItemStack display = itemStack.clone();
        display.setAmount((int) Math.max(1L, Math.min(displayAmount, Math.max(1, itemStack.getMaxStackSize()))));
        return itemStackBridge.itemToJson(display).orElse("");
    }

    private List<String> storedLoreLines(SlotItemRecord item) {
        try {
            ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
            return loreLines(stack);
        } catch (RuntimeException exception) {
            return List.of("&f物品描述读取失败。");
        }
    }

    private List<String> loreLines(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of("&f这个物品没有额外描述。");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
            return List.of("&f这个物品没有额外描述。");
        }
        List<String> result = new ArrayList<>();
        for (String line : meta.getLore()) {
            result.add(line == null ? "" : line.replace("k!", "§"));
        }
        return List.copyOf(result);
    }

    private String resolveCategory(ItemStack itemStack) {
        List<CategoryDefinition> definitions = new ArrayList<>(configuration.categories().values());
        definitions.sort(Comparator.comparingInt(CategoryDefinition::priority));
        for (CategoryDefinition definition : definitions) {
            if (definition.fallback() || definition.nbtPath().isBlank() || definition.values().isEmpty()) {
                continue;
            }
            String value = normalizeId(resolvePath(itemStack, definition.nbtPath()));
            if (!value.isBlank() && definition.values().contains(value)) {
                return definition.id();
            }
        }
        return configuration.otherCategory().id();
    }

    private String resolvePath(ItemStack itemStack, String rawPath) {
        String path = safe(rawPath).toLowerCase(Locale.ROOT);
        ItemMeta meta = itemStack.getItemMeta();
        if ("material".equals(path)) {
            return itemStack.getType().name();
        }
        if ("display-name".equals(path) || "name".equals(path)) {
            return resolveDisplayName(itemStack);
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
        if (string != null) {
            return string;
        }
        Integer integer = container.get(key, PersistentDataType.INTEGER);
        if (integer != null) {
            return Integer.toString(integer);
        }
        Long longValue = container.get(key, PersistentDataType.LONG);
        return longValue == null ? "" : Long.toString(longValue);
    }

    private boolean isSecondaryUnlocked(UUID playerUuid) throws Exception {
        if (repository.loadSecurity(playerUuid).isEmpty()) {
            return true;
        }
        return unlockedUntil.getOrDefault(playerUuid, 0L) > System.currentTimeMillis();
    }

    private boolean validatePassword(UUID playerUuid, String candidate) throws Exception {
        Optional<SecurityRecord> optional = repository.loadSecurity(playerUuid);
        if (optional.isEmpty()) {
            return true;
        }
        try {
            SecurityRecord security = optional.get();
            byte[] salt = Base64.getDecoder().decode(security.saltBase64());
            byte[] expected = Base64.getDecoder().decode(security.hashBase64());
            byte[] actual = derivePasswordHash(safe(candidate).toCharArray(), salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derivePasswordHash(char[] password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, PASSWORD_HASH_ITERATIONS, PASSWORD_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive warehouse password hash.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private String encryptPasswordForInspection(String password) {
        if (!configuration.security().allowAdminPasswordReveal() || configuration.security().adminRevealSecret().isBlank()) {
            return "";
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(configuration.security().adminRevealSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] merged = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, merged, 0, iv.length);
            System.arraycopy(encrypted, 0, merged, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(merged);
        } catch (GeneralSecurityException exception) {
            return "";
        }
    }

    private SharedPermissionTier resolveSharedTier(Player player) {
        return configuration.shared().permissionTiers().values().stream()
            .filter(tier -> tier.permission().isBlank() || player.hasPermission(tier.permission()))
            .max(Comparator.comparingInt(SharedPermissionTier::priority))
            .orElseGet(() -> configuration.shared().permissionTiers().values().iterator().next());
    }

    private boolean canUseProduct(Player player, DepositProductDefinition product) {
        return product.permission().isBlank() || player.hasPermission(product.permission());
    }

    private WarehouseLevelDefinition sharedInitialLevel() {
        Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
        if (levels.isEmpty()) {
            return null;
        }
        WarehouseLevelDefinition level = levels.get(configuration.shared().defaultLevel());
        return level == null ? levels.values().iterator().next() : level;
    }

    private WarehouseModuleConfiguration.UpgradeCost upgradeCost(Map<Integer, WarehouseLevelDefinition> levels, int currentLevel) {
        WarehouseLevelDefinition current = levels.get(currentLevel);
        WarehouseLevelDefinition next = levels.get(currentLevel + 1);
        return current == null || current.upgradeCost() == null ? next == null ? null : next.upgradeCost() : current.upgradeCost();
    }

    private Optional<SharedWarehouseRecord> currentSharedRecord(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return Optional.empty();
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(shared -> shared.id().equals(state.ownerId()))
            .findFirst();
    }

    private String formatCurrency(String currencyId, BigDecimal amount) {
        return currencyBridgeManager.format(currencyId, amount == null ? BigDecimal.ZERO : amount);
    }

    private String currencyDisplayName(String currencyId) {
        CurrencyDefinition definition = configuration.currency(currencyId);
        return definition == null ? currencyId : definition.displayName();
    }

    private String formatCurrencyWithName(String currencyId, BigDecimal amount) {
        return formatCurrency(currencyId, amount) + " " + currencyDisplayName(currencyId);
    }

    private String sharedRoleName(String role) {
        WarehouseModuleConfiguration.SharedRoleNames names = configuration.shared().roleNames();
        return switch (normalizeId(role)) {
            case "owner" -> names.owner();
            case "viewer" -> names.viewer();
            default -> names.member();
        };
    }

    private String sharedCreateCostText() {
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        return cost == null ? "创建共享仓库免费" : "创建共享仓库消耗 " + formatCurrencyWithName(cost.currencyId(), cost.amount());
    }

    private boolean withdrawCost(Player player, WarehouseModuleConfiguration.UpgradeCost cost, String unknownCurrencyMessage) {
        if (cost == null) {
            return true;
        }
        var bridge = currencyBridgeManager.bridge(cost.currencyId());
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? unknownCurrencyMessage : bridge.unavailableReason());
            return false;
        }
        var result = bridge.withdraw(player, cost.amount());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            return false;
        }
        return true;
    }

    private void refundCost(Player player, WarehouseModuleConfiguration.UpgradeCost cost) {
        if (cost == null) {
            return;
        }
        var bridge = currencyBridgeManager.bridge(cost.currencyId());
        if (bridge != null && bridge.available()) {
            bridge.deposit(player, cost.amount());
        }
    }

    private void sendMessage(Player player, boolean success, String message) {
        player.sendMessage(PREFIX + (success ? ChatColor.GREEN : ChatColor.RED) + message);
    }

    private void debug(String message) {
        if (configuration.debug()) {
            this.logger.info("[WarehouseDebug] " + message);
        }
    }

    private String summarizeStoragePacket(Map<String, Object> packet) {
        Map<?, ?> slots = mapValue(packet.get("slots"));
        Map<?, ?> backpack = mapValue(packet.get("backpack"));
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " page=" + packet.get("page")
            + "/" + packet.get("pageTotal")
            + " pageText=" + packet.get("pageText")
            + " matchedSlots=" + packet.get("matchedSlots")
            + " selectedSlot=" + packet.get("selectedSlot")
            + " slots=" + slots.size()
            + " nonEmptySlots=" + countRows(slots, false)
            + " slotItemJson=" + countNonBlankField(slots, "itemJson")
            + " backpack=" + backpack.size()
            + " nonEmptyBackpack=" + countRows(backpack, false)
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }

    private String summarizeManagePacket(Map<String, Object> packet) {
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " categories=" + mapValue(packet.get("categories")).size()
            + " personalWarehouses=" + mapValue(packet.get("personalWarehouses")).size()
            + " sharedWarehouses=" + mapValue(packet.get("sharedWarehouses")).size()
            + " balances=" + mapValue(packet.get("balances")).size()
            + " products=" + mapValue(packet.get("products")).size()
            + " fixedDeposits=" + mapValue(packet.get("fixedDeposits")).size()
            + " sharedMembers=" + mapValue(packet.get("sharedMembers")).size()
            + " searchResults=" + mapValue(packet.get("searchResults")).size()
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }

    private Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private long countRows(Map<?, ?> rows, boolean emptyValue) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && Boolean.valueOf(emptyValue).equals(map.get("empty")))
            .count();
    }

    private long countNonBlankField(Map<?, ?> rows, String field) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && !safe(String.valueOf(map.get(field))).isBlank() && !"null".equals(String.valueOf(map.get(field))))
            .count();
    }

    private String describeStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.getType().isAir()) {
            return stack.getType().name() + " x" + stack.getAmount();
        }
        ItemMeta meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stack.getType().name()
            + " x" + stack.getAmount()
            + " max=" + stack.getMaxStackSize()
            + " hasMeta=" + (meta != null)
            + (name.isBlank() ? "" : " name=" + name);
    }

    private String shortHash(String hash) {
        String safeHash = safe(hash);
        return safeHash.length() <= 12 ? safeHash : safeHash.substring(0, 12);
    }

    private BigDecimal parseDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private int parseInt(String rawValue, int defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).intValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }

    private long parseLong(String rawValue, long defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).longValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }

    private SearchTokens toSearchTokens(String rawValue) {
        String stripped = ChatColor.stripColor(safe(rawValue));
        String normalized = normalizeToken(stripped);
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) {
                continue;
            }
            String[] values;
            try {
                values = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                values = null;
            }
            if (values != null && values.length > 0) {
                String candidate = values[0].replaceAll("[^a-z0-9]", "");
                if (!candidate.isBlank()) {
                    pinyin.append(candidate);
                    initials.append(candidate.charAt(0));
                    continue;
                }
            }
            if (Character.isLetterOrDigit(character)) {
                pinyin.append(Character.toLowerCase(character));
                initials.append(Character.toLowerCase(character));
            }
        }
        return new SearchTokens(normalized, pinyin.toString(), initials.toString());
    }

    private String sha256Hex(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(Character.forDigit((current >> 4) & 0xF, 16));
                builder.append(Character.forDigit(current & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to build warehouse item fingerprint.", exception);
        }
    }

    private static String value(List<String> data, int index, String defaultValue) {
        return data != null && data.size() > index ? safe(data.get(index)) : defaultValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeId(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeToken(String value) {
        String stripped = ChatColor.stripColor(safe(value));
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static String crop(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    private record SearchTokens(String searchText, String pinyin, String initials) {
    }

    private record DepositResult(boolean success, long storedAmount, int remainingAmount, String message) {
        static DepositResult success(long storedAmount, int remainingAmount) {
            return new DepositResult(true, storedAmount, remainingAmount, "");
        }

        static DepositResult failure(String message) {
            return new DepositResult(false, 0L, 0, message);
        }
    }

    private static final class ViewState {
        private String ownerType;
        private String ownerId;
        private String warehouseId;
        private String categoryId;
        private String search;
        private int page;
        private int selectedSlot;
        private boolean sharedEditMode;
        private boolean autoPickup;
        private boolean autoPickupMythic;
        private boolean autoPickupNotify;
        private boolean previewMode;
        private UUID previewTargetUuid;
        private String previewWarehouseId;

        private ViewState(String ownerType, String ownerId, String warehouseId, String categoryId, String search, int selectedSlot,
                          boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.warehouseId = warehouseId;
            this.categoryId = categoryId;
            this.search = search;
            this.page = 1;
            this.selectedSlot = selectedSlot;
            this.sharedEditMode = false;
            this.autoPickup = autoPickup;
            this.autoPickupMythic = autoPickupMythic;
            this.autoPickupNotify = autoPickupNotify;
            this.previewMode = false;
            this.previewTargetUuid = null;
            this.previewWarehouseId = null;
        }

        static ViewState initial(Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            return new ViewState(OWNER_PERSONAL, player.getUniqueId().toString(), "", "all", "", -1, autoPickup, autoPickupMythic, autoPickupNotify);
        }

        static ViewState shared(String sharedId, Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            return new ViewState(OWNER_SHARED, sharedId, sharedId, "all", "", -1, autoPickup, autoPickupMythic, autoPickupNotify);
        }

        static ViewState preview(UUID targetUuid, String warehouseId, String ownerType) {
            String effectiveWarehouseId = warehouseId;
            String effectiveOwnerId = targetUuid.toString();
            if (OWNER_SHARED.equals(ownerType)) {
                effectiveOwnerId = warehouseId;
                effectiveWarehouseId = warehouseId;
            }
            ViewState state = new ViewState(ownerType, effectiveOwnerId, effectiveWarehouseId, "all", "", -1, false, false, false);
            state.previewMode = true;
            state.previewTargetUuid = targetUuid;
            state.previewWarehouseId = warehouseId;
            return state;
        }

        String ownerType() { return ownerType; }
        String ownerId() { return ownerId; }
        String warehouseId() { return warehouseId; }
        String categoryId() { return categoryId; }
        String search() { return search; }
        int page() { return page; }
        int selectedSlot() { return selectedSlot; }
        boolean sharedEditMode() { return sharedEditMode; }
        boolean autoPickup() { return autoPickup; }
        boolean autoPickupMythic() { return autoPickupMythic; }
        boolean autoPickupNotify() { return autoPickupNotify; }
        boolean previewMode() { return previewMode; }
        UUID previewTargetUuid() { return previewTargetUuid; }
        String previewWarehouseId() { return previewWarehouseId; }
        void setAutoPickup(boolean autoPickup) { this.autoPickup = autoPickup; }
        void setAutoPickupMythic(boolean autoPickupMythic) { this.autoPickupMythic = autoPickupMythic; }
        void setAutoPickupNotify(boolean autoPickupNotify) { this.autoPickupNotify = autoPickupNotify; }
        void setOwnerType(String ownerType) { this.ownerType = ownerType; }
        void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        void setSearch(String search) { this.search = search; }
        void setPage(int page) { this.page = page; }
        void setSelectedSlot(int selectedSlot) { this.selectedSlot = selectedSlot; }
        void setSharedEditMode(boolean sharedEditMode) { this.sharedEditMode = sharedEditMode; }
    }
}


