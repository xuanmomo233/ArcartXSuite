package xuanmo.arcartxsuite.map.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigationHandler;
import xuanmo.arcartxsuite.api.util.NavigationMarkerService;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorDefinition;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorUnlock;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CurrencyCost;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.ItemCost;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.PolygonRegion;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapNavigationState.TargetType;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapWaypoint;
import xuanmo.arcartxsuite.map.storage.MapRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import java.util.logging.Logger;

/**
 * 地图核心服务，实现 {@code Listener} 与 {@code MapUiPacketHandler.ActionTarget}。
 * <p>
 * 关联模块：map。管理玩家视图状态、导航状态、外部目标、锚点解锁 / 传送、
 * 路径点 CRUD、HUD 显示、数据包处理与 UI 快照同步等全部运行时逻辑。
 * 通过 {@code MapSnapshotBuilder} 生成菜单与 HUD 快照并推送到客户端。
 */
public final class MapService implements Listener, MapUiPacketHandler.ActionTarget {

    public static final String MENU_UI_RESOURCE_PATH = "arcartx/ui/map_menu.yml";
    public static final String MENU_UI_FILE_PATH = "ui/map_menu.yml";
    public static final String HUD_UI_RESOURCE_PATH = "arcartx/ui/map_hud.yml";
    public static final String HUD_UI_FILE_PATH = "ui/map_hud.yml";

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketGuardAPI packetGuard;
    private final MapModuleConfiguration configuration;
    private final MapRepository repository;
    private final PacketBridgeAPI bridge;
    private final String menuUiId;
    private final String hudUiId;
    private final MapSnapshotBuilder snapshotBuilder = new MapSnapshotBuilder();
    private final MapUiPacketHandler uiPacketHandler;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemMatcherAPI itemMatcherSupport;
    private final WaypointBridgeAPI waypointBridge;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final MessageProvider messages;
    private final java.util.function.Supplier<EventBusCapability> eventBusProvider;
    private NavigationMarkerService markerService;
    private final ConcurrentMap<UUID, MapPlayerViewState> viewStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MapNavigationState> navigationStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>> externalTargets = new ConcurrentHashMap<>();
    /** 外部目标导航回调：source（normalize 后）→ handler，由来源模块/插件注册。 */
    private final ConcurrentMap<String, MapNavigationHandler> navigationSources = new ConcurrentHashMap<>();
    /** owner 插件 → 已注册的 source 列表，用于插件禁用时自动注销回调。 */
    private final ConcurrentMap<Plugin, Set<String>> navigationSourceOwners = new ConcurrentHashMap<>();
    private final Set<UUID> openedHudPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defaultUnlocksEnsured = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, CachedPlayerMapData> playerDataCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();

    /** 分享码 → 点位快照（内存缓存，过期/重启/重载后失效）。 */
    private final ConcurrentMap<String, SharedPoint> shareCache = new ConcurrentHashMap<>();

    private record CachedPlayerMapData(Set<String> unlockedAnchors, List<MapWaypoint> waypoints) {
    }

    /**
     * 一条被分享的点位快照。
     * <p>
     * 路径点/标记点是玩家私有数据，分享不直接引用原记录 ID——
     * 存完整快照，原主改名/删除不影响已领取和待领取的副本。
     *
     * @param type        {@code "waypoint"} 或 {@code "pin"}
     * @param name        点位名称
     * @param description 点位描述
     * @param world       所在世界 ID（已 normalize）
     * @param expiresAt   过期时间戳（毫秒）
     */
    private record SharedPoint(
        String type, String name, String description, String world,
        double x, double y, double z, String senderName, long expiresAt
    ) {
    }

    public MapService(
        JavaPlugin plugin,
        Logger logger,
        PacketGuardAPI packetGuard,
        MapModuleConfiguration configuration,
        MapRepository repository,
        PacketBridgeAPI bridge,
        String menuUiId,
        String hudUiId,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        WaypointBridgeAPI waypointBridge
    ) {
        this(plugin, logger, packetGuard, configuration, repository, bridge, menuUiId, hudUiId,
            itemSourceRegistry, itemMatcherSupport, currencyBridgeManager, waypointBridge, null, null, null);
    }

    public MapService(
        JavaPlugin plugin,
        Logger logger,
        PacketGuardAPI packetGuard,
        MapModuleConfiguration configuration,
        MapRepository repository,
        PacketBridgeAPI bridge,
        String menuUiId,
        String hudUiId,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        WaypointBridgeAPI waypointBridge,
        AdyeshachNpcBridgeAPI npcBridge,
        MessageProvider messages,
        java.util.function.Supplier<EventBusCapability> eventBusProvider
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetGuard = packetGuard;
        this.configuration = configuration;
        this.repository = repository;
        this.bridge = bridge;
        this.menuUiId = menuUiId;
        this.hudUiId = hudUiId;
        this.uiPacketHandler = new MapUiPacketHandler(this, configuration.client().packetId());
        this.currencyBridgeManager = currencyBridgeManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.waypointBridge = waypointBridge;
        this.npcBridge = npcBridge;
        this.messages = messages;
        this.eventBusProvider = eventBusProvider != null ? eventBusProvider : () -> null;
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    /**
     * 启动地图服务，初始化存储、导航桥接与标记服务，并注册事件监听。
     *
     * @throws Exception 初始化失败时抛出
     */
    public void start() throws Exception {
        repository.initialize();
        if (configuration.navigation().enabled()) {
            waypointBridge.initialize("Map 导航");
        }
        if (configuration.navigation().marker().enabled() && npcBridge != null) {
            npcBridge.setDebug(configuration.debug());
            npcBridge.initialize();
            markerService = new NavigationMarkerService(
                plugin, npcBridge, configuration.navigation().marker(), logger,
                configuration.debug(), "map_path_"
            );
            markerService.start();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 关闭地图服务，注销监听、清理所有玩家状态并释放资源。
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTrackSilently(player);
            if (bridge != null && !hudUiId.isBlank()) {
                bridge.closeUi(player, hudUiId);
            }
        }
        if (markerService != null) {
            markerService.shutdown();
            markerService = null;
        }
        repository.shutdown();
        waypointBridge.shutdown();
        viewStates.clear();
        navigationStates.clear();
        externalTargets.clear();
        openedHudPlayers.clear();
        defaultUnlocksEnsured.clear();
        playerDataCache.clear();
        shareCache.clear();
    }

    /**
     * 处理来自客户端的地图数据包。
     *
     * @param player   发送数据包的玩家
     * @param packetId 数据包标识
     * @param data     数据包内容
     * @return {@code true} 表示数据包已被处理
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return uiPacketHandler.handleClientPacket(player, packetId, data);
    }

    /**
     * 处理客户端初始化完成事件，确保默认解锁并按配置延迟显示 HUD。
     *
     * @param player 完成初始化的玩家
     */
    public void handleClientInitialized(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ensureDefaultUnlocks(player);
        if (!configuration.join().showHudOnJoin()) {
            return;
        }
        long delay = configuration.join().showHudDelayTicks();
        AxsScheduler.runTaskLater(
            plugin,
            () -> {
                if (player.isOnline()) {
                    setHudInternal(player, true, false);
                }
            },
            delay
        );
    }

    /**
     * 获取已配置的世界 ID 列表。
     *
     * @return 世界 ID 列表
     */
    public List<String> configuredWorldIds() {
        return new ArrayList<>(configuration.worlds().keySet());
    }

    /**
     * 获取已配置的锚点 ID 列表。
     *
     * @return 锚点 ID 列表
     */
    public List<String> configuredAnchorIds() {
        return new ArrayList<>(configuration.anchors().keySet());
    }

    /**
     * 获取已配置的世界数量。
     *
     * @return 世界数量
     */
    public int configuredWorldCount() {
        return configuration.worlds().size();
    }

    /**
     * 获取已配置的锚点数量。
     *
     * @return 锚点数量
     */
    public int configuredAnchorCount() {
        return configuration.anchors().size();
    }

    /**
     * 获取当前正在导航的玩家数量。
     *
     * @return 处于活跃导航状态的玩家数
     */
    public int trackingPlayerCount() {
        return (int) navigationStates.values().stream().filter(MapNavigationState::active).count();
    }

    /**
     * 判断路径点运行时是否就绪（导航未启用或桥接可用时返回 {@code true}）。
     *
     * @return 路径点运行时就绪状态
     */
    public boolean waypointRuntimeReady() {
        return !configuration.navigation().enabled() || waypointBridge.available();
    }

    /**
     * 为玩家打开地图菜单并选中指定目标。
     * <p>
     * {@code targetId} 依次匹配外部目标 → 玩家路径点 → 标记点：命中则切到目标
     * 所在世界并选中它；否则若恰为已配置的世界 id 则打开该世界地图；
     * 均未命中或为空时按默认世界规则打开。
     *
     * @param player   目标玩家
     * @param targetId 目标 ID（可为空字符串）
     * @return 操作结果
     */
    public MapOperationResult openMenuFor(Player player, String targetId) {
        String id = normalizeId(targetId);
        String worldId = "";
        String selectType = "";
        if (!id.isBlank()) {
            MapExternalTarget ext = findExternalTarget(player, id);
            if (ext != null) {
                worldId = ext.worldId();
                selectType = "external";
            } else {
                MapWaypoint waypoint = findWaypoint(player, id);
                if (waypoint != null) {
                    worldId = waypoint.world();
                    selectType = "waypoint";
                } else {
                    MapPin pin = findPin(player, id);
                    if (pin != null) {
                        worldId = pin.world();
                        selectType = "pin";
                    }
                }
            }
            // 未匹配到任何目标时，允许把参数直接当作世界 id（命令 /map open <world> 兼容）
            if (selectType.isBlank() && configuration.worlds().containsKey(id)) {
                worldId = id;
            }
        }
        MapOperationResult result = openMenuInternal(player, worldId, true);
        if (result.success() && !selectType.isBlank()) {
            switch (selectType) {
                case "external" -> state(player).selectExternalTarget(id);
                case "waypoint" -> state(player).selectWaypoint(id);
                case "pin" -> state(player).selectPin(id);
                default -> { }
            }
            sync(player, false);
        }
        return result;
    }

    /**
     * 设置玩家 HUD 显示状态。
     *
     * @param player 目标玩家
     * @param mode   模式：{@code "on"} / {@code "off"} / {@code "toggle"}
     * @return 操作结果
     */
    public MapOperationResult setHud(Player player, String mode) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        boolean targetVisible;
        if ("on".equalsIgnoreCase(mode)) {
            targetVisible = true;
        } else if ("off".equalsIgnoreCase(mode)) {
            targetVisible = false;
        } else {
            targetVisible = !state(player).hudVisible();
        }
        return setHudInternal(player, targetVisible, true);
    }

