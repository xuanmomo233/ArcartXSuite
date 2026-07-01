package xuanmo.arcartxsuite.mail.service;

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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import xuanmo.arcartxsuite.api.util.InventoryViewCompat;
import org.bukkit.inventory.InventoryHolder;
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
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
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
    private static final String ADMIN_INVENTORY_TITLE = "AXS Mail Admin";
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
    private final Map<String, MailPresetDefinition> presets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> selectedMailIds = new ConcurrentHashMap<>();
    private final Map<UUID, MailInboxQuery> inboxQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> logPages = new ConcurrentHashMap<>();
    private final Map<UUID, ComposeSession> composeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, AdminEditSession> adminEditSessions = new ConcurrentHashMap<>();
    private final Map<UUID, CdkPreviewState> cdkPreviewStates = new ConcurrentHashMap<>();
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
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer
    ) {
        this(plugin, plugin.getDataFolder(), configuration, repository, bridge, packetGuard,
            uiResourceExporter, bundledResourceWriter, signalDispatcher, currencyBridgeManager, crossServer);
    }

    public MailService(
        JavaPlugin plugin,
        File baseDataDir,
        MailModuleConfiguration configuration,
        MailRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        BundledResourceWriter bundledResourceWriter,
        BiConsumer<String, Player> signalDispatcher,
        CurrencyBridgeAPI currencyBridgeManager,
        CrossServerAPI crossServer
    ) {
        this.plugin = plugin;
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
        if (!"AXS Mail Compose".equals(configuration.ui().composeInventoryTitle())) {
            plugin.getLogger()
                .warning(
                    "ArcartXMail 的 compose-inventory-title 当前不是默认值。mail_compose.yml 的 match 仍默认匹配 'AXS Mail Compose'，如需修改标题，请同步调整 ArcartX UI 文件。"
                );
        }

        logUnavailableCurrencyProviders();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            touchProfile(player, false);
        }

        cleanupTask = Bukkit.getScheduler()
            .runTaskTimer(plugin, this::runCleanup, configuration.retention().cleanupIntervalTicks(), configuration.retention().cleanupIntervalTicks());
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
        adminEditSessions.clear();
        selectedMailIds.clear();
        inboxQueries.clear();
        logPages.clear();
        cdkPreviewStates.clear();
        inboxViewers.clear();
        logViewers.clear();
        HandlerList.unregisterAll(this);

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
            return MailOperationResult.failure("预设 ID 不能为空。");
        }
        try {
            File directory = new File(baseDataDir, configuration.presetsDirectory());
            MailPresetLoader.savePreset(directory, preset);
            presets.put(preset.id().trim().toLowerCase(Locale.ROOT), preset);
            return MailOperationResult.success("预设已保存: " + preset.id());
        } catch (Exception exception) {
            plugin.getLogger().warning("保存预设失败: " + exception.getMessage());
            return MailOperationResult.failure("保存预设失败: " + exception.getMessage());
        }
    }

    /**
     * 删除预设（从内存和 YAML 文件）。
     */
    public MailOperationResult deletePreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return MailOperationResult.failure("预设 ID 不能为空。");
        }
        String normalized = presetId.trim().toLowerCase(Locale.ROOT);
        MailPresetDefinition removed = presets.remove(normalized);
        if (removed == null) {
            return MailOperationResult.failure("预设不存在: " + presetId);
        }
        try {
            File directory = new File(baseDataDir, configuration.presetsDirectory());
            MailPresetLoader.deletePresetFile(directory, normalized);
            return MailOperationResult.success("预设已删除: " + presetId);
        } catch (Exception exception) {
            plugin.getLogger().warning("删除预设文件失败: " + exception.getMessage());
            return MailOperationResult.success("预设已从内存移除，但文件删除失败: " + exception.getMessage());
        }
    }

    /**
     * 重新从磁盘加载全部预设。
     */
    public MailOperationResult reloadPresets() {
        try {
            loadPresets();
            return MailOperationResult.success("已重新加载 " + presets.size() + " 个预设。");
        } catch (Exception exception) {
            plugin.getLogger().warning("重新加载预设失败: " + exception.getMessage());
            return MailOperationResult.failure("重新加载预设失败: " + exception.getMessage());
        }
    }

    /**
     * 为管理员玩家打开 Admin UI（容器背板 + ArcartX 覆盖层）并发送预设列表。
     */
    public void openAdminUi(Player player) {
        if (player == null || !player.isOnline()) return;
        if (adminUiId == null) {
            player.sendMessage(MESSAGE_PREFIX + ChatColor.RED + "Admin UI 未注册。");
            return;
        }

        AdminEditSession previous = adminEditSessions.remove(player.getUniqueId());
        if (previous != null) {
            returnAdminItems(player, previous);
        }

        AdminEditInventoryHolder holder = new AdminEditInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9, ADMIN_INVENTORY_TITLE);
        holder.inventory = inventory;
        AdminEditSession session = new AdminEditSession(player.getUniqueId(), inventory, null);
        adminEditSessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);

        Bukkit.getScheduler().runTask(plugin, () -> {
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
            plugin.getLogger().warning("读取邮箱统计失败: " + exception.getMessage());
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
            return MailOperationResult.failure("收件人不能为空。");
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

    public MailOperationResult openInbox(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
        }
        try {
            touchProfile(player, false);
            inboxQueries.putIfAbsent(player.getUniqueId(), new MailInboxQuery(MailInboxFilter.ALL, 1, INBOX_PAGE_SIZE));
            inboxViewers.add(player.getUniqueId());
            logViewers.remove(player.getUniqueId());
            bridge.openUi(player, inboxUiId);
            refreshInbox(player, true);
            return MailOperationResult.success("已打开邮箱。");
        } catch (Exception exception) {
            plugin.getLogger().warning("打开邮箱失败: " + exception.getMessage());
            return MailOperationResult.failure("打开邮箱失败，请查看控制台。");
        }
    }

    public MailOperationResult openCompose(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
        }

        ComposeSession previous = composeSessions.remove(player.getUniqueId());
        if (previous != null && !previous.sent()) {
            returnComposeItems(player, previous);
        }

        inboxViewers.remove(player.getUniqueId());
        logViewers.remove(player.getUniqueId());

        ComposeInventoryHolder holder = new ComposeInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, configuration.ui().composeInventoryTitle());
        holder.inventory = inventory;
        ComposeSession session = new ComposeSession(UUID.randomUUID(), player.getUniqueId(), inventory, false);
        composeSessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                bridge.sendPacket(
                    player,
                    composeUiId,
                    "init",
                    MailComposePacketFactory.buildInit(
                        session.sessionId(),
                        configuration,
                        currencyBridgeManager,
                        calculateComposeQuote(configuration.playerSend(), Map.of(), 0),
                        effectiveAttachmentSlots(inventory),
                        0
                    )
                );
            }
        });
        return MailOperationResult.success("已打开写信界面。");
    }

    public MailOperationResult claimAll(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
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
                ? MailOperationResult.success("已批量领取 " + claimed + " 封邮件。")
                : MailOperationResult.failure("当前没有可领取的邮件。");
        } catch (Exception exception) {
            plugin.getLogger().warning("批量领取邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("批量领取失败。");
        }
    }

    public MailOperationResult deleteAll(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
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
                ? MailOperationResult.success("已批量删除 " + deleted + " 封邮件。")
                : MailOperationResult.failure("没有可删除的邮件。");
        } catch (Exception exception) {
            plugin.getLogger().warning("批量删除邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("批量删除失败。");
        }
    }

    public MailOperationResult redeemCdk(Player player, String rawCode) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
        }

        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            return MailOperationResult.failure("CDK 不能为空。");
        }

        try {
            Optional<MailCdkDefinition> preview = repository.loadCdk(code);
            if (preview.isEmpty()) {
                cdkPreviewStates.put(player.getUniqueId(), new CdkPreviewState(code, "missing", "CDK 不存在。", false));
                return MailOperationResult.failure("CDK 不存在。");
            }

            MailCdkDefinition previewDefinition = preview.get();
            MailPresetDefinition preset = preset(previewDefinition.presetId());
            if (preset == null || !preset.enabled()) {
                return MailOperationResult.failure("CDK 绑定的预设不存在或未启用。");
            }

            MailRepository.CdkClaimResult claimResult = repository.claimCdk(code, player.getUniqueId(), Instant.now());
            if (!claimResult.success()) {
                cdkPreviewStates.put(player.getUniqueId(), new CdkPreviewState(code, "rejected", claimResult.message(), false));
                return MailOperationResult.failure(claimResult.message());
            }

            MailOperationResult sendResult = sendPresetMail(
                new RecipientResolution(player.getUniqueId(), player.getName()),
                preset,
                MailSourceType.CDK,
                "",
                code,
                "CDK",
                true
            );
            if (!sendResult.success()) {
                cdkPreviewStates.put(player.getUniqueId(), new CdkPreviewState(code, "failed", sendResult.message(), false));
                return sendResult;
            }

            appendLog(player.getUniqueId(), "cdk", "兑换 CDK " + code + "，奖励已投递到邮箱。");
            cdkPreviewStates.put(player.getUniqueId(), new CdkPreviewState(code, "success", preset.displayName(), true));
            refreshPlayerViews(player, false);
            dispatchCdkRedeemedSignal(player, code, preset);
            return MailOperationResult.success("CDK 兑换成功，奖励已发送到邮箱。");
        } catch (Exception exception) {
            plugin.getLogger().warning("兑换 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure("兑换 CDK 失败。");
        }
    }

    public MailOperationResult dispatchPreset(String presetId, String target, String actorName) {
        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure("未找到邮件预设: " + presetId);
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure("邮件预设已禁用: " + preset.id());
        }

        try {
            List<RecipientResolution> recipients = resolvePresetRecipients(target);
            if (recipients.isEmpty()) {
                return MailOperationResult.failure("没有找到可派发的目标玩家。");
            }

            int successCount = 0;
            for (RecipientResolution recipient : recipients) {
                if (sendPresetMail(recipient, preset, MailSourceType.PRESET, preset.id(), "", actorName, true).success()) {
                    successCount++;
                }
            }

            if (successCount <= 0) {
                return MailOperationResult.failure("预设邮件派发失败。");
            }
            if (successCount < recipients.size()) {
                return MailOperationResult.success("预设邮件已部分派发: " + successCount + "/" + recipients.size());
            }
            return MailOperationResult.success("预设邮件已派发给 " + successCount + " 名玩家。");
        } catch (Exception exception) {
            plugin.getLogger().warning("派发预设邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("预设邮件派发失败。");
        }
    }

    public MailOperationResult dispatchPresetToPlayer(String presetId, Player player, String actorName) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
        }

        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure("未找到邮件预设: " + presetId);
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure("邮件预设已禁用: " + preset.id());
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
            plugin.getLogger().warning("派发预设邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("预设邮件派发失败。");
        }
    }

    public MailOperationResult createCdk(String presetId, String rawCode, int maxClaims, Instant expiresAt, String createdBy) {
        MailPresetDefinition preset = preset(presetId);
        if (preset == null) {
            return MailOperationResult.failure("未找到邮件预设: " + presetId);
        }
        if (!preset.enabled()) {
            return MailOperationResult.failure("邮件预设已禁用: " + preset.id());
        }
        if (maxClaims <= 0) {
            return MailOperationResult.failure("maxClaims 必须大于 0。");
        }

        try {
            String code = rawCode == null || rawCode.isBlank() || "auto".equalsIgnoreCase(rawCode)
                ? generateUniqueCdkCode()
                : normalizeCdkCode(rawCode);
            if (code.isBlank()) {
                return MailOperationResult.failure("CDK 格式无效。");
            }
            if (repository.loadCdk(code).isPresent()) {
                return MailOperationResult.failure("CDK 已存在: " + code);
            }

            Instant now = Instant.now();
            repository.saveCdk(new MailCdkDefinition(code, preset.id(), maxClaims, 0, expiresAt, true, safe(createdBy), now, now));
            return MailOperationResult.success("CDK 创建成功: " + code);
        } catch (Exception exception) {
            plugin.getLogger().warning("创建 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure("创建 CDK 失败。");
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
            plugin.getLogger().warning("读取 CDK 失败: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<MailCdkDefinition> listCdks(int page, int pageSize) {
        try {
            return repository.loadCdks(page, pageSize);
        } catch (Exception exception) {
            plugin.getLogger().warning("读取 CDK 列表失败: " + exception.getMessage());
            return List.of();
        }
    }

    public MailOperationResult deleteCdk(String rawCode) {
        String code = normalizeCdkCode(rawCode);
        if (code.isBlank()) {
            return MailOperationResult.failure("CDK 不能为空。");
        }
        try {
            return repository.deleteCdk(code)
                ? MailOperationResult.success("已删除 CDK: " + code)
                : MailOperationResult.failure("CDK 不存在。");
        } catch (Exception exception) {
            plugin.getLogger().warning("删除 CDK 失败: " + exception.getMessage());
            return MailOperationResult.failure("删除 CDK 失败。");
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
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory()) {
            composeSessions.remove(player.getUniqueId());
            if (!session.sent()) {
                returnComposeItems(player, session);
                player.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + "未发送的附件已退回。");
            }
            return;
        }

        AdminEditSession adminSession = adminEditSessions.get(player.getUniqueId());
        if (adminSession != null && event.getInventory() == adminSession.inventory()) {
            adminEditSessions.remove(player.getUniqueId());
            returnAdminItems(player, adminSession);
            player.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + "Admin 编辑已关闭，物品已退回。");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = InventoryViewCompat.getTopInventory(event);

        // Compose container
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session != null && topInventory == session.inventory() && topInventory.getHolder() instanceof ComposeInventoryHolder) {
            int maxAttachments = effectiveAttachmentSlots(topInventory);
            if (event.getRawSlot() < topInventory.getSize() && event.getRawSlot() >= maxAttachments) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlot() >= 0 && event.getRawSlot() < topInventory.getSize()) {
                scheduleComposeQuoteRefresh(player);
            }
            return;
        }

        // Admin container
        AdminEditSession adminSession = adminEditSessions.get(player.getUniqueId());
        if (adminSession != null && topInventory == adminSession.inventory() && topInventory.getHolder() instanceof AdminEditInventoryHolder) {
            if (event.getRawSlot() < topInventory.getSize() && event.getRawSlot() >= ADMIN_ATTACHMENT_SLOTS) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlot() >= 0 && event.getRawSlot() < ADMIN_ATTACHMENT_SLOTS) {
                scheduleAdminAttachmentCountRefresh(player);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = InventoryViewCompat.getTopInventory(event);

        // Compose container
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session != null && topInventory == session.inventory() && topInventory.getHolder() instanceof ComposeInventoryHolder) {
            int maxAttachments = effectiveAttachmentSlots(topInventory);
            boolean touchesTop = false;
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot == null || rawSlot < 0 || rawSlot >= topInventory.getSize()) continue;
                touchesTop = true;
                if (rawSlot >= maxAttachments) { event.setCancelled(true); return; }
            }
            if (touchesTop) scheduleComposeQuoteRefresh(player);
            return;
        }

        // Admin container
        AdminEditSession adminSession = adminEditSessions.get(player.getUniqueId());
        if (adminSession != null && topInventory == adminSession.inventory() && topInventory.getHolder() instanceof AdminEditInventoryHolder) {
            boolean touchesTop = false;
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot == null || rawSlot < 0 || rawSlot >= topInventory.getSize()) continue;
                touchesTop = true;
                if (rawSlot >= ADMIN_ATTACHMENT_SLOTS) { event.setCancelled(true); return; }
            }
            if (touchesTop) scheduleAdminAttachmentCountRefresh(player);
        }
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !CLIENT_PACKET_ID.equalsIgnoreCase(packetId)) {
            return false;
        }

        String action = data == null || data.isEmpty() ? "" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (packetGuard != null && !packetGuard.allow(player, "mail", action, configuration.debug())) {
            return true;
        }
        switch (action) {
            case "open" -> {
                if (composeSessions.containsKey(player.getUniqueId())) {
                    player.closeInventory();
                }
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
                    sendPlayerResult(player, MailOperationResult.failure("写信参数不完整。"));
                } else {
                    sendPlayerResult(player, handleComposeSend(player, data));
                }
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
                    sendPlayerResult(player, MailOperationResult.failure("请输入 CDK。"));
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
                    condStrings.add(cond.serialize().replace('\t', ':'));
                }
            }
            payload.put("claimConditions", condStrings);
        }
        bridge.sendPacket(player, adminUiId, "preset-detail", payload);
    }

    private void handleAdminPresetSave(Player player, List<String> data) {
        // data: [action, id, enabled, displayName, subject, body, expiresAfterDays, currencyAttachments, claimCommands]
        if (data == null || data.size() < 7) {
            sendAdminResult(player, MailOperationResult.failure("保存参数不完整。"));
            return;
        }
        try {
            String id = safe(data.get(1)).trim().toLowerCase(Locale.ROOT);
            if (id.isBlank()) {
                sendAdminResult(player, MailOperationResult.failure("预设 ID 不能为空。"));
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

            // 从容器读取物品附件
            AdminEditSession adminSession = adminEditSessions.get(player.getUniqueId());
            if (adminSession != null && adminSession.inventory() != null) {
                List<ItemStack> containerItems = collectAdminItems(adminSession.inventory());
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
                // 保存成功后退回物品并清空容器
                if (adminSession != null) {
                    returnAdminItems(player, adminSession);
                    adminSession.inventory().clear();
                }
                sendAdminPresetList(player);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Admin 预设保存处理异常: " + exception.getMessage());
            sendAdminResult(player, MailOperationResult.failure("保存处理异常: " + exception.getMessage()));
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
        int itemAttachmentCount
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
        return new MailSendQuote(
            true,
            "",
            normalizeCurrencyId(configuration.feeCurrency()),
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
        presets.putAll(MailPresetLoader.loadPresets(directory, plugin.getLogger(), configuration.playerSend().maxAttachments()));
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
                    plugin.getLogger().warning("邮件预设 CDK 重复定义，后续重复项已跳过: " + cdk.code());
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
            plugin.getLogger().info("已同步邮件预设 CDK: " + synced + " 个。");
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
                plugin.getLogger().warning("邮件货币桥接不可用: " + definition.id() + " -> " + currencyBridge.unavailableReason());
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
            plugin.getLogger().warning("更新邮箱玩家档案失败: " + exception.getMessage());
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
            plugin.getLogger().warning("执行邮件清理失败: " + exception.getMessage());
        }
    }

    private void handleCrossServerMessage(String message) {
        if (configuration.debug()) {
            plugin.getLogger().info("收到邮件跨服消息: " + message);
        }
        if (message == null || message.isBlank() || !message.startsWith("refresh:")) {
            return;
        }

        String rawUuid = message.substring("refresh:".length()).trim();
        try {
            UUID playerUuid = UUID.fromString(rawUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    refreshPlayerViews(player, false);
                }
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void returnComposeItems(Player player, ComposeSession session) {
        if (player == null || session == null || session.inventory() == null) {
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : session.inventory().getContents()) {
            if (!isRealItem(itemStack)) {
                continue;
            }
            HashMap<Integer, ItemStack> leftovers = playerInventory.addItem(itemStack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        session.inventory().clear();
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
            return MailOperationResult.success("邮件发送成功。");
        } catch (Exception exception) {
            plugin.getLogger().warning("发送邮件失败: " + exception.getMessage());
            if (configuration.debug() && logContent != null && !logContent.isBlank()) {
                plugin.getLogger().warning("发送邮件失败的上下文: " + logContent);
            }
            return MailOperationResult.failure("邮件发送失败。");
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
            int maxLineWidth = ui.maxLineWidth();
            int cardWidth;
            int cardHeight;
            List<String> lines;
            if (textWidth <= maxLineWidth) {
                // 单行：宽度跟随文字
                cardWidth = Math.max(ui.minWidth(), ui.textOffsetX() + textWidth + ui.padRight());
                cardHeight = ui.baseHeight();
                lines = List.of(subject);
            } else {
                // 多行：固定宽度，换行扩高
                cardWidth = Math.max(ui.minWidth(), ui.textOffsetX() + maxLineWidth + ui.padRight());
                lines = wrapText(subject, maxLineWidth, ui.charWidthFull(), ui.charWidthHalf());
                cardHeight = ui.baseHeight() + (lines.size() - 1) * ui.lineHeight();
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("senderName", message.senderName() == null ? "系统" : message.senderName());
            payload.put("sourceType", message.sourceType() == null ? "system" : message.sourceType().name().toLowerCase(Locale.ROOT));
            payload.put("hasAttachments", String.valueOf(message.hasAttachments()));
            for (int i = 0; i < lines.size(); i++) {
                payload.put("msg" + i, lines.get(i));
            }
            payload.put("cardWidth", String.valueOf(cardWidth));
            payload.put("cardHeight", String.valueOf(cardHeight));
            bridge.sendChatCard(recipient, cardId, payload);
        } catch (Exception exception) {
            if (configuration.debug()) {
                plugin.getLogger().warning("发送邮件通知卡片失败: " + exception.getMessage());
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
            bridge.sendPacket(
                player,
                inboxUiId,
                initPacket ? "init" : "update",
                MailInboxPacketFactory.build(page, selectedMailId, stats, query.filter(), claimableCount)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("刷新邮箱界面失败: " + exception.getMessage());
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
            bridge.sendPacket(player, logsUiId, initPacket ? "init" : "update", MailLogsPacketFactory.build(page));
        } catch (Exception exception) {
            plugin.getLogger().warning("刷新日志界面失败: " + exception.getMessage());
        }
    }

    private MailOperationResult openLogs(Player player) {
        if (player == null || !player.isOnline()) {
            return MailOperationResult.failure("目标玩家不在线。");
        }
        try {
            touchProfile(player, false);
            logPages.putIfAbsent(player.getUniqueId(), 1);
            logViewers.add(player.getUniqueId());
            inboxViewers.remove(player.getUniqueId());
            bridge.openUi(player, logsUiId);
            refreshLogs(player, true);
            return MailOperationResult.success("已打开邮件日志。");
        } catch (Exception exception) {
            plugin.getLogger().warning("打开邮件日志失败: " + exception.getMessage());
            return MailOperationResult.failure("打开邮件日志失败。");
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
            plugin.getLogger().warning("读取邮件详情失败: " + exception.getMessage());
        }
        refreshInbox(player, false);
    }

    private MailOperationResult handleComposeSend(Player player, List<String> data) {
        ComposeSession session = composeSessions.get(player.getUniqueId());
        if (session == null) {
            return MailOperationResult.failure("当前没有写信会话。");
        }
        if (!session.sessionId().toString().equalsIgnoreCase(safe(data.get(1)))) {
            return MailOperationResult.failure("写信会话已失效，请重新打开。");
        }

        if (!configuration.playerSend().enabled()) {
            MailOperationResult result = MailOperationResult.failure("当前服务器已关闭玩家寄信。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }
        if (configuration.playerSend().requirePermission() && !player.hasPermission("arcartxsuite.mail.send")) {
            MailOperationResult result = MailOperationResult.failure("你没有寄信权限。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }

        String recipientInput = normalizeRecipientInput(data.get(2));
        String subject = crop(data.get(3), configuration.playerSend().subjectMaxLength());
        String body = crop(data.get(4), configuration.playerSend().bodyMaxLength());
        if (recipientInput.isBlank()) {
            MailOperationResult result = MailOperationResult.failure("收件人不能为空。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }
        if (subject.isBlank()) {
            MailOperationResult result = MailOperationResult.failure("邮件标题不能为空。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }
        if (containsBlockedText(subject) || containsBlockedText(body)) {
            MailOperationResult result = MailOperationResult.failure("邮件内容包含敏感词或被屏蔽的正则。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }
        int maxAttachmentSlots = effectiveAttachmentSlots(session.inventory());
        if (hasItemsOutsideAttachmentSlots(session.inventory(), maxAttachmentSlots)) {
            MailOperationResult result = MailOperationResult.failure("仅允许在前 " + maxAttachmentSlots + " 个槽位放置附件。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }

        try {
            RecipientResolution recipient = resolveRecipient(recipientInput);
            if (recipient == null) {
                MailOperationResult result = MailOperationResult.failure("未找到玩家: " + recipientInput);
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }
            if (!configuration.playerSend().allowSelfSend() && player.getUniqueId().equals(recipient.playerUuid())) {
                MailOperationResult result = MailOperationResult.failure("当前配置不允许给自己寄信。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }
            boolean recipientOnline = isPlayerOnline(recipient.playerUuid());
            if (!recipientOnline && !configuration.playerSend().allowOfflineSend()) {
                MailOperationResult result = MailOperationResult.failure("当前配置不允许给离线玩家寄信。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }

            Optional<MailPlayerProfile> senderProfile = repository.loadPlayerProfile(player.getUniqueId());
            if (senderProfile.isPresent() && senderProfile.get().lastSendAt() != null) {
                long cooldownSeconds = configuration.playerSend().cooldownSeconds();
                Instant nextAvailableAt = senderProfile.get().lastSendAt().plusSeconds(cooldownSeconds);
                if (cooldownSeconds > 0 && nextAvailableAt.isAfter(Instant.now())) {
                    MailOperationResult result = MailOperationResult.failure(
                        "寄信冷却中，请在 " + TIME_FORMATTER.format(nextAvailableAt) + " 后再试。"
                    );
                    pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                    return result;
                }
            }

            List<ItemStack> attachmentItems = collectComposeItems(session.inventory());
            MailOperationResult attachmentValidation = validateAttachmentItems(attachmentItems);
            if (!attachmentValidation.success()) {
                pushComposeQuote(player, MailSendQuote.failure(attachmentValidation.message(), configuration.playerSend().feeCurrency()));
                return attachmentValidation;
            }

            BigDecimal vaultAmount = parseCurrencyAmount(data.get(5));
            if (vaultAmount == null) {
                MailOperationResult result = MailOperationResult.failure("金币附件金额格式无效。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }
            if (vaultAmount.compareTo(BigDecimal.ZERO) > 0 && !configuration.playerSend().allowVaultAttachment()) {
                MailOperationResult result = MailOperationResult.failure("当前配置未启用金币附件。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }

            Map<String, BigDecimal> attachmentAmounts = new LinkedHashMap<>();
            if (vaultAmount.compareTo(BigDecimal.ZERO) > 0) {
                attachmentAmounts.put("money", vaultAmount);
            }
            MailSendQuote quote = calculateComposeQuote(configuration.playerSend(), attachmentAmounts, attachmentItems.size());
            pushComposeQuote(player, quote);

            MailOperationResult paymentValidation = validateSenderPayment(player, quote);
            if (!paymentValidation.success()) {
                pushComposeQuote(player, MailSendQuote.failure(paymentValidation.message(), configuration.playerSend().feeCurrency()));
                return paymentValidation;
            }

            List<MailAttachment> attachments;
            try {
                attachments = buildComposeAttachments(attachmentItems, attachmentAmounts);
            } catch (IOException exception) {
                plugin.getLogger().warning("序列化玩家邮件附件失败: " + exception.getMessage());
                MailOperationResult result = MailOperationResult.failure("附件物品保存失败，请取回后重试。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
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
                MailOperationResult result = MailOperationResult.failure("扣费失败，请稍后重试。");
                pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
                return result;
            }

            MailOperationResult sendResult = sendMailInternal(recipient.playerUuid(), "compose-send", mail, true);
            if (!sendResult.success()) {
                refundSender(player, charged);
                pushComposeQuote(player, MailSendQuote.failure(sendResult.message(), configuration.playerSend().feeCurrency()));
                return sendResult;
            }

            touchProfile(player, true);
            appendLog(player.getUniqueId(), "send", "寄给 " + recipient.lastKnownName() + " 的邮件: " + subject);
            session.sent = true;
            session.inventory().clear();
            composeSessions.remove(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    openInbox(player);
                }
            });
            return MailOperationResult.success("邮件已发送给 " + recipient.lastKnownName() + "。");
        } catch (Exception exception) {
            plugin.getLogger().warning("发送玩家邮件失败: " + exception.getMessage());
            MailOperationResult result = MailOperationResult.failure("发送邮件失败。");
            pushComposeQuote(player, MailSendQuote.failure(result.message(), configuration.playerSend().feeCurrency()));
            return result;
        }
    }

    private MailOperationResult claimMail(Player player, long mailId, boolean refresh) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure("邮件不存在。");
            }

            MailMessage mail = optionalMail.get();
            if (!mail.claimable()) {
                return MailOperationResult.failure("该邮件当前不可领取。");
            }
            if (!checkClaimConditions(player, mail.claimConditions())) {
                return MailOperationResult.failure("你尚未满足该邮件的领取条件。");
            }

            List<ItemStack> itemRewards;
            try {
                itemRewards = deserializeItemRewards(mail.attachments());
            } catch (IOException exception) {
                plugin.getLogger().warning("读取邮件物品附件失败: " + exception.getMessage());
                return MailOperationResult.failure("附件物品读取失败，请联系管理员。");
            }
            MailOperationResult currencyValidation = validateClaimCurrencies(mail.attachments());
            if (!currencyValidation.success()) {
                return currencyValidation;
            }

            Instant now = Instant.now();
            MailStatus previousStatus = mail.status();
            if (!repository.tryClaimMail(player.getUniqueId(), mailId, now)) {
                return MailOperationResult.failure("该邮件已被领取或当前不可领取。");
            }

            deliverItemRewards(player, itemRewards);
            for (String command : mail.claimCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayerTokens(command, player));
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
            return MailOperationResult.success("邮件已领取。");
        } catch (Exception exception) {
            plugin.getLogger().warning("领取邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("领取邮件失败。");
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
            plugin.getLogger().warning("回滚邮件领取状态失败: mailId=" + mail.id() + " | " + exception.getMessage());
        }
    }

    private MailOperationResult deleteMail(Player player, long mailId, boolean refresh) {
        try {
            Optional<MailMessage> optionalMail = repository.loadMail(player.getUniqueId(), mailId);
            if (optionalMail.isEmpty()) {
                return MailOperationResult.failure("邮件不存在。");
            }

            MailMessage mail = optionalMail.get();
            if (!canDeleteMail(configuration.retention().allowDeleteWithUnclaimedAttachments(), mail)) {
                return MailOperationResult.failure("该邮件仍有未领取内容，当前配置不允许直接删除。");
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
            return MailOperationResult.success("邮件已删除。");
        } catch (Exception exception) {
            plugin.getLogger().warning("删除邮件失败: " + exception.getMessage());
            return MailOperationResult.failure("删除邮件失败。");
        }
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
            plugin.getLogger().warning("注册邮件 UI 失败: " + result.message());
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
            return MailOperationResult.failure("预设邮件参数无效。");
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
                return MailOperationResult.failure("附件包含被禁止的物品类型: " + itemStack.getType().name());
            }
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (matchesBlockedLore(line)) {
                        return MailOperationResult.failure("附件 Lore 命中了屏蔽规则。");
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

    private List<ItemStack> collectComposeItems(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        int maxAttachments = effectiveAttachmentSlots(inventory);
        for (int slot = 0; slot < maxAttachments; slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (isRealItem(itemStack)) {
                result.add(itemStack.clone());
            }
        }
        return List.copyOf(result);
    }

    private boolean hasItemsOutsideAttachmentSlots(Inventory inventory, int allowedSlots) {
        for (int slot = Math.max(0, allowedSlots); slot < inventory.getSize(); slot++) {
            if (isRealItem(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private void pushComposeQuote(Player player, MailSendQuote quote) {
        if (player != null && player.isOnline()) {
            ComposeSession session = composeSessions.get(player.getUniqueId());
            Inventory inventory = session == null ? null : session.inventory();
            int maxAttachments = effectiveAttachmentSlots(inventory);
            int attachmentCount = inventory == null ? 0 : collectComposeItems(inventory).size();
            bridge.sendPacket(
                player,
                composeUiId,
                "update",
                MailComposePacketFactory.buildQuote(configuration, currencyBridgeManager, quote, maxAttachments, attachmentCount)
            );
        }
    }

    private void scheduleComposeQuoteRefresh(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }
            ComposeSession session = composeSessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }
            int attachmentCount = collectComposeItems(session.inventory()).size();
            pushComposeQuote(player, calculateComposeQuote(configuration.playerSend(), Map.of(), attachmentCount));
        });
    }

    private int effectiveAttachmentSlots(Inventory inventory) {
        int inventorySize = inventory == null ? configuration.playerSend().maxAttachments() : inventory.getSize();
        return effectiveAttachmentSlots(configuration.playerSend().maxAttachments(), inventorySize);
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
                return MailOperationResult.failure("货币桥接不可用: " + entry.getKey());
            }
            if (currencyBridge.balance(player).compareTo(amount) < 0) {
                return MailOperationResult.failure("余额不足: " + entry.getKey());
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
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
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
                return MailOperationResult.failure("附件货币暂不可用: " + attachment.normalizedCurrencyId());
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
                return MailOperationResult.failure("附件货币暂不可用: " + attachment.normalizedCurrencyId());
            }
            CurrencyTransactionResult result = currencyBridge.deposit(player, BigDecimal.valueOf(attachment.amount()));
            if (!result.success()) {
                return MailOperationResult.failure("领取货币附件失败: " + attachment.normalizedCurrencyId());
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
            plugin.getLogger().warning("写入邮件日志失败: " + exception.getMessage());
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
        ItemMeta meta = itemStack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : itemStack.getType().name();
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
        return safe(command)
            .replace("<player>", player.getName())
            .replace("{player}", player.getName())
            .replace("%player%", player.getName());
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
        private final Inventory inventory;
        private boolean sent;

        private ComposeSession(UUID sessionId, UUID playerUuid, Inventory inventory, boolean sent) {
            this.sessionId = sessionId;
            this.playerUuid = playerUuid;
            this.inventory = inventory;
            this.sent = sent;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public UUID playerUuid() {
            return playerUuid;
        }

        public Inventory inventory() {
            return inventory;
        }

        public boolean sent() {
            return sent;
        }
    }

    private static final class ComposeInventoryHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class AdminEditSession {
        private final UUID playerUuid;
        private final Inventory inventory;
        private String editingPresetId;

        private AdminEditSession(UUID playerUuid, Inventory inventory, String editingPresetId) {
            this.playerUuid = playerUuid;
            this.inventory = inventory;
            this.editingPresetId = editingPresetId;
        }

        public UUID playerUuid() {
            return playerUuid;
        }

        public Inventory inventory() {
            return inventory;
        }

        public String editingPresetId() {
            return editingPresetId;
        }
    }

    private static final class AdminEditInventoryHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ─── Admin Container Helpers ─────────────────────────────────

    private void populateAdminContainer(Player player, String presetId) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;

        // 先归还旧物品并清空
        returnAdminItems(player, session);
        session.inventory.clear();
        session.editingPresetId = safe(presetId).trim().toLowerCase(Locale.ROOT);

        MailPresetDefinition def = getPreset(presetId);
        if (def == null || def.attachments() == null) return;

        int slot = 0;
        for (MailAttachment att : def.attachments()) {
            if (!att.isItem() || slot >= ADMIN_ATTACHMENT_SLOTS) break;
            try {
                ItemStack item = MailItemSerializer.deserialize(att.itemData());
                if (isRealItem(item)) {
                    markAsTemplateItem(item);
                    session.inventory.setItem(slot++, item);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Admin UI 反序列化物品附件失败: " + exception.getMessage());
            }
        }

        scheduleAdminAttachmentCountRefresh(player);
    }

    private void clearAdminContainer(Player player) {
        AdminEditSession session = adminEditSessions.get(player.getUniqueId());
        if (session == null) return;
        returnAdminItems(player, session);
        session.inventory.clear();
        session.editingPresetId = null;
        scheduleAdminAttachmentCountRefresh(player);
    }

    private void returnAdminItems(Player player, AdminEditSession session) {
        if (player == null || session == null || session.inventory() == null) return;
        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : session.inventory().getContents()) {
            if (!isRealItem(itemStack)) continue;
            if (isTemplateItem(itemStack)) continue;
            HashMap<Integer, ItemStack> leftovers = playerInventory.addItem(itemStack.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private List<ItemStack> collectAdminItems(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < ADMIN_ATTACHMENT_SLOTS && slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (isRealItem(itemStack)) {
                result.add(itemStack.clone());
            }
        }
        return List.copyOf(result);
    }

    private void scheduleAdminAttachmentCountRefresh(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) return;
            AdminEditSession session = adminEditSessions.get(player.getUniqueId());
            if (session == null) return;
            int count = collectAdminItems(session.inventory()).size();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemAttachmentCount", count);
            bridge.sendPacket(player, adminUiId, "attachment-count", payload);
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

