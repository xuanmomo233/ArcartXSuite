package xuanmo.arcartxsuite.mail.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.BiConsumer;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.config.MailPlayerSendConfiguration;
import xuanmo.arcartxsuite.mail.config.MailUiConfiguration;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailOperationResult;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailPlayerProfile;
import xuanmo.arcartxsuite.mail.model.MailPresetCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailPresetDefinition;
import xuanmo.arcartxsuite.mail.model.MailSendQuote;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.storage.MailRepository;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimPhase;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimMail;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimProgress;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ItemClaim;
import xuanmo.arcartxsuite.mail.util.MailItemSerializer;
import xuanmo.arcartxsuite.mail.util.MailItemPreview;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import java.util.logging.Logger;

public final class MailService implements Listener {

    public static final String CLIENT_PACKET_ID = "AXS_MAIL";
    private static final String INBOX_UI_RESOURCE_PATH = "arcartx/ui/mail_inbox.yml";
    private static final String COMPOSE_UI_RESOURCE_PATH = "arcartx/ui/mail_compose.yml";
    private static final String LOGS_UI_RESOURCE_PATH = "arcartx/ui/mail_logs.yml";
    private static final String ADMIN_UI_RESOURCE_PATH = "arcartx/ui/mail_admin.yml";
    private static final String INBOX_UI_FILE_PATH = "ui/mail_inbox.yml";
    private static final String COMPOSE_UI_FILE_PATH = "ui/mail_compose.yml";
    private static final String LOGS_UI_FILE_PATH = "ui/mail_logs.yml";
    private static final String ADMIN_UI_FILE_PATH = "ui/mail_admin.yml";
    private static final int ADMIN_ATTACHMENT_SLOTS_DEFAULT = 18;
    private static final String STARTER_PRESET_RESOURCE_PATH = "mail/presets/starter.yml";
    private static final int INBOX_PAGE_SIZE = 6;
    private static final int LOG_PAGE_SIZE = 200;
    private static final int CLAIM_PAGE_SIZE = 32;
    private static final int MAX_CLAIM_EFFECTS_PER_TICK = 4;
    private static final int MAX_CDK_REDEMPTIONS = 32;
    private static final long CLAIM_CALLBACK_BUDGET_NANOS = 2_000_000L;
    private static final NamespacedKey CLAIM_ITEM_RECEIPT = new NamespacedKey("arcartxsuite", "mail_item_receipt");
    private static final NamespacedKey COMPOSE_ITEM_RECEIPT = new NamespacedKey("arcartxsuite", "mail_compose_items");
    private static final NamespacedKey CLAIM_ITEM_BATCH_RECEIPT = new NamespacedKey("arcartxsuite", "mail_item_batch");
    private static final String MESSAGE_PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    /**
     * UI 资源导出函数。输入：(resourcePath, relativeUiPath, overwrite) -> File
     */
    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    /**
     * Bundled 资源导出函数。输入：(resourcePath, targetFile)
     */
    @FunctionalInterface
    public interface BundledResourceWriter {
        void write(String resourcePath, File targetFile);
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File baseDataDir;
    private final MailModuleConfiguration configuration;
    private final MailRepository repository;
    private final PacketBridgeAPI bridge;
    private final PacketGuardAPI packetGuard;
    private final UiResourceExporter uiResourceExporter;
    private final BundledResourceWriter bundledResourceWriter;
    private final xuanmo.arcartxsuite.api.bridge.ContentModerator contentModerator;
    private final BiConsumer<String, Player> signalDispatcher;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final CrossServerAPI crossServer;
    private final MessageProvider messages;
    private final Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry;
    private final xuanmo.arcartxsuite.api.icon.IconResolver iconResolver;
    private final ItemBridgeAPI itemStackBridge;
    private volatile Map<String, MailPresetDefinition> presets = new ConcurrentHashMap<>();
    private CompletableFuture<MailOperationResult> presetReload;
    private final Map<UUID, Long> selectedMailIds = new ConcurrentHashMap<>();
    private final Map<UUID, MailInboxQuery> inboxQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> logPages = new ConcurrentHashMap<>();
    private final Map<UUID, ComposeSession> composeSessions = new ConcurrentHashMap<>();
    private final Set<UUID> draftIo = new java.util.HashSet<>();
    private final Map<UUID, AuthSnapshot> pendingDraftRecovery = new HashMap<>();
    private final Map<UUID, Object> composeOpenRequests = new HashMap<>();
    private final Map<UUID, ViewRequest> inboxReads = new HashMap<>();
    private final Map<UUID, ViewRequest> logReads = new HashMap<>();
    private record ViewRequest(boolean initPacket) { }
    private final Set<UUID> composePasswordPanels = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AdminEditSession> adminEditSessions = new ConcurrentHashMap<>();
    private final Set<UUID> cdkRedemptions = ConcurrentHashMap.newKeySet();
    // CDK 兑换限流状态：玩家 UUID -> 限流条目
    private final Map<UUID, CdkRateLimitEntry> cdkRateLimitMap = new ConcurrentHashMap<>();
    private final Set<UUID> inboxViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> logViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ClaimJob> claimJobs = new ConcurrentHashMap<>();
    private final Set<UUID> deletingInboxes = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Runnable> claimEffects = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> claimSaves = new ConcurrentLinkedQueue<>();