    /**
     * 清除玩家的当前导航追踪。
     *
     * @param player 目标玩家
     * @return 操作结果
     */
    public MapOperationResult clearTrackCommand(Player player) {
        return clearTrackInternal(player, true);
    }

    /**
     * 新增或更新外部导航目标，并可选地自动选中。
     *
     * @param player 目标玩家
     * @param spec   外部目标规格（公开契约）
     * @param select 是否自动选中该目标
     */
    public void upsertExternalTarget(Player player, MapExternalTargetSpec spec, boolean select) {
        if (player == null || spec == null) {
            return;
        }
        MapExternalTarget target = MapExternalTarget.fromSpec(spec);
        externalTargets
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(normalizeId(target.targetId()), target);
        if (select) {
            state(player).selectWorld(normalizeId(target.worldId()));
            state(player).selectExternalTarget(normalizeId(target.targetId()));
        }
        sync(player, false);
    }

    /**
     * 批量新增或更新外部导航目标（一次视图同步）。
     *
     * @param player 目标玩家
     * @param specs  外部目标规格列表
     */
    public void upsertExternalTargets(Player player, List<MapExternalTargetSpec> specs) {
        if (player == null || specs == null || specs.isEmpty()) {
            return;
        }
        ConcurrentMap<String, MapExternalTarget> state =
            externalTargets.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        for (MapExternalTargetSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            MapExternalTarget target = MapExternalTarget.fromSpec(spec);
            state.put(normalizeId(target.targetId()), target);
        }
        sync(player, false);
    }

