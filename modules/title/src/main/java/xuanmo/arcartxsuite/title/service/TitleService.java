package xuanmo.arcartxsuite.title.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.bridge.ArcartXWorldTextureService;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.title.TitleDurationParser.TitleDurationSpec;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
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

    /**
     * UI 资源导出函数。输入：(resourcePath, relativeUiPath, overwrite) -> File
     */
    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    private final JavaPlugin plugin;
    private final TitleModuleConfiguration configuration;
    private final TitleRepository repository;
    private final ArcartXPacketBridge bridge;
    private final PacketGuardAPI packetGuard;
    private final Supplier<TabRefreshable> tabRefreshableProvider;
    private final UiResourceExporter uiResourceExporter;
    private final Clock clock;
    private final Logger logger;
    private final TitleAttributePlusService attributePlusService;
    private final TitleMythicLibService mythicLibService;
    private final TitleCraneAttributeService craneAttributeService;
    private final TitleSymphonyService symphonyService;
    private final TitleOverheadService overheadService;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(new TitleThreadFactory());
    private volatile boolean databaseWritesEnabled = true;
    private final ConcurrentMap<UUID, PlayerTitleState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<PlayerTitleState>> loadingStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> selectedTitleIds = new ConcurrentHashMap<>();

    private Listener playerListener;
    private BukkitTask expirationTask;
    private String runtimeUiId;
    private String registeredUiId;

    public TitleService(
        JavaPlugin plugin,
        TitleModuleConfiguration configuration,
        TitleRepository repository,
        ArcartXPacketBridge bridge,
        PacketGuardAPI packetGuard,
        Supplier<TabRefreshable> tabRefreshableProvider,
        UiResourceExporter uiResourceExporter,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this(plugin, configuration, repository, bridge, packetGuard, tabRefreshableProvider, uiResourceExporter, Clock.systemUTC(), attributeBridge);
    }

    public TitleService(
        JavaPlugin plugin,
        TitleModuleConfiguration configuration,
        TitleRepository repository,
        ArcartXPacketBridge bridge,
        PacketGuardAPI packetGuard,
        Supplier<TabRefreshable> tabRefreshableProvider,
        UiResourceExporter uiResourceExporter,
        Clock clock,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.bridge = bridge;
        this.packetGuard = packetGuard;
        this.tabRefreshableProvider = tabRefreshableProvider;
        this.uiResourceExporter = uiResourceExporter;
        this.clock = clock;
        this.logger = plugin.getLogger();
        this.attributePlusService = new TitleAttributePlusService(plugin, configuration.attributePlus(), attributeBridge.attributePlus());
        this.mythicLibService = new TitleMythicLibService(plugin, configuration.mythicLib(), attributeBridge.mythicLib());
        this.craneAttributeService = new TitleCraneAttributeService(plugin, configuration.craneAttribute(), attributeBridge.craneAttribute());
        this.symphonyService = new TitleSymphonyService(plugin, configuration.symphony(), attributeBridge.symphony());
        ArcartXWorldTextureService worldTextureService = new ArcartXWorldTextureService(plugin);
        worldTextureService.initialize();
        this.overheadService = new TitleOverheadService(worldTextureService, logger);
    }

    /**
     * 启动称号服务。
     * <p>
     * 初始化数据库、注册 UI、绑定玩家监听器、预加载在线玩家数据，
     * 并启动过期称号清理定时任务。
     */
    public void start() throws SQLException, IOException {
        repository.initialize();
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
        databaseWritesEnabled = false;
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
            overheadService.clear(player);
        }
        mythicLibService.shutdown();
        overheadService.shutdown();

        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        } finally {
            repository.close();
        }

        states.clear();
        loadingStates.clear();
        selectedTitleIds.clear();
    }

    public void preloadPlayer(Player player) {
        if (player == null) {
            return;
        }
        ensureLoadedAsync(player.getUniqueId()).whenComplete((state, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null || !player.isOnline()) {
                return;
            }
            syncExternalAttributes(player, state);
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    syncExternalAttributes(player, getCachedState(player.getUniqueId()));
                },
                20L
            );
        }));
    }

    public void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        attributePlusService.clear(player);
        mythicLibService.clear(player);
        craneAttributeService.clear(player);
        symphonyService.clear(player);
        UUID playerUuid = player.getUniqueId();
        states.remove(playerUuid);
        loadingStates.remove(playerUuid);
        selectedTitleIds.remove(playerUuid);
    }

    /**
     * 为指定玩家打开称号管理菜单。
     * <p>
     * 异步加载玩家数据后，通过 {@link ArcartXPacketBridge} 发送 UI 初始化包。
     *
     * @param player 目标玩家
     */
    public void openMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        withLoadedState(
            player,
            state -> {
                selectedTitleIds.put(player.getUniqueId(), ensureSelectedTitle(player.getUniqueId(), state));
                bridge.openUi(player, runtimeUiId);
                sendMenuPacket(player, "init");
            },
            exception -> player.sendMessage(prefix() + ChatColor.RED + "称号数据加载失败，请稍后重试。")
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

        String action = data == null || data.isEmpty() ? "refresh" : data.get(0).trim().toLowerCase(Locale.ROOT);
        if (packetGuard != null && !packetGuard.allow(player, "title", action, configuration.debug())) {
            return true;
        }
        switch (action) {
            case "open" -> openMenu(player);
            case "select" -> {
                if (data.size() > 1) {
                    selectTitle(player, data.get(1));
                }
            }
            case "equip" -> equipTitle(player, data.size() > 1 ? data.get(1) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""));
            case "unequip_group" -> unequipGroup(player, data.size() > 1 ? data.get(1) : selectedGroupId(player));
            case "unequip_all" -> unequipAll(player);
            case "unequip" -> {
                String target = data.size() > 1 ? data.get(1) : selectedGroupId(player);
                if ("all".equalsIgnoreCase(target)) {
                    unequipAll(player);
                } else {
                    unequipGroup(player, target);
                }
            }
            case "hide" -> hideTitle(player, data.size() > 1 ? data.get(1) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""), true);
            case "unhide" -> unhideTitle(player, data.size() > 1 ? data.get(1) : selectedTitleIds.getOrDefault(player.getUniqueId(), ""), true);
            case "refresh" -> refreshMenu(player);
            default -> refreshMenu(player);
        }
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
        withLoadedState(
            player,
            state -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    player.sendMessage(prefix() + ChatColor.RED + "称号不存在: " + titleId);
                    return;
                }
                if (!state.hasOwnedTitle(normalizedTitleId)) {
                    player.sendMessage(prefix() + ChatColor.RED + "你尚未拥有称号: " + definition.displayName());
                    return;
                }
                Instant now = clock.instant();
                PlayerTitleState updatedState = state.equip(definition.groupId(), normalizedTitleId, now).sanitize(now);
                storeState(player.getUniqueId(), updatedState);
                selectedTitleIds.put(player.getUniqueId(), normalizedTitleId);
                queueWrite(
                    "save-equipped:equip",
                    () -> repository.saveEquippedTitle(player.getUniqueId(), definition.groupId(), normalizedTitleId, updatedState.updatedAt())
                );
                syncExternalAttributes(player, updatedState);
                refreshMenu(player);
                requestGlobalTabRefresh("title-equip");
                player.sendMessage(prefix() + ChatColor.GREEN + "已装备称号到分组 " + definition.groupId() + ": " + definition.displayName());
            },
            exception -> player.sendMessage(prefix() + ChatColor.RED + "称号数据加载失败，请稍后重试。")
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
        withLoadedState(
            player,
            state -> {
                Instant now = clock.instant();
                PlayerTitleState updatedState = state.unequipGroup(normalizedGroupId, now).sanitize(now);
                storeState(player.getUniqueId(), updatedState);
                queueWrite("delete-equipped:group", () -> repository.deleteEquippedGroup(player.getUniqueId(), normalizedGroupId));
                syncExternalAttributes(player, updatedState);
                refreshMenu(player);
                requestGlobalTabRefresh("title-unequip");
                player.sendMessage(prefix() + ChatColor.YELLOW + "已卸下分组称号: " + normalizedGroupId);
            },
            exception -> player.sendMessage(prefix() + ChatColor.RED + "称号数据加载失败，请稍后重试。")
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
        withLoadedState(
            player,
            state -> {
                Instant now = clock.instant();
                PlayerTitleState updatedState = state.unequipAll(now).sanitize(now);
                storeState(player.getUniqueId(), updatedState);
                queueWrite("delete-equipped:all", () -> repository.deleteAllEquippedGroups(player.getUniqueId()));
                syncExternalAttributes(player, updatedState);
                refreshMenu(player);
                requestGlobalTabRefresh("title-unequip-all");
                player.sendMessage(prefix() + ChatColor.YELLOW + "已卸下全部分组称号。");
            },
            exception -> player.sendMessage(prefix() + ChatColor.RED + "称号数据加载失败，请稍后重试。")
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
     * @return 操作结果，{@link TitleOperationResult#success()} 为 true 表示成功
     */
    public TitleOperationResult giveTitle(UUID playerUuid, String titleId, TitleDurationSpec durationSpec, String grantedBy) {
        TitleDefinition definition = requireTitle(titleId);
        if (definition == null) {
            return TitleOperationResult.failure("称号不存在: " + titleId);
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
        states.compute(
            playerUuid,
            (ignored, currentState) -> {
                PlayerTitleState baseState = currentState == null ? PlayerTitleState.empty(playerUuid) : sanitizeCachedState(playerUuid, currentState);
                return baseState.grant(definition.id(), now, activatesAt, expiresAt, now, nullToEmpty(grantedBy)).sanitize(now);
            }
        );
        queueWrite(
            "save-owned:grant",
            () -> repository.saveOwnedTitle(playerUuid, new PlayerOwnedTitle(definition.id(), false, now, activatesAt, expiresAt, now, nullToEmpty(grantedBy)))
        );
        syncExternalAttributesForOnlinePlayer(playerUuid);
        refreshMenuForOnlinePlayer(playerUuid);
        requestGlobalTabRefresh("title-grant");
        return TitleOperationResult.success("已授予称号: " + definition.displayName());
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
    public TitleOperationResult revokeTitle(UUID playerUuid, String titleId) {
        TitleDefinition definition = requireTitle(titleId);
        if (definition == null) {
            return TitleOperationResult.failure("称号不存在: " + titleId);
        }

        Instant now = clock.instant();
        PlayerTitleState updatedState = states.computeIfPresent(playerUuid, (ignored, currentState) -> currentState.revoke(definition.id(), now).sanitize(now));
        if (updatedState != null) {
            selectedTitleIds.put(playerUuid, defaultSelectedTitle(updatedState));
            persistEquippedState(playerUuid, updatedState, "revoke");
        }
        queueWrite("delete-equipped:revoke", () -> repository.deleteEquippedTitle(playerUuid, definition.id()));
        queueWrite("delete-owned:revoke", () -> repository.deleteOwnedTitle(playerUuid, definition.id()));
        syncExternalAttributesForOnlinePlayer(playerUuid);
        refreshMenuForOnlinePlayer(playerUuid);
        requestGlobalTabRefresh("title-revoke");
        return TitleOperationResult.success("已扣除称号: " + definition.displayName());
    }

    public ResolvedTitleState resolveState(UUID playerUuid) {
        return TitleStateResolver.resolve(getCachedState(playerUuid), configuration, clock.instant());
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
        PlayerTitleState state = states.get(playerUuid);
        return state == null ? null : sanitizeCachedState(playerUuid, state);
    }

    public PlayerOwnedTitle getOwnedTitle(UUID playerUuid, String titleId) {
        PlayerTitleState state = getCachedState(playerUuid);
        if (state == null) {
            return null;
        }
        return state.ownedTitles().get(normalizeTitleId(titleId));
    }

    public boolean isReady() {
        return runtimeUiId != null && !runtimeUiId.isBlank();
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

    private void setHidden(Player player, String titleId, boolean hidden, boolean notify) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withLoadedState(
            player,
            state -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    if (notify) {
                        player.sendMessage(prefix() + ChatColor.RED + "称号不存在: " + titleId);
                    }
                    return;
                }
                if (!state.hasOwnedTitle(normalizedTitleId)) {
                    if (notify) {
                        player.sendMessage(prefix() + ChatColor.RED + "你尚未拥有称号: " + definition.displayName());
                    }
                    return;
                }
                Instant now = clock.instant();
                PlayerTitleState updatedState = state.setHidden(normalizedTitleId, hidden, now).sanitize(now);
                storeState(player.getUniqueId(), updatedState);
                PlayerOwnedTitle ownedTitle = updatedState.ownedTitles().get(normalizedTitleId);
                if (ownedTitle != null) {
                    queueWrite("save-owned:hidden", () -> repository.saveOwnedTitle(player.getUniqueId(), ownedTitle));
                }
                refreshMenu(player);
                requestGlobalTabRefresh(hidden ? "title-hide" : "title-unhide");
                if (notify) {
                    player.sendMessage(
                        prefix()
                            + (hidden ? ChatColor.YELLOW + "已隐藏称号: " : ChatColor.GREEN + "已取消隐藏称号: ")
                            + definition.displayName()
                    );
                }
            },
            exception -> {
                if (notify) {
                    player.sendMessage(prefix() + ChatColor.RED + "称号数据加载失败，请稍后重试。");
                }
            }
        );
    }

    private void selectTitle(Player player, String titleId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        withLoadedState(
            player,
            state -> {
                TitleDefinition definition = requireTitle(normalizedTitleId);
                if (definition == null) {
                    return;
                }
                if (!state.hasOwnedTitle(normalizedTitleId)) {
                    return;
                }
                selectedTitleIds.put(player.getUniqueId(), normalizedTitleId);
                refreshMenu(player);
            },
            exception -> { }
        );
    }

    private void refreshMenu(Player player) {
        if (player == null || !player.isOnline() || runtimeUiId == null || runtimeUiId.isBlank()) {
            return;
        }
        sendMenuPacket(player, "update");
    }

    private void sendMenuPacket(Player player, String handlerName) {
        PlayerTitleState state = getCachedState(player.getUniqueId());
        if (state == null) {
            return;
        }
        Instant now = clock.instant();
        PlayerTitleState sanitizedState = state.sanitize(now);
        ResolvedTitleState resolvedState = TitleStateResolver.resolve(sanitizedState, configuration, now);
        String selectedTitleId = ensureSelectedTitle(player.getUniqueId(), sanitizedState);
        bridge.sendPacket(
            player,
            runtimeUiId,
            handlerName,
            TitleMenuPacketFactory.build(configuration, sanitizedState, resolvedState, selectedTitleId, now)
        );
    }

    private void withLoadedState(Player player, Consumer<PlayerTitleState> consumer, Consumer<Throwable> errorConsumer) {
        ensureLoadedAsync(player.getUniqueId()).whenComplete((state, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                errorConsumer.accept(throwable);
                return;
            }
            consumer.accept(sanitizeCachedState(player.getUniqueId(), state));
        }));
    }

    private CompletableFuture<PlayerTitleState> ensureLoadedAsync(UUID playerUuid) {
        PlayerTitleState cachedState = states.get(playerUuid);
        if (cachedState != null) {
            return CompletableFuture.completedFuture(sanitizeCachedState(playerUuid, cachedState));
        }

        return loadingStates.computeIfAbsent(playerUuid, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                return repository.loadState(playerUuid).sanitize(clock.instant());
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor).whenComplete((loadedState, throwable) -> {
            loadingStates.remove(playerUuid);
            if (throwable == null && loadedState != null) {
                states.merge(
                    playerUuid,
                    loadedState,
                    (currentState, freshState) -> currentState.updatedAt().isAfter(freshState.updatedAt()) ? currentState : freshState
                );
            }
        }));
    }

    private PlayerTitleState sanitizeCachedState(UUID playerUuid, PlayerTitleState state) {
        Instant now = clock.instant();
        PlayerTitleState sanitizedState = state.sanitize(now);
        if (sanitizedState == state) {
            return state;
        }
        storeState(playerUuid, sanitizedState);
        if (!state.equippedTitleIdsByGroup().equals(sanitizedState.equippedTitleIdsByGroup())) {
            persistEquippedState(playerUuid, sanitizedState, "sanitize");
        }
        return sanitizedState;
    }

    private void storeState(UUID playerUuid, PlayerTitleState updatedState) {
        states.put(playerUuid, updatedState);
    }

    private void queueWrite(String description, RepositoryAction repositoryAction) {
        if (!databaseWritesEnabled) {
            return;
        }
        databaseExecutor.submit(() -> {
            try {
                repositoryAction.run();
                if (configuration.debug()) {
                    logger.info("TitleDB -> " + description);
                }
            } catch (SQLException exception) {
                logger.warning("TitleDB 操作失败(" + description + "): " + exception.getMessage());
            }
        });
    }

    private void tickExpirationCleanup() {
        Instant now = clock.instant();
        for (UUID playerUuid : states.keySet()) {
            PlayerTitleState state = states.get(playerUuid);
            if (state == null) {
                continue;
            }
            PlayerTitleState sanitizedState = state.sanitize(now);
            if (sanitizedState == state) {
                continue;
            }
            states.put(playerUuid, sanitizedState);
            persistEquippedState(playerUuid, sanitizedState, "cleanup");
            syncExternalAttributesForOnlinePlayer(playerUuid);
            refreshMenuForOnlinePlayer(playerUuid);
        }
        queueWrite("delete-expired", () -> repository.deleteExpiredTitles(now));
    }

    private void refreshMenuForOnlinePlayer(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshMenu(player));
    }

    private void syncExternalAttributes(Player player, PlayerTitleState state) {
        if (player == null || !player.isOnline() || state == null) {
            return;
        }
        PlayerTitleState sanitizedState = sanitizeCachedState(player.getUniqueId(), state);
        ResolvedTitleState resolvedState = TitleStateResolver.resolve(sanitizedState, configuration, clock.instant());
        attributePlusService.sync(player, resolvedState);
        mythicLibService.sync(player, resolvedState);
        craneAttributeService.sync(player, resolvedState);
        symphonyService.sync(player, resolvedState);
        overheadService.sync(player, resolvedState);
    }

    private void syncExternalAttributesForOnlinePlayer(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(
            plugin,
            () -> withLoadedState(player, state -> syncExternalAttributes(player, state), exception -> { })
        );
    }

    private TitleDefinition requireTitle(String titleId) {
        return configuration.title(normalizeTitleId(titleId));
    }

    private String ensureSelectedTitle(UUID playerUuid, PlayerTitleState state) {
        String currentSelection = normalizeTitleId(selectedTitleIds.get(playerUuid));
        if (!currentSelection.isBlank() && configuration.title(currentSelection) != null) {
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

    private void persistEquippedState(UUID playerUuid, PlayerTitleState state, String reason) {
        queueWrite(
            "save-equipped:" + reason,
            () -> {
                repository.deleteAllEquippedGroups(playerUuid);
                for (var entry : state.equippedTitleIdsByGroup().entrySet()) {
                    repository.saveEquippedTitle(playerUuid, entry.getKey(), entry.getValue(), state.updatedAt());
                }
            }
        );
    }

    private File exportUiFile() throws IOException {
        return uiResourceExporter.export(TITLE_MENU_RESOURCE_PATH, TITLE_MENU_FILE_PATH, false);
    }

    private String prefix() {
        return ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
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
