package xuanmo.arcartxsuite.map.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

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

    /** 玩家侧全部公开入口的使用权限（/map、全局按键、UI 数据包、地形请求）。 */
    private static final String PERMISSION_USE = "arcartxsuite.map.use";

    /** 路径点/标记点名称上限（与 UI 输入框 length 对齐）。 */
    private static final int MAX_NAME_LENGTH = 64;
    /** 路径点/标记点描述上限（与 UI 输入框 length 对齐）。 */
    private static final int MAX_DESC_LENGTH = 256;
    /** axsmap:request 同玩家最小请求间隔（防伪造客户端刷推送队列）。 */
    private static final long TERRAIN_REQUEST_INTERVAL_NANOS = 3_000_000_000L;

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
    private final xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge;
    private final MessageProvider messages;
    private final java.util.function.Supplier<EventBusCapability> eventBusProvider;
    /** Essentials 模块查询能力（读取 /setspawn 出生点，MV 不存在时的二级来源）。 */
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.EssentialsQueryable> essentialsProvider;
    private NavigationMarkerService markerService;
    /** 贴图路径提示服务（WorldTexture，与模型标记互补、独立开关）。 */
    private xuanmo.arcartxsuite.api.util.NavigationTextureMarkerService textureMarkerService;
    /** 共享导航目标编排服务（diff 推送 / 到达滞回判定 / 跨世界抑制），navigation.enabled 时创建。 */
    private NavigationTargetService targetService;
    /** 地形预加载推送服务（axsmap:terrain / axsmap:request 通道）。 */
    private MapTerrainPushService terrainPush;
    private final ConcurrentMap<UUID, MapPlayerViewState> viewStates = new ConcurrentHashMap<>();
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
            itemSourceRegistry, itemMatcherSupport, currencyBridgeManager, waypointBridge, null, null, null, null, null);
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
        xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge
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
        this.essentialsProvider = essentialsProvider != null ? essentialsProvider : () -> null;
        this.worldTextureBridge = worldTextureBridge;
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
        loadPreloadVersionBumps();
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
            player.sendMessage(PREFIX + message("player.world-not-configured", worldId));
            return false;
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
        // 移除"默认解锁已确保"标记：重登重新尝试，解锁失败的锚点有补救机会
        defaultUnlocksEnsured.remove(uuid);
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
            collectPolygonChunks(coords, region.points());
        }
        if (anchor.lightRadius() > 0) {
            collectCircleChunks(coords, anchor.x(), anchor.z(), anchor.lightRadius());
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
        if (world == null || !world.hasPreload()) {
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
            return MapOperationResult.failure(message("player.anchor-not-found", anchorId));
        }
        if (!isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure(message("player.anchor-locked"));
        }
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        World world = resolveWorld(anchor.worldId());
        if (world == null) {
            return MapOperationResult.failure(message("player.anchor-world-unloaded", anchor.worldId()));
        }
        PreparedItemConsumption preparedItems = prepareItemConsumption(player, anchor.teleportItems());
        if (!preparedItems.success()) {
            return MapOperationResult.failure(preparedItems.message());
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
        MapOperationResult itemResult = applyItemConsumption(player, preparedItems);
        if (!itemResult.success()) {
            rollbackCurrencies(player, currencyReceipt.successfulCharges());
            return itemResult;
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
                    center == null ? 0 : center.getZ()
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
                    anchor.unlock().radius()
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

    /** 世界ID→MC维度键的最近成功解析缓存：BSkyBlock 等动态世界卸载瞬间 Bukkit.getWorld 返回 null，用缓存兜底防下发空维度。 */
    private static final ConcurrentMap<String, String> RESOLVED_DIMENSION_CACHE = new ConcurrentHashMap<>();

    private static String resolveDimension(WorldDefinition world) {
        World bukkitWorld = resolveWorld(world.id());
        if (bukkitWorld != null) {
            String key = bukkitWorld.getKey().toString();
            RESOLVED_DIMENSION_CACHE.put(world.id(), key);
            return key;
        }
        return RESOLVED_DIMENSION_CACHE.getOrDefault(world.id(), "");
    }

    /** worldId → Bukkit World：精确匹配优先，退回 case-insensitive 遍历（文件名可能含大写）。 */
    private static World resolveWorld(String worldId) {
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
    private Location resolveWorldSpawn(World world) {
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

    /** 世界预加载区域 → 多边形并集（preload.radius 圆心=配置 center 或出生点链，16 边形近似）。 */
    private List<List<int[]>> preloadRegionPoints(WorldDefinition world) {
        List<List<int[]>> out = new ArrayList<>(toRegionPoints(world.preloadRegions()));
        if (world.preloadRadius() > 0) {
            Location center = resolveWorldCenter(world);
            if (center != null) {
                out.add(circlePoints(center.getX(), center.getZ(), world.preloadRadius()));
            }
        }
        return out;
    }

    // ─── 预加载版本手动刷新（/axs map refresh <worldId>）────────────
    //
    // 语义：bump 计数持久化在 data/map/preload-bumps.properties，有效版本 =
    // hash(配置版本, bump)。配置改动 → 基线哈希变 → 版本自然变；手动刷新 →
    // bump+1 → 版本变。重启后 bump 不丢，版本不回落，避免二次全量重拉。
    private final Map<String, Integer> preloadVersionBumps = new ConcurrentHashMap<>();

    private File preloadBumpFile() {
        return new File(plugin.getDataFolder(), "data/map/preload-bumps.properties");
    }

    private void loadPreloadVersionBumps() {
        File file = preloadBumpFile();
        if (!file.isFile()) {
            return;
        }
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                try {
                    preloadVersionBumps.put(normalizeId(key), Integer.parseInt(props.getProperty(key).trim()));
                } catch (NumberFormatException ignored) {
                    // 单条非法跳过
                }
            }
        } catch (Exception e) {
            logger.warning("读取预加载版本刷新记录失败: " + e.getMessage());
        }
    }

    private void savePreloadVersionBumps() {
        File file = preloadBumpFile();
        file.getParentFile().mkdirs();
        java.util.Properties props = new java.util.Properties();
        for (Map.Entry<String, Integer> e : preloadVersionBumps.entrySet()) {
            props.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            props.store(out, "map preload version bumps (manual refresh counters)");
        } catch (Exception e) {
            logger.warning("写入预加载版本刷新记录失败: " + e.getMessage());
        }
    }

    /**
     * 世界有效预加载版本号：手动 bump 优先于配置哈希基线。
     * payload 下发与 terrainPush sourceVersion 必须同源，否则客户端永判过期。
     */
    private int effectivePreloadVersion(WorldDefinition world) {
        Integer bump = preloadVersionBumps.get(world.id());
        if (bump == null) {
            return world.preloadVersion();
        }
        return Math.max(1, java.util.Objects.hash(world.preloadVersion(), bump) & 0x7FFFFFFF);
    }

    /**
     * 管理员手动刷新某世界的预加载版本：bump+1 持久化。
     * 下次快照下发新版本号，客户端发现 sourceVersion 过期自动重拉。
     */
    public MapOperationResult refreshPreloadVersion(String worldId) {
        WorldDefinition world = configuration.worlds().get(normalizeId(worldId));
        if (world == null) {
            return MapOperationResult.failure(message("refresh.world-unknown", worldId));
        }
        if (!world.hasPreload()) {
            return MapOperationResult.failure(message("refresh.world-no-preload", world.id()));
        }
        int bump = preloadVersionBumps.merge(world.id(), 1, Integer::sum);
        savePreloadVersionBumps();
        return MapOperationResult.success(
            message("refresh.done", world.id(), effectivePreloadVersion(world)));
    }

    /**
     * 世界中心点：配置 preload.center 优先，否则 ess→Bukkit 出生点链。
     * 双用途：预加载圆心 + 客户端浏览非当前世界的默认视野中心。
     */
    private Location resolveWorldCenter(WorldDefinition world) {
        World bukkitWorld = resolveWorld(world.id());
        if (world.hasPreloadCenter()) {
            return new Location(bukkitWorld, world.preloadCenterX(), 0, world.preloadCenterZ());
        }
        return resolveWorldSpawn(bukkitWorld);
    }

    /** 世界预加载覆盖的 MapChunk 坐标集合（64² 网格，逐项 {cx,cz}）。
     *  圆形部分实时取当前 spawn（/mv setspawn 即生效），
     *  多边形部分按 WorldDefinition 缓存（配置重载后新实例自然换键）。 */
    private final Map<WorldDefinition, Set<Long>> preloadRegionCoordCache = new ConcurrentHashMap<>();

    private List<int[]> preloadChunkCoords(WorldDefinition world) {
        Set<Long> coords = new HashSet<>();
        if (world.preloadRadius() > 0) {
            Location center = resolveWorldCenter(world);
            if (center != null) {
                collectCircleChunks(coords, center.getX(), center.getZ(), world.preloadRadius());
            }
        }
        coords.addAll(preloadRegionChunkCoords(world));
        List<int[]> out = new ArrayList<>(coords.size());
        for (long packed : coords) {
            out.add(new int[]{(int) (packed >> 32), (int) packed});
        }
        return out;
    }

    /** 多边形预加载区域覆盖的块坐标（打包 long），按 WorldDefinition 缓存。 */
    private Set<Long> preloadRegionChunkCoords(WorldDefinition world) {
        Set<Long> cached = preloadRegionCoordCache.get(world);
        if (cached != null) {
            return cached;
        }
        Set<Long> coords = new HashSet<>();
        for (PolygonRegion region : world.preloadRegions()) {
            collectPolygonChunks(coords, region.points());
        }
        preloadRegionCoordCache.putIfAbsent(world, coords);
        return coords;
    }

    /**
     * 圆形区域覆盖到的 MapChunk 集合。判定用"块中心距 ≤ r + 块对角线半长"
     * （多收边缘块无害——推送量略大但不漏）。
     */
    private static void collectCircleChunks(Set<Long> out, double cx, double cz, double radius) {
        int min = (int) Math.floor((cx - radius) / 64.0);
        int max = (int) Math.floor((cx + radius) / 64.0);
        int zMin = (int) Math.floor((cz - radius) / 64.0);
        int zMax = (int) Math.floor((cz + radius) / 64.0);
        double limit = radius + 45.3; // 64/2 * √2 ≈ 45.25
        double limitSq = limit * limit;
        for (int ccx = min; ccx <= max; ccx++) {
            for (int ccz = zMin; ccz <= zMax; ccz++) {
                double dx = ccx * 64.0 + 32 - cx;
                double dz = ccz * 64.0 + 32 - cz;
                if (dx * dx + dz * dz <= limitSq) {
                    out.add(((long) ccx << 32) | (ccz & 0xffffffffL));
                }
            }
        }
    }

    /**
     * 多边形区域覆盖到的 MapChunk 集合：包围盒内逐块判定——
     * 块中心在多边形内、或任一顶点落在块内、或多边形任一边中点落在块内。
     */
    private static void collectPolygonChunks(Set<Long> out, List<int[]> points) {
        if (points == null || points.size() < 3) {
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        for (int ccx = (int) Math.floor(minX / 64.0); ccx <= (int) Math.floor(maxX / 64.0); ccx++) {
            for (int ccz = (int) Math.floor(minZ / 64.0); ccz <= (int) Math.floor(maxZ / 64.0); ccz++) {
                int bx = ccx * 64, bz = ccz * 64;
                if (pointInPolygon(points, bx + 32.0, bz + 32.0) || chunkTouchesPolygon(points, bx, bz)) {
                    out.add(((long) ccx << 32) | (ccz & 0xffffffffL));
                }
            }
        }
    }

    /** 顶点/边中点是否落在 64² 块内（含边界）。 */
    private static boolean chunkTouchesPolygon(List<int[]> points, int bx, int bz) {
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] a = points.get(i), b = points.get(j);
            if (a[0] >= bx && a[0] < bx + 64 && a[1] >= bz && a[1] < bz + 64) {
                return true;
            }
            double mx = (a[0] + b[0]) / 2.0, mz = (a[1] + b[1]) / 2.0;
            if (mx >= bx && mx < bx + 64 && mz >= bz && mz < bz + 64) {
                return true;
            }
        }
        return false;
    }

    /** 射线法判点是否在多边形内。 */
    private static boolean pointInPolygon(List<int[]> pts, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
            double xi = pts.get(i)[0], zi = pts.get(i)[1];
            double xj = pts.get(j)[0], zj = pts.get(j)[1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 圆 → 16 边形顶点列表（与 MapSnapshotBuilder 的 circleToPolygon 同款，service 侧自用）。 */
    private static List<int[]> circlePoints(double cx, double cz, double radius) {
        List<int[]> points = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            points.add(new int[]{
                (int) Math.round(cx + radius * Math.cos(angle)),
                (int) Math.round(cz + radius * Math.sin(angle))
            });
        }
        return points;
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

    private String formatTeleportCostText(AnchorDefinition anchor) {
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

    /** 服务端侧文本截断（客户端输入框有 length 上限，伪造包可绕过）。 */
    private static String clipText(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
