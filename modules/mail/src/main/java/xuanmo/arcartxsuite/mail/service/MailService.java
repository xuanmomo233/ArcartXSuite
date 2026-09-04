package xuanmo.arcartxsuite.mail.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.BiConsumer;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
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
import xuanmo.arcartxsuite.mail.util.MailItemSerializer;
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
    private static final int ADMIN_ATTACHMENT_SLOTS = 6;
    private static final String STARTER_PRESET_RESOURCE_PATH = "mail/presets/starter.yml";
    private static final int INBOX_PAGE_SIZE = 6;
    private static final int LOG_PAGE_SIZE = 10;
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
    private final BiConsumer<String, Player> signalDispatcher;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final CrossServerAPI crossServer;
    private final MessageProvider messages;
    private final Supplier<SecondaryPasswordAccess> secondaryPasswordSupplier;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry;
    private final xuanmo.arcartxsuite.api.icon.IconResolver iconResolver;
    private final Map<String, MailPresetDefinition> presets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> selectedMailIds = new ConcurrentHashMap<>();
    private final Map<UUID, MailInboxQuery> inboxQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> logPages = new ConcurrentHashMap<>();
    private final Map<UUID, ComposeSession> composeSessions = new ConcurrentHashMap<>();
    private final Set<UUID> composePasswordPanels = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AdminEditSession> adminEditSessions = new ConcurrentHashMap<>();
    private final Map<UUID, CdkPreviewState> cdkPreviewStates = new ConcurrentHashMap<>();
    // CDK 兑换限流状态：玩家 UUID -> 限流条目
    private final Map<UUID, CdkRateLimitEntry> cdkRateLimitMap = new ConcurrentHashMap<>();
    private final Set<UUID> inboxViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> logViewers = ConcurrentHashMap.newKeySet();

    private BukkitTask cleanupTask;
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
            uiResourceExporter, bundledResourceWriter, signalDispatcher, currencyBridgeManager, crossServer, null, null, null, vanillaItemNameBridge, null);
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
            uiResourceExporter, bundledResourceWriter, signalDispatcher, currencyBridgeManager, crossServer, null, null, null, vanillaItemNameBridge, null);
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
        xuanmo.arcartxsuite.api.icon.IconResolver iconResolver
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
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    public void start() throws Exception {
        repository.initialize();
        loadPresets();
        syncPresetCdks();
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
                    ComposeSession session = composeSessions.remove(player.getUniqueId());
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            touchProfile(player, false);
        }

        cleanupTask = AxsScheduler.runTaskTimer(plugin, this::runCleanup, configuration.retention().cleanupIntervalTicks(), configuration.retention().cleanupIntervalTicks());
        crossServerChannel = crossServer.openChannel(
            "mail",
            configuration.crossServer(),
            delivery -> handleCrossServerMessage(delivery.payload())
        );
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }

        for (ComposeSession session : List.copyOf(composeSessions.values())) {
            Player player = Bukkit.getPlayer(session.playerUuid());
            if (player != null && player.isOnline() && !session.sent()) {
                returnComposeItems(player, session);
            }
        }
        for (AdminEditSession session : List.copyOf(adminEditSessions.values())) {
            Player player = Bukkit.getPlayer(session.playerUuid());
            if (player != null && player.isOnline()) {
                returnAdminItems(player, session);
            }
        }

        composeSessions.clear();
        composePasswordPanels.clear();
        adminEditSessions.clear();
        selectedMailIds.clear();
        inboxQueries.clear();
        logPages.clear();
        cdkPreviewStates.clear();
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

        repository.close();
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

    /**
     * 创建或更新预设并保存到 YAML 文件。
     */
    public MailOperationResult savePreset(MailPresetDefinition preset) {
        if (preset == null || preset.id() == null || preset.id().isBlank()) {
            return MailOperationResult.failure(message("player.preset-id-required"));
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
        if (presetId == null || presetId.isBlank()) {
            return MailOperationResult.failure(message("player.preset-id-required"));
        }
        String normalized = presetId.trim().toLowerCase(Locale.ROOT);
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

    /**
     * 重新从磁盘加载全部预设。
     */
    public MailOperationResult reloadPresets() {
        try {
            loadPresets();
            return MailOperationResult.success(message("admin.preset-reloaded", presets.size()));
        } catch (Exception exception) {
            this.logger.warning("重新加载预设失败: " + exception.getMessage());
            return MailOperationResult.failure(message("admin.preset-reload-failed", exception.getMessage()));
        }
    }

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

        AxsScheduler.runTask(plugin, () -> {
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

        Instant now = Instant.now();
        MailMessage message = new MailMessage(
            0L,
            playerUuid,
            null,
            senderName != null && !senderName.isBlank() ? senderName : "System",
            MailSourceType.SYSTEM,
            "",
            "",
            crop(subject, configuration.playerSend().subjectMaxLength()),
            crop(body, configuration.playerSend().bodyMaxLength()),
            MailStatus.UNREAD,
            copyAttachments(attachments),
            copyStrings(claimCommands),
            List.of(),
            now,
            expiresAt,
            now,
            null,
            null
        );
        MailOperationResult result = sendMailInternal(playerUuid, logContent, message, true);
        if (result.success()) {
            appendLog(playerUuid, "system", (logContent == null || logContent.isBlank()) ? message.subject() : logContent);
        }
        return result;
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
        try {
            touchProfile(player, false);
            inboxQueries.putIfAbsent(player.getUniqueId(), new MailInboxQuery(MailInboxFilter.ALL, 1, INBOX_PAGE_SIZE));
            inboxViewers.add(player.getUniqueId());
            logViewers.remove(player.getUniqueId());
            // 先打开 ArcartX UI（不鼠标重置），再延迟关闭 Bukkit 容器（compose/admin 会话）
            bridge.openUi(player, inboxUiId);
            AxsScheduler.runTask(plugin, () -> closeOtherMailUis(player));
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

        // 归还旧会话附件
        ComposeSession previous = composeSessions.remove(player.getUniqueId());
        composePasswordPanels.remove(player.getUniqueId());
        if (previous != null && !previous.sent()) {
            returnComposeItems(player, previous);
        }

        closeOtherMailUis(player);
        inboxViewers.remove(player.getUniqueId());
        logViewers.remove(player.getUniqueId());

        // 创建新会话（无 Bukkit 容器）
        ComposeSession session = new ComposeSession(UUID.randomUUID(), player.getUniqueId(), false, configuration.playerSend().feeCurrency());
        composeSessions.put(player.getUniqueId(), session);

        // 打开 ArcartX UI
        bridge.openUi(player, composeUiId);

        // 延迟 1 tick 发送 init 包（含背包和附件数据）
        AxsScheduler.runTask(plugin, () -> {
            if (player.isOnline()) {
                sendComposeInitPacket(player, session);
            }
        });
        return MailOperationResult.success(message("player.compose-opened"));
    }

    private void sendComposeInitPacket(Player player, ComposeSession session) {
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
        // 序列化附件和背包
        composePacket.put("attachment_jsons", serializeAttachments(session.attachments()));
        composePacket.put("backpack_jsons", serializeBackpack(player));
        bridge.sendPacket(player, composeUiId, "init", composePacket);
    }

    private Map<String, Object> serializeAttachments(List<ItemStack> attachments) {
        Map<String, Object> jsons = new LinkedHashMap<>();
        if (attachments == null) return jsons;
        for (int i = 0; i < attachments.size(); i++) {
            ItemStack item = attachments.get(i);
            if (item != null && !item.getType().isAir()) {
                jsons.put(Integer.toString(i), iconResolver.itemToJson(item));
            }
        }
        return jsons;
    }

    private Map<String, Object> serializeBackpack(Player player) {
        Map<String, Object> jsons = new LinkedHashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < Math.min(35, contents.length); i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
                jsons.put(Integer.toString(i), iconResolver.itemToJson(item));
            }
        }
        return jsons;
    }

    private Map<String, BigDecimal> buildAttachmentItemMap(List<ItemStack> attachments) {
        // compose 物品附件不产生货币金额，货币附件通过 vault 金额单独传入
        return Map.of();
    }

    public MailOperationResult claimAll(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }

        try {
            int claimed = 0;
            for (MailMessage message : repository.loadInbox(player.getUniqueId())) {
                if (message.claimable() && claimMail(player, message.id(), false).success()) {
                    claimed++;
                }
            }
            refreshPlayerViews(player, false);
            return claimed > 0
                ? MailOperationResult.success(message("player.claim-all-success", claimed))
                : MailOperationResult.failure(message("player.claim-all-empty"));
        } catch (Exception exception) {
            this.logger.warning("批量领取邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.claim-all-failed"));
        }
    }

    public MailOperationResult deleteAll(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }

        try {
            int deleted = 0;
            for (MailMessage message : repository.loadInbox(player.getUniqueId())) {
                if (deleteMail(player, message.id(), false).success()) {
                    deleted++;
                }
            }
            refreshPlayerViews(player, false);
            return deleted > 0
                ? MailOperationResult.success(message("player.delete-all-success", deleted))
                : MailOperationResult.failure(message("player.delete-all-empty"));
        } catch (Exception exception) {
            this.logger.warning("批量删除邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.delete-all-failed"));
        }
    }

    public MailOperationResult redeemCdk(Player player, String rawCode) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }

        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            return MailOperationResult.failure(message("player.cdk-required"));
        }

        // ── CDK 兑换限流检查 ──
        UUID playerId = player.getUniqueId();
        Instant now = Instant.now();
        CdkRateLimitEntry rateLimit = cdkRateLimitMap.computeIfAbsent(playerId, k -> new CdkRateLimitEntry());
        synchronized (rateLimit) {
            // 锁定检查
            if (rateLimit.lockoutUntil != null && rateLimit.lockoutUntil.isAfter(now)) {
                return MailOperationResult.failure(message("player.cdk-locked", TIME_FORMATTER.format(rateLimit.lockoutUntil)));
            }
            // 冷却检查
            if (rateLimit.lastFailAt != null && configuration.cdk().cooldownSeconds() > 0) {
                Instant cooldownEnd = rateLimit.lastFailAt.plusSeconds(configuration.cdk().cooldownSeconds());
                if (cooldownEnd.isAfter(now)) {
                    return MailOperationResult.failure(message("player.cdk-cooldown", TIME_FORMATTER.format(cooldownEnd)));
                }
            }
        }

        try {
            Optional<MailCdkDefinition> preview = repository.loadCdk(code);
            if (preview.isEmpty()) {
                cdkPreviewStates.put(playerId, new CdkPreviewState(code, "missing", message("player.cdk-preview-missing"), false));
                onCdkRedeemFailure(playerId, now);
                return MailOperationResult.failure(message("player.cdk-not-found"));
            }

            MailCdkDefinition previewDefinition = preview.get();
            MailPresetDefinition preset = preset(previewDefinition.presetId());
            if (preset == null || !preset.enabled()) {
                onCdkRedeemFailure(playerId, now);
                return MailOperationResult.failure(message("player.cdk-preset-unavailable"));
            }

            MailRepository.CdkClaimResult claimResult = repository.claimCdk(code, playerId, now);
            if (!claimResult.success()) {
                cdkPreviewStates.put(playerId, new CdkPreviewState(code, "rejected", claimResult.message(), false));
                onCdkRedeemFailure(playerId, now);
                return MailOperationResult.failure(claimResult.message());
            }

            MailOperationResult sendResult = sendPresetMail(
                new RecipientResolution(playerId, player.getName()),
                preset,
                MailSourceType.CDK,
                "",
                code,
                "CDK",
                true
            );
            if (!sendResult.success()) {
                cdkPreviewStates.put(playerId, new CdkPreviewState(code, "failed", sendResult.message(), false));
                onCdkRedeemFailure(playerId, now);
                return sendResult;
            }

            // 兑换成功：重置失败计数
            synchronized (rateLimit) {
                rateLimit.failCount = 0;
                rateLimit.lastFailAt = null;
                rateLimit.lockoutUntil = null;
            }
            appendLog(playerId, "cdk", "兑换 CDK " + code + "，奖励已投递到邮箱。");
            cdkPreviewStates.put(playerId, new CdkPreviewState(code, "success", preset.displayName(), true));
            refreshPlayerViews(player, false);
            dispatchCdkRedeemedSignal(player, code, preset);
            return MailOperationResult.success(message("player.cdk-claim-success"));
        } catch (Exception exception) {
            this.logger.warning("兑换 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.cdk-claim-failed"));
        }
    }

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
                if (sendPresetMail(recipient, preset, MailSourceType.PRESET, preset.id(), "", actorName, true).success()) {
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
                MailSourceType.PRESET,
                preset.id(),
                "",
                actorName,
                true
            );
        } catch (Exception exception) {
            this.logger.warning("派发预设邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.preset-dispatch-failed"));
        }
    }

    public MailOperationResult createCdk(String presetId, String rawCode, int maxClaims, Instant expiresAt, String createdBy) {
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
            String code = rawCode == null || rawCode.isBlank() || "auto".equalsIgnoreCase(rawCode)
                ? generateUniqueCdkCode()
                : normalizeCdkCode(rawCode);
            if (code.isBlank()) {
                return MailOperationResult.failure(message("player.cdk-format-invalid"));
            }
            if (repository.loadCdk(code).isPresent()) {
                return MailOperationResult.failure(message("player.cdk-exists", code));
            }

            Instant now = Instant.now();
            repository.saveCdk(new MailCdkDefinition(code, preset.id(), maxClaims, 0, expiresAt, true, safe(createdBy), now, now));
            return MailOperationResult.success(message("player.cdk-created", code));
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        ComposeSession session = composeSessions.remove(playerUuid);
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
        cdkPreviewStates.remove(playerUuid);
        inboxViewers.remove(playerUuid);
        logViewers.remove(playerUuid);
        // 清理 CDK 限流状态：仅清理未锁定的条目，锁定的保留（锁定期间玩家不能兑换）
        CdkRateLimitEntry rateLimitEntry = cdkRateLimitMap.get(playerUuid);
        if (rateLimitEntry != null) {
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

        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
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
                    long mailId = parseLong(data.get(1), -1L);
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
                    sendPlayerResult(player, handleComposeSend(player, data));
                }
                return true;
            }
            case "change-fee-currency" -> {
                if (data != null && data.size() >= 2) {
                    String currencyId = safe(data.get(1));
                    ComposeSession session = composeSessions.get(player.getUniqueId());
                    if (session != null && currencyId != null && !currencyId.isBlank()) {
                        session.setFeeCurrency(currencyId);
                        pushComposeQuote(player, calculateCurrentComposeQuote(player));
                    }
                }
                return true;
            }
            case "compose-add-attachment" -> {
                if (data != null && data.size() >= 3) {
                    int slot = (int) parseLong(data.get(1), -1);
                    String countStr = safe(data.get(2));
                    sendPlayerResult(player, handleComposeAddAttachment(player, slot, countStr));
                }
                return true;
            }
            case "compose-remove-attachment" -> {
                if (data != null && data.size() >= 2) {
                    int index = (int) parseLong(data.get(1), -1);
                    sendPlayerResult(player, handleComposeRemoveAttachment(player, index));
                }
                return true;
            }
            case "admin-add-attachment" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 3) {
                    int slot = (int) parseLong(data.get(1), -1);
                    String countStr = safe(data.get(2));
                    sendAdminResult(player, handleAdminAddAttachment(player, slot, countStr));
                }
                return true;
            }
            case "admin-remove-attachment" -> {
                if (!player.hasPermission("arcartxsuite.admin")) return true;
                if (data != null && data.size() >= 2) {
                    int index = (int) parseLong(data.get(1), -1);
                    sendAdminResult(player, handleAdminRemoveAttachment(player, index));
                }
                return true;
            }
            case "password-unlock" -> {
                sendPlayerResult(player, handlePasswordUnlock(player, data));
                return true;
            }
            case "claim" -> {
                if (data != null && data.size() >= 2) {
                    long mailId = parseLong(data.get(1), -1L);
                    if (mailId > 0L) {
                        sendPlayerResult(player, claimMail(player, mailId, true));
                    }
                }
                return true;
            }
            case "delete" -> {
                if (data != null && data.size() >= 2) {
                    long mailId = parseLong(data.get(1), -1L);
                    if (mailId > 0L) {
                        sendPlayerResult(player, deleteMail(player, mailId, true));
                    }
                }
                return true;
            }
            case "claimall" -> {
                sendPlayerResult(player, claimAll(player));
                return true;
            }
            case "deleteall" -> {
                sendPlayerResult(player, deleteAll(player));
                return true;
            }
            case "cdk" -> {
                if (data == null || data.size() < 2) {
                    sendPlayerResult(player, MailOperationResult.failure(message("player.cdk-please-enter")));
                } else {
                    sendPlayerResult(player, redeemCdk(player, data.get(1)));
                }
                return true;
            }
            case "logs" -> {
                sendPlayerResult(player, openLogs(player));
                return true;
            }
            case "logs-prev" -> {
                changeLogPage(player, -1);
                return true;
            }
            case "logs-next" -> {
                changeLogPage(player, 1);
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
            entry.put("displayName", safe(def.displayName()));
            entry.put("subject", safe(def.subject()));
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
        bridge.sendPacket(player, adminUiId, "preset-list", payload);
    }

    private void sendAdminPresetDetail(Player player, String presetId) {
        MailPresetDefinition def = getPreset(presetId);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (def == null) {
            payload.put("found", false);
            payload.put("id", safe(presetId));
            payload.put("itemAttachmentCount", 0);
        } else {
            payload.put("found", true);
            payload.put("id", def.id());
            payload.put("enabled", def.enabled());
            payload.put("displayName", safe(def.displayName()));
            payload.put("subject", safe(def.subject()));
            payload.put("body", safe(def.body()));
            payload.put("expiresAfterDays", def.expiresAfter() != null ? def.expiresAfter().toDays() : 15);
            List<Map<String, Object>> currencies = new ArrayList<>();
            if (def.attachments() != null) {
                for (MailAttachment att : def.attachments()) {
                    if (att.isCurrency()) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("currency", att.currencyId());
                        c.put("amount", att.amount());
                        c.put("description", att.description());
                        currencies.add(c);
                    }
                }
            }
            payload.put("currencyAttachments", currencies);
            long itemCount = def.attachments() == null ? 0 : def.attachments().stream().filter(MailAttachment::isItem).count();
            payload.put("itemAttachmentCount", itemCount);
            payload.put("claimCommands", def.claimCommands() != null ? def.claimCommands() : List.of());
            List<String> condStrings = new ArrayList<>();
            if (def.claimConditions() != null) {
                for (ScriptCondition cond : def.claimConditions()) {
                    condStrings.add(cond.serialize());
                }
            }
            payload.put("claimConditions", condStrings);
        }
        bridge.sendPacket(player, adminUiId, "preset-detail", payload);
    }

    private void handleAdminPresetSave(Player player, List<String> data) {
        // data: [action, id, enabled, displayName, subject, body, expiresAfterDays, currencyAttachments, claimCommands]
        if (data == null || data.size() < 7) {
            sendAdminResult(player, MailOperationResult.failure(message("player.admin-save-args-incomplete")));
            return;
        }
        try {
            String id = safe(data.get(1)).trim().toLowerCase(Locale.ROOT);
            if (id.isBlank()) {
                sendAdminResult(player, MailOperationResult.failure(message("player.preset-id-required")));
                return;
            }
            boolean enabled = "true".equalsIgnoreCase(safe(data.get(2)).trim());
            String displayName = safe(data.get(3)).trim();
            String subject = safe(data.get(4)).trim();
            String body = safe(data.get(5)).trim();
            long expiresAfterDays = parseLong(safe(data.get(6)).trim(), 15L);
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
                        MailItemSerializer.serialize(clean), "", 0.0D, resolveItemDescription(clean)
                    ));
                }
            }

            // 解析 currency attachments: "currency:amount:desc|currency:amount:desc"
            if (data.size() > 7 && !safe(data.get(7)).isBlank()) {
                String[] parts = safe(data.get(7)).split("\\|");
                for (String part : parts) {
                    String[] fields = part.split(":", 3);
                    if (fields.length >= 2) {
                        String currencyId = fields[0].trim().toLowerCase(Locale.ROOT);
                        double amount;
                        try { amount = Double.parseDouble(fields[1].trim()); } catch (NumberFormatException e) { continue; }
                        if (amount <= 0) continue;
                        String desc = fields.length >= 3 ? fields[2].trim() : currencyId + " " + amount;
                        attachments.add(new MailAttachment(0L, order++, MailAttachmentType.CURRENCY, "", currencyId, amount, desc));
                    }
                }
            }

            List<String> claimCommands = List.of();
            if (data.size() > 8 && !safe(data.get(8)).isBlank()) {
                claimCommands = List.of(safe(data.get(8)).split("\\|"));
            }

            MailPresetDefinition existing = getPreset(id);
            List<ScriptCondition> claimConditions = existing != null ? existing.claimConditions() : List.of();
            List<MailPresetCdkDefinition> cdks = existing != null ? existing.cdks() : List.of();

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
        if (player == null || !player.isOnline() || result == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("message", result.message());
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
                String currencyId = normalizeCurrencyId(entry.getKey());
                BigDecimal amount = normalizeAmount(entry.getValue());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                normalizedAttachmentAmounts.put(currencyId, amount);
                BigDecimal taxRate = BigDecimal.valueOf(configuration.attachmentTaxRate(currencyId));
                attachmentTaxes.put(currencyId, normalizeAmount(amount.multiply(taxRate)));
            }
        }

        BigDecimal baseFee = normalizeAmount(BigDecimal.valueOf(configuration.baseFee()));
        BigDecimal itemFee = normalizeAmount(BigDecimal.valueOf(configuration.itemFee()).multiply(BigDecimal.valueOf(Math.max(0, itemAttachmentCount))));
        BigDecimal totalFee = normalizeAmount(baseFee.add(itemFee));
        String effectiveFeeCurrency = feeCurrencyOverride != null && !feeCurrencyOverride.isBlank()
            ? normalizeCurrencyId(feeCurrencyOverride)
            : normalizeCurrencyId(configuration.feeCurrency());
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
        if (message == null || message.deleted() || message.expired()) {
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
        return safe(rawRecipient).trim();
    }

    private void loadPresets() throws IOException {
        presets.clear();
        File directory = new File(baseDataDir, configuration.presetsDirectory());
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建邮件预设目录: " + directory.getAbsolutePath());
        }
        exportBundledPresetIfMissing(directory);
        presets.putAll(MailPresetLoader.loadPresets(directory, this.logger, configuration.playerSend().maxAttachments(), itemSourceRegistry, vanillaItemNameBridge, iconResolver));
    }

    private void syncPresetCdks() throws Exception {
        Instant now = Instant.now();
        LinkedHashSet<String> seenCodes = new LinkedHashSet<>();
        int synced = 0;
        for (MailPresetDefinition preset : presets.values()) {
            if (!preset.enabled() || preset.cdks().isEmpty()) {
                continue;
            }
            for (MailPresetCdkDefinition cdk : preset.cdks()) {
                if (!seenCodes.add(cdk.code())) {
                    this.logger.warning("邮件预设 CDK 重复定义，后续重复项已跳过: " + cdk.code());
                    continue;
                }
                Optional<MailCdkDefinition> existing = repository.loadCdk(cdk.code());
                Instant createdAt = existing.map(MailCdkDefinition::createdAt).orElse(now);
                Instant expiresAt = cdk.expiresAt();
                if (expiresAt == null && cdk.expiresAfter() != null) {
                    expiresAt = createdAt.plus(cdk.expiresAfter());
                }
                MailCdkDefinition definition = new MailCdkDefinition(
                    cdk.code(),
                    preset.id(),
                    cdk.maxClaims(),
                    existing.map(MailCdkDefinition::claimedCount).orElse(0),
                    expiresAt,
                    cdk.enabled(),
                    existing.map(MailCdkDefinition::createdBy).filter(value -> !value.isBlank()).orElse("preset:" + preset.id()),
                    createdAt,
                    now
                );
                repository.saveCdk(definition);
                synced++;
            }
        }
        if (synced > 0) {
            this.logger.info("已同步邮件预设 CDK: " + synced + " 个。");
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

    private void touchProfile(Player player, boolean updateLastSendAt) {
        if (player == null) {
            return;
        }

        Instant now = Instant.now();
        try {
            Instant lastSendAt = updateLastSendAt ? now : repository.loadPlayerProfile(player.getUniqueId())
                .map(MailPlayerProfile::lastSendAt)
                .orElse(null);
            repository.upsertPlayerProfile(
                new MailPlayerProfile(player.getUniqueId(), player.getName(), lastSendAt, now, Bukkit.getServer().getName())
            );
        } catch (Exception exception) {
            this.logger.warning("更新邮箱玩家档案失败: " + exception.getMessage());
        }
    }

    private void runCleanup() {
        Instant now = Instant.now();
        try {
            repository.cleanupMail(
                now,
                now.minusSeconds(configuration.retention().claimedRetentionDays() * 86400L),
                now.minusSeconds(configuration.retention().deletedRetentionDays() * 86400L)
            );
            repository.cleanupCdks(now);
            refreshAllViewerStates();
        } catch (Exception exception) {
            this.logger.warning("执行邮件清理失败: " + exception.getMessage());
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
            AxsScheduler.runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    refreshPlayerViews(player, false);
                }
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void returnComposeItems(Player player, ComposeSession session) {
        if (player == null || session == null) return;
        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : session.attachments()) {
            if (!isRealItem(itemStack)) continue;
            HashMap<Integer, ItemStack> leftovers = playerInventory.addItem(itemStack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        session.attachments().clear();
    }

    private String crop(String rawValue, int maxLength) {
        String value = rawValue == null ? "" : rawValue.trim();
        int limit = Math.max(1, maxLength);
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private MailOperationResult sendMailInternal(UUID ownerUuid, String logContent, MailMessage message, boolean publishRedis) {
        try {
            repository.insertMail(message);
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
            return MailOperationResult.success(message("player.mail-send-success"));
        } catch (Exception exception) {
            this.logger.warning("发送邮件失败: " + exception.getMessage());
            if (configuration.debug() && logContent != null && !logContent.isBlank()) {
                this.logger.warning("发送邮件失败的上下文: " + logContent);
            }
            return MailOperationResult.failure(message("player.mail-send-failed"));
        }
    }

    private void sendMailNotifyCard(Player recipient, MailMessage message) {
        String cardId = configuration.ui().notifyCardId();
        if (cardId == null || cardId.isBlank()) {
            return;
        }
        try {
            MailUiConfiguration ui = configuration.ui();
            String subject = message.subject() == null ? "" : message.subject();
            int textWidth = measureTextWidth(subject, ui.charWidthFull(), ui.charWidthHalf());
            List<String> lines;
            if (textWidth <= ui.maxLineWidth()) {
                lines = List.of(subject);
            } else {
                lines = wrapText(subject, ui.maxLineWidth(), ui.charWidthFull(), ui.charWidthHalf());
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

    private static int measureTextWidth(String text, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                i++;
                continue;
            }
            width += (c >= '\u2E80') ? charWidthFull : charWidthHalf;
        }
        return width;
    }

    private static List<String> wrapText(String text, int maxLineWidth, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;
        String lastColor = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                lastColor = String.valueOf(c) + text.charAt(i + 1);
                currentLine.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            int cw = (c >= '\u2E80') ? charWidthFull : charWidthHalf;
            if (currentWidth + cw > maxLineWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(lastColor);
                currentWidth = 0;
            }
            currentLine.append(c);
            currentWidth += cw;
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private void refreshInbox(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            MailInboxQuery query = inboxQueries.computeIfAbsent(player.getUniqueId(), ignored -> new MailInboxQuery(MailInboxFilter.ALL, 1, INBOX_PAGE_SIZE));
            MailPage<MailMessage> page = repository.loadInboxPage(player.getUniqueId(), query);
            inboxQueries.put(player.getUniqueId(), query.withPage(page.page()));

            long selectedMailId = resolveSelectedMailId(page, selectedMailIds.getOrDefault(player.getUniqueId(), 0L));
            if (selectedMailId > 0L) {
                selectedMailIds.put(player.getUniqueId(), selectedMailId);
            } else {
                selectedMailIds.remove(player.getUniqueId());
            }

            int claimableCount = (int) page.entries().stream().filter(MailMessage::claimable).count();
            MailMailboxStats stats = repository.loadStats(player.getUniqueId());
            Map<String, Object> inboxPacket = MailInboxPacketFactory.build(page, selectedMailId, stats, query.filter(), claimableCount, messages);
            inboxPacket.put("is_admin", player.hasPermission("arcartxsuite.admin"));
            bridge.sendPacket(
                player,
                inboxUiId,
                initPacket ? "init" : "update",
                inboxPacket
            );
        } catch (Exception exception) {
            this.logger.warning("刷新邮箱界面失败: " + exception.getMessage());
        }
    }

    private void refreshLogs(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            int requestedPage = Math.max(1, logPages.getOrDefault(player.getUniqueId(), 1));
            MailPage<MailLogEntry> page = repository.loadLogPage(player.getUniqueId(), requestedPage, LOG_PAGE_SIZE);
            logPages.put(player.getUniqueId(), page.page());
            Map<String, Object> logsPacket = MailLogsPacketFactory.build(page, messages);
            logsPacket.put("is_admin", player.hasPermission("arcartxsuite.admin"));
            bridge.sendPacket(player, logsUiId, initPacket ? "init" : "update", logsPacket);
        } catch (Exception exception) {
            this.logger.warning("刷新日志界面失败: " + exception.getMessage());
        }
    }

    private MailOperationResult openLogs(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure(message("player.target-offline"));
        }
        try {
            touchProfile(player, false);
            logPages.putIfAbsent(player.getUniqueId(), 1);
            logViewers.add(player.getUniqueId());
            inboxViewers.remove(player.getUniqueId());
            // 先打开 ArcartX UI（不鼠标重置），再延迟关闭 Bukkit 容器（compose/admin 会话）
            bridge.openUi(player, logsUiId);
            AxsScheduler.runTask(plugin, () -> closeOtherMailUis(player));
            refreshLogs(player, true);
            return MailOperationResult.success(message("player.logs-opened"));
        } catch (Exception exception) {
            this.logger.warning("打开邮件日志失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.logs-open-failed"));
        }
    }

    private void changeLogPage(Player player, int delta) {
        UUID playerUuid = player.getUniqueId();
        int nextPage = Math.max(1, logPages.getOrDefault(playerUuid, 1) + delta);
        logPages.put(playerUuid, nextPage);
        refreshLogs(player, false);
    }

    private void selectMail(Player player, long mailId) {
        selectedMailIds.put(player.getUniqueId(), mailId);
        try {
            Optional<MailMessage> optionalMessage = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMessage.isEmpty()) {
                refreshInbox(player, false);
                return;
            }

            MailMessage message = optionalMessage.get();
            if (message.unread()) {
                Instant now = Instant.now();
                repository.updateMailState(
                    new MailMessage(
                        message.id(),
                        message.ownerUuid(),
                        message.senderUuid(),
                        message.senderName(),
                        message.sourceType(),
                        message.presetId(),
                        message.cdkCode(),
                        message.subject(),
                        message.body(),
                        MailStatus.READ,
                        message.attachments(),
                        message.claimCommands(),
                        message.claimConditions(),
                        message.createdAt(),
                        message.expiresAt(),
                        now,
                        message.claimedAt(),
                        message.deletedAt()
                    )
                );
            }
        } catch (Exception exception) {
            this.logger.warning("读取邮件详情失败: " + exception.getMessage());
        }
        refreshInbox(player, false);
    }

    private MailOperationResult handleComposeSend(Player player, List<String> data) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.compose-session-missing"));
        }
        String suppliedToken = data == null || data.size() <= 6 ? "" : safe(data.get(6));
        synchronized (session) {
            if (session.tokenInFlight
                || suppliedToken.isBlank()
                || !suppliedToken.equals(session.actionToken())) {
                // token 检查失败（并发冲突/token 轮换在途）静默丢弃，
                // 不再提示"写信操作令牌无效"——这些场景对正常玩家都是误报，UI 防抖会阻止重复点击。
                return MailOperationResult.failure("");
            }
            session.tokenInFlight = true;
            MailOperationResult result = handleComposeSendInternal(player, data);
            if (!result.success()) {
                session.tokenInFlight = false;
            }
            return result;
        }
    }

    private MailOperationResult handleComposeSendInternal(Player player, List<String> data) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.compose-session-missing"));
        }
        if (!session.sessionId().toString().equalsIgnoreCase(safe(data.get(1)))) {
            return MailOperationResult.failure(message("player.compose-session-expired"));
        }

        SecondaryPasswordAccess passwordAccess = secondaryPasswordAccess();
        if (passwordAccess == null) {
            MailOperationResult result = MailOperationResult.failure(message("player.compose-password-unavailable"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        if (passwordAccess.isPasswordSet(player) && !passwordAccess.isUnlocked(player)) {
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
        String subject = crop(data.get(3), configuration.playerSend().subjectMaxLength());
        String body = crop(data.get(4), configuration.playerSend().bodyMaxLength());
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
        if (containsBlockedText(subject) || containsBlockedText(body)) {
            MailOperationResult result = MailOperationResult.failure(message("player.body-blocked"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
        int maxAttachmentSlots = configuration.playerSend().maxAttachments();
        if (session.attachments().size() > maxAttachmentSlots) {
            MailOperationResult result = MailOperationResult.failure(message("player.attachment-slot-limit", maxAttachmentSlots));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }

        try {
            RecipientResolution recipient = resolveRecipient(recipientInput);
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

            Optional<MailPlayerProfile> senderProfile = repository.loadPlayerProfile(player.getUniqueId());
            if (senderProfile.isPresent() && senderProfile.get().lastSendAt() != null) {
                long cooldownSeconds = configuration.playerSend().cooldownSeconds();
                Instant nextAvailableAt = senderProfile.get().lastSendAt().plusSeconds(cooldownSeconds);
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

            BigDecimal vaultAmount = parseCurrencyAmount(data.get(5));
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

            Map<String, BigDecimal> charged = chargeSender(player, quote);
            if (charged.isEmpty() && requiredCurrencyTotal(quote).values().stream().anyMatch(amount -> amount.compareTo(BigDecimal.ZERO) > 0)) {
                MailOperationResult result = MailOperationResult.failure(message("player.charge-failed"));
                pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
                return result;
            }

            MailOperationResult sendResult = sendMailInternal(recipient.playerUuid(), "compose-send", mail, true);
            if (!sendResult.success()) {
                refundSender(player, charged);
                pushComposeQuote(player, MailSendQuote.failure(sendResult.message(), session.feeCurrency()));
                return sendResult;
            }

            touchProfile(player, true);
            appendLog(player.getUniqueId(), "send", "寄给 " + recipient.lastKnownName() + " 的邮件: " + subject);
            session.sent = true;
            session.attachments().clear();
            composeSessions.remove(player.getUniqueId());
            composePasswordPanels.remove(player.getUniqueId());
            AxsScheduler.runTask(plugin, () -> {
                if (player.isOnline()) {
                    openInbox(player);
                }
            });
            return MailOperationResult.success(message("player.mail-sent-to", recipient.lastKnownName()));
        } catch (Exception exception) {
            this.logger.warning("发送玩家邮件失败: " + exception.getMessage());
            MailOperationResult result = MailOperationResult.failure(message("player.mail-send-failed"));
            pushComposeQuote(player, MailSendQuote.failure(result.message(), session.feeCurrency()));
            return result;
        }
    }

    private MailOperationResult handlePasswordUnlock(Player player, List<String> data) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure(message("player.compose-session-missing"));
        }

        SecondaryPasswordAccess passwordAccess = secondaryPasswordAccess();
        if (passwordAccess == null) {
            return MailOperationResult.failure(message("player.compose-password-unavailable"));
        }

        String password = data != null && data.size() >= 2 ? safe(data.get(1)) : "";
        if (!passwordAccess.isPasswordSet(player) || passwordAccess.verify(player, password)) {
            composePasswordPanels.remove(player.getUniqueId());
            pushComposeQuote(player, calculateCurrentComposeQuote(player));
            return MailOperationResult.success(message("player.compose-password-unlocked"));
        }

        composePasswordPanels.add(player.getUniqueId());
        pushComposeQuote(
            player,
            MailSendQuote.failure(message("player.compose-password-invalid"), session.feeCurrency())
        );
        return MailOperationResult.failure(message("player.compose-password-invalid"));
    }

    private MailOperationResult handleComposeAddAttachment(Player player, int slot, String countStr) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null || session.sent()) {
            return MailOperationResult.failure(message("player.compose-not-open"));
        }
        if (slot < 0 || slot >= 35) {
            return MailOperationResult.failure("无效槽位");
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            return MailOperationResult.failure("该槽位没有物品");
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
        // 从玩家背包移除
        item.setAmount(item.getAmount() - count);
        if (item.getAmount() <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            player.getInventory().setItem(slot, item);
        }
        // 添加到附件列表
        session.attachments().add(toAdd);
        // 刷新 UI
        refreshCompose(player, false);
        return MailOperationResult.success("");
    }

    private MailOperationResult handleComposeRemoveAttachment(Player player, int index) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null || session.sent()) {
            return MailOperationResult.failure(message("player.compose-not-open"));
        }
        if (index < 0 || index >= session.attachments().size()) {
            return MailOperationResult.failure("无效附件索引");
        }
        ItemStack item = session.attachments().remove(index);
        if (item != null && !item.getType().isAir()) {
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        refreshCompose(player, false);
        return MailOperationResult.success("");
    }

    private MailOperationResult handleAdminAddAttachment(Player player, int slot, String countStr) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure("Admin 编辑会话不存在");
        }
        if (slot < 0 || slot >= 35) {
            return MailOperationResult.failure("无效槽位");
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            return MailOperationResult.failure("该槽位没有物品");
        }
        if (session.attachments().size() >= ADMIN_ATTACHMENT_SLOTS) {
            return MailOperationResult.failure("附件槽位已满");
        }
        int count = "all".equalsIgnoreCase(countStr) ? item.getAmount() : 1;
        count = Math.min(count, item.getAmount());
        ItemStack toAdd = item.clone();
        toAdd.setAmount(count);
        // 从玩家背包移除
        item.setAmount(item.getAmount() - count);
        if (item.getAmount() <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            player.getInventory().setItem(slot, item);
        }
        // 添加到附件列表
        session.attachments().add(toAdd);
        // 刷新 admin UI
        refreshAdminAttachments(player);
        return MailOperationResult.success("");
    }

    private MailOperationResult handleAdminRemoveAttachment(Player player, int index) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure("Admin 编辑会话不存在");
        }
        if (index < 0 || index >= session.attachments().size()) {
            return MailOperationResult.failure("无效附件索引");
        }
        ItemStack item = session.attachments().remove(index);
        if (item != null && !item.getType().isAir() && !isTemplateItem(item)) {
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        refreshAdminAttachments(player);
        return MailOperationResult.success("");
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
     * 刷新 admin UI 附件区域：发送 attachment-count + attachment_jsons + backpack_jsons。
     */
    private void refreshAdminAttachments(Player player) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;
        int count = session.attachments().size();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemAttachmentCount", count);
        payload.put("attachment_jsons", serializeAttachments(session.attachments()));
        payload.put("backpack_jsons", serializeBackpack(player));
        bridge.sendPacket(player, adminUiId, "attachment-count", payload);
    }

    private String requiredUiForAction(String action) {
        if ("compose-send".equals(action)
            || "password-unlock".equals(action)
            || "change-fee-currency".equals(action)
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

    private MailOperationResult claimMail(Player player, long mailId, boolean refresh) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure(message("player.mail-not-found"));
            }

            MailMessage mail = optionalMail.get();
            if (!mail.claimable()) {
                return MailOperationResult.failure(message("player.mail-not-claimable"));
            }
            if (!checkClaimConditions(player, mail.claimConditions())) {
                return MailOperationResult.failure(message("player.mail-claim-conditions-unmet"));
            }

            List<ItemStack> itemRewards;
            try {
                itemRewards = deserializeItemRewards(mail.attachments());
            } catch (IOException exception) {
                this.logger.warning("读取邮件物品附件失败: " + exception.getMessage());
                return MailOperationResult.failure(message("player.attachment-read-failed"));
            }
            MailOperationResult currencyValidation = validateClaimCurrencies(mail.attachments());
            if (!currencyValidation.success()) {
                return currencyValidation;
            }

            // 检查背包空间是否足够容纳所有物品附件
            if (!hasEnoughInventorySpace(player, itemRewards)) {
                return MailOperationResult.failure(message("player.mail-claim-inventory-full"));
            }

            Instant now = Instant.now();
            MailStatus previousStatus = mail.status();
            if (!repository.tryClaimMail(player.getUniqueId(), mailId, now)) {
                return MailOperationResult.failure(message("player.mail-already-claimed"));
            }

            deliverItemRewards(player, itemRewards);
            for (String command : mail.claimCommands()) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayerTokens(command, player));
                } catch (Exception e) {
                    logger.warning("[Mail] 执行领取命令失败: " + command + " | " + e.getMessage());
                }
            }
            MailOperationResult currencyResult = depositClaimCurrencies(player, mail.attachments());
            if (!currencyResult.success()) {
                rollbackClaimedMail(mail, previousStatus);
                return currencyResult;
            }

            appendLog(player.getUniqueId(), "claim", "领取邮件: " + mail.subject());
            publishRefresh(player.getUniqueId());
            if (refresh) {
                refreshPlayerViews(player, false);
            }
            return MailOperationResult.success(message("player.mail-claimed"));
        } catch (Exception exception) {
            this.logger.warning("领取邮件失败: " + exception.getMessage());
            return MailOperationResult.failure(message("player.mail-claim-failed"));
        }
    }

    private void rollbackClaimedMail(MailMessage mail, MailStatus previousStatus) {
        try {
            Instant now = Instant.now();
            repository.updateMailState(
                new MailMessage(
                    mail.id(),
                    mail.ownerUuid(),
                    mail.senderUuid(),
                    mail.senderName(),
                    mail.sourceType(),
                    mail.presetId(),
                    mail.cdkCode(),
                    mail.subject(),
                    mail.body(),
                    previousStatus,
                    mail.attachments(),
                    mail.claimCommands(),
                    mail.claimConditions(),
                    mail.createdAt(),
                    mail.expiresAt(),
                    now,
                    null,
                    mail.deletedAt()
                )
            );
        } catch (SQLException exception) {
            this.logger.warning("回滚邮件领取状态失败: mailId=" + mail.id() + " | " + exception.getMessage());
        }
    }

    private MailOperationResult deleteMail(Player player, long mailId, boolean refresh) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure(message("player.mail-not-found"));
            }

            MailMessage mail = optionalMail.get();
            if (!canDeleteMail(configuration.retention().allowDeleteWithUnclaimedAttachments(), mail)) {
                return MailOperationResult.failure(message("player.mail-delete-restricted"));
            }

            Instant now = Instant.now();
            repository.updateMailState(
                new MailMessage(
                    mail.id(),
                    mail.ownerUuid(),
                    mail.senderUuid(),
                    mail.senderName(),
                    mail.sourceType(),
                    mail.presetId(),
                    mail.cdkCode(),
                    mail.subject(),
                    mail.body(),
                    MailStatus.DELETED,
                    mail.attachments(),
                    mail.claimCommands(),
                    mail.claimConditions(),
                    mail.createdAt(),
                    mail.expiresAt(),
                    now,
                    mail.claimedAt(),
                    now
                )
            );
            appendLog(player.getUniqueId(), "delete", "删除邮件: " + mail.subject());
            publishRefresh(player.getUniqueId());
            if (refresh) {
                refreshPlayerViews(player, false);
            }
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
                repository.updateMailState(
                    new MailMessage(
                        mail.id(), mail.ownerUuid(), mail.senderUuid(), mail.senderName(),
                        mail.sourceType(), mail.presetId(), mail.cdkCode(),
                        mail.subject(), mail.body(), MailStatus.READ,
                        mail.attachments(), mail.claimCommands(), mail.claimConditions(),
                        mail.createdAt(), mail.expiresAt(), now,
                        mail.claimedAt(), mail.deletedAt()
                    )
                );
                return Optional.of(withStatus(mail, MailStatus.READ, now));
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
    public MailOperationResult claimMailByUuid(UUID playerUuid, long mailId) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("PLAYER_OFFLINE");
        }
        return claimMail(player, mailId, false);
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
            Instant now = Instant.now();
            repository.updateMailState(
                new MailMessage(
                    mail.id(), mail.ownerUuid(), mail.senderUuid(), mail.senderName(),
                    mail.sourceType(), mail.presetId(), mail.cdkCode(),
                    mail.subject(), mail.body(), MailStatus.DELETED,
                    mail.attachments(), mail.claimCommands(), mail.claimConditions(),
                    mail.createdAt(), mail.expiresAt(), now,
                    mail.claimedAt(), now
                )
            );
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
        String normalizedTarget = safe(target).trim();
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
                .map(profile -> new RecipientResolution(profile.playerUuid(), safe(profile.lastKnownName()).isBlank() ? profile.playerUuid().toString() : profile.lastKnownName()))
                .toList();
        }

        RecipientResolution single = resolveRecipient(normalizedTarget);
        return single == null ? List.of() : List.of(single);
    }

    private MailOperationResult sendPresetMail(
        RecipientResolution recipient,
        MailPresetDefinition preset,
        MailSourceType sourceType,
        String presetId,
        String cdkCode,
        String senderName,
        boolean publishRedis
    ) {
        if (recipient == null || preset == null) {
            return MailOperationResult.failure(message("player.preset-mail-params-invalid"));
        }

        Instant now = Instant.now();
        Instant expiresAt = preset.expiresAfter() == null
            ? now.plusSeconds(configuration.retention().defaultExpireAfterDays() * 86400L)
            : now.plus(preset.expiresAfter());
        MailMessage message = new MailMessage(
            0L,
            recipient.playerUuid(),
            null,
            safe(senderName).isBlank() ? "System" : senderName,
            sourceType,
            safe(presetId).isBlank() ? preset.id() : presetId,
            safe(cdkCode),
            crop(preset.subject(), configuration.playerSend().subjectMaxLength()),
            crop(preset.body(), configuration.playerSend().bodyMaxLength()),
            MailStatus.UNREAD,
            copyAttachments(preset.attachments()),
            copyStrings(preset.claimCommands()),
            copyConditions(preset.claimConditions()),
            now,
            expiresAt,
            now,
            null,
            null
        );
        MailOperationResult result = sendMailInternal(recipient.playerUuid(), "preset-send", message, publishRedis);
        if (result.success()) {
            appendLog(recipient.playerUuid(), sourceType == MailSourceType.CDK ? "cdk" : "preset", "收到邮件: " + preset.displayName());
        }
        return result;
    }

    private RecipientResolution resolveRecipient(String rawRecipient) throws Exception {
        String requestedName = normalizeRecipientInput(rawRecipient);
        if (requestedName.isBlank()) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(requestedName);
        if (online == null) {
            online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
        }
        if (online != null) {
            return new RecipientResolution(online.getUniqueId(), online.getName());
        }

        Optional<MailPlayerProfile> profile = repository.findPlayerProfileByName(requestedName);
        if (profile.isPresent()) {
            MailPlayerProfile value = profile.get();
            return new RecipientResolution(value.playerUuid(), safe(value.lastKnownName()).isBlank() ? requestedName : value.lastKnownName());
        }

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
            if (!isRealItem(itemStack)) {
                continue;
            }
            if (configuration.moderation().blockedMaterials().contains(itemStack.getType().name())) {
                return MailOperationResult.failure(message("player.attachment-blocked-material", itemStack.getType().name()));
            }
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (matchesBlockedLore(line)) {
                        return MailOperationResult.failure(message("player.attachment-blocked-lore"));
                    }
                }
            }
        }
        return MailOperationResult.success("");
    }

    private boolean containsBlockedText(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        for (String blockedWord : configuration.moderation().blockedWords()) {
            if (!blockedWord.isBlank() && normalized.contains(blockedWord)) {
                return true;
            }
        }
        for (Pattern blockedPattern : configuration.moderation().blockedPatterns()) {
            if (blockedPattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBlockedLore(String value) {
        String normalized = safe(value);
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
            if (isRealItem(item)) {
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
            // 序列化附件和背包
            if (session != null) {
                composeUpdatePacket.put("attachment_jsons", serializeAttachments(session.attachments()));
            }
            composeUpdatePacket.put("backpack_jsons", serializeBackpack(player));
            bridge.sendPacket(
                player,
                composeUiId,
                "update",
                composeUpdatePacket
            );
        }
    }

    private void scheduleComposeQuoteRefresh(Player player) {
        AxsScheduler.runTask(plugin, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }
            ComposeSession session = composeSessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }
            int attachmentCount = session.attachments().size();
            pushComposeQuote(player, calculateComposeQuote(configuration.playerSend(), Map.of(), attachmentCount, session.feeCurrency()));
        });
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

    private Map<String, BigDecimal> chargeSender(Player player, MailSendQuote quote) {
        Map<String, BigDecimal> charged = new LinkedHashMap<>();
        Map<String, BigDecimal> requiredTotals = requiredCurrencyTotal(quote);
        for (Map.Entry<String, BigDecimal> entry : requiredTotals.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(entry.getKey());
            if (currencyBridge == null || !currencyBridge.available()) {
                refundSender(player, charged);
                return Map.of();
            }
            CurrencyTransactionResult result = currencyBridge.withdraw(player, amount);
            if (!result.success()) {
                refundSender(player, charged);
                return Map.of();
            }
            charged.put(entry.getKey(), amount);
        }
        return Map.copyOf(charged);
    }

    private void refundSender(Player player, Map<String, BigDecimal> charged) {
        if (charged == null || charged.isEmpty()) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : charged.entrySet()) {
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(entry.getKey());
            if (currencyBridge != null && currencyBridge.available()) {
                currencyBridge.deposit(player, entry.getValue());
            }
        }
    }

    private Map<String, BigDecimal> requiredCurrencyTotal(MailSendQuote quote) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (quote == null) {
            return totals;
        }
        totals.put(quote.feeCurrencyId(), normalizeAmount(quote.totalFee()));
        mergeCurrencyTotals(totals, quote.attachmentAmounts());
        mergeCurrencyTotals(totals, quote.attachmentTaxes());
        return Map.copyOf(totals);
    }

    private void mergeCurrencyTotals(Map<String, BigDecimal> totals, Map<String, BigDecimal> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String currencyId = normalizeCurrencyId(entry.getKey());
            BigDecimal amount = normalizeAmount(entry.getValue());
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
                    resolveItemDescription(itemStack)
                )
            );
        }
        for (Map.Entry<String, BigDecimal> entry : attachmentAmounts.entrySet()) {
            BigDecimal amount = normalizeAmount(entry.getValue());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String currencyId = normalizeCurrencyId(entry.getKey());
            attachments.add(
                new MailAttachment(
                    0L,
                    sortOrder++,
                    MailAttachmentType.CURRENCY,
                    "",
                    currencyId,
                    amount.doubleValue(),
                    currencyDisplay(currencyId) + " " + trimDecimal(amount)
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

    private List<ItemStack> deserializeItemRewards(List<MailAttachment> attachments) throws IOException {
        List<ItemStack> result = new ArrayList<>();
        if (attachments == null) {
            return List.of();
        }
        for (MailAttachment attachment : attachments) {
            if (!attachment.isItem()) {
                continue;
            }
            result.add(MailItemSerializer.deserialize(attachment.itemData()));
        }
        return List.copyOf(result);
    }

    private void deliverItemRewards(Player player, List<ItemStack> itemRewards) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack itemReward : itemRewards) {
            HashMap<Integer, ItemStack> leftovers = inventory.addItem(itemReward.clone());
            // 理论上不应有剩余（领取前已检查空间），但作为兜底仍掉落地面避免丢物品
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
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
        // 克隆背包内容进行模拟
        ItemStack[] contents = player.getInventory().getStorageContents().clone();
        org.bukkit.inventory.Inventory simulated = Bukkit.createInventory(null, 36);
        simulated.setContents(contents);
        for (ItemStack itemReward : itemRewards) {
            Map<Integer, ItemStack> leftovers = simulated.addItem(itemReward.clone());
            if (!leftovers.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private MailOperationResult validateClaimCurrencies(List<MailAttachment> attachments) {
        if (attachments == null) {
            return MailOperationResult.success("");
        }
        for (MailAttachment attachment : attachments) {
            if (!attachment.isCurrency() || attachment.amount() <= 0.0D) {
                continue;
            }
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(attachment.normalizedCurrencyId());
            if (currencyBridge == null || !currencyBridge.available()) {
                return MailOperationResult.failure(message("player.attachment-currency-unavailable", attachment.normalizedCurrencyId()));
            }
        }
        return MailOperationResult.success("");
    }

    private MailOperationResult depositClaimCurrencies(Player player, List<MailAttachment> attachments) {
        if (attachments == null) {
            return MailOperationResult.success("");
        }
        for (MailAttachment attachment : attachments) {
            if (!attachment.isCurrency() || attachment.amount() <= 0.0D) {
                continue;
            }
            CurrencyBridge currencyBridge = currencyBridgeManager.bridge(attachment.normalizedCurrencyId());
            if (currencyBridge == null || !currencyBridge.available()) {
                return MailOperationResult.failure(message("player.attachment-currency-unavailable", attachment.normalizedCurrencyId()));
            }
            CurrencyTransactionResult result = currencyBridge.deposit(player, BigDecimal.valueOf(attachment.amount()));
            if (!result.success()) {
                return MailOperationResult.failure(message("player.claim-currency-failed", attachment.normalizedCurrencyId()));
            }
        }
        return MailOperationResult.success("");
    }

    private void appendLog(UUID playerUuid, String type, String content) {
        if (playerUuid == null) {
            return;
        }
        try {
            repository.appendLog(new MailLogEntry(0L, playerUuid, safe(type), safe(content), Instant.now()));
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline() && logViewers.contains(playerUuid)) {
                refreshLogs(player, false);
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

    private long resolveSelectedMailId(MailPage<MailMessage> page, long selectedMailId) {
        if (page.entries().isEmpty()) {
            return 0L;
        }
        if (selectedMailId > 0L && page.entries().stream().anyMatch(message -> message.id() == selectedMailId)) {
            return selectedMailId;
        }
        return page.entries().get(0).id();
    }

    private static int compareNumeric(String actualValue, String expectedValue) {
        BigDecimal actual = parseNumeric(actualValue);
        BigDecimal expected = parseNumeric(expectedValue);
        if (actual == null || expected == null) {
            return Integer.MIN_VALUE;
        }
        return actual.compareTo(expected);
    }

    private static BigDecimal parseNumeric(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim().replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO)).setScale(4, RoundingMode.DOWN);
    }

    private static BigDecimal parseCurrencyAmount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.DOWN);
        }
        try {
            return normalizeAmount(new BigDecimal(rawValue.trim().replace(",", "")));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalizeCurrencyId(String currencyId) {
        return currencyId == null || currencyId.isBlank() ? "money" : currencyId.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir() && itemStack.getAmount() > 0;
    }

    private static List<MailAttachment> copyAttachments(List<MailAttachment> attachments) {
        return attachments == null ? List.of() : List.copyOf(attachments);
    }

    private static List<String> copyStrings(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<ScriptCondition> copyConditions(List<ScriptCondition> conditions) {
        return conditions == null ? List.of() : List.copyOf(conditions);
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

    private String trimDecimal(BigDecimal amount) {
        return normalizeAmount(amount).stripTrailingZeros().toPlainString();
    }

    private String replacePlayerTokens(String command, Player player) {
        String safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
        return safe(command)
            .replace("<player>", safeName)
            .replace("{player}", safeName)
            .replace("%player%", safeName);
    }

    private String generateUniqueCdkCode() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = normalizeCdkCode(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            if (candidate.isBlank()) {
                continue;
            }
            if (repository.loadCdk(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成唯一 CDK。");
    }

    private static final class ComposeSession {
        private final UUID sessionId;
        private final UUID playerUuid;
        private final List<ItemStack> attachments;
        private final String actionToken;
        private boolean tokenInFlight;
        private boolean sent;
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

    private void populateAdminContainer(Player player, String presetId) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;

        // 先归还旧物品并清空附件列表
        returnAdminItems(player, session);
        session.attachments().clear();
        session.editingPresetId = safe(presetId).trim().toLowerCase(Locale.ROOT);

        MailPresetDefinition def = getPreset(presetId);
        if (def == null || def.attachments() == null) {
            refreshAdminAttachments(player);
            return;
        }

        for (MailAttachment att : def.attachments()) {
            if (!att.isItem() || session.attachments().size() >= ADMIN_ATTACHMENT_SLOTS) break;
            try {
                ItemStack item = MailItemSerializer.deserialize(att.itemData());
                if (isRealItem(item)) {
                    markAsTemplateItem(item);
                    session.attachments().add(item);
                }
            } catch (Exception exception) {
                this.logger.warning("Admin UI 反序列化物品附件失败: " + exception.getMessage());
            }
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
        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : session.attachments()) {
            if (!isRealItem(itemStack)) continue;
            if (isTemplateItem(itemStack)) continue;
            HashMap<Integer, ItemStack> leftovers = playerInventory.addItem(itemStack.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private List<ItemStack> collectAdminItems(AdminEditSession session) {
        List<ItemStack> result = new ArrayList<>();
        if (session == null) return List.of();
        for (ItemStack itemStack : session.attachments()) {
            if (isRealItem(itemStack)) {
                result.add(itemStack.clone());
            }
        }
        return List.copyOf(result);
    }

    private void scheduleAdminAttachmentCountRefresh(Player player) {
        AxsScheduler.runTask(plugin, () -> {
            if (player == null || !player.isOnline()) return;
            AdminEditSession session = adminEditSessions.get(player.getUniqueId());
            if (session == null) return;
            refreshAdminAttachments(player);
        });
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

    private static record CdkPreviewState(String code, String status, String description, boolean redeemable) {
        private static CdkPreviewState empty() {
            return new CdkPreviewState("", "", "", false);
        }
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

    private void dispatchCdkRedeemedSignal(Player player, String code, MailPresetDefinition preset) {
        if (player == null || !player.isOnline()) {
            return;
        }
        java.util.Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("cdk_code", code);
        variables.put("preset_id", preset.id());
        variables.put("preset_name", preset.displayName());
        if (signalDispatcher != null) {
            signalDispatcher.accept("cdk_redeemed", player);
        }
    }

}