    /**
     * 标记外部目标是否正被来源导航（驱动面板"导航"/"取消"按钮切换）。
     *
     * @param player    目标玩家
     * @param targetId  目标 id
     * @param navigated true = 来源正在导航
     */
    public void setExternalNavigated(Player player, String targetId, boolean navigated) {
        if (player == null || targetId == null || targetId.isBlank()) {
            return;
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        String key = normalizeId(targetId);
        MapExternalTarget target = state.get(key);
        if (target == null || target.navigated() == navigated) {
            return;
        }
        state.put(key, target.withNavigated(navigated));
        sync(player, false);
    }

    /**
     * 用给定集合原子替换某来源的全部外部目标（reconcile）。
     * <p>
     * 与"先 clearExternalTargets 再 upsert"的区别：一次视图同步、
     * 选中态按最终集合回收——仍然存在的 targetId 保持选中不闪断。
     *
     * @param player 目标玩家
     * @param source 来源标识
     * @param specs  最终目标集合（可为空列表 = 清空该来源）
     */
    public void reconcileExternalTargets(Player player, String source, List<MapExternalTargetSpec> specs) {
        if (player == null || source == null) {
            return;
        }
        String normalizedSource = normalizeId(source);
        ConcurrentMap<String, MapExternalTarget> state =
            externalTargets.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        Set<String> keep = ConcurrentHashMap.newKeySet();
        for (MapExternalTargetSpec spec : specs == null ? List.<MapExternalTargetSpec>of() : specs) {
            if (spec == null) {
                continue;
            }
            MapExternalTarget target = MapExternalTarget.fromSpec(spec);
            String key = normalizeId(target.targetId());
            keep.add(key);
            state.put(key, target);
        }
        state.entrySet().removeIf(e -> normalizeId(e.getValue().source()).equals(normalizedSource) && !keep.contains(e.getKey()));
        if (state.isEmpty()) {
            externalTargets.remove(player.getUniqueId());
        }
        // 选中态回收：选中的外部目标不在最终集合时清除
        String selected = state(player).selectedExternalTargetId();
        ConcurrentMap<String, MapExternalTarget> remaining = externalTargets.get(player.getUniqueId());
        if (!selected.isBlank() && (remaining == null || !remaining.containsKey(normalizeId(selected)))) {
            state(player).clearSelection();
        }
        sync(player, false);
    }

    /**
     * 注册外部目标导航回调（按 source 归属，插件禁用时自动注销）。
     *
     * @param owner   归属插件
     * @param source  来源标识
     * @param handler 导航回调
     */
    public void registerNavigationSource(Plugin owner, String source, MapNavigationHandler handler) {
        if (source == null || source.isBlank() || handler == null) {
            return;
        }
        String key = normalizeId(source);
        navigationSources.put(key, handler);
        if (owner != null) {
            navigationSourceOwners.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /**
     * 注销指定来源的导航回调。
     *
     * @param source 来源标识
     */
    public void unregisterNavigationSource(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        String key = normalizeId(source);
        navigationSources.remove(key);
        for (Set<String> owned : navigationSourceOwners.values()) {
            owned.remove(key);
        }
    }

    /**
     * 移除指定的外部导航目标。
     *
     * @param player    目标玩家
     * @param targetId  外部目标 ID
     * @param syncView  是否同步刷新 UI
     */
    public void removeExternalTarget(Player player, String targetId, boolean syncView) {
        if (player == null || targetId == null || targetId.isBlank()) {
            return;
        }
        MapNavigationState navigationState = navigationStates.get(player.getUniqueId());
        if (navigationState != null && navigationState.matchesExternal(targetId)) {
            clearTrackSilently(player);
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state != null) {
            state.remove(normalizeId(targetId));
            if (state.isEmpty()) {
                externalTargets.remove(player.getUniqueId());
            }
        }
        if (this.state(player).selectedExternalTargetId().equalsIgnoreCase(normalizeId(targetId))) {
            this.state(player).clearSelection();
        }
        if (syncView) {
            sync(player, false);
        }
    }

    /**
     * 按来源清除玩家的所有外部导航目标。
     *
     * @param player   目标玩家
     * @param source   来源模块标识，为空时清除全部
     * @param syncView 是否同步刷新 UI
     */
    public void clearExternalTargets(Player player, String source, boolean syncView) {
        if (player == null) {
            return;
        }
        MapNavigationState navigationState = navigationStates.get(player.getUniqueId());
        String normalizedSource = normalizeId(source);
        if (navigationState != null
            && navigationState.targetType() == TargetType.EXTERNAL
            && (normalizedSource.isBlank() || normalizeId(navigationState.source()).equals(normalizedSource))) {
            clearTrackSilently(player);
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state != null) {
            state.entrySet().removeIf(entry -> normalizedSource.isBlank() || normalizeId(entry.getValue().source()).equals(normalizedSource));
            if (state.isEmpty()) {
                externalTargets.remove(player.getUniqueId());
            }
        }
        // 选中态兜底：若当前选中的外部目标已被移除，清除选中避免详情面板残留幻影状态
        String selected = state(player).selectedExternalTargetId();
        ConcurrentMap<String, MapExternalTarget> remaining = externalTargets.get(player.getUniqueId());
        if (!selected.isBlank() && (remaining == null || !remaining.containsKey(normalizeId(selected)))) {
            state(player).clearSelection();
        }
        if (syncView) {
            sync(player, false);
        }
    }

    /**
     * 玩家在地图面板点击外部目标"导航"：转发给来源 handler（导航由来源全权执行）。
     * 同时发布 {@code axs.map.external_navigate} 事件供监测/兜底。
     *
     * @param player   发起玩家
     * @param targetId 外部目标 id
     */
    public void requestExternalNavigate(Player player, String targetId) {
        MapExternalTarget target = player == null ? null : findExternalTarget(player, targetId);
        if (player == null || !player.isOnline() || target == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.target-not-found", targetId)));
            return;
        }
        publishBusEvent("axs.map.external_navigate", player, Map.of(
            "target_id", target.targetId(),
            "source", normalizeId(target.source()),
            "world_id", normalizeId(target.worldId())
        ));
        MapNavigationHandler handler = navigationSources.get(normalizeId(target.source()));
        boolean handled = false;
        if (handler != null) {
            try {
                handled = handler.onNavigateRequest(player, target.targetId());
            } catch (Throwable e) {
                logger.warning("外部目标导航回调失败 source=" + target.source() + ": " + e.getMessage());
            }
        }
        if (!handled) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.external-nav-unavailable")));
        }
    }

    /**
     * 玩家在地图面板点击外部目标"取消"：转发给来源 handler 停止导航（目标标记保留）。
     * 同时发布 {@code axs.map.external_nav_cancel} 事件供监测。
     *
     * @param player   发起玩家
     * @param targetId 外部目标 id
     */
    public void requestExternalCancel(Player player, String targetId) {
        MapExternalTarget target = player == null ? null : findExternalTarget(player, targetId);
        if (player == null || !player.isOnline() || target == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.target-not-found", targetId)));
            return;
        }
        publishBusEvent("axs.map.external_nav_cancel", player, Map.of(
            "target_id", target.targetId(),
            "source", normalizeId(target.source()),
            "world_id", normalizeId(target.worldId())
        ));
        MapNavigationHandler handler = navigationSources.get(normalizeId(target.source()));
        if (handler != null) {
            try {
                handler.onCancelRequest(player, target.targetId());
            } catch (Throwable e) {
                logger.warning("外部目标取消回调失败 source=" + target.source() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 来源模块开始自身导航时调用：静默清除地图自身的导航（互斥），不打扰玩家。
     * 与玩家手动清除的区别：不发送"已清除导航"消息，但会发布 nav_cleared 事件。
     *
     * @param player 目标玩家
     */
    public void clearTrackBySource(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapNavigationState active = navigationStates.get(player.getUniqueId());
        boolean wasActive = active != null && active.active();
        clearTrackSilently(player);
        if (wasActive) {
            publishBusEvent("axs.map.nav_cleared", player, Map.of(
                "type", active.targetType().name().toLowerCase(Locale.ROOT),
                "id", active.targetId(),
                "world_id", active.worldId(),
                "reason", "source_took_over"
            ));
        }
        sync(player, false);
    }

    /** 发布 EventBus 事件（总线不可用时静默跳过）。 */
    private void publishBusEvent(String topic, Player player, Map<String, String> payload) {
        EventBusCapability bus;
        try {
            bus = eventBusProvider.get();
        } catch (Throwable e) {
            return;
        }
        if (bus == null) {
            return;
        }
        try {
            bus.publish(topic, player, payload);
        } catch (Throwable e) {
            logger.warning("Map EventBus 发布失败 topic=" + topic + ": " + e.getMessage());
        }
    }

    /** 发布 nav_started 事件（type: anchor/waypoint/external）。 */
    private void publishNavStarted(Player player, TargetType type, String targetId, String worldId) {
        publishBusEvent("axs.map.nav_started", player, Map.of(
            "type", type.name().toLowerCase(Locale.ROOT),
            "id", targetId,
            "world_id", normalizeId(worldId)
        ));
    }

    @Override
    public boolean allowClientPacket(Player player, String action) {
        return packetGuard == null || packetGuard.allow(player, "map", action, configuration.debug());
    }

    @Override
    public boolean beginActionToken(Player player, String action, List<String> data) {
        UUID uuid = player.getUniqueId();
        int tokenIndex = switch (action) {
            case "create_waypoint" -> 1;
            case "create_pin" -> 3;
            case "update_waypoint", "update_pin" -> 4;
            default -> 2;
        };
        String supplied = data != null && data.size() > tokenIndex ? data.get(tokenIndex) : "";
        return bridge != null && bridge.isUiOpen(player, menuUiId)
            && actionTokens.get(uuid) != null
            && actionTokens.get(uuid).equals(supplied)
            && actionTokensInFlight.add(uuid);
    }

    @Override
    public void finishActionToken(Player player, boolean committed) {
        UUID uuid = player.getUniqueId();
        actionTokensInFlight.remove(uuid);
        if (committed) {
            actionTokens.put(uuid, UUID.randomUUID().toString());
            // 重新 sync 让 UI 拿到新 token，否则 UI 端 actionToken 与服务端不一致
            // 导致后续 tokenized 按钮校验失败（如新建路径点后删除按钮无效）
            syncMenu(player, false);
        }
    }

    @Override
    public void refresh(Player player) {
        sync(player, false);
    }

    @Override
    public void openMenu(Player player, String worldId) {
        openMenuInternal(player, worldId, true);
    }

    @Override
    public void openWorld(Player player, String worldId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String resolvedWorldId = normalizeId(worldId);
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            player.sendMessage(PREFIX + message("player.world-not-configured", worldId));
            return;
        }
        state(player).selectWorld(resolvedWorldId);
        sync(player, false);
    }

    @Override
    public void selectAnchor(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null || !configuration.worlds().containsKey(anchor.worldId())) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(anchor.worldId());
        state(player).selectAnchor(anchor.id());
        sync(player, false);
    }

    @Override
    public void selectWaypoint(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(normalizeId(waypoint.world()));
        state(player).selectWaypoint(normalizeId(waypoint.waypointId()));
        sync(player, false);
    }

    @Override
    public void selectExternalTarget(Player player, String targetId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapExternalTarget target = findExternalTarget(player, targetId);
        if (target == null) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(normalizeId(target.worldId()));
        state(player).selectExternalTarget(normalizeId(target.targetId()));
        sync(player, false);
    }

    @Override
    public void selectPin(Player player, String pinId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapPin pin = findPin(player, pinId);
        if (pin == null) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(normalizeId(pin.world()));
        state(player).selectPin(normalizeId(pin.pinId()));
        sync(player, false);
    }

    @Override
    public boolean unlockAnchor(Player player, String anchorId) {
        MapOperationResult result = unlockAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
        return result.success();
    }

    @Override
    public boolean teleportAnchor(Player player, String anchorId) {
        MapOperationResult result = teleportAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
        return result.success();
    }

    @Override
    public void trackAnchor(Player player, String anchorId) {
        MapOperationResult result = trackAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
    }

    @Override
    public void trackWaypoint(Player player, String waypointId) {
        MapOperationResult result = trackWaypointInternal(player, waypointId);
        sendPlayerResult(player, result);
    }

    @Override
    public void trackExternalTarget(Player player, String targetId) {
        // 外部目标导航不由地图执行：转发给来源模块/插件的回调，来源自行接管。
        requestExternalNavigate(player, targetId);
    }

    /**
     * 外部目标"取消"入口（UI external_nav_cancel 动作）。
     */
    public void externalCancel(Player player, String targetId) {
        requestExternalCancel(player, targetId);
    }

    @Override
    public void crossWorldNavigate(Player player, String targetDesc) {
        if (player == null || !player.isOnline() || targetDesc == null) {
            return;
        }
        int sep = targetDesc.indexOf(':');
        if (sep <= 0 || sep >= targetDesc.length() - 1) {
            sync(player, false);
            return;
        }
        String type = targetDesc.substring(0, sep).toLowerCase(Locale.ROOT);
        String id = targetDesc.substring(sep + 1);
        MapOperationResult teleportResult;
        switch (type) {
            case "anchor" -> teleportResult = teleportAnchorInternal(player, id);
            case "waypoint", "external" -> teleportResult = teleportToNearestUnlockedAnchor(player, type, id);
            default -> {
                sync(player, false);
                return;
            }
        }
        if (!teleportResult.success()) {
            sendPlayerResult(player, teleportResult);
            return;
        }
        MapOperationResult trackResult = switch (type) {
            case "anchor" -> trackAnchorInternal(player, id);
            case "waypoint" -> trackWaypointInternal(player, id);
            case "external" -> {
                // 跨世界外部目标：已传送到目标世界就近锚点，导航交给来源接管
                requestExternalNavigate(player, id);
                yield MapOperationResult.success("");
            }
            default -> MapOperationResult.success("");
        };
        sendPlayerResult(player, trackResult.success()
            ? MapOperationResult.success(message("player.cross-world-navigated"))
            : trackResult);
    }

    /**
     * 在目标所在世界找最近已解锁锚点并传送（扣该锚点传送费）。
     * 用于 waypoint/external 跨世界导航的就近传送。
     */
    private MapOperationResult teleportToNearestUnlockedAnchor(Player player, String type, String id) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        double targetX;
        double targetZ;
        String worldId;
        if (type.equals("waypoint")) {
            MapWaypoint waypoint = findWaypoint(player, id);
            if (waypoint == null) {
                return MapOperationResult.failure(message("player.waypoint-not-found", id));
            }
            targetX = waypoint.x();
            targetZ = waypoint.z();
            worldId = normalizeId(waypoint.world());
        } else {
            MapExternalTarget target = findExternalTarget(player, id);
            if (target == null) {
                return MapOperationResult.failure(message("player.target-not-found", id));
            }
            targetX = target.x();
            targetZ = target.z();
            worldId = normalizeId(target.worldId());
        }
        AnchorDefinition nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (AnchorDefinition anchor : configuration.anchors().values()) {
            if (!normalizeId(anchor.worldId()).equals(worldId)) continue;
            if (!isAnchorUnlocked(player, anchor)) continue;
            double dx = anchor.x() - targetX;
            double dz = anchor.z() - targetZ;
            double dist = dx * dx + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = anchor;
            }
        }
        if (nearest == null) {
            return MapOperationResult.failure(message("player.no-unlocked-anchor-in-world"));
        }
        return teleportAnchorInternal(player, nearest.id());
    }

    @Override
    public void teleportToWorld(Player player, String worldId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String resolvedWorldId = normalizeId(worldId);
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            player.sendMessage(PREFIX + message("player.world-not-configured", worldId));
            return;
        }
        World world = Bukkit.getWorld(resolvedWorldId);
        if (world == null) {
            player.sendMessage(PREFIX + message("player.anchor-world-unloaded", resolvedWorldId));
            return;
        }
        player.teleport(world.getSpawnLocation());
        sync(player, false);
    }

    @Override
    public void clearTrack(Player player) {
        sendPlayerResult(player, clearTrackInternal(player, true));
    }

    @Override
    public void deselect(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        state(player).clearSelection();
        sync(player, false);
    }

    @Override
    public boolean createWaypoint(Player player) {
        MapOperationResult result = createWaypointInternal(player);
        sendPlayerResult(player, result);
        return result.success();
    }

    @Override
    public boolean deleteWaypoint(Player player, String waypointId) {
        MapOperationResult result = deleteWaypointInternal(player, waypointId);
        sendPlayerResult(player, result);
        return result.success();
    }

    @Override
    public void updateWaypoint(Player player, String waypointId, String name, String description) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-not-found", waypointId)));
            return;
        }
        String newName = name == null || name.isBlank() ? waypoint.name() : name;
        String newDesc = description == null ? "" : description;
        long now = System.currentTimeMillis();
        MapWaypoint updated = new MapWaypoint(
            waypoint.waypointId(), newName, newDesc, waypoint.world(),
            waypoint.x(), waypoint.y(), waypoint.z(), waypoint.createdAt(), now
        );
        try {
            repository.upsertWaypoint(player.getUniqueId(), updated);
            invalidatePlayerDataCache(player.getUniqueId());
            sync(player, false);
            sendPlayerResult(player, MapOperationResult.success(message("player.waypoint-updated", newName)));
        } catch (Exception e) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-save-failed")));
        }
    }

    @Override
    public boolean createPin(Player player, double x, double z) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!configuration.waypoints().enabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.custom-waypoints-disabled")));
            return false;
        }
        String worldId = normalizeId(player.getWorld().getName());
        if (!configuration.worlds().containsKey(worldId)) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-world-not-configured")));
            return false;
        }
        int limit = resolveWaypointLimit(player);
        List<MapPin> pins = loadPins(player);
        long countInWorld = pins.stream().filter(pin -> normalizeId(pin.world()).equals(worldId)).count();
        if (countInWorld >= limit) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
            return false;
        }
        long now = System.currentTimeMillis();
        String pinId = configuration.waypoints().idPrefix() + "pin-" + UUID.randomUUID().toString().replace("-", "");
        String name = configuration.waypoints().autoNamePrefix() + "-标记-" + (countInWorld + 1);
        MapPin pin = new MapPin(
            normalizeId(pinId), name, "", worldId, x, z, now, now
        );
        try {
            if (!repository.createPinIfUnderWorldLimit(player.getUniqueId(), pin, worldId, limit)) {
                sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
                return false;
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(worldId);
            state(player).selectPin(normalizeId(pinId));
            sync(player, false);
            sendPlayerResult(player, MapOperationResult.success(message("player.pin-created", name)));
            return true;
        } catch (Exception e) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-save-failed")));
            return false;
        }
    }

    @Override
    public boolean deletePin(Player player, String pinId) {
        MapOperationResult result = deletePinInternal(player, pinId);
        sendPlayerResult(player, result);
        return result.success();
    }

    private MapOperationResult deletePinInternal(Player player, String pinId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        MapPin pin = findPin(player, pinId);
        if (pin == null) {
            return MapOperationResult.failure(message("player.pin-not-found", pinId));
        }
        try {
            if (!repository.deletePin(player.getUniqueId(), pin.pinId())) {
                return MapOperationResult.failure(message("player.waypoint-delete-failed"));
            }
            invalidatePlayerDataCache(player.getUniqueId());
            if (state(player).selectedPinId().equalsIgnoreCase(pin.pinId())) {
                state(player).clearSelection();
            }
            sync(player, false);
            return MapOperationResult.success(message("player.pin-deleted", pin.name()));
        } catch (Exception e) {
            return MapOperationResult.failure(message("player.waypoint-delete-failed"));
        }
    }

    @Override
    public void updatePin(Player player, String pinId, String name, String description) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapPin pin = findPin(player, pinId);
        if (pin == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.pin-not-found", pinId)));
            return;
        }
        String newName = name == null || name.isBlank() ? pin.name() : name;
        String newDesc = description == null ? "" : description;
        long now = System.currentTimeMillis();
        MapPin updated = new MapPin(
            pin.pinId(), newName, newDesc, pin.world(), pin.x(), pin.z(), pin.createdAt(), now
        );
        try {
            repository.upsertPin(player.getUniqueId(), updated);
            invalidatePlayerDataCache(player.getUniqueId());
            sync(player, false);
            sendPlayerResult(player, MapOperationResult.success(message("player.pin-updated", newName)));
        } catch (Exception e) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-save-failed")));
        }
    }

    @Override
    public void shareWaypoint(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.share-disabled")));
            return;
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-not-found", waypointId)));
            return;
        }
        sweepExpiredShares();
        String code = newShareCode();
        shareCache.put(code, new SharedPoint(
            "waypoint", waypoint.name(), waypoint.description(), normalizeId(waypoint.world()),
            waypoint.x(), waypoint.y(), waypoint.z(), player.getName(),
            System.currentTimeMillis() + configuration.share().expireMinutes() * 60_000L
        ));
        broadcastShare(player, code, "waypoint", waypoint.name(), waypoint.world(),
            waypoint.x(), waypoint.y(), waypoint.z());
        sendPlayerResult(player, MapOperationResult.success(message("player.waypoint-shared", waypoint.name())));
    }

    @Override
    public void sharePin(Player player, String pinId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.share-disabled")));
            return;
        }
        MapPin pin = findPin(player, pinId);
        if (pin == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.pin-not-found", pinId)));
            return;
        }
        sweepExpiredShares();
        String code = newShareCode();
        shareCache.put(code, new SharedPoint(
            "pin", pin.name(), pin.description(), normalizeId(pin.world()),
            pin.x(), 0, pin.z(), player.getName(),
            System.currentTimeMillis() + configuration.share().expireMinutes() * 60_000L
        ));
        broadcastShare(player, code, "pin", pin.name(), pin.world(), pin.x(), 0, pin.z());
        sendPlayerResult(player, MapOperationResult.success(message("player.pin-shared", pin.name())));
    }

    /**
     * 领取分享点位（聊天卡片点击 → Packet.send('AXS_MAP', 'claim_share', code)，或 /map claim &lt;code&gt;）。
     * <p>
     * 把快照复制为点击者自己的路径点/标记点，受其世界内数量上限约束；
     * 不需要点击者与分享点位处于同一世界。
     */
    @Override
    public void claimShare(Player player, String code) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.share-disabled")));
            return;
        }
        String normalized = normalizeId(code);
        SharedPoint point = shareCache.get(normalized);
        if (point == null) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.share-not-found", code)));
            return;
        }
        if (point.expiresAt() < System.currentTimeMillis()) {
            shareCache.remove(normalized);
            sendPlayerResult(player, MapOperationResult.failure(message("player.share-expired")));
            return;
        }
        if ("pin".equals(point.type())) {
            claimPinShare(player, point);
        } else {
            claimWaypointShare(player, point);
        }
    }

    private void claimWaypointShare(Player player, SharedPoint point) {
        if (!configuration.waypoints().enabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.custom-waypoints-disabled")));
            return;
        }
        if (!configuration.worlds().containsKey(point.world())) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-world-not-configured")));
            return;
        }
        int limit = resolveWaypointLimit(player);
        long countInWorld = loadWaypoints(player).stream()
            .filter(waypoint -> normalizeId(waypoint.world()).equals(point.world())).count();
        if (countInWorld >= limit) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
            return;
        }
        long now = System.currentTimeMillis();
        String waypointId = configuration.waypoints().idPrefix() + UUID.randomUUID().toString().replace("-", "");
        MapWaypoint waypoint = new MapWaypoint(
            normalizeId(waypointId), point.name(), point.description(),
            point.world(), point.x(), point.y(), point.z(), now, now
        );
        try {
            if (!repository.createWaypointIfUnderWorldLimit(player.getUniqueId(), waypoint, point.world(), limit)) {
                sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
                return;
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(point.world());
            state(player).selectWaypoint(waypoint.waypointId());
            sync(player, false);
            sendPlayerResult(player, MapOperationResult.success(
                message("player.waypoint-claimed", point.name(), point.senderName())));
        } catch (Exception e) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-save-failed")));
        }
    }

    private void claimPinShare(Player player, SharedPoint point) {
        if (!configuration.waypoints().enabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.custom-waypoints-disabled")));
            return;
        }
        if (!configuration.worlds().containsKey(point.world())) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-world-not-configured")));
            return;
        }
        int limit = resolveWaypointLimit(player);
        long countInWorld = loadPins(player).stream()
            .filter(pin -> normalizeId(pin.world()).equals(point.world())).count();
        if (countInWorld >= limit) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
            return;
        }
        long now = System.currentTimeMillis();
        String pinId = configuration.waypoints().idPrefix() + "pin-" + UUID.randomUUID().toString().replace("-", "");
        MapPin pin = new MapPin(
            normalizeId(pinId), point.name(), point.description(),
            point.world(), point.x(), point.z(), now, now
        );
        try {
            if (!repository.createPinIfUnderWorldLimit(player.getUniqueId(), pin, point.world(), limit)) {
                sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
                return;
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(point.world());
            state(player).selectPin(pin.pinId());
            sync(player, false);
            sendPlayerResult(player, MapOperationResult.success(
                message("player.pin-claimed", point.name(), point.senderName())));
        } catch (Exception e) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-save-failed")));
        }
    }

    /**
     * 向全服在线玩家广播分享消息。
     * <p>
     * {@code share.card-id} 配置非空 → 聊天卡片（ArcartX/chat_card/&lt;card-id&gt;.yml）；
     * 留空 → 纯文本降级广播，附带可点击的"[点击领取]"（RUN_COMMAND 执行 /map claim &lt;code&gt;）。
     */
    private void broadcastShare(Player sender, String code, String type, String name,
                                String worldId, double x, double y, double z) {
        WorldDefinition definition = configuration.worlds().get(normalizeId(worldId));
        String worldName = definition == null ? worldId : resolveDisplayName(definition);
        String coords = "waypoint".equals(type)
            ? (long) x + ", " + (long) y + ", " + (long) z
            : (long) x + ", " + (long) z;
        String cardId = configuration.share() == null ? "" : configuration.share().cardId();
        if (cardId != null && !cardId.isBlank() && bridge != null) {
            Map<String, String> payload = Map.of(
                "type", type,
                "title", name == null ? "" : name,
                "sender", sender.getName(),
                "world", worldName,
                "coords", coords,
                "code", code,
                "expire", String.valueOf(configuration.share().expireMinutes())
            );
            for (Player online : Bukkit.getOnlinePlayers()) {
                bridge.sendChatCard(online, cardId, payload);
            }
            return;
        }
        broadcastShareText(sender, code, type, name, worldName, coords);
    }

    /** 无聊天卡片时的纯文本广播降级：主文本 + 可点击的"[点击领取]"组件。 */
    private void broadcastShareText(Player sender, String code, String type, String name,
                                    String worldName, String coords) {
        String typeName = "pin".equals(type)
            ? message("player.share-type-pin")
            : message("player.share-type-waypoint");
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(
            ChatColor.translateAlternateColorCodes('&', message("player.share-broadcast",
                sender.getName(), typeName, name, worldName, coords))));
        TextComponent claimButton = new TextComponent(TextComponent.fromLegacyText(
            ChatColor.translateAlternateColorCodes('&', message("player.share-claim-button"))));
        claimButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/map claim " + code));
        claimButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(message("player.share-claim-hover", "/map claim " + code)).create()));
        line.addExtra(claimButton);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.spigot().sendMessage(line);
        }
    }

    private boolean shareEnabled() {
        return configuration.share() == null || configuration.share().enabled();
    }

    /** 生成不与现有分享冲突的 8 位随机码。 */
    private String newShareCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (shareCache.containsKey(code));
        return code;
    }

    /** 惰性清扫过期分享（在分享/领取入口调用，无需独立定时任务）。 */
    private void sweepExpiredShares() {
        long now = System.currentTimeMillis();
        shareCache.values().removeIf(point -> point.expiresAt() < now);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewStates.remove(uuid);
        navigationStates.remove(uuid);
        externalTargets.remove(uuid);
        openedHudPlayers.remove(uuid);
        playerDataCache.remove(uuid);
        actionTokens.remove(uuid);
        actionTokensInFlight.remove(uuid);
    }

    /**
     * 外部插件禁用时自动注销其注册的导航回调，避免回调进已卸载的插件类。
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Set<String> owned = navigationSourceOwners.remove(event.getPlugin());
        if (owned != null) {
            for (String source : owned) {
                navigationSources.remove(source);
            }
        }
    }

    /**
     * 玩家跨世界时把地图选中世界切到新世界，并刷新 HUD 显示。
     * <p>
     * {@code selectedWorldId} 是粘性状态：玩家之前选过的世界会一直保留，
     * 导致传送到别的世界后 HUD 仍显示旧世界名（例如人在 boss_world 却显示
     * bskyblock_world）。此处在世界切换时自动跟随；新世界未配置地图时保持现状。
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        String newWorldId = normalizeId(player.getWorld().getName());
        if (!configuration.worlds().containsKey(newWorldId)) {
            return;
        }
        MapPlayerViewState viewState = state(player);
        if (newWorldId.equals(viewState.selectedWorldId())) {
            return;
        }
        viewState.selectWorld(newWorldId);
        if (viewState.hudVisible()) {
            syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
        }
    }

    private MapOperationResult openMenuInternal(Player player, String worldId, boolean pushInitPacket) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        String resolvedWorldId = resolveOpenWorld(player, worldId);
        if (resolvedWorldId.isBlank()) {
            return MapOperationResult.failure(message("player.map-unavailable"));
        }
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            return MapOperationResult.failure(message("player.world-not-configured", worldId));
        }
        state(player).selectWorld(resolvedWorldId);
        bridge.openUi(player, menuUiId);
        syncMenu(player, pushInitPacket);
        return MapOperationResult.success(message("player.map-opened"));
    }

    private MapOperationResult setHudInternal(Player player, boolean visible, boolean announce) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        String resolvedWorldId = resolveOpenWorld(player, "");
        if (visible && resolvedWorldId.isBlank()) {
            return MapOperationResult.failure(message("player.no-display-world"));
        }
        if (!resolvedWorldId.isBlank()) {
            state(player).selectWorld(resolvedWorldId);
        }
        state(player).setHudVisible(visible);
        if (!visible) {
            openedHudPlayers.remove(player.getUniqueId());
            // 先发一个 visible=false 的包让 UI 的 update 触发 deactivate，再关闭 UI
            MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
            bridge.sendPacket(player, hudUiId, "update", buildHudPayload(snapshot.hud()));
            bridge.closeUi(player, hudUiId);
            return announce ? MapOperationResult.success(message("player.hud-disabled")) : MapOperationResult.success("");
        }
        syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
        return announce ? MapOperationResult.success(message("player.hud-enabled")) : MapOperationResult.success("");
    }

    private MapOperationResult unlockAnchorInternal(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null) {
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        if (isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure(message("player.anchor-already-unlocked"));
        }
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        PreparedItemConsumption preparedItems = prepareItemConsumption(player, anchor.unlockItems());
        if (!preparedItems.success()) {
            return MapOperationResult.failure(preparedItems.message());
        }
        long unlockTime = System.currentTimeMillis();
        try {
            if (!repository.tryUnlockAnchor(player.getUniqueId(), anchor.id(), unlockTime)) {
                return MapOperationResult.failure(message("player.anchor-already-unlocked"));
            }
        } catch (Exception exception) {
            return MapOperationResult.failure(message("player.anchor-unlock-save-failed"));
        }
        CurrencyChargeReceipt currencyReceipt = chargeCurrencies(player, anchor.unlockCurrencies());
        if (!currencyReceipt.success()) {
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return MapOperationResult.failure(currencyReceipt.message());
        }
        MapOperationResult itemResult = applyItemConsumption(player, preparedItems);
        if (!itemResult.success()) {
            rollbackCurrencies(player, currencyReceipt.successfulCharges());
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return itemResult;
        }
        invalidatePlayerDataCache(player.getUniqueId());
        sync(player, false);
        return MapOperationResult.success(message("player.anchor-unlocked", anchor.displayName()));
    }

    private void rollbackUnlock(UUID playerUuid, String anchorId) {
        try {
            repository.removeUnlock(playerUuid, anchorId);
            invalidatePlayerDataCache(playerUuid);
        } catch (Exception ignored) {
        }
    }

    private MapOperationResult teleportAnchorInternal(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null) {
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        if (!isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure(message("player.anchor-locked"));
        }
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        World world = Bukkit.getWorld(anchor.worldId());
        if (world == null) {
            return MapOperationResult.failure(message("player.anchor-world-unloaded", anchor.worldId()));
        }
        CurrencyChargeReceipt currencyReceipt = chargeCurrencies(player, anchor.teleportCurrencies());
        if (!currencyReceipt.success()) {
            return MapOperationResult.failure(currencyReceipt.message());
        }
        boolean teleported = player.teleport(
            new Location(world, anchor.x(), anchor.y(), anchor.z(), player.getLocation().getYaw(), player.getLocation().getPitch())
        );
        if (!teleported) {
            rollbackCurrencies(player, currencyReceipt.successfulCharges());
            return MapOperationResult.failure(message("player.teleport-failed"));
        }
        sync(player, false);
        return MapOperationResult.success(message("player.teleported-to-anchor", anchor.displayName()));
    }

    private MapOperationResult trackAnchorInternal(Player player, String anchorId) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure(message("player.navigation-disabled"));
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (player == null || !player.isOnline() || anchor == null) {
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure(message("player.waypoint-unavailable"));
        }
        clearTrackSilently(player);
        String waypointId = configuration.navigation().anchorIdPrefix() + sanitize(anchor.id());
        String styleId = waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypointStyleId(),
            "Map"
        );
        if (!waypointBridge.addWaypoint(player, waypointId, anchor.displayName(), styleId, anchor.x(), anchor.y(), anchor.z())) {
            return MapOperationResult.failure(message("player.anchor-navigation-failed"));
        }
        navigationStates.put(
            player.getUniqueId(),
            new MapNavigationState(true, TargetType.ANCHOR, anchor.worldId(), anchor.id(), waypointId, anchor.displayName(), "map")
        );
        if (markerService != null) {
            markerService.showMarker(player, anchor.worldId(), anchor.x(), anchor.y(), anchor.z());
        }
        publishNavStarted(player, TargetType.ANCHOR, anchor.id(), anchor.worldId());
        sync(player, false);
        return MapOperationResult.success(message("player.anchor-navigation-started", anchor.displayName()));
    }

    private MapOperationResult trackWaypointInternal(Player player, String waypointId) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure(message("player.navigation-disabled"));
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (player == null || !player.isOnline() || waypoint == null) {
            return MapOperationResult.failure(message("player.waypoint-not-found", waypointId));
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure(message("player.waypoint-unavailable"));
        }
        clearTrackSilently(player);
        String runtimeWaypointId = configuration.navigation().waypointIdPrefix() + sanitize(waypoint.waypointId());
        String styleId = waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypointStyleId(),
            "Map"
        );
        if (!waypointBridge.addWaypoint(player, runtimeWaypointId, waypoint.name(), styleId, waypoint.x(), waypoint.y(), waypoint.z())) {
            return MapOperationResult.failure(message("player.waypoint-navigation-failed"));
        }
        navigationStates.put(
            player.getUniqueId(),
            new MapNavigationState(
                true,
                TargetType.WAYPOINT,
                normalizeId(waypoint.world()),
                waypoint.waypointId(),
                runtimeWaypointId,
                waypoint.name(),
                "map"
            )
        );
        if (markerService != null) {
            markerService.showMarker(player, normalizeId(waypoint.world()), waypoint.x(), waypoint.y(), waypoint.z());
        }
        publishNavStarted(player, TargetType.WAYPOINT, waypoint.waypointId(), waypoint.world());
        sync(player, false);
        return MapOperationResult.success(message("player.waypoint-navigation-started", waypoint.name()));
    }

    private MapOperationResult clearTrackInternal(Player player, boolean sync) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        MapNavigationState state = navigationStates.get(player.getUniqueId());
        if (state == null || !state.active()) {
            if (sync) {
                sync(player, false);
            }
            return MapOperationResult.success(message("player.no-active-navigation"));
        }
        if (!waypointBridge.removeWaypoint(player, state.waypointId(), false)) {
            return MapOperationResult.failure(message("player.clear-navigation-failed"));
        }
        if (markerService != null) {
            markerService.hideMarker(player);
        }
        navigationStates.remove(player.getUniqueId());
        publishBusEvent("axs.map.nav_cleared", player, Map.of(
            "type", state.targetType().name().toLowerCase(Locale.ROOT),
            "id", state.targetId(),
            "world_id", state.worldId(),
            "reason", "cleared"
        ));
        if (sync) {
            sync(player, false);
        }
        return MapOperationResult.success(message("player.navigation-cleared"));
    }

    private MapOperationResult createWaypointInternal(Player player) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        if (!configuration.waypoints().enabled()) {
            return MapOperationResult.failure(message("player.custom-waypoints-disabled"));
        }
        String worldId = normalizeId(player.getWorld().getName());
        if (!configuration.worlds().containsKey(worldId)) {
            return MapOperationResult.failure(message("player.waypoint-world-not-configured"));
        }
        List<MapWaypoint> waypoints = loadWaypoints(player);
        int limit = resolveWaypointLimit(player);
        long countInWorld = waypoints.stream().filter(waypoint -> normalizeId(waypoint.world()).equals(worldId)).count();
        if (countInWorld >= limit) {
            return MapOperationResult.failure(message("player.waypoint-limit-reached", limit));
        }
        long now = System.currentTimeMillis();
        String waypointId = configuration.waypoints().idPrefix() + UUID.randomUUID().toString().replace("-", "");
        String name = configuration.waypoints().autoNamePrefix() + "-" + (countInWorld + 1);
        Location location = player.getLocation();
        MapWaypoint waypoint = new MapWaypoint(
            normalizeId(waypointId),
            name,
            "",
            worldId,
            location.getX(),
            location.getY(),
            location.getZ(),
            now,
            now
        );
        try {
            if (!repository.createWaypointIfUnderWorldLimit(
                player.getUniqueId(), waypoint, worldId, limit
            )) {
                return MapOperationResult.failure(message("player.waypoint-limit-reached", limit));
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(worldId);
            state(player).selectWaypoint(waypoint.waypointId());
            sync(player, false);
            return MapOperationResult.success(message("player.waypoint-created", name));
        } catch (Exception exception) {
            return MapOperationResult.failure(message("player.waypoint-save-failed"));
        }
    }

    private MapOperationResult deleteWaypointInternal(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            return MapOperationResult.failure(message("player.waypoint-not-found", waypointId));
        }
        try {
            if (!repository.deleteWaypoint(player.getUniqueId(), waypoint.waypointId())) {
                return MapOperationResult.failure(message("player.waypoint-delete-failed"));
            }
            invalidatePlayerDataCache(player.getUniqueId());
            if (navigationStates.getOrDefault(player.getUniqueId(), MapNavigationState.none()).matchesWaypoint(waypoint.waypointId())) {
                clearTrackSilently(player);
            }
            if (state(player).selectedWaypointId().equalsIgnoreCase(waypoint.waypointId())) {
                state(player).clearSelection();
            }
            sync(player, false);
            return MapOperationResult.success(message("player.waypoint-deleted", waypoint.name()));
        } catch (Exception exception) {
            return MapOperationResult.failure(message("player.waypoint-delete-failed"));
        }
    }

    private MapOperationResult validateAnchorAccess(Player player, AnchorDefinition anchor) {
        if (!anchor.permission().isBlank() && !player.hasPermission(anchor.permission())) {
            return MapOperationResult.failure(message("player.anchor-no-permission"));
        }
        if (!configuration.worlds().containsKey(anchor.worldId())) {
            return MapOperationResult.failure(message("player.anchor-world-not-configured", anchor.worldId()));
        }
        return MapOperationResult.success("");
    }

    private CurrencyChargeReceipt chargeCurrencies(Player player, List<CurrencyCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return CurrencyChargeReceipt.success(List.of());
        }
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                return CurrencyChargeReceipt.failure(message("player.currency-bridge-unavailable", cost.currencyId()));
            }
            if (bridge.balance(player).compareTo(cost.amount()) < 0) {
                return CurrencyChargeReceipt.failure(message("player.insufficient-balance", cost.currencyId()));
            }
        }
        List<CurrencyCost> successfulCharges = new ArrayList<>();
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure(message("player.currency-bridge-unavailable", cost.currencyId()));
            }
            CurrencyTransactionResult result = bridge.withdraw(player, cost.amount());
            if (!result.success()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure(result.message().isBlank() ? message("player.charge-failed", cost.currencyId()) : result.message());
            }
            successfulCharges.add(cost);
        }
        return CurrencyChargeReceipt.success(successfulCharges);
    }

    private void rollbackCurrencies(Player player, List<CurrencyCost> successfulCharges) {
        for (CurrencyCost cost : successfulCharges) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, cost.amount());
            }
        }
    }

    private PreparedItemConsumption prepareItemConsumption(Player player, List<ItemCost> itemCosts) {
        if (itemCosts == null || itemCosts.isEmpty()) {
            return PreparedItemConsumption.empty();
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] snapshot = cloneContents(contents);
        int[] remaining = new int[contents.length];
        int[] deductions = new int[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            remaining[slot] = contents[slot] == null ? 0 : contents[slot].getAmount();
        }
        for (ItemCost cost : itemCosts) {
            int need = Math.max(1, cost.amount());
            for (int slot = 0; slot < contents.length && need > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir() || remaining[slot] <= 0 || !itemMatcherSupport.matches(cost.matcher(), stack)) {
                    continue;
                }
                int take = Math.min(need, remaining[slot]);
                remaining[slot] -= take;
                deductions[slot] += take;
                need -= take;
            }
            if (need > 0) {
                return PreparedItemConsumption.failure(message("player.missing-unlock-items"));
            }
        }
        return PreparedItemConsumption.success(snapshot, deductions);
    }

    private MapOperationResult applyItemConsumption(Player player, PreparedItemConsumption prepared) {
        if (prepared == null || !prepared.success() || !prepared.hasChanges()) {
            return MapOperationResult.success("");
        }
        ItemStack[] updatedContents = cloneContents(prepared.originalContents());
        for (int slot = 0; slot < updatedContents.length; slot++) {
            if (prepared.deductions()[slot] <= 0 || updatedContents[slot] == null) {
                continue;
            }
            int updatedAmount = updatedContents[slot].getAmount() - prepared.deductions()[slot];
            if (updatedAmount <= 0) {
                updatedContents[slot] = null;
            } else {
                updatedContents[slot].setAmount(updatedAmount);
            }
        }
        player.getInventory().setStorageContents(updatedContents);
        player.updateInventory();
        return MapOperationResult.success("");
    }

    private void rollbackItemConsumption(Player player, PreparedItemConsumption prepared) {
        if (player == null || prepared == null || !prepared.success() || !prepared.hasChanges()) {
            return;
        }
        player.getInventory().setStorageContents(cloneContents(prepared.originalContents()));
        player.updateInventory();
    }

    private void sync(Player player, boolean initPacket) {
        syncMenu(player, initPacket);
        syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
    }

    private void syncMenu(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
        if (initPacket) actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        Map<String, Object> payload = buildMenuPayload(snapshot.menu());
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));
        bridge.sendPacket(player, menuUiId, initPacket ? "init" : "update", payload);
    }

    private void syncHud(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
        if (!snapshot.hud().visible()) {
            bridge.closeUi(player, hudUiId);
            openedHudPlayers.remove(player.getUniqueId());
            return;
        }
        if (initPacket) {
            bridge.openUi(player, hudUiId);
            openedHudPlayers.add(player.getUniqueId());
        }
        bridge.sendPacket(player, hudUiId, initPacket ? "init" : "update", buildHudPayload(snapshot.hud()));
    }

    private MapSnapshotBuilder.BuildResult buildSnapshot(Player player) {
        MapPlayerViewState state = state(player);
        List<MapSnapshotBuilder.WorldView> worlds = new ArrayList<>();
        for (WorldDefinition world : configuration.worlds().values()) {
            worlds.add(
                new MapSnapshotBuilder.WorldView(
                    world.id(),
                    resolveDisplayName(world),
                    resolveDimension(world)
                )
            );
        }

        Set<String> unlockedAnchors = loadUnlockedAnchors(player);
        List<MapSnapshotBuilder.AnchorView> anchors = new ArrayList<>();
        for (AnchorDefinition anchor : configuration.anchors().values()) {
            boolean unlocked = unlockedAnchors.contains(anchor.id());
            anchors.add(
                new MapSnapshotBuilder.AnchorView(
                    anchor.id(),
                    anchor.worldId(),
                    anchor.displayName(),
                    anchor.description(),
                    anchor.x(),
                    anchor.y(),
                    anchor.z(),
                    unlocked,
                    formatUnlockCostText(anchor),
                    formatTeleportCostText(anchor),
                    !unlocked,
                    unlocked,
                    anchor.sortOrder(),
                    toRegionPoints(anchor.lightRegions()),
                    anchor.lightRadius()
                )
            );
        }

        Location location = player == null ? null : player.getLocation();
        return snapshotBuilder.build(
            new MapSnapshotBuilder.BuildInput(
                configuration.client().packetId(),
                worlds,
                anchors,
                loadWaypoints(player),
                loadExternalTargets(player),
                loadPins(player),
                state,
                navigationStates.getOrDefault(player.getUniqueId(), MapNavigationState.none()),
                location == null ? null : new MapSnapshotBuilder.PlayerPosition(location.getX(), location.getY(), location.getZ(), location.getYaw()),
                location == null ? "" : normalizeId(location.getWorld().getName()),
                resolveWaypointLimit(player),
                configuration.navigation().enabled() && waypointRuntimeReady(),
                configuration.waypoints().enabled()
            )
        );
    }

    /**
     * 解析世界维度 key：config 显式配置优先，否则按 Bukkit world.getKey() 自动获取。
     * 供客户端 mod 将 worldId 匹配到本地 MC 维度（overworld/the_nether 等）。
     */
    /** PolygonRegion 列表 → 顶点列表列表（SnapshotBuilder 内部传递格式）。 */
    private static List<List<int[]>> toRegionPoints(List<PolygonRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        List<List<int[]>> out = new ArrayList<>(regions.size());
        for (PolygonRegion r : regions) {
            out.add(r.points());
        }
        return List.copyOf(out);
    }

    private static String resolveDimension(WorldDefinition world) {
        World bukkitWorld = Bukkit.getWorld(world.id());
        if (bukkitWorld == null) {
            // 世界文件名可能含大写（如 DANTIAO），normalizeId 转小写后
            // Bukkit.getWorld 精确匹配不到，遍历做 case-insensitive 匹配
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(world.id())) {
                    bukkitWorld = w;
                    break;
                }
            }
        }
        return bukkitWorld == null ? "" : bukkitWorld.getKey().toString();
    }

    /** MV alias 缓存：worldId → colourlessAlias。启动时一次性补空，避免每次 buildSnapshot 调 MV API。 */
    private final java.util.Map<String, String> mvDisplayNames = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 解析世界显示名：配置填了就用配置，留空则从 Multiverse-Core 的 colourless alias 补。
     * MV 不存在或世界未在 MV 注册时，降级用 world id。
     */
    private String resolveDisplayName(WorldDefinition world) {
        String configured = world.displayName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String cached = mvDisplayNames.get(world.id());
        if (cached != null) {
            return cached;
        }
        String resolved = resolveDisplayNameFromMV(world.id());
        mvDisplayNames.put(world.id(), resolved);
        return resolved;
    }

    /** 从 Multiverse-Core API 读取世界的去色码 alias。MV 未加载时降级用 world id。 */
    private String resolveDisplayNameFromMV(String worldId) {
        if (!org.mvplugins.multiverse.core.MultiverseCoreApi.isLoaded()) {
            return worldId;
        }
        try {
            org.mvplugins.multiverse.core.MultiverseCoreApi api = org.mvplugins.multiverse.core.MultiverseCoreApi.get();
            org.mvplugins.multiverse.core.world.WorldManager wm = api.getWorldManager();
            for (org.mvplugins.multiverse.core.world.LoadedMultiverseWorld mvWorld : wm.getLoadedWorlds()) {
                if (worldId.equals(mvWorld.getName())) {
                    String alias = mvWorld.getColourlessAlias();
                    return (alias != null && !alias.isBlank()) ? alias : worldId;
                }
            }
        } catch (Exception e) {
            logger.warning("从 Multiverse-Core 读取世界 " + worldId + " 的 alias 失败: " + e.getMessage());
        }
        return worldId;
    }

    private Map<String, Object> buildMenuPayload(MapSnapshotBuilder.MenuSnapshot snapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", snapshot.packetId());
        payload.put("selectedWorldId", snapshot.selectedWorldId());
        payload.put("selectedWorldName", snapshot.selectedWorldName());
        payload.put("playerWorldId", snapshot.playerWorldId());

        LinkedHashMap<String, Object> worldRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.worldRows().size(); i++) {
            MapSnapshotBuilder.WorldRow row = snapshot.worldRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("displayName", row.displayName());
            entry.put("dimension", row.dimension());
            entry.put("selected", row.selected());
            worldRows.put(Integer.toString(i), entry);
        }
        payload.put("worldRows", worldRows);

        LinkedHashMap<String, Object> anchorRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.anchorRows().size(); i++) {
            MapSnapshotBuilder.AnchorRow row = snapshot.anchorRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("displayName", row.displayName());
            entry.put("description", row.description());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("unlocked", row.unlocked());
            entry.put("unlockCostText", row.unlockCostText());
            entry.put("teleportCostText", row.teleportCostText());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            anchorRows.put(Integer.toString(i), entry);
        }
        payload.put("anchorRows", anchorRows);

        LinkedHashMap<String, Object> waypointRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.waypointRows().size(); i++) {
            MapSnapshotBuilder.WaypointRow row = snapshot.waypointRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("name", row.name());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            waypointRows.put(Integer.toString(i), entry);
        }
        payload.put("waypointRows", waypointRows);

        LinkedHashMap<String, Object> externalRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.externalTargetRows().size(); i++) {
            MapSnapshotBuilder.ExternalTargetRow row = snapshot.externalTargetRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("source", row.source());
            entry.put("name", row.name());
            entry.put("description", row.description());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("icon", row.iconId());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            externalRows.put(Integer.toString(i), entry);
        }
        payload.put("externalTargetRows", externalRows);

        LinkedHashMap<String, Object> pinRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.pinRows().size(); i++) {
            MapSnapshotBuilder.PinRow row = snapshot.pinRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("name", row.name());
            entry.put("description", row.description());
            entry.put("x", row.x());
            entry.put("z", row.z());
            entry.put("selected", row.selected());
            pinRows.put(Integer.toString(i), entry);
        }
        payload.put("pinRows", pinRows);

        payload.put("waypointLimit", snapshot.waypointLimit());
        payload.put("waypointCount", snapshot.waypointCount());
        payload.put("canCreateWaypoint", snapshot.canCreateWaypoint());
        payload.put("shareEnabled", shareEnabled());
        payload.put("clearTrackVisible", snapshot.clearTrackVisible());
        payload.put("trackingText", snapshot.trackingText());
        payload.put("detailSelectedType", snapshot.detail().selectedType());
        payload.put("detailSelectedId", snapshot.detail().selectedId());
        payload.put("detailTitle", snapshot.detail().title());
        payload.put("detailDescription", snapshot.detail().description());
        payload.put("detailUnlocked", snapshot.detail().unlocked());
        payload.put("detailUnlockCostText", snapshot.detail().unlockCostText());
        payload.put("detailTeleportCostText", snapshot.detail().teleportCostText());
        payload.put("detailExternalTarget", snapshot.detail().externalTarget());
        payload.put("detailCanUnlock", snapshot.detail().canUnlock());
        payload.put("detailCanTeleport", snapshot.detail().canTeleport());
        payload.put("detailCanTrackAnchor", snapshot.detail().canTrackAnchor());
        payload.put("detailCanTrackWaypoint", snapshot.detail().canTrackWaypoint());
        payload.put("detailCanTrackExternal", snapshot.detail().canTrackExternal());
        payload.put("detailCanDeleteWaypoint", snapshot.detail().canDeleteWaypoint());
        payload.put("detailCanDeletePin", snapshot.detail().canDeletePin());
        payload.put("detailCanCreateWaypoint", snapshot.detail().canCreateWaypoint());
        payload.put("detailClearTrackVisible", snapshot.detail().clearTrackVisible());
        payload.put("detailTrackingText", snapshot.detail().trackingText());
        payload.put("detailNavigated", snapshot.detail().navigated());
        payload.put("litEnabled", snapshot.litEnabled());
        payload.put("litRegions", snapshot.litRegions());
        payload.put("maxWorldCount", worldRows.size());
        payload.put("maxAnchorCount", anchorRows.size());
        payload.put("maxWaypointCount", waypointRows.size());
        payload.put("maxExternalCount", externalRows.size());
        payload.put("maxPinCount", pinRows.size());
        return payload;
    }

    private Map<String, Object> buildHudPayload(MapSnapshotBuilder.HudSnapshot snapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", snapshot.packetId());
        payload.put("visible", snapshot.visible());
        payload.put("worldId", snapshot.worldId());
        payload.put("dimension", snapshot.dimension());
        payload.put("playerYaw", snapshot.playerYaw());
        payload.put("trackingText", snapshot.trackingText());
        payload.put("litEnabled", snapshot.litEnabled());
        payload.put("litRegions", snapshot.litRegions());
        return payload;
    }


    private String formatUnlockCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.unlockCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        for (ItemCost cost : anchor.unlockItems()) {
            parts.add("物品条件 x" + cost.amount());
        }
        return parts.isEmpty() ? "免费解锁" : String.join(" + ", parts);
    }

    private String formatTeleportCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.teleportCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        return parts.isEmpty() ? "免费传送" : String.join(" + ", parts);
    }

    private String formatCurrencyCost(CurrencyCost cost) {
        CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
        String displayName = bridge == null || bridge.definition() == null ? cost.currencyId() : bridge.definition().displayName();
        return displayName + " x" + cost.amount().setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private Set<String> loadUnlockedAnchors(Player player) {
        if (player == null) {
            return Set.of();
        }
        return loadPlayerData(player.getUniqueId()).unlockedAnchors();
    }

    private void ensureDefaultUnlocks(Player player) {
        if (player == null || !player.isOnline() || !defaultUnlocksEnsured.add(player.getUniqueId())) {
            return;
        }
        try {
            Set<String> existing = new LinkedHashSet<>(loadUnlockedAnchors(player));
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (AnchorDefinition anchor : configuration.anchors().values()) {
                AnchorUnlock unlock = anchor.unlock();
                boolean granted = unlock.defaultUnlock()
                    || (unlock.onPermission()
                        && !anchor.permission().isBlank()
                        && player.hasPermission(anchor.permission()));
                if (granted && existing.add(anchor.id())) {
                    repository.unlockAnchor(player.getUniqueId(), anchor.id(), now);
                    changed = true;
                }
            }
            if (changed) {
                invalidatePlayerDataCache(player.getUniqueId());
            }
        } catch (Exception ignored) {
        }
    }

    private int resolveWaypointLimit(Player player) {
        int limit = configuration.waypoints().defaultMaxCount();
        if (player == null) {
            return limit;
        }
        for (MapModuleConfiguration.WaypointLimit entry : configuration.waypoints().limits()) {
            if (entry.permission().isBlank() || player.hasPermission(entry.permission())) {
                limit = Math.max(limit, entry.maxCount());
            }
        }
        return limit;
    }

    private List<MapWaypoint> loadWaypoints(Player player) {
        if (player == null) {
            return List.of();
        }
        return loadPlayerData(player.getUniqueId()).waypoints();
    }

    private CachedPlayerMapData loadPlayerData(UUID playerUuid) {
        CachedPlayerMapData cached = playerDataCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        try {
            Set<String> anchors = repository.loadUnlockedAnchors(playerUuid);
            List<MapWaypoint> waypoints = repository.loadWaypoints(playerUuid);
            CachedPlayerMapData fresh = new CachedPlayerMapData(Set.copyOf(anchors), List.copyOf(waypoints));
            playerDataCache.putIfAbsent(playerUuid, fresh);
            return fresh;
        } catch (Exception exception) {
            // DB 异常时不缓存空结果，避免暂时故障后一直读到脏数据
            return new CachedPlayerMapData(Set.of(), List.of());
        }
    }

    private void invalidatePlayerDataCache(UUID playerUuid) {
        if (playerUuid != null) {
            playerDataCache.remove(playerUuid);
        }
    }

    private List<MapExternalTarget> loadExternalTargets(Player player) {
        if (player == null) {
            return List.of();
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        return List.copyOf(state.values());
    }

    private MapWaypoint findWaypoint(Player player, String waypointId) {
        String normalized = normalizeId(waypointId);
        for (MapWaypoint waypoint : loadWaypoints(player)) {
            if (normalizeId(waypoint.waypointId()).equals(normalized)) {
                return waypoint;
            }
        }
        return null;
    }

    private MapExternalTarget findExternalTarget(Player player, String targetId) {
        if (player == null) {
            return null;
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        return state == null ? null : state.get(normalizeId(targetId));
    }

    private boolean isAnchorUnlocked(Player player, AnchorDefinition anchor) {
        if (anchor == null) {
            return false;
        }
        return loadUnlockedAnchors(player).contains(anchor.id());
    }

    private MapPin findPin(Player player, String pinId) {
        String normalized = normalizeId(pinId);
        for (MapPin pin : loadPins(player)) {
            if (normalizeId(pin.pinId()).equals(normalized)) {
                return pin;
            }
        }
        return null;
    }

    private List<MapPin> loadPins(Player player) {
        if (player == null) {
            return List.of();
        }
        try {
            return repository.loadPins(player.getUniqueId());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveOpenWorld(Player player, String requestedWorldId) {
        String requested = normalizeId(requestedWorldId);
        if (!requested.isBlank()) {
            return configuration.worlds().containsKey(requested) ? requested : "";
        }
        // 玩家当前世界优先：打开大地图默认显示当前世界，而不是上次关闭前
        // 残留的 selectedWorldId（粘性状态只在玩家当前世界未配置地图时回退）
        if (player != null && player.getWorld() != null) {
            String currentWorldId = normalizeId(player.getWorld().getName());
            if (configuration.worlds().containsKey(currentWorldId)) {
                return currentWorldId;
            }
        }
        MapPlayerViewState viewState = state(player);
        if (!viewState.selectedWorldId().isBlank() && configuration.worlds().containsKey(viewState.selectedWorldId())) {
            return viewState.selectedWorldId();
        }
        return configuration.worlds().isEmpty() ? "" : configuration.worlds().keySet().iterator().next();
    }

    private MapPlayerViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new MapPlayerViewState());
    }

    private void clearTrackSilently(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapNavigationState state = navigationStates.remove(player.getUniqueId());
        if (state != null && state.active()) {
            waypointBridge.removeWaypoint(player, state.waypointId(), false);
        }
        if (markerService != null) {
            markerService.hideMarker(player);
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents == null ? 0 : contents.length];
        if (contents == null) {
            return clone;
        }
        for (int index = 0; index < contents.length; index++) {
            clone[index] = contents[index] == null ? null : contents[index].clone();
        }
        return clone;
    }

    private void sendPlayerResult(Player player, MapOperationResult result) {
        if (player == null || result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static String sanitize(String value) {
        return normalizeId(value).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record CurrencyChargeReceipt(
        boolean success,
        String message,
        List<CurrencyCost> successfulCharges
    ) {

        private static CurrencyChargeReceipt success(List<CurrencyCost> successfulCharges) {
            return new CurrencyChargeReceipt(true, "", List.copyOf(successfulCharges));
        }

        private static CurrencyChargeReceipt failure(String message) {
            return new CurrencyChargeReceipt(false, message, List.of());
        }
    }

    private record PreparedItemConsumption(
        boolean success,
        String message,
        ItemStack[] originalContents,
        int[] deductions
    ) {

        private static PreparedItemConsumption empty() {
            return new PreparedItemConsumption(true, "", new ItemStack[0], new int[0]);
        }

        private static PreparedItemConsumption success(ItemStack[] originalContents, int[] deductions) {
            return new PreparedItemConsumption(true, "", originalContents, deductions.clone());
        }

        private static PreparedItemConsumption failure(String message) {
            return new PreparedItemConsumption(false, message, new ItemStack[0], new int[0]);
        }

        private boolean hasChanges() {
            for (int deduction : deductions) {
                if (deduction > 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
