package xuanmo.arcartxsuite.map.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
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
import org.bukkit.event.player.PlayerJoinEvent;
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
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigationDelegate;
import xuanmo.arcartxsuite.api.capability.MapNavigationHandler;
import xuanmo.arcartxsuite.api.capability.MapNavigationRequest;
import xuanmo.arcartxsuite.api.util.NavigationMarkerService;
import xuanmo.arcartxsuite.api.util.NavigationTarget;
import xuanmo.arcartxsuite.api.util.NavigationTargetService;
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
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.map.crossserver.MapCrossServerService;
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

    /** 玩家侧全部公开入口的使用权限（/map、全局按键、UI 数据包、地形请求）。 */
    private static final String PERMISSION_USE = "arcartxsuite.map.use";

    /** 路径点/标记点名称上限（与 UI 输入框 length 对齐）。 */
    private static final int MAX_NAME_LENGTH = 64;
    /** 路径点/标记点描述上限（与 UI 输入框 length 对齐）。 */
    private static final int MAX_DESC_LENGTH = 256;
    /** axsmap:request 同玩家最小请求间隔（防伪造客户端刷推送队列）。 */
    private static final long TERRAIN_REQUEST_INTERVAL_NANOS = 3_000_000_000L;
    /** 玩家设置回传通道（客户端→服务端，UTF key + UTF value）。 */
    public static final String CHANNEL_SETTING = "axsmap:setting";

    final JavaPlugin plugin;
    final Logger logger;
    private final PacketGuardAPI packetGuard;
    final MapModuleConfiguration configuration;
    final MapRepository repository;
    final PacketBridgeAPI bridge;
    private final String menuUiId;
    private final String hudUiId;
    private final MapSnapshotBuilder snapshotBuilder = new MapSnapshotBuilder();
    private final MapUiPacketHandler uiPacketHandler;
    final CurrencyBridgeAPI currencyBridgeManager;
    private final ItemSourceRegistry itemSourceRegistry;
    final ItemMatcherAPI itemMatcherSupport;
    private final WaypointBridgeAPI waypointBridge;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge;
    private final MessageProvider messages;
    private final java.util.function.Supplier<EventBusCapability> eventBusProvider;
    /** Essentials 模块查询能力（读取 /setspawn 出生点，MV 不存在时的二级来源）。 */
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.EssentialsQueryable> essentialsProvider;
    /** 宿主跨服 API（{@code cross-server.enabled=false} 或未注入时为 null，跨服功能整体禁用）。 */
    private final CrossServerAPI crossServerApi;
    /** 跨服地图服务（目录广播/地形中继/传送握手）；cross-server.enabled=false 时为 null。 */
    private MapCrossServerService crossServer;
    private NavigationMarkerService markerService;
    /** 贴图路径提示服务（WorldTexture，与模型标记互补、独立开关）。 */
    private xuanmo.arcartxsuite.api.util.NavigationTextureMarkerService textureMarkerService;
    /** 共享导航目标编排服务（diff 推送 / 到达滞回判定 / 跨世界抑制），navigation.enabled 时创建。 */
    private NavigationTargetService targetService;
    /** 地形预加载推送服务（axsmap:terrain / axsmap:request 通道）。 */
    private MapTerrainPushService terrainPush;
    final ConcurrentMap<UUID, MapPlayerViewState> viewStates = new ConcurrentHashMap<>();
    final MapPreloadService preload;
    final MapShareService share;
    final MapChargeService charge;
    /** 玩家 → (导航键 type:id → 导航状态)，同一玩家可并行多个导航目标。 */
    private final ConcurrentMap<UUID, LinkedHashMap<String, MapNavigationState>> navigationStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>> externalTargets = new ConcurrentHashMap<>();
    /** 外部目标导航回调：source（normalize 后）→ handler，由来源模块/插件注册。 */
    private final ConcurrentMap<String, MapNavigationHandler> navigationSources = new ConcurrentHashMap<>();
    /** 全局导航委托：注册后所有导航点击先经它；null = 无委托走本地/来源逻辑。 */
    private volatile MapNavigationDelegate navigationDelegate;
    private volatile Plugin navigationDelegateOwner;
    /** owner 插件 → 已注册的 source 列表，用于插件禁用时自动注销回调。 */
    private final ConcurrentMap<Plugin, Set<String>> navigationSourceOwners = new ConcurrentHashMap<>();
    private final Set<UUID> openedHudPlayers = ConcurrentHashMap.newKeySet();
    /** 玩家 → 当前 HUD 会话号：每次开启小地图自增，随包下发给客户端 mod
     *  做锚点会话门控——同名 HUD 多副本并存时，旧会话副本的锚点被忽略。 */
    private final ConcurrentMap<UUID, Integer> hudSessions = new ConcurrentHashMap<>();
    private final Set<UUID> defaultUnlocksEnsured = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, CachedPlayerMapData> playerDataCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();
    /** 玩家 → 上次 axsmap:request 受理时间（节流，超出间隔的请求直接丢弃）。 */
    private final ConcurrentMap<UUID, Long> terrainRequestAt = new ConcurrentHashMap<>();
    /** 玩家 → 地图界面设置（key=value，服务端留存）。进服异步加载后常驻内存。 */
    private final ConcurrentMap<UUID, Map<String, String>> playerSettings = new ConcurrentHashMap<>();
    /** 正在异步加载设置的玩家（防 join 风暴重复查库）。 */
    private final Set<UUID> playerSettingsLoading = ConcurrentHashMap.newKeySet();
    /** 允许客户端回传的设置键白名单——非白名单键直接丢弃，防伪造客户端塞脏数据。 */
    private static final Set<String> PLAYER_SETTING_KEYS = Set.of(
        "radar", "radarNames", "radarPlayers", "radarHostile", "radarPassive",
        "radarNameScale", "hudZoom", "caveDepth",
        "hudShape", "hudPosition", "hudSize");

    /** 分享码 → 点位快照（内存缓存，过期/重启/重载后失效）。 */

    private record CachedPlayerMapData(Set<String> unlockedAnchors, List<MapWaypoint> waypoints) {
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
            itemSourceRegistry, itemMatcherSupport, currencyBridgeManager, waypointBridge, null, null, null, null, null, null);
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
        java.util.function.Supplier<EventBusCapability> eventBusProvider,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.EssentialsQueryable> essentialsProvider,
        xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge,
        CrossServerAPI crossServerApi
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
        this.share = new MapShareService(this);
        this.charge = new MapChargeService(this);
        this.preload = new MapPreloadService(this);
        this.currencyBridgeManager = currencyBridgeManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.waypointBridge = waypointBridge;
        this.npcBridge = npcBridge;
        this.messages = messages;
        this.eventBusProvider = eventBusProvider != null ? eventBusProvider : () -> null;
        this.essentialsProvider = essentialsProvider != null ? essentialsProvider : () -> null;
        this.worldTextureBridge = worldTextureBridge;
        this.crossServerApi = crossServerApi;
    }

    String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    /**
     * 启动地图服务，初始化存储、导航桥接与标记服务，并注册事件监听。
     *
     * @throws Exception 初始化失败时抛出
     */
    public void start() throws Exception {
        repository.initialize();
        preload.loadPreloadVersionBumps();
        if (configuration.navigation().enabled()) {
            waypointBridge.initialize("Map 导航");
        }
        if (configuration.navigation().modelMarker().enabled() && npcBridge != null) {
            npcBridge.setDebug(configuration.debug());
            npcBridge.initialize();
            markerService = new NavigationMarkerService(
                plugin, npcBridge, configuration.navigation().modelMarker(), logger,
                configuration.debug(), "map_path_"
            );
            markerService.start();
        }
        if (configuration.navigation().textureMarker().enabled()) {
            if (worldTextureBridge == null) {
                logger.warning("Map: 贴图导航已启用但 WorldTexture 桥接为 null，贴图提示将跳过。");
            } else {
                textureMarkerService = new xuanmo.arcartxsuite.api.util.NavigationTextureMarkerService(
                    plugin, worldTextureBridge, configuration.navigation().textureMarker(), logger,
                    configuration.debug(), "map_tex_"
                );
                textureMarkerService.start();
            }
        }
        if (configuration.navigation().enabled()) {
            targetService = new NavigationTargetService(
                plugin, waypointBridge, markerService, logger,
                new NavigationTargetService.Config(
                    Math.max(1, configuration.navigation().refreshTicks()),
                    Math.max(0, configuration.navigation().worldHintIntervalSeconds()) * 1000L,
                    configuration.navigation().rearmBuffer(),
                    1_500L,
                    configuration.navigation().waypoint().enabled()
                )
            );
            if (textureMarkerService != null) {
                targetService.addRouteRenderer(textureMarkerService);
            }
            targetService.setWorldHintHandler((p, target, resolved) ->
                p.sendMessage(PREFIX + message("player.navigation-other-world",
                    resolved.getWorld() == null ? target.world() : resolved.getWorld().getName())));
            targetService.setArriveHandler((p, target, resolved) ->
                p.sendMessage(PREFIX + message("player.navigation-arrived", target.title())));
            targetService.start();
        }
        terrainPush = new MapTerrainPushService(plugin, logger);
        terrainPush.start();
        // 跨服地图：目录广播 + 远程世界缓存 + 地形中继 + 传送握手。
        // 宿主 cross-server 未启用时 channel.isActive()=false，服务自降级为纯广播空转。
        if (configuration.crossServer().enabled() && crossServerApi != null) {
            crossServer = new MapCrossServerService(
                plugin, logger, configuration.crossServer(), crossServerApi,
                terrainPush, new MapCrossServerHandler(this));
            crossServer.start();
            logger.info("[Map] 跨服地图已启用，节点 " + crossServer.nodeId()
                + "，远程节点 " + crossServer.remoteNodeCount() + " 个");
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
        if (targetService != null) {
            targetService.shutdown();
            targetService = null;
        }
        if (markerService != null) {
            markerService.shutdown();
            markerService = null;
        }
        if (textureMarkerService != null) {
            textureMarkerService.shutdown();
            textureMarkerService = null;
        }
        if (crossServer != null) {
            crossServer.shutdown();
            crossServer = null;
        }
        if (terrainPush != null) {
            terrainPush.shutdown();
            terrainPush = null;
        }
        repository.shutdown();
        waypointBridge.shutdown();
        viewStates.clear();
        navigationStates.clear();
        externalTargets.clear();
        openedHudPlayers.clear();
        hudSessions.clear();
        defaultUnlocksEnsured.clear();
        playerDataCache.clear();
        share.clearAll();
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
        Bukkit.getScheduler().runTaskLater(
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
     * 获取指定世界的已配置锚点 ID 列表（按锚点所属世界过滤，非 id 子串匹配）。
     *
     * @param worldId 世界 ID（大小写不敏感）
     * @return 锚点 ID 列表
     */
    public List<String> configuredAnchorIds(String worldId) {
        String filter = normalizeId(worldId);
        List<String> ids = new ArrayList<>();
        for (AnchorDefinition anchor : configuration.anchors().values()) {
            if (normalizeId(anchor.worldId()).equals(filter)) {
                ids.add(anchor.id());
            }
        }
        return ids;
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
        return (int) navigationStates.values().stream().filter(states -> !states.isEmpty()).count();
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
        // HUD 开关同 use 权限门控（命令层已查过一次，这里兜底 keybind 入口）
        if (!player.hasPermission(PERMISSION_USE)) {
            return MapOperationResult.failure(message("player.no-permission"));
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
        refreshExternalNavigation(player, target);
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
            refreshExternalNavigation(player, target);
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
        if (markExternalNavigated(player, targetId, navigated)) {
            sync(player, false);
        }
    }

    /** 静默更新外部目标 navigated 标志（不发视图同步）。返回是否有变更。 */
    private boolean markExternalNavigated(Player player, String targetId, boolean navigated) {
        if (player == null || targetId == null || targetId.isBlank()) {
            return false;
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        String key = normalizeId(targetId);
        MapExternalTarget target = state.get(key);
        if (target == null || target.navigated() == navigated) {
            return false;
        }
        state.put(key, target.withNavigated(navigated));
        return true;
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
            refreshExternalNavigation(player, target);
        }
        // 被移除的外部目标若存在地图代导航态，一并回收
        Map<String, MapNavigationState> navs = navigationMap(player);
        if (navs != null && !navs.isEmpty()) {
            for (Map.Entry<String, MapNavigationState> entry : new ArrayList<>(navs.entrySet())) {
                MapNavigationState nav = entry.getValue();
                if (nav.targetType() == TargetType.EXTERNAL
                    && normalizeId(nav.source()).equals(normalizedSource)
                    && !keep.contains(normalizeId(nav.targetId()))) {
                    removeNavigation(player, entry.getKey(), nav);
                }
            }
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
     * 注册全局导航委托：注册后所有"导航"点击（锚点/路径点/标记点/外部目标）
     * 先回调 {@link MapNavigationDelegate#onNavigateRequest}，返回 false 才交还原处理方。
     * 全服单委托，重复注册由后者覆盖；插件禁用时自动注销。
     *
     * @param owner    归属插件
     * @param delegate 委托实现
     */
    public void setNavigationDelegate(Plugin owner, MapNavigationDelegate delegate) {
        if (owner == null || delegate == null) {
            return;
        }
        this.navigationDelegate = delegate;
        this.navigationDelegateOwner = owner;
    }

    /**
     * 注销导航委托（仅当当前委托属于该插件）。
     *
     * @param owner 注册时使用的插件实例
     */
    public void clearNavigationDelegate(Plugin owner) {
        if (owner != null && owner == this.navigationDelegateOwner) {
            this.navigationDelegate = null;
            this.navigationDelegateOwner = null;
        }
    }

    /**
     * 当前是否注册了全局导航委托。
     */
    public boolean hasNavigationDelegate() {
        return navigationDelegate != null;
    }

    /**
     * 模块 reload 跨 service 实例续传的外部运行时状态。
     * <p>
     * reload 会重建整个 MapService，若不留存，第三方来源推送的外部目标、
     * 导航回调与全局委托会随 {@link #shutdown()} 全部丢失且来源无感知——
     * 由 MapModule 在 stopService/startService 之间暂存并回灌。
     */
    public record ExternalRuntime(
        ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>> externalTargets,
        ConcurrentMap<String, MapNavigationHandler> navigationSources,
        ConcurrentMap<Plugin, Set<String>> navigationSourceOwners,
        MapNavigationDelegate navigationDelegate,
        Plugin navigationDelegateOwner
    ) {
    }

    /**
     * 导出外部运行时状态（深拷贝）。必须在 {@link #shutdown()} 之前调用——
     * shutdown 会清空 externalTargets。
     */
    public ExternalRuntime exportExternalRuntime() {
        ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>> targets = new ConcurrentHashMap<>();
        externalTargets.forEach((uuid, map) -> targets.put(uuid, new ConcurrentHashMap<>(map)));
        ConcurrentMap<Plugin, Set<String>> owners = new ConcurrentHashMap<>();
        navigationSourceOwners.forEach((owner, sources) -> {
            Set<String> copy = ConcurrentHashMap.newKeySet(sources.size());
            copy.addAll(sources);
            owners.put(owner, copy);
        });
        return new ExternalRuntime(targets, new ConcurrentHashMap<>(navigationSources),
            owners, navigationDelegate, navigationDelegateOwner);
    }

    /**
     * 回灌外部运行时状态（reload 后调用）：只恢复仍在线玩家的外部目标，
     * 导航回调/委托全量恢复（handler 是来源插件持有的对象，本身仍有效），
     * 随后对恢复了目标的玩家重推视图，并向全体在线玩家发布
     * {@code axs.map.ready}——来源可订阅该事件决定是否重推最新数据。
     */
    public void importExternalRuntime(ExternalRuntime state) {
        if (state == null) {
            return;
        }
        state.externalTargets().forEach((uuid, map) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !map.isEmpty()) {
                externalTargets.put(uuid, new ConcurrentHashMap<>(map));
            }
        });
        navigationSources.putAll(state.navigationSources());
        state.navigationSourceOwners().forEach((owner, sources) ->
            navigationSourceOwners.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(sources));
        if (state.navigationDelegate() != null) {
            navigationDelegate = state.navigationDelegate();
            navigationDelegateOwner = state.navigationDelegateOwner();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (externalTargets.containsKey(player.getUniqueId())) {
                sync(player, false);
            }
            publishBusEvent("axs.map.ready", player, Map.of());
        }
    }

    /** 导航是否可用：本地导航可用，或已注册全局委托。 */
    private boolean navigationAvailable() {
        return (configuration.navigation().enabled() && waypointRuntimeReady()) || navigationDelegate != null;
    }

    /** 委托态来源前缀：MapNavigationState.source 以此前缀标识由委托接管。 */
    private static final String DELEGATE_SOURCE_PREFIX = "delegate:";

    /** 地图代导航外部目标的客户端路标 ID 前缀。 */
    private static final String EXTERNAL_NAV_WAYPOINT_PREFIX = "AXS-map-ext-";

    /** 玩家导航状态表（不存在时返回 null）。 */
    private LinkedHashMap<String, MapNavigationState> navigationMap(Player player) {
        return player == null ? null : navigationStates.get(player.getUniqueId());
    }

    /** 玩家导航状态表（不存在时创建）。 */
    private LinkedHashMap<String, MapNavigationState> navigationMapOrCreate(Player player) {
        return navigationStates.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
    }

    /** 导航键：type + ":" + 目标 id（同一玩家内的唯一导航目标标识）。 */
    private static String navKey(MapNavigationState.TargetType type, String targetId) {
        return type.name().toLowerCase(Locale.ROOT) + ":" + normalizeId(targetId);
    }

    /** 查找玩家指定类型/目标的导航状态。 */
    private MapNavigationState findNavigation(Player player, MapNavigationState.TargetType type, String targetId) {
        Map<String, MapNavigationState> states = navigationMap(player);
        return states == null ? null : states.get(navKey(type, targetId));
    }

    /** 移除单个导航状态（含委托通知 / 路标与标记清除）。 */
    private void removeNavigation(Player player, String key, MapNavigationState state) {
        Map<String, MapNavigationState> states = navigationMap(player);
        if (states != null) {
            states.remove(key);
            if (states.isEmpty()) {
                navigationStates.remove(player.getUniqueId());
            }
        }
        if (isDelegatedNav(state)) {
            notifyDelegateCancel(player, state);
        } else if (targetService != null) {
            targetService.removeTarget(player, state.waypointId());
        } else if (state != null && !state.waypointId().isBlank()) {
            waypointBridge.removeWaypoint(player, state.waypointId(), false);
        }
        if (state != null && state.targetType() == TargetType.EXTERNAL) {
            // 外部目标行按钮回弹：地图代导航态/委托态移除时静默清 navigated
            markExternalNavigated(player, state.targetId(), false);
        }
    }

    private boolean isDelegatedNav(MapNavigationState state) {
        return state != null && normalizeId(state.source()).startsWith(DELEGATE_SOURCE_PREFIX);
    }

    /**
     * 把导航点击先交给全局委托：返回 true 表示已接管。
     * 接管时记录委托导航态（不建客户端路径点，视觉由委托方负责）。
     */
    private boolean tryDelegateNavigate(Player player, MapNavigationRequest request) {
        MapNavigationDelegate delegate = navigationDelegate;
        if (delegate == null) {
            return false;
        }
        boolean handled;
        try {
            handled = delegate.onNavigateRequest(player, request);
        } catch (Throwable e) {
            logger.warning("导航委托 onNavigateRequest 失败 owner="
                + (navigationDelegateOwner == null ? "?" : navigationDelegateOwner.getName()) + ": " + e.getMessage());
            return false;
        }
        if (!handled) {
            return false;
        }
        Plugin owner = navigationDelegateOwner;
        navigationMapOrCreate(player).put(
            navKey(targetTypeOf(request.targetType()), request.targetId()),
            new MapNavigationState(
                true, targetTypeOf(request.targetType()), request.worldId(), request.targetId(),
                "", request.title(), DELEGATE_SOURCE_PREFIX + (owner == null ? "unknown" : owner.getName())
            )
        );
        publishNavStarted(player, targetTypeOf(request.targetType()), request.targetId(), request.worldId());
        return true;
    }

    /**
     * 委托态被终止时回调委托方（玩家取消 / 互斥清除 / 来源接管）。
     * 请求按当前状态尽力重解坐标。
     */
    private void notifyDelegateCancel(Player player, MapNavigationState state) {
        MapNavigationDelegate delegate = navigationDelegate;
        if (delegate == null || state == null) {
            return;
        }
        try {
            delegate.onCancelRequest(player, rebuildRequest(player, state));
        } catch (Throwable e) {
            logger.warning("导航委托 onCancelRequest 失败: " + e.getMessage());
        }
    }

    /** 由记录的导航态重建请求（坐标尽力重解；目标已删时用 0）。 */
    private MapNavigationRequest rebuildRequest(Player player, MapNavigationState state) {
        double x = 0, y = 0, z = 0;
        String type = requestTypeOf(state.targetType());
        String source = MapNavigationRequest.SOURCE_MAP;
        switch (state.targetType()) {
            case ANCHOR -> {
                AnchorDefinition anchor = configuration.anchor(state.targetId());
                if (anchor != null) { x = anchor.x(); y = anchor.y(); z = anchor.z(); }
            }
            case WAYPOINT -> {
                MapWaypoint waypoint = findWaypoint(player, state.targetId());
                if (waypoint != null) { x = waypoint.x(); y = waypoint.y(); z = waypoint.z(); }
            }
            case EXTERNAL -> {
                MapExternalTarget target = findExternalTarget(player, state.targetId());
                if (target != null) {
                    x = target.x(); y = target.y(); z = target.z();
                    source = normalizeId(target.source());
                }
            }
            default -> { }
        }
        return new MapNavigationRequest(type, state.targetId(), state.worldId(), state.label(), x, y, z, source);
    }

    private static MapNavigationState.TargetType targetTypeOf(String requestType) {
        return switch (requestType == null ? "" : requestType) {
            case MapNavigationRequest.TYPE_ANCHOR -> MapNavigationState.TargetType.ANCHOR;
            case MapNavigationRequest.TYPE_WAYPOINT -> MapNavigationState.TargetType.WAYPOINT;
            case MapNavigationRequest.TYPE_EXTERNAL -> MapNavigationState.TargetType.EXTERNAL;
            default -> MapNavigationState.TargetType.NONE;
        };
    }

    private static String requestTypeOf(MapNavigationState.TargetType type) {
        return switch (type) {
            case ANCHOR -> MapNavigationRequest.TYPE_ANCHOR;
            case WAYPOINT -> MapNavigationRequest.TYPE_WAYPOINT;
            case EXTERNAL -> MapNavigationRequest.TYPE_EXTERNAL;
            default -> "none";
        };
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
        MapNavigationState navigationState = findNavigation(player, TargetType.EXTERNAL, targetId);
        if (navigationState != null) {
            removeNavigation(player, navKey(TargetType.EXTERNAL, targetId), navigationState);
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
        Map<String, MapNavigationState> navigationMap = navigationMap(player);
        String normalizedSource = normalizeId(source);
        if (navigationMap != null && !navigationMap.isEmpty()) {
            for (Map.Entry<String, MapNavigationState> entry : new ArrayList<>(navigationMap.entrySet())) {
                MapNavigationState nav = entry.getValue();
                if (nav.targetType() == TargetType.EXTERNAL
                    && (normalizedSource.isBlank() || normalizeId(nav.source()).equals(normalizedSource))) {
                    removeNavigation(player, entry.getKey(), nav);
                }
            }
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
        // 全局导航委托优先：接管后地图记委托态（互斥），按钮切换由委托方
        // 调 setExternalNavigated 回推
        if (tryDelegateNavigate(player, new MapNavigationRequest(
            MapNavigationRequest.TYPE_EXTERNAL, target.targetId(), normalizeId(target.worldId()),
            target.title(), target.x(), target.y(), target.z(), normalizeId(target.source())
        ))) {
            sync(player, false);
            return;
        }
        // 目标声明由地图代导航：直接走内置导航栈，不回调来源
        if (target.useMapNavigation()) {
            sendPlayerResult(player, trackExternalInternal(player, target));
            return;
        }
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
        // 若该目标的导航是全局委托接管的，取消回调给委托而非来源
        MapNavigationState active = findNavigation(player, TargetType.EXTERNAL, targetId);
        if (isDelegatedNav(active)) {
            removeNavigation(player, navKey(TargetType.EXTERNAL, targetId), active);
            sync(player, false);
            return;
        }
        // 地图代导航态：本地移除，不回调来源
        if (active != null) {
            removeNavigation(player, navKey(TargetType.EXTERNAL, targetId), active);
            publishBusEvent("axs.map.nav_cleared", player, Map.of(
                "type", "external",
                "id", active.targetId(),
                "world_id", active.worldId(),
                "reason", "cleared"
            ));
            sendPlayerResult(player, MapOperationResult.success(message("player.navigation-removed")));
            sync(player, false);
            return;
        }
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
        Map<String, MapNavigationState> states = navigationMap(player);
        boolean wasActive = states != null && !states.isEmpty();
        if (wasActive) {
            for (MapNavigationState nav : new ArrayList<>(states.values())) {
                publishBusEvent("axs.map.nav_cleared", player, Map.of(
                    "type", nav.targetType().name().toLowerCase(Locale.ROOT),
                    "id", nav.targetId(),
                    "world_id", nav.worldId(),
                    "reason", "source_took_over"
                ));
            }
        }
        clearTrackSilently(player);
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
            case "create_pin" -> 4; // x, z, worldId, token
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

    /**
     * HUD UI 加载/重载完成后主动上报（load/open 触发器发 {@code hud_ready}）。
     * 换发新会话号并重推状态：新 UI 副本立即获得有效会话，旧副本的锚点随
     * 会话号过期被 mod 忽略，实现 UI 重建后的自愈。
     */
    @Override
    public void hudReady(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (state(player).hudVisible()) {
            hudSessions.merge(player.getUniqueId(), 1, Integer::sum);
        }
        syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
    }

    @Override
    public void openMenu(Player player, String worldId) {
        // 全局按键/UI open_menu 直达此入口，use 权限在此兜底（静默拒绝）
        if (player == null || !player.hasPermission(PERMISSION_USE)) {
            return;
        }
        openMenuInternal(player, worldId, true);
    }

    @Override
    public void openWorld(Player player, String worldId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String resolvedWorldId = normalizeId(worldId);
        // 玩家当前所在的未配置世界（世界列表里的"当前世界"条目）允许选中——
        // 无服务端数据，客户端按本地维度渲染临时世界地形
        if (resolvedWorldId.equals(normalizeId(player.getWorld().getName()))) {
            state(player).selectWorld(resolvedWorldId);
            sync(player, false);
            return;
        }
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            // 复合 id（nodeId/worldId）：远程世界同样允许打开浏览
            MapCrossServerService.RemoteWorldView remote =
                crossServer == null ? null : crossServer.remoteWorld(resolvedWorldId);
            if (remote == null) {
                player.sendMessage(PREFIX + message("player.world-not-configured", worldId));
                return;
            }
            state(player).selectWorld(remote.compositeId());
            sync(player, false);
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
        if (anchor == null) {
            // 复合锚点 id（nodeId/anchorId）：选中远程锚点并切换到其远程世界
            MapCrossServerService.RemoteAnchorView remote =
                crossServer == null ? null : crossServer.remoteAnchor(normalizeId(anchorId));
            if (remote != null) {
                state(player).selectWorld(remote.compositeWorldId());
                state(player).selectAnchor(remote.compositeId());
            }
            sync(player, false);
            return;
        }
        if (!configuration.worlds().containsKey(anchor.worldId())) {
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
    public boolean crossWorldNavigate(Player player, String targetDesc) {
        if (player == null || !player.isOnline() || targetDesc == null) {
            return false;
        }
        int sep = targetDesc.indexOf(':');
        if (sep <= 0 || sep >= targetDesc.length() - 1) {
            sync(player, false);
            return false;
        }
        String type = targetDesc.substring(0, sep).toLowerCase(Locale.ROOT);
        String id = targetDesc.substring(sep + 1);
        MapOperationResult teleportResult;
        switch (type) {
            case "anchor" -> teleportResult = teleportAnchorInternal(player, id);
            case "waypoint", "external" -> teleportResult = teleportToNearestUnlockedAnchor(player, type, id);
            default -> {
                sync(player, false);
                return false;
            }
        }
        if (!teleportResult.success()) {
            sendPlayerResult(player, teleportResult);
            return false;
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
        return trackResult.success();
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
    public boolean teleportToWorld(Player player, String worldId) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        String resolvedWorldId = normalizeId(worldId);
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            // 复合世界 id → 跨服传送到该远程世界出生点
            MapCrossServerService.RemoteWorldView remote =
                crossServer == null ? null : crossServer.remoteWorld(resolvedWorldId);
            if (remote == null) {
                player.sendMessage(PREFIX + message("player.world-not-configured", worldId));
                return false;
            }
            sendPlayerResult(player,
                crossServer.requestTeleport(player, remote.nodeId(), remote.worldId(), ""));
            return true;
        }
        Location spawn = resolveWorldSpawn(resolveWorld(resolvedWorldId));
        if (spawn == null) {
            player.sendMessage(PREFIX + message("player.anchor-world-unloaded", resolvedWorldId));
            return false;
        }
        player.teleport(spawn);
        sync(player, false);
        return true;
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
        String newName = name == null || name.isBlank() ? waypoint.name() : clipText(name, MAX_NAME_LENGTH);
        String newDesc = description == null ? "" : clipText(description, MAX_DESC_LENGTH);
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
    public boolean createPin(Player player, double x, double z, String worldId) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        // NaN/Infinity 等非法坐标直接拒绝（客户端 parseDouble 不挡这两种）
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            sync(player, false);
            return false;
        }
        if (!configuration.waypoints().enabled()) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.custom-waypoints-disabled")));
            return false;
        }
        // 世界归属 = 客户端正在浏览的世界（所见即所标，pin 允许任意世界任意点）。
        // 包内世界未配置说明参数被篡改，明确失败而非静默落到玩家世界
        String resolvedWorldId = normalizeId(worldId);
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-world-not-configured")));
            return false;
        }
        int limit = resolveWaypointLimit(player);
        List<MapPin> pins = loadPins(player);
        long countInWorld = pins.stream().filter(pin -> normalizeId(pin.world()).equals(resolvedWorldId)).count();
        if (countInWorld >= limit) {
            sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
            return false;
        }
        long now = System.currentTimeMillis();
        String pinId = configuration.waypoints().idPrefix() + "pin-" + UUID.randomUUID().toString().replace("-", "");
        String name = configuration.waypoints().autoNamePrefix() + "-" + (countInWorld + 1);
        MapPin pin = new MapPin(
            normalizeId(pinId), name, "", resolvedWorldId, x, z, now, now
        );
        try {
            if (!repository.createPinIfUnderWorldLimit(player.getUniqueId(), pin, resolvedWorldId, limit)) {
                sendPlayerResult(player, MapOperationResult.failure(message("player.waypoint-limit-reached", limit)));
                return false;
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(resolvedWorldId);
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
        String newName = name == null || name.isBlank() ? pin.name() : clipText(name, MAX_NAME_LENGTH);
        String newDesc = description == null ? "" : clipText(description, MAX_DESC_LENGTH);
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
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewStates.remove(uuid);
        navigationStates.remove(uuid);
        if (targetService != null) {
            targetService.handleQuit(event.getPlayer());
        }
        if (markerService != null) {
            markerService.hideMarker(event.getPlayer());
        }
        if (textureMarkerService != null) {
            textureMarkerService.hideMarker(event.getPlayer());
        }
        externalTargets.remove(uuid);
        openedHudPlayers.remove(uuid);
        hudSessions.remove(uuid);
        playerDataCache.remove(uuid);
        actionTokens.remove(uuid);
        actionTokensInFlight.remove(uuid);
        terrainRequestAt.remove(uuid);
        playerSettings.remove(uuid);
        playerSettingsLoading.remove(uuid);
        if (crossServer != null) {
            crossServer.onPlayerQuit(uuid);
        }
        // 移除"默认解锁已确保"标记：重登重新尝试，解锁失败的锚点有补救机会
        defaultUnlocksEnsured.remove(uuid);
    }

    /**
     * 玩家进服：消费该玩家在目标服上挂起的跨服落地传送（若有）。
     * 事件在玩家正式加入时触发，crossServer 内部按 arrivalDelayTicks 延迟执行。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (crossServer != null && player != null) {
            crossServer.onPlayerJoin(player);
        }
        if (player != null) {
            // 预热玩家设置缓存——HUD init 包需要带上 settings 字典，异步加载避免阻塞登录
            ensurePlayerSettings(player.getUniqueId());
        }
    }

    /**
     * 玩家地图设置缓存（uuid → key→value）。未加载时触发异步查库，立即返回空 Map。
     * 首次下发可能为空——加载完成后下一次 HUD init（重载/重进）即补全。
     */
    Map<String, String> playerSettings(UUID uuid) {
        Map<String, String> cached = playerSettings.get(uuid);
        if (cached != null) {
            return cached;
        }
        ensurePlayerSettings(uuid);
        return Map.of();
    }

    private void ensurePlayerSettings(UUID uuid) {
        if (uuid == null || playerSettings.containsKey(uuid) || !playerSettingsLoading.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                playerSettings.put(uuid, new java.util.concurrent.ConcurrentHashMap<>(
                    repository.loadPlayerSettings(uuid)));
            } catch (Exception e) {
                logger.fine("[Map] 加载玩家设置失败 uuid=" + uuid + ": " + e.getMessage());
            } finally {
                playerSettingsLoading.remove(uuid);
            }
        });
    }

    /**
     * 客户端 {@code axsmap:setting} 包处理（载荷 = UTF key + UTF value）。
     * 只更新本玩家设置：写内存缓存 + 异步落库；键走白名单、值限长，防伪造客户端刷脏数据。
     */
    public void handleSettingPacket(Player player, byte[] message) {
        if (player == null || !player.isOnline() || message == null) {
            return;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }
        String key;
        String value;
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(message))) {
            key = in.readUTF();
            value = in.readUTF();
        } catch (Exception e) {
            return;
        }
        if (!PLAYER_SETTING_KEYS.contains(key) || value.length() > 64) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<String, String> settings = playerSettings.computeIfAbsent(
            uuid, k -> new java.util.concurrent.ConcurrentHashMap<>());
        if (value.equals(settings.put(key, value))) {
            return; // 值没变——不落库
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.savePlayerSetting(uuid, key, value);
            } catch (Exception e) {
                logger.fine("[Map] 保存玩家设置失败 uuid=" + uuid + " key=" + key + ": " + e.getMessage());
            }
        });
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
        if (navigationDelegateOwner == event.getPlugin()) {
            navigationDelegate = null;
            navigationDelegateOwner = null;
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
        MapPlayerViewState viewState = state(player);
        // 未配置世界不写 selectedWorldId（保持粘性选中），但 HUD 仍要同步——
        // 换到未配置/临时世界时下发 worldId="" + visible，客户端切本地维度渲染
        if (configuration.worlds().containsKey(newWorldId)
            && !newWorldId.equals(viewState.selectedWorldId())) {
            viewState.selectWorld(newWorldId);
        }
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
        // 未配置世界仅放行"玩家当前所在世界"（本地渲染回退）；
        // 伪造/浏览其它未配置世界的请求照常拒绝
        if (!configuration.worlds().containsKey(resolvedWorldId)
            && !resolvedWorldId.equals(normalizeId(player.getWorld().getName()))) {
            return MapOperationResult.failure(message("player.world-not-configured", worldId));
        }
        if (bridge == null) {
            // PacketBridge 初始化失败（ArcartX 未就绪）：菜单打不开，明确失败而非 NPE
            return MapOperationResult.failure(message("player.map-unavailable"));
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
            // 不 closeUi：HUD defaultOpen 常驻打开，显隐全走数据包。
            // visible=false 让 UI tick 的 deactivateHud 即时停 mod 渲染并注销会话；
            // 若每次都 closeUi+openUi 会在客户端叠出同名 HUD 副本互相打架。
            MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
            sendHudPacket(player, "update", snapshot.hud());
            return announce ? MapOperationResult.success(message("player.hud-disabled")) : MapOperationResult.success("");
        }
        // 开启新会话：会话号单调递增，客户端 mod 只认最新会话的锚点，
        // 旧副本（漏收关闭包的僵尸 UI）的锚点被直接忽略。
        hudSessions.merge(player.getUniqueId(), 1, Integer::sum);
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
        // 三维距离门槛：unlock.radius > 0 时玩家须站在锚点 radius 格内
        // （同世界 + 含 Y 的欧氏距离），才允许手动解锁
        MapOperationResult rangeResult = validateUnlockRange(player, anchor);
        if (!rangeResult.success()) {
            return rangeResult;
        }
        MapChargeService.PreparedItemConsumption preparedItems = charge.prepareItemConsumption(player, anchor.unlockItems());
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
        MapChargeService.CurrencyChargeReceipt currencyReceipt = charge.chargeCurrencies(player, anchor.unlockCurrencies());
        if (!currencyReceipt.success()) {
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return MapOperationResult.failure(currencyReceipt.message());
        }
        MapOperationResult itemResult = charge.applyItemConsumption(player, preparedItems);
        if (!itemResult.success()) {
            charge.rollbackCurrencies(player, currencyReceipt.successfulCharges());
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return itemResult;
        }
        invalidatePlayerDataCache(player.getUniqueId());
        sync(player, false);
        pushAnchorTerrain(player, anchor);
        return MapOperationResult.success(message("player.anchor-unlocked", anchor.displayName()));
    }

    /**
     * 免费强制解锁（被动 API / 管理命令）：跳过 permission、距离门槛与全部
     * 费用消耗，直接写入解锁记录。供 {@code MapAnchorUnlockable#unlock}
     * 与管理命令使用——任务奖励、活动发放等"系统授予"场景。
     *
     * @param player   目标玩家（须在线）
     * @param anchorId 锚点 id
     * @return 操作结果
     */
    public MapOperationResult grantAnchorUnlock(Player player, String anchorId) {
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
        try {
            if (!repository.tryUnlockAnchor(player.getUniqueId(), anchor.id(), System.currentTimeMillis())) {
                return MapOperationResult.failure(message("player.anchor-already-unlocked"));
            }
        } catch (Exception exception) {
            return MapOperationResult.failure(message("player.anchor-unlock-save-failed"));
        }
        invalidatePlayerDataCache(player.getUniqueId());
        sync(player, false);
        pushAnchorTerrain(player, anchor);
        MapOperationResult result = MapOperationResult.success(
            message("player.anchor-unlocked", anchor.displayName()));
        // 系统授予也要让目标玩家感知（任务奖励/管理授予等）
        sendPlayerResult(player, result);
        return result;
    }

    /**
     * 解锁成功后推送锚点点亮区域的地形数据（"解锁即亮"）：
     * 配置了 light-region / light-radius 的锚点，其覆盖 MapChunk 立即推给客户端。
     */
    private void pushAnchorTerrain(Player player, AnchorDefinition anchor) {
        if (terrainPush == null || anchor.lightRegions().isEmpty() && anchor.lightRadius() <= 0) {
            return;
        }
        World world = resolveWorld(anchor.worldId());
        if (world == null) {
            return;
        }
        Set<Long> coords = new HashSet<>();
        for (PolygonRegion region : anchor.lightRegions()) {
            MapPreloadService.collectPolygonChunks(coords, region.points());
        }
        if (anchor.lightRadius() > 0) {
            MapPreloadService.collectCircleChunks(coords, anchor.x(), anchor.z(), anchor.lightRadius());
        }
        // sourceVersion=0：锚点推送不参与世界预加载版本去重（merge 按列源标记处理）
        terrainPush.submit(player, world, coords, 0);
    }

    /**
     * 客户端 {@code axsmap:request} 插件消息入口（MapModule 注册的
     * PluginMessageListener 转发）。校验请求块是否 ⊆ 该世界预加载覆盖集合，
     * 越界请求直接丢弃（防客户端越权拉取任意地形）。
     *
     * @param player  请求玩家
     * @param message 原始请求字节
     */
    public void handleTerrainRequest(Player player, byte[] message) {
        if (terrainPush == null || player == null || !player.isOnline() || message == null) {
            return;
        }
        // use 门控：该入口不经 MapUiPacketHandler（独立插件通道），权限在此自查
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }
        // 节流：每次受理都可能排入数百块任务，高频伪造包可刷爆队列/带宽
        long now = System.nanoTime();
        Long last = terrainRequestAt.get(player.getUniqueId());
        if (last != null && now - last < TERRAIN_REQUEST_INTERVAL_NANOS) {
            return;
        }
        terrainRequestAt.put(player.getUniqueId(), now);
        MapTerrainPushService.RequestData request = MapTerrainPushService.parseRequest(message);
        if (request == null) {
            return;
        }
        WorldDefinition world = configuration.worlds().get(normalizeId(request.worldId()));
        if (world == null) {
            // 复合世界 id（nodeId/worldId）→ 远程地形中继到目标节点
            if (crossServer != null && MapCrossServerService.splitComposite(request.worldId()) != null) {
                crossServer.relayTerrainRequest(player, normalizeId(request.worldId()), request.chunks());
            }
            return;
        }
        if (!world.hasPreload()) {
            return;
        }
        World bukkitWorld = resolveWorld(world.id());
        if (bukkitWorld == null) {
            return;
        }
        // 请求的块必须 ⊆ 预加载覆盖集合（服务端重算覆盖，越界坐标直接丢弃）
        Set<Long> allowed = new HashSet<>();
        for (int[] c : preloadChunkCoords(world)) {
            allowed.add(((long) c[0] << 32) | (c[1] & 0xffffffffL));
        }
        List<Long> accepted = new ArrayList<>(request.chunks().size());
        for (long[] c : request.chunks()) {
            long packed = ((long) c[0] << 32) | (c[1] & 0xffffffffL);
            if (allowed.contains(packed)) {
                accepted.add(packed);
            }
        }
        terrainPush.submit(player, bukkitWorld, accepted, effectivePreloadVersion(world));
    }

    /** 三维距离门槛校验：unlock.radius > 0 时须同世界且在 radius 格内。 */
    private MapOperationResult validateUnlockRange(Player player, AnchorDefinition anchor) {
        double radius = anchor.unlock().radius();
        if (radius <= 0) {
            return MapOperationResult.success("");
        }
        World anchorWorld = resolveWorld(anchor.worldId());
        if (anchorWorld == null || player.getWorld() == null
            || !player.getWorld().equals(anchorWorld)) {
            return MapOperationResult.failure(
                message("player.anchor-wrong-world", anchor.displayName()));
        }
        double dx = player.getLocation().getX() - anchor.x();
        double dy = player.getLocation().getY() - anchor.y();
        double dz = player.getLocation().getZ() - anchor.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > radius) {
            return MapOperationResult.failure(
                message("player.anchor-too-far",
                    String.valueOf(Math.round(distance)),
                    String.valueOf(Math.round(radius))));
        }
        return MapOperationResult.success("");
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
            // 复合锚点 id → 跨服传送握手（目标服落地时才扣费）
            MapCrossServerService.RemoteAnchorView remote =
                crossServer == null ? null : crossServer.remoteAnchor(normalizeId(anchorId));
            if (remote != null) {
                return crossServer.requestTeleport(player, remote.nodeId(), remote.worldId(), remote.anchorId());
            }
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        if (!isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure(message("player.anchor-locked"));
        }
        return performAnchorTeleport(player, anchor);
    }

    /**
     * 锚点传送的扣费+传送段：权限/世界校验 → 物品预检 → 货币扣费 → 传送 →
     * 失败回滚。本服 UI 传送与跨服落地传送共用（跨服落地由调用方自行处理解锁要求）。
     */
    MapOperationResult performAnchorTeleport(Player player, AnchorDefinition anchor) {
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        World world = resolveWorld(anchor.worldId());
        if (world == null) {
            return MapOperationResult.failure(message("player.anchor-world-unloaded", anchor.worldId()));
        }
        MapChargeService.PreparedItemConsumption preparedItems = charge.prepareItemConsumption(player, anchor.teleportItems());
        if (!preparedItems.success()) {
            return MapOperationResult.failure(preparedItems.message());
        }
        MapChargeService.CurrencyChargeReceipt currencyReceipt = charge.chargeCurrencies(player, anchor.teleportCurrencies());
        if (!currencyReceipt.success()) {
            return MapOperationResult.failure(currencyReceipt.message());
        }
        boolean teleported = player.teleport(
            new Location(world, anchor.x(), anchor.y(), anchor.z(), player.getLocation().getYaw(), player.getLocation().getPitch())
        );
        if (!teleported) {
            charge.rollbackCurrencies(player, currencyReceipt.successfulCharges());
            return MapOperationResult.failure(message("player.teleport-failed"));
        }
        MapOperationResult itemResult = charge.applyItemConsumption(player, preparedItems);
        if (!itemResult.success()) {
            charge.rollbackCurrencies(player, currencyReceipt.successfulCharges());
            return itemResult;
        }
        sync(player, false);
        return MapOperationResult.success(message("player.teleported-to-anchor", anchor.displayName()));
    }

    /** 免费锚点落地：不扣费，仅做权限/世界校验后传送。 */
    MapOperationResult performFreeAnchorTeleport(Player player, AnchorDefinition anchor, World world) {
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        boolean teleported = player.teleport(
            new Location(world, anchor.x(), anchor.y(), anchor.z(), player.getLocation().getYaw(), player.getLocation().getPitch())
        );
        if (!teleported) {
            return MapOperationResult.failure(message("player.teleport-failed"));
        }
        sync(player, false);
        return MapOperationResult.success(message("player.teleported-to-anchor", anchor.displayName()));
    }

    private MapOperationResult trackAnchorInternal(Player player, String anchorId) {
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (player == null || !player.isOnline() || anchor == null) {
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        // 全局导航委托优先：第三方插件接管时地图不建本地信标，仅记委托态
        if (tryDelegateNavigate(player, new MapNavigationRequest(
            MapNavigationRequest.TYPE_ANCHOR, anchor.id(), anchor.worldId(),
            anchor.displayName(), anchor.x(), anchor.y(), anchor.z(),
            MapNavigationRequest.SOURCE_MAP
        ))) {
            sync(player, false);
            return MapOperationResult.success(message("player.anchor-navigation-started", anchor.displayName()));
        }
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure(message("player.navigation-disabled"));
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure(message("player.waypoint-unavailable"));
        }
        String waypointId = configuration.navigation().anchorIdPrefix() + sanitize(anchor.id());
        String styleId = resolveNavStyleId();
        if (targetService != null) {
            targetService.setTarget(player, navTarget(
                waypointId, anchor.worldId(), anchor.x(), anchor.y(), anchor.z(),
                anchor.displayName(), styleId, MapNavigationRequest.TYPE_ANCHOR
            ));
        } else if (configuration.navigation().waypoint().enabled()
            && !waypointBridge.addWaypoint(player, waypointId, anchor.displayName(), styleId, anchor.x(), anchor.y(), anchor.z())) {
            return MapOperationResult.failure(message("player.anchor-navigation-failed"));
        }
        navigationMapOrCreate(player).put(
            navKey(TargetType.ANCHOR, anchor.id()),
            new MapNavigationState(true, TargetType.ANCHOR, anchor.worldId(), anchor.id(), waypointId, anchor.displayName(), "map")
        );
        publishNavStarted(player, TargetType.ANCHOR, anchor.id(), anchor.worldId());
        sync(player, false);
        return MapOperationResult.success(message("player.anchor-navigation-started", anchor.displayName()));
    }

    private MapOperationResult trackWaypointInternal(Player player, String waypointId) {
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (player == null || !player.isOnline() || waypoint == null) {
            return MapOperationResult.failure(message("player.waypoint-not-found", waypointId));
        }
        // 全局导航委托优先：返回 false 才落地图内置导航
        if (tryDelegateNavigate(player, new MapNavigationRequest(
            MapNavigationRequest.TYPE_WAYPOINT, waypoint.waypointId(), normalizeId(waypoint.world()),
            waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(),
            MapNavigationRequest.SOURCE_MAP
        ))) {
            sync(player, false);
            return MapOperationResult.success(message("player.waypoint-navigation-started", waypoint.name()));
        }
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure(message("player.navigation-disabled"));
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure(message("player.waypoint-unavailable"));
        }
        String runtimeWaypointId = configuration.navigation().waypointIdPrefix() + sanitize(waypoint.waypointId());
        String styleId = resolveNavStyleId();
        if (targetService != null) {
            targetService.setTarget(player, navTarget(
                runtimeWaypointId, normalizeId(waypoint.world()), waypoint.x(), waypoint.y(), waypoint.z(),
                waypoint.name(), styleId, MapNavigationRequest.TYPE_WAYPOINT
            ));
        } else if (configuration.navigation().waypoint().enabled()
            && !waypointBridge.addWaypoint(player, runtimeWaypointId, waypoint.name(), styleId, waypoint.x(), waypoint.y(), waypoint.z())) {
            return MapOperationResult.failure(message("player.waypoint-navigation-failed"));
        }
        navigationMapOrCreate(player).put(
            navKey(TargetType.WAYPOINT, waypoint.waypointId()),
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
        publishNavStarted(player, TargetType.WAYPOINT, waypoint.waypointId(), waypoint.world());
        sync(player, false);
        return MapOperationResult.success(message("player.waypoint-navigation-started", waypoint.name()));
    }

    /**
     * 由地图内置导航栈开始导航指定外部目标（地图代导航）。
     * <p>
     * 等同玩家在面板点击 useMapNavigation=true 目标的"导航"：
     * 建客户端路标 + 路径标记、记录 EXTERNAL 导航态（source 记真实来源）、
     * 目标按钮翻转为"取消"。不经过全局委托。
     *
     * @param player   目标玩家
     * @param targetId 已推送的外部目标 id
     * @return true 表示导航已开始（同目标已在导航视为幂等成功）
     */
    public boolean navigateExternalTarget(Player player, String targetId) {
        MapExternalTarget target = player == null || !player.isOnline() ? null : findExternalTarget(player, targetId);
        if (target == null) {
            return false;
        }
        MapNavigationState active = findNavigation(player, TargetType.EXTERNAL, target.targetId());
        if (active != null) {
            return !isDelegatedNav(active);
        }
        return trackExternalInternal(player, target).success();
    }

    /** 地图代导航执行：建路标/路径标记并记录 EXTERNAL 导航态。 */
    private MapOperationResult trackExternalInternal(Player player, MapExternalTarget target) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure(message("player.navigation-disabled"));
        }
        if (!waypointRuntimeReady()) {
            return MapOperationResult.failure(message("player.waypoint-unavailable"));
        }
        String waypointId = EXTERNAL_NAV_WAYPOINT_PREFIX + sanitize(target.targetId());
        String styleId = resolveNavStyleId();
        String worldId = normalizeId(target.worldId());
        if (targetService != null) {
            targetService.setTarget(player, navTarget(
                waypointId, worldId, target.x(), target.y(), target.z(),
                target.title(), styleId, MapNavigationRequest.TYPE_EXTERNAL
            ));
        } else if (configuration.navigation().waypoint().enabled()
            && !waypointBridge.addWaypoint(player, waypointId, target.title(), styleId, target.x(), target.y(), target.z())) {
            return MapOperationResult.failure(message("player.waypoint-navigation-failed"));
        }
        navigationMapOrCreate(player).put(
            navKey(TargetType.EXTERNAL, target.targetId()),
            new MapNavigationState(
                true, TargetType.EXTERNAL, worldId, target.targetId(),
                waypointId, target.title(), normalizeId(target.source())
            )
        );
        markExternalNavigated(player, target.targetId(), true);
        publishNavStarted(player, TargetType.EXTERNAL, target.targetId(), worldId);
        sync(player, false);
        return MapOperationResult.success(message("player.target-navigation-started", target.title()));
    }

    /** 外部目标坐标/标题更新时同步刷新地图代导航态（非委托态才处理）。 */
    private void refreshExternalNavigation(Player player, MapExternalTarget target) {
        MapNavigationState nav = findNavigation(player, TargetType.EXTERNAL, target.targetId());
        if (nav == null || isDelegatedNav(nav)) {
            return;
        }
        String worldId = normalizeId(target.worldId());
        String styleId = resolveNavStyleId();
        if (targetService != null) {
            targetService.setTarget(player, navTarget(
                nav.waypointId(), worldId, target.x(), target.y(), target.z(),
                target.title(), styleId, MapNavigationRequest.TYPE_EXTERNAL
            ));
        } else if (configuration.navigation().waypoint().enabled()) {
            waypointBridge.addWaypoint(player, nav.waypointId(), target.title(), styleId, target.x(), target.y(), target.z());
        }
        navigationMapOrCreate(player).put(
            navKey(TargetType.EXTERNAL, target.targetId()),
            new MapNavigationState(true, TargetType.EXTERNAL, worldId, target.targetId(), nav.waypointId(), target.title(), nav.source())
        );
        // 代导航期间 navigated 以导航态为准：来源重推 spec 时强制回写，避免按钮闪回"导航"
        markExternalNavigated(player, target.targetId(), true);
    }

    /** 解析导航路标样式 ID（导航配置优先，回退路径点默认样式）。 */
    private String resolveNavStyleId() {
        return waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypoint().styleId(),
            "Map"
        );
    }

    /** 构建地图自有目标（锚点/路径点）的共享导航目标描述。 */
    private NavigationTarget navTarget(
        String navId, String worldId, double x, double y, double z,
        String label, String styleId, String type
    ) {
        NavigationTarget.Builder builder = NavigationTarget.builder(navId)
            .world(worldId)
            .position(x, y, z)
            .title(label)
            .styleId(styleId)
            .placeholder("type", type);
        String template = configuration.navigation().waypoint().titleTemplate();
        if (template != null && !template.isBlank()) {
            builder.titleTemplate(template);
        }
        if (configuration.navigation().finishRange() > 0) {
            builder.finishRange(configuration.navigation().finishRange())
                .finishCommands(configuration.navigation().finishCommands());
        }
        return builder.build();
    }

    private MapOperationResult clearTrackInternal(Player player, boolean sync) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        Map<String, MapNavigationState> states = navigationMap(player);
        if (states == null || states.isEmpty()) {
            if (sync) {
                sync(player, false);
            }
            return MapOperationResult.success(message("player.no-active-navigation"));
        }
        for (Map.Entry<String, MapNavigationState> entry : new ArrayList<>(states.entrySet())) {
            MapNavigationState nav = entry.getValue();
            removeNavigation(player, entry.getKey(), nav);
            publishBusEvent("axs.map.nav_cleared", player, Map.of(
                "type", nav.targetType().name().toLowerCase(Locale.ROOT),
                "id", nav.targetId(),
                "world_id", nav.worldId(),
                "reason", "cleared"
            ));
        }
        if (markerService != null) {
            markerService.hideMarker(player);
        }
        if (textureMarkerService != null) {
            textureMarkerService.hideMarker(player);
        }
        if (sync) {
            sync(player, false);
        }
        return MapOperationResult.success(message("player.navigation-cleared"));
    }

    /**
     * 取消单个导航目标（锚点/路径点；外部目标取消走 external_nav_cancel 转发来源）。
     *
     * @param player   玩家
     * @param type     目标类型（anchor / waypoint / external）
     * @param targetId 目标 id
     */
    public void untrackTarget(Player player, String type, String targetId) {
        MapOperationResult result = untrackInternal(player, type, targetId);
        sendPlayerResult(player, result);
    }

    private MapOperationResult untrackInternal(Player player, String type, String targetId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure(message("player.offline"));
        }
        TargetType targetType = targetTypeOf(type);
        if (targetType == TargetType.NONE || targetId == null || targetId.isBlank()) {
            return MapOperationResult.failure(message("player.no-active-navigation"));
        }
        String key = navKey(targetType, targetId);
        MapNavigationState state = findNavigation(player, targetType, targetId);
        if (state == null) {
            sync(player, false);
            return MapOperationResult.success(message("player.no-active-navigation"));
        }
        removeNavigation(player, key, state);
        publishBusEvent("axs.map.nav_cleared", player, Map.of(
            "type", state.targetType().name().toLowerCase(Locale.ROOT),
            "id", state.targetId(),
            "world_id", state.worldId(),
            "reason", "cleared"
        ));
        sync(player, false);
        return MapOperationResult.success(message("player.navigation-removed"));
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
        String name = configuration.waypoints().waypointNamePrefix() + "-" + (countInWorld + 1);
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
            MapNavigationState waypointNav = findNavigation(player, TargetType.WAYPOINT, waypoint.waypointId());
            if (waypointNav != null) {
                removeNavigation(player, navKey(TargetType.WAYPOINT, waypoint.waypointId()), waypointNav);
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
    void sync(Player player, boolean initPacket) {
        // 单次快照供 menu+hud 复用（原来各自 buildSnapshot，每 tick 双倍开销）
        MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
        syncMenu(player, initPacket, snapshot);
        syncHud(player, !openedHudPlayers.contains(player.getUniqueId()), snapshot);
    }

    private void syncMenu(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        syncMenu(player, initPacket, buildSnapshot(player));
    }

    private void syncMenu(Player player, boolean initPacket,
                          MapSnapshotBuilder.BuildResult snapshot) {
        if (player == null || !player.isOnline() || bridge == null) {
            return;
        }
        if (initPacket) actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        Map<String, Object> payload = buildMenuPayload(snapshot.menu());
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));
        payload.put("serverId", serverId());
        bridge.sendPacket(player, menuUiId, initPacket ? "init" : "update", payload);
    }

    private void syncHud(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        syncHud(player, initPacket, buildSnapshot(player));
    }

    private void syncHud(Player player, boolean initPacket,
                         MapSnapshotBuilder.BuildResult snapshot) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // HUD 常驻打开（defaultOpen）：不再 openUi/closeUi 切换实例，避免同名
        // HUD 叠副本；隐藏完全由 var.visible 驱动（tick → deactivateHud 即时停）。
        sendHudPacket(player, initPacket ? "init" : "update", snapshot.hud());
        if (initPacket) {
            openedHudPlayers.add(player.getUniqueId());
        }
    }

    private void sendHudPacket(Player player, String handler, MapSnapshotBuilder.HudSnapshot hud) {
        if (bridge == null) {
            return;
        }
        Map<String, Object> payload = buildHudPayload(hud);
        payload.put("hudSession", hudSessions.getOrDefault(player.getUniqueId(), -1));
        payload.put("serverId", serverId());
        // 玩家设置字典随 init 下发（客户端 mod applySetting 逐项套回，不回环）
        if ("init".equals(handler)) {
            payload.put("settings", playerSettings(player.getUniqueId()));
        }
        bridge.sendPacket(player, hudUiId, handler, payload);
    }

    /**
     * 当前服务器的稳定标识（{@code plugins/ArcartXSuite/server-id} 文件持久化，
     * 首次启动自动生成 UUID）。随 HUD/菜单数据包下发，客户端 mod 以此为
     * 地形缓存的一级隔离键——换 IP/域名/端口不影响缓存命中；迁移 VPS 时
     * 连同本文件一起复制即可让玩家沿用旧缓存。
     *
     * @return 服务器标识字符串
     */
    public String serverId() {
        String id = serverId;
        if (id == null) {
            id = loadOrCreateServerId();
            serverId = id;
        }
        return id;
    }

    private volatile String serverId;

    private String loadOrCreateServerId() {
        File file = new File(plugin.getDataFolder(), "server-id");
        if (file.isFile()) {
            try {
                String id = Files.readString(file.toPath()).trim();
                if (id.matches("[A-Za-z0-9_-]{8,64}")) {
                    return id;
                }
                logger.warning("[Map] server-id 文件内容非法，重新生成");
            } catch (IOException e) {
                logger.warning("[Map] 读取 server-id 失败: " + e.getMessage());
            }
        }
        String generated = UUID.randomUUID().toString();
        try {
            Files.writeString(file.toPath(), generated);
            logger.info("[Map] 已生成服务器标识 server-id=" + generated);
        } catch (IOException e) {
            logger.warning("[Map] 写入 server-id 失败（仅本次会话内有效）: " + e.getMessage());
        }
        return generated;
    }

    private MapSnapshotBuilder.BuildResult buildSnapshot(Player player) {
        MapPlayerViewState state = state(player);
        List<MapSnapshotBuilder.WorldView> worlds = new ArrayList<>();
        for (WorldDefinition world : configuration.worlds().values()) {
            Location center = resolveWorldCenter(world);
            worlds.add(
                new MapSnapshotBuilder.WorldView(
                    world.id(),
                    resolveDisplayName(world),
                    resolveDimension(world),
                    effectivePreloadVersion(world),
                    world.hasPreload() ? preloadRegionPoints(world) : List.of(),
                    world.hasPreload() ? preloadChunkCoords(world) : List.of(),
                    center == null ? 0 : center.getX(),
                    center == null ? 0 : center.getZ(),
                    false, "", false
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
                    anchor.lightRadius(),
                    anchor.unlock().radius(),
                    false, ""
                )
            );
        }

        // 远程世界/锚点并入快照：复合 id（nodeId/worldId）与本机世界同列表共存，
        // 互不覆盖；远程地形维度桶 axsremote.<node>.<dim> 隔离客户端缓存。
        if (crossServer != null) {
            for (MapCrossServerService.RemoteWorldView remote : crossServer.remoteWorlds()) {
                worlds.add(new MapSnapshotBuilder.WorldView(
                    remote.compositeId(),
                    remote.displayName().isBlank() ? remote.worldId() : remote.displayName(),
                    remote.dimensionKey(),
                    remote.preloadVersion(),
                    List.of(),
                    remote.preloadChunkList(),
                    remote.centerX(),
                    remote.centerZ(),
                    true,
                    remote.serverName(),
                    remote.allowArrive()
                ));
                for (MapCrossServerService.RemoteAnchorView anchor : remote.anchors()) {
                    anchors.add(new MapSnapshotBuilder.AnchorView(
                        anchor.compositeId(),
                        anchor.compositeWorldId(),
                        anchor.displayName(),
                        anchor.description(),
                        anchor.x(),
                        anchor.y(),
                        anchor.z(),
                        true,              // 远程锚点对浏览者展示为可用（落地校验在目标服）
                        "",
                        anchor.teleportCost(),
                        false,             // 本服不可解锁远程锚点
                        anchor.allowArrive(),
                        anchor.sortOrder(),
                        List.of(),
                        0,
                        0,
                        true,
                        anchor.serverName()
                    ));
                }
            }
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
                navigationStates.getOrDefault(player.getUniqueId(), new LinkedHashMap<>()),
                location == null ? null : new MapSnapshotBuilder.PlayerPosition(location.getX(), location.getY(), location.getZ(), location.getYaw()),
                location == null ? "" : normalizeId(location.getWorld().getName()),
                resolveWaypointLimit(player),
                navigationAvailable(),
                configuration.waypoints().enabled()
            )
        );
    }

    /**
     * 解析世界维度 key：config 显式配置优先，否则按 Bukkit world.getKey() 自动获取。
     * 供客户端 mod 将 worldId 匹配到本地 MC 维度（overworld/the_nether 等）。
     */
    /** PolygonRegion 列表 → 顶点列表列表（SnapshotBuilder 内部传递格式）。 */
    static List<List<int[]>> toRegionPoints(List<PolygonRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        List<List<int[]>> out = new ArrayList<>(regions.size());
        for (PolygonRegion r : regions) {
            out.add(r.points());
        }
        return List.copyOf(out);
    }

    /** 世界ID→MC维度键的最近成功解析缓存：BSkyBlock 等动态世界卸载瞬间 Bukkit.getWorld 返回 null，用缓存兜底防下发空维度。 */
    private static final ConcurrentMap<String, String> RESOLVED_DIMENSION_CACHE = new ConcurrentHashMap<>();

    static String resolveDimension(WorldDefinition world) {
        World bukkitWorld = resolveWorld(world.id());
        if (bukkitWorld != null) {
            String key = bukkitWorld.getKey().toString();
            RESOLVED_DIMENSION_CACHE.put(world.id(), key);
            return key;
        }
        return RESOLVED_DIMENSION_CACHE.getOrDefault(world.id(), "");
    }

    /** worldId → Bukkit World：精确匹配优先，退回 case-insensitive 遍历（文件名可能含大写）。 */
    static World resolveWorld(String worldId) {
        World bukkitWorld = Bukkit.getWorld(worldId);
        if (bukkitWorld == null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(worldId)) {
                    return w;
                }
            }
        }
        return bukkitWorld;
    }

    /**
     * 世界出生点解析：Essentials SpawnBackend → Bukkit spawn 兜底。
     * <p>
     * ess 模块软依赖 MV 且后端自选（MV 已加载→该世界 MV spawn；无 MV→本地
     * spawn 表），map 只需问 ess 一次，不再自行分支 MV/ess。
     *
     * @param world 已解析的 Bukkit 世界（ess SpawnBackend 的 context）
     * @return 出生点 Location；world 为 null 时返回 null
     */
    Location resolveWorldSpawn(World world) {
        if (world == null) {
            return null;
        }
        xuanmo.arcartxsuite.api.capability.EssentialsQueryable ess = essentialsProvider.get();
        if (ess != null) {
            try {
                Location spawn = ess.getSpawn(world);
                // 世界过滤：本地后端返回的是全局单点 spawn，若其所在世界
                // 与目标世界不同则不能当该世界出生点用（圆心会落到错误坐标）
                if (spawn != null && spawn.getWorld() != null
                    && spawn.getWorld().getName().equalsIgnoreCase(world.getName())) {
                    return spawn;
                }
            } catch (Exception e) {
                logger.warning("从 Essentials 模块读取世界 " + world.getName() + " 的出生点失败: " + e.getMessage());
            }
        }
        return world.getSpawnLocation();
    }
    /** MV alias 缓存：worldId → colourlessAlias。启动时一次性补空，避免每次 buildSnapshot 调 MV API。 */
    private final java.util.Map<String, String> mvDisplayNames = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 解析世界显示名：配置填了就用配置，留空则从 Multiverse-Core 的 colourless alias 补。
     * MV 不存在或世界未在 MV 注册时，降级用 world id。
     */
    String resolveDisplayName(WorldDefinition world) {
        String configured = world.displayName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String cached = mvDisplayNames.get(world.id());
        if (cached != null) {
            return cached;
        }
        String resolved = resolveDisplayNameFromMV(world.id());
        // 只缓存真实解析到的 alias：fallback 值（MV 未加载/世界未注册时
        // 等于 worldId）一旦入缓存，世界后注册或 reload 后会被永久挡住
        if (!resolved.equals(world.id())) {
            mvDisplayNames.put(world.id(), resolved);
        }
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
            entry.put("preloadVersion", row.preloadVersion());
            entry.put("preloadChunks", row.preloadChunks());
            entry.put("centerX", row.centerX());
            entry.put("centerZ", row.centerZ());
            entry.put("remote", row.remote());
            entry.put("server", row.server());
            entry.put("canTeleport", row.canTeleport());
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
            entry.put("remote", row.remote());
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
        payload.put("shareEnabled", share.shareEnabled());
        payload.put("clearTrackVisible", snapshot.clearTrackVisible());
        payload.put("trackingText", snapshot.trackingText());
        payload.put("detailSelectedType", snapshot.detail().selectedType());
        payload.put("detailSelectedId", snapshot.detail().selectedId());
        payload.put("detailTitle", snapshot.detail().title());
        payload.put("detailDescription", snapshot.detail().description());
        payload.put("detailLocation", snapshot.detail().location());
        payload.put("detailUnlocked", snapshot.detail().unlocked());
        payload.put("detailUnlockCostText", snapshot.detail().unlockCostText());
        payload.put("detailTeleportCostText", snapshot.detail().teleportCostText());
        payload.put("detailExternalTarget", snapshot.detail().externalTarget());
        payload.put("detailCanUnlock", snapshot.detail().canUnlock());
        payload.put("detailUnlockRadius", snapshot.detail().unlockRadius());
        payload.put("detailUnlockInRange", snapshot.detail().unlockInRange());
        payload.put("detailUnlockRangeText", snapshot.detail().unlockRangeText());
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
        payload.put("detailCrossWorldCostText", snapshot.detail().crossWorldCostText());
        payload.put("detailRemote", snapshot.detail().remote());
        payload.put("detailServerName", snapshot.detail().serverName());
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
        payload.put("worldName", snapshot.worldName());
        payload.put("dimension", snapshot.dimension());
        payload.put("playerYaw", snapshot.playerYaw());
        payload.put("trackingText", snapshot.trackingText());
        payload.put("litEnabled", snapshot.litEnabled());
        payload.put("litRegions", snapshot.litRegions());
        payload.put("preloadVersion", snapshot.preloadVersion());
        payload.put("preloadChunks", snapshot.preloadChunks());

        // HUD 标记行（玩家当前世界，与菜单选中世界无关）
        LinkedHashMap<String, Object> markerRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.markerRows().size(); i++) {
            MapSnapshotBuilder.HudMarkerRow row = snapshot.markerRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("type", row.type());
            entry.put("label", row.label());
            entry.put("x", row.x());
            entry.put("z", row.z());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            entry.put("unlocked", row.unlocked());
            markerRows.put(Integer.toString(i), entry);
        }
        payload.put("markerRows", markerRows);
        return payload;
    }


    private String formatUnlockCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.unlockCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        for (ItemCost cost : anchor.unlockItems()) {
            parts.add(describeItemCost(cost) + " x" + cost.amount());
        }
        return parts.isEmpty() ? "免费解锁" : String.join(" + ", parts);
    }

    String formatTeleportCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.teleportCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        for (ItemCost cost : anchor.teleportItems()) {
            parts.add(describeItemCost(cost) + " x" + cost.amount());
        }
        return parts.isEmpty() ? "免费传送" : String.join(" + ", parts);
    }

    /** 物品消耗展示名：取 matcher 首个具体物品 id，取不到退化为"物品"。 */
    private String describeItemCost(ItemCost cost) {
        ItemMatcher matcher = cost.matcher();
        for (List<String> ids : List.of(matcher.materialIds(), matcher.mythicItemIds(),
            matcher.neigeItemIds(), matcher.overtureItemIds())) {
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return "物品";
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
            List<AnchorDefinition> newlyGranted = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (AnchorDefinition anchor : configuration.anchors().values()) {
                AnchorUnlock unlock = anchor.unlock();
                boolean granted = unlock.defaultUnlock()
                    || (unlock.onPermission()
                        && !anchor.permission().isBlank()
                        && player.hasPermission(anchor.permission()));
                if (granted && existing.add(anchor.id())) {
                    repository.unlockAnchor(player.getUniqueId(), anchor.id(), now);
                    newlyGranted.add(anchor);
                }
            }
            if (!newlyGranted.isEmpty()) {
                invalidatePlayerDataCache(player.getUniqueId());
                for (AnchorDefinition anchor : newlyGranted) {
                    pushAnchorTerrain(player, anchor);
                }
            }
        } catch (Exception e) {
            // 存储异常导致解锁失败：移除已确保标记，让下次 sync 重试
            // （否则该玩家整会话内默认解锁永久丢失，且没有任何日志可查）
            defaultUnlocksEnsured.remove(player.getUniqueId());
            logger.warning("[Map] 玩家 " + player.getName() + " 默认锚点解锁失败: " + e.getMessage());
        }
    }

    int resolveWaypointLimit(Player player) {
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

    List<MapWaypoint> loadWaypoints(Player player) {
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

    void invalidatePlayerDataCache(UUID playerUuid) {
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

    MapWaypoint findWaypoint(Player player, String waypointId) {
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

    MapPin findPin(Player player, String pinId) {
        String normalized = normalizeId(pinId);
        for (MapPin pin : loadPins(player)) {
            if (normalizeId(pin.pinId()).equals(normalized)) {
                return pin;
            }
        }
        return null;
    }

    List<MapPin> loadPins(Player player) {
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
        // 残留的 selectedWorldId。未配置世界也返回——客户端 mod 无服务端映射时
        // 回退本地维度渲染本地扫描地形（临时/副本世界可看地图导航）。
        if (player != null && player.getWorld() != null) {
            String currentWorldId = normalizeId(player.getWorld().getName());
            if (!currentWorldId.isBlank()) {
                return currentWorldId;
            }
        }
        MapPlayerViewState viewState = state(player);
        if (!viewState.selectedWorldId().isBlank() && configuration.worlds().containsKey(viewState.selectedWorldId())) {
            return viewState.selectedWorldId();
        }
        return configuration.worlds().isEmpty() ? "" : configuration.worlds().keySet().iterator().next();
    }

    MapPlayerViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new MapPlayerViewState());
    }

    private void clearTrackSilently(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, MapNavigationState> states = navigationStates.remove(player.getUniqueId());
        if (states != null) {
            for (MapNavigationState state : states.values()) {
                if (isDelegatedNav(state)) {
                    // 委托态被静默清除：通知委托方停止自身导航（实现需幂等）
                    notifyDelegateCancel(player, state);
                } else if (targetService != null) {
                    targetService.removeTarget(player, state.waypointId());
                } else {
                    waypointBridge.removeWaypoint(player, state.waypointId(), false);
                }
            }
        }
        if (markerService != null) {
            markerService.hideMarker(player);
        }
        if (textureMarkerService != null) {
            textureMarkerService.hideMarker(player);
        }
    }
    void sendPlayerResult(Player player, MapOperationResult result) {
        if (player == null || result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static String sanitize(String value) {
        return normalizeId(value).replaceAll("[^a-z0-9_\\-]", "_");
    }


    int effectivePreloadVersion(WorldDefinition world) {
        return preload.effectivePreloadVersion(world);
    }

    Location resolveWorldCenter(WorldDefinition world) {
        return preload.resolveWorldCenter(world);
    }

    List<int[]> preloadChunkCoords(WorldDefinition world) {
        return preload.preloadChunkCoords(world);
    }

    public MapOperationResult refreshPreloadVersion(String worldId) {
        return preload.refreshPreloadVersion(worldId);
    }

    List<List<int[]>> preloadRegionPoints(WorldDefinition world) {
        return preload.preloadRegionPoints(world);
    }

    // ─── 分享/扣费委托（协作者拆分后保留接口契约）────────────

    @Override
    public void shareWaypoint(Player player, String waypointId) {
        share.shareWaypoint(player, waypointId);
    }

    @Override
    public void sharePin(Player player, String pinId) {
        share.sharePin(player, pinId);
    }

    @Override
    public void claimShare(Player player, String shareCode) {
        share.claimShare(player, shareCode);
    }

    static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 服务端侧文本截断（客户端输入框有 length 上限，伪造包可绕过）。 */
    private static String clipText(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
