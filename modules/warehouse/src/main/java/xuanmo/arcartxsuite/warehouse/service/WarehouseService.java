package xuanmo.arcartxsuite.warehouse.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;

import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.api.capability.PickupInterceptor;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerLockService;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerPayloadCodec;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.CategoryDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.DepositProductDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.InterestTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SharedPermissionTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SortField;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseLevelDefinition;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.FixedDepositRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.PendingTransfer;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedMemberRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;
import xuanmo.arcartxsuite.api.capability.WarehouseRewardDepositable.Result;
import java.util.logging.Logger;

/**
 * Warehouse 核心业务服务，统筹个人仓库、共享仓库、多货币银行、二级密码与自动拾取逻辑。
 * <p>
 * 通过 {@link PacketBridgeAPI} 与客户端 AXUI 通信，所有状态变更后回发更新包刷新界面。
 * 共享仓库编辑权限由共享数据库租约和事务内校验决定。
 */
public final class WarehouseService implements Listener, WarehouseAccess {
    private record PickupKey(UUID player, UUID item) { }
    private record AutomaticScope(String warehouseId,long until) { }
    private static final class NativePickup {
        final Player player;
        final Item item;
        final ItemStack snapshot;
        final long expires;
        long lastContactPass;

        NativePickup(Player player, Item item, ItemStack snapshot, long expires, long lastContactPass) {
            this.player = player;
            this.item = item;
            this.snapshot = snapshot;
            this.expires = expires;
            this.lastContactPass = lastContactPass;
        }
    }
    private static final int MAX_PENDING_NATIVE_PICKUPS = 64;
    private static final int NATIVE_PICKUPS_PER_TICK = 12;
    private static final long NATIVE_PICKUP_WAIT_NANOS = 5_000_000_000L;
    private static final long NATIVE_PICKUP_RETRY_NANOS = 500_000_000L;
    private final Map<UUID,AutomaticScope> automaticScopes=new HashMap<>();
    private final Map<UUID, NativePickup> pendingNativePickup = new LinkedHashMap<>();
    private org.bukkit.scheduler.BukkitTask nativePickupDrain;
    private long nativePickupPass;
    // Only previews are reused; the ground operation still validates and stores its own item bytes.
    private static final int GROUND_PREVIEW_MAX_ENTRIES = 128;
    private static final int GROUND_PREVIEW_MAX_BYTES = 4096;
    private static final int GROUND_PREVIEW_MAX_CHARS = 4096;
    private record GroundPreviewKey(byte[] bytes, int hash) {
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof GroundPreviewKey key && Arrays.equals(bytes, key.bytes);
        }
    }
    private final long[] recentGroundPreviewHashes = new long[256];
    private final Map<GroundPreviewKey, String> groundPreviews =
        new LinkedHashMap<>(32, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<GroundPreviewKey, String> entry) {
                return size() > GROUND_PREVIEW_MAX_ENTRIES;
            }
        };
    private final Map<PickupKey, Long> nativePickupRetry = new LinkedHashMap<>(128,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PickupKey,Long> e){return size()>2048;}
    };
    private final Set<UUID> closingCrafting = new java.util.HashSet<>();

    private record TokenLease(UUID playerUuid, String uiId) {
    }

    private final MessageProvider messages;
    private final NativeDepositNotice[] nativeDepositNotices = new NativeDepositNotice[65];

    private record NativeDepositNotice(String body, String legacy, Object component) {}

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    static final String OWNER_PERSONAL = "personal";
    static final String OWNER_SHARED = "shared";
    private static final String STORAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_storage.yml";
    private static final String STORAGE_UI_FILE_PATH = "ui/warehouse/warehouse_storage.yml";
    private static final String MANAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_manage.yml";
    private static final String MANAGE_UI_FILE_PATH = "ui/warehouse/warehouse_manage.yml";
    private static final String BANK_UI_RESOURCE_PATH = "arcartx/ui/warehouse_bank.yml";
    private static final String BANK_UI_FILE_PATH = "ui/warehouse/warehouse_bank.yml";
    static final int SLOT_COUNT = 54;
    static final long MAX_AGGREGATED_AMOUNT = Integer.MAX_VALUE;
    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

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
    private final Map<UUID,String> noticeTargets=new java.util.concurrent.ConcurrentHashMap<>();
    final xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge;
    private final PacketGuardAPI packetGuard;
    private final UiResourceExporter uiResourceExporter;
    final WarehouseModuleConfiguration configuration;
    private final List<CategoryDefinition> priorityCategories;
    private final List<WarehouseDefinition> defaultWarehouses;
    final WarehouseRepository repository;
    final ItemSourceRegistry itemSourceRegistry;
    final ItemMatcherAPI itemMatcherSupport;
    private final CurrencyBridgeAPI currencyBridgeManager;
    final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final Supplier<PickupNotifiable> pickupNotifiableSupplier;
    private Supplier<PickupInterceptor> pickupInterceptorSupplier = () -> null;

    final Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier;
    /** 玩家当前 UI 视图状态（ownerType / warehouseId / page / search 等）。 */
    final ConcurrentMap<UUID, ViewState> viewStates = new ConcurrentHashMap<>();
    final WarehousePacketFactory packetFactory;
    /** 共享仓库编辑互斥锁：共享仓库 ID → 当前编辑者（含子服 nodeId）。 */
    private static final long SHARED_LEASE_MS = 60_000L;
    private final ConcurrentMap<String, WarehouseRepository.SharedLease> sharedEditLocks = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask sharedLeaseHeartbeat;
    private final CrossServerAPI crossServerApi;
    private final CrossServerChannelConfig crossServerChannelConfig;
    private WarehouseCrossServerLockService crossServerLockService;
    private Supplier<EventBusCapability> eventBusProvider;
    private volatile boolean active;
    private volatile boolean stopping;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor background =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0, 128, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "AXS-Warehouse-IO");
                thread.setDaemon(true);
                return thread;
            });
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor cleanup =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "AXS-Warehouse-Close");
                thread.setDaemon(true);
                return thread;
            });
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback leaseRenewal =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::renewSharedLeases);
    private final WarehouseGroundTransfers groundTransfers;
    private WarehouseMaterials materialTransfers;
    private final String groundDepositRun = UUID.randomUUID().toString();
    private org.bukkit.scheduler.BukkitTask recoveryTask;
    private final Map<UUID, WarehouseRepository.PlayerSettingsRecord> pickupPreferences = new ConcurrentHashMap<>();
    /** 玩家上次展示仓库的时间戳（毫秒），用于冷却控制。 */
    private final ConcurrentMap<UUID, Long> showcaseCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activityRequests = new HashMap<>();
    /** 仓库级互斥锁：ownerType:ownerId:warehouseId → lock object，防止 deposit/withdraw 竞态。 */
    private final ConcurrentMap<String, Object> warehouseLocks = new ConcurrentHashMap<>();
    /** 仓库键 → 上次重归类时使用的分类规则指纹（用于存量物品懒重算）。 */
    private final ConcurrentMap<String, String> recategorizedRules = new ConcurrentHashMap<>();
    final ConcurrentMap<UUID, String> storageActionTokens = new ConcurrentHashMap<>();
    final ConcurrentMap<UUID, String> manageActionTokens = new ConcurrentHashMap<>();
    final ConcurrentMap<UUID, String> bankActionTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> actionTokenLocks = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Set<UUID>> committedActionTokens =
        ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<TokenLease> activeActionToken = new ThreadLocal<>();
    private final ThreadLocal<SecondaryPasswordAccess.Status> actionPasswordStatus = new ThreadLocal<>();
    private final ConcurrentMap<UUID, Object> pendingPasswordChecks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, java.util.concurrent.CompletionStage<SecondaryPasswordAccess.Status>> passwordViews = new ConcurrentHashMap<>();
    private final Set<String> deletingSharedWarehouses = ConcurrentHashMap.newKeySet();
    String storageRuntimeUiId = "";
    String manageRuntimeUiId = "";
    String bankRuntimeUiId = "";
    private String storageRegisteredUiId = "";
    private String manageRegisteredUiId = "";
    private String bankRegisteredUiId = "";

    /** 物品级锁定标记的 PDC key：写入物品 NBT，随物品移动/掉落/交易持久保留。 */
    private final NamespacedKey warehouseLockKey;

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
        CrossServerChannelConfig crossServerChannelConfig,
        Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this(plugin, logger, packetBridge, itemStackBridge, packetGuard, uiResourceExporter, configuration,
            repository, itemSourceRegistry, itemMatcherSupport, currencyBridgeManager,
            pickupNotifiableSupplier, crossServerApi, crossServerChannelConfig, null,
            secondaryPasswordSupplier, vanillaItemNameBridge);
    }

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
        CrossServerChannelConfig crossServerChannelConfig,
        MessageProvider messages,
        Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.packetGuard = packetGuard;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.priorityCategories = configuration.categories().values().stream()
            .filter(definition -> !definition.fallback()).sorted(Comparator.comparingInt(CategoryDefinition::priority)).toList();
        this.defaultWarehouses = configuration.warehouses().values().stream().filter(WarehouseDefinition::defaultOwned).toList();
        this.repository = repository;
        this.groundTransfers = new WarehouseGroundTransfers(plugin, repository, logger, background);
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.currencyBridgeManager = currencyBridgeManager;
        this.pickupNotifiableSupplier = pickupNotifiableSupplier;
        this.secondaryPasswordSupplier = secondaryPasswordSupplier;
        this.crossServerApi = crossServerApi;
        this.messages = messages;
        this.crossServerChannelConfig = crossServerChannelConfig == null
            ? CrossServerChannelConfig.disabled() : crossServerChannelConfig;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.warehouseLockKey = new NamespacedKey(plugin, "warehouse_locked");
        this.packetFactory = new WarehousePacketFactory(this);
    }

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
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this(plugin, logger, packetBridge, itemStackBridge, packetGuard, uiResourceExporter, configuration,
            repository, itemSourceRegistry, itemMatcherSupport, currencyBridgeManager,
            pickupNotifiableSupplier, null, CrossServerChannelConfig.disabled(), null, null, vanillaItemNameBridge);
    }

    /**
     * 启动服务：初始化数据库、绑定三套 AXUI、注册 Bukkit 事件监听。
     */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /** 设置拾取拦截器供应者（由 WarehouseModule 注入，可为 null）。 */
    public void setPickupInterceptorProvider(Supplier<PickupInterceptor> provider) {
        this.pickupInterceptorSupplier = provider == null ? () -> null : provider;
    }



    public void start() throws Exception {
        if (configuration.pickup().enabled() && Bukkit.getOnlinePlayers().isEmpty()) warmGroundItemMetadata();
        bindUis();
        active = true;
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerChannelConfig.enabled()
            && crossServerApi != null) {
            crossServerLockService = new WarehouseCrossServerLockService(
                plugin,
                logger,
                crossServerApi,
                crossServerChannelConfig,
                (id, lock) -> { },
                (unlock, ignored) -> { },
                this::closePublicPreviews
            );
            crossServerLockService.start();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        groundTransfers.start();
        sharedLeaseHeartbeat = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, leaseRenewal, 300L, 300L);
        bindFullInventoryPickup();
        // 模块重载后，向所有 UI 仍开着的在线玩家重新签发 token 并推送 update 包，
        // 避免重载导致 token 丢失后玩家操作被静默丢弃。
        recoveryTask = Bukkit.getScheduler().runTaskLater(plugin, this::reissueTokensForOpenUis, 20L);
    }

    private void warmGroundItemMetadata() {
        long started = System.nanoTime();
        try {
            // Use Paper's public conversion API when the runtime exposes it. A plain
            // Bukkit stack alone does not enter the native, tagged-item metadata path.
            var convert = org.bukkit.inventory.ItemFactory.class.getMethod("ensureServerConversions", ItemStack.class);
            var probe = new ItemStack(Material.IRON_INGOT);
            ItemMeta meta = probe.getItemMeta();
            if (meta == null) return;
            meta.setDisplayName("Warehouse");
            meta.getPersistentDataContainer().set(warehouseLockKey, PersistentDataType.BYTE, (byte) 1);
            if (!probe.setItemMeta(meta)) return;
            ItemStack nativeProbe = (ItemStack) convert.invoke(Bukkit.getItemFactory(), probe);
            if (nativeProbe == null || nativeProbe.getItemMeta() == null) return;
            ItemStack decoded = ItemSerializer.deserialize(ItemSerializer.serialize(nativeProbe));
            if (decoded != null && nativeProbe.isSimilar(decoded)) {
                debug("物品元数据启服预热: " + (System.nanoTime() - started) / 1_000_000L + " ms");
            }
        } catch (NoSuchMethodException unavailable) {
            // Spigot has no Paper conversion entry point.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            debug("物品元数据启服预热跳过: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 模块重载后，向所有 UI 仍开着的在线玩家重新签发 action token 并推送 update 包。
     * <p>
     * 重载会清空 token map，但客户端 UI 可能仍开着（var.actionToken 是旧值）。
     * 此方法在 start() 后延迟 1 秒执行，为仍打开的 UI 重新 issue token 并推送 update，
     * 使客户端 var.actionToken 与服务端同步。
     */
    private void reissueTokensForOpenUis() {
        if (!active || packetBridge == null) {
            return;
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            try {
                boolean anyOpen = false;
                if (!storageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, storageRuntimeUiId)) {
                    issueActionToken(player, storageRuntimeUiId);
                    anyOpen = true;
                }
                if (!manageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, manageRuntimeUiId)) {
                    issueActionToken(player, manageRuntimeUiId);
                    anyOpen = true;
                }
                if (!bankRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, bankRuntimeUiId)) {
                    issueActionToken(player, bankRuntimeUiId);
                    anyOpen = true;
                }
                if (anyOpen) {
                    refreshBoth(player);
                    debug("reissue-tokens player=" + player.getName() + " (reload recovery)");
                }
            } catch (Exception exception) {
                debug("reissue-tokens-failed player=" + player.getName() + " error=" + exception.getMessage());
            }
        }
    }

    public void setMaterialTransfers(WarehouseMaterials materialTransfers) {
        this.materialTransfers = materialTransfers;
    }

    /**
     * 关闭服务：注销事件监听、清理 UI 回调与玩家状态、关闭数据库连接。
     */
    public void shutdown() {
        if (stopping) return;
        stopping = true;
        active = false;
        leaseRenewal.close();
        var leases = Map.copyOf(sharedEditLocks);
        sharedEditLocks.clear();
        try {
        groundTransfers.close();
        noticeTargets.clear();
        if (nativePickupDrain != null) nativePickupDrain.cancel();
        nativePickupDrain = null;
        pendingNativePickup.clear();
        groundPreviews.clear();
        Arrays.fill(recentGroundPreviewHashes, 0);
        Arrays.fill(nativeDepositNotices, null);
        nativePickupRetry.clear();
        closingCrafting.clear();
        automaticScopes.clear();
        if (recoveryTask != null) recoveryTask.cancel();
        if (sharedLeaseHeartbeat != null) sharedLeaseHeartbeat.cancel();
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerLockService != null) {
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
        sharedEditLocks.clear();
        storageActionTokens.clear();
        manageActionTokens.clear();
        bankActionTokens.clear();
        actionTokensInFlight.clear();
        pendingPasswordChecks.clear();
        passwordViews.clear();
        actionTokenLocks.clear();
        warehouseLocks.clear();
        recategorizedRules.clear();
        showcaseCooldowns.clear();
        activityRequests.clear();
        pickupPreferences.clear();
        } finally {
            background.shutdown();
            java.util.concurrent.CompletableFuture.allOf(background.stopped().toCompletableFuture(),
                leaseRenewal.stopped().toCompletableFuture()).whenComplete((ignored, failure) -> {
                    if (leases.isEmpty()) cleanup.shutdown();
                    else cleanup.execute(() -> {
                        try { leases.forEach((id, lease) -> releaseSharedLeaseInStorage(id, lease)); }
                        finally { cleanup.shutdown(); }
                    });
                });
        }
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped() {
        return java.util.concurrent.CompletableFuture.allOf(background.stopped().toCompletableFuture(),
            leaseRenewal.stopped().toCompletableFuture(), cleanup.stopped().toCompletableFuture());
    }

    public boolean isStopping() { return stopping; }

    public int cachedPlayerCount() {
        return viewStates.size();
    }

    public boolean mythicBridgeAvailable() {
        return itemSourceRegistry.mythicBridgeAvailable();
    }

    public boolean neigeBridgeAvailable() {
        return itemSourceRegistry.neigeBridgeAvailable();
    }

    public boolean crossServerActive() {
        return BuildEdition.CROSS_SERVER_INCLUDED && crossServerLockService != null && crossServerLockService.isActive();
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
     * 返回已注册到 ArcartX 的 UI ID 列表（供 Module 记录到基类生命周期管理）。
     *
     * @return 已注册的 UI ID 列表，未注册的条目自动跳过
     */
    public java.util.List<String> registeredUiIds() {
        java.util.List<String> ids = new java.util.ArrayList<>(3);
        if (!storageRegisteredUiId.isBlank()) ids.add(storageRegisteredUiId);
        if (!manageRegisteredUiId.isBlank()) ids.add(manageRegisteredUiId);
        if (!bankRegisteredUiId.isBlank()) ids.add(bankRegisteredUiId);
        return java.util.List.copyOf(ids);
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
        return automaticDeposit(player,itemStack,true,false);
    }

    record GroundPlan(WarehouseRepository.MaterialTransfer transfer,
                      List<WarehouseRepository.SlotChange> changes, long stored, boolean notifyPlayer) {}

    private record GroundItemSnapshot(byte[] serialized, String category, String displayName, String material,
                                      String preview, int amount, long timestamp, String lore) {}

    private record RewardItem(GroundItemSnapshot snapshot, boolean mythic) {}

    public CompletionStage<Result> depositReward(UUID operation, UUID owner, List<ItemStack> stacks) {
        if (!active || !Bukkit.isPrimaryThread() || stacks.isEmpty() || stacks.size() > 8) {
            return CompletableFuture.completedFuture(Result.RETRY);
        }
        var player = Bukkit.getPlayer(owner);
        var entitlements = player != null && xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(owner)
            ? defaultEntitlements(player) : List.<String>of();
        var items = new ArrayList<RewardItem>(stacks.size());
        var fingerprint = new StringBuilder();
        boolean eligible = configuration.pickup().enabled();
        for (var stack : stacks) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                return CompletableFuture.completedFuture(Result.RETRY);
            }
            boolean mythic = itemSourceRegistry.isMythicItem(stack);
            ItemStack prototype = stack.clone();
            prototype.setAmount(1);
            byte[] bytes = ItemSerializer.serialize(prototype);
            fingerprint.append(WarehouseTexts.sha256Hex(bytes)).append(':').append(stack.getAmount()).append(';');
            if (eligible && (isBlacklisted(stack) || isItemLocked(stack)
                    || mythic && !configuration.pickup().mythicLootEnabled()
                    || validateSerializedItem(owner.toString(), prototype, bytes) != null)) eligible = false;
            items.add(new RewardItem(snapshotDeposit(prototype, bytes, stack.getAmount(), eligible), mythic));
        }
        String evidence = WarehouseTexts.sha256Hex(fingerprint.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean permitted = eligible;
        try {
            return CompletableFuture.supplyAsync(
                () -> commitReward(operation, owner, items, entitlements, evidence, permitted), background);
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            return CompletableFuture.completedFuture(Result.RETRY);
        }
    }

    private Result commitReward(
            UUID operation, UUID owner, List<RewardItem> items, List<String> entitlements, String evidence, boolean eligible) {
        try {
            var receipt = repository.materialTransfer(operation.toString(), owner.toString()).orElse(null);
            if (receipt != null) return rewardResult(receipt, evidence);
            if (eligible) {
                var preferences = repository.loadPlayerSettings(owner).orElseGet(() -> defaultPickupSettings(owner));
                eligible = items.stream().allMatch(item -> item.mythic() ? preferences.autoMythic() : preferences.autoPickup());
            }
            String warehouse = "";
            var changes = List.<WarehouseRepository.SlotChange>of();
            if (eligible) {
                var owned = personalWarehouseMap(owner);
                warehouse = firstOwnedWarehouse(owned);
                if (warehouse.isEmpty()) {
                    for (String id : entitlements) repository.ensureDefaultPersonalWarehouse(owner, id, System.currentTimeMillis());
                    owned = personalWarehouseMap(owner);
                    warehouse = firstOwnedWarehouse(owned);
                }
                if (!warehouse.isEmpty()) {
                    long capacity = configuration.warehouse(warehouse).baseSlots() + Math.max(0, owned.get(warehouse).extraSlots());
                    changes = planRewardBatch(owner, warehouse, capacity, items);
                }
            }
            long now = System.currentTimeMillis();
            boolean stored = !changes.isEmpty();
            var written = new WarehouseRepository.MaterialTransfer(operation.toString(), owner.toString(), warehouse,
                "", "", stored ? "REWARD_STORED" : "REWARD_DECLINED", "reward-overflow-v1", "", evidence, "",
                now, now, stored ? "奖励已全部入库" : "仓库未接收该批奖励");
            if (repository.commitRewardDeposit(changes, written)) {
                if (stored) notifyCommittedReward(owner, items);
                return rewardResult(written, evidence);
            }
        } catch (Exception failure) {
            // A second node may have committed the same request while this one planned its slots.
            logger.fine("奖励入库回执未确认 " + operation + ": " + failure.getMessage());
        }
        try {
            return repository.materialTransfer(operation.toString(), owner.toString())
                .map(receipt -> rewardResult(receipt, evidence))
                .orElse(Result.RETRY);
        } catch (java.sql.SQLException failure) {
            return Result.RETRY;
        }
    }

    private static Result rewardResult(
            WarehouseRepository.MaterialTransfer receipt, String evidence) {
        if ("reward-overflow-v1".equals(receipt.adapter()) && evidence.equals(receipt.evidence())) {
            if ("REWARD_STORED".equals(receipt.state())) return Result.STORED;
            if ("REWARD_DECLINED".equals(receipt.state())) return Result.DECLINED;
        }
        return Result.RETRY;
    }

    private void notifyCommittedReward(UUID owner, List<RewardItem> items) {
        if (!active || eventBusProvider == null) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(owner);
                if (!active || player == null || !player.isOnline()) return;
                for (var item : items) {
                    publishDepositEvent(player, item.snapshot().material(), item.snapshot().amount());
                }
            });
        } catch (RuntimeException unavailable) {
            logger.fine("奖励已入库，通知调度暂不可用");
        }
    }

    private String firstOwnedWarehouse(Map<String, WarehouseRecord> owned) {
        return configuration.warehouses().keySet().stream().filter(owned::containsKey).findFirst().orElse("");
    }

    private List<WarehouseRepository.SlotChange> planRewardBatch(UUID owner, String warehouse, long capacity,
                                                                  List<RewardItem> items) throws java.sql.SQLException {
        var slots = new LinkedHashMap<Integer, SlotItemRecord>();
        for (var slot : repository.loadSlotSummaries(OWNER_PERSONAL, owner.toString(), warehouse)) slots.put(slot.slot(), slot);
        var changes = new LinkedHashMap<Integer, WarehouseRepository.SlotChange>();
        for (var item : items) {
            var snapshot = item.snapshot();
            var plan = planDepositSlots(owner.toString(), warehouse, List.copyOf(slots.values()), capacity, snapshot);
            if (plan.stored() != snapshot.amount()) return List.of();
            for (var change : plan.changes()) {
                int slot = change.after().slot();
                var previous = changes.get(slot);
                changes.put(slot, new WarehouseRepository.SlotChange(previous == null ? change.before() : previous.before(), change.after()));
                slots.put(slot, change.after());
            }
        }
        return List.copyOf(changes.values());
    }

    private GroundItemSnapshot snapshotDeposit(ItemStack prototype, byte[] bytes, int amount, boolean withPreview) {
        return new GroundItemSnapshot(bytes, resolveCategory(prototype), resolveDisplayName(prototype),
            prototype.getType().name(), withPreview ? groundItemPreview(prototype, bytes) : "", amount,
            System.currentTimeMillis(), WarehouseTexts.loreText(prototype));
    }

    public boolean depositFromGroundAsync(Player player, Item source, ItemStack stack,
        java.util.function.BooleanSupplier authorize,
        java.util.function.Predicate<WarehouseAutoDepositable.GroundDepositResult> completion) {
        return depositFromGroundAsync(player, source, stack, authorize, (item, result) -> completion.test(result));
    }

    boolean depositFromGroundAsync(Player player, Item source, ItemStack stack, java.util.function.BooleanSupplier authorize,
        java.util.function.BiPredicate<String, WarehouseAutoDepositable.GroundDepositResult> completion) {
        if (!active || source == null || !configuration.pickup().enabled() || stack == null || stack.getAmount() <= 0
            || stack.getType().isAir() || isBlacklisted(stack) || isItemLocked(stack)) return false;
        boolean mythic = itemSourceRegistry.isMythicItem(stack);
        if (mythic && !configuration.pickup().mythicLootEnabled()) return false;
        UUID playerId = player.getUniqueId();
        ItemStack prototype = stack.clone();
        prototype.setAmount(1);
        byte[] bytes = ItemSerializer.serialize(prototype);
        if (validateSerializedItem(player.getName(), prototype, bytes) != null) return false;
        var item = snapshotDeposit(prototype, bytes, stack.getAmount(), true);
        var entitlements = defaultEntitlements(player);
        String node = WarehouseTexts.crop(localNodeId(), 36);
        UUID sourceId = source.getUniqueId();
        return groundTransfers.begin(source, stack, authorize, (id, original, target) ->
            planGroundDeposit(playerId, item, mythic, entitlements, node, sourceId, id, original, target),
            result -> {
                if (result.storedAmount() > 0 && player.isOnline()) publishDepositEvent(player, stack, result.storedAmount());
                return completion.test(item.preview(), result);
            });
    }

    private List<String> defaultEntitlements(Player player) {
        List<String> entitlements = new ArrayList<>(defaultWarehouses.size());
        for (WarehouseDefinition definition : defaultWarehouses) {
            if (definition.permission().isBlank() || player.hasPermission(definition.permission())) {
                entitlements.add(definition.id());
            }
        }
        return entitlements;
    }

    private String groundItemPreview(ItemStack prototype, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > GROUND_PREVIEW_MAX_BYTES
            || itemStackBridge == null || !itemStackBridge.isAvailable()) return itemJson(prototype);
        // serialize() returns an owned array that is never mutated. Full equality includes item metadata.
        int hash = Arrays.hashCode(bytes);
        var key = new GroundPreviewKey(bytes, hash);
        String preview = groundPreviews.get(key);
        if (preview != null) return preview;
        // The fingerprint only controls admission. A preview hit always compares all item bytes.
        int bucket = (hash ^ (hash >>> 16)) & (recentGroundPreviewHashes.length - 1);
        long fingerprint = Integer.toUnsignedLong(hash) + 1;
        boolean repeated = recentGroundPreviewHashes[bucket] == fingerprint;
        recentGroundPreviewHashes[bucket] = fingerprint;
        preview = itemJson(prototype);
        if (repeated && !preview.isBlank() && preview.length() <= GROUND_PREVIEW_MAX_CHARS) {
            groundPreviews.put(key, preview);
        }
        return preview;
    }

    public boolean isGroundDepositReserved(Item item) { return groundTransfers.isReserved(item); }

    private GroundPlan planGroundDeposit(UUID playerId, GroundItemSnapshot item, boolean mythic, List<String> entitlements,
                                         String node, UUID entityId, String operation, int original, String source) throws Exception {
        var settings = pickupSettings(playerId);
        if (mythic ? !settings.autoMythic() : !settings.autoPickup()) return null;
        var owned = personalWarehouseMap(playerId);
        if (configuration.warehouses().keySet().stream().noneMatch(owned::containsKey)) {
            for (String id : entitlements) {
                if (!owned.containsKey(id)) repository.ensureDefaultPersonalWarehouse(playerId, id, System.currentTimeMillis());
            }
            owned = personalWarehouseMap(playerId);
        }
        String id = configuration.warehouses().keySet().stream().filter(owned::containsKey).findFirst().orElse("");
        if (id.isEmpty()) return null;
        String ownerId = playerId.toString();
        var slots = repository.loadSlotSummaries(OWNER_PERSONAL, ownerId, id);
        long capacity = configuration.warehouse(id).baseSlots() + Math.max(0, owned.get(id).extraSlots());
        var plan = planDepositSlots(ownerId, id, slots, capacity, item);
        if (plan.stored() <= 0) return null;
        var transfer = new WarehouseRepository.MaterialTransfer(operation, ownerId, id,
            node, groundDepositRun, "PICKUP", "ground-source-v1", WarehouseTexts.crop(entityId + " " + source, 255),
            "地面原始数量=" + original + " 预期入库数量=" + plan.stored() + " 物品=" + item.displayName(),
            plan.itemData() == null ? Base64.getEncoder().encodeToString(item.serialized()) : plan.itemData(),
            item.timestamp(), item.timestamp(), "掉落物等待入库；服务器中断时请核对原掉落物");
        return new GroundPlan(transfer, plan.changes(), plan.stored(), settings.autoNotify());
    }

    private record SlotPlan(List<WarehouseRepository.SlotChange> changes, long stored, String itemData) {}

    private SlotPlan planDepositSlots(String ownerId, String id, List<SlotItemRecord> slots, long capacity,
                                      GroundItemSnapshot item) {
        String hash = WarehouseTexts.sha256Hex(item.serialized());
        int remaining = item.amount();
        List<WarehouseRepository.SlotChange> changes = new ArrayList<>();
        for (var slot : slots) {
            if (remaining == 0 || !slot.itemHash().equals(hash)) continue;
            int added = (int) Math.min(remaining, Math.max(0, MAX_AGGREGATED_AMOUNT - slot.amount()));
            if (added > 0) {
                changes.add(new WarehouseRepository.SlotChange(slot,
                    withAmount(slot, slot.amount() + added, item.timestamp())));
                remaining -= added;
            }
        }
        int empty = remaining == 0 ? -1 : firstEmptySlot(slots, capacity);
        String itemData = null;
        if (remaining > 0 && empty >= 0) {
            var search = WarehouseTexts.toSearchTokens(item.displayName());
            itemData = Base64.getEncoder().encodeToString(item.serialized());
            changes.add(new WarehouseRepository.SlotChange(null, new SlotItemRecord(OWNER_PERSONAL, ownerId,
                id, empty, hash, item.category(), item.displayName(), item.material(), search.searchText(),
                search.pinyin(), search.initials(), itemData, item.preview(), remaining, item.timestamp(),
                item.timestamp(), item.lore())));
            remaining = 0;
        }
        return new SlotPlan(changes, item.amount() - remaining, itemData);
    }

    private WarehouseAutoDepositable.DepositResult automaticDeposit(Player player,ItemStack itemStack,boolean publish,boolean closing) {
        if (player == null || (!closing && !player.isOnline())) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "玩家不在线。");
        }
        ItemStack stack = itemStack == null ? null : itemStack.clone();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "没有可存入物品。");
        }
        // 服务器级开关兜底：总开关或战利品开关关闭时，外部模块（如 Pickup 扫描模式）的自动入库同样拒绝
        if (!configuration.pickup().enabled()) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "服务器已关闭自动入库功能。");
        }
        if (itemSourceRegistry.isMythicItem(stack) && !configuration.pickup().mythicLootEnabled()) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "服务器已关闭战利品入库功能。");
        }
        try {
            var settings = pickupSettings(player);
            boolean allowed = itemSourceRegistry.isMythicItem(stack) ? settings.autoMythic() : settings.autoPickup();
            if (!allowed) return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "玩家已关闭此类物品自动入库。");
            String warehouseId=automaticWarehouse(player);
            if(warehouseId.isEmpty())return new WarehouseAutoDepositable.DepositResult(false,0L,stack.getAmount(),"没有可用个人仓库");
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), warehouseId, stack);
            if (publish && result.success()) {
                publishDepositEvent(player, stack, result.storedAmount());
            }
            return new WarehouseAutoDepositable.DepositResult(
                result.success(),
                result.storedAmount(),
                result.success() ? result.remainingAmount() : stack.getAmount(),
                result.message()
            );
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("外部自动入库失败: " + exception.getMessage());
            }
            return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "自动入库失败。");
        }
    }

    void publishDepositEvent(Player player, ItemStack stack, long storedAmount) {
        publishDepositEvent(player, stack.getType().name(), storedAmount);
    }

    private void publishDepositEvent(Player player, String material, long storedAmount) {
        try {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("material", material);
        payload.put("amount", String.valueOf(storedAmount));
        eventBus.publish("axs.warehouse.item_deposited", player, payload);
        } catch (RuntimeException exception) {
            logger.warning("入库事件通知失败: " + exception.getMessage());
        }
    }

    public boolean shouldNotifyAutoDeposit(Player player) {
        try { return pickupSettings(player).autoNotify(); }
        catch (java.sql.SQLException exception) { return false; }
    }

    boolean pickupIntercepted(Player player) {
        PickupInterceptor interceptor = pickupInterceptorSupplier.get();
        return interceptor != null && interceptor.isAutoPickupIntercepted(player.getUniqueId());
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
            return ActionResult.failure(message("player.player-offline"));
        }
        if (storageRuntimeUiId == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure(message("player.ui-not-registered"));
        }
        try {
            ViewState previous = viewStates.get(player.getUniqueId());
            if (previous != null && previous.previewMode()) {
                viewStates.remove(player.getUniqueId(), previous);
            }
            ensureEntitlements(player);
            ViewState state = state(player);
            ensureCurrentWarehouse(player, state);
            openStorage(player);
            return ActionResult.success(message("player.opened"));
        } catch (Exception exception) {
            this.logger.warning("打开仓库失败: " + exception.getMessage());
            return ActionResult.failure(message("player.open-failed"));
        }
    }

    /**
     * 以只读预览模式打开目标玩家的仓库。
     * 管理员可以在目标的个人仓库与其拥有的共享仓库之间切换。
     *
     * @param viewer      预览者
     * @param targetUuid  被预览玩家 UUID
     * @param warehouseId 指定仓库 ID，空字符串则使用默认仓库
     * @return 操作结果
     */
    public ActionResult openPreview(Player viewer, UUID targetUuid, String warehouseId) {
        return openPreview(viewer, new WarehousePreview(targetUuid, true), warehouseId);
    }

    public ActionResult openShowcasePreview(Player viewer, UUID targetUuid, String warehouseId) {
        return openPreview(viewer, new WarehousePreview(targetUuid, false), warehouseId);
    }

    private ActionResult openPreview(Player viewer, WarehousePreview preview, String warehouseId) {
        if (viewer == null || !viewer.isOnline()) {
            return ActionResult.failure(message("player.player-offline"));
        }
        if (packetBridge == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure(message("player.ui-not-registered"));
        }
        if (!preview.permits(viewer)) return ActionResult.failure(message("player.no-permission"));
        ViewState previewState = null;
        try {
            WarehousePreview.Warehouses warehouses = preview.read(viewer, repository);
            String id = WarehouseTexts.normalizeId(warehouseId);
            if (id.isEmpty() && preview.administrator()) {
                id = warehouses.personal().keySet().stream().findFirst()
                    .orElseGet(() -> warehouses.shared().keySet().stream().findFirst().orElse(""));
            }
            String ownerType = warehouses.personal().containsKey(id) ? OWNER_PERSONAL : OWNER_SHARED;
            if (!warehouses.contains(ownerType, id)) return ActionResult.failure(message("player.warehouse-not-found", id));

            releaseCurrentSharedLock(viewer);
            previewState = ViewState.preview(preview, id, ownerType, configuration.search().defaultSortId());
            UUID viewerId = viewer.getUniqueId();
            invalidateActionToken(viewer, storageRuntimeUiId);
            viewStates.put(viewerId, previewState);
            if (!packetBridge.openUi(viewer, storageRuntimeUiId)) {
                viewStates.remove(viewerId, previewState);
                return ActionResult.failure(message("player.preview-open-failed"));
            }
            sendStorage(viewer, "update", previewState, warehouses);
            return ActionResult.success(message("player.preview-opened"));
        } catch (Exception exception) {
            if (previewState != null) closePreview(viewer, previewState);
            this.logger.warning("打开仓库预览失败: " + exception.getMessage());
            return ActionResult.failure(message("player.preview-open-failed"));
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
            return ActionResult.failure(message("player.player-offline"));
        }
        var showcaseConfig = configuration.showcase();
        if (!showcaseConfig.enabled()) {
            return ActionResult.failure(message("player.showcase-disabled"));
        }
        if (!player.hasPermission(showcaseConfig.permission())) {
            return ActionResult.failure(message("player.showcase-no-permission"));
        }
        UUID showcaseCooldownKey = player.getUniqueId();
        Long lastShowcase = showcaseCooldowns.get(showcaseCooldownKey);
        long now = System.currentTimeMillis();
        if (lastShowcase != null && (now - lastShowcase) < showcaseConfig.cooldownSeconds() * 1000L) {
            long remaining = (showcaseConfig.cooldownSeconds() * 1000L - (now - lastShowcase)) / 1000L;
            return ActionResult.failure(message("player.showcase-cooldown", remaining));
        }

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
        } catch (Exception exception) {
            this.logger.warning("收集展示仓库失败: " + exception.getMessage());
        }
        if (showcaseEntries.isEmpty()) {
            return ActionResult.failure(message("player.showcase-empty"));
        }

        if (showcaseConfig.useCard()) {
            String previewCommand = "/wh spreview " + playerUuid + " " + showcaseEntries.get(0)[2];
            boolean sent = false;
            if (packetBridge != null) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    sent |= packetBridge.sendChatCard(viewer, showcaseConfig.cardId(), Map.of(
                        "player_name", displayName, "preview_command", previewCommand));
                }
            }
            if (!sent) return ActionResult.failure(message("player.showcase-send-failed"));
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
        showcaseCooldowns.put(showcaseCooldownKey, now);
        return ActionResult.success(message("player.showcase-success"));
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
        return handleClientPacket(player, packetId, data, null);
    }

    private boolean handleClientPacket(Player player, String packetId, List<String> data,
                                       SecondaryPasswordAccess.Status passwordStatus) {
        if (!active || player == null || !player.isOnline() || packetId == null || !configuration.ui().packetId().equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : WarehouseTexts.safe(data.get(0)).toLowerCase(Locale.ROOT);
        debug("IN player=" + player.getName() + " packetId=" + packetId + " action=" + action
            + " args=" + (data == null ? 0 : data.size()));
        if (!isUiOpenForAction(player, action)) {
            sendMessage(player, false, message("player.ui-required"));
            return true;
        }
        ViewState currentViewState = viewStates.get(player.getUniqueId());
        if (currentViewState != null && currentViewState.previewMode()) {
            handlePreviewPacket(player, currentViewState, action, data);
            return true;
        }
        if ("close".equals(action)) {
            handleUiClosed(player);
            return true;
        }
        if (!AuthAccess.admitted(player.getUniqueId())) return true;
        if (!player.hasPermission("arcartxsuite.warehouse.use")) {
            sendMessage(player, false, message("player.no-permission"));
            return true;
        }
        TokenLease tokenLease = beginActionToken(player, action, data);
        if (requiresActionToken(action) && tokenLease == null) {
            // token 检查失败（并发冲突/token 轮换在途/模块重载后 token 丢失）静默丢弃，
            // 不再提示"操作令牌无效"——这些场景对正常玩家都是误报，UI 防抖会阻止重复点击。
            debug("token-check-failed player=" + player.getName() + " action=" + action + " (silent drop)");
            return true;
        }
        try {
            if (passwordStatus == null && requiresPasswordStatus(action)) {
                deferPasswordCheck(player, packetId, data, action, currentViewState);
                return true;
            }
            actionPasswordStatus.set(passwordStatus);
            switch (action) {
                case "open", "storage" -> openStorage(player);
                case "manage" -> openManage(player);
                case "bank" -> openBank(player);
                case "refresh" -> {
                    // 刷新时重新签发 action token，使客户端 var.actionToken 与服务端同步，
                    // 作为玩家遇到 token 失效时的手动恢复手段。
                    if (packetBridge != null) {
                        if (!storageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, storageRuntimeUiId)) {
                            issueActionToken(player, storageRuntimeUiId);
                        }
                        if (!manageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, manageRuntimeUiId)) {
                            issueActionToken(player, manageRuntimeUiId);
                        }
                        if (!bankRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, bankRuntimeUiId)) {
                            issueActionToken(player, bankRuntimeUiId);
                        }
                    }
                    refreshBoth(player);
                }
                case "showcase" -> {
                    ActionResult result = showcase(player);
                    if (!result.success()) {
                        sendMessage(player, false, result.message());
                    }
                }
                case "page" -> setPage(player, WarehouseTexts.value(data, 1, ""));
                case "warehouse_upgrade" -> upgradeCurrentWarehouse(player);
                case "buy_slots" -> buySlots(player, WarehouseTexts.parseLong(WarehouseTexts.value(data, 1, "1"), 1));
                case "warehouse" -> selectPersonalWarehouse(player, WarehouseTexts.value(data, 1, packetFactory.firstPersonalWarehouseId()));
                case "shared" -> selectSharedWarehouse(player, WarehouseTexts.value(data, 1, ""));
                case "shared_activity" -> showSharedActivity(player, data);
                case "shared_mode" -> setSharedMode(player, WarehouseTexts.value(data, 1, "readonly"));
                case "category" -> setCategory(player, WarehouseTexts.value(data, 1, "all"));
                case "search" -> setSearch(player, WarehouseTexts.value(data, 1, ""));
                case "sort" -> setSort(player, WarehouseTexts.value(data, 1, ""));
                case "select" -> selectSlot(player, parsePacketSlot(WarehouseTexts.value(data, 1, "-1")));
                case "deposit_slot" -> depositSlot(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.parseLong(WarehouseTexts.value(data, 2, "1"), 1L));
                case "deposit_all_backpack" -> depositAllBackpack(player);
                case "toggle_lock" -> toggleBackpackLock(player, WarehouseTexts.value(data, 1, ""));
                case "withdraw" -> withdraw(player, parsePacketSlot(WarehouseTexts.value(data, 1, "-1")), WarehouseTexts.parseLong(WarehouseTexts.value(data, 2, "1"), 1L), false, false);
                case "withdraw_quick" -> withdraw(player, parsePacketSlot(WarehouseTexts.value(data, 1, "-1")), WarehouseTexts.parseLong(WarehouseTexts.value(data, 2, "64"), 64L), false, true);
                case "withdraw_all" -> withdraw(player, parsePacketSlot(WarehouseTexts.value(data, 1, "-1")), Long.MAX_VALUE, true, false);
                case "bank_deposit" -> bankDeposit(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.parseDecimal(WarehouseTexts.value(data, 2, "0")));
                case "bank_withdraw" -> bankWithdraw(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.parseDecimal(WarehouseTexts.value(data, 2, "0")));
                case "select_currency" -> selectCurrency(player, WarehouseTexts.value(data, 1, ""));
                case "fixed_create" -> createFixedDeposit(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.parseDecimal(WarehouseTexts.value(data, 2, "0")));
                case "fixed_claim" -> claimFixedDeposit(player, WarehouseTexts.value(data, 1, ""));
                case "fixed_delete" -> deleteFixedDeposit(player, WarehouseTexts.value(data, 1, ""));
                case "shared_create" -> createSharedWarehouse(player, WarehouseTexts.value(data, 1, "共享仓库"));
                case "shared_rename" -> renameSharedWarehouse(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.value(data, 2, ""));
                case "shared_showcase_toggle" -> toggleSharedWarehouseShowcase(player, WarehouseTexts.value(data, 1, ""));
                case "personal_rename" -> renamePersonalWarehouse(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.value(data, 2, ""));
                case "personal_showcase_toggle" -> togglePersonalWarehouseShowcase(player, WarehouseTexts.value(data, 1, ""));
                case "shared_delete" -> deleteSharedWarehouse(player, WarehouseTexts.value(data, 1, ""));
                case "shared_invite" -> inviteSharedMember(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.value(data, 2, ""), WarehouseTexts.value(data, 3, "member"));
                case "shared_remove" -> removeSharedMember(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.value(data, 2, ""));
                case "shared_transfer" -> transferSharedWarehouse(player, WarehouseTexts.value(data, 1, ""), WarehouseTexts.value(data, 2, ""));
                case "shared_transfer_confirm" -> confirmPendingTransfer(player, WarehouseTexts.value(data, 1, ""));
                case "shared_transfer_reject" -> rejectPendingTransfer(player, WarehouseTexts.value(data, 1, ""));
                case "password_unlock" -> unlockPassword(player, WarehouseTexts.value(data, 1, ""));
                case "password_set" -> setPassword(player, WarehouseTexts.value(data, 1, ""));
                case "password_lock" -> lockPassword(player);
                case "toggle_auto_pickup" -> toggleAutoPickup(player, "pickup");
                case "toggle_auto_mythic" -> toggleAutoPickup(player, "mythic");
                case "toggle_auto_notify" -> toggleAutoPickup(player, "notify");
                default -> refreshBoth(player);
            }
        } catch (Exception exception) {
            this.logger.warning("处理仓库客户端包失败: " + exception.getMessage());
            debug("IN-ERROR player=" + player.getName() + " action=" + action + " error=" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            sendMessage(player, false, message("player.operation-failed"));
            try {
                refreshBoth(player);
            } catch (Exception refreshException) {
                this.logger.warning("刷新仓库 UI 失败: " + refreshException.getMessage());
            }
        } finally {
            actionPasswordStatus.remove();
            finishActionToken(player, tokenLease);
        }
        return true;
    }

    private boolean requiresPasswordStatus(String action) {
        return switch (action) {
            case "deposit_slot", "deposit_all_backpack", "withdraw", "withdraw_quick", "withdraw_all",
                 "bank_withdraw", "fixed_claim", "shared_invite", "shared_remove", "shared_transfer",
                 "warehouse_upgrade", "buy_slots", "shared_delete" -> true;
            default -> false;
        };
    }

    private void deferPasswordCheck(Player player, String packetId, List<String> data, String action, ViewState view) {
        UUID id = player.getUniqueId();
        Object request = new Object();
        if (pendingPasswordChecks.putIfAbsent(id, request) != null) return;
        var auth = AuthAccess.snapshot(id);
        String owner = view == null ? "" : view.ownerId();
        String warehouse = view == null ? "" : view.warehouseId();
        List<String> packet = new ArrayList<>(data);
        SecondaryPasswordAccess access = secondaryPasswordSupplier == null ? null : secondaryPasswordSupplier.get();
        java.util.function.BiConsumer<SecondaryPasswordAccess.Status, Throwable> resume = (status, failure) -> {
            if (!active) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!pendingPasswordChecks.remove(id, request) || !active
                    || Bukkit.getPlayer(id) != player || !player.isOnline() || viewStates.get(id) != view
                    || !player.hasPermission("arcartxsuite.warehouse.use")
                    || (view != null && (!owner.equals(view.ownerId()) || !warehouse.equals(view.warehouseId())))) return;
                var current = AuthAccess.snapshot(id);
                if (!current.admitted() || !java.util.Objects.equals(auth.connectionId(), current.connectionId())
                    || auth.generation() != current.generation()) return;
                handleClientPacket(player, packetId, packet, failure == null && status != null
                    ? status : SecondaryPasswordAccess.Status.LOCKED);
            });
        };
        if (access == null) {
            resume.accept(SecondaryPasswordAccess.Status.LOCKED, null);
        } else if ("shared_delete".equals(action)) {
            access.verifyAsync(player, WarehouseTexts.value(packet, 2, "")).whenComplete((ok, failure) -> {
                if (!active) return;
                if (failure != null || !Boolean.TRUE.equals(ok)) {
                    resume.accept(SecondaryPasswordAccess.Status.LOCKED, failure);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (pendingPasswordChecks.get(id) != request || Bukkit.getPlayer(id) != player
                        || !player.hasPermission("arcartxsuite.warehouse.use")) {
                        pendingPasswordChecks.remove(id, request);
                        return;
                    }
                    access.statusAsync(player).whenComplete(resume);
                });
            });
        } else {
            access.statusAsync(player).whenComplete(resume);
        }
    }

    private void handlePreviewPacket(Player player, ViewState state, String action, List<String> data) {
        if ("close".equals(action)) {
            handleUiClosed(player, storageRuntimeUiId);
            return;
        }
        if (packetBridge == null || !packetBridge.isUiOpen(player, storageRuntimeUiId)) return;
        if ("open".equals(action)) {
            if (!player.hasPermission("arcartxsuite.warehouse.use")) {
                sendMessage(player, false, message("player.no-permission"));
                return;
            }
            ActionResult result = openMenu(player);
            if (!result.success()) sendMessage(player, false, result.message());
            return;
        }
        try {
            WarehousePreview.Warehouses warehouses = authorizedPreview(player, state);
            if (warehouses == null) return;
            String value = WarehouseTexts.value(data, 1, "");
            switch (action) {
                case "page" -> updatePage(state, value);
                case "category" -> updateCategory(state, value);
                case "search" -> updateSearch(state, value);
                case "sort" -> updateSort(state, value);
                case "select" -> state.setSelectedSlot(packetFactory.actualSlotFromDisplay(state, parsePacketSlot(value)));
                case "warehouse", "shared" -> {
                    String ownerType = "warehouse".equals(action) ? OWNER_PERSONAL : OWNER_SHARED;
                    String id = WarehouseTexts.normalizeId(value);
                    if (!warehouses.contains(ownerType, id)) {
                        sendStorage(player, "update", state, warehouses);
                        sendMessage(player, false, message("player.warehouse-not-found", id));
                        return;
                    }
                    state.setOwnerType(ownerType);
                    state.setOwnerId(OWNER_SHARED.equals(ownerType) ? id : state.preview().target().toString());
                    state.setWarehouseId(id);
                    state.setSelectedSlot(-1);
                    state.setPage(1);
                }
                case "refresh" -> { }
                default -> {
                    sendMessage(player, false, message("player.preview-read-only"));
                    return;
                }
            }
            if (Set.of("page", "category", "search", "sort").contains(action)) state.setSelectedSlot(-1);
            sendStorage(player, "update", state, warehouses);
        } catch (Exception exception) {
            logger.warning("读取仓库预览失败: " + exception.getMessage());
            closePreview(player, state);
        }
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
            List<WarehouseRecord> personal = repository.loadPersonalWarehouses(playerUuid);
            List<FixedDepositRecord> deposits = repository.loadFixedDeposits(playerUuid);
            List<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(playerUuid);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "玩家: " + ChatColor.WHITE + playerName);
            lines.add(ChatColor.GRAY + "个人仓库: " + ChatColor.WHITE + used + "/" + capacity + " 格，合计 " + total + " 件");
            for (WarehouseRecord wh : personal) {
                String name = wh.customName() == null || wh.customName().isBlank() ? wh.warehouseId() : wh.customName();
                lines.add(ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE + wh.warehouseId() + ChatColor.GRAY + " (" + name + ") 已购 +" + wh.extraSlots() + " 格");
            }
            lines.add(ChatColor.GRAY + "共享仓库: " + ChatColor.WHITE + shared.size() + " 个");
            for (SharedWarehouseRecord sw : shared) {
                String ownerTag = sw.ownerUuid().equals(playerUuid) ? "主人" : "成员";
                lines.add(ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE + sw.id() + ChatColor.GRAY + " (" + sw.name() + ") " + ownerTag);
            }
            lines.add(ChatColor.GRAY + "定期存款: " + ChatColor.WHITE + deposits.stream().filter(d -> !d.claimed()).count() + " 笔未领取");
            callback.accept(lines);
        } catch (Exception exception) {
            errorCallback.accept("读取仓库信息失败: " + exception.getMessage());
        }
    }

    /**
     * 获取玩家个人仓库 ID 列表（用于管理员命令 tab 补全）。
     */
    public List<String> personalWarehouseIds(UUID playerUuid) {
        try {
            return repository.loadPersonalWarehouses(playerUuid).stream()
                .map(WarehouseRecord::warehouseId)
                .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 管理员清除玩家二级密码。
     *
     * @param playerUuid 目标玩家 UUID
     * @param callback   操作结果回调
     */
    public void describePersonalWarehouse(UUID playerUuid, String playerName, String warehouseId, Consumer<List<String>> callback, Consumer<String> errorCallback) {
        try {
            Optional<WarehouseRecord> warehouse = repository.loadPersonalWarehouses(playerUuid).stream().filter(record -> record.warehouseId().equals(warehouseId)).findFirst();
            if (warehouse.isEmpty()) { errorCallback.accept(message("player.warehouse-not-found", warehouseId)); return; }
            List<SlotItemRecord> slots = repository.loadSlotSummaries(OWNER_PERSONAL, playerUuid.toString(), warehouseId);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "玩家: " + ChatColor.WHITE + playerName);
            lines.add(ChatColor.GRAY + "仓库: " + ChatColor.WHITE + warehouseId + " (" + warehouse.get().customName() + ")");
            lines.add(ChatColor.GRAY + "物品槽: " + ChatColor.WHITE + slots.size());
            if (slots.isEmpty()) lines.add(ChatColor.DARK_GRAY + "- 空");
            for (SlotItemRecord slot : slots) lines.add(ChatColor.GRAY + "[" + slot.slot() + "] " + ChatColor.WHITE + slot.displayName() + ChatColor.GRAY + " x" + slot.amount() + " (" + slot.materialId() + ")");
            callback.accept(lines);
        } catch (Exception exception) { errorCallback.accept("读取仓库内容失败: " + exception.getMessage()); }
    }

    public ActionResult adminDeletePersonalWarehouse(UUID playerUuid, String warehouseId) {
        try {
            Optional<WarehouseRecord> warehouse = repository.loadPersonalWarehouses(playerUuid).stream().filter(record -> record.warehouseId().equals(warehouseId)).findFirst();
            if (warehouse.isEmpty()) return ActionResult.failure(message("player.warehouse-not-found", warehouseId));
            repository.deletePersonalWarehouse(playerUuid, warehouseId);
            Player target = Bukkit.getPlayer(playerUuid);
            if (target != null) refreshBoth(target);
            return ActionResult.success(message("admin.warehouse-deleted", playerUuid, warehouseId));
        } catch (Exception exception) { return ActionResult.failure(message("admin.delete-failed", exception.getMessage())); }
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
            String normalizedCurrency = WarehouseTexts.normalizeId(currencyId);
            if (!currencyBridgeManager.currencyIds().contains(normalizedCurrency)) {
                callback.accept(ActionResult.failure(message("admin.unknown-currency", normalizedCurrency)));
                return;
            }
            BigDecimal amount = WarehouseTexts.parseDecimal(amountText);
            BigDecimal current = bankBalance(playerUuid, normalizedCurrency);
            BigDecimal updated = switch (WarehouseTexts.normalizeId(mode)) {
                case "set" -> amount;
                case "add" -> current.add(amount);
                case "take" -> current.subtract(amount).max(BigDecimal.ZERO);
                default -> null;
            };
            if (updated == null) {
                callback.accept(ActionResult.failure(message("admin.unknown-bank-mode", mode)));
                return;
            }
            repository.setBankBalance(playerUuid, normalizedCurrency, updated, System.currentTimeMillis());
            callback.accept(ActionResult.success(message("admin.bank-updated", normalizedCurrency, formatCurrency(normalizedCurrency, updated))));
        } catch (Exception exception) {
            callback.accept(ActionResult.failure(message("admin.bank-update-failed", exception.getMessage())));
        }
    }

    public long totalItems(UUID playerUuid) {
        try {
            long total = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                total += repository.sumAmount(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId());
            }
            return total;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalUsed(UUID playerUuid) {
        try {
            long total = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                total += repository.countSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId());
            }
            return total;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalCapacity(UUID playerUuid) {
        try {
            long capacity = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                capacity += packetFactory.resolveCapacity(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId());
            }
            return capacity;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long categoryAmount(UUID playerUuid, String categoryId) {
        try {
            String normalized = WarehouseTexts.normalizeId(categoryId);
            long total = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                // 先确保该仓库分类已按当前规则懒重算，再走 SQL 聚合
                recategorizeIfStale(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId());
                total += repository.sumAmountByCategory(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId(), normalized);
            }
            return total;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public BigDecimal bankBalance(UUID playerUuid, String currencyId) {
        try {
            return repository.loadBankBalances(playerUuid).getOrDefault(WarehouseTexts.normalizeId(currencyId), BigDecimal.ZERO);
        } catch (Exception exception) {
            return BigDecimal.ZERO;
        }
    }

    public long fixedActive(UUID playerUuid, String currencyId) {
        try {
            String normalized = WarehouseTexts.normalizeId(currencyId);
            return repository.loadFixedDeposits(playerUuid).stream().filter(d -> !d.claimed() && normalized.equals(d.currencyId())).count();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long fixedMatured(UUID playerUuid, String currencyId) {
        try {
            long now = System.currentTimeMillis();
            String normalized = WarehouseTexts.normalizeId(currencyId);
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
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    private void clearPlayerState(UUID playerUuid) {
        activityRequests.remove(playerUuid);
        noticeTargets.remove(playerUuid);
        pickupPreferences.remove(playerUuid);
        viewStates.remove(playerUuid);
        storageActionTokens.remove(playerUuid);
        manageActionTokens.remove(playerUuid);
        bankActionTokens.remove(playerUuid);
        actionTokensInFlight.remove(playerUuid);
        pendingPasswordChecks.remove(playerUuid);
        passwordViews.remove(playerUuid);
        committedActionTokens.get().remove(playerUuid);
        actionTokenLocks.remove(playerUuid);
        releaseSharedLocks(playerUuid);
        for (String warehouseId : configuration.warehouses().keySet()) {
            String key = OWNER_PERSONAL + ':' + playerUuid + ':' + warehouseId;
            warehouseLocks.remove(key);
            recategorizedRules.remove(key);
        }
    }

    /** 数据清理提交后丢弃旧视图和令牌；null 表示全部玩家。 */
    public void invalidatePlayerData(@Nullable UUID playerUuid) {
        Runnable invalidate = () -> {
            if (!active) return;
            Set<UUID> targets = new HashSet<>();
            if (playerUuid != null) targets.add(playerUuid);
            else {
                targets.addAll(viewStates.keySet());
                targets.addAll(pickupPreferences.keySet());
            }
            for (UUID id : targets) {
                clearPlayerState(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null && online.isOnline() && packetBridge != null) {
                    for (String uiId : List.of(storageRuntimeUiId, manageRuntimeUiId, bankRuntimeUiId)) {
                        if (!uiId.isBlank()) packetBridge.closeUi(online, uiId);
                    }
                }
            }
            if (playerUuid == null) {
                sharedEditLocks.clear();
                recategorizedRules.clear();
                warehouseLocks.clear();
            }
        };
        if (Bukkit.isPrimaryThread()) invalidate.run();
        else Bukkit.getScheduler().runTask(plugin, invalidate);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
            && queueNativePickup(player, event.getItem(), event.getRemaining())) {
            event.setCancelled(true);
        }
    }

    private boolean queueNativePickup(Player player, Item item, int remaining) {
        if (!active || !configuration.pickup().enabled() || pickupIntercepted(player)
            || !eligibleGroundPickup(player, item)) return false;
        UUID entityId = item.getUniqueId();
        NativePickup queued = pendingNativePickup.get(entityId);
        if (queued != null) {
            if (queued.player == player) queued.lastContactPass = nativePickupPass;
            return true;
        }
        if (isGroundDepositReserved(item)) return true;
        var key = new PickupKey(player.getUniqueId(), entityId);
        long now = System.nanoTime();
        if (nativePickupRetry.getOrDefault(key, 0L) > now || pendingNativePickup.size() >= MAX_PENDING_NATIVE_PICKUPS) return false;
        ItemStack eventStack = item.getItemStack();
        if (eventStack == null || eventStack.getType().isAir()) return false;
        var cached = pickupPreferences.get(key.player());
        boolean mythic = itemSourceRegistry.isMythicItem(eventStack);
        if (mythic && (!configuration.pickup().mythicLootEnabled() || cached != null && !cached.autoMythic())
            || !mythic && cached != null && !cached.autoPickup()) return false;
        int amount = eventStack.getAmount();
        if (amount <= 0 || remaining < 0 || (long) amount + remaining > Integer.MAX_VALUE) return false;
        ItemStack snapshot = eventStack.clone();
        snapshot.setAmount(amount + remaining);
        pendingNativePickup.put(entityId, new NativePickup(player, item, snapshot, now + NATIVE_PICKUP_WAIT_NANOS, nativePickupPass));
        if (nativePickupDrain != null) return true;
        try {
            // Bukkit's pickup event temporarily exposes just the part that can fit in the backpack.
            // Keep the queue entry until the full stack is restored and a transfer slot opens.
            nativePickupDrain = Bukkit.getScheduler().runTask(plugin, this::drainNativePickups);
            return true;
        } catch (RuntimeException failure) {
            pendingNativePickup.remove(entityId);
            return false;
        }
    }

    private void drainNativePickups() {
        nativePickupDrain = null;
        if (!active) return;
        long contactPass = nativePickupPass++;
        long now = System.nanoTime();
        var pending = pendingNativePickup.entrySet().iterator();
        int started = 0;
        while (started < NATIVE_PICKUPS_PER_TICK && pending.hasNext()) {
            var head = pending.next();
            NativePickup pickup = head.getValue();
            UUID entityId = head.getKey();
            Player player = pickup.player;
            Item item = pickup.item;
            var key = new PickupKey(player.getUniqueId(), entityId);
            if (pickup.expires <= now) {
                pending.remove();
                nativePickupRetry.put(key, now + NATIVE_PICKUP_RETRY_NANOS);
                continue;
            }
            // Prepared work can wait on MySQL. Leave this original entity untouched until a slot opens.
            if (!groundTransfers.hasCapacity()) break;
            // Waiting does not extend the permission from a previous collision. Only a new
            // native contact can refresh it, after the server's pickup listeners have run.
            if (pickup.lastContactPass != contactPass) continue;
            pending.remove();
            started++;
            if (!eligibleGroundPickup(player, item) || pickupIntercepted(player)
                || player.getWorld() != item.getWorld() || player.getLocation().distanceSquared(item.getLocation()) > 9
                || !pickup.snapshot.equals(item.getItemStack())) continue;
            try {
                if (!depositFromGroundAsync(player, item, pickup.snapshot, () -> true, (previewJson, result) -> {
                    if (!result.review() && player.isOnline()) {
                        nativePickupRetry.put(key, System.nanoTime() + NATIVE_PICKUP_RETRY_NANOS);
                        notifyNativeDeposit(player, pickup.snapshot, previewJson, result);
                    }
                    return !result.review();
                })) nativePickupRetry.put(key, now + NATIVE_PICKUP_RETRY_NANOS);
            } catch (RuntimeException failure) {
                nativePickupRetry.put(key, now + NATIVE_PICKUP_RETRY_NANOS);
                logger.warning("地面自动入库准备失败: " + failure.getMessage());
            }
        }
        if (!pendingNativePickup.isEmpty()) {
            nativePickupDrain = Bukkit.getScheduler().runTaskLater(plugin, this::drainNativePickups, 1L);
        }
    }

    private void notifyNativeDeposit(Player player, ItemStack snapshot, String previewJson,
                                     WarehouseAutoDepositable.GroundDepositResult result) {
        if (result.storedAmount() <= 0) return;
        if (result.notifyPlayer()) sendNativeDepositNotice(player, result.storedAmount());
        PickupNotifiable notifier = pickupNotifiableSupplier.get();
        if (notifier != null) {
            ItemStack display = snapshot.clone();
            int stored = Math.toIntExact(result.storedAmount());
            display.setAmount(stored);
            notifier.notifyPickupPrepared(player, display, stored, previewJson);
        }
    }

    private void sendNativeDepositNotice(Player player, long amount) {
        if (amount >= nativeDepositNotices.length) {
            sendMessage(player, true, message("player.auto-deposited", amount));
            return;
        }
        int count = (int) amount;
        NativeDepositNotice notice = nativeDepositNotices[count];
        if (notice == null) {
            String body = message("player.auto-deposited", amount);
            String legacy = PREFIX + ChatColor.GREEN + body;
            notice = new NativeDepositNotice(body, legacy, WarehousePickupChat.prepare(legacy, body));
            nativeDepositNotices[count] = notice;
        }
        if (!sendUiNotice(player, notice.body())) WarehousePickupChat.send(player, notice.legacy(), notice.component());
    }

    private String automaticWarehouse(Player player) throws Exception {
        long now=System.nanoTime();var cached=automaticScopes.get(player.getUniqueId());
        if(cached!=null && cached.until()>now)return cached.warehouseId();
        var owned=personalWarehouseMap(player.getUniqueId());
        String id=configuration.warehouses().keySet().stream().filter(owned::containsKey).findFirst().orElse("");
        if(id.isEmpty()){
            ensureEntitlements(player);var initialized=personalWarehouseMap(player.getUniqueId());
            id=configuration.warehouses().keySet().stream().filter(initialized::containsKey).findFirst().orElse("");
        }
        automaticScopes.put(player.getUniqueId(),new AutomaticScope(id,now+(id.isEmpty()?500_000_000L:3_000_000_000L)));
        return id;
    }

    @EventHandler public void clearAutomaticPickupState(PlayerQuitEvent event){
        UUID id=event.getPlayer().getUniqueId();automaticScopes.remove(id);
        nativePickupRetry.keySet().removeIf(k->k.player().equals(id));
        pendingNativePickup.values().removeIf(pickup -> pickup.player.getUniqueId().equals(id));
    }

    private boolean eligibleGroundPickup(Player player,org.bukkit.entity.Item item){
        return player.isOnline() && player.getCanPickupItems() && player.getGameMode()!=org.bukkit.GameMode.SPECTATOR
            && item.isValid() && !item.isDead() && item.getPickupDelay()<=0
            && (item.getOwner()==null || item.getOwner().equals(player.getUniqueId()));
    }

    /** Optional early hook: old Bukkit versions retain the existing normal pickup handler. */
    private void bindFullInventoryPickup(){
        try {
            var type=Class.forName("org.bukkit.event.player.PlayerAttemptPickupItemEvent",false,org.bukkit.event.player.PlayerEvent.class.getClassLoader()).asSubclass(org.bukkit.event.Event.class);
            var getItem=type.getMethod("getItem");var getRemaining=type.getMethod("getRemaining");
            var setFlyAtPlayer=type.getMethod("setFlyAtPlayer",boolean.class);
            Bukkit.getPluginManager().registerEvent(type,this,EventPriority.HIGHEST,(listener,event)->{
                try {
                    var player=((org.bukkit.event.player.PlayerEvent)event).getPlayer();
                    var item=(org.bukkit.entity.Item)getItem.invoke(event);
                    NativePickup queued = pendingNativePickup.get(item.getUniqueId());
                    if (queued != null && (queued.player != player || !groundTransfers.hasCapacity()
                        || queued.lastContactPass == nativePickupPass)) {
                        setFlyAtPlayer.invoke(event,false);
                        ((org.bukkit.event.Cancellable)event).setCancelled(true);
                        return;
                    }
                    int remaining=((Number)getRemaining.invoke(event)).intValue();
                    // A non-full backpack will receive the regular (cancellable) entity pickup event.
                    if (remaining >= item.getItemStack().getAmount() && active && configuration.pickup().enabled()
                        && !pickupIntercepted(player) && eligibleGroundPickup(player, item)
                        && nativePickupRetry.getOrDefault(new PickupKey(player.getUniqueId(), item.getUniqueId()), 0L) <= System.nanoTime()
                        && dispatchFullInventoryPickup(player,item)) {
                        setFlyAtPlayer.invoke(event,false);
                        ((org.bukkit.event.Cancellable)event).setCancelled(true);
                    }
                }catch(Exception exception){throw new org.bukkit.event.EventException(exception);}
            },plugin,true);
        }catch(ClassNotFoundException exception){logger.warning("当前 Bukkit 不提供拾取尝试事件，满背包地面自动入库不可用；普通拾取仍保留。");}
        catch(ReflectiveOperationException exception){logger.warning("注册满背包拾取事件失败: "+exception.getMessage());}
    }

    @SuppressWarnings("deprecation")
    private boolean dispatchFullInventoryPickup(Player player,org.bukkit.entity.Item item){
        // Preserve protection/scanner cancellation before the normal Warehouse handler runs.
        var legacy=new org.bukkit.event.player.PlayerPickupItemEvent(player,item,0);
        Bukkit.getPluginManager().callEvent(legacy);
        if(legacy.isCancelled() || !eligibleGroundPickup(player,item))return false;
        var pickup=new EntityPickupItemEvent(player,item,0);
        Bukkit.getPluginManager().callEvent(pickup);
        return pickup.isCancelled();
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onCraftingOverflow(org.bukkit.event.inventory.InventoryCloseEvent event){
        int[] inputs=switch(event.getView().getType()){
            case WORKBENCH -> new int[]{1,2,3,4,5,6,7,8,9};
            case STONECUTTER -> new int[]{0};
            case SMITHING -> new int[]{0,1,2};
            case MERCHANT -> new int[]{0,1};
            case ANVIL, ENCHANTING, GRINDSTONE, CARTOGRAPHY -> new int[]{0,1};
            case LOOM -> new int[]{0,1,2};
            case BEACON -> new int[]{0};
            default -> new int[0];
        };
        if(inputs.length==0 || !(event.getPlayer() instanceof Player player)
            || !active || !configuration.pickup().enabled() || !closingCrafting.add(player.getUniqueId()))return;
        org.bukkit.inventory.Inventory crafting=event.getInventory();
        try {
            ItemStack[] simulated=java.util.Arrays.stream(player.getInventory().getStorageContents()).map(s->s==null?null:s.clone()).toArray(ItemStack[]::new);
            simulated=java.util.Arrays.copyOf(simulated,37);
            simulated[36]=player.getInventory().getItemInOffHand().clone();
            long stored=0L;
            for(int i:inputs) {
                if(player.getOpenInventory()!=event.getView())break;
                ItemStack source=crafting.getItem(i);
                if(source==null || source.getType().isAir() || source.getAmount()<=0)continue;
                int overflow=reserveReturnSpace(simulated,source,player.getInventory().getMaxStackSize());
                if(overflow<=0)continue;
                ItemStack requested=source.clone();requested.setAmount(overflow);
                var result=automaticDeposit(player,requested,false,true);
                int deposited=(int)Math.min(overflow,Math.max(0L,result.storedAmount()));
                if(!result.success() || deposited==0)continue;
                ItemStack remainder=source.clone();remainder.setAmount(source.getAmount()-deposited);
                crafting.setItem(i,remainder.getAmount()==0?null:remainder);
                stored+=deposited;
                // Publish only after removing the committed part from its real source.
                publishDepositEvent(player,requested,deposited);
            }
            if(stored>0 && shouldNotifyAutoDeposit(player))sendMessage(player,true,message("player.auto-deposited",stored));
        }catch(RuntimeException exception){logger.warning("容器退回溢出处理失败，剩余材料交由原版退回: "+exception.getMessage());}
        finally{closingCrafting.remove(player.getUniqueId());}
    }

    private static int reserveReturnSpace(ItemStack[] storage,ItemStack item,int inventoryLimit){
        int remaining=item.getAmount(),limit=Math.min(inventoryLimit,item.getMaxStackSize());
        for(ItemStack existing:storage)if(existing!=null && existing.isSimilar(item)){
            int fit=Math.min(remaining,Math.max(0,limit-existing.getAmount()));existing.setAmount(existing.getAmount()+fit);remaining-=fit;
            if(remaining==0)return 0;
        }
        // Native returns may merge into an existing offhand stack, but do not fill an empty offhand.
        for(int i=0;i<Math.min(36,storage.length);i++)if(storage[i]==null || storage[i].getType().isAir()){
            int fit=Math.min(remaining,limit);storage[i]=item.clone();storage[i].setAmount(fit);remaining-=fit;
            if(remaining==0)return 0;
        }
        return remaining;
    }

    /**
     * 导出并注册三套 AXUI 文件（storage / manage / bank），同时为每套 UI 注册关闭回调。
     */
    private void bindUis() throws Exception {
        storageRuntimeUiId = bindUi(
            configuration.ui().uiId(),
            STORAGE_UI_RESOURCE_PATH,
            STORAGE_UI_FILE_PATH,
            "storage"
        );
        manageRuntimeUiId = bindUi(
            configuration.ui().manageUiId(),
            MANAGE_UI_RESOURCE_PATH,
            MANAGE_UI_FILE_PATH,
            "manage"
        );
        bankRuntimeUiId = bindUi(
            configuration.ui().bankUiId(),
            BANK_UI_RESOURCE_PATH,
            BANK_UI_FILE_PATH,
            "bank"
        );
        if (packetBridge != null) {
            packetBridge.registerUiCloseCallback(
                storageRuntimeUiId,
                player -> handleUiClosed(player, storageRuntimeUiId)
            );
            packetBridge.registerUiCloseCallback(
                manageRuntimeUiId,
                player -> handleUiClosed(player, manageRuntimeUiId)
            );
            packetBridge.registerUiCloseCallback(
                bankRuntimeUiId,
                player -> handleUiClosed(player, bankRuntimeUiId)
            );
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
     * 打开仓库存取界面；数据由 UI open 触发器请求。
     */
    private void openStorage(Player player) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        if (state(player).sharedEditMode() && !acquireCurrentSharedLock(player, state(player))) {
            state(player).setSharedEditMode(false);
        }
        issueActionToken(player, storageRuntimeUiId);
        // 切换管理或银行页时，客户端可能没有回报存取页关闭。显式打开应重新发包。
        if (!packetBridge.openUi(player, storageRuntimeUiId)) {
            invalidateActionToken(player, storageRuntimeUiId);
            throw new IllegalStateException("仓库存取界面打开失败");
        }
        sendStorage(player, "update");
    }

    /**
     * 打开共享管理界面；数据由 UI open 触发器请求。
     */
    private void openManage(Player player) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        issueActionToken(player, manageRuntimeUiId);
        if (!packetBridge.openUi(player, manageRuntimeUiId)) {
            invalidateActionToken(player, manageRuntimeUiId);
        }
        sendManage(player, "update");
    }

    /**
     * 打开银行界面；数据由 UI open 触发器请求。
     */
    private void openBank(Player player) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        issueActionToken(player, bankRuntimeUiId);
        if (!packetBridge.openUi(player, bankRuntimeUiId)) {
            invalidateActionToken(player, bankRuntimeUiId);
        }
        // 打开后立即推送数据包，确保 UI 端 var.actionToken 与服务端同步
        sendBank(player, "update");
    }

    /**
     * 刷新已打开的仓库 UI 数据包（storage / manage / bank），跳过未打开的界面。
     */
    void refreshBoth(Player player) throws Exception {
        ViewState current = state(player);
        if (current.previewMode()) {
            sendStorage(player, "update");
            return;
        }
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, current);
        sendStorage(player, "update");
        sendManage(player, "update");
        sendBank(player, "update");
    }

    private void sendStorage(Player player, String handler) throws Exception {
        if (packetBridge == null || storageRuntimeUiId.isBlank() || !packetBridge.isUiOpen(player, storageRuntimeUiId)) return;
        ViewState current = state(player);
        WarehousePreview.Warehouses warehouses = null;
        if (current.previewMode()) {
            warehouses = authorizedPreview(player, current);
            if (warehouses == null) return;
        }
        sendStorage(player, handler, current, warehouses);
    }

    private void sendStorage(Player player, String handler, ViewState state, WarehousePreview.Warehouses warehouses) throws Exception {
        if (viewStates.get(player.getUniqueId()) != state) return;
        sendWithPasswordStatus(player, storageRuntimeUiId, state,
            status -> sendStoragePacket(player, handler, state, warehouses, status));
    }

    @FunctionalInterface
    private interface PasswordPacket {
        void send(SecondaryPasswordAccess.Status status) throws Exception;
    }

    private void sendWithPasswordStatus(Player player, String uiId, ViewState state, PasswordPacket send) throws Exception {
        SecondaryPasswordAccess access = secondaryPasswordSupplier == null ? null : secondaryPasswordSupplier.get();
        if (state.previewMode() || access == null) {
            send.send(SecondaryPasswordAccess.Status.LOCKED);
            return;
        }
        UUID id = player.getUniqueId();
        var request = passwordViews.computeIfAbsent(id, ignored -> access.statusAsync(player));
        request.whenComplete((status, failure) -> {
            if (!active) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                passwordViews.remove(id, request);
                if (!active || Bukkit.getPlayer(id) != player || !player.isOnline() || viewStates.get(id) != state
                    || !packetBridge.isUiOpen(player, uiId)) return;
                try {
                    send.send(failure == null && status != null ? status : SecondaryPasswordAccess.Status.LOCKED);
                } catch (Exception exception) {
                    logger.warning("刷新仓库状态失败: " + exception.getMessage());
                }
            });
        });
    }

    private void sendStoragePacket(Player player, String handler, ViewState state, WarehousePreview.Warehouses warehouses,
                                   SecondaryPasswordAccess.Status passwordStatus) throws Exception {
        Map<String, Object> packet = packetFactory.buildStoragePacket(player, state, warehouses, passwordStatus);
        if (viewStates.get(player.getUniqueId()) != state) return;
        if (configuration.debug()) {
            debug("OUT storage player=" + player.getName() + " uiId=" + storageRuntimeUiId + " handler=" + handler + " " + WarehouseTexts.summarizeStoragePacket(packet));
        }
        boolean success = packetBridge.sendPacket(player, storageRuntimeUiId, handler, packet);
        debug("OUT storage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
    }

    private WarehousePreview.Warehouses authorizedPreview(Player player, ViewState state) throws java.sql.SQLException {
        try {
            WarehousePreview.Warehouses warehouses = state.preview().read(player, repository);
            if (warehouses.contains(state.ownerType(), state.warehouseId())) return warehouses;
        } catch (java.sql.SQLException exception) {
            closePreview(player, state);
            throw exception;
        }
        closePreview(player, state);
        return null;
    }

    private void closePreview(Player player, ViewState state) {
        if (!viewStates.remove(player.getUniqueId(), state)) return;
        invalidateActionToken(player, storageRuntimeUiId);
        if (packetBridge != null && !storageRuntimeUiId.isBlank()) {
            packetBridge.closeUi(player, storageRuntimeUiId);
        }
        sendMessage(player, false, message("player.preview-unavailable"));
    }

    private void revokeShowcase(UUID owner, String ownerType, String warehouseId) {
        var revoke = new WarehouseCrossServerPayloadCodec.ShowcaseRevocation(owner, ownerType, warehouseId);
        closePublicPreviews(revoke);
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerLockService != null) crossServerLockService.publishShowcaseRevocation(revoke);
    }

    private void closePublicPreviews(WarehouseCrossServerPayloadCodec.ShowcaseRevocation revoke) {
        viewStates.forEach((id, state) -> {
            WarehousePreview preview = state.preview();
            if (preview == null || preview.administrator() || !preview.target().equals(revoke.ownerUuid())
                || !state.ownerType().equals(revoke.ownerType()) || !state.warehouseId().equals(revoke.warehouseId())) return;
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.isOnline()) closePreview(viewer, state);
            else viewStates.remove(id, state);
        });
    }

    private void sendManage(Player player, String handler) throws Exception {
        if (packetBridge != null && !manageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, manageRuntimeUiId)) {
            ViewState view = state(player);
            sendWithPasswordStatus(player, manageRuntimeUiId, view, status -> {
                Map<String, Object> packet = packetFactory.buildManagePacket(player, view, status);
                debug("OUT manage player=" + player.getName() + " uiId=" + manageRuntimeUiId + " handler=" + handler + " " + WarehouseTexts.summarizeManagePacket(packet));
                boolean success = packetBridge.sendPacket(player, manageRuntimeUiId, handler, packet);
                debug("OUT manage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
            });
        }
    }

    private void sendBank(Player player, String handler) throws Exception {
        if (packetBridge != null && !bankRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, bankRuntimeUiId)) {
            ViewState view = state(player);
            sendWithPasswordStatus(player, bankRuntimeUiId, view, status -> {
                Map<String, Object> packet = packetFactory.buildBankPacket(player, view, status);
                debug("OUT bank player=" + player.getName() + " uiId=" + bankRuntimeUiId + " handler=" + handler + " balances=" + WarehouseTexts.mapValue(packet.get("balances")).size());
                boolean success = packetBridge.sendPacket(player, bankRuntimeUiId, handler, packet);
                debug("OUT bank-result player=" + player.getName() + " handler=" + handler + " success=" + success);
            });
        }
    }

    private boolean isUiOpenForAction(Player player, String action) {
        if ("shared_activity".equals(action)) {
            return packetBridge != null && !manageRuntimeUiId.isBlank() && packetBridge.isUiOpen(player, manageRuntimeUiId);
        }
        if ("page".equals(action)
            || "withdraw".equals(action)
            || "withdraw_quick".equals(action)
            || "withdraw_all".equals(action)
            || "deposit_slot".equals(action)
            || "deposit_all_backpack".equals(action)
            || "toggle_lock".equals(action)
            || "shared_mode".equals(action)) {
            return packetBridge != null
                && packetBridge.isUiOpen(player, storageRuntimeUiId);
        }
        if ("bank_deposit".equals(action)
            || "bank_withdraw".equals(action)
            || "fixed_create".equals(action)
            || "fixed_claim".equals(action)
            || "fixed_delete".equals(action)) {
            return packetBridge != null
                && packetBridge.isUiOpen(player, bankRuntimeUiId);
        }
        if ("warehouse_upgrade".equals(action) || "buy_slots".equals(action)) {
            return packetBridge != null
                && (packetBridge.isUiOpen(player, storageRuntimeUiId)
                    || packetBridge.isUiOpen(player, manageRuntimeUiId));
        }
        if ("shared_create".equals(action)
            || "shared_rename".equals(action)
            || "shared_showcase_toggle".equals(action)
            || "personal_rename".equals(action)
            || "personal_showcase_toggle".equals(action)
            || "shared_delete".equals(action)
            || "shared_invite".equals(action)
            || "shared_remove".equals(action)
            || "shared_transfer".equals(action)
            || "shared_transfer_confirm".equals(action)
            || "shared_transfer_reject".equals(action)
            || "toggle_auto_pickup".equals(action)
            || "toggle_auto_mythic".equals(action)
            || "toggle_auto_notify".equals(action)) {
            return packetBridge != null
                && packetBridge.isUiOpen(player, manageRuntimeUiId);
        }
        if ("password_set".equals(action)
            || "password_unlock".equals(action)
            || "password_lock".equals(action)) {
            return packetBridge != null
                && (packetBridge.isUiOpen(player, storageRuntimeUiId)
                    || packetBridge.isUiOpen(player, manageRuntimeUiId)
                    || packetBridge.isUiOpen(player, bankRuntimeUiId));
        }
        return true;
    }

    private boolean requiresActionToken(String action) {
        return "withdraw".equals(action) || "withdraw_quick".equals(action) || "withdraw_all".equals(action)
            || "deposit_slot".equals(action) || "deposit_all_backpack".equals(action)
            || "toggle_lock".equals(action) || "shared_mode".equals(action)
            || "bank_deposit".equals(action)
            || "bank_withdraw".equals(action)
            || "fixed_create".equals(action)
            || "fixed_claim".equals(action)
            || "fixed_delete".equals(action)
            || "warehouse_upgrade".equals(action)
            || "buy_slots".equals(action)
            || "shared_create".equals(action)
            || "shared_delete".equals(action)
            || "shared_rename".equals(action)
            || "personal_rename".equals(action)
            || "shared_transfer".equals(action)
            || "shared_transfer_confirm".equals(action)
            || "shared_transfer_reject".equals(action)
            || "shared_invite".equals(action)
            || "shared_remove".equals(action)
            || "shared_showcase_toggle".equals(action)
            || "personal_showcase_toggle".equals(action)
            || "toggle_auto_pickup".equals(action)
            || "toggle_auto_mythic".equals(action)
            || "toggle_auto_notify".equals(action)
            || "password_set".equals(action)
            || "password_lock".equals(action);
    }

    private TokenLease beginActionToken(Player player, String action, List<String> data) {
        if (!requiresActionToken(action)) {
            return null;
        }
        String supplied = data == null || data.isEmpty()
            ? ""
            : WarehouseTexts.safe(data.get(data.size() - 1));
        if (supplied.isBlank()) {
            return null;
        }
        List<String> candidateUiIds = tokenUiCandidates(action);
        for (String uiId : candidateUiIds) {
            if (!packetBridge.isUiOpen(player, uiId)) {
                continue;
            }
            ConcurrentMap<UUID, String> tokens = tokenMap(uiId);
            UUID uuid = player.getUniqueId();
            Object lock = actionTokenLocks.computeIfAbsent(uuid, ignored -> new Object());
            synchronized (lock) {
                String expected = tokens.get(uuid);
                if (expected == null || !expected.equals(supplied) || !actionTokensInFlight.add(uuid)) {
                    continue;
                }
                TokenLease lease = new TokenLease(uuid, uiId);
                activeActionToken.set(lease);
                return lease;
            }
        }
        return null;
    }

    private void finishActionToken(Player player, TokenLease lease) {
        if (lease == null) {
            return;
        }
        UUID uuid = lease.playerUuid();
        Object lock = actionTokenLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            actionTokensInFlight.remove(uuid);
            committedActionTokens.get().remove(uuid);
            activeActionToken.remove();
        }
    }

    private void markActionCommitted(Player player) {
        if (player != null) {
            UUID uuid = player.getUniqueId();
            committedActionTokens.get().add(uuid);
            TokenLease lease = activeActionToken.get();
            if (lease != null && lease.playerUuid().equals(uuid)) {
                tokenMap(lease.uiId()).put(uuid, UUID.randomUUID().toString());
            }
        }
    }

    void issueActionToken(Player player, String uiId) {
        if (player != null && uiId != null && !uiId.isBlank()) {
            tokenMap(uiId).put(player.getUniqueId(), UUID.randomUUID().toString());
        }
    }

    private void invalidateActionToken(Player player, String uiId) {
        if (player != null && uiId != null && !uiId.isBlank()) {
            tokenMap(uiId).remove(player.getUniqueId());
        }
    }

    private ConcurrentMap<UUID, String> tokenMap(String uiId) {
        if (storageRuntimeUiId.equals(uiId)) {
            return storageActionTokens;
        }
        if (manageRuntimeUiId.equals(uiId)) {
            return manageActionTokens;
        }
        return bankActionTokens;
    }

    private List<String> tokenUiCandidates(String action) {
        if (List.of("withdraw", "withdraw_quick", "withdraw_all", "deposit_slot", "deposit_all_backpack", "toggle_lock", "shared_mode").contains(action))
            return List.of(storageRuntimeUiId);
        if ("bank_deposit".equals(action)
            || "bank_withdraw".equals(action)
            || "fixed_create".equals(action)
            || "fixed_claim".equals(action)
            || "fixed_delete".equals(action)) {
            return List.of(bankRuntimeUiId);
        }
        if ("warehouse_upgrade".equals(action) || "buy_slots".equals(action)) {
            return List.of(storageRuntimeUiId, manageRuntimeUiId);
        }
        if ("password_set".equals(action)
            || "password_unlock".equals(action)
            || "password_lock".equals(action)) {
            return List.of(storageRuntimeUiId, manageRuntimeUiId, bankRuntimeUiId);
        }
        return List.of(manageRuntimeUiId);
    }

    private void handleUiClosed(Player player) {
        invalidateActionToken(player, storageRuntimeUiId);
        invalidateActionToken(player, manageRuntimeUiId);
        invalidateActionToken(player, bankRuntimeUiId);
        handleUiClosed(player, null);
    }

    private void handleUiClosed(Player player, String uiId) {
        if (player == null) {
            return;
        }
        // 桥接会延迟派发关闭回调。重新打开后应保留新视图的令牌和锁。
        if (uiId != null && packetBridge != null && packetBridge.isUiOpen(player, uiId)) {
            return;
        }
        invalidateActionToken(player, uiId);
        if (uiId == null || manageRuntimeUiId.equals(uiId)) activityRequests.remove(player.getUniqueId());
        releaseCurrentSharedLock(player);
        // 预览模式下关闭 UI 即退出预览状态，避免玩家"卡"在预览中
        ViewState state = viewStates.get(player.getUniqueId());
        if (state != null && state.previewMode() && (uiId == null || storageRuntimeUiId.equals(uiId))) {
            viewStates.remove(player.getUniqueId(), state);
        }
    }

    private void setSort(Player player, String sortId) throws Exception {
        ViewState state = state(player);
        if (!updateSort(state, sortId)) return;
        packetFactory.clearFilteredSelection(state);
        refreshBoth(player);
    }

    private boolean updateSort(ViewState state, String sortId) {
        List<String> sortIds = configuration.sortProfileIds();
        if (sortIds.isEmpty()) {
            return false;
        }
        String normalized = WarehouseTexts.normalizeId(sortId);
        String selected;
        if (normalized.isBlank()) {
            int currentIndex = sortIds.indexOf(state.sortId());
            selected = sortIds.get((currentIndex < 0 ? 0 : currentIndex + 1) % sortIds.size());
        } else if (sortIds.contains(normalized)) {
            selected = normalized;
        } else {
            selected = sortIds.contains(WarehouseTexts.normalizeId(configuration.search().defaultSortId()))
                ? WarehouseTexts.normalizeId(configuration.search().defaultSortId())
                : sortIds.get(0);
        }
        state.setSortId(selected);
        state.setPage(1);
        return true;
    }

    void setCategory(Player player, String categoryId) throws Exception {
        ViewState state = state(player);
        updateCategory(state, categoryId);
        packetFactory.clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void updateCategory(ViewState state, String categoryId) {
        String normalized = WarehouseTexts.normalizeId(categoryId);
        state.setCategoryId(normalized.isBlank() ? "all" : normalized);
        state.setPage(1);
    }

    private void selectCurrency(Player player, String currencyId) throws Exception {
        ViewState state = state(player);
        String normalized = WarehouseTexts.normalizeId(currencyId);
        if (normalized.isBlank() || !configuration.bankCurrencies().contains(normalized)) {
            refreshBoth(player);
            return;
        }
        state.setSelectedCurrency(normalized);
        refreshBoth(player);
    }

    private void setSearch(Player player, String search) throws Exception {
        ViewState state = state(player);
        updateSearch(state, search);
        packetFactory.clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void updateSearch(ViewState state, String search) {
        state.setSearch(WarehouseTexts.crop(WarehouseTexts.safe(search), 32));
        state.setPage(1);
    }

    private void setPage(Player player, String request) throws Exception {
        ViewState state = state(player);
        updatePage(state, request);
        packetFactory.clearFilteredSelection(state);
        sendStorage(player, "update");
    }

    private void updatePage(ViewState state, String request) {
        int page = switch (request) {
            case "prev" -> state.page() - 1;
            case "next" -> state.page() < Integer.MAX_VALUE ? state.page() + 1 : state.page();
            default -> WarehouseTexts.parseInt(request, state.page());
        };
        state.setPage(Math.max(1, page));
    }

    private void selectSlot(Player player, int displaySlot) throws Exception {
        ViewState state = state(player);
        state.setSelectedSlot(packetFactory.actualSlotFromDisplay(state, displaySlot));
        sendStorage(player, "update");
    }

    private void selectPersonalWarehouse(Player player, String warehouseId) throws Exception {
        releaseCurrentSharedLock(player);
        ViewState state = state(player);
        state.setOwnerType(OWNER_PERSONAL);
        state.setOwnerId(player.getUniqueId().toString());
        state.setWarehouseId(WarehouseTexts.normalizeId(warehouseId).isBlank() ? packetFactory.firstPersonalWarehouseId() : WarehouseTexts.normalizeId(warehouseId));
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        ensureCurrentWarehouse(player, state);
        refreshBoth(player);
    }

    private void selectSharedWarehouse(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> selected = repository.loadSharedWarehouse(player.getUniqueId(), sharedId);
        if (selected.isEmpty()) {
            sendMessage(player, false, message("player.not-shared-member"));
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

    private void setSharedMode(Player player, String mode) throws Exception {
        ViewState state = state(player);
        if (!OWNER_SHARED.equals(state.ownerType())) {
            sendMessage(player, false, message("player.not-shared-warehouse"));
            refreshBoth(player);
            return;
        }
        if ("edit".equalsIgnoreCase(WarehouseTexts.normalizeId(mode))) {
            if (!sharedCanEdit(player, state)) {
                state.setSharedEditMode(false);
                sendMessage(player, false, message("player.shared-no-permission"));
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
        sendMessage(player, true, message("player.shared-read-only-mode"));
        refreshBoth(player);
    }

    private void toggleAutoPickup(Player player, String kind) throws Exception {
        ViewState state = state(player);
        // 服务器开关关闭时对应开关已在管理界面隐藏，此处兜底拒绝伪造包；仍需刷新 UI 复位客户端防抖标志
        var pickupConfig = configuration.pickup();
        boolean serverAllowed = switch (kind) {
            case "mythic" -> pickupConfig.enabled() && pickupConfig.mythicLootEnabled();
            default -> pickupConfig.enabled();
        };
        if (!serverAllowed) {
            refreshBoth(player);
            return;
        }
        boolean oldPickup = state.autoPickup(), oldMythic = state.autoPickupMythic(), oldNotify = state.autoPickupNotify();
        try {
            switch (kind) {
                case "pickup" -> state.setAutoPickup(!state.autoPickup());
                case "mythic" -> state.setAutoPickupMythic(!state.autoPickupMythic());
                case "notify" -> state.setAutoPickupNotify(!state.autoPickupNotify());
                default -> { return; }
            }
            // 先持久化，成功后再更新缓存与提示。
            repository.savePlayerSettings(player.getUniqueId(), state.autoPickup(), state.autoPickupMythic(), state.autoPickupNotify(), System.currentTimeMillis());
            pickupPreferences.put(player.getUniqueId(), new WarehouseRepository.PlayerSettingsRecord(player.getUniqueId(),
                state.autoPickup(), state.autoPickupMythic(), state.autoPickupNotify(), System.currentTimeMillis()));
        } catch (Exception exception) {
            state.setAutoPickup(oldPickup); state.setAutoPickupMythic(oldMythic); state.setAutoPickupNotify(oldNotify);
            throw exception;
        }
        boolean enabled = switch (kind) { case "pickup" -> state.autoPickup(); case "mythic" -> state.autoPickupMythic(); default -> state.autoPickupNotify(); };
        String key = switch (kind) { case "pickup" -> "player.auto-pickup"; case "mythic" -> "player.auto-pickup-mythic"; default -> "player.deposit-notify"; };
        sendMessage(player, true, message(key, enabled ? message("player.enabled") : message("player.disabled")));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 物品是否带有仓库锁定标记（PDC 写在物品自身 NBT 上，随物品移动/掉落/交易保留）。
     */
    boolean isItemLocked(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(warehouseLockKey, PersistentDataType.BYTE);
    }

    /**
     * 切换背包指定槽位物品的仓库锁定标记。
     * 标记写在物品 PDC 上（随物品本体），与槽位无关——物品换格、丢弃再捡、交易给他人都保留。
     * 不涉及仓库数据写入，因此不要求二级密码解锁。
     */
    private void toggleBackpackLock(Player player, String rawSlotValue) throws Exception {
        int rawSlot = parseRawBackpackSlot(rawSlotValue);
        if (rawSlot < 9 || rawSlot > 44) {
            sendMessage(player, false, message("player.invalid-slot", WarehouseTexts.safe(rawSlotValue)));
            refreshBoth(player);
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null || stack.getType().isAir()) {
            sendMessage(player, false, message("player.slot-empty"));
            refreshBoth(player);
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            sendMessage(player, false, message("player.item-lock-unsupported"));
            refreshBoth(player);
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean locked;
        if (pdc.has(warehouseLockKey, PersistentDataType.BYTE)) {
            pdc.remove(warehouseLockKey);
            locked = false;
        } else {
            pdc.set(warehouseLockKey, PersistentDataType.BYTE, (byte) 1);
            locked = true;
        }
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
        player.updateInventory();
        debug("TOGGLE-LOCK player=" + player.getName() + " rawSlot=" + rawSlot + " bukkitSlot=" + slot
            + " locked=" + locked + " stack=" + WarehouseTexts.describeStack(stack));
        sendMessage(player, true, message(locked ? "player.item-lock-on" : "player.item-lock-off"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 将玩家背包指定槽位的物品存入当前仓库。
     * 会检查只读权限、黑名单和容量上限，成功时扣除背包物品并刷新 UI。
     * 支持指定存入数量（requestedAmount），若大于实际堆叠数量则按实际数量存入。
     */
    private void depositSlot(Player player, String rawSlotValue, long requestedAmount) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT start player=" + player.getName() + " rawArg=" + WarehouseTexts.safe(rawSlotValue)
            + " amount=" + requestedAmount + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!isSecondaryUnlocked(player)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=secondary-locked");
            sendMessage(player, false, message("player.secondary-required-deposit"));
            refreshBoth(player);
            return;
        }
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, message("player.read-only-deposit"));
            refreshBoth(player);
            return;
        }
        int rawSlot = parseRawBackpackSlot(rawSlotValue);
        if (rawSlot < 9 || rawSlot > 44) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=invalid-raw rawArg=" + WarehouseTexts.safe(rawSlotValue) + " parsed=" + rawSlot);
            sendMessage(player, false, message("player.invalid-slot", WarehouseTexts.safe(rawSlotValue)));
            refreshBoth(player);
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        ItemStack stack = player.getInventory().getItem(slot);
        debug("DEPOSIT slot player=" + player.getName() + " rawSlot=" + rawSlot + " bukkitSlot=" + slot + " stack=" + WarehouseTexts.describeStack(stack));
        if (stack == null || stack.getType().isAir()) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=empty rawSlot=" + rawSlot + " bukkitSlot=" + slot);
            sendMessage(player, false, message("player.slot-empty"));
            refreshBoth(player);
            return;
        }
        if (isBlacklisted(stack)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=blacklist stack=" + WarehouseTexts.describeStack(stack));
            sendMessage(player, false, message("player.item-blocked"));
            refreshBoth(player);
            return;
        }
        int originalAmount = stack.getAmount();
        int depositAmount = (requestedAmount <= 0) ? originalAmount : (int) Math.min(originalAmount, requestedAmount);
        if (isItemLocked(stack)) {
            sendMessage(player, false, message("player.item-locked"));
            refreshBoth(player);
            return;
        }
        ItemStack prototype = stack.clone();
        prototype.setAmount(1);
        byte[] bytes = ItemSerializer.serialize(prototype);
        String rejected = validateSerializedItem(player.getName(), prototype, bytes);
        if (rejected != null) {
            sendMessage(player, false, rejectMessage(rejected));
            refreshBoth(player);
            return;
        }
        var deposit = new BackpackDeposit(slot, stack, prototype, bytes, WarehouseTexts.sha256Hex(bytes), depositAmount);
        var result = commitBackpackDeposit(player, state, List.of(deposit));
        int stored = result.storedBySlot().getOrDefault(slot, 0);
        if (stored == 0) {
            sendMessage(player, false, result.detail().isEmpty() ? message("player.warehouse-full") : result.detail());
            refreshBoth(player);
            return;
        }
        debug("DEPOSIT inventory-updated player=" + player.getName() + " bukkitSlot=" + slot + " newStack=" + WarehouseTexts.describeStack(player.getInventory().getItem(slot)));
        markActionCommitted(player);
        sendMessage(player, true, message("player.deposit-success", stored));
        refreshBoth(player);
    }

    /**
     * 一键存入背包全部物品（主库存 9~44 槽）。
     * 保留背包槽位顺序并按实际入库数量结算整个批次。
     */
    private void depositAllBackpack(Player player) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT-ALL start player=" + player.getName()
            + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!isSecondaryUnlocked(player)) {
            debug("DEPOSIT-ALL reject player=" + player.getName() + " reason=secondary-locked");
            sendMessage(player, false, message("player.secondary-required-deposit"));
            refreshBoth(player);
            return;
        }
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT-ALL reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, message("player.read-only-deposit"));
            refreshBoth(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<BackpackDeposit> deposits = new ArrayList<>();
        int skippedBlacklisted = 0;
        int skippedLocked = 0;
        int failedSlots = 0;
        boolean foundItem = false;
        for (int rawSlot = 9; rawSlot <= 44; rawSlot++) {
            int slot = toBukkitBackpackSlot(rawSlot);
            ItemStack stack = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            foundItem = true;
            if (isItemLocked(stack)) {
                skippedLocked++;
                continue;
            }
            if (isBlacklisted(stack)) {
                skippedBlacklisted++;
                continue;
            }
            if (stack.getAmount() <= 0) {
                failedSlots++;
                continue;
            }
            ItemStack prototype = stack.clone();
            prototype.setAmount(1);
            byte[] bytes = ItemSerializer.serialize(prototype);
            if (validateSerializedItem(player.getName(), prototype, bytes) != null) {
                failedSlots++;
                continue;
            }
            deposits.add(new BackpackDeposit(slot, stack, prototype, bytes, WarehouseTexts.sha256Hex(bytes), stack.getAmount()));
        }
        var result = commitBackpackDeposit(player, state, deposits);
        if (!result.detail().isEmpty()) {
            sendMessage(player, false, result.detail());
            refreshBoth(player);
            return;
        }
        Map<Integer, Integer> storedBySlot = result.storedBySlot();
        failedSlots += deposits.size() - storedBySlot.size();
        long totalStored = storedBySlot.values().stream().mapToLong(Integer::longValue).sum();
        if (totalStored > 0L) {
            markActionCommitted(player);
            List<String> skippedParts = new ArrayList<>(3);
            if (skippedBlacklisted > 0) {
                skippedParts.add(skippedBlacklisted + " 格禁存物品");
            }
            if (skippedLocked > 0) {
                skippedParts.add(skippedLocked + " 格锁定物品");
            }
            if (failedSlots > 0) {
                skippedParts.add(failedSlots + " 格未能存入");
            }
            String suffix = skippedParts.isEmpty() ? "。" : "，跳过 " + String.join("，", skippedParts) + "。";
            sendMessage(player, true, message("player.deposit-from-inventory", totalStored, storedBySlot.size(), suffix));
        } else if (!foundItem) {
            sendMessage(player, false, message("player.no-deposit-items"));
        } else if (skippedLocked > 0 && skippedBlacklisted == 0 && failedSlots == 0) {
            sendMessage(player, false, message("player.all-items-locked"));
        } else if (skippedBlacklisted > 0 && failedSlots == 0 && skippedLocked == 0) {
            sendMessage(player, false, message("player.all-items-blocked"));
        } else {
            sendMessage(player, false, message("player.deposit-none"));
        }
        refreshBoth(player);
    }

    private record BackpackDeposit(int slot, ItemStack source, ItemStack prototype, byte[] bytes, String hash, int amount) { }

    private record BackpackDepositResult(Map<Integer, Integer> storedBySlot, String detail) { }

    private BackpackDepositResult commitBackpackDeposit(Player player, ViewState state, List<BackpackDeposit> deposits) throws Exception {
        if (deposits.isEmpty()) {
            return new BackpackDepositResult(Map.of(), "");
        }
        String ownerType = state.ownerType();
        String ownerId = state.ownerId();
        String warehouseId = state.warehouseId();
        Object warehouseLock = warehouseLocks.computeIfAbsent(
            ownerType + ':' + ownerId + ':' + warehouseId, ignored -> new Object());
        synchronized (warehouseLock) {
            Map<String, List<SlotItemRecord>> slotsByHash = new HashMap<>();
            Set<Integer> occupied = new HashSet<>();
            for (SlotItemRecord item : repository.loadSlotSummaries(ownerType, ownerId, warehouseId)) {
                slotsByHash.computeIfAbsent(item.itemHash(), ignored -> new ArrayList<>()).add(item);
                occupied.add(item.slot());
            }
            Map<Integer, WarehouseRepository.SlotChange> changes = new LinkedHashMap<>();
            Map<Integer, Integer> storedBySlot = new HashMap<>();
            long now = System.currentTimeMillis();
            long capacity = -1;
            int nextEmptySlot = 0;
            for (BackpackDeposit deposit : deposits) {
                int amount = deposit.amount();
                int remaining = amount;
                List<SlotItemRecord> matching = slotsByHash.computeIfAbsent(deposit.hash(), ignored -> new ArrayList<>());
                for (SlotItemRecord item : matching) {
                    if (remaining == 0) {
                        break;
                    }
                    WarehouseRepository.SlotChange planned = changes.get(item.slot());
                    SlotItemRecord current = planned == null ? item : planned.after();
                    int moved = (int) Math.min(remaining, Math.max(0L, MAX_AGGREGATED_AMOUNT - current.amount()));
                    if (moved > 0) {
                        changes.put(item.slot(), new WarehouseRepository.SlotChange(
                            planned == null ? item : planned.before(), withAmount(current, current.amount() + moved, now)));
                        remaining -= moved;
                    }
                }
                if (remaining > 0) {
                    if (capacity < 0) {
                        capacity = Math.max(0L, Math.min(Integer.MAX_VALUE,
                            packetFactory.currentWarehouseCapacity(player, ownerType, ownerId, warehouseId)));
                    }
                    while (nextEmptySlot < capacity && occupied.contains(nextEmptySlot)) {
                        nextEmptySlot++;
                    }
                    if (nextEmptySlot < capacity) {
                        ItemStack prototype = deposit.prototype();
                        String displayName = resolveDisplayName(prototype);
                        SearchTokens tokens = WarehouseTexts.toSearchTokens(displayName);
                        SlotItemRecord created = new SlotItemRecord(ownerType, ownerId, warehouseId, nextEmptySlot,
                            deposit.hash(), resolveCategory(prototype), displayName, prototype.getType().name(),
                            tokens.searchText(), tokens.pinyin(), tokens.initials(), Base64.getEncoder().encodeToString(deposit.bytes()),
                            itemJson(prototype), remaining, now, now, WarehouseTexts.loreText(prototype));
                        matching.add(created);
                        changes.put(nextEmptySlot++, new WarehouseRepository.SlotChange(null, created));
                        remaining = 0;
                    }
                }
                if (remaining < amount) {
                    storedBySlot.put(deposit.slot(), amount - remaining);
                }
            }
            if (changes.isEmpty()) return new BackpackDepositResult(Map.of(), "");
            ItemStack[] after = InventoryItems.copy(player.getInventory().getStorageContents());
            var deposited = new ArrayList<WarehouseMaterials.InventoryPart>();
            for (BackpackDeposit deposit : deposits) {
                int stored = storedBySlot.getOrDefault(deposit.slot(), 0);
                if (stored == 0) continue;
                if (!java.util.Objects.equals(after[deposit.slot()], deposit.source())) {
                    return new BackpackDepositResult(Map.of(), "背包内容已变化，请重试");
                }
                int remaining = after[deposit.slot()].getAmount() - stored;
                if (remaining == 0) after[deposit.slot()] = null;
                else after[deposit.slot()].setAmount(remaining);
                deposited.add(new WarehouseMaterials.InventoryPart(deposit.slot(), stored,
                    Base64.getEncoder().encodeToString(deposit.bytes())));
            }
            String detail = transferInventory(player, state, new ArrayList<>(changes.values()), after, deposited, false);
            return new BackpackDepositResult(detail.isEmpty() ? storedBySlot : Map.of(), detail);
        }
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
    DepositResult depositStack(Player player, String ownerType, String ownerId, String warehouseId, ItemStack stack) throws Exception {
        return depositStack(player, ownerType, ownerId, warehouseId, stack, null);
    }

    DepositResult depositCursor(Player player, String warehouseId, InventoryView view, ItemStack stack) throws Exception {
        InventoryType type = view.getType();
        boolean ownInventory = type == InventoryType.PLAYER && view.getTopInventory() instanceof PlayerInventory top
            && player.equals(top.getHolder());
        if (!ownInventory && type != InventoryType.CRAFTING && type != InventoryType.WORKBENCH) {
            return DepositResult.failure("请先把光标物品放回背包，在随身背包或工作台存入仓库。");
        }
        return depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), warehouseId, stack, view);
    }

    private DepositResult depositStack(Player player, String ownerType, String ownerId, String warehouseId,
                                       ItemStack stack, @Nullable InventoryView cursorView) throws Exception {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || isBlacklisted(stack)) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=invalid-stack stack=" + WarehouseTexts.describeStack(stack));
            return DepositResult.failure(message("player.item-not-depositable"));
        }
        if (isItemLocked(stack)) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=item-locked stack=" + WarehouseTexts.describeStack(stack));
            return DepositResult.failure(message("player.item-locked"));
        }
        if (OWNER_SHARED.equals(ownerType) && !canModifyCurrent(player, state(player))) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=shared-read-only ownerId=" + ownerId);
            return DepositResult.failure(message("player.shared-read-only"));
        }
        ItemStack prototype = stack.clone();
        prototype.setAmount(1);
        byte[] bytes = ItemSerializer.serialize(prototype);
        String rejectReason = validateSerializedItem(player.getName(), prototype, bytes);
        if (rejectReason != null) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=" + rejectReason
                + " stack=" + WarehouseTexts.describeStack(stack));
            return DepositResult.failure(rejectMessage(rejectReason));
        }
        String itemData = Base64.getEncoder().encodeToString(bytes);
        String itemHash = WarehouseTexts.sha256Hex(bytes);
        long now = System.currentTimeMillis();
        int remaining = stack.getAmount();
        long stored = 0L;
        Object warehouseLock = warehouseLocks.computeIfAbsent(
            ownerType + ':' + ownerId + ':' + warehouseId, ignored -> new Object());
        List<SlotItemRecord> currentSlots;
        synchronized (warehouseLock) {
            currentSlots = repository.loadSlotSummaries(ownerType, ownerId, warehouseId);
        List<WarehouseRepository.SlotChange> changes = new ArrayList<>();
        debug("DEPOSIT-STACK load player=" + player.getName() + " ownerType=" + ownerType + " ownerId=" + ownerId
            + " warehouseId=" + warehouseId + " currentSlots=" + currentSlots.size() + " hash=" + WarehouseTexts.shortHash(itemHash)
            + " itemJsonEmpty=" + itemJson(prototype).isBlank() + " verify=" + configuration.verifyOnDeposit());

        for (SlotItemRecord current : currentSlots) {
            if (remaining <= 0 || !itemHash.equals(current.itemHash())) {
                continue;
            }
            long room = Math.max(0L, MAX_AGGREGATED_AMOUNT - current.amount());
            int moved = (int) Math.min(remaining, room);
            if (moved > 0) {
                changes.add(new WarehouseRepository.SlotChange(current, withAmount(current, current.amount() + moved, now)));
                debug("DEPOSIT-STACK merge player=" + player.getName() + " slot=" + current.slot()
                    + " moved=" + moved + " oldAmount=" + current.amount() + " newAmount=" + (current.amount() + moved));
                remaining -= moved;
                stored += moved;
            }
        }

        if (remaining > 0) {
            int emptySlot = firstEmptySlot(currentSlots, packetFactory.currentWarehouseCapacity(player, ownerType, ownerId, warehouseId));
            if (emptySlot >= 0) {
                String displayName = resolveDisplayName(prototype);
                String categoryId = resolveCategory(prototype);
                // searchText 写 displayName（原版物品为中文翻译名，自定义物品为自定义名）
                SearchTokens searchTokens = WarehouseTexts.toSearchTokens(displayName);
                String displayJson = itemJson(prototype);
                changes.add(new WarehouseRepository.SlotChange(null, new SlotItemRecord(
                    ownerType,
                    ownerId,
                    warehouseId,
                    emptySlot,
                    itemHash,
                    categoryId,
                    displayName,
                    prototype.getType().name(),
                    searchTokens.searchText(),
                    searchTokens.pinyin(),
                    searchTokens.initials(),
                    itemData,
                    displayJson,
                    remaining,
                    now,
                    now,
                    WarehouseTexts.loreText(prototype)
                )));
                debug("DEPOSIT-STACK insert player=" + player.getName() + " slot=" + emptySlot + " amount=" + remaining
                    + " item=" + WarehouseTexts.describeStack(prototype) + " itemJsonEmpty=" + displayJson.isBlank());
                stored += remaining;
                remaining = 0;
            } else {
                debug("DEPOSIT-STACK no-empty-slot player=" + player.getName() + " currentSlots=" + currentSlots.size());
            }
        }

        if (stored <= 0L) {
            debug("DEPOSIT-STACK failure player=" + player.getName() + " reason=no-stored remaining=" + remaining);
            return DepositResult.failure(message("player.warehouse-full"));
        }
            if (cursorView != null) {
                if (materialTransfers == null) return DepositResult.failure("仓库物品存档暂不可用，请稍后重试");
                ItemStack[] after = InventoryItems.copy(player.getInventory().getStorageContents());
                if (remaining > 0) {
                    int limit = Math.min(stack.getMaxStackSize(), player.getInventory().getMaxStackSize());
                    if (planWithdrawal(after, stack, remaining, limit) != remaining) {
                        return DepositResult.failure(message("player.inventory-full"));
                    }
                }
                var cursor = new WarehouseMaterials.CursorDeposit(cursorView, stack, itemData);
                String detail = materialTransfers.transferInventory(player, OWNER_PERSONAL, warehouseId, null,
                    changes, after, List.of(), false, cursor);
                if (!detail.isEmpty()) return DepositResult.failure(detail);
            } else if (!commitSlotChanges(player, ownerType, changes, false)) {
                throw new java.sql.SQLException("仓库内容已变化，请重试入库");
            }
            return DepositResult.success(stored, remaining);
        }
    }

    private static SlotItemRecord withAmount(SlotItemRecord item, long amount, long now) {
        return new SlotItemRecord(item.ownerType(), item.ownerId(), item.warehouseId(), item.slot(), item.itemHash(),
            item.categoryId(), item.displayName(), item.materialId(), item.searchText(), item.pinyin(), item.initials(),
            item.itemData(), item.itemJson(), amount, item.createdAt(), now, item.loreText());
    }

    boolean isBlacklisted(ItemStack stack) {
        var blacklist = configuration.blacklist();
        return !blacklist.isEmpty() && itemMatcherSupport.matchesAny(blacklist, stack);
    }

    /**
     * 已序列化物品的入库前校验：大小上限 + 按 {@code settings.verify-on-deposit} 级别的往返验证。
     * <p>
     * 往返验证能拦下"序列化成功但反序列化降级/损坏"的物品（混合端尤其常见），
     * 让坏物品在存入时就被拒绝而不是取回时才发现。返回 {@code null} 表示通过，
     * 否则返回消息键后缀（对应 messages.yml 中 {@code player.<key>} 条目）。
     */
    private String validateSerializedItem(String player, ItemStack prototype, byte[] bytes) {
        long maxBytes = configuration.maxItemBytes();
        if (maxBytes > 0 && bytes.length > maxBytes) {
            this.logger.warning("拒绝存入超限物品: " + prototype.getType().name()
                + " bytes=" + bytes.length + " max=" + maxBytes + " player=" + player);
            return "item-too-large:" + bytes.length;
        }
        if (!configuration.verifyOnDeposit().covers(prototype)) {
            return null;
        }
        try {
            ItemStack verify = ItemSerializer.deserialize(bytes);
            if (verify == null || verify.getType().isAir() || verify.getType() != prototype.getType()
                || !prototype.isSimilar(verify)
                || (prototype.getItemMeta() instanceof BlockStateMeta && !(verify.getItemMeta() instanceof BlockStateMeta))) {
                this.logger.warning("物品序列化往返校验失败，拒绝存入: "
                    + prototype.getType().name() + " player=" + player);
                return "item-not-depositable";
            }
        } catch (RuntimeException exception) {
            this.logger.warning("物品序列化往返校验异常，拒绝存入: "
                + prototype.getType().name() + " player=" + player + " - " + exception.getMessage());
            return "item-not-depositable";
        }
        return null;
    }

    /**
     * 提取校验失败原因对应的消息键：{@code "item-too-large:<bytes>"} 携带字节数参数。
     */
    private String rejectMessage(String rejectReason) {
        if (rejectReason != null && rejectReason.startsWith("item-too-large:")) {
            long bytes = WarehouseTexts.parseLong(rejectReason.substring("item-too-large:".length()), 0L);
            return message("player.item-too-large", WarehouseTexts.formatByteSize(bytes));
        }
        return message("player.item-not-depositable");
    }

    /**
     * 从当前仓库取出物品到玩家背包。
     * 需要二级密码已解锁，且当前仓库可写。按堆叠上限分批给予，背包满时停止。
     */
    private void withdraw(Player player, int slot, long requestedAmount, boolean all, boolean quickStack) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-withdraw"));
            refreshBoth(player);
            return;
        }
        ViewState state = state(player);
        if (!canModifyCurrent(player, state)) {
            sendMessage(player, false, message("player.read-only-withdraw"));
            refreshBoth(player);
            return;
        }
        int selectedSlot = slot >= 0 && slot < packetFactory.pageSize() ? packetFactory.actualSlotFromDisplay(state, slot) : -1;
        if (!validSlot(selectedSlot)) {
            sendMessage(player, false, message("player.no-slot-selected"));
            refreshBoth(player);
            return;
        }
        Object warehouseLock = warehouseLocks.computeIfAbsent(
            state.ownerType() + ':' + state.ownerId() + ':' + state.warehouseId(), ignored -> new Object());
        long delivered;
        synchronized (warehouseLock) {
            Optional<SlotItemRecord> optional = repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
            if (optional.isEmpty()) {
                sendMessage(player, false, message("player.slot-no-item"));
                refreshBoth(player);
                return;
            }
            SlotItemRecord item = optional.get();
            ItemStack base = deserializeSlotItem(item);
            if (base == null || base.getType().isAir()) {
                SlotItemRecord displayed=state.displayedSlots.get(slot);
                if(displayed!=null && displayed.itemHash().equals(item.itemHash()) && displayed.createdAt()==item.createdAt()) {
                    long count=all?item.amount():Math.max(1L,Math.min(requestedAmount,quickStack?unavailableBatchSize(item):item.amount()));
                    discardUnavailable(player,item,count);
                }
                refreshBoth(player);
                return;
            }
            // 快捷取出（双击）按「一组」结算：上限收紧为物品自身堆叠数——不可堆叠取 1，16 堆叠取 16，64 堆叠取 64。
            SlotItemRecord displayed = state.displayedSlots.get(slot);
            if (displayed == null || !displayed.itemHash().equals(item.itemHash())) {
                refreshBoth(player);
                return;
            }
            long requested = quickStack ? Math.min(requestedAmount, Math.max(1L, base.getMaxStackSize())) : requestedAmount;
            long amount = all ? item.amount() : Math.max(1L, Math.min(requested, item.amount()));
            ItemStack[] after = InventoryItems.copy(player.getInventory().getStorageContents());
            int stackLimit = Math.min(base.getMaxStackSize(), player.getInventory().getMaxStackSize());
            delivered = planWithdrawal(after, base, amount, stackLimit);
            if (delivered <= 0L) {
                sendMessage(player, false, message("player.inventory-full"));
                refreshBoth(player);
                return;
            }
            long updatedAmount = item.amount() - delivered;
            var change = new WarehouseRepository.SlotChange(item,
                updatedAmount <= 0 ? null : withAmount(item, updatedAmount, System.currentTimeMillis()));
            String detail = transferInventory(player, state, List.of(change), after, List.of(), true);
            if (!detail.isEmpty()) {
                sendMessage(player, false, detail);
                refreshBoth(player);
                return;
            }
            if (updatedAmount <= 0L) {
                state.setSelectedSlot(-1);
            }
        }
        sendMessage(player, true, message("player.withdraw-success", delivered));
        markActionCommitted(player);
        refreshBoth(player);
    }

    private static long planWithdrawal(ItemStack[] inventory, ItemStack prototype, long amount, int limit) {
        long remaining = amount;
        for (ItemStack stack : inventory) {
            if (remaining == 0) break;
            if (InventoryItems.empty(stack) || !stack.isSimilar(prototype)) continue;
            int added = (int) Math.min(remaining, Math.max(0, limit - stack.getAmount()));
            if (added == 0) continue;
            stack.setAmount(stack.getAmount() + added);
            remaining -= added;
        }
        for (int slot = 0; slot < inventory.length && remaining > 0; slot++) {
            if (!InventoryItems.empty(inventory[slot])) continue;
            int added = (int) Math.min(remaining, limit);
            if (added == 0) break;
            inventory[slot] = prototype.clone();
            inventory[slot].setAmount(added);
            remaining -= added;
        }
        return amount - remaining;
    }

    ActionResult withdrawPanelItem(Player player, SlotItemRecord record, ItemStack item, int amount) {
        String owner = player.getUniqueId().toString();
        if (amount <= 0 || amount > record.amount() || !OWNER_PERSONAL.equals(record.ownerType())
            || !owner.equals(record.ownerId())) {
            return ActionResult.failure(message("player.operation-failed"));
        }
        if (materialTransfers == null) return ActionResult.failure("仓库物品存档暂不可用，请稍后重试");
        Object warehouseLock = warehouseLocks.computeIfAbsent(
            OWNER_PERSONAL + ':' + owner + ':' + record.warehouseId(), ignored -> new Object());
        synchronized (warehouseLock) {
            ItemStack[] after = InventoryItems.copy(player.getInventory().getStorageContents());
            int limit = Math.min(item.getMaxStackSize(), player.getInventory().getMaxStackSize());
            if (planWithdrawal(after, item, amount, limit) != amount) {
                return ActionResult.failure(message("player.inventory-full"));
            }
            long remaining = record.amount() - amount;
            var change = new WarehouseRepository.SlotChange(record,
                remaining == 0 ? null : withAmount(record, remaining, System.currentTimeMillis()));
            String detail = materialTransfers.transferInventory(player, OWNER_PERSONAL, record.warehouseId(), null,
                List.of(change), after, List.of(), true);
            return detail.isEmpty() ? ActionResult.success("") : ActionResult.failure(detail);
        }
    }

    private String transferInventory(Player player, ViewState state, List<WarehouseRepository.SlotChange> changes,
                                     ItemStack[] after, List<WarehouseMaterials.InventoryPart> deposited,
                                     boolean withdrawal) {
        if (materialTransfers == null) return "仓库物品存档暂不可用，请稍后重试";
        WarehouseRepository.SharedActor actor = null;
        if (OWNER_SHARED.equals(state.ownerType())) {
            var lease = sharedEditLocks.get(state.ownerId());
            if (lease == null || !lease.player().equals(player.getUniqueId())) return "共享仓库操作权限已变化";
            actor = new WarehouseRepository.SharedActor(player.getUniqueId(), player.getName(), false, lease.token());
        }
        return materialTransfers.transferInventory(player, state.ownerType(), state.warehouseId(), actor,
            changes, after, deposited, withdrawal);
    }

    private CurrencyBridgeAPI.CurrencyBridge bankTransferBridge(Player player, String normalized) throws Exception {
        if (!configuration.bankCurrencies().contains(normalized)) throw new IllegalArgumentException("该货币未启用银行服务");
        var bridge = currencyBridgeManager.bridge(normalized);
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? message("player.unknown-currency") : bridge.unavailableReason());
            refreshBoth(player);
            return null;
        }
        if (bridge.creditRequiresReconciliation() || bridge.debitRequiresReconciliation()) {
            sendMessage(player, false, message("player.bank-transfer-provider"));
            refreshBoth(player);
            return null;
        }
        return bridge;
    }

    private BigDecimal bankTransferAmount(Player player, CurrencyBridgeAPI.CurrencyBridge bridge, BigDecimal input) throws Exception {
        BigDecimal amount = exactBankPrincipal(input);
        if (amount != null && bridge.supportsExactAmount(amount)) return amount;
        sendMessage(player, false, message("player.bank-transfer-amount-invalid"));
        refreshBoth(player);
        return null;
    }

    private static BigDecimal exactBankPrincipal(BigDecimal input) {
        // Match DECIMAL(38,8) in the bank debit and reject oversized exponents before expanding them.
        if (input.signum() > 0 && (long) input.precision() - input.scale() <= 30) {
            BigDecimal amount = input.stripTrailingZeros();
            if (amount.scale() <= 8) return amount;
        }
        return null;
    }

    /**
     * 玩家将钱包货币存入银行活期账户。
     * 钱包扣款前保存凭证，银行入账与凭证结案同事务。
     */
    private void bankDeposit(Player player, String currencyId, BigDecimal input) throws Exception {
        String normalized = WarehouseTexts.normalizeId(currencyId);
        var bridge = bankTransferBridge(player, normalized);
        if (bridge == null) return;
        BigDecimal amount = bankTransferAmount(player, bridge, input);
        if (amount == null) return;
        var deposit = new WarehouseRepository.BankTransfer(UUID.randomUUID().toString(), player.getUniqueId(),
            normalized, amount, System.currentTimeMillis());
        try {
            repository.beginBankDeposit(deposit);
            var result = bridge.withdraw(player, amount);
            if (result == null || result.uncertain()) throw new IllegalStateException("钱包扣款未确认");
            if (!result.success()) {
                repository.closeBankDeposit(deposit, false);
                sendMessage(player, false, message("player.insufficient-funds"));
                refreshBoth(player);
                return;
            }
            try {
                repository.creditBankDeposit(deposit);
            } catch (java.sql.SQLException | RuntimeException | LinkageError failure) {
                try {
                    if (!repository.reserveBankDepositRefund(deposit)) throw failure;
                    var refund = bridge.deposit(player, amount);
                    if (refund == null || refund.uncertain() || !refund.success() || bridge.creditRequiresReconciliation()) {
                        throw new IllegalStateException("钱包退款未确认");
                    }
                    repository.closeBankDeposit(deposit, true);
                } catch (java.sql.SQLException | RuntimeException | LinkageError refundFailure) {
                    if (failure != refundFailure) refundFailure.addSuppressed(failure);
                    throw refundFailure;
                }
                markActionCommitted(player);
                sendMessage(player, false, message("player.bank-deposit-refunded"));
                refreshBoth(player);
                return;
            }
        } catch (java.sql.SQLException | RuntimeException | LinkageError failure) {
            logger.log(java.util.logging.Level.WARNING, "仓库存款待核对: id=" + deposit.id() + " player="
                + deposit.playerUuid() + " currency=" + normalized + " amount=" + amount.toPlainString(), failure);
            markActionCommitted(player);
            sendMessage(player, false, message("player.bank-deposit-review", deposit.id()));
            refreshBoth(player);
            return;
        }
        markActionCommitted(player);
        sendMessage(player, true, message("player.bank-deposit-success", formatCurrency(normalized, amount)));
        refreshBoth(player);
    }

    /**
     * 玩家从银行活期账户提现到背包。
     * 先保存提现凭证并预留银行余额。只有明确未入账时才释放预留。
     */
    private void bankWithdraw(Player player, String currencyId, BigDecimal input) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-withdraw-bank"));
            refreshBoth(player);
            return;
        }
        String normalized = WarehouseTexts.normalizeId(currencyId);
        var bridge = bankTransferBridge(player, normalized);
        if (bridge == null) return;
        BigDecimal amount = bankTransferAmount(player, bridge, input);
        if (amount == null) return;
        var withdrawal = new WarehouseRepository.BankTransfer(UUID.randomUUID().toString(), player.getUniqueId(),
            normalized, amount, System.currentTimeMillis());
        final boolean credited;
        try {
            if (!repository.beginBankWithdrawal(withdrawal)) {
                sendMessage(player, false, message("player.bank-invalid-amount"));
                refreshBoth(player);
                return;
            }
            var result = bridge.deposit(player, amount);
            if (result == null || result.uncertain() || (result.success() && bridge.creditRequiresReconciliation())) {
                throw new IllegalStateException("钱包未确认本次入账");
            }
            credited = result.success();
            repository.finishBankWithdrawal(withdrawal, credited);
        } catch (java.sql.SQLException | RuntimeException | LinkageError failure) {
            logger.warning("仓库提现待核对: id=" + withdrawal.id() + " player=" + withdrawal.playerUuid()
                + " currency=" + normalized + " amount=" + amount.toPlainString() + "，" + failure.getMessage());
            markActionCommitted(player);
            sendMessage(player, false, message("player.bank-withdraw-review", withdrawal.id()));
            refreshBoth(player);
            return;
        }
        if (!credited) {
            sendMessage(player, false, message("player.bank-deposit-failed"));
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.bank-withdraw-success", formatCurrency(normalized, amount)));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 购买定期存款产品。
     * 校验金额区间、匹配利率阶梯、原子扣减活期余额后创建定期记录。
     */
    private void createFixedDeposit(Player player, String productId, BigDecimal input) throws Exception {
        DepositProductDefinition product = configuration.depositProduct(productId);
        if (product == null || !configuration.bankCurrencies().contains(product.currencyId()) || !packetFactory.canUseProduct(player, product)) {
            sendMessage(player, false, message("player.fixed-deposit-unavailable"));
            refreshBoth(player);
            return;
        }
        BigDecimal amount = exactBankPrincipal(input);
        if (amount == null) {
            sendMessage(player, false, message("player.bank-transfer-amount-invalid"));
            refreshBoth(player);
            return;
        }
        if (amount.compareTo(product.minAmount()) < 0 || (product.maxAmount().compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(product.maxAmount()) > 0)) {
            sendMessage(player, false, message("player.fixed-deposit-range"));
            refreshBoth(player);
            return;
        }
        InterestTier tier = product.tierFor(amount);
        if (tier == null) {
            sendMessage(player, false, message("player.fixed-deposit-tier"));
            refreshBoth(player);
            return;
        }
        long maturesAt = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(product.durationSeconds(), 1000L));
        long now = System.currentTimeMillis();
        var deposit = new FixedDepositRecord(UUID.randomUUID().toString(), player.getUniqueId(), product.id(),
            product.currencyId(), amount, tier.rate(), now, maturesAt, false, 0L);
        if (!repository.purchaseFixedDeposit(deposit)) {
            sendMessage(player, false, message("player.bank-insufficient"));
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.fixed-deposit-created"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 领取到期定期存款本息。
     * 通过 {@link WarehouseRepository#claimFixedDepositAtomic} 原子标记 claimed 并计算本息入账，防止并发重复领取。
     */
    private void claimFixedDeposit(Player player, String depositId) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-claim"));
            refreshBoth(player);
            return;
        }
        Optional<FixedDepositRecord> optional = repository.loadFixedDeposits(player.getUniqueId()).stream()
            .filter(deposit -> deposit.id().equals(depositId))
            .findFirst();
        if (optional.isEmpty()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        FixedDepositRecord deposit = optional.get();
        if (deposit.claimed() || deposit.maturesAt() > System.currentTimeMillis()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        Optional<BigDecimal> payout = repository.claimFixedDepositAtomic(depositId, player.getUniqueId(), System.currentTimeMillis());
        if (payout.isEmpty()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.fixed-deposit-claimed", formatCurrency(deposit.currencyId(), payout.get())));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 删除已领取的定期存款记录。
     * 服务端二次校验 claimed 状态，仓库层 DELETE 语句也强制 claimed = true，双保险防止误删未领取资产。
     */
    private void deleteFixedDeposit(Player player, String depositId) throws Exception {
        Optional<FixedDepositRecord> optional = repository.loadFixedDeposits(player.getUniqueId()).stream()
            .filter(deposit -> deposit.id().equals(depositId))
            .findFirst();
        if (optional.isEmpty() || !optional.get().claimed()) {
            sendMessage(player, false, message("player.fixed-deposit-not-deletable"));
            refreshBoth(player);
            return;
        }
        if (!repository.deleteFixedDeposit(depositId, player.getUniqueId())) {
            sendMessage(player, false, message("player.fixed-deposit-not-deletable"));
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.fixed-deposit-deleted"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 创建共享仓库。校验权限层级的 max-owned 限制，扣除创建费用后写入数据库。
     */
    private void createSharedWarehouse(Player player, String rawName) throws Exception {
        if (!configuration.shared().enabled()) {
            sendMessage(player, false, message("player.shared-disabled"));
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = packetFactory.resolveSharedTier(player);
        if (repository.countOwnedSharedWarehouses(player.getUniqueId()) >= tier.maxOwned()) {
            sendMessage(player, false, message("player.shared-limit"));
            refreshBoth(player);
            return;
        }
        WarehouseLevelDefinition initialLevel = packetFactory.sharedInitialLevel();
        if (initialLevel == null) {
            sendMessage(player, false, message("player.shared-level-missing"));
            refreshBoth(player);
            return;
        }
        int level = initialLevel.level();
        long capacity = Math.max(SLOT_COUNT, initialLevel.capacity());
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        if (!withdrawCost(player, cost, message("player.unknown-shared-create-currency"))) {
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        try {
            repository.createSharedWarehouse(new SharedWarehouseRecord(id, player.getUniqueId(), WarehouseTexts.crop(rawName.isBlank() ? "共享仓库" : rawName, 64), level, capacity, now, now, "owner", true));
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, cost == null ? message("player.shared-created") : message("player.shared-created-cost", formatCurrency(cost.currencyId(), cost.amount())));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 删除共享仓库（需二级密码确认）。仅所有者可操作，删除后清理互斥锁并重置当前视图。
     */
    private void deleteSharedWarehouse(Player player, String sharedId) throws Exception {
        var status = actionPasswordStatus.get();
        if (status == null || !status.unlocked()) {
            sendMessage(player, false, message(secondaryPasswordSupplier == null
                || secondaryPasswordSupplier.get() == null
                ? "player.secondary-unavailable" : "player.shared-delete-confirm"));
            refreshBoth(player);
            return;
        }
        UUID owner = player.getUniqueId();
        var existing = sharedEditLocks.get(sharedId);
        if ((existing != null && !existing.player().equals(owner)) || !deletingSharedWarehouses.add(sharedId)) {
            sendMessage(player, false, message("player.lock-busy"));
            return;
        }
        String name = player.getName();
        String node = localNodeId();
        try {
            background.execute(() -> {
                String result;
                try {
                    result = deleteSharedWarehouseInStorage(sharedId, owner, name, node, existing, status);
                } catch (java.sql.SQLException failure) {
                    logger.warning("删除共享仓库失败: " + failure.getMessage());
                    result = "player.operation-failed";
                } finally {
                    deletingSharedWarehouses.remove(sharedId);
                }
                if (!active) return;
                String resultKey = result;
                Bukkit.getScheduler().runTask(plugin, () -> finishSharedWarehouseDeletion(player, owner, sharedId, resultKey));
            });
        } catch (RuntimeException failure) {
            deletingSharedWarehouses.remove(sharedId);
            throw failure;
        }
        markActionCommitted(player);
    }

    private String deleteSharedWarehouseInStorage(String sharedId, UUID owner, String name, String node,
            WarehouseRepository.SharedLease existing, SecondaryPasswordAccess.Status status) throws java.sql.SQLException {
        if (!active || !status.unlocked()) return "player.shared-delete-confirm";
        var record = repository.loadSharedWarehouse(owner, sharedId);
        if (record.isEmpty() || !"owner".equalsIgnoreCase(record.get().viewerRole())) return "player.shared-delete-owner";
        UUID token = existing == null ? UUID.randomUUID() : existing.token();
        if (existing == null && !repository.acquireSharedLease(sharedId, owner, token, name, node, SHARED_LEASE_MS)) {
            return "player.lock-busy";
        }
        boolean deleted = false;
        try {
            if (!active || !status.unlocked()) return "player.shared-delete-confirm";
            repository.deleteSharedWarehouse(sharedId, owner, token, SHARED_LEASE_MS);
            deleted = true;
            if (existing != null) sharedEditLocks.remove(sharedId, existing);
            return "player.shared-deleted";
        } finally {
            if (deleted || existing == null) {
                try {
                    repository.releaseSharedLease(sharedId, owner, token);
                } catch (java.sql.SQLException failure) {
                    logger.warning("释放删除操作的共享仓库租约失败: " + failure.getMessage());
                }
            }
        }
    }

    private void finishSharedWarehouseDeletion(Player player, UUID owner, String sharedId, String result) {
        if (!active || Bukkit.getPlayer(owner) != player || !player.isOnline()) return;
        boolean deleted = "player.shared-deleted".equals(result);
        ViewState state = viewStates.get(owner);
        if (deleted && state != null && OWNER_SHARED.equals(state.ownerType()) && sharedId.equals(state.ownerId())) {
            state.setOwnerType(OWNER_PERSONAL);
            state.setOwnerId(owner.toString());
            state.setWarehouseId(packetFactory.firstPersonalWarehouseId());
            state.setSelectedSlot(-1);
        }
        sendMessage(player, deleted, message(result));
        refreshAfterPasswordAction(player);
    }

    private void renameSharedWarehouse(Player player, String sharedId, String rawName) throws Exception {
        String name = WarehouseTexts.crop(WarehouseTexts.safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, message("player.shared-name-empty"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-name-owner"));
            refreshBoth(player);
            return;
        }
        repository.updateSharedWarehouseName(sharedId, player.getUniqueId(), name, System.currentTimeMillis());
        sendMessage(player, true, message("player.shared-name-updated", name));
        markActionCommitted(player);
        refreshBoth(player);
    }

    private void toggleSharedWarehouseShowcase(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-showcase-owner"));
            refreshBoth(player);
            return;
        }
        boolean newValue = !shared.get().showcaseEnabled();
        repository.updateSharedWarehouseShowcase(sharedId, player.getUniqueId(), newValue, System.currentTimeMillis());
        markActionCommitted(player);
        if (!newValue) revokeShowcase(player.getUniqueId(), OWNER_SHARED, sharedId);
        sendMessage(player, true, message("player.shared-showcase-updated", newValue ? message("player.enabled") : message("player.disabled")));
        refreshBoth(player);
    }

    private void renamePersonalWarehouse(Player player, String warehouseId, String rawName) throws Exception {
        String name = WarehouseTexts.crop(WarehouseTexts.safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, message("player.warehouse-name-empty"));
            refreshBoth(player);
            return;
        }
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, message("player.warehouse-missing"));
            refreshBoth(player);
            return;
        }
        repository.updatePersonalWarehouseName(player.getUniqueId(), warehouseId, name, System.currentTimeMillis());
        sendMessage(player, true, message("player.warehouse-name-updated", name));
        markActionCommitted(player);
        refreshBoth(player);
    }

    private void togglePersonalWarehouseShowcase(Player player, String warehouseId) throws Exception {
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, message("player.warehouse-missing"));
            refreshBoth(player);
            return;
        }
        boolean newValue = !record.showcaseEnabled();
        repository.updatePersonalWarehouseShowcase(player.getUniqueId(), warehouseId, newValue, System.currentTimeMillis());
        markActionCommitted(player);
        if (!newValue) revokeShowcase(player.getUniqueId(), OWNER_PERSONAL, warehouseId);
        sendMessage(player, true, message("player.showcase-updated", newValue ? message("player.enabled") : message("player.disabled")));
        refreshBoth(player);
    }

    /**
     * 邀请玩家加入共享仓库，或修改现有成员角色。
     * 仅所有者可操作，校验成员数量上限，目标角色不可为 owner。
     */
    private void inviteSharedMember(Player player, String sharedId, String memberName, String role) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-add-member"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), sharedId);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-invite-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (!isRealPlayer(target)) {
            sendMessage(player, false, message("player.player-not-found"));
            refreshBoth(player);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, message("player.shared-invite-self"));
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = packetFactory.resolveSharedTier(player);
        String normalizedRole = switch (WarehouseTexts.normalizeId(role)) {
            case "viewer" -> "viewer";
            case "owner" -> "member";
            default -> "member";
        };
        repository.upsertSharedMember(sharedId, player.getUniqueId(), target.getUniqueId(), normalizedRole, tier.maxMembers(), System.currentTimeMillis());
        sendMessage(player, true, message("player.shared-member-updated"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    private void removeSharedMember(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-remove-member"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), sharedId);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-remove-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (target == null || target.getUniqueId() == null || target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, message("player.shared-member-remove-failed"));
            refreshBoth(player);
            return;
        }
        repository.removeSharedMember(sharedId, player.getUniqueId(), target.getUniqueId());
        releaseSharedLocks(target.getUniqueId());
        sendMessage(player, true, message("player.shared-member-removed"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 将共享仓库所有权转让给现有 member 角色成员。
     * 转让后原所有者变为 member，目标成员提升为 owner。
     */
    private boolean isRealPlayer(OfflinePlayer player) {
        return player != null && player.getUniqueId() != null
            && (Bukkit.getPlayer(player.getUniqueId()) != null || player.hasPlayedBefore());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            List<PendingTransfer> pending = repository.loadPendingTransfers(event.getPlayer().getUniqueId());
            long now = System.currentTimeMillis();
            for (PendingTransfer transfer : pending) {
                if (transfer.expiresAt() > 0 && transfer.expiresAt() <= now) {
                    repository.deletePendingTransfer(transfer.sharedId());
                    sendMessage(event.getPlayer(), false, message("player.transfer-expired-or-invalid"));
                } else {
                    sendMessage(event.getPlayer(), true, message("player.transfer-pending-notice"));
                }
            }
        } catch (Exception exception) {
            logger.warning("检查仓库待确认转让失败: " + exception.getMessage());
        }
    }

    public ActionResult confirmPendingTransfer(Player player, String sharedId) {
        try {
            if (!configuration.shared().enabled() || repository.countOwnedSharedWarehouses(player.getUniqueId()) >= packetFactory.resolveSharedTier(player).maxOwned())
                return ActionResult.failure(message("player.shared-limit"));
            Optional<PendingTransfer> pendingOptional = repository.loadPendingTransfer(sharedId);
            if (pendingOptional.isEmpty() || !pendingOptional.get().targetUuid().equals(player.getUniqueId())) return ActionResult.failure(message("player.transfer-none"));
            PendingTransfer pending = pendingOptional.get();
            if (pending.expiresAt() > 0 && pending.expiresAt() <= System.currentTimeMillis()) { repository.deletePendingTransfer(sharedId); return ActionResult.failure(message("player.transfer-expired")); }
            Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(pending.fromOwnerUuid()).stream().filter(record -> record.id().equals(sharedId) && record.ownerUuid().equals(pending.fromOwnerUuid()) && "owner".equalsIgnoreCase(record.viewerRole())).findFirst();
            Optional<SharedMemberRecord> member = repository.loadSharedMembers(sharedId).stream().filter(record -> record.playerUuid().equals(player.getUniqueId()) && "member".equalsIgnoreCase(record.role())).findFirst();
            if (shared.isEmpty() || member.isEmpty()) { repository.deletePendingTransfer(sharedId); return ActionResult.failure(message("player.transfer-invalid")); }
            repository.transferSharedWarehouse(sharedId, pending.fromOwnerUuid(), player.getUniqueId(), System.currentTimeMillis());
            repository.deletePendingTransfer(sharedId);

            WarehouseRepository.SharedLease sharedLock = sharedEditLocks.get(sharedId);
            if (sharedLock != null) releaseSharedLock(sharedId, sharedLock.player());
            releaseSharedLocks(pending.fromOwnerUuid());
            releaseSharedLocks(player.getUniqueId());

            Player previousOwner = Bukkit.getPlayer(pending.fromOwnerUuid());
            if (previousOwner != null) {
                ViewState previousOwnerState = viewStates.get(previousOwner.getUniqueId());
                if (previousOwnerState != null && OWNER_SHARED.equals(previousOwnerState.ownerType())
                    && sharedId.equals(previousOwnerState.ownerId())) {
                    previousOwnerState.setSharedEditMode(false);
                }
                refreshBoth(previousOwner);
            }
            ViewState targetState = viewStates.get(player.getUniqueId());
            if (targetState != null && OWNER_SHARED.equals(targetState.ownerType())
                && sharedId.equals(targetState.ownerId())) {
                targetState.setSharedEditMode(false);
            }
            markActionCommitted(player);
            refreshBoth(player);
            return ActionResult.success(message("player.transfer-confirmed"));
        } catch (Exception exception) { return ActionResult.failure(message("player.transfer-confirm-failed", exception.getMessage())); }
    }

    public ActionResult rejectPendingTransfer(Player player, String sharedId) {
        try {
            Optional<PendingTransfer> pending = repository.loadPendingTransfer(sharedId);
            if (pending.isEmpty() || !pending.get().targetUuid().equals(player.getUniqueId())) return ActionResult.failure(message("player.transfer-none"));
            repository.deletePendingTransfer(sharedId);
            markActionCommitted(player);
            return ActionResult.success(message("player.transfer-rejected"));
        } catch (Exception exception) { return ActionResult.failure(message("player.transfer-reject-failed", exception.getMessage())); }
    }

    private void transferSharedWarehouse(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-transfer"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), sharedId);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-transfer-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (!isRealPlayer(target)) {
            sendMessage(player, false, message("player.player-not-found"));
            refreshBoth(player);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, message("player.transfer-member-only"));
            refreshBoth(player);
            return;
        }
        Optional<SharedMemberRecord> member = repository.loadSharedMembers(sharedId).stream()
            .filter(record -> record.playerUuid().equals(target.getUniqueId()))
            .findFirst();
        if (member.isEmpty() || !"member".equalsIgnoreCase(member.get().role())) {
            sendMessage(player, false, message("player.transfer-role-required", sharedRoleName("member")));
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        long expireHours = configuration.transferConfirmExpireHours();
        long expiresAt = expireHours <= 0 ? 0L : now + expireHours * 60L * 60L * 1000L;
        repository.upsertPendingTransfer(new PendingTransfer(sharedId, player.getUniqueId(), target.getUniqueId(), now, expiresAt));
        sendMessage(player, true, message("player.transfer-started"));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            sendMessage(onlineTarget, true, message("player.transfer-received"));
        }
        markActionCommitted(player);
        refreshBoth(player);
        return;
    }

    private void unlockPassword(Player player, String password) throws Exception {
        SecondaryPasswordAccess passwordAccess = secondaryPasswordSupplier == null
            ? null : secondaryPasswordSupplier.get();
        if (passwordAccess == null) {
            sendMessage(player, false, message("player.secondary-unavailable"));
            refreshBoth(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        passwordAccess.verifyAsync(player, WarehouseTexts.safe(password)).whenComplete((verified, failure) -> {
            if (!active) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active || Bukkit.getPlayer(playerId) != player || !player.isOnline()) return;
                boolean ok = failure == null && Boolean.TRUE.equals(verified);
                sendMessage(player, ok, message(ok ? "player.password-unlocked" : "player.password-wrong"));
                refreshAfterPasswordAction(player);
            });
        });
    }

    private void setPassword(Player player, String newPassword) throws Exception {
        SecondaryPasswordAccess passwordAccess = secondaryPasswordSupplier == null
            ? null : secondaryPasswordSupplier.get();
        if (passwordAccess == null) {
            sendMessage(player, false, message("player.secondary-unavailable"));
            refreshBoth(player);
            return;
        }
        String trimmed = WarehouseTexts.safe(newPassword);
        int minLen = passwordAccess.minLength();
        int maxLen = passwordAccess.maxLength();
        if (trimmed.length() < minLen || trimmed.length() > maxLen) {
            sendMessage(player, false, message("player.password-length", minLen, maxLen));
            refreshBoth(player);
            return;
        }
        UUID id = player.getUniqueId();
        TokenLease lease = activeActionToken.get();
        if (lease == null) return;
        String token = tokenMap(lease.uiId()).get(id);
        passwordAccess.setAsync(player, "", trimmed).whenComplete((applied, failure) -> {
            if (!active) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active || Bukkit.getPlayer(id) != player || !player.isOnline() || !packetBridge.isUiOpen(player, lease.uiId())
                    || !java.util.Objects.equals(token, tokenMap(lease.uiId()).get(id))) return;
                boolean success = failure == null && Boolean.TRUE.equals(applied);
                if (success) issueActionToken(player, lease.uiId());
                sendMessage(player, success, message(success ? "player.password-unlocked" : "player.password-wrong"));
                refreshAfterPasswordAction(player);
            });
        });
    }

    private void refreshAfterPasswordAction(Player player) {
        try {
            refreshBoth(player);
        } catch (Exception exception) {
            logger.warning("刷新仓库密码状态失败: " + exception.getMessage());
        }
    }

    private void lockPassword(Player player) throws Exception {
        SecondaryPasswordAccess passwordAccess = secondaryPasswordSupplier == null
            ? null : secondaryPasswordSupplier.get();
        if (passwordAccess == null) {
            sendMessage(player, false, message("player.secondary-unavailable"));
            refreshBoth(player);
            return;
        }
        passwordAccess.lock(player);
        sendMessage(player, true, message("player.secondary-locked"));
        markActionCommitted(player);
        refreshBoth(player);
    }

    void ensureEntitlements(Player player) throws Exception {
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
            repository.upsertPersonalWarehouse(player.getUniqueId(), definition.id(), 1, "", now);
        }
    }

    private void ensureCurrentWarehouse(Player player, ViewState state) throws Exception {
        if (OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId());
            if (shared.isPresent()) {
                return;
            }
        }
        state.setOwnerType(OWNER_PERSONAL);
        state.setOwnerId(player.getUniqueId().toString());
        if (state.warehouseId().isBlank() || !personalWarehouseMap(player.getUniqueId()).containsKey(state.warehouseId())) {
            state.setWarehouseId(packetFactory.firstPersonalWarehouseId());
        }
    }

    ViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> {
            // 优先从数据库加载玩家个人设置，没有记录时回退到 config.yml 全局默认值
            boolean autoPickup = configuration.pickup().autoStoreOnPickup();
            boolean autoMythic = configuration.pickup().autoStoreMythicLoot();
            boolean autoNotify = configuration.pickup().notifyOnAutoStore();
            try {
                var settings = pickupSettings(player);
                autoPickup = settings.autoPickup(); autoMythic = settings.autoMythic(); autoNotify = settings.autoNotify();
            } catch (Exception exception) {
                debug("load-player-settings-failed player=" + player.getName() + " error=" + exception.getMessage());
            }
            return ViewState.initial(player, autoPickup, autoMythic, autoNotify,
                configuration.bankCurrencies().stream().findFirst().orElse("money"),
                configuration.search().defaultSortId());
        });
    }

    private WarehouseRepository.PlayerSettingsRecord pickupSettings(Player player) throws java.sql.SQLException {
        return pickupSettings(player.getUniqueId());
    }

    private WarehouseRepository.PlayerSettingsRecord pickupSettings(UUID id) throws java.sql.SQLException {
        var cached = pickupPreferences.get(id);
        if (cached != null) return cached;
        var loaded = repository.loadPlayerSettings(id).orElseGet(() -> defaultPickupSettings(id));
        pickupPreferences.put(id, loaded);
        return loaded;
    }

    private WarehouseRepository.PlayerSettingsRecord defaultPickupSettings(UUID id) {
        return new WarehouseRepository.PlayerSettingsRecord(id, configuration.pickup().autoStoreOnPickup(),
            configuration.pickup().autoStoreMythicLoot(), configuration.pickup().notifyOnAutoStore(), 0);
    }

    private List<WarehouseRecord> personalRecords(UUID playerUuid) throws Exception {
        return repository.loadPersonalWarehouses(playerUuid);
    }

    Map<String, WarehouseRecord> personalWarehouseMap(UUID playerUuid) throws Exception {
        Map<String, WarehouseRecord> records = new LinkedHashMap<>();
        for (WarehouseRecord record : repository.loadPersonalWarehouses(playerUuid)) {
            records.put(record.warehouseId(), record);
        }
        return records;
    }

    /** 第一个个人仓库 ID（管理命令 view 的默认目标）。 */
    public String firstPersonalWarehouseId() {
        return packetFactory.firstPersonalWarehouseId();
    }

    /** 个人仓库已解锁槽位数 = base-slots + extra_slots。 */
    long personalUnlockedSlots(UUID playerUuid, String warehouseId) throws Exception {
        WarehouseDefinition definition = configuration.warehouse(warehouseId);
        WarehouseRecord record = personalWarehouseMap(playerUuid).get(warehouseId);
        if (definition == null || record == null) {
            return definition == null ? 0L : definition.baseSlots();
        }
        return definition.baseSlots() + Math.max(0L, record.extraSlots());
    }

    /**
     * 加载槽位摘要（不含 item_data 大字段）。返回前按需触发分类懒重算，
     * 保证 category_id 已按当前规则刷新。
     */
    List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) {
        try {
            recategorizeIfStale(ownerType, ownerId, warehouseId);
            return repository.loadSlotSummaries(ownerType, ownerId, warehouseId);
        } catch (Exception exception) {
            throw new IllegalStateException("读取仓库物品失败，请稍后重试", exception);
        }
    }

    /**
     * 仓库已用格数（SQL COUNT，不加载物品数据）。
     */
    long countSlots(String ownerType, String ownerId, String warehouseId) {
        try {
            return repository.countSlots(ownerType, ownerId, warehouseId);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("统计仓库槽位失败: " + exception.getMessage());
            }
            return 0L;
        }
    }

    /**
     * 分类规则变更后的存量物品重归类：按仓库懒执行。
     * <p>
     * 每个仓库记录上次重算时使用的分类规则指纹；指纹与当前配置一致直接返回。
     * 不一致时加载完整槽位数据、反序列化所有物品按当前规则重算 categoryId，
     * 差异经 {@code updateSlotCategory} 轻量写回——下次调用走指纹短路，零开销。
     */
    private void recategorizeIfStale(String ownerType, String ownerId, String warehouseId) {
        String key = ownerType + '|' + ownerId + '|' + warehouseId;
        String fingerprint = categoriesFingerprint();
        if (fingerprint.equals(recategorizedRules.get(key))) {
            return;
        }
        recategorizedRules.put(key, fingerprint);
        List<SlotItemRecord> records;
        try {
            records = repository.loadSlots(ownerType, ownerId, warehouseId);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("重归类读取仓库槽位失败: " + exception.getMessage());
            }
            return;
        }
        if (records.isEmpty()) {
            return;
        }
        int changed = 0;
        for (SlotItemRecord record : records) {
            ItemStack stack = deserializeSlotItem(record);
            if (stack == null) {
                continue;
            }
            String categoryId = resolveCategory(stack);
            if (record.categoryId().equals(categoryId)) {
                continue;
            }
            try {
                repository.updateSlotCategory(ownerType, ownerId, warehouseId, record.slot(), categoryId, System.currentTimeMillis());
                changed++;
            } catch (Exception exception) {
                this.logger.warning("槽位重分类写回失败 " + ownerId + "/" + warehouseId + "#" + record.slot() + ": " + exception.getMessage());
            }
        }
        if (changed > 0) {
            this.logger.info("仓库 " + ownerId + "/" + warehouseId + " 存量物品重分类完成: " + changed + " 个槽位已更新");
        }
    }

    ItemStack deserializeSlotItem(SlotItemRecord record) {
        try {
            if (record.itemData() == null || record.itemData().isBlank()) {
                return null;
            }
            ItemStack item=ItemSerializer.deserialize(Base64.getDecoder().decode(record.itemData()));
            if(item==null || item.getType().isAir())return null;
            if(record.materialId()!=null && !record.materialId().isBlank()
                && !record.materialId().equalsIgnoreCase(item.getType().name())
                && !record.materialId().equalsIgnoreCase(item.getType().getKey().toString())
                && Material.matchMaterial(record.materialId())!=item.getType())return null;
            return item;
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    ItemStack unavailableItemIcon(SlotItemRecord record){
        ItemStack icon=new ItemStack(Material.BARRIER);
        ItemMeta meta=icon.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(ChatColor.RED+"不可用物品："+ChatColor.stripColor(WarehouseTexts.safe(record.displayName())));
            meta.setLore(List.of(ChatColor.GRAY+"原类型："+WarehouseTexts.safe(record.materialId()),
                ChatColor.RED+"物品无法还原，手动取出将直接丢弃",ChatColor.RED+"不会生成物品；已清理部分无法恢复",
                ChatColor.GRAY+"剩余数量："+record.amount()));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    int unavailableBatchSize(SlotItemRecord record){
        Material known=Material.matchMaterial(WarehouseTexts.safe(record.materialId()));
        int max=known==null || known.isAir()?64:Math.max(1,known.getMaxStackSize());
        return (int)Math.max(0L,Math.min(record.amount(),max));
    }

    /** Only explicit manual UI withdrawal may discard unavailable stock. Never used by material APIs. */
    long discardUnavailable(Player player,SlotItemRecord expected,long requested) throws Exception {
        if(requested<=0)return 0;
        if(OWNER_PERSONAL.equals(expected.ownerType()) && !player.getUniqueId().toString().equals(expected.ownerId()))return 0;
        Object lock=warehouseLocks.computeIfAbsent(expected.ownerType()+':'+expected.ownerId()+':'+expected.warehouseId(),ignored->new Object());
        synchronized(lock){
            var current=repository.loadSlot(expected.ownerType(),expected.ownerId(),expected.warehouseId(),expected.slot()).orElse(null);
            if(current==null || !current.itemHash().equals(expected.itemHash()) || current.createdAt()!=expected.createdAt()
                || current.amount()<=0 || deserializeSlotItem(current)!=null)return 0;
            long taken=Math.min(current.amount(),requested);
            if(!commitSlotChanges(player, current.ownerType(), List.of(new WarehouseRepository.SlotChange(current,
                taken==current.amount()?null:withAmount(current,current.amount()-taken,System.currentTimeMillis()))), true))return 0;
            logger.warning("玩家 "+player.getUniqueId()+" 手动丢弃不可用仓库物品: "+current.ownerType()+"/"+current.ownerId()+"/"+current.warehouseId()+"#"+current.slot()+" 类型="+current.materialId()+" 数量="+taken+" hash="+current.itemHash());
            sendMessage(player,false,message("player.unavailable-discarded",taken));
            return taken;
        }
    }

    private boolean commitSlotChanges(Player player, String ownerType, List<WarehouseRepository.SlotChange> changes,
                                      boolean discarded) throws java.sql.SQLException {
        if (OWNER_SHARED.equals(ownerType)) {
            if (changes.isEmpty()) return true;
            var first = changes.get(0).before() == null ? changes.get(0).after() : changes.get(0).before();
            var lease = sharedEditLocks.get(first.ownerId());
            if (lease == null || !lease.player().equals(player.getUniqueId())) return false;
            return repository.applySharedSlotChanges(changes,
                new WarehouseRepository.SharedActor(player.getUniqueId(), player.getName(), discarded, lease.token()));
        }
        return repository.applySlotChanges(changes);
    }

    private void showSharedActivity(Player player, List<String> data) throws Exception {
        ViewState view = state(player);
        UUID playerId = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(playerId);
        if (!admission.admitted() || !player.hasPermission("arcartxsuite.warehouse.use")) return;
        String warehouseId = view.ownerId();
        if (!OWNER_SHARED.equals(view.ownerType()) || !ownsSharedWarehouse(playerId, warehouseId)) {
            sendMessage(player, false, "只有共享仓库所有者可以查看取放记录");
            return;
        }
        String member = WarehouseTexts.crop(WarehouseTexts.value(data, 1, "").strip(), 64);
        String item = WarehouseTexts.crop(WarehouseTexts.value(data, 2, "").strip(), 64);
        int days = Math.max(0, Math.min(365, WarehouseTexts.parseInt(WarehouseTexts.value(data, 3, "0"), 0)));
        int page = Math.max(0, Math.min(9999, WarehouseTexts.parseInt(WarehouseTexts.value(data, 4, "0"), 0)));
        int pageSize = 8;
        long since = days == 0 ? 0 : System.currentTimeMillis() - days * 86_400_000L;
        UUID request = UUID.randomUUID();
        if (activityRequests.putIfAbsent(playerId, request) != null) {
            sendMessage(player, false, "正在读取取放记录，请稍候");
            return;
        }
        try {
        background.execute(() -> {
            try {
                var records = repository.sharedActivity(warehouseId, playerId, member, item, since, page * pageSize, pageSize + 1);
                Bukkit.getScheduler().runTask(plugin, () -> {
                     if (!activityRequests.remove(playerId, request)
                         || !canReceiveSharedActivity(player, admission, view, warehouseId)) return;
                    try {
                         if (!ownsSharedWarehouse(playerId, warehouseId)
                             || !canReceiveSharedActivity(player, admission, view, warehouseId)) return;
                        Map<String, Object> rows = new LinkedHashMap<>();
                        for (var entry : records.subList(0, Math.min(records.size(), pageSize))) {
                            String action = switch (entry.action()) {
                                case "deposit" -> "存入";
                                case "discard" -> "清理";
                                default -> "取出";
                            };
                            rows.put(Integer.toString(rows.size()), Map.of("actor", entry.actor(), "action", action,
                                "item", entry.item().replaceAll("\\p{Cntrl}", " "), "amount", Long.toString(entry.amount()),
                                "time", TIME_FORMATTER.format(Instant.ofEpochMilli(entry.time()))));
                        }
                        packetBridge.sendPacket(player, manageRuntimeUiId, "shared_activity", Map.of(
                            "warehouse", warehouseId, "page", page, "hasNext", records.size() > pageSize, "rows", rows));
                    } catch (Exception exception) {
                        sendMessage(player, false, "共享仓库取放记录读取失败");
                    }
                });
            } catch (Exception exception) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                     if (activityRequests.remove(playerId, request)
                         && canReceiveSharedActivity(player, admission, view, warehouseId))
                         sendMessage(player, false, "共享仓库取放记录读取失败");
                });
            }
        });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            activityRequests.remove(playerId, request);
            if (active) sendMessage(player, false, "共享仓库记录读取繁忙，请稍后重试");
        }
    }

    private boolean canReceiveSharedActivity(Player player, AuthSnapshot admission, ViewState view, String warehouseId) {
        UUID id = player.getUniqueId();
        if (!active || Bukkit.getPlayer(id) != player || !player.isOnline()
            || !player.hasPermission("arcartxsuite.warehouse.use") || viewStates.get(id) != view
            || !OWNER_SHARED.equals(view.ownerType()) || !warehouseId.equals(view.ownerId())
            || !packetBridge.isUiOpen(player, manageRuntimeUiId)) return false;
        AuthSnapshot current = AuthAccess.snapshot(id);
        return current.admitted() && current.generation() == admission.generation()
            && java.util.Objects.equals(current.connectionId(), admission.connectionId());
    }

    private boolean ownsSharedWarehouse(UUID player, String warehouse) throws java.sql.SQLException {
        return repository.loadSharedWarehouse(player, warehouse)
            .map(record -> player.equals(record.ownerUuid())).orElse(false);
    }

    /**
     * 槽位展示 lore：优先返回存入时冗余写入的 lore_text（零反序列化开销）；
     * 历史行（lore_text 为 NULL）按需加载完整槽位、反序列化派生 lore 并回填写库。
     */
    List<String> loreLinesFor(SlotItemRecord item) {
        String stored = item.loreText();
        if (stored != null) {
            return WarehouseTexts.loreLinesFromStored(stored);
        }
        try {
            SlotItemRecord full = item.itemData() != null && !item.itemData().isBlank()
                ? item
                : repository.loadSlot(item.ownerType(), item.ownerId(), item.warehouseId(), item.slot()).orElse(null);
            ItemStack stack = full == null ? null : deserializeSlotItem(full);
            String lore = WarehouseTexts.loreText(stack);
            if (full != null) {
                try {
                    repository.updateSlotLore(full.ownerType(), full.ownerId(), full.warehouseId(), full.slot(), lore, System.currentTimeMillis());
                } catch (Exception exception) {
                    if (configuration.debug()) {
                        this.logger.warning("槽位 lore 回填失败 " + full.ownerId() + "/" + full.warehouseId() + "#" + full.slot() + ": " + exception.getMessage());
                    }
                }
            }
            return WarehouseTexts.loreLinesFromStored(lore);
        } catch (Exception exception) {
            return List.of("&f物品描述读取失败。");
        }
    }

    /**
     * 导出指定槽位的原始物品数据到 exports/warehouse 目录（损坏物品人工恢复用）。
     */
    public void exportSlotItem(UUID playerUuid, String warehouseId, int slot, Consumer<String> successCallback, Consumer<String> errorCallback) {
        try {
            Optional<SlotItemRecord> record = repository.loadSlot(OWNER_PERSONAL, playerUuid.toString(), warehouseId, slot);
            if (record.isEmpty()) {
                errorCallback.accept(message("player.slot-no-item"));
                return;
            }
            SlotItemRecord item = record.get();
            File dir = new File(plugin.getDataFolder(), "exports" + File.separator + "warehouse");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("无法创建导出目录: " + dir.getAbsolutePath());
            }
            File target = new File(dir,
                playerUuid + "_" + warehouseId + "_slot" + slot + "_" + System.currentTimeMillis() + ".txt");
            String content = "# ArcartXSuite Warehouse 槽位物品导出（item_data 为 BukkitObjectOutputStream 序列化的 Base64）\n"
                + "player_uuid=" + playerUuid + '\n'
                + "warehouse_id=" + warehouseId + '\n'
                + "slot=" + item.slot() + '\n'
                + "item_hash=" + item.itemHash() + '\n'
                + "display_name=" + item.displayName() + '\n'
                + "material_id=" + item.materialId() + '\n'
                + "amount=" + item.amount() + '\n'
                + "created_at=" + item.createdAt() + '\n'
                + "updated_at=" + item.updatedAt() + '\n'
                + "item_data_base64=\n" + item.itemData() + '\n'
                + "item_json=\n" + (item.itemJson() == null ? "" : item.itemJson()) + '\n';
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            successCallback.accept(message("admin.export.success", target.getAbsolutePath()));
        } catch (Exception exception) {
            errorCallback.accept(message("admin.export.failed", exception.getMessage()));
        }
    }

    /** 当前分类规则的指纹：任一字段变化即触发全部仓库的懒重算。 */
    private String categoriesFingerprint() {
        StringBuilder builder = new StringBuilder();
        List<CategoryDefinition> definitions = new ArrayList<>(configuration.categories().values());
        definitions.sort(Comparator.comparing(CategoryDefinition::id));
        for (CategoryDefinition definition : definitions) {
            builder.append(definition.id()).append('=').append(definition.priority())
                .append('|').append(definition.nbtPath())
                .append('|').append(definition.values())
                .append('|').append(definition.materialIds())
                .append('|').append(definition.materialPrefixes())
                .append('|').append(definition.fallback()).append(';');
        }
        return Integer.toHexString(builder.toString().hashCode());
    }

    static int firstEmptySlot(List<SlotItemRecord> currentSlots, long capacity) {
        long cappedCapacity = Math.max(0L, Math.min(Integer.MAX_VALUE, capacity));
        Set<Integer> occupied = new HashSet<>();
        for (SlotItemRecord item : currentSlots) {
            if (item.slot() >= 0 && item.slot() < cappedCapacity) {
                occupied.add(item.slot());
            }
        }
        for (int slot = 0; slot < cappedCapacity; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    boolean canModifyCurrent(Player player, ViewState state) throws Exception {
        if (state.previewMode()) return false;
        if (OWNER_PERSONAL.equals(state.ownerType())) {
            return player.getUniqueId().toString().equals(state.ownerId());
        }
        if (!state.sharedEditMode() || deletingSharedWarehouses.contains(state.ownerId())) {
            return false;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId());
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        var lock = sharedEditLocks.get(state.ownerId());
        return lock != null && lock.player().equals(player.getUniqueId());
    }

    boolean sharedCanEdit(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return false;
        }
        return repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId())
            .map(shared -> canEditShared(shared.viewerRole()))
            .orElse(false);
    }

    boolean currentShowcaseEnabled(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return personalWarehouseMap(player.getUniqueId()).getOrDefault(state.warehouseId(), new WarehouseRecord(player.getUniqueId(), state.warehouseId(), 1, "", true, 0L, 0L)).showcaseEnabled();
        }
        return repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId())
            .map(SharedWarehouseRecord::showcaseEnabled)
            .orElse(true);
    }

    private boolean acquireCurrentSharedLock(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return true;
        }
        if (deletingSharedWarehouses.contains(state.ownerId())) return false;
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId());
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        String sharedId = state.ownerId();
        UUID playerId = player.getUniqueId();
        var existing = sharedEditLocks.get(sharedId);
        if (existing != null && existing.player().equals(playerId)
            && repository.renewSharedLease(sharedId, playerId, existing.token(), SHARED_LEASE_MS)) return true;
        UUID token = UUID.randomUUID();
        if (repository.acquireSharedLease(sharedId, playerId, token, player.getName(), localNodeId(), SHARED_LEASE_MS)) {
            sharedEditLocks.put(sharedId, new WarehouseRepository.SharedLease(playerId, token, player.getName(), localNodeId()));
            return true;
        }
        sendMessage(player, false, buildLockBusyMessage(repository.sharedLease(sharedId).orElse(null)));
        return false;
    }

    private boolean canEditShared(String role) {
        String normalized = WarehouseTexts.normalizeId(role);
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
            if (lock.player().equals(playerUuid)) {
                sharedIds.add(sharedId);
            }
        });
        for (String sharedId : sharedIds) {
            releaseSharedLock(sharedId, playerUuid);
        }
    }

    private void releaseSharedLock(String sharedId, UUID playerUuid) {
        var existing = sharedEditLocks.get(sharedId);
        if (existing == null || !existing.player().equals(playerUuid) || !sharedEditLocks.remove(sharedId, existing)) return;
        releaseSharedLeaseInStorage(sharedId, existing);
    }

    private void releaseSharedLeaseInStorage(String sharedId, WarehouseRepository.SharedLease existing) {
        try {
            repository.releaseSharedLease(sharedId, existing.player(), existing.token());
        } catch (java.sql.SQLException failure) {
            logger.warning("释放共享仓库租约失败，将等待到期: " + sharedId + " " + failure.getMessage());
        }
    }

    private void renewSharedLeases() {
        if (!active) return;
        for (var entry : sharedEditLocks.entrySet()) {
            if (!active) break;
            var lease = entry.getValue();
            try {
                if (!repository.renewSharedLease(entry.getKey(), lease.player(), lease.token(), SHARED_LEASE_MS)) {
                    sharedEditLocks.remove(entry.getKey(), lease);
                }
            } catch (java.sql.SQLException failure) {
                logger.warning("续期共享仓库租约失败: " + entry.getKey() + " " + failure.getMessage());
            }
        }
    }

    private String buildLockBusyMessage(WarehouseRepository.SharedLease lock) {
        if (lock == null) {
            return "该共享仓库正在被其他成员编辑，当前以只读方式打开。";
        }
        if (localNodeId().equals(lock.node())) {
            return "该共享仓库正在被 " + lock.name() + " 编辑，当前以只读方式打开。";
        }
        return "该共享仓库正在被 " + lock.name() + "（" + lock.node() + "）编辑，当前以只读方式打开。";
    }

    private String localNodeId() {
        return BuildEdition.CROSS_SERVER_INCLUDED && crossServerLockService != null ? crossServerLockService.nodeId() : "local";
    }

    String lockOwnerName(ViewState state) {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return "";
        }
        var lock = sharedEditLocks.get(state.ownerId());
        if (lock == null) {
            return "";
        }
        return lock.name();
    }

    /**
     * 扩充当前仓库到下一等级。
     * 个人仓库和共享仓库均支持，扣除升级费用后更新数据库。
     */
    private void upgradeCurrentWarehouse(Player player) throws Exception {
        ViewState state = state(player);
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-upgrade"));
            refreshBoth(player);
            return;
        }
        if (OWNER_SHARED.equals(state.ownerType())) {
            upgradeCurrentSharedWarehouse(player, state);
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            sendMessage(player, false, message("player.upgrade-not-available-here"));
            refreshBoth(player);
            return;
        }
        PurchaseOutcome outcome = purchasePersonalSlots(player, state.warehouseId(), 1L);
        if (!outcome.success()) {
            if (!outcome.message().isBlank()) {
                sendMessage(player, false, outcome.message());
            }
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.buy-slots-success", outcome.purchased(), outcome.newUnlocked()));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /**
     * 个人仓库按数量购买槽位：qty 指定购买格数，总价 = slot-price.amount × qty，
     * 超出可购买余量时按余量封顶。
     */
    private void buySlots(Player player, long qty) throws Exception {
        ViewState state = state(player);
        if (!isSecondaryUnlocked(player)) {
            sendMessage(player, false, message("player.secondary-required-upgrade"));
            refreshBoth(player);
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            sendMessage(player, false, message("player.buy-slots-not-here"));
            refreshBoth(player);
            return;
        }
        PurchaseOutcome outcome = purchasePersonalSlots(player, state.warehouseId(), qty);
        if (!outcome.success()) {
            if (!outcome.message().isBlank()) {
                sendMessage(player, false, outcome.message());
            }
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.buy-slots-success", outcome.purchased(), outcome.newUnlocked()));
        markActionCommitted(player);
        refreshBoth(player);
    }

    /** 逐格购买个人仓库槽位：扣款后 extra_slots+purchased，返回实际购买数与新解锁格数。 */
    private record PurchaseOutcome(boolean success, long purchased, long newUnlocked, String message) {
        static PurchaseOutcome failure(String message) {
            return new PurchaseOutcome(false, 0L, 0L, message);
        }
    }

    private PurchaseOutcome purchasePersonalSlots(Player player, String warehouseId, long qty) throws Exception {
        WarehouseDefinition definition = configuration.warehouse(warehouseId);
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (definition == null || record == null) {
            return PurchaseOutcome.failure(message("player.upgrade-unavailable"));
        }
        if (qty < 1) {
            return PurchaseOutcome.failure(message("player.buy-slots-invalid"));
        }
        long unlocked = definition.baseSlots() + Math.max(0L, record.extraSlots());
        long remaining = definition.maxSlots() - unlocked;
        if (remaining <= 0) {
            return PurchaseOutcome.failure(message("player.buy-slots-maxed"));
        }
        WarehouseModuleConfiguration.UpgradeCost cost = definition.slotPrice();
        if (cost == null) {
            return PurchaseOutcome.failure(message("player.buy-slots-not-configured"));
        }
        long purchased = Math.min(qty, remaining);
        WarehouseModuleConfiguration.UpgradeCost scaledCost =
            new WarehouseModuleConfiguration.UpgradeCost(cost.currencyId(), cost.amount().multiply(BigDecimal.valueOf(purchased)));
        if (!withdrawCost(player, scaledCost, message("player.unknown-buy-slots-currency"))) {
            return PurchaseOutcome.failure("");
        }
        try {
            repository.addPersonalExtraSlots(player.getUniqueId(), warehouseId, purchased, System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, scaledCost);
            throw exception;
        }
        return new PurchaseOutcome(true, purchased, unlocked + purchased, "");
    }

    private void upgradeCurrentSharedWarehouse(Player player, ViewState state) throws Exception {
        Optional<SharedWarehouseRecord> shared = packetFactory.currentSharedRecord(player, state);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-upgrade-owner"));
            refreshBoth(player);
            return;
        }
        Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
        if (levels.isEmpty()) {
            sendMessage(player, false, message("player.shared-upgrade-level-missing"));
            refreshBoth(player);
            return;
        }
        SharedWarehouseRecord record = shared.get();
        WarehouseLevelDefinition next = levels.get(record.level() + 1);
        if (next == null) {
            sendMessage(player, false, message("player.shared-upgrade-maxed"));
            refreshBoth(player);
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = packetFactory.upgradeCost(levels, record.level());
        if (!withdrawCost(player, cost, message("player.unknown-shared-upgrade-currency"))) {
            refreshBoth(player);
            return;
        }
        try {
            repository.updateSharedWarehouseLevel(record.id(), player.getUniqueId(), record.level(), next.level(), Math.max(SLOT_COUNT, next.capacity()), System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, message("player.shared-upgrade-success", next.level(), next.capacity()));
        markActionCommitted(player);
        refreshBoth(player);
    }

    boolean slotMatches(SlotItemRecord item, String categoryId, String keyword) {
        String normalizedCategory = WarehouseTexts.normalizeId(categoryId);
        if (!normalizedCategory.isBlank() && !"all".equals(normalizedCategory) && !normalizedCategory.equals(item.categoryId())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        // 纯字段匹配：存入时 searchText 已包含中文翻译名/自定义名
        return matchesTokens(item.searchText(), item.pinyin(), item.initials(), keyword);
    }

    /**
     * 多关键词匹配：搜索词按空格拆分，每个部分都要在 searchText/pinyin/initials 中至少匹配一个。
     * pinyin/initials 字段内部用空格分隔每个字，匹配时去空格还原为连续串再 contains。
     * 支持混合搜索如 "zs jian"（首字母 + 拼音）匹配 "钻石剑"。
     */
    private static boolean matchesTokens(String searchText, String pinyin, String initials, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedSearchText = WarehouseTexts.normalizeToken(searchText);
        String normalizedPinyin = pinyin == null ? "" : pinyin.replace(" ", "");
        String normalizedInitials = initials == null ? "" : initials.replace(" ", "");
        String[] parts = keyword.split("\\s+");
        for (String part : parts) {
            String normalizedPart = WarehouseTexts.normalizeToken(part);
            if (normalizedPart.isBlank()) {
                continue;
            }
            if (!normalizedSearchText.contains(normalizedPart)
                && !normalizedPinyin.contains(normalizedPart)
                && !normalizedInitials.contains(normalizedPart)) {
                return false;
            }
        }
        return true;
    }

    int toBukkitBackpackSlot(int rawSlot) {
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }
        return rawSlot;
    }

    private int parseRawBackpackSlot(String rawValue) {
        String value = WarehouseTexts.safe(rawValue);
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

    boolean validSlot(int slot) {
        return slot >= 0;
    }

    String resolveDisplayName(ItemStack itemStack) {
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(itemStack);
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    String itemJson(ItemStack itemStack) {
        if (itemStackBridge == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        return itemStackBridge.itemToPreviewJson(itemStack).orElse("");
    }
    String resolveCategory(ItemStack itemStack) {
        // 物品注册键（minecraft:diamond / tacz:modern_kinetic_gun），mod 物品按此分类
        String materialKey = itemStack.getType().getKey().toString().toLowerCase(Locale.ROOT);
        for (CategoryDefinition definition : priorityCategories) {
            // 注册键匹配：material-ids 精确命中或 material-prefixes 前缀命中（如 tacz:）
            if (definition.materialIds().contains(materialKey)
                || definition.materialPrefixes().stream().anyMatch(materialKey::startsWith)) {
                return definition.id();
            }
            // NBT 路径匹配：path 解析出的值命中 values 列表
            if (!definition.nbtPath().isBlank() && !definition.values().isEmpty()) {
                String value = WarehouseTexts.normalizeId(resolvePath(itemStack, definition.nbtPath()));
                if (!value.isBlank() && definition.values().contains(value)) {
                    return definition.id();
                }
            }
        }
        return configuration.otherCategory().id();
    }

    private String resolvePath(ItemStack itemStack, String rawPath) {
        String path = WarehouseTexts.safe(rawPath).toLowerCase(Locale.ROOT);
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

    boolean isSecondaryUnlocked(Player player) {
        SecondaryPasswordAccess.Status status = actionPasswordStatus.get();
        return status != null && status.unlocked();
    }

    String formatCurrency(String currencyId, BigDecimal amount) {
        return currencyBridgeManager.format(currencyId, amount == null ? BigDecimal.ZERO : amount);
    }
    String currencyDisplayName(String currencyId) {
        CurrencyDefinition definition = currencyBridgeManager.definition(currencyId);
        return definition == null ? currencyId : definition.displayName();
    }

    String formatCurrencyWithName(String currencyId, BigDecimal amount) {
        return formatCurrency(currencyId, amount) + " " + currencyDisplayName(currencyId);
    }

    String sharedRoleName(String role) {
        WarehouseModuleConfiguration.SharedRoleNames names = configuration.shared().roleNames();
        return switch (WarehouseTexts.normalizeId(role)) {
            case "owner" -> names.owner();
            case "viewer" -> names.viewer();
            default -> names.member();
        };
    }

    String sharedCreateCostText() {
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        return cost == null ? message("ui.shared-create-free") : message("ui.shared-create-cost", formatCurrencyWithName(cost.currencyId(), cost.amount()));
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
            sendMessage(player, false, message("player.insufficient-funds"));
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

    String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    private void sendMessage(Player player, boolean success, String message) {
        if (!sendUiNotice(player, message)) {
            player.sendMessage(PREFIX + (success ? ChatColor.GREEN : ChatColor.RED) + message);
        }
    }

    private boolean sendUiNotice(Player player, String message) {
        if (packetBridge != null) {
            String parent=noticeTargets.getOrDefault(player.getUniqueId(),"");
            for(String ui:List.of(parent,storageRuntimeUiId,manageRuntimeUiId,bankRuntimeUiId))
                if (!ui.isBlank() && packetBridge.isUiOpen(player, ui)
                    && packetBridge.sendPacket(player, ui, "ui_notice", Map.of("message", ChatColor.stripColor(message)))) {
                    return true;
                }
        }
        return false;
    }

    void noticeTarget(Player player,String ui){noticeTargets.put(player.getUniqueId(),ui);}
    void forgetNoticeTarget(UUID player){noticeTargets.remove(player);}

    void debug(String message) {
        if (configuration.debug()) {
            this.logger.info("[WarehouseDebug] " + message);
        }
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    record SearchTokens(String searchText, String pinyin, String initials) {
    }

    record DepositResult(boolean success, long storedAmount, int remainingAmount, String message) {
        static DepositResult success(long storedAmount, int remainingAmount) {
            return new DepositResult(true, storedAmount, remainingAmount, "");
        }

        static DepositResult failure(String message) {
            return new DepositResult(false, 0L, 0, message);
        }
    }

    static final class ViewState {
        private String ownerType;
        private String ownerId;
        private String warehouseId;
        private String categoryId;
        private String search;
        private String sortId;
        private int page;
        private int selectedSlot;
        private boolean sharedEditMode;
        private boolean autoPickup;
        private boolean autoPickupMythic;
        private boolean autoPickupNotify;
        private WarehousePreview preview;
        private String selectedCurrency;
        final Map<Integer, SlotItemRecord> displayedSlots = new HashMap<>();
        String displayedView = "";

        private ViewState(String ownerType, String ownerId, String warehouseId, String categoryId, String search, String sortId, int selectedSlot,
                          boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify, String selectedCurrency) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.warehouseId = warehouseId;
            this.categoryId = categoryId;
            this.search = search;
            this.sortId = sortId;
            this.page = 1;
            this.selectedSlot = selectedSlot;
            this.sharedEditMode = false;
            this.autoPickup = autoPickup;
            this.autoPickupMythic = autoPickupMythic;
            this.autoPickupNotify = autoPickupNotify;
            this.selectedCurrency = selectedCurrency;
        }

        static ViewState initial(Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify, String selectedCurrency, String sortId) {
            return new ViewState(OWNER_PERSONAL, player.getUniqueId().toString(), "", "all", "", sortId, -1, autoPickup, autoPickupMythic, autoPickupNotify, selectedCurrency);
        }

        static ViewState shared(String sharedId, Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify, String selectedCurrency, String sortId) {
            return new ViewState(OWNER_SHARED, sharedId, sharedId, "all", "", sortId, -1, autoPickup, autoPickupMythic, autoPickupNotify, selectedCurrency);
        }

        static ViewState preview(WarehousePreview preview, String warehouseId, String ownerType, String sortId) {
            String ownerId = OWNER_SHARED.equals(ownerType) ? warehouseId : preview.target().toString();
            ViewState state = new ViewState(ownerType, ownerId, warehouseId, "all", "", sortId, -1, false, false, false, "");
            state.preview = preview;
            return state;
        }

        String ownerType() { return ownerType; }
        String ownerId() { return ownerId; }
        String warehouseId() { return warehouseId; }
        String categoryId() { return categoryId; }
        String search() { return search; }
        String sortId() { return sortId; }
        int page() { return page; }
        int selectedSlot() { return selectedSlot; }
        boolean sharedEditMode() { return sharedEditMode; }
        boolean autoPickup() { return autoPickup; }
        boolean autoPickupMythic() { return autoPickupMythic; }
        boolean autoPickupNotify() { return autoPickupNotify; }
        boolean previewMode() { return preview != null; }
        WarehousePreview preview() { return preview; }
        String selectedCurrency() { return selectedCurrency; }
        void setAutoPickup(boolean autoPickup) { this.autoPickup = autoPickup; }
        void setAutoPickupMythic(boolean autoPickupMythic) { this.autoPickupMythic = autoPickupMythic; }
        void setAutoPickupNotify(boolean autoPickupNotify) { this.autoPickupNotify = autoPickupNotify; }
        void setOwnerType(String ownerType) { this.ownerType = ownerType; }
        void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        void setSearch(String search) { this.search = search; }
        void setSortId(String sortId) { this.sortId = sortId; }
        void setPage(int page) { this.page = page; }
        void setSelectedSlot(int selectedSlot) { this.selectedSlot = selectedSlot; }
        void setSharedEditMode(boolean sharedEditMode) { this.sharedEditMode = sharedEditMode; }
        void setSelectedCurrency(String selectedCurrency) { this.selectedCurrency = selectedCurrency; }
    }

    // ---------------------------------------------------------------------
    // WarehouseAccess capability：扩展背包等模块直连玩家个人仓库
    // ---------------------------------------------------------------------

    @Override
    public boolean isAvailable(@NotNull Player player) {
        return player != null && player.isOnline() && !configuration.warehouses().isEmpty();
    }

    @Override
    public @NotNull WarehouseAccess.Snapshot snapshot(@NotNull Player player) {
        try {
            UUID uuid = player.getUniqueId();
            String warehouseId = packetFactory.firstPersonalWarehouseId();
            WarehouseDefinition definition = configuration.warehouse(warehouseId);
            if (definition == null) {
                return new WarehouseAccess.Snapshot(false, 0L, 0L, 0L, false, new WarehouseAccess.SlotPrice("", BigDecimal.ZERO));
            }
            long unlocked = personalUnlockedSlots(uuid, warehouseId);
            long used = repository.countSlots(OWNER_PERSONAL, uuid.toString(), warehouseId);
            WarehouseModuleConfiguration.UpgradeCost price = definition.slotPrice();
            WarehouseAccess.SlotPrice slotPrice = price == null
                ? new WarehouseAccess.SlotPrice("", BigDecimal.ZERO)
                : new WarehouseAccess.SlotPrice(price.currencyId(), price.amount());
            return new WarehouseAccess.Snapshot(
                true, unlocked, used, definition.maxSlots(),
                price != null && unlocked < definition.maxSlots(), slotPrice);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("读取仓库快照失败(" + player.getName() + "): " + exception.getMessage());
            }
            return new WarehouseAccess.Snapshot(false, 0L, 0L, 0L, false, new WarehouseAccess.SlotPrice("", BigDecimal.ZERO));
        }
    }

    @Override
    public @NotNull List<WarehouseAccess.SlotItem> slots(@NotNull Player player) {
        try {
            String warehouseId = packetFactory.firstPersonalWarehouseId();
            // 需要完整物品数据走全列加载；先确保分类已按当前规则重算
            recategorizeIfStale(OWNER_PERSONAL, player.getUniqueId().toString(), warehouseId);
            List<WarehouseAccess.SlotItem> items = new ArrayList<>();
            for (SlotItemRecord record : repository.loadSlots(OWNER_PERSONAL, player.getUniqueId().toString(), warehouseId)) {
                ItemStack item;
                try {
                    item = deserializeSlotItem(record);
                } catch (RuntimeException exception) {
                    continue;
                }
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                item.setAmount((int) Math.min(Integer.MAX_VALUE, record.amount()));
                items.add(new WarehouseAccess.SlotItem(record.slot(), item, record.displayName(), record.categoryId()));
            }
            return items;
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("读取仓库槽位列表失败(" + player.getName() + "): " + exception.getMessage());
            }
            return List.of();
        }
    }

    @Override
    public @NotNull WarehouseAccess.DepositResult deposit(@NotNull Player player, @NotNull ItemStack itemStack) {
        try {
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), packetFactory.firstPersonalWarehouseId(), itemStack);
            return new WarehouseAccess.DepositResult(result.success(), result.storedAmount(),
                result.success() ? result.remainingAmount() : itemStack.getAmount(), result.message());
        } catch (Exception exception) {
            return new WarehouseAccess.DepositResult(false, 0L, itemStack == null ? 0 : itemStack.getAmount(), "存入失败");
        }
    }

    @Override
    public @NotNull WarehouseAccess.SlotWriteResult setSlot(@NotNull Player player, int slotIndex, @Nullable ItemStack itemStack) {
        try {
            UUID uuid = player.getUniqueId();
            String ownerId = uuid.toString();
            String warehouseId = packetFactory.firstPersonalWarehouseId();
            long unlocked = personalUnlockedSlots(uuid, warehouseId);
            if (slotIndex < 0 || slotIndex >= unlocked) {
                return new WarehouseAccess.SlotWriteResult(false, message("player.invalid-slot", slotIndex));
            }
            Object warehouseLock = warehouseLocks.computeIfAbsent(
                OWNER_PERSONAL + ':' + ownerId + ':' + warehouseId, ignored -> new Object());
            synchronized (warehouseLock) {
                if (itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0) {
                    repository.deleteSlot(OWNER_PERSONAL, ownerId, warehouseId, slotIndex);
                    return new WarehouseAccess.SlotWriteResult(true, "");
                }
                if (isItemLocked(itemStack)) {
                    return new WarehouseAccess.SlotWriteResult(false, message("player.item-locked"));
                }
                if (isBlacklisted(itemStack)) {
                    return new WarehouseAccess.SlotWriteResult(false, message("player.item-blocked"));
                }
                ItemStack prototype = itemStack.clone();
                prototype.setAmount(1);
                byte[] bytes = ItemSerializer.serialize(prototype);
                String rejectReason = validateSerializedItem(player.getName(), prototype, bytes);
                if (rejectReason != null) {
                    return new WarehouseAccess.SlotWriteResult(false, rejectMessage(rejectReason));
                }
                String itemData = Base64.getEncoder().encodeToString(bytes);
                String itemHash = WarehouseTexts.sha256Hex(bytes);
                String displayName = resolveDisplayName(prototype);
                String categoryId = resolveCategory(prototype);
                SearchTokens searchTokens = WarehouseTexts.toSearchTokens(displayName);
                String displayJson = itemJson(prototype);
                long now = System.currentTimeMillis();
                long createdAt = repository.loadSlot(OWNER_PERSONAL, ownerId, warehouseId, slotIndex)
                    .map(SlotItemRecord::createdAt).orElse(now);
                repository.upsertSlot(new SlotItemRecord(
                    OWNER_PERSONAL, ownerId, warehouseId, slotIndex, itemHash, categoryId,
                    displayName, prototype.getType().name(), searchTokens.searchText(),
                    searchTokens.pinyin(), searchTokens.initials(), itemData, displayJson,
                    itemStack.getAmount(), createdAt, now, WarehouseTexts.loreText(prototype)));
                return new WarehouseAccess.SlotWriteResult(true, "");
            }
        } catch (Exception exception) {
            this.logger.warning("写入仓库槽位失败(" + player.getName() + ", " + slotIndex + "): " + exception.getMessage());
            return new WarehouseAccess.SlotWriteResult(false, "写入失败");
        }
    }

    @Override
    public @NotNull WarehouseAccess.WithdrawResult withdraw(@NotNull Player player, int slotIndex, int amount) {
        return withdraw(player, slotIndex, amount, null);
    }

    WarehouseAccess.WithdrawResult withdraw(Player player, int slotIndex, int amount, SlotItemRecord expected) {
        try {
            UUID uuid = player.getUniqueId();
            String ownerId = uuid.toString();
            String warehouseId = expected == null ? packetFactory.firstPersonalWarehouseId() : expected.warehouseId();
            Object warehouseLock = warehouseLocks.computeIfAbsent(
                OWNER_PERSONAL + ':' + ownerId + ':' + warehouseId, ignored -> new Object());
            synchronized (warehouseLock) {
                Optional<SlotItemRecord> slotOpt = repository.loadSlot(OWNER_PERSONAL, ownerId, warehouseId, slotIndex);
                if (slotOpt.isEmpty() || amount <= 0) {
                    return new WarehouseAccess.WithdrawResult(false, 0, null, "槽位为空");
                }
                SlotItemRecord record = slotOpt.get();
                if (expected != null && (!record.itemHash().equals(expected.itemHash())
                    || record.createdAt() != expected.createdAt() || !warehouseId.equals(expected.warehouseId()))) {
                    return new WarehouseAccess.WithdrawResult(false, 0, null, "仓库物品已变化");
                }
                if (record.amount() <= 0) return new WarehouseAccess.WithdrawResult(false, 0, null, "物品数量异常");
                int taken = (int) Math.min(amount, record.amount());
                ItemStack item = deserializeSlotItem(record);
                if (item == null || item.getType().isAir()) {
                    return new WarehouseAccess.WithdrawResult(false, 0, null, "物品损坏");
                }
                item.setAmount(taken);
                if (!repository.applySlotChanges(List.of(new WarehouseRepository.SlotChange(record,
                    taken >= record.amount() ? null : withAmount(record, record.amount() - taken, System.currentTimeMillis())))))
                    return new WarehouseAccess.WithdrawResult(false, 0, null, "槽位已变化，请重试");
                return new WarehouseAccess.WithdrawResult(true, taken, item, "");
            }
        } catch (Exception exception) {
            this.logger.warning("取出仓库槽位失败(" + player.getName() + ", " + slotIndex + "): " + exception.getMessage());
            return new WarehouseAccess.WithdrawResult(false, 0, null, "取出失败");
        }
    }

    @Override
    public @NotNull WarehouseAccess.PurchaseResult purchaseSlot(@NotNull Player player) {
        try {
            PurchaseOutcome outcome = purchasePersonalSlots(player, packetFactory.firstPersonalWarehouseId(), 1L);
            return new WarehouseAccess.PurchaseResult(outcome.success(), outcome.newUnlocked(), outcome.message());
        } catch (Exception exception) {
            this.logger.warning("购买仓库槽位失败(" + player.getName() + "): " + exception.getMessage());
            return new WarehouseAccess.PurchaseResult(false, 0L, "购买失败");
        }
    }

    @Override
    public @NotNull WarehouseAccess.SlotPrice slotPrice(@NotNull Player player, int amount) {
        try {
            WarehouseDefinition definition = configuration.warehouse(packetFactory.firstPersonalWarehouseId());
            WarehouseModuleConfiguration.UpgradeCost price = definition == null ? null : definition.slotPrice();
            if (price == null) {
                return new WarehouseAccess.SlotPrice("", BigDecimal.ZERO);
            }
            return new WarehouseAccess.SlotPrice(
                price.currencyId(), price.amount().multiply(BigDecimal.valueOf(Math.max(0, amount))));
        } catch (Exception exception) {
            return new WarehouseAccess.SlotPrice("", BigDecimal.ZERO);
        }
    }

    @Override
    public long grantSlots(@NotNull OfflinePlayer target, int amount) {
        try {
            String warehouseId = packetFactory.firstPersonalWarehouseId();
            WarehouseDefinition definition = configuration.warehouse(warehouseId);
            WarehouseRecord record = personalWarehouseMap(target.getUniqueId()).get(warehouseId);
            if (definition == null || record == null || amount <= 0) {
                return -1L;
            }
            long unlocked = definition.baseSlots() + Math.max(0L, record.extraSlots());
            long granted = Math.min(amount, Math.max(0L, definition.maxSlots() - unlocked));
            if (granted <= 0) {
                return 0L;
            }
            repository.addPersonalExtraSlots(target.getUniqueId(), warehouseId, granted, System.currentTimeMillis());
            return granted;
        } catch (Exception exception) {
            return -1L;
        }
    }

    @Override
    public boolean resetSlots(@NotNull OfflinePlayer target) {
        try {
            repository.setPersonalExtraSlots(target.getUniqueId(), packetFactory.firstPersonalWarehouseId(), 0L, System.currentTimeMillis());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
