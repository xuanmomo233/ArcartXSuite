package xuanmo.arcartxsuite.title.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permissible;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.title.TitleDurationParser.TitleDurationSpec;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleGroupDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.listener.PlayerTitleListener;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;
import xuanmo.arcartxsuite.title.storage.TitleRepository;

public class TitleService {

    public static final String CLIENT_PACKET_ID = "AXS_TITLE_MENU";
    private static final String TITLE_MENU_RESOURCE_PATH = "arcartx/ui/title_menu.yml";
    private static final String TITLE_MENU_FILE_PATH = "ui/title_menu.yml";
    private static final int MAX_PENDING_DATABASE_TASKS = 256;

    /**
     * UI 资源导出函数。输入：(resourcePath, relativeUiPath, overwrite) -> File
     */
    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    private final JavaPlugin plugin;
    private final TitleModuleConfiguration configuration;
    private final Set<String> publicGroups;
    private final List<TitleGroupDefinition> restrictedGroups;
    private final TitleRepository repository;
    private final PacketBridgeAPI bridge;
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();
    private final PacketGuardAPI packetGuard;
    private final Supplier<TabRefreshable> tabRefreshableProvider;
    private final UiResourceExporter uiResourceExporter;
    private final Clock clock;
    private final Logger logger;
    private final TitleAttributePlusService attributePlusService;
    private final TitleMythicLibService mythicLibService;
    private final TitleCraneAttributeService craneAttributeService;
    private final TitleSymphonyService symphonyService;
    private final TitlePotionEffectService potionEffectService;
    private final TitleModAttributeService modAttributeService;
    private final TitleOverheadService overheadService;
    private final MessageProvider messages;
    private final CompletableFuture<Void> databaseInitialization = new CompletableFuture<>();
    private final ModuleExecutor databaseExecutor = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_PENDING_DATABASE_TASKS), new TitleThreadFactory());
    private final Semaphore writeSlots = new Semaphore(MAX_PENDING_DATABASE_TASKS);
    private final Object databaseAdmission = new Object();
    private volatile boolean databaseWritesEnabled = true;
    private final Set<PendingTitleWrite> writeReceipts = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> databaseStopped = databaseExecutor.stopped()
        .thenRun(this::finishRetiringWriteReceipts).toCompletableFuture();
    private final ConcurrentMap<UUID, PlayerWriteBarrier> playerWrites = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerTitleState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TitleStateView> stateViews = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<PlayerTitleState>> loadingStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> selectedTitleIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> pendingPlayerRefresh = new ConcurrentHashMap<>();
    private final AtomicBoolean maintenanceQueued = new AtomicBoolean();

    private Listener playerListener;
    private BukkitTask expirationTask;
    private String runtimeUiId;
    private String registeredUiId;

    private static final class PlayerWriteBarrier {
        int pending;
        final CompletableFuture<Void> drained = new CompletableFuture<>();
    }

    private final class PendingTitleWrite {
        final UUID playerUuid;
        final CompletableFuture<TitleOperationResult> future = new CompletableFuture<>();
        PlayerWriteBarrier barrier;
        volatile TitleOperationResult committed;
        boolean completed;

        PendingTitleWrite(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        void complete(TitleOperationResult result) {
            CompletableFuture<Void> drained = null;
            synchronized (databaseAdmission) {
                if (completed) return;
                completed = true;
                writeReceipts.remove(this);
                writeSlots.release();
                if (barrier != null && --barrier.pending == 0) {
                    playerWrites.remove(playerUuid, barrier);
                    drained = barrier.drained;
                }
            }
            if (drained != null) drained.complete(null);
            future.complete(result);
        }
    }

    public TitleService(
        JavaPlugin plugin,
        Logger logger,
        TitleModuleConfiguration configuration,
        TitleRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        Supplier<TabRefreshable> tabRefreshableProvider,
        UiResourceExporter uiResourceExporter,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        WorldTextureBridgeAPI worldTextureBridge
    ) {
        this(plugin, logger, configuration, repository, bridge, packetGuard, tabRefreshableProvider, uiResourceExporter, Clock.systemUTC(), attributeBridge, worldTextureBridge, null, null, null);
    }

    public TitleService(
        JavaPlugin plugin,
        Logger logger,
        TitleModuleConfiguration configuration,
        TitleRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        Supplier<TabRefreshable> tabRefreshableProvider,
        UiResourceExporter uiResourceExporter,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        WorldTextureBridgeAPI worldTextureBridge,
        MessageProvider messages
    ) {
        this(plugin, logger, configuration, repository, bridge, packetGuard, tabRefreshableProvider, uiResourceExporter, Clock.systemUTC(), attributeBridge, worldTextureBridge, null, null, messages);
    }

    public TitleService(
        JavaPlugin plugin,
        Logger logger,
        TitleModuleConfiguration configuration,
        TitleRepository repository,
        PacketBridgeAPI bridge,
        PacketGuardAPI packetGuard,
        Supplier<TabRefreshable> tabRefreshableProvider,
        UiResourceExporter uiResourceExporter,
        Clock clock,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        WorldTextureBridgeAPI worldTextureBridge,
        xuanmo.arcartxsuite.api.potion.PotionAuraService potionAura,
        xuanmo.arcartxsuite.api.modattr.ModAttributeService modAttributes,
        MessageProvider messages
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        var openGroups = new HashSet<String>();
        for (var group : configuration.groups().values()) {
            if (group.permission().isBlank()) openGroups.add(group.id());
        }
        this.publicGroups = Set.copyOf(openGroups);
        this.restrictedGroups = configuration.groups().values().stream()
            .filter(group -> !group.permission().isBlank()).toList();
        this.repository = repository;
        this.bridge = bridge;
        this.packetGuard = packetGuard;
        this.tabRefreshableProvider = tabRefreshableProvider;
        this.uiResourceExporter = uiResourceExporter;
        this.clock = clock;
        this.logger = logger;
        this.messages = messages;
        this.attributePlusService = new TitleAttributePlusService(plugin, configuration.attributePlus(), attributeBridge.attributePlus());
        this.mythicLibService = new TitleMythicLibService(plugin, this.logger, configuration.mythicLib(), attributeBridge.mythicLib());
        this.craneAttributeService = new TitleCraneAttributeService(plugin, configuration.craneAttribute(), attributeBridge.craneAttribute());
        this.symphonyService = new TitleSymphonyService(plugin, configuration.symphony(), attributeBridge.symphony());
        this.potionEffectService = new TitlePotionEffectService(potionAura);
        this.modAttributeService = new TitleModAttributeService(modAttributes);
        this.overheadService = new TitleOverheadService(worldTextureBridge, logger);
    }

    /**
     * 启动称号服务。
     * <p>
     * 初始化数据库、注册 UI、绑定玩家监听器、预加载在线玩家数据，
     * 并启动过期称号清理定时任务。
     */
    public void start() throws SQLException, IOException {
        queueMaintenance("initialize", () -> {});
        mythicLibService.start();
        runtimeUiId = configuration.ui().uiId();
        registeredUiId = null;

        File uiFile = exportUiFile();
        if (configuration.ui().registerUiOnEnable()) {
            xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult registration = bridge.registerOrReloadUi(configuration.ui().uiId(), uiFile);
            if (!registration.success()) {
                throw new IOException("注册称号 UI 失败: " + registration.message());
            }
            runtimeUiId = registration.runtimeUiId();
            registeredUiId = registration.registeredUiId();
        }

        playerListener = new PlayerTitleListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            preloadPlayer(player);
        }

        expirationTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::tickExpirationCleanup,
            configuration.expirationCleanupIntervalTicks(),
            configuration.expirationCleanupIntervalTicks()
        );
    }

    /**
     * 关闭称号服务。
     * <p>
     * 取消定时任务、注销监听器、卸载 UI、清理所有在线玩家的外部属性加成，
     * 并优雅关闭数据库线程池与连接。
     */
    public void shutdown() {
        synchronized (databaseAdmission) {
            if (!databaseWritesEnabled) return;
            databaseWritesEnabled = false;
        }
        databaseExecutor.shutdown();
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }
        if (playerListener != null) {
            HandlerList.unregisterAll(playerListener);
            playerListener = null;
        }
        if (registeredUiId != null) {
            bridge.unregisterUi(registeredUiId);
            registeredUiId = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            attributePlusService.clear(player);
            mythicLibService.clear(player);
            craneAttributeService.clear(player);
            symphonyService.clear(player);
            potionEffectService.clear(player);
            modAttributeService.clear(player);
            overheadService.clear(player);
        }
        mythicLibService.shutdown();
        potionEffectService.shutdown();
        modAttributeService.shutdown();
        overheadService.shutdown();

        for (var loading : loadingStates.values()) {
            loading.completeExceptionally(new IllegalStateException(message("common.service-down")));
        }

        states.clear();
        stateViews.clear();
        loadingStates.clear();
        selectedTitleIds.clear();
        pendingPlayerRefresh.clear();
        tokenGuard.clearAll();
    }

    public CompletionStage<Void> databaseStopped() {
        return databaseStopped.copy();
    }

    private void finishRetiringWriteReceipts() {
        // Workers have exited. A committed write may still have a UI callback queued for the retired instance.
        var unconfirmed = TitleOperationResult.failure(message("common.service-down"));
        for (var pending : writeReceipts) {
            pending.complete(pending.committed == null ? unconfirmed : pending.committed);
        }
    }

    private void ensureDatabaseInitialized() throws SQLException {
        if (!databaseInitialization.isDone()) {
            try {
                repository.initialize();
                databaseInitialization.complete(null);
            } catch (SQLException | RuntimeException failure) {
                databaseInitialization.completeExceptionally(failure);
                throw failure;
            }
        }
        try {
            databaseInitialization.join();
        } catch (CompletionException failure) {
            throw new SQLException("称号数据库初始化未完成", failure.getCause());
        }
    }

    public void preloadPlayer(Player player) {
        if (!databaseWritesEnabled || player == null) {
            return;
        }
        withLoadedState(player, state -> {
            syncExternalAttributes(player);
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isCurrentPlayer(player)) {
                        return;
                    }
                    syncExternalAttributes(player);
                },
                20L
            );
        }, ignored -> {});
    }

    public void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        attributePlusService.clear(player);
        mythicLibService.clear(player);
        craneAttributeService.clear(player);
        symphonyService.clear(player);
        potionEffectService.clear(player);
        modAttributeService.clear(player);
        overheadService.clear(player);
        UUID playerUuid = player.getUniqueId();
        loadingStates.remove(playerUuid);
        states.remove(playerUuid);
        stateViews.remove(playerUuid);
        selectedTitleIds.remove(playerUuid);
        pendingPlayerRefresh.remove(playerUuid);
        tokenGuard.clear(playerUuid);
    }

    /**
     * 为指定玩家打开称号管理菜单。
     * <p>
     * 异步加载玩家数据后，通过 {@link PacketBridgeAPI} 发送 UI 初始化包。
     *
     * @param player 目标玩家
     */
    public void openMenu(Player player) {
        openMenu(player, true);
    }

    /** Opens the menu when explicitly requested by a title administrator. */
    public void openMenuForAdmin(Player player) {
        openMenu(player, false);
    }

    private void openMenu(Player player, boolean requireUsePermission) {
        if (player == null || !player.isOnline()) {
            return;
        }
        withAuthorizedState(
            player,
            (state, admission) -> {
                selectedTitleIds.put(player.getUniqueId(), ensureSelectedTitle(player.getUniqueId(), state));
                bridge.openUi(player, runtimeUiId);
                sendMenuPacket(player, "init");
            },
            exception -> player.sendMessage(prefix() + message("player.data-load-failed")),
            requireUsePermission
        );
    }

    /**
     * 处理来自客户端的称号菜单交互包。
     * <p>
     * 支持的动作：open、select、equip、unequip_group、unequip_all、hide、unhide、refresh。
     *
     * @param player   发包玩家
     * @param packetId 包 ID（仅处理 {@value #CLIENT_PACKET_ID}）
     * @param data     包数据，首元素为动作名
     * @return true 表示已消费该包
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !CLIENT_PACKET_ID.equalsIgnoreCase(packetId)) {
            return false;
        }
        if (!player.hasPermission("arcartxsuite.title.use")) return true;

        String action = data == null || data.isEmpty() ? "refresh" : data.get(0).trim().toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查，避免双重计数导致限流阈值减半。
        // 写操作需要有效动作令牌：data[1] 为 token，称号参数从 data[2] 起
        boolean writeAction = switch (action) {
            case "equip", "unequip_group", "unequip_all", "hide", "unhide", "set_display" -> true;
            default -> false;
        };
        if (writeAction && !beginActionToken(player, data)) return true;
        try {
            switch (action) {
                case "open" -> openMenu(player);
                case "select" -> {
                    if (data.size() > 1) {
                        selectTitle(player, data.get(1));
                    }
                }
                case "equip" -> equipTitle(player, data.size() > 2 ? data.get(2) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""));
                case "unequip_group" -> unequipGroup(player, data.size() > 2 ? data.get(2) : selectedGroupId(player));
                case "unequip_all" -> unequipAll(player);
                case "hide" -> hideTitle(player, data.size() > 2 ? data.get(2) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""), true);
                case "unhide" -> unhideTitle(player, data.size() > 2 ? data.get(2) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""), true);
                case "set_display" -> selectDisplayTitle(player, data.size() > 2 ? data.get(2) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""));
                case "refresh" -> refreshMenu(player);
                default -> refreshMenu(player);
            }
        } finally {
            if (writeAction) tokenGuard.finish(player.getUniqueId());
        }
        return true;
    }

    /**
     * 校验写操作令牌：要求称号菜单已打开、data[1] 与当前签发 token 一致、且玩家无在途写操作。
     */
    private boolean beginActionToken(Player player, List<String> data) {
        String supplied = data != null && data.size() > 1 && data.get(1) != null ? data.get(1).trim() : "";
        UUID playerUuid = player.getUniqueId();
        if (runtimeUiId == null || bridge == null || !bridge.isUiOpen(player, runtimeUiId)
            || playerWrites.containsKey(playerUuid) || !tokenGuard.begin(playerUuid, runtimeUiId, supplied)) return false;
        tokenGuard.issue(playerUuid, runtimeUiId);
        return true;
    }

    /**
     * 为玩家装备指定称号（若已拥有且未过期）。
     * <p>
     * 装备后会同步外部属性、刷新菜单，并触发全局 Tab 刷新。
     *
     * @param player  目标玩家
     * @param titleId 称号 ID
     */
    public void equipTitle(Player player, String titleId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withAuthorizedState(
            player,
            (state, admission) -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    player.sendMessage(prefix() + message("player.title-not-found", titleId));
                    return;
                }
                if (!hasGroupAccess(player, definition.groupId())) {
                    player.sendMessage(prefix() + message("player.group-no-permission"));
                    return;
                }
                if (!state.hasOwnedTitle(normalizedTitleId)) {
                    player.sendMessage(prefix() + message(
                        "player.not-owned",
                        ChatColor.translateAlternateColorCodes('&', definition.displayName())
                    ));
                    return;
                }
                submitPlayerStateChange(player, admission, "title-equip",
                    now -> repository.setEquippedTitle(player.getUniqueId(), definition.groupId(), normalizedTitleId, now),
                    saved -> {
                        String display = ChatColor.translateAlternateColorCodes('&', definition.displayName());
                        if (!normalizedTitleId.equals(saved.equippedTitleIdsByGroup().get(definition.groupId()))) {
                            player.sendMessage(prefix() + message("player.not-owned", display));
                            return;
                        }
                        selectedTitleIds.put(player.getUniqueId(), normalizedTitleId);
                        player.sendMessage(prefix() + message("player.equipped", definition.groupId(), display));
                    }, true);
            },
            exception -> player.sendMessage(prefix() + message("player.data-load-failed"))
        );
    }

    /**
     * 卸下玩家指定分组中已装备的称号。
     * <p>
     * 卸下后会同步外部属性、刷新菜单，并触发全局 Tab 刷新。
     *
     * @param player  目标玩家
     * @param groupId 分组 ID
     */
    public void unequipGroup(Player player, String groupId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedGroupId = normalizeTitleId(groupId);
        withAuthorizedState(
            player,
            (state, admission) -> {
                submitPlayerStateChange(player, admission, "title-unequip",
                    now -> repository.setEquippedTitle(player.getUniqueId(), normalizedGroupId, "", now),
                    saved -> player.sendMessage(prefix() + message("player.unequipped-group", normalizedGroupId)), true);
            },
            exception -> player.sendMessage(prefix() + message("player.data-load-failed"))
        );
    }

    /**
     * 卸下玩家所有分组中已装备的称号。
     * <p>
     * 卸下后会同步外部属性、刷新菜单，并触发全局 Tab 刷新。
     *
     * @param player 目标玩家
     */
    public void unequipAll(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        withAuthorizedState(
            player,
            (state, admission) -> {
                submitPlayerStateChange(player, admission, "title-unequip-all",
                    now -> repository.clearEquippedTitles(player.getUniqueId()),
                    saved -> player.sendMessage(prefix() + message("player.unequipped-all")), true);
            },
            exception -> player.sendMessage(prefix() + message("player.data-load-failed"))
        );
    }

    public void hideTitle(Player player, String titleId, boolean notify) {
        setHidden(player, titleId, true, notify);
    }

    public void unhideTitle(Player player, String titleId, boolean notify) {
        setHidden(player, titleId, false, notify);
    }

    /**
     * 向玩家授予称号。
     * <p>
     * 支持永久、限时（如 7d）或日期区间（如 2025-01-01~2025-12-31）。
     * 授予后会更新缓存、同步外部属性，并触发全局 Tab 刷新。
     *
     * @param playerUuid  玩家 UUID
     * @param titleId     称号 ID
     * @param durationSpec 有效期描述（null 视为永久）
     * @param grantedBy   来源标识（如 "EventPacket"、管理员名）
     * @return 操作结果，{@link TitleOperationResult#success()} 为 true 表示请求已受理
     */
    public TitleOperationResult giveTitle(UUID playerUuid, String titleId, TitleDurationSpec durationSpec, String grantedBy) {
        return giveTitleAsync(playerUuid, titleId, durationSpec, grantedBy)
            .getNow(TitleOperationResult.success(message("give.queued")));
    }

    public CompletableFuture<TitleOperationResult> giveTitleAsync(UUID playerUuid, String titleId, TitleDurationSpec durationSpec, String grantedBy) {
        return giveTitleAsync(playerUuid, titleId, durationSpec, grantedBy, false);
    }

    public CompletableFuture<Boolean> grantPermanent(UUID playerUuid, String titleId, String grantedBy) {
        return giveTitleAsync(playerUuid, titleId, null, grantedBy, true).thenApply(TitleOperationResult::success);
    }

    private CompletableFuture<TitleOperationResult> giveTitleAsync(UUID playerUuid, String titleId, TitleDurationSpec durationSpec,
                                                                   String grantedBy, boolean keepPermanent) {
        if (!databaseWritesEnabled) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("common.service-down")));
        }
        TitleDefinition definition = requireTitle(titleId);
        if (definition == null) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("give.title-not-found", titleId)));
        }
        Player target = Bukkit.getPlayer(playerUuid);
        if (target != null && target.isOnline() && !hasGroupAccess(target, definition.groupId())) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("give.group-no-permission")));
        }

        Instant now = clock.instant();
        Instant activatesAt;
        Instant expiresAt;
        if (durationSpec == null || durationSpec.permanent()) {
            activatesAt = null;
            expiresAt = null;
        } else if (durationSpec.isDateRange()) {
            activatesAt = durationSpec.activatesAt();
            expiresAt = durationSpec.expiresAt();
        } else {
            activatesAt = null;
            expiresAt = now.plus(durationSpec.duration());
        }
        TitleOperationResult success = TitleOperationResult.success(message(
            "give.success",
            ChatColor.translateAlternateColorCodes('&', definition.displayName())
        ));
        PlayerOwnedTitle proposal = new PlayerOwnedTitle(definition.id(), false, now, activatesAt, expiresAt, now,
            nullToEmpty(grantedBy));
        return submitTitleWrite(playerUuid, "title-grant", "give.storage-failed", success, () -> {
            var grant = repository.grantOwnedTitle(playerUuid, proposal, keepPermanent);
            return () -> states.computeIfPresent(playerUuid, (ignored, current) -> {
                Instant appliedAt = clock.instant();
                var previous = grant.clearedPresentation() ? current.revoke(grant.title().titleId(), appliedAt) : current;
                return previous.withOwnedTitle(grant.title(), appliedAt);
            });
        });
    }

    /**
     * 收回玩家的指定称号。
     * <p>
     * 收回后会更新缓存、同步外部属性，并触发全局 Tab 刷新。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @return 操作结果，{@link TitleOperationResult#success()} 为 true 表示成功
     */
    public CompletableFuture<TitleOperationResult> revokeTitle(UUID playerUuid, String titleId) {
        TitleDefinition definition = requireTitle(titleId);
        if (definition == null) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("revoke.title-not-found", titleId)));
        }
        TitleOperationResult success = TitleOperationResult.success(message(
            "revoke.success",
            ChatColor.translateAlternateColorCodes('&', definition.displayName())
        ));
        return submitTitleWrite(playerUuid, "title-revoke", "revoke.storage-failed", success, () -> {
            repository.deleteOwnedTitle(playerUuid, definition.id());
            return () -> {
                Instant now = clock.instant();
                states.computeIfPresent(playerUuid, (ignored, current) -> current.revoke(definition.id(), now).sanitizeStored(now));
                selectedTitleIds.remove(playerUuid);
            };
        });
    }

    private CompletableFuture<TitleOperationResult> submitTitleWrite(UUID playerUuid, String action, String errorKey,
                                                                     TitleOperationResult success, ConfirmedWrite write) {
        return submitTitleWrite(playerUuid, null, action, errorKey, success, write);
    }

    private CompletableFuture<TitleOperationResult> submitTitleWrite(UUID playerUuid, AuthSnapshot admission, String action,
                                                                     String errorKey, TitleOperationResult success, ConfirmedWrite write) {
        synchronized (databaseAdmission) {
            return enqueueTitleWrite(playerUuid, admission, action, errorKey, success, write);
        }
    }

    private CompletableFuture<TitleOperationResult> enqueueTitleWrite(UUID playerUuid, AuthSnapshot admission, String action, String errorKey,
                                                                     TitleOperationResult success, ConfirmedWrite write) {
        if (!databaseWritesEnabled) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("common.service-down")));
        }
        if (!writeSlots.tryAcquire()) {
            return CompletableFuture.completedFuture(TitleOperationResult.failure(message("common.storage-busy")));
        }
        PendingTitleWrite pendingWrite = new PendingTitleWrite(playerUuid);
        CompletableFuture<TitleOperationResult> receipt = pendingWrite.future;
        try {
            databaseExecutor.execute(() -> {
                Runnable publish;
                try {
                    ensureDatabaseInitialized();
                    if (admission != null && !sameAdmission(admission)) {
                        pendingWrite.complete(TitleOperationResult.failure(message(errorKey)));
                        return;
                    }
                    publish = write.save();
                } catch (SQLException | RuntimeException exception) {
                    logger.log(java.util.logging.Level.WARNING,
                        "称号写入未获确认: " + action + " / " + playerUuid, exception);
                    pendingWrite.complete(TitleOperationResult.failure(message(errorKey)));
                    return;
                }
                pendingWrite.committed = success;
                if (!runWhenActive(() -> {
                    try {
                        publish.run();
                        refreshPlayerAfterWrite(playerUuid);
                        requestGlobalTabRefresh(action);
                    } finally {
                        pendingWrite.complete(success);
                    }
                })) pendingWrite.complete(success);
            });
        } catch (java.util.concurrent.RejectedExecutionException retired) {
            pendingWrite.complete(TitleOperationResult.failure(message(databaseWritesEnabled
                ? "common.storage-busy" : "common.service-down")));
            return receipt.copy();
        }
        if (!pendingWrite.completed) {
            PlayerWriteBarrier barrier = playerWrites.computeIfAbsent(playerUuid, ignored -> new PlayerWriteBarrier());
            barrier.pending++;
            pendingWrite.barrier = barrier;
            writeReceipts.add(pendingWrite);
        }
        return receipt.copy();
    }

    @FunctionalInterface
    private interface ConfirmedWrite {
        Runnable save() throws SQLException;
    }

    @FunctionalInterface
    private interface PlayerStateWrite {
        PlayerTitleState save(Instant now) throws SQLException;
    }

    private void submitPlayerStateChange(Player player, AuthSnapshot admission, String action, PlayerStateWrite write,
                                          Consumer<PlayerTitleState> confirmed, boolean notifyFailure) {
        UUID playerUuid = player.getUniqueId();
        submitTitleWrite(playerUuid, admission, action, "player.data-save-failed", TitleOperationResult.success(""), () -> {
            PlayerTitleState saved = write.save(clock.instant());
            return () -> {
                Instant now = clock.instant();
                PlayerTitleState current = saved.sanitizeStored(now);
                states.computeIfPresent(playerUuid, (ignored, previous) -> current);
                if (isCurrentPlayer(player) && sameAdmission(admission) && player.hasPermission("arcartxsuite.title.use")) {
                    confirmed.accept(current.sanitize(now));
                }
            };
        }).thenAccept(result -> {
            if (!result.success()) runWhenActive(() -> {
                if (!isCurrentPlayer(player) || !sameAdmission(admission) || !player.hasPermission("arcartxsuite.title.use")) return;
                if (notifyFailure) player.sendMessage(prefix() + result.message());
                refreshMenu(player);
            });
        });
    }

    public ResolvedTitleState resolveState(UUID playerUuid) {
        var view = stateView(playerUuid, clock.instant());
        return view == null ? TitleStateResolver.EMPTY : view.resolve(configuration, accessibleGroupIds(playerUuid));
    }

    public boolean hasGroupAccess(Permissible player, String groupId) {
        var group = configuration.group(groupId);
        if (group == null || group.permission().isBlank()) {
            return true;
        }
        return player != null && player.hasPermission(group.permission());
    }

    public Set<String> accessibleGroupIds(UUID playerUuid) {
        if (restrictedGroups.isEmpty() || playerUuid == null) return publicGroups;
        return accessibleGroups(Bukkit.getPlayer(playerUuid));
    }

    private Set<String> accessibleGroups(Player player) {
        if (restrictedGroups.isEmpty() || player == null || !player.isOnline()) return publicGroups;
        Set<String> accessible = null;
        for (var group : restrictedGroups) {
            if (player.hasPermission(group.permission())) {
                if (accessible == null) accessible = new HashSet<>(publicGroups);
                accessible.add(group.id());
            }
        }
        return accessible == null ? publicGroups : Set.copyOf(accessible);
    }

    public boolean attributePlusHooked() {
        return attributePlusService.hooked();
    }

    public boolean mythicLibHooked() {
        return mythicLibService.hooked();
    }

    public boolean craneAttributeHooked() {
        return craneAttributeService.hooked();
    }

    public boolean symphonyHooked() {
        return symphonyService.hooked();
    }

    public PlayerTitleState getCachedState(UUID playerUuid) {
        var view = stateView(playerUuid, clock.instant());
        return view == null ? null : view.effective;
    }

    public PlayerOwnedTitle getOwnedTitle(UUID playerUuid, String titleId) {
        PlayerTitleState state = getCachedState(playerUuid);
        if (state == null) {
            return null;
        }
        return state.ownedTitles().get(normalizeTitleId(titleId));
    }

    public boolean isReady() {
        return databaseWritesEnabled && runtimeUiId != null && !runtimeUiId.isBlank()
            && databaseInitialization.isDone() && !databaseInitialization.isCompletedExceptionally();
    }

    public int cachedPlayerCount() {
        return states.size();
    }

    public java.util.Set<String> titleIds() {
        return configuration == null ? java.util.Set.of() : configuration.titles().keySet();
    }

    public String runtimeUiId() {
        return runtimeUiId == null ? "" : runtimeUiId;
    }

    /**
     * 返回已注册到 ArcartX 的 UI ID 列表（供 Module 记录到基类生命周期管理）。
     *
     * @return 已注册的 UI ID 列表，未注册时返回空列表
     */
    public java.util.List<String> registeredUiIds() {
        return registeredUiId != null ? java.util.List.of(registeredUiId) : java.util.List.of();
    }

    private void setHidden(Player player, String titleId, boolean hidden, boolean notify) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withAuthorizedState(
            player,
            (state, admission) -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    if (notify) {
                        player.sendMessage(prefix() + message("player.title-not-found", titleId));
                    }
                    return;
                }
                if (!state.hasOwnedTitle(normalizedTitleId)) {
                    if (notify) {
                        player.sendMessage(prefix() + message(
                            "player.not-owned",
                            ChatColor.translateAlternateColorCodes('&', definition.displayName())
                        ));
                    }
                    return;
                }
                submitPlayerStateChange(player, admission, hidden ? "title-hide" : "title-unhide",
                    now -> repository.setOwnedTitleHidden(player.getUniqueId(), normalizedTitleId, hidden, now),
                    saved -> {
                        if (notify) {
                            String display = ChatColor.translateAlternateColorCodes('&', definition.displayName());
                            String key = hidden ? "player.hidden" : "player.unhidden";
                            if (!saved.hasOwnedTitle(normalizedTitleId)) key = "player.not-owned";
                            player.sendMessage(prefix() + message(key, display));
                        }
                    }, notify);
            },
            exception -> {
                if (notify) {
                    player.sendMessage(prefix() + message("player.data-load-failed"));
                }
            }
        );
    }

    private void selectTitle(Player player, String titleId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withAuthorizedState(
            player,
            (state, admission) -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    return;
                }
                selectedTitleIds.put(player.getUniqueId(), normalizedTitleId);
                refreshMenu(player);
            },
            exception -> { }
        );
    }

    private void selectDisplayTitle(Player player, String titleId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withAuthorizedState(
            player,
            (state, admission) -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    return;
                }
                if (!hasGroupAccess(player, definition.groupId())) {
                    player.sendMessage(prefix() + message("player.group-no-permission"));
                    return;
                }
                if (!state.equippedTitleIdsByGroup().containsValue(normalizedTitleId)) {
                    player.sendMessage(prefix() + message("player.only-equipped"));
                    return;
                }
                submitPlayerStateChange(player, admission, "title-display-change",
                    now -> repository.setDisplayTitle(player.getUniqueId(), normalizedTitleId, now),
                    saved -> player.sendMessage(prefix() + (normalizedTitleId.equals(saved.displayTitleId())
                        ? message("player.display-set", ChatColor.translateAlternateColorCodes('&', definition.displayName()))
                        : message("player.only-equipped"))), true);
            },
            exception -> { }
        );
    }

    private void refreshMenu(Player player) {
        if (player == null || !player.isOnline() || runtimeUiId == null || runtimeUiId.isBlank()
            || !player.hasPermission("arcartxsuite.title.use") || !bridge.isUiOpen(player, runtimeUiId)
            || playerWrites.containsKey(player.getUniqueId())) {
            return;
        }
        sendMenuPacket(player, "update");
    }

    private void sendMenuPacket(Player player, String handlerName) {
        if (!AuthAccess.admitted(player.getUniqueId())) return;
        Instant now = clock.instant();
        var view = stateView(player.getUniqueId(), now);
        if (view == null) return;
        PlayerTitleState sanitizedState = view.effective;
        Set<String> accessibleGroupIds = accessibleGroups(player);
        ResolvedTitleState resolvedState = view.resolve(configuration, accessibleGroupIds);
        String selectedTitleId = ensureSelectedTitle(player.getUniqueId(), sanitizedState);
        Map<String, Object> payload = TitleMenuPacketFactory.build(
            configuration,
            sanitizedState,
            resolvedState,
            selectedTitleId,
            accessibleGroupIds,
            now
        );
        payload.put("action_token", tokenGuard.issue(player.getUniqueId(), runtimeUiId));
        bridge.sendPacket(player, runtimeUiId, handlerName, payload);
    }

    private void withAuthorizedState(Player player, BiConsumer<PlayerTitleState, AuthSnapshot> consumer,
                                     Consumer<Throwable> errorConsumer) {
        withAuthorizedState(player, consumer, errorConsumer, true);
    }

    private void withAuthorizedState(Player player, BiConsumer<PlayerTitleState, AuthSnapshot> consumer,
                                     Consumer<Throwable> errorConsumer, boolean requireUsePermission) {
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!admission.admitted() || (requireUsePermission && !player.hasPermission("arcartxsuite.title.use"))) return;
        withLoadedState(player, admission, state -> {
            if (!requireUsePermission || player.hasPermission("arcartxsuite.title.use")) consumer.accept(state, admission);
        }, error -> {
            if (!requireUsePermission || player.hasPermission("arcartxsuite.title.use")) errorConsumer.accept(error);
        });
    }

    private void withLoadedState(Player player, Consumer<PlayerTitleState> consumer, Consumer<Throwable> errorConsumer) {
        withLoadedState(player, null, consumer, errorConsumer);
    }

    private void withLoadedState(Player player, AuthSnapshot admission, Consumer<PlayerTitleState> consumer,
                                 Consumer<Throwable> errorConsumer) {
        if (!databaseWritesEnabled) return;
        ensureLoadedAsync(player.getUniqueId()).whenComplete((state, throwable) -> runWhenActive(() -> {
            if (!isCurrentPlayer(player) || (admission != null && !sameAdmission(admission))) return;
            var pending = playerWrites.get(player.getUniqueId());
            if (pending != null && !pending.drained.isDone()) {
                pending.drained.whenComplete((ignored, failure) -> withLoadedState(player, admission, consumer, errorConsumer));
                return;
            }
            if (throwable != null) {
                errorConsumer.accept(throwable);
                return;
            }
            Instant now = clock.instant();
            var view = stateView(player.getUniqueId(), now);
            consumer.accept(view == null ? state.sanitize(now) : view.effective);
        }));
    }

    private CompletableFuture<PlayerTitleState> ensureLoadedAsync(UUID playerUuid) {
        PlayerTitleState cachedState = states.get(playerUuid);
        if (cachedState != null) {
            return CompletableFuture.completedFuture(cachedState);
        }

        var promise = new CompletableFuture<PlayerTitleState>();
        var running = loadingStates.putIfAbsent(playerUuid, promise);
        if (running != null) return running;
        try {
            databaseExecutor.execute(() -> {
                if (!databaseWritesEnabled) {
                    loadingStates.remove(playerUuid, promise);
                    promise.completeExceptionally(new IllegalStateException(message("common.service-down")));
                    return;
                }
                try {
                    ensureDatabaseInitialized();
                    PlayerTitleState loaded = repository.loadState(playerUuid).sanitizeStored(clock.instant());
                    loadingStates.computeIfPresent(playerUuid, (ignored, current) -> {
                        if (current != promise) return current;
                        if (databaseWritesEnabled) states.putIfAbsent(playerUuid, loaded);
                        return null;
                    });
                    promise.complete(loaded);
                } catch (SQLException | RuntimeException exception) {
                    loadingStates.remove(playerUuid, promise);
                    promise.completeExceptionally(exception);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException retired) {
            loadingStates.remove(playerUuid, promise);
            promise.completeExceptionally(retired);
        }
        return promise;
    }

    private TitleStateView stateView(UUID playerUuid, Instant now) {
        PlayerTitleState state = states.get(playerUuid);
        if (state == null || !databaseWritesEnabled) return null;
        var cached = stateViews.get(playerUuid);
        if (cached != null && cached.matches(state, now)) return cached;
        var next = new TitleStateView(state, now);
        var published = stateViews.compute(playerUuid, (ignored, current) -> {
            if (!databaseWritesEnabled || states.get(playerUuid) != state) return current;
            return current != null && current.matches(state, now) ? current : next;
        });
        return published != null && published.matches(state, now) ? published : next;
    }

    private void queueMaintenance(String description, RepositoryAction repositoryAction) {
        if (!databaseWritesEnabled || !maintenanceQueued.compareAndSet(false, true)) return;
        try {
            databaseExecutor.execute(() -> {
                try {
                    if (!databaseWritesEnabled) return;
                    ensureDatabaseInitialized();
                    repositoryAction.run();
                    if (configuration.debug()) logger.info("TitleDB -> " + description);
                } catch (SQLException | RuntimeException exception) {
                    logger.log(databaseWritesEnabled ? java.util.logging.Level.WARNING : java.util.logging.Level.FINE,
                        "TitleDB 操作失败(" + description + "): " + exception.getMessage());
                } finally {
                    maintenanceQueued.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            maintenanceQueued.set(false);
            logger.fine("TitleDB 尚未排队: " + description);
        }
    }

    private void tickExpirationCleanup() {
        Instant now = clock.instant();
        for (UUID playerUuid : states.keySet()) {
            if (playerWrites.containsKey(playerUuid)) continue;
            var view = stateView(playerUuid, now);
            if (view == null) continue;
            if (view.stored != view.source) {
                if (!states.replace(playerUuid, view.source, view.stored)) continue;
                view = stateView(playerUuid, now);
                if (view == null) continue;
            }
            if (view.appliedGroups != null && restrictedGroups.isEmpty()) continue;
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline() || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(playerUuid)) continue;
            if (!accessibleGroups(player).equals(view.appliedGroups)) {
                runWhenActive(() -> {
                    if (!isCurrentPlayer(player)) return;
                    syncExternalAttributes(player);
                    refreshMenu(player);
                });
            }
        }
        queueMaintenance("delete-expired", () -> repository.deleteExpiredTitles(clock.instant()));
    }

    private void refreshPlayerAfterWrite(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;
        Object refresh = new Object();
        if (pendingPlayerRefresh.putIfAbsent(playerUuid, refresh) != null) return;
        withLoadedState(player, state -> {
            if (!pendingPlayerRefresh.remove(playerUuid, refresh)) return;
            syncExternalAttributes(player);
            refreshMenu(player);
        }, failure -> pendingPlayerRefresh.remove(playerUuid, refresh));
    }

    private void syncExternalAttributes(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
        var view = stateView(player.getUniqueId(), clock.instant());
        if (view == null) return;
        var groups = accessibleGroups(player);
        var resolvedState = view.resolve(configuration, groups);
        attributePlusService.sync(player, resolvedState);
        mythicLibService.sync(player, resolvedState);
        craneAttributeService.sync(player, resolvedState);
        symphonyService.sync(player, resolvedState);
        potionEffectService.sync(player, resolvedState);
        modAttributeService.sync(player, resolvedState);
        overheadService.sync(player, resolvedState);
        view.appliedGroups = groups;
    }

    private boolean isCurrentPlayer(Player player) {
        return databaseWritesEnabled && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private static boolean sameAdmission(AuthSnapshot admission) {
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return current.admitted() && admission.generation() == current.generation()
            && Objects.equals(admission.connectionId(), current.connectionId());
    }

    private boolean runWhenActive(Runnable action) {
        if (!databaseWritesEnabled) return false;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (databaseWritesEnabled) action.run();
            });
            return true;
        } catch (org.bukkit.plugin.IllegalPluginAccessException retired) {
            return false;
        }
    }

    private TitleDefinition requireTitle(String titleId) {
        return configuration.title(normalizeTitleId(titleId));
    }

    private String ensureSelectedTitle(UUID playerUuid, PlayerTitleState state) {
        String currentSelection = normalizeTitleId(selectedTitleIds.get(playerUuid));
        TitleDefinition currentDefinition = configuration.title(currentSelection);
        if (!currentSelection.isBlank()
            && currentDefinition != null
            && hasGroupAccess(Bukkit.getPlayer(playerUuid), currentDefinition.groupId())) {
            return currentSelection;
        }
        String defaultSelection = defaultSelectedTitle(state);
        selectedTitleIds.put(playerUuid, defaultSelection);
        return defaultSelection;
    }

    private String defaultSelectedTitle(PlayerTitleState state) {
        if (state != null && !state.equippedTitleIdsByGroup().isEmpty()) {
            for (String titleId : state.equippedTitleIdsByGroup().values()) {
                if (configuration.title(titleId) != null) {
                    return titleId;
                }
            }
        }
        List<TitleDefinition> orderedTitles = configuration.orderedTitles();
        return orderedTitles.isEmpty() ? "" : orderedTitles.get(0).id();
    }

    private String selectedGroupId(Player player) {
        String selectedTitleId = selectedTitleIds.getOrDefault(player.getUniqueId(), "");
        TitleDefinition definition = requireTitle(selectedTitleId);
        return definition == null ? "" : definition.groupId();
    }

    private File exportUiFile() throws IOException {
        return uiResourceExporter.export(TITLE_MENU_RESOURCE_PATH, TITLE_MENU_FILE_PATH, false);
    }

    private String prefix() {
        return ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    private void requestGlobalTabRefresh(String reason) {
        TabRefreshable refresher = tabRefreshableProvider == null ? null : tabRefreshableProvider.get();
        if (refresher != null) {
            refresher.requestGlobalRefresh(reason);
        }
    }

    static void requestGlobalTabRefresh(TabRefreshable requester, String reason) {
        if (requester == null) {
            return;
        }
        String refreshReason = nullToEmpty(reason).isBlank() ? "title-change" : reason;
        requester.requestGlobalRefresh(refreshReason);
    }

    private static String normalizeTitleId(String titleId) {
        if (titleId == null) {
            return "";
        }
        return titleId.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface RepositoryAction {
        void run() throws SQLException;
    }

    private static final class TitleThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "AXS-title-db");
            thread.setDaemon(true);
            return thread;
        }
    }
}