    private BukkitTask cleanupTask;
    private BukkitTask claimTask;
    private Path playerData;
    private MailDraftAttachments draftAttachments;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean();
    private volatile boolean active;
    private volatile boolean stopping;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor background =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0, Integer.MAX_VALUE, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<>(), work -> new MailWorker(this, work));
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback claimPulse =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::drainClaimEffects);
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback cleanupPulse =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::runCleanup);
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback capabilityCalls =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});

    private static final class MailWorker extends Thread {
        private final MailService owner;
        MailWorker(MailService owner, Runnable work) { super(work,"AXS-Mail-IO"); this.owner=owner; setDaemon(true); }
    }
    private CrossServerChannel crossServerChannel;
    private String inboxUiId;
    private String composeUiId;
    private String logsUiId;
    private String adminUiId;
    private String registeredInboxUiId;
    private String registeredComposeUiId;
    private String registeredLogsUiId;
    private String registeredAdminUiId;
    private NamespacedKey adminTemplateKey;

    public MailService(
        JavaPlugin plugin,
        Logger logger,
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this(plugin, logger, plugin.getDataFolder(), configuration, repository, bridge, packetGuard,
            uiResourceExporter, bundledResourceWriter, signalDispatcher, currencyBridgeManager, crossServer, null, null, null, vanillaItemNameBridge, null, null, null);
    }

    public MailService(
        JavaPlugin plugin,
        Logger logger,
        File baseDataDir,
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this(plugin, logger, baseDataDir, configuration, repository, bridge, packetGuard,
            uiResourceExporter, bundledResourceWriter, signalDispatcher, currencyBridgeManager, crossServer, null, null, null, vanillaItemNameBridge, null, null, null);
    }

    public MailService(
        JavaPlugin plugin,
        Logger logger,
        File baseDataDir,
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this(
            plugin,
            logger,
            baseDataDir,
            configuration,
            repository,
            bridge,
            packetGuard,
            uiResourceExporter,
            bundledResourceWriter,
            signalDispatcher,
            currencyBridgeManager,
            crossServer,
            messages,
            null,
            itemSourceRegistry,
            vanillaItemNameBridge,
            null,
            null,
            null
        );
    }

    public MailService(
        JavaPlugin plugin,
        Logger logger,
        File baseDataDir,
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer,
        MessageProvider messages,
        Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
        xuanmo.arcartxsuite.api.icon.IconResolver iconResolver,
        ItemBridgeAPI itemStackBridge,
        xuanmo.arcartxsuite.api.bridge.ContentModerator contentModerator
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.baseDataDir = baseDataDir;
        this.configuration = configuration;
        this.repository = repository;
        this.bridge = bridge;
        this.packetGuard = packetGuard;
        this.uiResourceExporter = uiResourceExporter;
        this.bundledResourceWriter = bundledResourceWriter;
        this.signalDispatcher = signalDispatcher;
        this.currencyBridgeManager = currencyBridgeManager;
        this.crossServer = crossServer;
        this.messages = messages;
        this.secondaryPasswordSupplier = secondaryPasswordSupplier;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.itemSourceRegistry = itemSourceRegistry;
        this.iconResolver = iconResolver;
        this.itemStackBridge = itemStackBridge;
        this.contentModerator = contentModerator != null ? contentModerator : new xuanmo.arcartxsuite.mail.NoopContentModerator();
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    public java.util.concurrent.CompletionStage<Void> prepareStorage(xuanmo.arcartxsuite.api.storage.ModuleStorage scope) throws IOException {
        var initial = readPresets();
        return scope.initialize(() -> {
            repository.initialize();
            syncPresetCdks(initial.values());
            presets = new ConcurrentHashMap<>(initial);
        });
    }

    public void start() throws Exception {
        playerData = Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("playerdata");
        draftAttachments = new MailDraftAttachments(baseDataDir.toPath().resolve("attachment-recovery"), playerData);
        inboxUiId = configuration.ui().inboxUiId();
        composeUiId = configuration.ui().composeUiId();
        logsUiId = configuration.ui().logsUiId();
        adminUiId = configuration.ui().adminUiId();
        bindUiResources();
        adminTemplateKey = new NamespacedKey(plugin, "admin_template");

        // 注册 compose/admin UI 关闭回调，关闭时归还附件
        if (bridge != null) {
            if (composeUiId != null) {
                bridge.registerUiCloseCallback(composeUiId, player -> {
                    ComposeSession session = takeReturnableDraft(player.getUniqueId());
                    composePasswordPanels.remove(player.getUniqueId());
                    if (session != null && !session.sent()) {
                        returnComposeItems(player, session);
                    }
                });
            }
            if (adminUiId != null) {
                bridge.registerUiCloseCallback(adminUiId, player -> {
                    AdminEditSession adminSession = adminEditSessions.remove(player.getUniqueId());
                    if (adminSession != null) {
                        returnAdminItems(player, adminSession);
                    }
                });
            }
        }

        logUnavailableCurrencyProviders();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var profiles = Bukkit.getOnlinePlayers().stream().map(player -> profileSnapshot(player, false)).toList();
        if (!profiles.isEmpty()) background.execute(() -> profiles.forEach(this::saveProfile));

        active = true;
        claimTask = Bukkit.getScheduler().runTaskTimer(plugin, claimPulse, 1L, 1L);
        long cleanupInterval = configuration.retention().cleanupIntervalTicks();
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, cleanupPulse, cleanupInterval, cleanupInterval);
        crossServerChannel = crossServer.openChannel(
            "mail",
            configuration.crossServer(),
            delivery -> handleCrossServerMessage(delivery.payload())
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            recoverAdmittedDraft(player, AuthAccess.snapshot(player.getUniqueId()));
        }
    }

    public void shutdown() {
        if (stopping) return;
        stopping = true;
        active = false;
        if (presetReload != null) {
            presetReload.complete(MailOperationResult.failure(message("player.module-down")));
            presetReload = null;
        }
        capabilityCalls.close();
        claimPulse.close();
        cleanupPulse.close();
        claimJobs.values().forEach(job -> job.disconnected = true);
        try {
        if (claimTask != null) { claimTask.cancel(); claimTask = null; }
        discardClaimCallbacks(claimEffects);
        discardClaimCallbacks(claimSaves);
        claimJobs.values().forEach(job -> job.result.complete(MailOperationResult.failure(message("player.module-down"))));
        claimJobs.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }

        for (AdminEditSession session : List.copyOf(adminEditSessions.values())) {
            Player player = Bukkit.getPlayer(session.playerUuid());
            if (player != null && player.isOnline()) {
                returnAdminItems(player, session);
            }
        }

        composeSessions.clear();
        draftIo.clear();
        pendingDraftRecovery.clear();
        composeOpenRequests.clear();
        inboxReads.clear();
        logReads.clear();
        composePasswordPanels.clear();
        adminEditSessions.clear();
        selectedMailIds.clear();
        inboxQueries.clear();
        logPages.clear();
        inboxViewers.clear();
        logViewers.clear();
        HandlerList.unregisterAll(this);

        if (bridge != null) {
            if (composeUiId != null) {
                bridge.unregisterUiCloseCallback(composeUiId);
            }
            if (adminUiId != null) {
                bridge.unregisterUiCloseCallback(adminUiId);
            }
        }

        unregisterBoundUi(registeredInboxUiId);
        unregisterBoundUi(registeredComposeUiId);
        unregisterBoundUi(registeredLogsUiId);
        unregisterBoundUi(registeredAdminUiId);
        registeredInboxUiId = null;
        registeredComposeUiId = null;
        registeredLogsUiId = null;
        registeredAdminUiId = null;

        } finally {
            CompletableFuture.allOf(claimPulse.stopped().toCompletableFuture(),cleanupPulse.stopped().toCompletableFuture(),
                capabilityCalls.stopped().toCompletableFuture()).whenComplete((ignored,failure) -> background.shutdown());
        }
    }

    public boolean isStopping() { return stopping; }
    public <T> T callApi(java.util.function.Supplier<T> operation, T unavailable) { return capabilityCalls.call(operation,unavailable); }
    public java.util.concurrent.CompletionStage<Void> databaseStopped() { return background.stopped(); }

    private void onMain(Runnable action) {
        if (!active) return;
        Bukkit.getScheduler().runTask(plugin, () -> capabilityCalls.call(() -> {
            if (active) action.run();
            return null;
        }, null));
    }

    /**
     * 返回已注册到 ArcartX 的 UI ID 列表（供 Module 记录到基类生命周期管理）。
     *
     * @return 已注册的 UI ID 列表，未注册的条目自动跳过
     */
    public java.util.List<String> registeredUiIds() {
        java.util.List<String> ids = new java.util.ArrayList<>(4);
        if (registeredInboxUiId != null) ids.add(registeredInboxUiId);
        if (registeredComposeUiId != null) ids.add(registeredComposeUiId);
        if (registeredLogsUiId != null) ids.add(registeredLogsUiId);
        if (registeredAdminUiId != null) ids.add(registeredAdminUiId);
        return java.util.List.copyOf(ids);
    }

    public String inboxUiId() {
        return inboxUiId;
    }

    public String composeUiId() {
        return composeUiId;
    }

    public String adminUiId() {
        return adminUiId;
    }

    /** 管理员 UI 预设编辑的最大物品附件槽位数（来自配置，默认 9）。 */
    public int adminAttachmentSlots() {
        int configured = configuration.adminMaxAttachments();
        return configured > 0 ? configured : ADMIN_ATTACHMENT_SLOTS_DEFAULT;
    }

    public int presetCount() {
        return presets.size();
    }

    /** CDK 兑换是否要求 arcartxsuite.mail.cdk 权限。 */
    public boolean isCdkPermissionRequired() {
        return configuration.cdk().requirePermission();
    }

    public List<String> presetIds() {
        return presets.keySet().stream().sorted().toList();
    }

    /**
     * 获取预设定义，如果不存在返回 null。
     */
    public MailPresetDefinition getPreset(String presetId) {
        return preset(presetId);
    }

    /**
     * 返回所有预设定义的不可变列表（按 ID 排序）。
     */
    public List<MailPresetDefinition> getPresetDefinitions() {
        return presets.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();
    }

    /** 预设 id 合法字符集——id 直接拼接文件名，必须拒绝 /、.. 等路径穿越字符 */
    private static final java.util.regex.Pattern PRESET_ID_PATTERN =
        java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /**
     * 创建或更新预设并保存到 YAML 文件。
     */
    public MailOperationResult savePreset(MailPresetDefinition preset) {
        if (isPresetReloading() || !cdkRedemptions.isEmpty()) return MailOperationResult.failure(message("admin.preset-reload-busy"));
        if (preset == null || preset.id() == null || preset.id().isBlank()) {
            return MailOperationResult.failure(message("player.preset-id-required"));
        }
        if (!PRESET_ID_PATTERN.matcher(preset.id().trim()).matches()) {
            return MailOperationResult.failure(message("admin.preset-id-invalid", preset.id()));
        }
        try {
            File directory = new File(baseDataDir, configuration.presetsDirectory());
            MailPresetLoader.savePreset(directory, preset);
            presets.put(preset.id().trim().toLowerCase(Locale.ROOT), preset);
            return MailOperationResult.success(message("admin.preset-saved", preset.id()));
        } catch (Exception exception) {
            this.logger.warning("保存预设失败: " + exception.getMessage());
            return MailOperationResult.failure(message("admin.preset-save-failed", exception.getMessage()));
        }
    }

    /**
     * 删除预设（从内存和 YAML 文件）。
     */
    public MailOperationResult deletePreset(String presetId) {
        if (isPresetReloading() || !cdkRedemptions.isEmpty()) return MailOperationResult.failure(message("admin.preset-reload-busy"));
        if (presetId == null || presetId.isBlank()) {
            return MailOperationResult.failure(message("player.preset-id-required"));
        }
        String normalized = presetId.trim().toLowerCase(Locale.ROOT);
        if (!PRESET_ID_PATTERN.matcher(normalized).matches()) {
            return MailOperationResult.failure(message("admin.preset-id-invalid", presetId));
        }
        MailPresetDefinition removed = presets.remove(normalized);
        if (removed == null) {
            return MailOperationResult.failure(message("admin.preset-not-found", presetId));
        }
        try {
            File directory = new File(baseDataDir, configuration.presetsDirectory());
            MailPresetLoader.deletePresetFile(directory, normalized);
            return MailOperationResult.success(message("admin.preset-deleted", presetId));
        } catch (Exception exception) {
            this.logger.warning("删除预设文件失败: " + exception.getMessage());
            return MailOperationResult.success(message("admin.preset-memory-removed-file-delete-failed", exception.getMessage()));
        }
    }

    /** 主线程解析含 Bukkit 物品的预设，后台同步兑换码，提交后替换当前预设。 */
    public java.util.concurrent.CompletionStage<MailOperationResult> reloadPresets() {
        if (stopping) return CompletableFuture.completedFuture(MailOperationResult.failure(message("player.module-down")));
        if (isPresetReloading() || !cdkRedemptions.isEmpty()) {
            return CompletableFuture.completedFuture(MailOperationResult.failure(message("admin.preset-reload-busy")));
        }
        Map<String, MailPresetDefinition> replacement;
        try {
            replacement = readPresets();
        } catch (Exception | LinkageError failure) {
            return CompletableFuture.completedFuture(presetReloadFailed(failure));
        }
        var result = new CompletableFuture<MailOperationResult>();
        presetReload = result;
        try {
            background.execute(() -> {
                MailOperationResult outcome;
                try {
                    syncPresetCdks(replacement.values());
                    outcome = MailOperationResult.success(message("admin.preset-reloaded", replacement.size()));
                } catch (Exception | LinkageError failure) {
                    outcome = presetReloadFailed(failure);
                }
                var completed = outcome;
                if (stopping) return;
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (presetReload != result || stopping) return;
                        presetReload = null;
                        if (completed.success()) presets = new ConcurrentHashMap<>(replacement);
                        result.complete(completed);
                    });
                } catch (RuntimeException | LinkageError failure) {
                    result.complete(presetReloadFailed(failure));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException failure) {
            presetReload = null;
            result.complete(presetReloadFailed(failure));
        }
        return result.copy();
    }

    private MailOperationResult presetReloadFailed(Throwable failure) {
        logger.warning("重新加载预设失败: " + failure.getMessage());
        return MailOperationResult.failure(message("admin.preset-reload-failed", failure.getMessage()));
    }

    private boolean isPresetReloading() { return presetReload != null && !presetReload.isDone(); }

    /**
     * 为管理员玩家打开 Admin UI（ArcartX UI）并发送预设列表。
     */
    public void openAdminUi(Player player) {
        if (player == null || !player.isOnline()) return;
        if (adminUiId == null) {
            player.sendMessage(MESSAGE_PREFIX + ChatColor.RED + message("player.admin-ui-unregistered"));
            return;
        }

        AdminEditSession previous = adminEditSessions.remove(player.getUniqueId());
        if (previous != null) {
            returnAdminItems(player, previous);
        }

        closeOtherMailUis(player);

        AdminEditSession session = new AdminEditSession(player.getUniqueId(), null);
        adminEditSessions.put(player.getUniqueId(), session);
        bridge.openUi(player, adminUiId);

        onMain(() -> {
            if (player.isOnline()) {
                sendAdminPresetList(player);
            }
        });
    }

    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    public int composeSessionCount() {
        return composeSessions.size();
    }

    public MailModuleConfiguration configuration() {
        return configuration;
    }

    public MailRepository repository() {
        return repository;
    }

    public MailMailboxStats loadStats(UUID playerUuid) {
        try {
            return repository.loadStats(playerUuid);
        } catch (Exception exception) {
            this.logger.warning("读取邮箱统计失败: " + exception.getMessage());
            return MailMailboxStats.empty();
        }
    }

    public int unreadCount(UUID playerUuid) {
        return loadStats(playerUuid).unreadCount();
    }

    public MailOperationResult sendSystemMail(
        UUID playerUuid,
        String logContent,
        String subject,
        String body,
        List<MailAttachment> attachments,
        List<String> claimCommands,
        Instant expiresAt,
        String senderName
    ) {
        if (playerUuid == null) {
            return MailOperationResult.failure(message("player.recipient-required"));
        }

        MailMessage message = systemMail(playerUuid, subject, body, attachments, claimCommands, expiresAt, senderName);
        MailOperationResult result = sendMailInternal(playerUuid, logContent, message, true);
        if (result.success()) {
            appendLog(playerUuid, "system", (logContent == null || logContent.isBlank()) ? message.subject() : logContent);
        }
        return result;
    }

    MailMessage systemMail(UUID playerUuid, String subject, String body, List<MailAttachment> attachments,
                           List<String> claimCommands, Instant expiresAt, String senderName) {
        Instant now = Instant.now();
        return new MailMessage(
            0L,
            playerUuid,
            null,
            senderName != null && !senderName.isBlank() ? senderName : "System",
            MailSourceType.SYSTEM,
            "",
            "",
            MailTexts.crop(subject, configuration.playerSend().subjectMaxLength()),
            MailTexts.crop(body, configuration.playerSend().bodyMaxLength()),
            MailStatus.UNREAD,
            MailTexts.copyAttachments(attachments),
            MailTexts.copyStrings(claimCommands),
            List.of(),
            now,
            expiresAt,
            now,
            null,
            null
        );
    }

    void notifyDurableMail(MailMessage message) {
        Player owner = Bukkit.getPlayer(message.ownerUuid());
        if (owner != null && owner.isOnline()) {
            touchProfile(owner, false);
            refreshPlayerViews(owner, false);
            sendMailNotifyCard(owner, message);
        }
        publishRefresh(message.ownerUuid());
    }

    /**
     * compose/admin 不再使用 Bukkit 容器，无需关闭容器。
     * ArcartX 的 openUi 会自动替换当前打开的 ArcartX UI。
     */
    private void closeOtherMailUis(Player player) {
        // 无需关闭 Bukkit 容器，compose/admin 不再使用容器
    }

    public MailOperationResult openInbox(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }
        composeOpenRequests.remove(player.getUniqueId());
        try {
            touchProfile(player, false);
            inboxQueries.putIfAbsent(player.getUniqueId(), new MailInboxQuery(MailInboxFilter.ALL, 1, INBOX_PAGE_SIZE));
            inboxViewers.add(player.getUniqueId());
            logViewers.remove(player.getUniqueId());
            // 先打开 ArcartX UI（不鼠标重置），再延迟关闭 Bukkit 容器（compose/admin 会话）
            bridge.openUi(player, inboxUiId);
            onMain(() -> closeOtherMailUis(player));
            refreshInbox(player, true);
            return MailOperationResult.success(message("player.mailbox-opened"));
        } catch (Exception exception) {
            this.logger.warning("打开邮箱失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.mailbox-open-failed"));
        }
    }

    public MailOperationResult openCompose(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }
        UUID owner = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(owner);
        if (!currentMailAdmission(player, admission)) {
            return MailOperationResult.failure(message("player.compose-session-expired"));
        }

        // 归还旧会话附件
        ComposeSession previous = composeSessions.get(owner);
        if (previous != null && previous.pendingSend != null) {
            MailOperationResult resolved = resolveComposeSend(player, previous);
            if (previous.pendingSend != null) return resolved;
            sendPlayerResult(player, resolved);
        }
        if (draftIo.contains(owner)) return attachmentsProcessing();
        takeReturnableDraft(owner);
        composePasswordPanels.remove(owner);
        Object request = new Object();
        composeOpenRequests.put(owner, request);
        recoverDraftAttachments(player, admission, result -> {
            if (!composeOpenRequests.remove(owner, request) || !currentMailAdmission(player, admission)) return;
            sendPlayerResult(player, result.success() ? openComposeUi(player) : result);
        });
        return attachmentsProcessing();
    }

    private MailOperationResult openComposeUi(Player player) {
        closeOtherMailUis(player);
        inboxViewers.remove(player.getUniqueId());
        logViewers.remove(player.getUniqueId());

        // 创建新会话（无 Bukkit 容器）
        ComposeSession session = new ComposeSession(UUID.randomUUID(), player.getUniqueId(), false, configuration.playerSend().feeCurrency());
        composeSessions.put(player.getUniqueId(), session);

        // 打开 ArcartX UI
        bridge.openUi(player, composeUiId);

        // 延迟 1 tick 发送 init 包（含背包和附件数据）
        onMain(() -> {
            if (player.isOnline()) {
                SecondaryPasswordAccess passwordAccess = secondaryPasswordAccess();
                if (passwordAccess == null) {
                    sendComposeInitPacket(player, session, SecondaryPasswordAccess.Status.LOCKED);
                    return;
                }
                passwordAccess.statusAsync(player).whenComplete((status, failure) -> {
                    if (!active) return;
                    onMain(() -> {
                        if (currentCompose(player, session)) {
                            sendComposeInitPacket(player, session, failure == null && status != null
                                ? status : SecondaryPasswordAccess.Status.LOCKED);
                        }
                    });
                });
            }
        });
        return MailOperationResult.success(message("player.compose-opened"));
    }

    private void sendComposeInitPacket(Player player, ComposeSession session, SecondaryPasswordAccess.Status status) {
        // 查询失败时也保留密码面板，发送端会重新检查。
        if (!status.unlocked()) {
            composePasswordPanels.add(player.getUniqueId());
        }
        Map<String, Object> composePacket = MailComposePacketFactory.buildInit(
            session.sessionId(),
            configuration,
            currencyBridgeManager,
            messages,
            calculateComposeQuote(configuration.playerSend(), buildAttachmentItemMap(session.attachments()), session.attachments().size(), session.feeCurrency()),
            configuration.playerSend().maxAttachments(),
            session.attachments().size(),
            composePasswordPanels.contains(player.getUniqueId()),
            session.actionToken()
        );
        composePacket.put("is_admin", player.hasPermission("arcartxsuite.admin"));
        // 序列化附件（背包槽位使用 ~Backpack 类型，由客户端自动显示，无需发送 backpack_jsons）
        composePacket.put("attachment_jsons", serializeAttachments(session.attachments()));
        composePacket.put("attachment_amounts", MailItemPreview.amounts(session.attachments()));
        bridge.sendPacket(player, composeUiId, "init", composePacket);
    }

    private Map<String, Object> serializeAttachments(List<ItemStack> attachments) {
        Map<String, Object> jsons = new LinkedHashMap<>();
        if (attachments == null) return jsons;
        for (int i = 0; i < attachments.size(); i++) {
            ItemStack item = attachments.get(i);
            if (item != null && !item.getType().isAir()) {
                jsons.put(Integer.toString(i), iconResolver.itemToJson(
                    MailItemPreview.copyForIcon(item)));
            }
        }
        return jsons;
    }

    private Map<String, BigDecimal> buildAttachmentItemMap(List<ItemStack> attachments) {
        // compose 物品附件不产生货币金额，货币附件通过 vault 金额单独传入
        return Map.of();
    }

    public void claimAll(Player player) {
        if (player == null) return;
        startClaim(player, 0, true).thenAccept(result -> {
            if (player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) sendPlayerResult(player, result);
        });
    }

    public void deleteAll(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!active) {
            sendPlayerResult(player, MailOperationResult.failure(message("player.module-down")));
            return;
        }
        UUID owner = player.getUniqueId();
        if (!deletingInboxes.add(owner)) return;
        boolean allowUnclaimed = configuration.retention().allowDeleteWithUnclaimedAttachments();
        Instant now = Instant.now();
        try {
            background.execute(() -> {
                int deleted;
                try {
                    deleted = repository.deleteInbox(owner, allowUnclaimed, now);
                } catch (Exception failure) {
                    logger.warning("批量删除邮件失败: " + failure.getMessage());
                    onMain(() -> {
                        if (isCurrentClaimPlayer(player, owner)) {
                            sendPlayerResult(player, MailOperationResult.failure(message("player.delete-all-failed")));
                        }
                    });
                    return;
                } finally {
                    deletingInboxes.remove(owner);
                }
                onMain(() -> {
                    if (deleted > 0) publishRefresh(owner);
                    if (!isCurrentClaimPlayer(player, owner)) return;
                    refreshPlayerViews(player, false);
                    sendPlayerResult(player, deleted > 0
                        ? MailOperationResult.success(message("player.delete-all-success", deleted))
                        : MailOperationResult.failure(message("player.delete-all-empty")));
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            deletingInboxes.remove(owner);
            sendPlayerResult(player, MailOperationResult.failure(message("player.module-down")));
        }
    }

    public void redeemCdk(Player player, String rawCode, java.util.function.Consumer<MailOperationResult> reply) {
        if (isPresetReloading()) {
            reply.accept(MailOperationResult.failure(message("admin.preset-reload-busy")));
            return;
        }
        if (!active || player == null || !player.isOnline()) {
            reply.accept(MailOperationResult.failure(message("player.target-offline")));
            return;
        }
        if (isCdkPermissionRequired() && !player.hasPermission("arcartxsuite.mail.cdk")) {
            reply.accept(MailOperationResult.failure(message("player.cdk-no-permission")));
            return;
        }
        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            reply.accept(MailOperationResult.failure(message("player.cdk-required")));
            return;
        }

        UUID owner = player.getUniqueId();
        var admission = AuthAccess.snapshot(owner);
        if (!admission.admitted()) {
            reply.accept(claimStopped());
            return;
        }
        Instant now = Instant.now();
        var rateLimit = cdkRateLimitMap.computeIfAbsent(owner, ignored -> new CdkRateLimitEntry());
        synchronized (rateLimit) {
            if (rateLimit.lockoutUntil != null && rateLimit.lockoutUntil.isAfter(now)) {
                reply.accept(MailOperationResult.failure(message("player.cdk-locked", TIME_FORMATTER.format(rateLimit.lockoutUntil))));
                return;
            }
            if (rateLimit.lastFailAt != null && configuration.cdk().cooldownSeconds() > 0) {
                Instant cooldownEnd = rateLimit.lastFailAt.plusSeconds(configuration.cdk().cooldownSeconds());
                if (cooldownEnd.isAfter(now)) {
                    reply.accept(MailOperationResult.failure(message("player.cdk-cooldown", TIME_FORMATTER.format(cooldownEnd))));
                    return;
                }
            }
        }
        if (cdkRedemptions.size() >= MAX_CDK_REDEMPTIONS || !cdkRedemptions.add(owner)) {
            reply.accept(MailOperationResult.failure(message("player.cdk-busy")));
            return;
        }
        try {
            var profile = profileSnapshot(player, false);
            var view = new CdkView(inboxViewers.contains(owner) ? inboxQueries.get(owner) : null,
                selectedMailIds.getOrDefault(owner, 0L), logViewers.contains(owner) ? logPages.getOrDefault(owner, 1) : null);
            background.execute(() -> {
                try {
                    if (!active || !sameCdkSession(admission)) return;
                    processCdk(player, profile, admission, view, code, reply);
                } finally {
                    cdkRedemptions.remove(owner);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            cdkRedemptions.remove(owner);
            reply.accept(MailOperationResult.failure(message("player.module-down")));
        }
    }

    private boolean sameCdkSession(AuthSnapshot accepted) {
        var current = AuthAccess.snapshot(accepted.playerId());
        return current.admitted() && current.generation() == accepted.generation()
            && java.util.Objects.equals(current.connectionId(), accepted.connectionId());
    }

    private void processCdk(Player player, MailPlayerProfile profile, AuthSnapshot admission, CdkView view,
                            String code, java.util.function.Consumer<MailOperationResult> reply) {
        UUID owner = profile.playerUuid();
        Instant now = Instant.now();
        MailRepository.CdkRedemption redemption;
        try {
            redemption = repository.redeemCdk(code, owner, now, presetId -> {
                var preset = preset(presetId);
                return preset == null || !preset.enabled() ? null
                    : buildPresetMail(owner, preset, MailSourceType.CDK, presetId, code, "CDK", now);
            });
        } catch (Exception | LinkageError failure) {
            logger.warning("兑换 CDK 失败: " + failure.getMessage());
            replyCdk(player, admission, reply, MailOperationResult.failure(message("player.cdk-claim-failed")));
            return;
        }
        if (!redemption.success()) {
            onCdkRedeemFailure(owner, now);
            replyCdk(player, admission, reply, MailOperationResult.failure(message(redemption.rejectionKey())));
            return;
        }

        var rateLimit = cdkRateLimitMap.get(owner);
        if (rateLimit != null) synchronized (rateLimit) {
            rateLimit.failCount = 0;
            rateLimit.lastFailAt = null;
            rateLimit.lockoutUntil = null;
        }
        var mail = redemption.mail();
        saveProfile(profile);
        try {
            repository.appendLog(new MailLogEntry(0L, owner, "cdk", "收到邮件: " + mail.subject(), now));
            repository.appendLog(new MailLogEntry(0L, owner, "cdk", "兑换 CDK " + code + "，奖励已投递到邮箱。", now));
        } catch (SQLException failure) {
            logger.warning("写入 CDK 兑换日志失败: " + failure.getMessage());
        }
        MailRepository.InboxView inbox = null;
        MailPage<MailLogEntry> logs = null;
        try {
            if (view.inbox() != null) inbox = repository.loadInboxView(owner, view.inbox(), view.selected());
            if (view.logs() != null) logs = repository.loadLogPage(owner, view.logs(), LOG_PAGE_SIZE);
        } catch (SQLException failure) {
            logger.warning("更新 CDK 兑换后的邮箱视图失败: " + failure.getMessage());
        }
        var loadedInbox = inbox;
        var loadedLogs = logs;
        onMain(() -> {
            publishRefresh(owner);
            if (!isCurrentClaimPlayer(player, owner) || !sameCdkSession(admission)) return;
            reply.accept(MailOperationResult.success(message("player.cdk-claim-success")));
            sendMailNotifyCard(player, mail);
            if (loadedInbox != null && inboxViewers.contains(owner)
                && view.inbox().equals(inboxQueries.get(owner)) && view.selected() == selectedMailIds.getOrDefault(owner, 0L)) {
                sendInboxView(player, view.inbox(), loadedInbox, false);
            }
            if (loadedLogs != null && logViewers.contains(owner) && view.logs().equals(logPages.getOrDefault(owner, 1))) {
                sendLogView(player, loadedLogs, false);
            }
            if (signalDispatcher != null) signalDispatcher.accept("cdk_redeemed", player);
        });
    }

    private void replyCdk(Player player, AuthSnapshot admission, java.util.function.Consumer<MailOperationResult> reply,
                          MailOperationResult result) {
        onMain(() -> {
            if (isCurrentClaimPlayer(player, admission.playerId()) && sameCdkSession(admission)) reply.accept(result);
        });
    }

    private record CdkView(MailInboxQuery inbox, long selected, Integer logs) {}

    /**
     * CDK 兑换失败时更新限流状态：增加失败计数，记录失败时间，达到阈值时锁定。
     */
    private void onCdkRedeemFailure(UUID playerId, Instant now) {
        CdkRateLimitEntry entry = cdkRateLimitMap.get(playerId);
        if (entry == null) return;
        synchronized (entry) {
            entry.failCount++;
            entry.lastFailAt = now;
            int maxFail = configuration.cdk().maxFailAttempts();
            if (maxFail > 0 && entry.failCount >= maxFail) {
                entry.lockoutUntil = now.plusSeconds(configuration.cdk().lockoutMinutes() * 60L);
                entry.failCount = 0;
            }
        }
    }

    public MailOperationResult dispatchPreset(String presetId, String target, String actorName) {
        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure(message("admin.preset-not-found", presetId));
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure(message("admin.preset-disabled", preset.id()));
        }

        try {
            List<RecipientResolution> recipients = resolvePresetRecipients(target);
            if (recipients.isEmpty()) {
                return MailOperationResult.failure(message("player.preset-dispatch-no-target"));
            }

            int successCount = 0;
            for (RecipientResolution recipient : recipients) {
                if (sendPresetMail(recipient, preset, actorName).success()) {
                    successCount++;
                }
            }

            if (successCount <= 0) {
                return MailOperationResult.failure(message("player.preset-dispatch-failed"));
            }
            if (successCount < recipients.size()) {
                return MailOperationResult.success(message("player.preset-dispatch-partial", successCount, recipients.size()));
            }
            return MailOperationResult.success(message("player.preset-dispatch-success", successCount));
        } catch (Exception exception) {
            this.logger.warning("派发预设邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.preset-dispatch-failed"));
        }
    }

    public MailOperationResult dispatchPresetToPlayer(String presetId, Player player, String actorName) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }

        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure(message("admin.preset-not-found", presetId));
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure(message("admin.preset-disabled", preset.id()));
        }

        try {
            return sendPresetMail(
                new RecipientResolution(player.getUniqueId(), player.getName()),
                preset,
                actorName
            );
        } catch (Exception exception) {
            this.logger.warning("派发预设邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.preset-dispatch-failed"));
        }
    }

    public MailOperationResult createCdk(String presetId, String rawCode, int maxClaims, Instant expiresAt, String createdBy) {
        if (isPresetReloading()) return MailOperationResult.failure(message("admin.preset-reload-busy"));
        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure(message("admin.preset-not-found", presetId));
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure(message("admin.preset-disabled", preset.id()));
        }
        if (maxClaims <= 0) {
            return MailOperationResult.failure(message("player.cdk-max-claims-invalid"));
        }

        try {
            boolean automatic = rawCode == null || rawCode.isBlank() || "auto".equalsIgnoreCase(rawCode);
            String requested = automatic ? "" : normalizeCdkCode(rawCode);
            if (!automatic && requested.isBlank()) {
                return MailOperationResult.failure(message("player.cdk-format-invalid"));
            }
            Instant now = Instant.now();
            for (int attempt = 0; attempt < (automatic ? 8 : 1); attempt++) {
                String code = automatic ? normalizeCdkCode(UUID.randomUUID().toString().replace("-", "").substring(0, 10)) : requested;
                if (repository.createCdk(new MailCdkDefinition(code, preset.id(), maxClaims, 0, expiresAt, true,
                    MailTexts.safe(createdBy), now, now))) return MailOperationResult.success(message("player.cdk-created", code));
            }
            return MailOperationResult.failure(automatic ? message("player.cdk-create-failed") : message("player.cdk-exists", requested));
        } catch (Exception exception) {
            this.logger.warning("创建 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.cdk-create-failed"));
        }
    }

    public Optional<MailCdkDefinition> loadCdk(String rawCode) {
        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            return Optional.empty();
        }
        try {
            return repository.loadCdk(code);
        } catch (Exception exception) {
            this.logger.warning("读取 CDK 失败: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<MailCdkDefinition> listCdks(int page, int pageSize) {
        try {
            return repository.loadCdks(page, pageSize);
        } catch (Exception exception) {
            this.logger.warning("读取 CDK 列表失败: " + exception.getMessage());
            return List.of();
        }
    }

    public MailOperationResult deleteCdk(String rawCode) {
        if (isPresetReloading()) return MailOperationResult.failure(message("admin.preset-reload-busy"));
        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            return MailOperationResult.failure(message("player.cdk-required"));
        }
        try {
            return repository.deleteCdk(code)
                ? MailOperationResult.success(message("player.cdk-deleted", code))
                : MailOperationResult.failure(message("player.cdk-not-found"));
        } catch (Exception exception) {
            this.logger.warning("删除 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.cdk-delete-failed"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        touchProfile(event.getPlayer(), false);
    }

    @EventHandler
    public void onAdmission(AuthAdmissionEvent event) {
        var admission = event.snapshot();
        UUID owner = admission.playerId();
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !currentMailAdmission(player, admission)) return;
        if (draftIo.contains(owner)) pendingDraftRecovery.put(owner, admission);
        else recoverAdmittedDraft(player, admission);
    }

    @EventHandler
    public void onClaimRestriction(AuthRestrictionEvent event) {
        var previous = event.previous();
        var job = claimJobs.get(previous.playerId());
        if (job != null && job.sameAdmission(previous)) job.disconnected = true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        pendingDraftRecovery.remove(playerUuid);
        composeOpenRequests.remove(playerUuid);
        var claim = claimJobs.get(playerUuid);
        if (claim != null && claim.player == player) claim.disconnected = true;
        ComposeSession session = takeReturnableDraft(playerUuid);
        composePasswordPanels.remove(playerUuid);
        if (session != null && !session.sent()) {
            returnComposeItems(player, session);
        }
        AdminEditSession adminSession = adminEditSessions.remove(playerUuid);
        if (adminSession != null) {
            returnAdminItems(player, adminSession);
        }
        selectedMailIds.remove(playerUuid);
        inboxQueries.remove(playerUuid);
        logPages.remove(playerUuid);
        inboxViewers.remove(playerUuid);
        logViewers.remove(playerUuid);
        inboxReads.remove(playerUuid);
        logReads.remove(playerUuid);
        // 清理 CDK 限流状态：仅清理未锁定的条目，锁定的保留（锁定期间玩家不能兑换）
        CdkRateLimitEntry rateLimitEntry = cdkRateLimitMap.get(playerUuid);
        if (rateLimitEntry != null && !cdkRedemptions.contains(playerUuid)) {
            synchronized (rateLimitEntry) {
                if (rateLimitEntry.lockoutUntil == null || !rateLimitEntry.lockoutUntil.isAfter(Instant.now())) {
                    cdkRateLimitMap.remove(playerUuid);
                }
            }
        }
    }

    // compose/admin 不再使用 Bukkit 容器，关闭/点击/拖拽事件由 ArcartX UI 关闭回调处理

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !CLIENT_PACKET_ID.equalsIgnoreCase(packetId)) {
            return false;
        }

        String action = data == null || data.isEmpty() ? "refresh" : MailTexts.safe(data.get(0)).toLowerCase(Locale.ROOT);
        String requiredUi = requiredUiForAction(action);
        if (requiredUi != null
            && (bridge == null || !bridge.isUiOpen(player, requiredUi))) {
            sendPlayerResult(
                player,
                MailOperationResult.failure(message("player.ui-required"))
            );
            return true;
        }
        switch (action) {
            case "open" -> {
                openInbox(player);
                return true;
            }
            case "refresh" -> {
                if (logViewers.contains(player.getUniqueId())) {
                    refreshLogs(player, false);
                } else {
                    refreshInbox(player, false);
                }
                return true;
            }
            case "filter" -> {
                if (data != null && data.size() >= 2) {
                    MailInboxFilter filter = MailInboxFilter.parse(data.get(1));
                    MailInboxQuery currentQuery = inboxQueries.get(player.getUniqueId());
                    if (currentQuery != null) {
                        inboxQueries.put(player.getUniqueId(), currentQuery.withFilter(filter));
                    } else {
                        inboxQueries.put(player.getUniqueId(), new MailInboxQuery(filter, 1, INBOX_PAGE_SIZE));
                    }
                    refreshInbox(player, false);
                }
                return true;
            }
            case "select" -> {
                if (data != null && data.size() >= 2) {
                    long mailId = MailTexts.parseLong(data.get(1), -1L);
                    if (mailId > 0L) {
                        selectMail(player, mailId);
                    }
                }
                return true;
            }
            case "compose" -> {
                sendPlayerResult(player, openCompose(player));
                return true;
            }
            case "compose-send" -> {
                if (data == null || data.size() < 6) {
                    sendPlayerResult(player, MailOperationResult.failure(message("player.compose-args-incomplete")));
                } else {
                    handleComposeSend(player, data);
                }
                return true;
            }
            case "compose-add-attachment" -> {
                if (data != null && data.size() >= 3) {
                    int slot = (int) MailTexts.parseLong(data.get(1), -1);
                    String countStr = MailTexts.safe(data.get(2));
                    sendPlayerResult(player, handleComposeAddAttachment(player, slot, countStr));
                }
                return true;
            }
            case "compose-remove-attachment" -> {
                if (data != null && data.size() >= 2) {
                    int index = (int) MailTexts.parseLong(data.get(1), -1);
                    sendPlayerResult(player, handleComposeRemoveAttachment(player, index));
                }
                return true;
            }
            case "admin-add-attachment" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 3) {
                    int slot = (int) MailTexts.parseLong(data.get(1), -1);
                    String countStr = MailTexts.safe(data.get(2));
                    sendAdminResult(player, handleAdminAddAttachment(player, slot, countStr), false);
                }
                return true;
            }
            case "admin-remove-attachment" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 2) {
                    int index = (int) MailTexts.parseLong(data.get(1), -1);
                    sendAdminResult(player, handleAdminRemoveAttachment(player, index), false);
                }
                return true;
            }
            case "password-unlock" -> {
                handlePasswordUnlock(player, data);
                return true;
            }
            case "claim" -> {
                if (data != null && data.size() >= 2) {
                    long mailId = MailTexts.parseLong(data.get(1), -1L);
                    if (mailId > 0L) {
                        startClaim(player, mailId, true).thenAccept(result -> {
                            if (player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) sendPlayerResult(player, result);
                        });
                    }
                }
                return true;
            }
            case "delete" -> {
                if (data != null && data.size() >= 2) {
                    long mailId = MailTexts.parseLong(data.get(1), -1L);
                    if (mailId > 0L) {
                        sendPlayerResult(player, deleteMail(player, mailId));
                    }
                }
                return true;
            }
            case "claimall" -> {
                claimAll(player);
                return true;
            }
            case "deleteall" -> {
                deleteAll(player);
                return true;
            }
            case "cdk" -> {
                if (data == null || data.size() < 2) {
                    sendPlayerResult(player, MailOperationResult.failure(message("player.cdk-please-enter")));
                } else {
                    redeemCdk(player, data.get(1), result -> sendPlayerResult(player, result));
                }
                return true;
            }
            case "logs" -> {
                sendPlayerResult(player, openLogs(player));
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                openAdminUi(player);
                return true;
            }
            // ─── Admin Preset Management (UI) ────────────────────
            case "admin-preset-list" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                sendAdminPresetList(player);
                return true;
            }
            case "admin-preset-new" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                clearAdminContainer(player);
                return true;
            }
            case "admin-preset-get" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 2) {
                    sendAdminPresetDetail(player, data.get(1));
                    populateAdminContainer(player, data.get(1));
                }
                return true;
            }
            case "admin-preset-cancel" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                clearAdminContainer(player);
                return true;
            }
            case "admin-preset-save" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                handleAdminPresetSave(player, data);
                return true;
            }
            case "admin-preset-delete" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 2) {
                    MailOperationResult result = deletePreset(data.get(1));
                    sendAdminResult(player, result);
                    if (result.success()) sendAdminPresetList(player);
                }
                return true;
            }
            case "admin-preset-send" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 3) {
                    MailOperationResult result = dispatchPreset(data.get(1), data.get(2), player.getName());
                    sendAdminResult(player, result);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    // ─── Admin UI Helpers ──────────────────────────────────────

    private void sendAdminPresetList(Player player) {
        List<MailPresetDefinition> definitions = getPresetDefinitions();
        List<Map<String, Object>> list = new ArrayList<>();
        for (MailPresetDefinition def : definitions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", def.id());
            entry.put("displayName", MailTexts.safe(def.displayName()));
            entry.put("subject", MailTexts.safe(def.subject()));
            entry.put("enabled", def.enabled());
            entry.put("attachmentCount", def.attachments() != null ? def.attachments().size() : 0);
            entry.put("commandCount", def.claimCommands() != null ? def.claimCommands().size() : 0);
            list.add(entry);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presets", list);
        payload.put("total", definitions.size());
        payload.put("maxPresetCount", definitions.size());
        payload.put("is_admin", player.hasPermission("arcartxsuite.admin"));
        payload.put("adminAttachmentSlots", adminAttachmentSlots());
        bridge.sendPacket(player, adminUiId, "preset-list", payload);
    }

    private void sendAdminPresetDetail(Player player, String presetId) {
        MailPresetDefinition def = getPreset(presetId);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (def == null) {
            payload.put("found", false);
            payload.put("id", MailTexts.safe(presetId));
            payload.put("itemAttachmentCount", 0);
            payload.put("currencyAttachments", "");
            payload.put("claimCommands", "");
            payload.put("claimConditions", "");
            payload.put("cdks", "");
        } else {
            payload.put("found", true);
            payload.put("id", def.id());
            payload.put("enabled", def.enabled());
            payload.put("displayName", MailTexts.safe(def.displayName()));
            payload.put("subject", MailTexts.safe(def.subject()));
            payload.put("body", MailTexts.safe(def.body()));
            payload.put("expiresAfterDays", def.expiresAfter() != null ? def.expiresAfter().toDays() : 15);
            // 货币附件：发送结构化字典供 UI 动态行使用（key 为索引字符串，避免 ARIA 对 Java List 的索引访问限制）
            Map<String, Map<String, Object>> currencyMap = new java.util.LinkedHashMap<>();
            if (def.attachments() != null) {
                int curIdx = 0;
                for (MailAttachment att : def.attachments()) {
                    if (att.isCurrency()) {
                        Map<String, Object> entry = new java.util.LinkedHashMap<>();
                        entry.put("currency", att.currencyId());
                        entry.put("amount", att.amount());
                        entry.put("description", att.description() != null ? att.description() : "");
                        // icon 字段：发送结构化字段供 UI 副面板编辑
                        if (att.icon() != null && att.icon().hasIcon()) {
                            entry.put("iconMaterial", att.icon().material() != null ? att.icon().material() : "");
                            entry.put("iconCustomModelData", att.icon().customModelData());
                            entry.put("iconTexture", att.icon().texture() != null ? att.icon().texture() : "");
                            entry.put("iconTextureUrl", att.icon().textureUrl() != null ? att.icon().textureUrl() : "");
                            entry.put("iconName", att.icon().name() != null ? att.icon().name() : "");
                            entry.put("iconLore", att.icon().lore() != null ? att.icon().lore() : java.util.List.of());
                        }
                        currencyMap.put(String.valueOf(curIdx), entry);
                        curIdx++;
                    }
                }
            }
            payload.put("currencyAttachmentsList", currencyMap);
            long itemCount = def.attachments() == null ? 0 : def.attachments().stream().filter(MailAttachment::isItem).count();
            payload.put("itemAttachmentCount", itemCount);
            // 领取命令序列化为每行一条的字符串，供 UI 多行输入框直接显示
            StringBuilder cmdStr = new StringBuilder();
            if (def.claimCommands() != null) {
                for (String cmd : def.claimCommands()) {
                    if (cmd == null || cmd.isBlank()) continue;
                    if (cmdStr.length() > 0) cmdStr.append("\n");
                    cmdStr.append(cmd);
                }
            }
            payload.put("claimCommands", cmdStr.toString());
            // 领取条件序列化为每行一条的字符串，供 UI 多行输入框直接显示
            // 优先使用 raw（inline 格式如 %ph% >= 10），fallback 到 serialize()
            StringBuilder condStr = new StringBuilder();
            if (def.claimConditions() != null) {
                for (ScriptCondition cond : def.claimConditions()) {
                    String display = cond.raw() != null && !cond.raw().isBlank() ? cond.raw() : cond.serialize();
                    if (display != null && !display.isBlank()) {
                        if (condStr.length() > 0) condStr.append("\n");
                        condStr.append(display);
                    }
                }
            }
            payload.put("claimConditions", condStr.toString());
            // CDK：发送结构化字典供 UI 动态行使用（key 为索引字符串）
            Map<String, Map<String, Object>> cdkMap = new java.util.LinkedHashMap<>();
            if (def.cdks() != null) {
                int cdkIdx = 0;
                for (MailPresetCdkDefinition cdk : def.cdks()) {
                    if (cdk.code() != null && !cdk.code().isBlank()) {
                        Map<String, Object> entry = new java.util.LinkedHashMap<>();
                        entry.put("code", cdk.code());
                        entry.put("enabled", cdk.enabled());
                        entry.put("maxClaims", cdk.maxClaims());
                        entry.put("expiresAfter", cdk.expiresAfter() != null ? cdk.expiresAfter().toDays() + "d" : "");
                        // expiresAt：绝对过期时间（UTC ISO 字符串），与 expiresAfter 互斥
                        entry.put("expiresAt", cdk.expiresAt() != null ? cdk.expiresAt().toString() : "");
                        // 过期模式：absolute=绝对时间 / relative=相对时长 / none=不过期
                        entry.put("expireMode", cdk.expiresAt() != null ? "absolute" : (cdk.expiresAfter() != null ? "relative" : "none"));
                        cdkMap.put(String.valueOf(cdkIdx), entry);
                        cdkIdx++;
                    }
                }
            }
            payload.put("cdkList", cdkMap);
            // 物品附件图标 JSON：直接从预设定义反序列化并序列化为 UI 图标 JSON
            // 不依赖 populateAdminContainer 的 session（该 session 可能因反序列化环境差异为空）
            List<ItemStack> previewItems = deserializePresetItems(def);
            payload.put("attachment_jsons", serializeAttachments(previewItems));
            payload.put("attachment_amounts", MailItemPreview.amounts(previewItems));
        }
        bridge.sendPacket(player, adminUiId, "preset-detail", payload);
    }

    private void handleAdminPresetSave(Player player, List<String> data) {
        // data: [action, id, enabled, displayName, subject, body, expiresAfterDays, currencyAttachments, claimCommands, claimConditions, cdks]
        if (data == null || data.size() < 7) {
            sendAdminResult(player, MailOperationResult.failure(message("player.admin-save-args-incomplete")));
            return;
        }
        try {
            String id = MailTexts.safe(data.get(1)).trim().toLowerCase(Locale.ROOT);
            if (id.isBlank()) {
                sendAdminResult(player, MailOperationResult.failure(message("player.preset-id-required")));
                return;
            }
            boolean enabled = "true".equalsIgnoreCase(MailTexts.safe(data.get(2)).trim());
            String displayName = MailTexts.safe(data.get(3)).trim();
            String subject = MailTexts.safe(data.get(4)).trim();
            String body = MailTexts.safe(data.get(5)).trim();
            long expiresAfterDays = MailTexts.parseLong(MailTexts.safe(data.get(6)).trim(), 15L);
            if (expiresAfterDays < 1) expiresAfterDays = 15;

            List<MailAttachment> attachments = new ArrayList<>();
            int order = 0;

            // 从附件列表读取物品附件
            AdminEditSession adminSession = adminEditSessions.get(player.getUniqueId());
            if (adminSession != null) {
                List<ItemStack> containerItems = collectAdminItems(adminSession);
                for (ItemStack itemStack : containerItems) {
                    ItemStack clean = stripTemplateTag(itemStack);
                    attachments.add(new MailAttachment(
                        0L, order++, MailAttachmentType.ITEM,
                        MailItemSerializer.serialize(clean), "", 0.0D, resolveItemDescription(clean), null
                    ));
                }
            }

            // 解析 currency attachments: JSON 数组 [{"currency":"money","amount":88,"description":"..."}]
            if (data.size() > 7 && !MailTexts.safe(data.get(7)).isBlank()) {
                try {
                    JsonArray curArray = JsonParser.parseString(MailTexts.safe(data.get(7))).getAsJsonArray();
                    for (JsonElement elem : curArray) {
                        if (!elem.isJsonObject()) continue;
                        JsonObject obj = elem.getAsJsonObject();
                        String currencyId = obj.has("currency") ? obj.get("currency").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                        if (currencyId.isBlank()) continue;
                        double amount = 0;
                        if (obj.has("amount")) {
                            try { amount = obj.get("amount").getAsDouble(); } catch (Exception e) { continue; }
                        }
                        if (amount <= 0) continue;
                        String desc = obj.has("description") ? obj.get("description").getAsString().trim() : currencyId + " " + amount;
                        // 解析 icon 字段：material/custom-model-data/texture/texture-url/name/lore
                        IconDefinition icon = null;
                        String iconMaterial = obj.has("iconMaterial") ? obj.get("iconMaterial").getAsString().trim() : "";
                        if (!iconMaterial.isBlank()) {
                            int iconCustomModelData = obj.has("iconCustomModelData") ? obj.get("iconCustomModelData").getAsInt() : 0;
                            String iconTexture = obj.has("iconTexture") ? obj.get("iconTexture").getAsString().trim() : "";
                            String iconTextureUrl = obj.has("iconTextureUrl") ? obj.get("iconTextureUrl").getAsString().trim() : "";
                            String iconName = obj.has("iconName") ? obj.get("iconName").getAsString().trim() : "";
                            List<String> iconLore = new ArrayList<>();
                            if (obj.has("iconLore")) {
                                if (obj.get("iconLore").isJsonArray()) {
                                    for (JsonElement loreElem : obj.get("iconLore").getAsJsonArray()) {
                                        iconLore.add(loreElem.getAsString());
                                    }
                                } else {
                                    String loreStr = obj.get("iconLore").getAsString();
                                    if (!loreStr.isBlank()) {
                                        for (String line : loreStr.split("\\R")) {
                                            String trimmed = line.trim();
                                            if (!trimmed.isEmpty()) {
                                                iconLore.add(trimmed);
                                            }
                                        }
                                    }
                                }
                            }
                            icon = new IconDefinition(
                                iconMaterial, 1, iconName, iconLore, iconCustomModelData,
                                "", "", "", "", "", iconTexture, iconTextureUrl,
                                java.util.Map.of(), "", false, "", ""
                            );
                        }
                        attachments.add(new MailAttachment(0L, order++, MailAttachmentType.CURRENCY, "", currencyId, amount, desc, icon));
                    }
                } catch (Exception e) {
                    this.logger.warning("Admin 预设保存: 货币附件 JSON 解析失败: " + e.getMessage());
                }
            }

            // 领取命令: 每行一条
            List<String> claimCommands = new ArrayList<>();
            if (data.size() > 8 && !MailTexts.safe(data.get(8)).isBlank()) {
                for (String line : MailTexts.safe(data.get(8)).split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        claimCommands.add(trimmed);
                    }
                }
            }

            MailPresetDefinition existing = getPreset(id);
            // 领取条件: 每行一条（格式 %placeholder% >= value 或 aria:/js: 前缀）
            List<ScriptCondition> claimConditions = new ArrayList<>();
            if (data.size() > 9 && !MailTexts.safe(data.get(9)).isBlank()) {
                for (String line : MailTexts.safe(data.get(9)).split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            ScriptCondition parsed = ScriptCondition.deserialize(trimmed);
                            if (parsed != null) {
                                claimConditions.add(parsed);
                            }
                        } catch (Exception e) {
                            this.logger.warning("Admin 预设保存: 无法解析领取条件 '" + trimmed + "': " + e.getMessage());
                        }
                    }
                }
            }
            // CDK: JSON 数组 [{"code":"STARTER2026","enabled":true,"maxClaims":100,"expiresAfter":"30d"}]
            List<MailPresetCdkDefinition> cdks = new ArrayList<>();
            if (data.size() > 10 && !MailTexts.safe(data.get(10)).isBlank()) {
                try {
                    JsonArray cdkArray = JsonParser.parseString(MailTexts.safe(data.get(10))).getAsJsonArray();
                    for (JsonElement elem : cdkArray) {
                        if (!elem.isJsonObject()) continue;
                        JsonObject obj = elem.getAsJsonObject();
                        String code = obj.has("code") ? obj.get("code").getAsString().trim() : "";
                        if (code.isEmpty()) continue;
                        String normalizedCode = normalizeCdkCode(code);
                        if (normalizedCode.isBlank()) continue;
                        boolean cdkEnabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
                        int maxClaims = obj.has("maxClaims") ? obj.get("maxClaims").getAsInt() : 100;
                        if (maxClaims < 1) maxClaims = 100;
                        // expiresAt：绝对过期时间（UTC ISO 字符串），与 expiresAfter 互斥
                        // 优先级：expireMode == "absolute" 且 expiresAt 非空 → 使用 expiresAt
                        //         expireMode == "relative" 或 expiresAfter 非空 → 使用 expiresAfter
                        //         否则默认 30 天
                        java.time.Instant expiresAtInstant = null;
                        String expireMode = obj.has("expireMode") ? obj.get("expireMode").getAsString().trim() : "relative";
                        if ("absolute".equals(expireMode) && obj.has("expiresAt") && !obj.get("expiresAt").getAsString().isBlank()) {
                            try {
                                expiresAtInstant = java.time.Instant.parse(obj.get("expiresAt").getAsString().trim());
                            } catch (Exception ignored) {}
                        }
                        java.time.Duration expiresAfterDur = null;
                        if (expiresAtInstant == null) {
                            if (obj.has("expiresAfter") && !obj.get("expiresAfter").getAsString().isBlank()) {
                                String eaStr = obj.get("expiresAfter").getAsString().trim();
                                try {
                                    if (eaStr.endsWith("d")) {
                                        expiresAfterDur = java.time.Duration.ofDays(Long.parseLong(eaStr.substring(0, eaStr.length() - 1)));
                                    } else {
                                        expiresAfterDur = java.time.Duration.ofDays(Long.parseLong(eaStr));
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                            if (expiresAfterDur == null) {
                                expiresAfterDur = java.time.Duration.ofDays(30);
                            }
                        }
                        cdks.add(new MailPresetCdkDefinition(normalizedCode, cdkEnabled, maxClaims, expiresAtInstant, expiresAfterDur));
                    }
                } catch (Exception e) {
                    this.logger.warning("Admin 预设保存: CDK JSON 解析失败: " + e.getMessage());
                }
            }

            MailPresetDefinition newPreset = new MailPresetDefinition(
                id, enabled, displayName.isBlank() ? id : displayName, subject, body,
                java.time.Duration.ofDays(expiresAfterDays),
                List.copyOf(attachments), claimCommands, claimConditions, cdks
            );
            MailOperationResult result = savePreset(newPreset);
            sendAdminResult(player, result);
            if (result.success()) {
                // 保存成功后退回物品并清空附件列表
                if (adminSession != null) {
                    returnAdminItems(player, adminSession);
                    adminSession.attachments().clear();
                }
                sendAdminPresetList(player);
            }
        } catch (Exception exception) {
            this.logger.warning("Admin 预设保存处理异常: " + exception.getMessage());
            sendAdminResult(player, MailOperationResult.failure(message("player.admin-save-exception", exception.getMessage())));
        }
    }

    private void sendAdminResult(Player player, MailOperationResult result) {
        sendAdminResult(player, result, true);
    }

    /** @param closeView true=操作完成后切回列表视图（保存/删除）；false=留在编辑视图（附件增删） */
    private void sendAdminResult(Player player, MailOperationResult result, boolean closeView) {
        if (player == null || !player.isOnline() || result == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("message", result.message());
        payload.put("closeView", closeView);
        bridge.sendPacket(player, adminUiId, "result", payload);
        player.sendMessage(MESSAGE_PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    static MailSendQuote calculateComposeQuote(
        MailPlayerSendConfiguration configuration,
        Map<String, BigDecimal> attachmentAmounts,
        int itemAttachmentCount,
        String feeCurrencyOverride
    ) {
        Map<String, BigDecimal> normalizedAttachmentAmounts = new LinkedHashMap<>();
        Map<String, BigDecimal> attachmentTaxes = new LinkedHashMap<>();
        if (attachmentAmounts != null) {
            for (Map.Entry<String, BigDecimal> entry : attachmentAmounts.entrySet()) {
                String currencyId = MailTexts.normalizeCurrencyId(entry.getKey());
                BigDecimal amount = MailTexts.normalizeAmount(entry.getValue());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                normalizedAttachmentAmounts.put(currencyId, amount);
                BigDecimal taxRate = BigDecimal.valueOf(configuration.attachmentTaxRate(currencyId));
                attachmentTaxes.put(currencyId, MailTexts.normalizeAmount(amount.multiply(taxRate)));
            }
        }

        BigDecimal baseFee = MailTexts.normalizeAmount(BigDecimal.valueOf(configuration.baseFee()));
        BigDecimal itemFee = MailTexts.normalizeAmount(BigDecimal.valueOf(configuration.itemFee()).multiply(BigDecimal.valueOf(Math.max(0, itemAttachmentCount))));
        BigDecimal totalFee = MailTexts.normalizeAmount(baseFee.add(itemFee));
        String effectiveFeeCurrency = feeCurrencyOverride != null && !feeCurrencyOverride.isBlank()
            ? MailTexts.normalizeCurrencyId(feeCurrencyOverride)
            : MailTexts.normalizeCurrencyId(configuration.feeCurrency());
        return new MailSendQuote(
            true,
            "",
            effectiveFeeCurrency,
            baseFee,
            itemFee,
            totalFee,
            Map.copyOf(normalizedAttachmentAmounts),
            Map.copyOf(attachmentTaxes)
        );
    }

    static boolean canDeleteMail(boolean allowDeleteWithUnclaimedAttachments, MailMessage message) {
        if (message == null || message.deleted() || message.expired()
            || message.status() == MailStatus.CLAIM_PENDING || message.status() == MailStatus.CLAIM_REVIEW) {
            return false;
        }
        return allowDeleteWithUnclaimedAttachments
            || !message.claimable()
            || (!message.hasAttachments() && !message.hasClaimCommands());
    }

    static String normalizeCdkCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
    }

    static String normalizeRecipientInput(String rawRecipient) {
        return MailTexts.safe(rawRecipient).trim();
    }

    private Map<String, MailPresetDefinition> readPresets() throws IOException {
        File directory = new File(baseDataDir, configuration.presetsDirectory());
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建邮件预设目录: " + directory.getAbsolutePath());
        }
        exportBundledPresetIfMissing(directory);
        // 预设加载使用 admin 附件上限（取 admin 和 playerSend 的较大值，确保 admin 保存的附件不被截断）
        int presetMaxAttachments = Math.max(adminAttachmentSlots(), configuration.playerSend().maxAttachments());
        return MailPresetLoader.loadPresets(
            directory, this.logger, presetMaxAttachments, itemSourceRegistry, vanillaItemNameBridge, iconResolver);
    }

    private void syncPresetCdks(java.util.Collection<MailPresetDefinition> definitions) throws Exception {
        Instant now = Instant.now();
        LinkedHashSet<String> seenCodes = new LinkedHashSet<>();
        List<MailRepository.PresetCdk> cdks = new ArrayList<>();
        for (MailPresetDefinition preset : definitions) {
            if (!preset.enabled() || preset.cdks().isEmpty()) {
                continue;
            }
            for (MailPresetCdkDefinition cdk : preset.cdks()) {
                if (!seenCodes.add(cdk.code())) {
                    this.logger.warning("邮件预设 CDK 重复定义，后续重复项已跳过: " + cdk.code());
                    continue;
                }
                boolean relativeExpiry = cdk.expiresAt() == null && cdk.expiresAfter() != null;
                MailCdkDefinition definition = new MailCdkDefinition(
                    cdk.code(), preset.id(), cdk.maxClaims(), 0,
                    relativeExpiry ? now.plus(cdk.expiresAfter()) : cdk.expiresAt(),
                    cdk.enabled(), "preset:" + preset.id(), now, now
                );
                cdks.add(new MailRepository.PresetCdk(definition, relativeExpiry));
            }
        }
        if (!cdks.isEmpty()) {
            cdks.sort(java.util.Comparator.comparing(cdk -> cdk.definition().code()));
            repository.syncPresetCdks(cdks);
            this.logger.info("已同步邮件预设 CDK: " + cdks.size() + " 个。");
        }
    }

    private void bindUiResources() throws IOException {
        File inboxFile = exportUiResource(INBOX_UI_RESOURCE_PATH, INBOX_UI_FILE_PATH);
        File composeFile = exportUiResource(COMPOSE_UI_RESOURCE_PATH, COMPOSE_UI_FILE_PATH);
        File logsFile = exportUiResource(LOGS_UI_RESOURCE_PATH, LOGS_UI_FILE_PATH);
        File adminFile = exportUiResource(ADMIN_UI_RESOURCE_PATH, ADMIN_UI_FILE_PATH);
        inboxUiId = bindSingleUi(configuration.ui().inboxUiId(), inboxFile);
        composeUiId = bindSingleUi(configuration.ui().composeUiId(), composeFile);
        logsUiId = bindSingleUi(configuration.ui().logsUiId(), logsFile);
        adminUiId = bindSingleUi(configuration.ui().adminUiId(), adminFile);
    }

    private void logUnavailableCurrencyProviders() {
        for (CurrencyDefinition definition : currencyBridgeManager.definitions()) {
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(definition.id());
            if (currencyBridge != null && !currencyBridge.available()) {
                this.logger.warning("邮件货币桥接不可用: " + definition.id() + " -> " + currencyBridge.unavailableReason());
            }
        }
    }

    private MailPlayerProfile profileSnapshot(Player player, boolean sent) {
        Instant now = Instant.now();
        return new MailPlayerProfile(player.getUniqueId(), player.getName(), sent ? now : null, now, Bukkit.getServer().getName());
    }

    private void touchProfile(Player player, boolean updateLastSendAt) {
        if (player == null) return;
        var profile = profileSnapshot(player, updateLastSendAt);
        try { background.execute(() -> saveProfile(profile)); }
        catch (java.util.concurrent.RejectedExecutionException stopped) {
            if (active) logger.warning("邮箱玩家档案保存队列不可用: " + profile.playerUuid());
        }
    }

    private void saveProfile(MailPlayerProfile profile) {
        try {
            repository.upsertPlayerProfile(profile);
        } catch (Exception exception) {
            this.logger.warning("更新邮箱玩家档案失败: " + exception.getMessage());
        }
    }

    private void runCleanup() {
        if (!active || !cleanupRunning.compareAndSet(false, true)) return;
        Instant now = Instant.now();
        try {
            repository.cleanupMail(
                now,
                now.minusSeconds(configuration.retention().claimedRetentionDays() * 86400L),
                now.minusSeconds(configuration.retention().deletedRetentionDays() * 86400L)
            );
            repository.cleanupCdks(now);
            if (active) onMain(() -> {
                if (active) refreshAllViewerStates();
            });
        } catch (Exception exception) {
            if (active) this.logger.warning("执行邮件清理失败: " + exception.getMessage());
        } finally {
            cleanupRunning.set(false);
        }
    }

    private void handleCrossServerMessage(String message) {
        if (configuration.debug()) {
            this.logger.info("收到邮件跨服消息: " + message);
        }
        if (message == null || message.isBlank() || !message.startsWith("refresh:")) {
            return;
        }

        String rawUuid = message.substring("refresh:".length()).trim();
        try {
            UUID playerUuid = UUID.fromString(rawUuid);
            onMain(() -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    refreshPlayerViews(player, false);
                }
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private ComposeSession takeReturnableDraft(UUID owner) {
        ComposeSession session = composeSessions.get(owner);
        if (session == null || session.pendingSend != null) return null;
        if (draftIo.contains(owner)) {
            session.returnDraftWhenReady = true;
            return null;
        }
        return composeSessions.remove(owner, session) ? session : null;
    }

    private void returnComposeItems(Player player, ComposeSession session) {
        if (player == null || session == null || session.pendingSend != null) return;
        recoverAdmittedDraft(player, AuthAccess.snapshot(player.getUniqueId()));
    }

    private void recoverAdmittedDraft(Player player, AuthSnapshot admission) {
        if (!currentMailAdmission(player, admission) || composeSessions.containsKey(admission.playerId())) return;
        recoverDraftAttachments(player, admission, result -> {
            if (!result.success() && currentMailAdmission(player, admission)) sendPlayerResult(player, result);
        });
    }

    private void resumeAdmittedDraft(UUID owner) {
        if (draftIo.contains(owner)) return;
        AuthSnapshot admission = pendingDraftRecovery.remove(owner);
        if (admission == null) return;
        Player player = Bukkit.getPlayer(owner);
        if (player != null) recoverAdmittedDraft(player, admission);
    }

    private MailOperationResult attachmentsProcessing() {
        return MailOperationResult.success(message("player.compose-recovery-processing"));
    }

    private boolean currentMailPlayer(Player player) {
        return active && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private boolean currentMailAdmission(Player player, AuthSnapshot admission) {
        if (!currentMailPlayer(player) || !admission.admitted() || !player.getUniqueId().equals(admission.playerId())) return false;
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return current.admitted() && current.generation() == admission.generation()
            && java.util.Objects.equals(current.connectionId(), admission.connectionId());
    }

    private String liveReceipt(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private void recoverDraftAttachments(Player player, AuthSnapshot admission,
                                         java.util.function.Consumer<MailOperationResult> complete) {
        UUID uuid = player.getUniqueId();
        if (!currentMailAdmission(player, admission)) {
            complete.accept(MailOperationResult.failure(message("player.compose-session-expired")));
            return;
        }
        if (!draftIo.add(uuid)) {
            complete.accept(MailOperationResult.failure(message("player.compose-recovery-processing")));
            return;
        }
        java.util.function.Consumer<MailOperationResult> done = result -> {
            draftIo.remove(uuid);
            complete.accept(result);
            resumeAdmittedDraft(uuid);
        };
        String live = liveReceipt(player, MailDraftAttachments.RECEIPT);
        submitComposeStorage(() -> draftAttachments.inspect(uuid, live), (recovery, failure) -> {
            if (failure != null || !currentMailAdmission(player, admission)) {
                done.accept(draftRecoveryError(uuid, failure));
            } else if (recovery.send() != null) {
                checkRestartedHandoff(player, admission, recovery.send(), live, done);
            } else if (recovery.items().isEmpty()) {
                done.accept(MailOperationResult.success(""));
            } else {
                try {
                    ReturnInventory inventory = prepareAttachmentReturn(player, decodeAttachmentItems(recovery.items()));
                    if (inventory == null) {
                        done.accept(MailOperationResult.failure(message("player.draft-return-no-space")));
                        return;
                    }
                    mutateDraft(player, admission, null, inventory, List.of(), true, error -> {
                        if (error == null) {
                            if (currentMailAdmission(player, admission)) player.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + message("player.compose-attachments-returned"));
                            done.accept(MailOperationResult.success(""));
                        } else done.accept(draftRecoveryError(uuid, error));
                    });
                } catch (IOException | RuntimeException error) {
                    done.accept(draftRecoveryError(uuid, error));
                }
            }
        });
    }

    private MailOperationResult draftRecoveryError(UUID uuid, Exception error) {
        if (error != null) logger.warning("写信附件待核对: player=" + uuid + "，" + error.getMessage());
        return MailOperationResult.failure(message("player.draft-return-review"));
    }

    private static List<ItemStack> decodeAttachmentItems(List<String> encoded) throws IOException {
        List<ItemStack> items = new ArrayList<>(encoded.size());
        for (String item : encoded) items.add(MailItemSerializer.deserialize(item));
        return items;
    }

    private record ReturnInventory(ItemStack[] before, ItemStack[] after) { }

    private ReturnInventory prepareAttachmentReturn(Player player, List<ItemStack> items) {
        ItemStack[] before = InventoryItems.copy(player.getInventory().getStorageContents());
        ItemStack[] after = InventoryItems.copy(before);
        for (ItemStack item : items) if (!InventoryItems.put(after, item)) return null;
        return new ReturnInventory(before, after);
    }

    private record RestartedAttachments(long receipt, List<String> items, boolean canRefund) { }

    private void checkRestartedHandoff(Player player, AuthSnapshot admission, MailDraftAttachments.Handoff send, String liveDraft,
                                        java.util.function.Consumer<MailOperationResult> done) {
        UUID uuid = player.getUniqueId();
        String token = send.key() + ":returned";
        String liveReturn = send.fundsOnly() ? null : liveReceipt(player, COMPOSE_ITEM_RECEIPT);
        submitComposeStorage(() -> {
            long receipt = restartedSendReceipt(send);
            if (receipt >= 0) return new RestartedAttachments(receipt, List.of(), false);
            if (!send.fundsOnly()) {
                String savedReturn = PlayerDataReceipt.read(playerData.resolve(uuid + ".dat"), COMPOSE_ITEM_RECEIPT.toString());
                if (!java.util.Objects.equals(liveReturn, savedReturn)) throw new IOException("附件退回回执未确认");
                if (token.equals(savedReturn)) return new RestartedAttachments(receipt, List.of(), false);
            }
            Path review = composeReviewFile(send.key());
            if (Files.notExists(review)) throw new IOException("寄信附件退回凭证缺失");
            var evidence = JsonParser.parseString(Files.readString(review)).getAsJsonObject();
            String phase = evidence.get("status").getAsString();
            if (ComposeSendPhase.SENDING.name().equals(phase)) {
                return new RestartedAttachments(receipt, List.of(), true);
            }
            if (send.fundsOnly()) return new RestartedAttachments(receipt, List.of(), false);
            String start = evidence.has("returnStart") ? evidence.get("returnStart").getAsString() : "";
            if (ComposeSendPhase.RETURN_REVIEW.name().equals(phase)
                    || (ComposeSendPhase.RETURNING.name().equals(phase)
                        && (start.isBlank() || draftAttachments.returnStarted(uuid, start)))) {
                throw new IOException("附件可能已退回，未取得存档证明");
            }
            return new RestartedAttachments(receipt, draftAttachments.unsentItems(uuid, liveDraft, send), false);
        }, (returned, failure) -> {
            if (failure != null || !currentMailAdmission(player, admission)) {
                if (failure != null) logger.warning("重启后寄信待核对: key=" + send.key() + "，" + failure.getMessage());
                done.accept(MailOperationResult.failure(message(send.fundsOnly()
                    ? "player.compose-send-review" : "player.compose-return-review", send.key())));
            } else if (returned.receipt() == 0) {
                done.accept(MailOperationResult.failure(message("player.compose-send-review", send.key())));
            } else if (returned.canRefund()) {
                refundRestartedCompose(player, admission, send, liveDraft, done);
            } else if (returned.items().isEmpty()) {
                finishRestartedHandoff(player, admission, send, returned.receipt() > 0, done);
            } else {
                try {
                    ReturnInventory inventory = prepareAttachmentReturn(player, decodeAttachmentItems(returned.items()));
                    if (inventory == null) {
                        done.accept(MailOperationResult.failure(message("player.compose-return-no-space", send.key())));
                        return;
                    }
                    String start = UUID.randomUUID().toString();
                    submitComposeStorage(() -> markRecoveredReturnStarted(send.key(), start), (previous, error) -> {
                        if (error != null) {
                            done.accept(MailOperationResult.failure(message("player.compose-return-review", send.key())));
                            return;
                        }
                        saveComposeReturn(player, inventory, token, start,
                            () -> canApplyInventory(player, admission, null, inventory)
                                && java.util.Objects.equals(liveReturn, liveReceipt(player, COMPOSE_ITEM_RECEIPT)), saveFailure -> {
                            if (saveFailure instanceof java.util.concurrent.CancellationException) {
                                submitComposeStorage(() -> {
                                    writeComposeFile(composeReviewFile(send.key()), previous);
                                    return null;
                                }, (ignored, failed) -> done.accept(failed == null
                                    ? MailOperationResult.failure(message("player.compose-session-expired"))
                                    : MailOperationResult.failure(message("player.compose-return-review", send.key()))));
                            } else if (saveFailure != null) done.accept(MailOperationResult.failure(message("player.compose-return-review", send.key())));
                            else finishRestartedHandoff(player, admission, send, false, done);
                        });
                    });
                } catch (IOException | RuntimeException error) {
                    done.accept(draftRecoveryError(uuid, error));
                }
            }
        });
    }

    private void finishRestartedHandoff(Player player, AuthSnapshot admission, MailDraftAttachments.Handoff send, boolean sent,
                                        java.util.function.Consumer<MailOperationResult> done) {
        UUID uuid = player.getUniqueId();
        submitComposeStorage(() -> {
            boolean refundReview = !sent && send.fundsOnly() && markRecoveredFunds(send.key());
            if (!sent && !send.fundsOnly()) markRecoveredAttachments(send.key());
            draftAttachments.finishHandoff(uuid, send.key());
            if (sent || (send.fundsOnly() && !refundReview)) Files.deleteIfExists(composeReviewFile(send.key()));
            return refundReview;
        }, (refundReview, error) -> {
            if (error == null && currentMailAdmission(player, admission)) {
                if (refundReview) sendPlayerResult(player, MailOperationResult.failure(message("player.compose-refund-review", send.key())));
                else sendPlayerResult(player, MailOperationResult.success(message(sent
                    ? "player.compose-recovered-sent"
                    : send.fundsOnly() ? "player.compose-recovered-cancelled" : "player.compose-attachments-returned")));
            }
            done.accept(error == null ? MailOperationResult.success("") : draftRecoveryError(uuid, error));
        });
    }

    private String markRecoveredReturnStarted(String key, String start) throws IOException {
        Path file = composeReviewFile(key);
        String before = Files.readString(file);
        JsonObject evidence = JsonParser.parseString(before).getAsJsonObject();
        evidence.addProperty("status", ComposeSendPhase.RETURNING.name());
        evidence.addProperty("returnStart", start);
        writeComposeFile(file, evidence.toString());
        return before;
    }

    private long restartedSendReceipt(MailDraftAttachments.Handoff send) throws IOException, SQLException {
        long receipt = repository.readPlayerSendReceipt(send.key(), send.recipient());
        Path file = composeReviewFile(send.key());
        if (receipt == 0 && !Files.notExists(file)) {
            var evidence = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String phase = evidence.get("status").getAsString();
            if (ComposeSendPhase.SAVING_ATTACHMENTS.name().equals(phase) || ComposeSendPhase.SENDING.name().equals(phase)) {
                // Resolve against the same unique key as the interrupted insert.
                receipt = repository.resolvePlayerSend(send.key(), send.recipient());
                return receipt == 0 ? -1 : receipt;
            }
        }
        return receipt;
    }

    private record RestartedRefund(String previous, JsonObject evidence, Map<String, BigDecimal> charges) { }

    private void refundRestartedCompose(Player player, AuthSnapshot admission, MailDraftAttachments.Handoff send, String liveDraft,
                                         java.util.function.Consumer<MailOperationResult> done) {
        UUID owner = player.getUniqueId();
        Path file = composeReviewFile(send.key());
        MailOperationResult review = MailOperationResult.failure(message("player.compose-refund-review", send.key()));
        submitComposeStorage(() -> {
            String previous = Files.readString(file);
            JsonObject evidence = JsonParser.parseString(previous).getAsJsonObject();
            if (!ComposeSendPhase.SENDING.name().equals(evidence.get("status").getAsString())
                    || !owner.toString().equals(evidence.get("sender").getAsString())
                    || !send.recipient().toString().equals(evidence.get("recipient").getAsString())
                    || !send.key().equals(evidence.get("operation").getAsString())) {
                throw new IOException("寄信退款凭证已变化");
            }
            var charges = new LinkedHashMap<String, BigDecimal>();
            for (var entry : evidence.getAsJsonObject("charges").entrySet()) {
                BigDecimal amount = entry.getValue().getAsBigDecimal();
                if (amount.signum() <= 0) throw new IOException("寄信退款金额无效");
                charges.put(entry.getKey(), amount);
            }
            evidence.addProperty("status", ComposeSendPhase.REFUND_PENDING.name());
            writeComposeFile(file, evidence.toString());
            return new RestartedRefund(previous, evidence, charges);
        }, (refund, failure) -> {
            if (failure != null) {
                logger.warning("重登寄信退款凭证未确认: " + send.key() + "，" + failure.getMessage());
                done.accept(review);
                return;
            }
            if (!currentMailAdmission(player, admission)) {
                // No wallet call ran, so the original refund can resume after admission.
                submitComposeStorage(() -> {
                    writeComposeFile(file, refund.previous());
                    return null;
                }, (ignored, error) -> {
                    if (error != null) logger.warning("重登寄信退款凭证待核对: " + send.key() + "，" + error.getMessage());
                    done.accept(review);
                });
                return;
            }
            boolean confirmed = refundSender(player, refund.charges(), () -> currentMailAdmission(player, admission));
            refund.evidence().addProperty("refundConfirmed", confirmed);
            refund.evidence().addProperty("status", ComposeSendPhase.RETURN_PENDING.name());
            String evidence = refund.evidence().toString();
            submitComposeStorage(() -> {
                writeComposeFile(file, evidence);
                return null;
            }, (ignored, error) -> {
                if (currentMailAdmission(player, admission)) {
                    if (!confirmed || error != null) sendPlayerResult(player, review);
                    checkRestartedHandoff(player, admission, send, liveDraft, done);
                } else done.accept(review);
            });
        });
    }

    private void markRecoveredAttachments(String key) throws IOException {
        Path file = composeReviewFile(key);
        if (Files.notExists(file)) return;
        JsonObject evidence = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        evidence.addProperty("attachmentsReturned", true);
        writeComposeFile(file, evidence.toString());
    }

    private boolean markRecoveredFunds(String key) throws IOException {
        Path file = composeReviewFile(key);
        JsonObject evidence = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if (evidence.getAsJsonObject("charges").size() == 0 || evidence.get("refundConfirmed").getAsBoolean()) return false;
        evidence.addProperty("status", ComposeSendPhase.REFUND_REVIEW.name());
        writeComposeFile(file, evidence.toString());
        return true;
    }
    private MailOperationResult sendMailInternal(UUID ownerUuid, String logContent, MailMessage message, boolean publishRedis) {
        try {
            repository.insertMail(message);
        } catch (Exception exception) {
            this.logger.warning("发送邮件失败: " + exception.getMessage());
            if (configuration.debug() && logContent != null && !logContent.isBlank()) {
                this.logger.warning("发送邮件失败的上下文: " + logContent);
            }
            return MailOperationResult.failure(message("player.mail-send-failed"));
        }
        try {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                touchProfile(owner, false);
                if (inboxViewers.contains(ownerUuid) || logViewers.contains(ownerUuid)) {
                    refreshPlayerViews(owner, false);
                }
                sendMailNotifyCard(owner, message);
            }
            if (publishRedis) {
                publishRefresh(ownerUuid);
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("邮件已入库，收件提示或刷新失败: " + failure.getMessage());
        }
        return MailOperationResult.success(message("player.mail-send-success"));
    }

    private void sendMailNotifyCard(Player recipient, MailMessage message) {
        String cardId = configuration.ui().notifyCardId();
        if (cardId == null || cardId.isBlank()) {
            return;
        }
        try {
            MailUiConfiguration ui = configuration.ui();
            String subject = message.subject() == null ? "" : message.subject();
            int textWidth = MailTexts.measureTextWidth(subject, ui.charWidthFull(), ui.charWidthHalf());
            List<String> lines;
            if (textWidth <= ui.maxLineWidth()) {
                lines = List.of(subject);
            } else {
                lines = MailTexts.wrapText(subject, ui.maxLineWidth(), ui.charWidthFull(), ui.charWidthHalf());
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("senderName", message.senderName() == null ? "系统" : message.senderName());
            payload.put("sourceType", message.sourceType() == null ? "system" : message.sourceType().name().toLowerCase(Locale.ROOT));
            payload.put("hasAttachments", String.valueOf(message.hasAttachments()));
            for (int i = 0; i < lines.size(); i++) {
                payload.put("msg" + i, lines.get(i));
            }
            bridge.sendChatCard(recipient, cardId, payload);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("发送邮件通知卡片失败: " + exception.getMessage());
            }
        }
    }
    private void refreshInbox(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID id = player.getUniqueId();
        MailInboxQuery query = inboxQueries.computeIfAbsent(id, ignored -> new MailInboxQuery(MailInboxFilter.ALL, 1, INBOX_PAGE_SIZE));
        long selected = selectedMailIds.getOrDefault(id, 0L);
        var previous = inboxReads.get(id);
        var request = new ViewRequest(initPacket || (previous != null && previous.initPacket()));
        inboxReads.put(id, request);
        submitComposeStorage(() -> repository.loadInboxView(id, query, selected), (inbox, failure) -> {
            if (inboxReads.get(id) != request) return;
            inboxReads.remove(id);
            if (failure != null) {
                logger.warning("刷新邮箱界面失败: " + failure.getMessage());
                return;
            }
            if (Bukkit.getPlayer(id) == player && player.isOnline() && inboxViewers.contains(id)
                    && query.equals(inboxQueries.get(id)) && selected == selectedMailIds.getOrDefault(id, 0L)) {
                sendInboxView(player, query, inbox, request.initPacket());
            }
        });
    }

    private void sendInboxView(Player player, MailInboxQuery query, MailRepository.InboxView inbox, boolean initPacket) {
        UUID id = player.getUniqueId();
        var page = inbox.page();
        inboxQueries.put(id, query.withPage(page.page()));
        long selected = inbox.selectedMailId();
        if (selected > 0L) selectedMailIds.put(id, selected);
        else selectedMailIds.remove(id);
        int claimableCount = (int) page.entries().stream().filter(MailMessage::claimable).count();
        Map<String, Object> packet = MailInboxPacketFactory.build(page, selected, inbox.stats(), query.filter(),
            claimableCount, messages, configuration.ui().inboxPreviewMaxChars(), iconResolver);
        packet.put("is_admin", player.hasPermission("arcartxsuite.admin"));
        bridge.sendPacket(player, inboxUiId, initPacket ? "init" : "update", packet);
    }

    private void refreshLogs(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID id = player.getUniqueId();
        int requestedPage = Math.max(1, logPages.getOrDefault(id, 1));
        var previous = logReads.get(id);
        var request = new ViewRequest(initPacket || (previous != null && previous.initPacket()));
        logReads.put(id, request);
        submitComposeStorage(() -> repository.loadLogPage(id, requestedPage, LOG_PAGE_SIZE), (page, failure) -> {
            if (logReads.get(id) != request) return;
            logReads.remove(id);
            if (failure != null) {
                logger.warning("刷新日志界面失败: " + failure.getMessage());
                return;
            }
            if (Bukkit.getPlayer(id) == player && player.isOnline() && logViewers.contains(id)
                    && requestedPage == logPages.getOrDefault(id, 1)) {
                sendLogView(player, page, request.initPacket());
            }
        });
    }

    private void sendLogView(Player player, MailPage<MailLogEntry> page, boolean initPacket) {
        logPages.put(player.getUniqueId(), page.page());
        Map<String, Object> packet = MailLogsPacketFactory.build(page, messages);
        packet.put("is_admin", player.hasPermission("arcartxsuite.admin"));
        bridge.sendPacket(player, logsUiId, initPacket ? "init" : "update", packet);
    }

    private MailOperationResult openLogs(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }
        composeOpenRequests.remove(player.getUniqueId());
        try {
            touchProfile(player, false);
            logPages.putIfAbsent(player.getUniqueId(), 1);
            logViewers.add(player.getUniqueId());
            inboxViewers.remove(player.getUniqueId());
            // 先打开 ArcartX UI（不鼠标重置），再延迟关闭 Bukkit 容器（compose/admin 会话）
            bridge.openUi(player, logsUiId);
            onMain(() -> closeOtherMailUis(player));
            refreshLogs(player, true);
            return MailOperationResult.success(message("player.logs-opened"));
        } catch (Exception exception) {
            this.logger.warning("打开邮件日志失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.logs-open-failed"));
        }
    }

    private void selectMail(Player player, long mailId) {
        selectedMailIds.put(player.getUniqueId(), mailId);
        try {
            repository.markUnreadRead(player.getUniqueId(), mailId, Instant.now());
        } catch (Exception exception) {
            this.logger.warning("读取邮件详情失败: " + exception.getMessage());
        }
        refreshInbox(player, false);
    }

    private void handleComposeSend(Player player, List<String> data) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            sendPlayerResult(player, MailOperationResult.failure(message("player.compose-session-missing")));
            return;
        }
        if (draftIo.contains(player.getUniqueId())) {
            sendPlayerResult(player, attachmentsProcessing());
            return;
        }
        SecondaryPasswordAccess passwordAccess = secondaryPasswordAccess();
        if (passwordAccess == null) {
            sendPlayerResult(player, MailOperationResult.failure(message("player.compose-password-unavailable")));
            return;
        }
        String suppliedToken = data == null || data.size() <= 6 ? "" : MailTexts.safe(data.get(6));
        synchronized (session) {
            if (session.tokenInFlight
                || suppliedToken.isBlank()
                || !suppliedToken.equals(session.actionToken())) {
                // token 检查失败（并发冲突/token 轮换在途）静默丢弃，
                // 不再提示"写信操作令牌无效"——这些场景对正常玩家都是误报，UI 防抖会阻止重复点击。
                return;
            }
            session.tokenInFlight = true;
        }
        List<String> request = new ArrayList<>(data);
        List<ItemStack> attachments = session.attachments().stream().map(item -> item == null ? null : item.clone()).toList();
        String feeCurrency = session.feeCurrency();
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        passwordAccess.statusAsync(player).whenComplete((status, failure) -> {
            if (!active) return;
            onMain(() -> {
                if (!currentCompose(player, session)) { session.tokenInFlight = false; return; }
                var checked = failure == null && status != null ? status : SecondaryPasswordAccess.Status.LOCKED;
                if (session.pendingSend != null || !checked.unlocked() || !configuration.playerSend().enabled()
                        || (configuration.playerSend().requirePermission() && !player.hasPermission("arcartxsuite.mail.send"))
                        || normalizeRecipientInput(request.get(2)).isBlank()) {
                    finishComposeRequest(player, session, request, checked, null, admission, attachments, feeCurrency, null);
                    return;
                }
                String requested = normalizeRecipientInput(request.get(2));
                RecipientResolution online = resolveOnlineRecipient(requested);
                UUID sender = player.getUniqueId();
                submitComposeStorage(() -> new ComposeLookup(online == null ? resolveStoredRecipient(requested) : online,
                        repository.loadPlayerProfile(sender).map(MailPlayerProfile::lastSendAt).orElse(null)),
                    (lookup, error) -> finishComposeRequest(player, session, request, checked, lookup, admission,
                        attachments, feeCurrency, error));
            });
        });
    }

    private record ComposeLookup(RecipientResolution recipient, Instant lastSent) { }

    private <T> void submitComposeStorage(java.util.concurrent.Callable<T> work, BiConsumer<T, Exception> callback) {
        try {
            background.execute(() -> {
                try {
                    T result = work.call();
                    onMain(() -> callback.accept(result, null));
                } catch (Exception failure) {
                    onMain(() -> callback.accept(null, failure));
                } catch (LinkageError failure) {
                    onMain(() -> callback.accept(null, new IllegalStateException(failure)));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            callback.accept(null, stopped);
        }
    }

    private void finishComposeRequest(Player player, ComposeSession session, List<String> request,
                                      SecondaryPasswordAccess.Status status, ComposeLookup lookup, AuthSnapshot admission,
                                      List<ItemStack> attachments, String feeCurrency, Exception failure) {
        try {
            if (!currentCompose(player, session)) return;
            AuthSnapshot current = AuthAccess.snapshot(player.getUniqueId());
            if (!current.admitted() || !admission.admitted()
                    || current.generation() != admission.generation()
                    || !java.util.Objects.equals(current.connectionId(), admission.connectionId())
                    || !attachments.equals(session.attachments()) || !java.util.Objects.equals(feeCurrency, session.feeCurrency())) {
                sendPlayerResult(player, MailOperationResult.failure(message("player.compose-session-expired")));
                return;
            }
            if (failure != null) {
                logger.warning("读取寄信资料失败: " + failure.getMessage());
                sendPlayerResult(player, MailOperationResult.failure(message("player.mail-send-failed")));
                return;
            }
            sendPlayerResult(player, handleComposeSendInternal(player, request, status, lookup));
        } finally {
            session.tokenInFlight = false;
        }
    }

    private boolean currentCompose(Player player, ComposeSession session) {
        UUID id = player.getUniqueId();
        return active && Bukkit.getPlayer(id) == player && player.isOnline() && composeSessions.get(id) == session;
    }

    private MailOperationResult handleComposeSendInternal(Player player, List<String> data, SecondaryPasswordAccess.Status status,
                                                            ComposeLookup lookup) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.compose-session-missing"));
        }
        if (!session.sessionId().toString().equalsIgnoreCase(MailTexts.safe(data.get(1)))) {
            return MailOperationResult.failure(message("player.compose-session-expired"));
        }

        if (session.pendingSend != null) return resolveComposeSend(player, session);
        if (draftIo.contains(player.getUniqueId())) return attachmentsProcessing();
        if (session.draftReview) return MailOperationResult.failure(message("player.draft-return-review"));

        if (!status.unlocked()) {
            composePasswordPanels.add(player.getUniqueId());
            MailOperationResult result = MailOperationResult.failure(message("player.compose-password-required"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }

        if (!configuration.playerSend().enabled()) {
            MailOperationResult result = MailOperationResult.failure(message("player.compose-disabled"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        if (configuration.playerSend().requirePermission() && !player.hasPermission("arcartxsuite.mail.send")) {
            MailOperationResult result = MailOperationResult.failure(message("player.compose-no-permission"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }

        String recipientInput = normalizeRecipientInput(data.get(2));
        String subject = MailTexts.crop(data.get(3), configuration.playerSend().subjectMaxLength());
        String body = MailTexts.crop(data.get(4), configuration.playerSend().bodyMaxLength());
        if (recipientInput.isBlank()) {
            MailOperationResult result = MailOperationResult.failure(message("player.recipient-required"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        if (subject.isBlank()) {
            MailOperationResult result = MailOperationResult.failure(message("player.subject-required"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        var moderatedSubject = contentModerator.filter(subject);
        var moderatedBody = contentModerator.filter(body);
        if (moderatedSubject.blocked() || moderatedBody.blocked() || moderatedSubject.text().isBlank()) {
            MailOperationResult result = MailOperationResult.failure(message("player.body-blocked"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        subject = MailTexts.crop(moderatedSubject.text(), configuration.playerSend().subjectMaxLength());
        body = MailTexts.crop(moderatedBody.text(), configuration.playerSend().bodyMaxLength());
        int maxAttachmentSlots = configuration.playerSend().maxAttachments();
        if (session.attachments().size() > maxAttachmentSlots) {
            MailOperationResult result = MailOperationResult.failure(message("player.attachment-slot-limit", maxAttachmentSlots));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }

        try {
            if (lookup == null) return MailOperationResult.failure(message("player.compose-session-expired"));
            RecipientResolution recipient = lookup.recipient() == null ? resolveOfflineRecipient(recipientInput) : lookup.recipient();
            if (recipient == null) {
                MailOperationResult result = MailOperationResult.failure(message("player.recipient-not-found", recipientInput));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }
            if (!configuration.playerSend().allowSelfSend() && player.getUniqueId().equals(recipient.playerUuid())) {
                MailOperationResult result = MailOperationResult.failure(message("player.compose-self-not-allowed"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }
            boolean recipientOnline = isPlayerOnline(recipient.playerUuid());
            if (!recipientOnline && !configuration.playerSend().allowOfflineSend()) {
                MailOperationResult result = MailOperationResult.failure(message("player.compose-offline-not-allowed"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }

            if (lookup.lastSent() != null) {
                long cooldownSeconds = configuration.playerSend().cooldownSeconds();
                Instant nextAvailableAt = lookup.lastSent().plusSeconds(cooldownSeconds);
                if (cooldownSeconds > 0 && nextAvailableAt.isAfter(Instant.now())) {
                    MailOperationResult result = MailOperationResult.failure(
                        message("player.compose-cooldown", TIME_FORMATTER.format(nextAvailableAt))
                    );
                    pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                    return result;
                }
            }

            List<ItemStack> attachmentItems = collectComposeItems(session);
            MailOperationResult attachmentValidation = validateAttachmentItems(attachmentItems);
            if (!attachmentValidation.success()) {
                pushComposeQuote(player, MailSendQuote.failure(attachmentValidation.message(), session.feeCurrency()));
                return attachmentValidation;
            }

            BigDecimal vaultAmount = MailTexts.parseCurrencyAmount(data.get(5));
            if (vaultAmount == null) {
                MailOperationResult result = MailOperationResult.failure(message("player.currency-amount-invalid"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }
            if (vaultAmount.compareTo(BigDecimal.ZERO) > 0 && !configuration.playerSend().allowVaultAttachment()) {
                MailOperationResult result = MailOperationResult.failure(message("player.currency-attachment-disabled"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }

            Map<String, BigDecimal> attachmentAmounts = new LinkedHashMap<>();
            if (vaultAmount.compareTo(BigDecimal.ZERO) > 0) {
                attachmentAmounts.put("money", vaultAmount);
            }
            MailSendQuote quote = calculateComposeQuote(configuration.playerSend(), attachmentAmounts, attachmentItems.size(), session.feeCurrency());
            pushComposeQuote(player, quote);

            MailOperationResult paymentValidation = validateSenderPayment(player, quote);
            if (!paymentValidation.success()) {
                pushComposeQuote(player, MailSendQuote.failure(paymentValidation.message(), session.feeCurrency()));
                return paymentValidation;
            }

            List<MailAttachment> attachments;
            try {
                attachments = buildComposeAttachments(attachmentItems, attachmentAmounts);
            } catch (IOException exception) {
                this.logger.warning("序列化玩家邮件附件失败: " + exception.getMessage());
                MailOperationResult result = MailOperationResult.failure(message("player.attachment-save-failed"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }
            Instant now = Instant.now();
            MailMessage mail = new MailMessage(
                0L,
                recipient.playerUuid(),
                player.getUniqueId(),
                player.getName(),
                MailSourceType.PLAYER,
                "",
                "",
                subject,
                body,
                MailStatus.UNREAD,
                attachments,
                List.of(),
                List.of(),
                now,
                now.plusSeconds(configuration.retention().defaultExpireAfterDays() * 86400L),
                now,
                null,
                null
            );

            String key = (session.sessionId().toString() + UUID.randomUUID()).replace("-", "");
            var pending = new PendingComposeSend(key, mail, requiredCurrencyTotal(quote), recipient.lastKnownName());
            session.pendingSend = pending;
            beginComposeSend(player, session, pending, status, !attachmentItems.isEmpty());
            return MailOperationResult.success(message("player.compose-send-processing"));
        } catch (Exception exception) {
            this.logger.warning("发送玩家邮件失败: " + exception.getMessage());
            MailOperationResult result = MailOperationResult.failure(message("player.mail-send-failed"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
    }

    private record ComposeAuthorization(AuthSnapshot admission, SecondaryPasswordAccess.Status password) { }

    private boolean canContinueCompose(Player player, ComposeSession session, ComposeAuthorization access) {
        if (!currentCompose(player, session) || !access.password().unlocked() || !configuration.playerSend().enabled()) return false;
        return currentMailAdmission(player, access.admission())
            && (!configuration.playerSend().requirePermission() || player.hasPermission("arcartxsuite.mail.send"));
    }

    private void beginComposeSend(Player player, ComposeSession session, PendingComposeSend pending,
                                  SecondaryPasswordAccess.Status password, boolean hasItems) {
        var access = new ComposeAuthorization(AuthAccess.snapshot(player.getUniqueId()), password);
        var costs = pending.planned.entrySet().iterator();
        pending.phase = ComposeSendPhase.CHARGING;
        if (!hasItems && pending.planned.values().stream().noneMatch(amount -> amount.signum() > 0)) {
            chargeNextComposeCurrency(player, session, pending, access, costs);
            return;
        }
        pending.phase = ComposeSendPhase.SAVING_ATTACHMENTS;
        String live = hasItems ? liveReceipt(player, MailDraftAttachments.RECEIPT) : null;
        UUID owner = pending.mail.senderUuid();
        String evidence = composeEvidence(pending, ComposeSendPhase.SAVING_ATTACHMENTS);
        var send = new MailDraftAttachments.Handoff(pending.key, pending.mail.ownerUuid(), !hasItems);
        submitComposeStorage(() -> {
            writeComposeFile(composeReviewFile(pending.key), evidence);
            draftAttachments.handoff(owner, live, send);
            return null;
        }, (ignored, error) -> {
            if (error != null) {
                pending.phase = ComposeSendPhase.ATTACHMENT_REVIEW;
                logger.warning("寄信交接待核对: key=" + pending.key + "，" + error.getMessage());
                if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
                return;
            }
            pending.draftLinked = true;
            if (hasItems && currentCompose(player, session)
                    && !java.util.Objects.equals(live, liveReceipt(player, MailDraftAttachments.RECEIPT))) {
                pending.phase = ComposeSendPhase.ATTACHMENT_REVIEW;
                sendPlayerResult(player, composeSendReview(pending));
                return;
            }
            pending.phase = ComposeSendPhase.CHARGING;
            chargeNextComposeCurrency(player, session, pending, access, costs);
        });
    }

    private void chargeNextComposeCurrency(Player player, ComposeSession session, PendingComposeSend pending,
                                           ComposeAuthorization access, java.util.Iterator<Map.Entry<String, BigDecimal>> costs) {
        if (!canContinueCompose(player, session, access)) {
            cancelUnsentCompose(player, session, pending);
            return;
        }
        while (costs.hasNext()) {
            var cost = costs.next();
            if (cost.getValue().signum() <= 0) continue;
            String currency = cost.getKey();
            pending.attemptedCurrency = currency;
            persistComposeReview(pending, ComposeSendPhase.CHARGING, failure -> {
                if (failure != null || !canContinueCompose(player, session, access)) {
                    pending.attemptedCurrency = "";
                    cancelUnsentCompose(player, session, pending);
                    return;
                }
                CurrencyTransactionResult receipt;
                try {
                    var wallet = currencyBridgeManager.bridge(currency);
                    if (wallet == null || !wallet.available()) {
                        pending.attemptedCurrency = "";
                        cancelUnsentCompose(player, session, pending);
                        return;
                    }
                    receipt = wallet.withdraw(player, cost.getValue());
                    if (receipt == null || receipt.uncertain() || (receipt.success() && wallet.debitRequiresReconciliation())) {
                        composeDebitReview(player, session, pending);
                        return;
                    }
                } catch (RuntimeException | LinkageError error) {
                    logger.warning("寄信扣款未确认: key=" + pending.key + "，" + error.getMessage());
                    composeDebitReview(player, session, pending);
                    return;
                }
                pending.attemptedCurrency = "";
                if (!receipt.success()) {
                    cancelUnsentCompose(player, session, pending);
                    return;
                }
                pending.charged.put(currency, cost.getValue());
                chargeNextComposeCurrency(player, session, pending, access, costs);
            });
            return;
        }
        insertComposeMail(player, session, pending);
    }

    private void composeDebitReview(Player player, ComposeSession session, PendingComposeSend pending) {
        pending.phase = ComposeSendPhase.DEBIT_REVIEW;
        persistComposeReview(pending, ComposeSendPhase.DEBIT_REVIEW, ignored -> {
            if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
        });
    }

    private void cancelUnsentCompose(Player player, ComposeSession session, PendingComposeSend pending) {
        if (pending.charged.isEmpty() && !pending.draftLinked) {
            session.pendingSend = null;
            submitComposeStorage(() -> Files.deleteIfExists(composeReviewFile(pending.key)), (ignored, failure) -> {
                if (failure != null) logger.warning("无法清理未扣款寄信凭证: " + pending.key);
            });
            if (currentCompose(player, session)) {
                var result = MailOperationResult.failure(message("player.charge-failed"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                sendPlayerResult(player, result);
            }
            return;
        }
        pending.phase = ComposeSendPhase.RESOLVING;
        // No debit is left unconfirmed. Persist the known cancellation before any offline recovery.
        persistComposeReview(pending, ComposeSendPhase.SENDING, ignored -> {
            pending.phase = ComposeSendPhase.SENDING;
            if (currentCompose(player, session)) sendPlayerResult(player, resolveComposeSend(player, session));
        });
    }

    private void insertComposeMail(Player player, ComposeSession session, PendingComposeSend pending) {
        pending.phase = ComposeSendPhase.INSERTING;
        var sender = profileSnapshot(player, true);
        String evidence = composeEvidence(pending, ComposeSendPhase.SENDING);
        submitComposeStorage(() -> {
            writeComposeFile(composeReviewFile(pending.key), evidence);
            repository.insertDurableMail(pending.key, pending.mail);
            recordComposeSent(sender, pending);
            return null;
        }, (ignored, failure) -> {
            if (failure == null) {
                MailOperationResult result = finishComposeSend(player, session, pending);
                if (currentMailPlayer(player)) sendPlayerResult(player, result);
            } else {
                pending.phase = ComposeSendPhase.SENDING;
                logger.warning("玩家寄信写入未确认: key=" + pending.key + "，" + failure.getMessage());
                if (currentCompose(player, session)) sendPlayerResult(player, resolveComposeSend(player, session));
            }
        });
    }

    private MailOperationResult resolveComposeSend(Player player, ComposeSession session) {
        PendingComposeSend pending = session.pendingSend;
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!currentMailAdmission(player, admission)) return composeSendReview(pending);
        if (pending.phase == ComposeSendPhase.RETURN_PENDING) return returnConfirmedComposeItems(player, admission, session, pending);
        if (pending.phase != ComposeSendPhase.SENDING) return composeSendReview(pending);
        pending.phase = ComposeSendPhase.RESOLVING;
        var sender = profileSnapshot(player, true);
        submitComposeStorage(() -> {
            long mailId = repository.resolvePlayerSend(pending.key, pending.mail.ownerUuid());
            if (mailId > 0) recordComposeSent(sender, pending);
            return mailId;
        }, (mailId, failure) -> {
            if (failure != null) {
                pending.phase = ComposeSendPhase.SENDING;
                logger.warning("玩家寄信仍待核对: key=" + pending.key + "，" + failure.getMessage());
                if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
            } else if (mailId > 0) {
                MailOperationResult result = finishComposeSend(player, session, pending);
                if (Bukkit.getPlayer(sender.playerUuid()) == player && player.isOnline()) sendPlayerResult(player, result);
            } else {
                pending.phase = ComposeSendPhase.SENDING;
                if (currentCompose(player, session) && currentMailAdmission(player, admission)) {
                    sendPlayerResult(player, refundConfirmedUnsent(player, admission, session, pending));
                }
            }
        });
        return composeSendReview(pending);
    }

    private void recordComposeSent(MailPlayerProfile sender, PendingComposeSend pending) {
        saveProfile(sender);
        appendLog(sender.playerUuid(), "send", "寄给 " + pending.recipientName + " 的邮件: " + pending.mail.subject());
    }

    private MailOperationResult refundConfirmedUnsent(Player player, AuthSnapshot admission, ComposeSession session,
                                                      PendingComposeSend pending) {
        pending.phase = ComposeSendPhase.REFUND_PENDING;
        java.util.function.Consumer<Exception> refund = failure -> {
            if (failure != null || !currentCompose(player, session) || !currentMailAdmission(player, admission)) {
                pending.phase = ComposeSendPhase.RESOLVING;
                // No refund call ran. Release the intent so a later login can resume it.
                persistComposeReview(pending, ComposeSendPhase.SENDING, ignored -> {
                    pending.phase = ComposeSendPhase.SENDING;
                    if (currentCompose(player, session) && currentMailAdmission(player, admission)) {
                        sendPlayerResult(player, composeSendReview(pending));
                    }
                });
                return;
            }
            pending.refundConfirmed = refundSender(player, pending.charged, () -> currentMailAdmission(player, admission));
            pending.phase = ComposeSendPhase.RETURN_PENDING;
            if (currentMailAdmission(player, admission)) {
                sendPlayerResult(player, returnConfirmedComposeItems(player, admission, session, pending));
            } else {
                persistComposeReview(pending, ComposeSendPhase.RETURN_PENDING, ignored -> { });
            }
        };
        if (pending.charged.isEmpty()) refund.accept(null);
        else persistComposeReview(pending, ComposeSendPhase.REFUND_PENDING, refund);
        return attachmentsProcessing();
    }

    private MailOperationResult returnConfirmedComposeItems(Player player, AuthSnapshot admission, ComposeSession session,
                                                            PendingComposeSend pending) {
        pending.phase = ComposeSendPhase.RETURNING;
        if (session.attachments().isEmpty()) {
            finishUnsentCompose(player, session, pending);
            return attachmentsProcessing();
        }
        ReturnInventory inventory = prepareAttachmentReturn(player, session.attachments());
        if (inventory == null) {
            persistComposeReview(pending, ComposeSendPhase.RETURN_PENDING,
                ignored -> pending.phase = ComposeSendPhase.RETURN_PENDING);
            return MailOperationResult.failure(message("player.compose-return-no-space", pending.key));
        }
        pending.returnStart = UUID.randomUUID().toString();
        persistComposeReview(pending, ComposeSendPhase.RETURNING, failure -> {
            if (failure != null || !canApplyInventory(player, admission, session, inventory)) {
                persistComposeReview(pending, ComposeSendPhase.RETURN_PENDING, ignored -> {
                    pending.phase = ComposeSendPhase.RETURN_PENDING;
                    if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
                });
                return;
            }
            saveComposeReturn(player, inventory, pending.key + ":returned", pending.returnStart,
                () -> canApplyInventory(player, admission, session, inventory), error -> {
                if (error instanceof java.util.concurrent.CancellationException) {
                    persistComposeReview(pending, ComposeSendPhase.RETURN_PENDING, ignored -> {
                        pending.phase = ComposeSendPhase.RETURN_PENDING;
                        if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
                    });
                } else if (error != null) {
                    pending.phase = ComposeSendPhase.RETURN_REVIEW;
                    logger.warning("附件退回存档待核对: " + pending.key + "，" + error.getMessage());
                    persistComposeReview(pending, ComposeSendPhase.RETURN_REVIEW, ignored -> {
                        if (currentCompose(player, session)) sendPlayerResult(player, composeSendReview(pending));
                    });
                } else {
                    session.attachments().clear();
                    if (currentCompose(player, session)) {
                        player.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + message("player.compose-attachments-returned"));
                    }
                    finishUnsentCompose(player, session, pending);
                }
            });
        });
        return attachmentsProcessing();
    }

    private void finishUnsentCompose(Player player, ComposeSession session, PendingComposeSend pending) {
        pending.attachmentsReturned = true;
        String refundEvidence = pending.refundConfirmed ? null : composeEvidence(pending, ComposeSendPhase.REFUND_REVIEW);
        submitComposeStorage(() -> {
            if (refundEvidence != null) writeComposeFile(composeReviewFile(pending.key), refundEvidence);
            if (pending.draftLinked) draftAttachments.finishHandoff(pending.mail.senderUuid(), pending.key);
            if (refundEvidence == null) Files.deleteIfExists(composeReviewFile(pending.key));
            return null;
        }, (ignored, failure) -> {
            if (failure == null) session.pendingSend = null;
            else {
                pending.phase = ComposeSendPhase.RETURN_REVIEW;
                logger.warning("已退回的附件凭证待清理: key=" + pending.key + "，" + failure.getMessage());
            }
            if (currentCompose(player, session)) {
                var result = failure != null ? composeSendReview(pending) : MailOperationResult.failure(pending.refundConfirmed
                    ? message("player.mail-send-failed") : message("player.compose-refund-review", pending.key));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                sendPlayerResult(player, result);
            }
        });
    }

    private MailOperationResult composeSendReview(PendingComposeSend pending) {
        return MailOperationResult.failure(message(switch (pending.phase) {
            case DEBIT_REVIEW -> "player.compose-debit-review";
            case RETURN_REVIEW -> "player.compose-return-review";
            case SAVING_ATTACHMENTS, CHARGING, INSERTING, RESOLVING, REFUND_PENDING, RETURNING -> "player.compose-send-processing";
            default -> "player.compose-send-review";
        }, pending.key));
    }

    private Path composeReviewFile(String key) {
        return baseDataDir.toPath().resolve("send-review").resolve(key + ".json");
    }

    private static String composeEvidence(PendingComposeSend pending, ComposeSendPhase phase) {
        var mail = pending.mail;
        return new Gson().toJson(Map.ofEntries(
                Map.entry("operation", pending.key), Map.entry("status", phase.name()),
                Map.entry("time", mail.createdAt().toString()), Map.entry("sender", mail.senderUuid()),
                Map.entry("recipient", mail.ownerUuid()), Map.entry("charges", pending.charged),
                Map.entry("plannedCharges", pending.planned), Map.entry("attemptedCurrency", pending.attemptedCurrency),
                Map.entry("attachmentReceipt", COMPOSE_ITEM_RECEIPT.toString()),
                Map.entry("returnStart", pending.returnStart),
                Map.entry("attachments", mail.attachments()), Map.entry("attachmentsReturned", pending.attachmentsReturned),
                Map.entry("refundConfirmed", pending.refundConfirmed)));
    }

    private void persistComposeReview(PendingComposeSend pending, ComposeSendPhase phase,
                                      java.util.function.Consumer<Exception> complete) {
        String evidence = composeEvidence(pending, phase);
        submitComposeStorage(() -> {
            writeComposeFile(composeReviewFile(pending.key), evidence);
            return null;
        }, (ignored, failure) -> {
            if (failure != null) logger.warning("无法保存寄信核对资料: key=" + pending.key + "，" + failure.getMessage());
            complete.accept(failure);
        });
    }

    private static void writeComposeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "send-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try (var channel = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private MailOperationResult finishComposeSend(Player player, ComposeSession session, PendingComposeSend pending) {
        session.sent = true;
        session.pendingSend = null;
        session.attachments().clear();
        composeSessions.remove(player.getUniqueId(), session);
        composePasswordPanels.remove(player.getUniqueId());
        UUID owner = pending.mail.senderUuid();
        if (pending.draftLinked) draftIo.add(owner);
        submitComposeStorage(() -> {
            if (pending.draftLinked) draftAttachments.finishHandoff(owner, pending.key);
            Files.deleteIfExists(composeReviewFile(pending.key));
            return null;
        }, (ignored, failure) -> {
            if (pending.draftLinked) {
                draftIo.remove(owner);
                resumeAdmittedDraft(owner);
            }
            if (failure != null) logger.warning("寄信已结案，附件凭证待清理: key=" + pending.key + "，" + failure.getMessage());
        });
        try {
            notifyDurableMail(pending.mail);
            onMain(() -> {
                if (Bukkit.getPlayer(player.getUniqueId()) == player && player.isOnline()
                        && !composeSessions.containsKey(player.getUniqueId())) openInbox(player);
            });
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("玩家邮件已寄出，通知失败: key=" + pending.key + "，" + failure.getMessage());
        }
        return MailOperationResult.success(message("player.mail-sent-to", pending.recipientName));
    }

    private void handlePasswordUnlock(Player player, List<String> data) {
        UUID playerId = player.getUniqueId();
        ComposeSession session = composeSessions.get(playerId);
        if (session == null) {
            sendPlayerResult(player, MailOperationResult.failure(message("player.compose-session-missing")));
            return;
        }

        SecondaryPasswordAccess passwordAccess = secondaryPasswordAccess();
        if (passwordAccess == null) {
            sendPlayerResult(player, MailOperationResult.failure(message("player.compose-password-unavailable")));
            return;
        }

        String password = data != null && data.size() >= 2 ? MailTexts.safe(data.get(1)) : "";
        passwordAccess.verifyAsync(player, password).whenComplete((verified, failure) -> {
            if (!active) return;
            onMain(() -> {
                ComposeSession current = composeSessions.get(playerId);
                if (!active || Bukkit.getPlayer(playerId) != player || !player.isOnline()
                    || current == null || !session.sessionId().equals(current.sessionId())) return;
                if (failure == null && Boolean.TRUE.equals(verified)) {
                    composePasswordPanels.remove(playerId);
                    pushComposeQuote(player, calculateCurrentComposeQuote(player));
                    sendPlayerResult(player, MailOperationResult.success(message("player.compose-password-unlocked")));
                } else {
                    composePasswordPanels.add(playerId);
                    String error = message("player.compose-password-invalid");
                    pushComposeQuote(player, MailSendQuote.failure(error, current.feeCurrency()));
                    sendPlayerResult(player, MailOperationResult.failure(error));
                }
            });
        });
    }

    private MailOperationResult handleComposeAddAttachment(Player player, int slot, String countStr) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session != null && session.pendingSend != null) {
            return composeSendReview(session.pendingSend);
        }
        if (session == null || session.sent()) {
            return MailOperationResult.failure(message("player.compose-not-open"));
        }
        if (session.draftReview) return MailOperationResult.failure(message("player.draft-return-review"));
        if (slot < 9 || slot > 44) {
            return MailOperationResult.failure(message("player.attachment-slot-invalid"));
        }
        // ArcartX Backpack slot id（9~44）需转换为 Bukkit PlayerInventory slot index
        int bukkitSlot = MailTexts.arcartxSlotToBukkit(slot);
        ItemStack item = player.getInventory().getItem(bukkitSlot);
        if (item == null || item.getType().isAir()) {
            return MailOperationResult.failure(message("player.attachment-slot-empty"));
        }
        if (session.attachments().size() >= configuration.playerSend().maxAttachments()) {
            return MailOperationResult.failure(message("player.attachment-slot-limit", configuration.playerSend().maxAttachments()));
        }
        int count = "all".equalsIgnoreCase(countStr) ? item.getAmount() : 1;
        count = Math.min(count, item.getAmount());
        // 验证物品
        ItemStack toAdd = item.clone();
        toAdd.setAmount(count);
        List<ItemStack> singleItem = new ArrayList<>();
        singleItem.add(toAdd);
        MailOperationResult validation = validateAttachmentItems(singleItem);
        if (!validation.success()) {
            return validation;
        }
        ItemStack[] inventory = InventoryItems.copy(player.getInventory().getStorageContents());
        if (item.getAmount() == count) inventory[bukkitSlot] = null;
        else inventory[bukkitSlot].setAmount(item.getAmount() - count);
        List<ItemStack> attachments = new ArrayList<>(session.attachments());
        attachments.add(toAdd);
        return changeDraftAttachments(player, session, inventory, attachments, false);
    }

    private MailOperationResult handleComposeRemoveAttachment(Player player, int index) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session != null && session.pendingSend != null) {
            return composeSendReview(session.pendingSend);
        }
        if (session == null || session.sent()) {
            return MailOperationResult.failure(message("player.compose-not-open"));
        }
        if (session.draftReview) return MailOperationResult.failure(message("player.draft-return-review"));
        if (index < 0 || index >= session.attachments().size()) {
            return MailOperationResult.failure(message("player.attachment-index-invalid"));
        }
        List<ItemStack> attachments = new ArrayList<>(session.attachments());
        ItemStack item = attachments.remove(index);
        ItemStack[] inventory = InventoryItems.copy(player.getInventory().getStorageContents());
        if (!InventoryItems.put(inventory, item)) return MailOperationResult.failure(message("player.draft-return-no-space"));
        return changeDraftAttachments(player, session, inventory, attachments, true);
    }

    private MailOperationResult changeDraftAttachments(Player player, ComposeSession session, ItemStack[] inventory,
                                                       List<ItemStack> items, boolean returning) {
        UUID uuid = session.playerUuid();
        AuthSnapshot admission = AuthAccess.snapshot(uuid);
        if (!currentMailAdmission(player, admission)) {
            return MailOperationResult.failure(message("player.compose-session-expired"));
        }
        if (!draftIo.add(uuid)) return attachmentsProcessing();
        var plan = new ReturnInventory(InventoryItems.copy(player.getInventory().getStorageContents()), inventory);
        mutateDraft(player, admission, session, plan, items, returning, failure -> {
            draftIo.remove(uuid);
            if (failure == null) {
                session.attachments().clear();
                session.attachments().addAll(items);
            } else if (!(failure instanceof java.util.concurrent.CancellationException)) {
                session.draftReview = true;
            }
            if (session.returnDraftWhenReady) {
                if (composeSessions.remove(uuid, session) && currentMailPlayer(player)) returnComposeItems(player, session);
                resumeAdmittedDraft(uuid);
                return;
            }
            if (currentCompose(player, session)) {
                if (failure == null) refreshCompose(player, false);
                else if (failure instanceof java.util.concurrent.CancellationException) {
                    sendPlayerResult(player, MailOperationResult.failure(message("player.compose-session-expired")));
                } else sendPlayerResult(player, draftRecoveryError(uuid, failure));
            }
            resumeAdmittedDraft(uuid);
        });
        return attachmentsProcessing();
    }

    private boolean canApplyInventory(Player player, AuthSnapshot admission, ComposeSession session, ReturnInventory inventory) {
        return currentMailAdmission(player, admission)
            && (session == null || (currentCompose(player, session) && !session.returnDraftWhenReady))
            && java.util.Arrays.equals(inventory.before(), player.getInventory().getStorageContents());
    }

    private void mutateDraft(Player player, AuthSnapshot admission, ComposeSession session, ReturnInventory inventory, List<ItemStack> items,
                             boolean returning, java.util.function.Consumer<Exception> complete) {
        UUID uuid = player.getUniqueId();
        List<String> encoded = new ArrayList<>(items.size());
        try {
            for (ItemStack item : items) {
                String data = MailItemSerializer.serialize(item);
                if (data.isEmpty()) throw new IOException("写信附件无法序列化");
                encoded.add(data);
            }
        } catch (IOException | RuntimeException failure) {
            complete.accept(failure);
            return;
        }
        String live = liveReceipt(player, MailDraftAttachments.RECEIPT);
        submitComposeStorage(() -> draftAttachments.prepare(uuid, live, encoded, returning), (change, failure) -> {
            if (failure != null) {
                complete.accept(failure);
                return;
            }
            BooleanSupplier unchanged = () -> canApplyInventory(player, admission, session, inventory)
                && java.util.Objects.equals(live, liveReceipt(player, MailDraftAttachments.RECEIPT));
            java.util.function.Consumer<Exception> apply = error -> {
                if (error instanceof java.util.concurrent.CancellationException) {
                    submitComposeStorage(() -> {
                        draftAttachments.abandon(change);
                        return null;
                    }, (ignored, failed) -> complete.accept(failed == null ? error : failed));
                    return;
                }
                if (error != null) {
                    complete.accept(error);
                    return;
                }
                try {
                    player.getInventory().setStorageContents(inventory.after());
                    PlayerDataReceipt.save(player, MailDraftAttachments.RECEIPT, change.receipt());
                } catch (RuntimeException | LinkageError failureToSave) {
                    complete.accept(new IOException("附件背包保存未确认", failureToSave));
                    return;
                }
                submitComposeStorage(() -> {
                    draftAttachments.confirm(change);
                    return null;
                }, (ignored, failed) -> complete.accept(failed));
            };
            if (returning) confirmAttachmentReturnStart(player, change.receipt(), unchanged, apply);
            else apply.accept(unchanged.getAsBoolean() ? null : attachmentReturnCancelled());
        });
    }

    private static java.util.concurrent.CancellationException attachmentReturnCancelled() {
        return new java.util.concurrent.CancellationException("背包或写信会话已变化");
    }

    private java.util.concurrent.CancellationException attachmentReturnNotStarted(UUID uuid, Throwable failure) {
        logger.warning("附件退回尚未执行: player=" + uuid + "，" + failure.getMessage());
        var cancelled = new java.util.concurrent.CancellationException("附件退回开始凭证未确认");
        cancelled.initCause(failure);
        return cancelled;
    }

    private void confirmAttachmentReturnStart(Player player, String start, BooleanSupplier unchanged,
                                              java.util.function.Consumer<Exception> complete) {
        if (!unchanged.getAsBoolean()) {
            complete.accept(attachmentReturnCancelled());
            return;
        }
        UUID uuid = player.getUniqueId();
        // A return may touch the inventory only after its start token is durable.
        try {
            PlayerDataReceipt.save(player, MailDraftAttachments.RETURN_STARTED, start);
        } catch (RuntimeException | LinkageError failure) {
            complete.accept(attachmentReturnNotStarted(uuid, failure));
            return;
        }
        submitComposeStorage(() -> {
            if (!draftAttachments.returnStarted(uuid, start)) throw new IOException("附件退回开始凭证未确认");
            return null;
        }, (ignored, failure) -> {
            if (failure != null) complete.accept(attachmentReturnNotStarted(uuid, failure));
            else complete.accept(unchanged.getAsBoolean() ? null : attachmentReturnCancelled());
        });
    }

    private void saveComposeReturn(Player player, ReturnInventory inventory, String token, String start,
                                   BooleanSupplier unchanged,
                                   java.util.function.Consumer<Exception> complete) {
        confirmAttachmentReturnStart(player, start, unchanged, error -> {
            if (error != null) complete.accept(error);
            else applyComposeReturn(player, inventory, token, complete);
        });
    }

    private void applyComposeReturn(Player player, ReturnInventory inventory, String token,
                                   java.util.function.Consumer<Exception> complete) {
        UUID uuid = player.getUniqueId();
        try {
            player.getInventory().setStorageContents(inventory.after());
            PlayerDataReceipt.save(player, COMPOSE_ITEM_RECEIPT, token);
        } catch (RuntimeException | LinkageError failure) {
            complete.accept(new IOException("附件退回存档未确认", failure));
            return;
        }
        submitComposeStorage(() -> {
            String saved = PlayerDataReceipt.read(playerData.resolve(uuid + ".dat"), COMPOSE_ITEM_RECEIPT.toString());
            if (!token.equals(saved)) throw new IOException("附件退回存档未确认");
            return null;
        }, (ignored, failure) -> complete.accept(failure));
    }

    private MailOperationResult handleAdminAddAttachment(Player player, int slot, String countStr) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.admin-session-missing"));
        }
        if (slot < 9 || slot > 44) {
            return MailOperationResult.failure(message("player.attachment-slot-invalid"));
        }
        // ArcartX Backpack slot id（9~44）需转换为 Bukkit PlayerInventory slot index
        int bukkitSlot = MailTexts.arcartxSlotToBukkit(slot);
        ItemStack item = player.getInventory().getItem(bukkitSlot);
        if (item == null || item.getType().isAir()) {
            return MailOperationResult.failure(message("player.attachment-slot-empty"));
        }
        if (session.attachments().size() >= adminAttachmentSlots()) {
            return MailOperationResult.failure(message("player.admin-attachment-slot-full"));
        }
        int count = "all".equalsIgnoreCase(countStr) ? item.getAmount() : 1;
        count = Math.min(count, item.getAmount());
        ItemStack toAdd = item.clone();
        toAdd.setAmount(count);
        // 从玩家背包移除
        item.setAmount(item.getAmount() - count);
        if (item.getAmount() <= 0) {
            player.getInventory().setItem(bukkitSlot, null);
        } else {
            player.getInventory().setItem(bukkitSlot, item);
        }
        // 添加到附件列表
        session.attachments().add(toAdd);
        // 刷新 admin UI
        refreshAdminAttachments(player);
        return MailOperationResult.success(message("player.admin-attachment-added"));
    }

    private MailOperationResult handleAdminRemoveAttachment(Player player, int index) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.admin-session-missing"));
        }
        if (index < 0 || index >= session.attachments().size()) {
            return MailOperationResult.failure(message("player.attachment-index-invalid"));
        }
        ItemStack item = session.attachments().remove(index);
        if (item != null && !item.getType().isAir() && !isTemplateItem(item)) {
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        refreshAdminAttachments(player);
        return MailOperationResult.success(message("player.admin-attachment-removed"));
    }

    /**
     * 刷新 compose UI：发送 update 包，含报价、附件和背包数据。
     */
    private void refreshCompose(Player player, boolean initPacket) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) return;
        MailSendQuote quote = calculateComposeQuote(
            configuration.playerSend(),
            Map.of(),
            session.attachments().size(),
            session.feeCurrency()
        );
        pushComposeQuote(player, quote);
    }

    /**
     * 刷新 admin UI 附件区域：发送 attachment-count + attachment_jsons。
     */
    private void refreshAdminAttachments(Player player) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;
        int count = session.attachments().size();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemAttachmentCount", count);
        payload.put("attachment_jsons", serializeAttachments(session.attachments()));
        payload.put("attachment_amounts", MailItemPreview.amounts(session.attachments()));
        bridge.sendPacket(player, adminUiId, "attachment-count", payload);
    }

    private String requiredUiForAction(String action) {
        if ("compose-send".equals(action)
            || "password-unlock".equals(action)
            || "compose-add-attachment".equals(action)
            || "compose-remove-attachment".equals(action)) {
            return composeUiId;
        }
        if ("claim".equals(action)
            || "delete".equals(action)
            || "claimall".equals(action)
            || "deleteall".equals(action)
            || "cdk".equals(action)
            || "filter".equals(action)) {
            return inboxUiId;
        }
        if (action.startsWith("admin-")) {
            return adminUiId;
        }
        return null;
    }

    private MailSendQuote calculateCurrentComposeQuote(Player player) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        int attachmentCount = session == null ? 0 : session.attachments().size();
        return calculateComposeQuote(configuration.playerSend(), Map.of(), attachmentCount, session != null ? session.feeCurrency() : configuration.playerSend().feeCurrency());
    }

    private SecondaryPasswordAccess secondaryPasswordAccess() {
        if (secondaryPasswordSupplier == null) {
            return null;
        }
        try {
            return secondaryPasswordSupplier.get();
        } catch (RuntimeException exception) {
            logger.warning("Mail secondary password capability unavailable: " + exception.getMessage());
            return null;
        }
    }

    private final class ClaimJob {
        final Player player;
        final UUID owner;
        final AuthSnapshot admission;
        final long mailId;
        final boolean refresh;
        final CompletableFuture<MailOperationResult> result = new CompletableFuture<>();
        final ArrayDeque<ClaimMail> pending = new ArrayDeque<>();
        long beforeId = Long.MAX_VALUE;
        boolean lastPage;
        volatile boolean disconnected;
        int claimed;
        boolean changed;

        ClaimJob(Player player, AuthSnapshot admission, long mailId, boolean refresh) {
            this.player = player;
            this.owner = player.getUniqueId();
            this.admission = admission;
            this.mailId = mailId;
            this.refresh = refresh;
        }

        boolean sameAdmission(AuthSnapshot current) {
            return java.util.Objects.equals(admission.connectionId(), current.connectionId())
                && admission.generation() == current.generation();
        }
    }

    private record PreparedClaim(MailMessage mail, ClaimProgress progress, List<ClaimStep> steps) {}
    private record PreparedItem(ClaimMail entry, ItemClaim claim, ItemStack stack,
                                Supplier<CurrencyTransactionResult> backgroundCredit) {}

    CompletableFuture<MailOperationResult> startClaim(Player player, long mailId, boolean refresh) {
        if (!active || player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(MailOperationResult.failure(message("player.target-offline")));
        }
        var admission = AuthAccess.snapshot(player.getUniqueId());
        if (!admission.admitted()) return CompletableFuture.completedFuture(claimStopped());
        var job = new ClaimJob(player, admission, mailId, refresh);
        if (claimJobs.putIfAbsent(job.owner, job) != null) {
            return CompletableFuture.completedFuture(MailOperationResult.failure(message("player.mail-claim-busy")));
        }
        claimWorker(job, () -> nextClaim(job));
        return job.result;
    }

    private boolean currentClaim(ClaimJob job) {
        return mayStartClaimStep(job) && isCurrentClaimPlayer(job.player, job.owner);
    }

    private boolean mayStartClaimStep(ClaimJob job) {
        if (!active || job.disconnected) return false;
        var current = AuthAccess.snapshot(job.owner);
        if (current.admitted() && job.sameAdmission(current)) return true;
        job.disconnected = true;
        return false;
    }

    private MailOperationResult claimStopped() {
        return MailOperationResult.failure(message("player.mail-claim-paused"));
    }

    boolean isCurrentClaimPlayer(Player player, UUID owner) {
        return player.isOnline() && Bukkit.getPlayer(owner) == player;
    }

    @FunctionalInterface
    private interface ClaimWork {
        void run() throws Exception;
    }

    private void claimWorker(ClaimJob job, ClaimWork work) {
        if (stopping && Thread.currentThread() instanceof MailWorker worker && worker.owner == this) {
            runClaimWork(job,work);
            return;
        }
        try {
            scheduleClaimWorker(() -> runClaimWork(job,work));
        } catch (RuntimeException failure) {
            finishClaim(job, MailOperationResult.failure(message("player.mail-claim-failed")));
        }
    }

    private void runClaimWork(ClaimJob job,ClaimWork work) {
        try { work.run(); }
        catch (Exception | LinkageError failure) {
            logger.warning("领取邮件失败: " + failure.getMessage());
            if (active) queueClaimEffect(() -> finishClaim(job, MailOperationResult.failure(message("player.mail-claim-failed"))));
        }
    }

    void scheduleClaimWorker(Runnable work) {
        background.execute(work);
    }

    private void queueClaimEffect(Runnable callback) {
        if (!active) return;
        claimEffects.add(callback);
        if (!active) claimEffects.remove(callback);
    }

    private void enqueueClaimCallback(java.util.Queue<Runnable> queue, Runnable effect, Runnable discarded) {
        var callback = new ReservedClaimCallback(effect, discarded);
        if (!active) { callback.discard(); return; }
        queue.add(callback);
        if (!active && queue.remove(callback)) callback.discard();
    }

    private void discardClaimCallbacks(java.util.Queue<Runnable> queue) {
        for (Runnable callback; (callback = queue.poll()) != null;) {
            if (callback instanceof ReservedClaimCallback reserved) reserved.discard();
        }
    }

    private static final class ReservedClaimCallback extends java.util.concurrent.atomic.AtomicBoolean implements Runnable {
        private final Runnable effect;
        private final Runnable discarded;
        ReservedClaimCallback(Runnable effect, Runnable discarded) { this.effect=effect; this.discarded=discarded; }
        @Override public void run() { if (compareAndSet(false,true)) effect.run(); }
        void discard() { if (compareAndSet(false,true)) discarded.run(); }
    }

    void drainClaimEffects() {
        if (!active) return;
        Runnable save = claimSaves.poll();
        if (save != null) {
            // Keep synchronous player saves on a separate tick from other Mail claim callbacks.
            runClaimCallback(save);
            return;
        }
        long started = System.nanoTime();
        for (int index = 0; active && index < MAX_CLAIM_EFFECTS_PER_TICK; index++) {
            Runnable next = claimEffects.poll();
            if (next == null) break;
            runClaimCallback(next);
            if (System.nanoTime() - started >= CLAIM_CALLBACK_BUDGET_NANOS) break;
        }
    }

    private void runClaimCallback(Runnable callback) {
        try { callback.run(); }
        catch (RuntimeException | LinkageError failure) {
            logger.warning("处理邮件领取回调失败: " + failure.getMessage());
        }
    }

    private void nextClaim(ClaimJob job) throws SQLException {
        if (stopping) return;
        if (!mayStartClaimStep(job)) {
            queueClaimEffect(() -> finishClaim(job, claimStopped()));
            return;
        }
        if (job.mailId > 0) {
            var entry = repository.loadClaimMail(job.owner, job.mailId).orElse(null);
            var prepared = entry == null ? null : prepareClaimSnapshot(entry);
            queueClaimEffect(() -> prepareClaim(job, job.mailId, prepared));
            return;
        }
        if (job.pending.isEmpty() && !job.lastPage && !job.disconnected) {
            var page = repository.loadClaimablePage(job.owner, job.beforeId, CLAIM_PAGE_SIZE + 1, Instant.now());
            job.lastPage = page.size() <= CLAIM_PAGE_SIZE;
            if (!job.lastPage) page = page.subList(0, CLAIM_PAGE_SIZE);
            job.pending.addAll(page);
            if (!page.isEmpty()) job.beforeId = job.pending.getLast().mail().id();
        }
        var entry = job.pending.peek();
        if (entry == null || job.disconnected) {
            var result = job.claimed > 0 ? MailOperationResult.success(message("player.claim-all-success", job.claimed))
                : MailOperationResult.failure(message("player.claim-all-empty"));
            queueClaimEffect(() -> finishClaim(job, result));
            return;
        }
        queueClaimEffect(() -> prepareClaimPage(job));
    }

    private void prepareClaimPage(ClaimJob job) {
        if (!currentClaim(job)) { finishClaim(job, claimStopped()); return; }
        long started = System.nanoTime();
        var contents = InventoryItems.copy(job.player.getInventory().getStorageContents());
        var items = new ArrayList<PreparedItem>();
        var decodedItems = new HashMap<String, ItemStack>();
        for (var entry : job.pending) {
            var mail = entry.mail();
            var progress = entry.progress();
            if (!mail.claimConditions().isEmpty() || !mail.claimCommands().isEmpty()
                || (progress.phase() != ClaimPhase.NONE && progress.phase() != ClaimPhase.READY)
                || progress.step() != 0 || !mail.claimable()) break;
            var steps = claimPlan(mail);
            if (steps.isEmpty() || steps.get(0).attachment() == null || !steps.get(0).attachment().isItem()) break;
            Supplier<CurrencyTransactionResult> credit = null;
            boolean eligible = true;
            for (var step : steps.subList(1, steps.size())) {
                if (!step.attachment().isCurrency()) { eligible = false; break; }
                var bridge = currencyBridgeManager.bridge(step.attachment().normalizedCurrencyId());
                if (bridge == null || !bridge.available()) { eligible = false; break; }
                if (steps.size() == 2) {
                    try {
                        if (!bridge.creditRequiresReconciliation()) {
                            credit = bridge.prepareBackgroundCredit(job.player, BigDecimal.valueOf(step.attachment().amount()));
                        }
                    } catch (RuntimeException | LinkageError failure) {
                        // The regular claim path reports a provider that fails to prepare the credit.
                    }
                }
            }
            if (!eligible) break;
            String itemData = steps.get(0).attachment().itemData();
            ItemStack item = decodedItems.get(itemData);
            if (item == null) {
                try { item = MailItemSerializer.deserialize(itemData); }
                catch (IOException | RuntimeException failure) { break; }
                decodedItems.put(itemData, item);
            }
            // InventoryItems.put mutates the destination and clones the template for new slots.
            if (InventoryItems.empty(item) || !InventoryItems.put(contents, item)) break;
            items.add(new PreparedItem(entry, new ItemClaim(mail.id(), 0, UUID.randomUUID(), steps.size() == 1), item, credit));
            if (items.size() >= 2 && System.nanoTime() - started >= CLAIM_CALLBACK_BUDGET_NANOS) break;
        }
        if (items.size() < 2) { prepareNextMail(job); return; }

        String saved = job.player.getPersistentDataContainer().get(CLAIM_ITEM_BATCH_RECEIPT, PersistentDataType.STRING);
        List<UUID> previous;
        try {
            previous = saved == null ? List.of() : java.util.Arrays.stream(saved.split(",")).map(UUID::fromString).toList();
        } catch (IllegalArgumentException failure) {
            logger.warning("邮件批量物品回执格式无效: " + job.owner);
            prepareNextMail(job);
            return;
        }
        var claims = items.stream().map(PreparedItem::claim).toList();
        claimWorker(job, () -> {
            if (!mayStartClaimStep(job)) {
                queueClaimEffect(() -> finishClaim(job, claimStopped()));
                return;
            }
            if (!repository.reserveItemClaims(job.owner, claims, previous, Instant.now())) {
                queueClaimEffect(() -> prepareNextMail(job));
                return;
            }
            enqueueClaimCallback(claimSaves, () -> {
                var outcome = currentClaim(job)
                    ? runClaimEffect(() -> applyClaimItems(job.player, items)) : ClaimOutcome.OFFLINE;
                claimWorker(job, () -> finishItemBatch(job, items, claims, outcome));
            }, () -> claimWorker(job, () -> finishItemBatch(job, items, claims, ClaimOutcome.OFFLINE)));
        });
    }

    private void prepareNextMail(ClaimJob job) {
        var entry = job.pending.remove();
        prepareClaim(job, entry.mail().id(), prepareClaimSnapshot(entry));
    }

    private ClaimOutcome applyClaimItems(Player player, List<PreparedItem> items) {
        String receipt = String.join(",", items.stream().map(item -> item.claim().attempt().toString()).toList());
        return saveClaimItems(player, CLAIM_ITEM_BATCH_RECEIPT, receipt, items.stream().map(PreparedItem::stack).toList());
    }

    private void finishItemBatch(ClaimJob job, List<PreparedItem> items, List<ItemClaim> claims, ClaimOutcome outcome) throws SQLException {
        boolean delivered = outcome == ClaimOutcome.DELIVERED;
        boolean finished = false;
        try {
            if (outcome != ClaimOutcome.REVIEW) finished = repository.finishItemClaims(job.owner, claims, delivered, Instant.now());
        } catch (SQLException failure) {
            logger.warning("邮件批量物品回执写入失败: " + failure.getMessage());
        }
        if (!finished) {
            logger.warning("邮件批量物品待核对: owner=" + job.owner + " mails=" + claims.stream().map(ItemClaim::mailId).toList());
            job.changed = true;
            queueClaimEffect(() -> finishClaim(job, MailOperationResult.failure(message("player.mail-claim-review", claims.get(0).mailId()))));
            return;
        }
        job.changed = true;
        if (!delivered) {
            // The old snapshots are safe to retry because reservation still checks the persisted cursor.
            queueClaimEffect(() -> {
                if (currentClaim(job)) prepareNextMail(job);
                else finishClaim(job, claimStopped());
            });
            return;
        }
        var remaining = new ArrayDeque<ClaimMail>();
        boolean continueOnWorker = items.stream().allMatch(item -> item.claim().last() || item.backgroundCredit() != null);
        for (var item : items) {
            job.pending.remove();
            var mail = item.entry().mail();
            if (item.claim().last() || (continueOnWorker && mayStartClaimStep(job) && settleBatchCredit(job, item))) {
                job.claimed++;
                recordClaimLog(job.owner, "claim", "领取邮件: " + mail.subject());
            } else if (!continueOnWorker) {
                remaining.add(new ClaimMail(mail, new ClaimProgress(1, ClaimPhase.READY, null)));
            }
        }
        while (!remaining.isEmpty()) job.pending.addFirst(remaining.removeLast());
        nextClaim(job);
    }

    private boolean settleBatchCredit(ClaimJob job, PreparedItem item) throws SQLException {
        long mailId = item.claim().mailId();
        UUID attempt = UUID.randomUUID();
        if (!repository.reserveClaimStep(job.owner, mailId, 1, attempt, Instant.now())) return false;
        var claim = new ClaimProgress(1, ClaimPhase.EXECUTING, attempt);
        if (!mayStartClaimStep(job)) {
            repository.releaseUnstartedClaimStep(job.owner, mailId, claim, Instant.now());
            return false;
        }
        var outcome = runClaimEffect(() -> item.backgroundCredit().get().success() ? ClaimOutcome.DELIVERED : ClaimOutcome.REVIEW);
        return checkpointClaimStep(job, prepareClaimSnapshot(item.entry()), claim, true, outcome);
    }

    private PreparedClaim prepareClaimSnapshot(ClaimMail entry) {
        return new PreparedClaim(entry.mail(), entry.progress(), claimPlan(entry.mail()));
    }

    private String readClaimItemReceipt(UUID owner) {
        return readPlayerItemReceipt(owner, CLAIM_ITEM_RECEIPT);
    }

    private String readPlayerItemReceipt(UUID owner, NamespacedKey key) {
        if (playerData == null) return null;
        try { return PlayerDataReceipt.read(playerData.resolve(owner + ".dat"), key.toString()); }
        catch (IOException failure) {
            logger.warning("读取邮件物品回执失败: " + failure.getMessage());
            return null;
        }
    }

    private void prepareClaim(ClaimJob job, long mailId, PreparedClaim prepared) {
        if (!currentClaim(job)) { finishClaim(job, claimStopped()); return; }
        if (prepared == null) {
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-not-found")));
            return;
        }
        var mail = prepared.mail();
        var progress = prepared.progress();
        if (mail.status() == MailStatus.CLAIM_REVIEW || progress.phase() == ClaimPhase.EXECUTING
            || progress.phase() == ClaimPhase.REVIEW) {
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-review", mailId)));
        } else if (!mail.claimable() || (job.mailId == 0 && prepared.steps().isEmpty())) {
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-not-claimable")));
        } else if (progress.phase() == ClaimPhase.NONE && !checkClaimConditions(job.player, mail.claimConditions())) {
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-conditions-unmet")));
        } else if (prepared.steps().isEmpty()) {
            claimWorker(job, () -> {
                if (!mayStartClaimStep(job)) {
                    queueClaimEffect(() -> finishClaim(job, claimStopped()));
                    return;
                }
                boolean claimed = repository.tryClaimMail(job.owner, mailId, Instant.now());
                if (claimed) recordClaim(job, mail);
                else queueClaimEffect(() -> finishClaimMail(job, MailOperationResult.failure(message("player.mail-already-claimed"))));
            });
        } else if (progress.step() >= 0 && progress.step() < prepared.steps().size()) {
            prepareClaimStep(job, prepared, progress.step(), null);
        } else {
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-already-claimed")));
        }
    }

    private void prepareClaimStep(ClaimJob job, PreparedClaim prepared, int index, java.util.ArrayDeque<ItemStack> preparedItems) {
        if (!currentClaim(job)) { finishClaim(job, claimStopped()); return; }
        var plan = prepared.steps();
        try {
            if (preparedItems == null) {
                var remainingItems = new ArrayList<ItemStack>();
                for (var entry : plan.subList(index, plan.size())) {
                    if (entry.attachment() != null && entry.attachment().isItem()) {
                        var item = MailItemSerializer.deserialize(entry.attachment().itemData());
                        if (InventoryItems.empty(item)) {
                            finishClaimMail(job, MailOperationResult.failure(message("player.attachment-read-failed")));
                            return;
                        }
                        remainingItems.add(item);
                    } else if (entry.attachment() != null) {
                        var currency = entry.attachment().normalizedCurrencyId();
                        var bridge = currencyBridgeManager.bridge(currency);
                        if (bridge == null || !bridge.available()) {
                            finishClaimMail(job, MailOperationResult.failure(message("player.attachment-currency-unavailable", currency)));
                            return;
                        }
                    }
                }
                if (!hasEnoughInventorySpace(job.player, remainingItems)) {
                    finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-inventory-full")));
                    return;
                }
                preparedItems = new java.util.ArrayDeque<>(remainingItems);
            }
            var entry = plan.get(index);
            var remaining = preparedItems;
            if (entry.attachment() != null && entry.attachment().isItem()) {
                var item = remaining.remove();
                String saved = job.player.getPersistentDataContainer().get(CLAIM_ITEM_RECEIPT, PersistentDataType.STRING);
                var previous = saved == null ? List.<UUID>of() : List.of(UUID.fromString(saved));
                reserveClaim(job, prepared, index, remaining, false, claimSaves, previous,
                    attempt -> applyClaimItem(job.player, attempt, item));
            } else if (entry.attachment() != null) {
                var attachment = entry.attachment();
                var bridge = currencyBridgeManager.bridge(attachment.normalizedCurrencyId());
                if (bridge == null || !bridge.available()) {
                    finishClaimMail(job, MailOperationResult.failure(message("player.attachment-currency-unavailable",
                        attachment.normalizedCurrencyId())));
                    return;
                }
                var amount = BigDecimal.valueOf(attachment.amount());
                boolean needsReview = bridge.creditRequiresReconciliation();
                var background = bridge.prepareBackgroundCredit(job.player, amount);
                if (background != null) {
                    reserveClaim(job, prepared, index, remaining, true, attempt -> background.get().success() && !needsReview
                        ? ClaimOutcome.DELIVERED : ClaimOutcome.REVIEW);
                } else {
                    reserveClaim(job, prepared, index, remaining, false, attempt -> bridge.deposit(job.player, amount).success() && !needsReview
                        ? ClaimOutcome.DELIVERED : ClaimOutcome.REVIEW);
                }
            } else {
                String expanded = MailTexts.replacePlayerTokens(entry.command(), job.player);
                String command = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(
                    expanded.startsWith("/") ? expanded.substring(1) : expanded);
                reserveClaim(job, prepared, index, remaining, false, attempt -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    ? ClaimOutcome.DELIVERED : ClaimOutcome.REVIEW);
            }
        } catch (IOException | RuntimeException | LinkageError failure) {
            logger.warning("准备邮件附件失败: " + failure.getMessage());
            finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-failed")));
        }
    }

    private enum ClaimOutcome { DELIVERED, REVIEW, INVENTORY_FULL, OFFLINE }

    private ClaimOutcome applyClaimItem(Player player, UUID attempt, ItemStack item) {
        return saveClaimItems(player, CLAIM_ITEM_RECEIPT, attempt.toString(), List.of(item));
    }

    private ClaimOutcome saveClaimItems(Player player, NamespacedKey key, String expected, List<ItemStack> items) {
        var inventory = player.getInventory();
        var before = inventory.getStorageContents();
        var after = InventoryItems.copy(before);
        for (var item : items) {
            if (!InventoryItems.put(after, item)) return ClaimOutcome.INVENTORY_FULL;
        }
        var data = player.getPersistentDataContainer();
        String previous = data.get(key, PersistentDataType.STRING);
        boolean stored = false;
        try {
            inventory.setStorageContents(after);
            PlayerDataReceipt.save(player, key, expected);
            stored = expected.equals(readPlayerItemReceipt(player.getUniqueId(), key));
            return stored ? ClaimOutcome.DELIVERED : ClaimOutcome.REVIEW;
        } finally {
            if (!stored) {
                inventory.setStorageContents(before);
                if (previous == null) data.remove(key);
                else data.set(key, PersistentDataType.STRING, previous);
            }
        }
    }

    private void reserveClaim(ClaimJob job, PreparedClaim prepared, int index, java.util.ArrayDeque<ItemStack> remaining, boolean background,
                              java.util.function.Function<UUID, ClaimOutcome> effect) {
        reserveClaim(job, prepared, index, remaining, background, claimEffects, null, effect);
    }

    private void reserveClaim(ClaimJob job, PreparedClaim prepared, int index, java.util.ArrayDeque<ItemStack> remaining,
                              boolean background, ConcurrentLinkedQueue<Runnable> callbacks,
                              List<UUID> previousItemReceipts,
                              java.util.function.Function<UUID, ClaimOutcome> effect) {
        long mailId = prepared.mail().id();
        UUID attempt = UUID.randomUUID();
        claimWorker(job, () -> {
            if (!mayStartClaimStep(job)) {
                queueClaimEffect(() -> finishClaim(job, claimStopped()));
                return;
            }
            boolean reserved = previousItemReceipts == null
                ? repository.reserveClaimStep(job.owner, mailId, index, attempt, Instant.now())
                : repository.reserveItemClaims(job.owner,
                    List.of(new ItemClaim(mailId, index, attempt, index == prepared.steps().size() - 1)),
                    previousItemReceipts, Instant.now());
            if (!reserved) {
                queueClaimEffect(() -> finishClaimMail(job, MailOperationResult.failure(message("player.mail-already-claimed"))));
                return;
            }
            var claim = new ClaimProgress(index, ClaimPhase.EXECUTING, attempt);
            if (background) {
                var outcome = mayStartClaimStep(job) ? runClaimEffect(() -> effect.apply(attempt)) : ClaimOutcome.OFFLINE;
                finishClaimStep(job, prepared, claim, remaining, outcome);
                return;
            }
            enqueueClaimCallback(callbacks, () -> {
                if (!currentClaim(job)) {
                    claimWorker(job, () -> finishClaimStep(job, prepared, claim, remaining, ClaimOutcome.OFFLINE));
                } else {
                    var outcome = runClaimEffect(() -> effect.apply(attempt));
                    claimWorker(job, () -> finishClaimStep(job, prepared, claim, remaining, outcome));
                }
            }, () -> claimWorker(job, () -> finishClaimStep(job, prepared, claim, remaining, ClaimOutcome.OFFLINE)));
        });
    }

    private ClaimOutcome runClaimEffect(Supplier<ClaimOutcome> effect) {
        try { return effect.get(); }
        catch (RuntimeException | LinkageError failure) {
            logger.warning("发放邮件附件失败: " + failure.getMessage());
            return ClaimOutcome.REVIEW;
        }
    }

    private void finishClaimStep(ClaimJob job, PreparedClaim prepared, ClaimProgress claim, java.util.ArrayDeque<ItemStack> remaining,
                                 ClaimOutcome outcome) throws SQLException {
        long mailId = prepared.mail().id();
        int next = claim.step() + 1;
        boolean last = next == prepared.steps().size();
        if (outcome == ClaimOutcome.OFFLINE || outcome == ClaimOutcome.INVENTORY_FULL) {
            if (!repository.releaseUnstartedClaimStep(job.owner, mailId, claim, Instant.now())) {
                queueClaimEffect(() -> finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-review", mailId))));
                return;
            }
            job.changed = true;
            queueClaimEffect(() -> finishClaimMail(job, outcome == ClaimOutcome.OFFLINE ? claimStopped()
                : MailOperationResult.failure(message("player.mail-claim-inventory-full"))));
            return;
        }
        if (checkpointClaimStep(job, prepared, claim, last, outcome)) {
            if (last) recordClaim(job, prepared.mail());
            else queueClaimEffect(() -> prepareClaimStep(job, prepared, next, remaining));
        } else {
            queueClaimEffect(() -> finishClaimMail(job, MailOperationResult.failure(message("player.mail-claim-review", mailId))));
        }
    }

    private boolean checkpointClaimStep(ClaimJob job, PreparedClaim prepared, ClaimProgress claim, boolean last,
                                        ClaimOutcome outcome) throws SQLException {
        long mailId = prepared.mail().id();
        if (outcome == ClaimOutcome.DELIVERED) {
            try {
                if (repository.completeClaimStep(job.owner, mailId, claim, last, Instant.now())) {
                    job.changed = true;
                    return true;
                }
            } catch (SQLException failure) {
                logger.warning("邮件领取回执待核对: " + failure.getMessage());
            }
        }
        repository.markClaimReview(job.owner, mailId, claim.step(), claim.attempt(), Instant.now());
        logger.warning("邮件领取待核对: owner=" + job.owner + " mailId=" + mailId + " step="
            + (claim.step() + 1) + " attempt=" + claim.attempt() + " action=" + prepared.steps().get(claim.step()).description());
        job.changed = true;
        recordClaimLog(job.owner, "claim-review", "邮件 " + mailId + " 发放步骤 " + (claim.step() + 1) + " 待核对");
        return false;
    }

    private void recordClaim(ClaimJob job, MailMessage mail) throws SQLException {
        job.changed = true;
        recordClaimLog(job.owner, "claim", "领取邮件: " + mail.subject());
        job.claimed++;
        if (job.mailId == 0 && !job.disconnected) nextClaim(job);
        else queueClaimEffect(() -> finishClaim(job, MailOperationResult.success(message("player.mail-claimed"))));
    }

    private void recordClaimLog(UUID owner, String type, String content) {
        try { repository.appendLog(new MailLogEntry(0L, owner, type, content, Instant.now())); }
        catch (SQLException failure) { logger.warning("写入邮件领取日志失败: " + failure.getMessage()); }
    }

    private void finishClaimMail(ClaimJob job, MailOperationResult result) {
        if (result.success()) job.claimed++;
        if (job.mailId == 0 && currentClaim(job)) claimWorker(job, () -> nextClaim(job));
        else finishClaim(job, result);
    }

    private void finishClaim(ClaimJob job, MailOperationResult result) {
        if (!claimJobs.remove(job.owner, job)) return;
        try {
            if (job.changed) publishRefresh(job.owner);
            if (job.refresh && currentClaim(job)) refreshPlayerViews(job.player, false);
        } finally {
            job.result.complete(result);
        }
    }

    public MailOperationResult inspectClaimReview(UUID owner, long mailId) {
        try {
            MailMessage mail = repository.loadMail(owner, mailId).orElse(null);
            if (mail == null) return MailOperationResult.failure(message("player.mail-not-found"));
            ClaimProgress claim = repository.loadClaimProgress(owner, mailId);
            List<ClaimStep> plan = claimPlan(mail);
            if (claim.attempt() == null || claim.step() >= plan.size()) {
                return MailOperationResult.failure(message("admin.claim-review-not-found"));
            }
            return MailOperationResult.success(message("admin.claim-review-detail", mailId, owner,
                claim.step() + 1, plan.size(), claim.phase(), claim.attempt(), plan.get(claim.step()).description()));
        } catch (SQLException exception) {
            logger.warning("读取邮件领取进度失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.mail-claim-failed"));
        }
    }

    public MailOperationResult resolveClaimReview(UUID owner, long mailId, UUID attempt, boolean confirmed, String actor) {
        try {
            MailMessage mail = repository.loadMail(owner, mailId).orElse(null);
            if (mail == null) return MailOperationResult.failure(message("player.mail-not-found"));
            ClaimProgress claim = repository.loadClaimProgress(owner, mailId);
            var plan = claimPlan(mail);
            int count = plan.size();
            if (!attempt.equals(claim.attempt()) || claim.step() >= count) {
                return MailOperationResult.failure(message("admin.claim-review-not-found"));
            }
            if (claim.phase() != ClaimPhase.REVIEW) {
                return MailOperationResult.failure(message("admin.claim-review-in-flight"));
            }
            var attachment = plan.get(claim.step()).attachment();
            if (!confirmed && attachment != null && attachment.isItem()) {
                if (playerData == null) return MailOperationResult.failure(message("player.mail-claim-failed"));
                String key = attempt.toString();
                Path file = playerData.resolve(owner + ".dat");
                String single = PlayerDataReceipt.readExisting(file, CLAIM_ITEM_RECEIPT.toString());
                String batch = PlayerDataReceipt.readExisting(file, CLAIM_ITEM_BATCH_RECEIPT.toString());
                if (key.equals(single) || (batch != null && java.util.Arrays.asList(batch.split(",")).contains(key))) {
                    return MailOperationResult.failure(message("admin.claim-item-stored"));
                }
            }
            Instant now = Instant.now();
            boolean updated = confirmed
                ? repository.completeClaimStep(owner, mailId, claim, claim.step() == count - 1, now)
                : repository.releaseReviewedClaimStep(owner, mailId, claim, now);
            if (!updated) return MailOperationResult.failure(message("admin.claim-review-not-found"));
            String resolution = confirmed ? "confirmed" : "retry";
            logger.info("管理员核对邮件: actor=" + actor + " owner=" + owner + " mailId=" + mailId
                + " step=" + (claim.step() + 1) + " attempt=" + attempt + " result=" + resolution);
            appendLog(owner, "claim-resolve", "管理员核对邮件 " + mailId + " 步骤 " + (claim.step() + 1));
            publishRefresh(owner);
            if (inboxViewers.contains(owner) || logViewers.contains(owner)) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null) refreshPlayerViews(player, false);
            }
            return MailOperationResult.success(message("admin.claim-review-resolved", mailId));
        } catch (SQLException | IOException exception) {
            logger.warning("核对邮件领取状态失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.mail-claim-failed"));
        }
    }

    public MailOperationResult isolateInterruptedClaim(UUID owner, long mailId, UUID attempt, String actor) {
        try {
            var claim = repository.loadClaimProgress(owner, mailId);
            if (claim.phase() != ClaimPhase.EXECUTING || !attempt.equals(claim.attempt())
                || claimJobs.containsKey(owner)) return MailOperationResult.failure(message("admin.claim-review-not-found"));
            if (!repository.markClaimReview(owner, mailId, claim.step(), attempt, Instant.now())) {
                return MailOperationResult.failure(message("admin.claim-review-not-found"));
            }
            logger.info("管理员隔离中断的邮件领取: actor=" + actor + " owner=" + owner + " mailId=" + mailId + " attempt=" + attempt);
            appendLog(owner, "claim-interrupted", "管理员隔离中断的邮件 " + mailId + "，尝试编号 " + attempt);
            publishRefresh(owner);
            return MailOperationResult.success(message("admin.claim-review-resolved", mailId));
        } catch (SQLException failure) {
            logger.warning("隔离邮件领取失败: " + failure.getMessage());
            return MailOperationResult.failure(message("player.mail-claim-failed"));
        }
    }

    private static List<ClaimStep> claimPlan(MailMessage mail) {
        List<ClaimStep> steps = new ArrayList<>();
        // Keep the original award order and a stable cursor across retries.
        for (MailAttachment attachment : mail.attachments()) {
            if (attachment.isItem()) steps.add(new ClaimStep(attachment, null));
        }
        for (String command : mail.claimCommands()) steps.add(new ClaimStep(null, command));
        for (MailAttachment attachment : mail.attachments()) {
            if (attachment.isCurrency() && attachment.amount() > 0.0D) steps.add(new ClaimStep(attachment, null));
        }
        return steps;
    }

    private record ClaimStep(MailAttachment attachment, String command) {
        String description() {
            if (attachment == null) return "命令 " + command;
            if (attachment.isCurrency()) return attachment.normalizedCurrencyId() + " " + attachment.amount();
            return "物品附件 #" + attachment.id() + " " + attachment.description();
        }
    }

    private MailOperationResult deleteMail(Player player, long mailId) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure(message("player.mail-not-found"));
            }

            MailMessage mail = optionalMail.get();
            if (!canDeleteMail(configuration.retention().allowDeleteWithUnclaimedAttachments(), mail)) {
                return MailOperationResult.failure(message("player.mail-delete-restricted"));
            }

            if (!repository.transitionMailState(mail, MailStatus.DELETED, Instant.now())) {
                return MailOperationResult.failure(message("player.mail-delete-restricted"));
            }
            appendLog(player.getUniqueId(), "delete", "删除邮件: " + mail.subject());
            publishRefresh(player.getUniqueId());
            refreshPlayerViews(player, false);
            return MailOperationResult.success(message("player.mail-deleted"));
        } catch (Exception exception) {
            this.logger.warning("删除邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.mail-delete-failed"));
        }
    }

    // ── 公开 API 适配方法（供 AxsMailServiceAdapter 调用） ──────────────

    /**
     * 标记邮件为已读，返回更新后的邮件。
     * <p>
     * 如果邮件已经是已读或更后状态，不改变状态但仍返回邮件。
     *
     * @param playerUuid 玩家 UUID
     * @param mailId     邮件 ID
     * @return 更新后的邮件，不存在时返回 {@link Optional#empty()}
     */
    public Optional<MailMessage> markMailRead(UUID playerUuid, long mailId) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(playerUuid, mailId);
            if (optionalMail.isEmpty()) {
                return Optional.empty();
            }
            MailMessage mail = optionalMail.get();
            if (mail.unread()) {
                Instant now = Instant.now();
                if (repository.transitionMailState(mail, MailStatus.READ, now)) {
                    return Optional.of(withStatus(mail, MailStatus.READ, now));
                }
                return repository.loadMail(playerUuid, mailId);
            }
            return optionalMail;
        } catch (Exception exception) {
            this.logger.warning("标记已读失败: " + exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按 UUID 领取邮件附件。
     * <p>
     * 领取物品附件需要玩家在线（发放到背包）。玩家离线时返回失败。
     *
     * @param playerUuid 玩家 UUID
     * @param mailId     邮件 ID
     * @return 领取结果
     */
    public CompletableFuture<MailOperationResult> claimMailByUuid(UUID playerUuid, long mailId) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(MailOperationResult.failure("PLAYER_OFFLINE"));
        }
        return startClaim(player, mailId, false);
    }

    /**
     * 按 UUID 删除邮件。
     * <p>
     * 不需要玩家在线，直接操作数据库。
     *
     * @param playerUuid 玩家 UUID
     * @param mailId     邮件 ID
     * @return 删除结果
     */
    public MailOperationResult deleteMailByUuid(UUID playerUuid, long mailId) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(playerUuid, mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure("NOT_FOUND");
            }
            MailMessage mail = optionalMail.get();
            if (!canDeleteMail(configuration.retention().allowDeleteWithUnclaimedAttachments(), mail)) {
                return MailOperationResult.failure("PROTECTED_ATTACHMENTS");
            }
            if (!repository.transitionMailState(mail, MailStatus.DELETED, Instant.now())) {
                return MailOperationResult.failure("PROTECTED_ATTACHMENTS");
            }
            appendLog(playerUuid, "delete", "删除邮件: " + mail.subject());
            return MailOperationResult.success("OK");
        } catch (Exception exception) {
            this.logger.warning("删除邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("DELETE_FAILED");
        }
    }

    private MailMessage withStatus(MailMessage mail, MailStatus status, Instant updatedAt) {
        return new MailMessage(
            mail.id(), mail.ownerUuid(), mail.senderUuid(), mail.senderName(),
            mail.sourceType(), mail.presetId(), mail.cdkCode(),
            mail.subject(), mail.body(), status,
            mail.attachments(), mail.claimCommands(), mail.claimConditions(),
            mail.createdAt(), mail.expiresAt(), updatedAt,
            mail.claimedAt(), mail.deletedAt()
        );
    }

    private File exportUiResource(String resourcePath, String relativePath) throws IOException {
        return uiResourceExporter.export(resourcePath, relativePath, configuration.ui().overwriteUiFiles());
    }

    private String bindSingleUi(String configuredUiId, File uiFile) {
        String runtimeUiId = PacketBridgeAPI.normalizeUiId(configuredUiId, uiFile);
        if (bridge == null || !configuration.ui().registerUiOnEnable()) {
            return runtimeUiId;
        }

        UiRegistrationResult result = bridge.registerOrReloadUi(configuredUiId, uiFile);
        if (!result.success()) {
            this.logger.warning("注册邮件 UI 失败: " + result.message());
            return runtimeUiId;
        }

        switch (uiFile.getName().toLowerCase(Locale.ROOT)) {
            case "mail_inbox.yml" -> registeredInboxUiId = result.registeredUiId();
            case "mail_compose.yml" -> registeredComposeUiId = result.registeredUiId();
            case "mail_logs.yml" -> registeredLogsUiId = result.registeredUiId();
            case "mail_admin.yml" -> registeredAdminUiId = result.registeredUiId();
            default -> {
            }
        }
        return result.runtimeUiId();
    }

    private void unregisterBoundUi(String uiId) {
        if (uiId != null) {
            bridge.unregisterUi(uiId);
        }
    }

    private void exportBundledPresetIfMissing(File directory) throws IOException {
        File starterFile = new File(directory, "starter.yml");
        if (starterFile.exists()) {
            return;
        }

        bundledResourceWriter.write(STARTER_PRESET_RESOURCE_PATH, starterFile);
    }

    private MailPresetDefinition preset(String presetId) {
        if (presetId == null) {
            return null;
        }
        return presets.get(presetId.trim().toLowerCase(Locale.ROOT));
    }

    private List<RecipientResolution> resolvePresetRecipients(String target) throws Exception {
        String normalizedTarget = MailTexts.safe(target).trim();
        if (normalizedTarget.isBlank()) {
            return List.of();
        }
        if ("all-online".equalsIgnoreCase(normalizedTarget)) {
            return Bukkit.getOnlinePlayers().stream()
                .map(player -> new RecipientResolution(player.getUniqueId(), player.getName()))
                .toList();
        }
        if ("all-registered".equalsIgnoreCase(normalizedTarget)) {
            return repository.loadAllProfiles().stream()
                .filter(profile -> profile.playerUuid() != null)
                .map(profile -> new RecipientResolution(profile.playerUuid(), MailTexts.safe(profile.lastKnownName()).isBlank() ? profile.playerUuid().toString() : profile.lastKnownName()))
                .toList();
        }

        RecipientResolution single = resolveRecipient(normalizedTarget);
        return single == null ? List.of() : List.of(single);
    }

    private MailOperationResult sendPresetMail(RecipientResolution recipient, MailPresetDefinition preset, String senderName) {
        if (recipient == null || preset == null) {
            return MailOperationResult.failure(message("player.preset-mail-params-invalid"));
        }

        var mail = buildPresetMail(recipient.playerUuid(), preset, MailSourceType.PRESET, preset.id(), "", senderName, Instant.now());
        var result = sendMailInternal(recipient.playerUuid(), "preset-send", mail, true);
        if (result.success()) appendLog(recipient.playerUuid(), "preset", "收到邮件: " + preset.displayName());
        return result;
    }

    MailMessage durablePreset(UUID recipient, String presetId, String senderName) {
        MailPresetDefinition preset = preset(presetId);
        if (preset == null || !preset.enabled()) return null;
        return buildPresetMail(recipient, preset, MailSourceType.PRESET, preset.id(), "", senderName, Instant.now());
    }

    private MailMessage buildPresetMail(UUID owner, MailPresetDefinition preset, MailSourceType sourceType,
                                        String presetId, String cdkCode, String senderName, Instant now) {
        Instant expiresAt = preset.expiresAfter() == null
            ? now.plusSeconds(configuration.retention().defaultExpireAfterDays() * 86400L)
            : now.plus(preset.expiresAfter());
        return new MailMessage(
            0L,
            owner,
            null,
            MailTexts.safe(senderName).isBlank() ? "System" : senderName,
            sourceType,
            MailTexts.safe(presetId).isBlank() ? preset.id() : presetId,
            MailTexts.safe(cdkCode),
            MailTexts.crop(preset.subject(), configuration.playerSend().subjectMaxLength()),
            MailTexts.crop(preset.body(), configuration.playerSend().bodyMaxLength()),
            MailStatus.UNREAD,
            MailTexts.copyAttachments(preset.attachments()),
            MailTexts.copyStrings(preset.claimCommands()),
            MailTexts.copyConditions(preset.claimConditions()),
            now,
            expiresAt,
            now,
            null,
            null
        );
    }

    private RecipientResolution resolveRecipient(String rawRecipient) throws Exception {
        String requestedName = normalizeRecipientInput(rawRecipient);
        if (requestedName.isBlank()) {
            return null;
        }

        RecipientResolution online = resolveOnlineRecipient(requestedName);
        if (online != null) return online;
        RecipientResolution profile = resolveStoredRecipient(requestedName);
        return profile != null ? profile : resolveOfflineRecipient(requestedName);
    }

    private RecipientResolution resolveOnlineRecipient(String requestedName) {
        Player online = Bukkit.getPlayerExact(requestedName);
        if (online == null) {
            online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
        }
        return online == null ? null : new RecipientResolution(online.getUniqueId(), online.getName());
    }

    private RecipientResolution resolveStoredRecipient(String requestedName) throws SQLException {
        Optional<MailPlayerProfile> profile = repository.findPlayerProfileByName(requestedName);
        if (profile.isPresent()) {
            MailPlayerProfile value = profile.get();
            return new RecipientResolution(value.playerUuid(), MailTexts.safe(value.lastKnownName()).isBlank() ? requestedName : value.lastKnownName());
        }

        return null;
    }

    private RecipientResolution resolveOfflineRecipient(String requestedName) {
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(requestedName) && offlinePlayer.getUniqueId() != null) {
                return new RecipientResolution(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            }
        }
        return null;
    }

    private boolean isPlayerOnline(UUID playerUuid) {
        Player online = Bukkit.getPlayer(playerUuid);
        return online != null && online.isOnline();
    }

    private MailOperationResult validateAttachmentItems(List<ItemStack> attachmentItems) {
        for (ItemStack itemStack : attachmentItems) {
            if (!MailTexts.isRealItem(itemStack)) {
                continue;
            }
            if (configuration.moderation().blockedMaterials().contains(itemStack.getType().name())) {
                return MailOperationResult.failure(message("player.attachment-blocked-material", itemStack.getType().name()));
            }
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                // displayName 检测
                if (meta.hasDisplayName()) {
                    String displayName = meta.getDisplayName();
                    for (Pattern pattern : configuration.moderation().blockedDisplaynamePatterns()) {
                        if (pattern.matcher(displayName).find()) {
                            return MailOperationResult.failure(message("player.attachment-blocked-displayname"));
                        }
                    }
                }
                // Lore 检测
                if (meta.getLore() != null) {
                    for (String line : meta.getLore()) {
                        if (matchesBlockedLore(line)) {
                            return MailOperationResult.failure(message("player.attachment-blocked-lore"));
                        }
                    }
                }
                // NBT/PDC 键存在检测
                org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                for (String blockedKey : configuration.moderation().blockedNbtKeys()) {
                    for (NamespacedKey existingKey : pdc.getKeys()) {
                        if (existingKey.getKey().equalsIgnoreCase(blockedKey)
                            || existingKey.toString().equalsIgnoreCase(blockedKey)) {
                            return MailOperationResult.failure(message("player.attachment-blocked-nbt-key", blockedKey));
                        }
                    }
                }
                // NBT/PDC 键值检测
                for (Map.Entry<String, String> entry : configuration.moderation().blockedNbtValues().entrySet()) {
                    String expectedKey = entry.getKey();
                    String expectedValue = entry.getValue();
                    String actualValue = readPdcValue(pdc, expectedKey);
                    if (actualValue != null && MailTexts.valuesEqual(expectedValue, actualValue)) {
                        return MailOperationResult.failure(message("player.attachment-blocked-nbt-value", expectedKey));
                    }
                }
            }
        }
        return MailOperationResult.success("");
    }

    private String readPdcValue(org.bukkit.persistence.PersistentDataContainer pdc, String configuredKey) {
        // 先尝试 plugin 命名空间
        NamespacedKey pluginKey = new NamespacedKey(plugin, configuredKey);
        String value = MailTexts.readPdcValueByKey(pdc, pluginKey);
        if (value != null) {
            return value;
        }
        // 遍历所有键，匹配 key 部分（忽略命名空间）或完整键
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().equalsIgnoreCase(configuredKey)
                || key.toString().equalsIgnoreCase(configuredKey)) {
                return MailTexts.readPdcValueByKey(pdc, key);
            }
        }
        return null;
    }
    private boolean matchesBlockedLore(String value) {
        String normalized = MailTexts.safe(value);
        for (Pattern blockedPattern : configuration.moderation().blockedLorePatterns()) {
            if (blockedPattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> collectComposeItems(ComposeSession session) {
        List<ItemStack> items = new ArrayList<>();
        if (session == null) return List.of();
        for (ItemStack item : session.attachments()) {
            if (MailTexts.isRealItem(item)) {
                items.add(item.clone());
            }
        }
        return List.copyOf(items);
    }

    private void pushComposeQuote(Player player, MailSendQuote quote) {
        if (player != null && player.isOnline()) {
            ComposeSession session = composeSessions.get(player.getUniqueId());
            int maxAttachments = configuration.playerSend().maxAttachments();
            int attachmentCount = session == null ? 0 : session.attachments().size();
            Map<String, Object> composeUpdatePacket = MailComposePacketFactory.buildQuote(
                configuration,
                currencyBridgeManager,
                messages,
                quote,
                maxAttachments,
                attachmentCount,
                composePasswordPanels.contains(player.getUniqueId()),
                session == null ? null : session.actionToken()
            );
            composeUpdatePacket.put("is_admin", player.hasPermission("arcartxsuite.admin"));
            // 序列化附件（背包槽位使用 ~Backpack 类型，由客户端自动显示，无需发送 backpack_jsons）
            if (session != null) {
                composeUpdatePacket.put("attachment_jsons", serializeAttachments(session.attachments()));
                composeUpdatePacket.put("attachment_amounts", MailItemPreview.amounts(session.attachments()));
            }
            bridge.sendPacket(
                player,
                composeUiId,
                "update",
                composeUpdatePacket
            );
        }
    }

    static int effectiveAttachmentSlots(int configuredMaxAttachments, int inventorySize) {
        return Math.max(0, Math.min(Math.max(1, configuredMaxAttachments), Math.max(0, inventorySize)));
    }

    private MailOperationResult validateSenderPayment(Player player, MailSendQuote quote) {
        Map<String, BigDecimal> requiredTotals = requiredCurrencyTotal(quote);
        for (Map.Entry<String, BigDecimal> entry : requiredTotals.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(entry.getKey());
            if (currencyBridge == null || !currencyBridge.available()) {
                return MailOperationResult.failure(message("player.currency-bridge-unavailable", entry.getKey()));
            }
            if (currencyBridge.balance(player).compareTo(amount) < 0) {
                return MailOperationResult.failure(message("player.insufficient-balance", entry.getKey()));
            }
        }
        return MailOperationResult.success("");
    }

    private boolean refundSender(Player player, Map<String, BigDecimal> charged, BooleanSupplier stillAdmitted) {
        boolean confirmed = true;
        for (Map.Entry<String, BigDecimal> entry : charged.entrySet()) {
            try {
                CurrencyBridge bridge = currencyBridgeManager.bridge(entry.getKey());
                if (bridge != null && bridge.available()) {
                    if (!stillAdmitted.getAsBoolean()) return false;
                    var receipt = bridge.deposit(player, entry.getValue());
                    if (receipt != null && receipt.success() && !receipt.uncertain() && !bridge.creditRequiresReconciliation()) {
                        continue;
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                logger.warning("寄信费用退款未确认: " + failure.getMessage());
            }
            confirmed = false;
        }
        return confirmed;
    }

    private Map<String, BigDecimal> requiredCurrencyTotal(MailSendQuote quote) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (quote == null) {
            return totals;
        }
        totals.put(quote.feeCurrencyId(), MailTexts.normalizeAmount(quote.totalFee()));
        mergeCurrencyTotals(totals, quote.attachmentAmounts());
        mergeCurrencyTotals(totals, quote.attachmentTaxes());
        return Map.copyOf(totals);
    }

    private void mergeCurrencyTotals(Map<String, BigDecimal> totals, Map<String, BigDecimal> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String currencyId = MailTexts.normalizeCurrencyId(entry.getKey());
            BigDecimal amount = MailTexts.normalizeAmount(entry.getValue());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            totals.merge(currencyId, amount, BigDecimal::add);
        }
    }

    private List<MailAttachment> buildComposeAttachments(List<ItemStack> items, Map<String, BigDecimal> attachmentAmounts) throws IOException {
        List<MailAttachment> attachments = new ArrayList<>();
        int sortOrder = 0;
        for (ItemStack itemStack : items) {
            attachments.add(
                new MailAttachment(
                    0L,
                    sortOrder++,
                    MailAttachmentType.ITEM,
                    MailItemSerializer.serialize(itemStack),
                    "",
                    0.0D,
                    resolveItemDescription(itemStack),
                    null
                )
            );
        }
        for (Map.Entry<String, BigDecimal> entry : attachmentAmounts.entrySet()) {
            BigDecimal amount = MailTexts.normalizeAmount(entry.getValue());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String currencyId = MailTexts.normalizeCurrencyId(entry.getKey());
            xuanmo.arcartxsuite.api.icon.IconDefinition vaultIcon = "money".equals(currencyId) ? configuration.playerSend().vaultDefaultIcon() : null;
            attachments.add(
                new MailAttachment(
                    0L,
                    sortOrder++,
                    MailAttachmentType.CURRENCY,
                    "",
                    currencyId,
                    amount.doubleValue(),
                    currencyDisplay(currencyId) + " " + MailTexts.trimDecimal(amount),
                    vaultIcon
                )
            );
        }
        return List.copyOf(attachments);
    }

    private boolean checkClaimConditions(Player player, List<ScriptCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return ScriptConditionServices.evaluator().passes(player, conditions);
    }

    /**
     * 检查玩家背包是否有足够空间容纳所有物品附件。
     * <p>
     * 模拟发放：克隆背包内容，逐个 addItem，检查是否有剩余。
     * 不修改实际背包内容。
     *
     * @param player     玩家
     * @param itemRewards 物品附件列表
     * @return true 表示空间足够
     */
    private boolean hasEnoughInventorySpace(Player player, List<ItemStack> itemRewards) {
        if (itemRewards == null || itemRewards.isEmpty()) return true;
        var contents = InventoryItems.copy(player.getInventory().getStorageContents());
        for (ItemStack itemReward : itemRewards) {
            if (!InventoryItems.put(contents, itemReward)) return false;
        }
        return true;
    }

    private void appendLog(UUID playerUuid, String type, String content) {
        if (playerUuid == null) {
            return;
        }
        try {
            repository.appendLog(new MailLogEntry(0L, playerUuid, MailTexts.safe(type), MailTexts.safe(content), Instant.now()));
            if (logViewers.contains(playerUuid)) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) refreshLogs(player, false);
            }
        } catch (Exception exception) {
            this.logger.warning("写入邮件日志失败: " + exception.getMessage());
        }
    }

    private void publishRefresh(UUID playerUuid) {
        if (playerUuid != null && crossServerChannel != null && crossServerChannel.isActive()) {
            crossServerChannel.publish("refresh:" + playerUuid);
        }
    }

    private void refreshPlayerViews(Player player, boolean initPacket) {
        UUID playerUuid = player.getUniqueId();
        if (logViewers.contains(playerUuid)) {
            refreshLogs(player, initPacket);
        }
        if (inboxViewers.contains(playerUuid)) {
            refreshInbox(player, initPacket);
        }
    }

    private void refreshAllViewerStates() {
        for (UUID viewerUuid : new LinkedHashSet<>(inboxViewers)) {
            Player player = Bukkit.getPlayer(viewerUuid);
            if (player != null && player.isOnline()) {
                refreshInbox(player, false);
            }
        }
        for (UUID viewerUuid : new LinkedHashSet<>(logViewers)) {
            Player player = Bukkit.getPlayer(viewerUuid);
            if (player != null && player.isOnline()) {
                refreshLogs(player, false);
            }
        }
    }

    private void sendPlayerResult(Player player, MailOperationResult result) {
        if (player == null || result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage(MESSAGE_PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private String resolveItemDescription(ItemStack itemStack) {
        String name;
        if (vanillaItemNameBridge != null) {
            name = vanillaItemNameBridge.resolveDisplayName(itemStack);
        } else {
            ItemMeta meta = itemStack.getItemMeta();
            name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : itemStack.getType().name();
        }
        return name + " x" + itemStack.getAmount();
    }

    private String currencyDisplay(String currencyId) {
        CurrencyDefinition definition = currencyBridgeManager.definition(currencyId);
        return definition == null || definition.displayName().isBlank() ? currencyId : definition.displayName();
    }
    private enum ComposeSendPhase {
        SAVING_ATTACHMENTS, ATTACHMENT_REVIEW, CHARGING, DEBIT_REVIEW, SENDING, REFUND_PENDING, REFUND_REVIEW,
        RETURN_PENDING, RETURNING, RETURN_REVIEW, INSERTING, RESOLVING
    }

    private static final class PendingComposeSend {
        final String key;
        final MailMessage mail;
        final Map<String, BigDecimal> planned;
        final String recipientName;
        final Map<String, BigDecimal> charged = new LinkedHashMap<>();
        ComposeSendPhase phase = ComposeSendPhase.CHARGING;
        String attemptedCurrency = "";
        String returnStart = "";
        boolean refundConfirmed;
        boolean attachmentsReturned;
        boolean draftLinked;

        PendingComposeSend(String key, MailMessage mail, Map<String, BigDecimal> planned, String recipientName) {
            this.key = key;
            this.mail = mail;
            this.planned = planned;
            this.recipientName = recipientName;
        }
    }

    private static final class ComposeSession {
        private final UUID sessionId;
        private final UUID playerUuid;
        private final List<ItemStack> attachments;
        private final String actionToken;
        private boolean tokenInFlight;
        private boolean sent;
        private boolean draftReview;
        private boolean returnDraftWhenReady;
        private PendingComposeSend pendingSend;
        private String feeCurrency;

        private ComposeSession(UUID sessionId, UUID playerUuid, boolean sent, String feeCurrency) {
            this.sessionId = sessionId;
            this.playerUuid = playerUuid;
            this.attachments = new ArrayList<>();
            this.actionToken = UUID.randomUUID().toString();
            this.sent = sent;
            this.feeCurrency = feeCurrency;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public UUID playerUuid() {
            return playerUuid;
        }

        public List<ItemStack> attachments() {
            return attachments;
        }

        public String actionToken() {
            return actionToken;
        }

        public boolean sent() {
            return sent;
        }

        public String feeCurrency() {
            return feeCurrency;
        }

        public void setFeeCurrency(String feeCurrency) {
            this.feeCurrency = feeCurrency;
        }
    }

    private static final class AdminEditSession {
        private final UUID playerUuid;
        private final List<ItemStack> attachments;
        private String editingPresetId;

        private AdminEditSession(UUID playerUuid, String editingPresetId) {
            this.playerUuid = playerUuid;
            this.attachments = new ArrayList<>();
            this.editingPresetId = editingPresetId;
        }

        public UUID playerUuid() {
            return playerUuid;
        }

        public List<ItemStack> attachments() {
            return attachments;
        }

        public String editingPresetId() {
            return editingPresetId;
        }
    }

    // ─── Admin Attachment Helpers ─────────────────────────────────

    /**
     * 从预设定义中反序列化所有物品附件，标记为模板物品并返回列表。
     * 供 sendAdminPresetDetail（序列化为 UI 图标 JSON）和 populateAdminContainer（填充 session）共用。
     */
    private List<ItemStack> deserializePresetItems(MailPresetDefinition def) {
        List<ItemStack> items = new ArrayList<>();
        if (def == null || def.attachments() == null) return items;
        for (MailAttachment att : def.attachments()) {
            if (!att.isItem() || att.itemData() == null || att.itemData().isBlank()) continue;
            try {
                ItemStack item = MailItemSerializer.deserialize(att.itemData());
                if (MailTexts.isRealItem(item)) {
                    markAsTemplateItem(item);
                    items.add(item);
                } else {
                    this.logger.warning("Admin UI 预设附件反序列化后非真实物品: preset=" + def.id()
                        + " itemData=" + att.itemData().substring(0, Math.min(60, att.itemData().length())));
                }
            } catch (Exception exception) {
                this.logger.warning("Admin UI 预设附件反序列化异常: preset=" + def.id() + " | " + exception.getMessage());
            }
        }
        return items;
    }

    private void populateAdminContainer(Player player, String presetId) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;

        // 先归还旧物品并清空附件列表
        returnAdminItems(player, session);
        session.attachments().clear();
        session.editingPresetId = MailTexts.safe(presetId).trim().toLowerCase(Locale.ROOT);

        MailPresetDefinition def = getPreset(presetId);
        if (def == null || def.attachments() == null) {
            refreshAdminAttachments(player);
            return;
        }

        for (ItemStack item : deserializePresetItems(def)) {
            if (session.attachments().size() >= adminAttachmentSlots()) break;
            session.attachments().add(item);
        }

        refreshAdminAttachments(player);
    }

    private void clearAdminContainer(Player player) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;
        returnAdminItems(player, session);
        session.attachments().clear();
        session.editingPresetId = null;
        refreshAdminAttachments(player);
    }

    private void returnAdminItems(Player player, AdminEditSession session) {
        if (player == null || session == null) return;
        boolean returned = false;
        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : session.attachments()) {
            if (!MailTexts.isRealItem(itemStack)) continue;
            if (isTemplateItem(itemStack)) continue;
            returned = true;
            HashMap<Integer, ItemStack> leftovers = playerInventory.addItem(itemStack.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        if (returned && player.isOnline()) {
            player.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + message("player.admin-edits-returned"));
        }
    }

    private List<ItemStack> collectAdminItems(AdminEditSession session) {
        List<ItemStack> result = new ArrayList<>();
        if (session == null) return List.of();
        for (ItemStack itemStack : session.attachments()) {
            if (MailTexts.isRealItem(itemStack)) {
                result.add(itemStack.clone());
            }
        }
        return List.copyOf(result);
    }

    private void markAsTemplateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(adminTemplateKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    private boolean isTemplateItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(adminTemplateKey, PersistentDataType.BYTE);
    }

    private ItemStack stripTemplateTag(ItemStack item) {
        if (item == null) return null;
        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(adminTemplateKey);
            clean.setItemMeta(meta);
        }
        return clean;
    }

    /** CDK 兑换限流条目：记录连续失败次数、上次失败时间和锁定到期时间。 */
    private static final class CdkRateLimitEntry {
        private int failCount;
        private Instant lastFailAt;
        private Instant lockoutUntil;

        CdkRateLimitEntry() {
            this.failCount = 0;
            this.lastFailAt = null;
            this.lockoutUntil = null;
        }
    }

    private static record RecipientResolution(UUID playerUuid, String lastKnownName) {
    }


}
