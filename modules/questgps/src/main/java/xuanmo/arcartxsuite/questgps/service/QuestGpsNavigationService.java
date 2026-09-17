package xuanmo.arcartxsuite.questgps.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahTrackerBridge;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * QuestGPS 导航服务。
 * <p>
 * 属于 {@code questgps} 模块，负责管理玩家任务/子任务的路径点追踪、
 * 导航标记模型显示以及 Chemdah 动态坐标刷新。支持 overlay（配置坐标）、
 * chemdah（任务系统动态坐标）和 hybrid（混合）三种导航模式。
 */
public final class QuestGpsNavigationService {

    private final QuestGpsModuleConfiguration configuration;
    private final WaypointRuntime waypointRuntime;
    private final Logger logger;
    private final JavaPlugin plugin;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final ChemdahTrackerBridge trackerBridge;
    private final ConcurrentMap<java.util.UUID, TrackingState> trackingStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<java.util.UUID, NavigationPoint> trackingPoints = new ConcurrentHashMap<>();

    private boolean runtimeReady;
    private QuestGpsMarkerService markerService;
    private int chemdahRefreshTaskId = -1;
    /** 导航坐标变更监听（地图模块同步外部目标坐标用），可空。 */
    private volatile java.util.function.BiConsumer<Player, NavigationPoint> positionListener;

    /**
     * 设置导航坐标更新监听：激活/刷新路径点时回调，用于地图外部目标坐标跟随。
     *
     * @param listener 监听器，{@code null} 清除
     */
    public void setPositionListener(java.util.function.BiConsumer<Player, NavigationPoint> listener) {
        this.positionListener = listener;
    }

    private void notifyPositionListener(Player player, NavigationPoint point) {
        java.util.function.BiConsumer<Player, NavigationPoint> listener = positionListener;
        if (listener == null || player == null || point == null) {
            return;
        }
        try {
            listener.accept(player, point);
        } catch (Throwable e) {
            logger.warning("QuestGPS: 位置更新监听回调失败: " + e.getMessage());
        }
    }

    /**
     * 预览任务导航坐标点（不要求已追踪）。
     * <p>
     * 供 QuestGpsService 在任务接受后即向地图推送外部目标使用。
     *
     * @param player             玩家（可为 null，仅影响 chemdah 动态坐标解析）
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先检查的子任务 ID 列表
     * @param fallbackTitle      标题兜底
     * @return 可解析的导航坐标点，不可用时返回空
     */
    public Optional<NavigationPoint> previewQuestPoint(
        Player player,
        String questId,
        List<String> prioritizedTaskIds,
        String fallbackTitle
    ) {
        return resolveQuestPoint(player, questId, prioritizedTaskIds, fallbackTitle);
    }

    /**
     * 构建任务目标 ID（与追踪 waypointId 同规则），供地图外部目标标识与回调反解。
     *
     * @param questId 任务 ID
     * @param taskId  子任务 ID（可为空）
     * @return 目标 ID
     */
    public String buildTargetId(String questId, String taskId) {
        return buildWaypointId(questId, taskId);
    }

    /**
     * 构造导航服务，内部将 {@link WaypointBridgeAPI} 包装为 {@link BridgeWaypointRuntime}。
     *
     * @param plugin         宿主插件实例
     * @param logger         日志器
     * @param configuration  模块配置
     * @param waypointBridge 路径点桥接 API
     * @param npcBridge      Adyeshach NPC 桥接 API，用于导航标记
     * @param trackerBridge  Chemdah 追踪桥接，用于动态坐标解析
     */
    public QuestGpsNavigationService(
        JavaPlugin plugin,
        Logger logger,
        QuestGpsModuleConfiguration configuration,
        WaypointBridgeAPI waypointBridge,
        AdyeshachNpcBridgeAPI npcBridge,
        ChemdahTrackerBridge trackerBridge
    ) {
        this(
            plugin,
            configuration,
            new BridgeWaypointRuntime(waypointBridge),
            logger,
            npcBridge,
            trackerBridge
        );
    }

    QuestGpsNavigationService(
        JavaPlugin plugin,
        QuestGpsModuleConfiguration configuration,
        WaypointRuntime waypointRuntime,
        Logger logger,
        AdyeshachNpcBridgeAPI npcBridge,
        ChemdahTrackerBridge trackerBridge
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.waypointRuntime = waypointRuntime;
        this.logger = logger;
        this.npcBridge = npcBridge;
        this.trackerBridge = trackerBridge;
    }

    /** 启动导航运行时与标记服务，并按配置启动 Chemdah 坐标刷新定时任务。 */
    public void start() {
        runtimeReady = configuration.navigation().enabled() && waypointRuntime.initialize("QuestGPS 导航");
        if (configuration.navigation().marker().enabled()) {
            if (configuration.debug()) logger.info("QuestGPS: 正在初始化导航标记 (Adyeshach + ArcartX)...");
            npcBridge.setDebug(configuration.debug());
            boolean adyAvailable = npcBridge.initialize();
            if (configuration.debug()) logger.info("QuestGPS: AdyeshachNpcBridgeAPI.initialize() = " + adyAvailable);
            markerService = new QuestGpsMarkerService(
                plugin, npcBridge, configuration.navigation().marker(), logger, configuration.debug()
            );
            markerService.start();
        } else {
            logger.info("QuestGPS: 导航标记已在配置中禁用 (navigation.marker.enabled=false)");
        }
        if (chemdahNavigationEnabled()) {
            int interval = Math.max(5, configuration.navigation().marker().pathUpdateTicks());
            chemdahRefreshTaskId = AxsScheduler.runTaskTimer(plugin, this::refreshChemdahTrackingPositions, interval, interval).getTaskId();
        }
    }

    /** 关闭导航服务，取消定时任务、清除所有玩家追踪并释放运行时资源。 */
    public void shutdown() {
        if (chemdahRefreshTaskId >= 0) {
            AxsScheduler.cancelTask(chemdahRefreshTaskId);
            chemdahRefreshTaskId = -1;
        }
        if (markerService != null) {
            markerService.shutdown();
            markerService = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTracking(player, true);
        }
        trackingStates.clear();
        trackingPoints.clear();
        runtimeReady = false;
        waypointRuntime.shutdown();
    }

    /** @return 导航运行时是否就绪 */
    public boolean runtimeReady() {
        return configuration.navigation().enabled() && runtimeReady;
    }

    /** @return 任务完成后是否自动移除导航 */
    public boolean removeOnFinish() {
        return configuration.navigation().removeOnFinish();
    }

    /**
     * 获取玩家的追踪状态。
     *
     * @param player 玩家，为 {@code null} 时返回空状态
     * @return 当前追踪状态，无追踪时返回 {@link TrackingState#none()}
     */
    public TrackingState trackingState(Player player) {
        if (player == null) {
            return TrackingState.none();
        }
        return trackingStates.getOrDefault(player.getUniqueId(), TrackingState.none());
    }

    /**
     * 获取玩家当前的追踪坐标点。
     *
     * @param player 玩家，为 {@code null} 时返回空
     * @return 导航坐标点，无追踪时返回空
     */
    public Optional<NavigationPoint> trackingPoint(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(trackingPoints.get(player.getUniqueId()));
    }

    /**
     * 判断指定任务是否存在可用的导航坐标点。
     *
     * @param player             玩家，为 {@code null} 时仅检查 overlay 配置
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先检查的子任务 ID 列表
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasQuestPoint(Player player, String questId, List<String> prioritizedTaskIds) {
        if (!runtimeReady()) {
            return false;
        }
        boolean overlay = overlayNavigationEnabled() && hasOverlayQuestPoint(questId, prioritizedTaskIds);
        if (!chemdahNavigationEnabled()) {
            return overlay;
        }
        if (player == null || !player.isOnline()) {
            return overlay;
        }
        if (chemdahOnlyNavigation()) {
            return true;
        }
        return overlay || trackerBridge.resolveTrackingLocation(player, questId).isPresent();
    }

    /**
     * 判断指定子任务是否存在可用的导航坐标点。
     *
     * @param player  玩家，为 {@code null} 时仅检查 overlay 配置
     * @param questId 任务 ID
     * @param taskId  子任务 ID
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasTaskPoint(Player player, String questId, String taskId) {
        if (!runtimeReady()) {
            return false;
        }
        boolean overlay = overlayNavigationEnabled() && hasOverlayTaskPoint(questId, taskId);
        if (!chemdahNavigationEnabled()) {
            return overlay;
        }
        if (player == null || !player.isOnline()) {
            return overlay;
        }
        if (chemdahOnlyNavigation()) {
            return true;
        }
        return overlay || trackerBridge.resolveTrackingLocation(player, questId).isPresent();
    }

    /**
     * 判断指定任务是否存在可用的导航坐标点（不检查玩家在线状态）。
     *
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先检查的子任务 ID 列表
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasQuestPoint(String questId, List<String> prioritizedTaskIds) {
        return hasQuestPoint(null, questId, prioritizedTaskIds);
    }

    /**
     * 判断指定子任务是否存在可用的导航坐标点（不检查玩家在线状态）。
     *
     * @param questId 任务 ID
     * @param taskId  子任务 ID
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasTaskPoint(String questId, String taskId) {
        return hasTaskPoint(null, questId, taskId);
    }

    /**
     * 为玩家开启任务级导航追踪。
     *
     * @param player             玩家
     * @param questId            任务 ID
     * @param questName          任务名称（用于路径点标题）
     * @param prioritizedTaskIds 优先解析的子任务 ID 列表
     * @return 成功激活或进入 Chemdah 追踪时返回 {@code true}
     */
    public boolean trackQuest(Player player, String questId, String questName, List<String> prioritizedTaskIds) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            trackerBridge.startQuestTracking(player);
        }
        Optional<NavigationPoint> point = resolveQuestPoint(player, questId, prioritizedTaskIds, questName);
        if (point.isPresent()) {
            return activateWaypoint(player, TrackingMode.QUEST, questId, "", point.get());
        }
        if (chemdahNavigationEnabled()) {
            trackingStates.put(
                player.getUniqueId(),
                new TrackingState(
                    true,
                    TrackingMode.QUEST,
                    safe(questId),
                    "",
                    buildWaypointId(questId, ""),
                    effectiveTitle("", questName)
                )
            );
            return true;
        }
        return false;
    }

    /**
     * 为玩家开启子任务级导航追踪。
     *
     * @param player   玩家
     * @param questId  任务 ID
     * @param taskId   子任务 ID
     * @param taskName 子任务名称（用于路径点标题）
     * @return 成功激活或进入 Chemdah 追踪时返回 {@code true}
     */
    public boolean trackTask(Player player, String questId, String taskId, String taskName) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            trackerBridge.startQuestTracking(player);
        }
        Optional<NavigationPoint> point = resolveTaskPoint(player, questId, taskId, taskName);
        if (point.isPresent()) {
            return activateWaypoint(player, TrackingMode.TASK, questId, taskId, point.get());
        }
        if (chemdahNavigationEnabled()) {
            trackingStates.put(
                player.getUniqueId(),
                new TrackingState(
                    true,
                    TrackingMode.TASK,
                    safe(questId),
                    safe(taskId),
                    buildWaypointId(questId, taskId),
                    effectiveTitle("", taskName)
                )
            );
            return true;
        }
        return false;
    }

    /**
     * 清除玩家的导航追踪。
     *
     * @param player 玩家
     * @param silent 为 {@code true} 时删除失败不输出警告日志
     */
    public void clearTracking(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        if (chemdahNavigationEnabled()) {
            trackerBridge.stopAll(player);
        }
        TrackingState state = trackingStates.remove(player.getUniqueId());
        trackingPoints.remove(player.getUniqueId());
        // 移除导航标记模型
        if (markerService != null) {
            markerService.hideMarker(player);
        }
        if (state == null || !state.active() || !runtimeReady()) {
            return;
        }
        if (!waypointRuntime.removeWaypoint(player, state.waypointId(), false) && !silent) {
            logger.warning("QuestGPS: 删除导航失败: " + state.waypointId());
        }
    }

    /**
     * 当玩家当前追踪的目标属于指定任务时，清除其追踪。
     *
     * @param player  玩家
     * @param questId 任务 ID
     */
    public void clearTrackingForQuest(Player player, String questId) {
        TrackingState state = trackingState(player);
        if (state.matchesQuest(questId) || state.questId().equalsIgnoreCase(safe(questId))) {
            clearTracking(player, true);
        }
    }

    private Optional<NavigationPoint> resolveQuestPoint(
        Player player,
        String questId,
        List<String> prioritizedTaskIds,
        String fallbackTitle
    ) {
        if (!runtimeReady()) {
            return Optional.empty();
        }
        Optional<NavigationPoint> chemdahPoint = resolveChemdahPoint(player, questId, "", fallbackTitle);
        if (chemdahPoint.isPresent()) {
            return chemdahPoint;
        }
        if (!overlayNavigationEnabled()) {
            return Optional.empty();
        }
        return resolveOverlayQuestPoint(questId, prioritizedTaskIds, fallbackTitle);
    }

    private Optional<NavigationPoint> resolveTaskPoint(Player player, String questId, String taskId, String fallbackTitle) {
        if (!runtimeReady()) {
            return Optional.empty();
        }
        Optional<NavigationPoint> chemdahPoint = resolveChemdahPoint(player, questId, taskId, fallbackTitle);
        if (chemdahPoint.isPresent()) {
            return chemdahPoint;
        }
        if (!overlayNavigationEnabled()) {
            return Optional.empty();
        }
        return resolveOverlayTaskPoint(questId, taskId, fallbackTitle);
    }

    private boolean hasOverlayQuestPoint(String questId, List<String> prioritizedTaskIds) {
        return resolveOverlayQuestPoint(questId, prioritizedTaskIds, "").isPresent();
    }

    private boolean hasOverlayTaskPoint(String questId, String taskId) {
        return resolveOverlayTaskPoint(questId, taskId, "").isPresent();
    }

    private Optional<NavigationPoint> resolveOverlayQuestPoint(
        String questId,
        List<String> prioritizedTaskIds,
        String fallbackTitle
    ) {
        QuestGpsModuleConfiguration.QuestDefinition quest = configuration.quest(questId);
        if (quest == null || !quest.navigation().enabled()) {
            return Optional.empty();
        }
        if (prioritizedTaskIds != null) {
            for (String taskId : prioritizedTaskIds) {
                Optional<NavigationPoint> taskPoint = resolveOverlayTaskPoint(questId, taskId, fallbackTitle);
                if (taskPoint.isPresent()) {
                    return taskPoint;
                }
            }
        }
        QuestGpsModuleConfiguration.NavigationPointDefinition point = quest.navigation().point();
        return point == null ? Optional.empty() : Optional.of(toNavigationPoint(questId, "", point, fallbackTitle));
    }

    private Optional<NavigationPoint> resolveOverlayTaskPoint(String questId, String taskId, String fallbackTitle) {
        QuestGpsModuleConfiguration.TaskDefinition task = configuration.task(questId, taskId);
        if (task == null || task.navigation() == null) {
            return Optional.empty();
        }
        return Optional.of(toNavigationPoint(questId, taskId, task.navigation(), fallbackTitle));
    }

    private Optional<NavigationPoint> resolveChemdahPoint(
        Player player,
        String questId,
        String taskId,
        String fallbackTitle
    ) {
        if (!chemdahNavigationEnabled() || player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return trackerBridge.resolveTrackingLocation(player, questId)
            .map(location -> new NavigationPoint(
                buildWaypointId(questId, taskId),
                effectiveTitle("", fallbackTitle),
                effectiveStyle(""),
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                effectiveTitle("", fallbackTitle)
            ));
    }

    private void refreshChemdahTrackingPositions() {
        if (!chemdahNavigationEnabled() || !runtimeReady()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            TrackingState state = trackingStates.get(player.getUniqueId());
            if (state == null || !state.active()) {
                continue;
            }
            Optional<NavigationPoint> point = resolveChemdahPoint(player, state.questId(), state.taskId(), state.label());
            if (point.isEmpty()) {
                continue;
            }
            NavigationPoint current = trackingPoints.get(player.getUniqueId());
            NavigationPoint next = point.get();
            if (current != null && samePosition(current, next)) {
                continue;
            }
            if (current == null) {
                activateWaypoint(player, state.mode(), state.questId(), state.taskId(), next);
            } else {
                refreshWaypointPosition(player, state, next);
            }
        }
    }

    private void refreshWaypointPosition(Player player, TrackingState state, NavigationPoint point) {
        if (!runtimeReady()) {
            return;
        }
        waypointRuntime.removeWaypoint(player, state.waypointId(), false);
        boolean success = waypointRuntime.addWaypoint(
            player,
            point.waypointId(),
            point.title(),
            point.styleId(),
            point.x(),
            point.y(),
            point.z()
        );
        if (!success) {
            return;
        }
        trackingPoints.put(player.getUniqueId(), point);
        if (markerService != null) {
            markerService.showMarker(player, point);
        }
        notifyPositionListener(player, point);
    }

    private static boolean samePosition(NavigationPoint current, NavigationPoint next) {
        return current.world().equalsIgnoreCase(next.world())
            && Math.abs(current.x() - next.x()) < 0.5
            && Math.abs(current.y() - next.y()) < 0.5
            && Math.abs(current.z() - next.z()) < 0.5;
    }

    private String navigationMode() {
        String mode = configuration.navigation().mode();
        if (mode == null || mode.isBlank()) {
            return "hybrid";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean chemdahNavigationEnabled() {
        return trackerBridge != null && trackerBridge.available() && !navigationMode().equals("overlay");
    }

    private boolean overlayNavigationEnabled() {
        return navigationMode().equals("overlay") || navigationMode().equals("hybrid");
    }

    private boolean chemdahOnlyNavigation() {
        return navigationMode().equals("chemdah") && chemdahNavigationEnabled();
    }

    private Optional<NavigationPoint> resolveQuestPoint(
        String questId,
        List<String> prioritizedTaskIds,
        String fallbackTitle
    ) {
        return resolveQuestPoint(null, questId, prioritizedTaskIds, fallbackTitle);
    }

    private Optional<NavigationPoint> resolveTaskPoint(String questId, String taskId, String fallbackTitle) {
        return resolveTaskPoint(null, questId, taskId, fallbackTitle);
    }

    private NavigationPoint toNavigationPoint(
        String questId,
        String taskId,
        QuestGpsModuleConfiguration.NavigationPointDefinition point,
        String fallbackTitle
    ) {
        String title = effectiveTitle(point.title(), fallbackTitle);
        return new NavigationPoint(
            buildWaypointId(questId, taskId),
            title,
            effectiveStyle(point.styleId()),
            point.world(),
            point.x(),
            point.y(),
            point.z(),
            blankTo(point.mapLabel(), title)
        );
    }

    private boolean activateWaypoint(
        Player player,
        TrackingMode mode,
        String questId,
        String taskId,
        NavigationPoint point
    ) {
        clearTracking(player, true);
        if (!runtimeReady()) {
            return false;
        }
        boolean success = waypointRuntime.addWaypoint(
            player,
            point.waypointId(),
            point.title(),
            point.styleId(),
            point.x(),
            point.y(),
            point.z()
        );
        if (!success) {
            return false;
        }
        trackingStates.put(
            player.getUniqueId(),
            new TrackingState(
                true,
                mode,
                safe(questId),
                mode == TrackingMode.TASK ? safe(taskId) : "",
                point.waypointId(),
                point.title()
            )
        );
        trackingPoints.put(player.getUniqueId(), point);
        // 显示导航标记模型
        if (markerService != null) {
            if (configuration.debug()) logger.info("QuestGPS: activateWaypoint → 调用 markerService.showMarker player=" + player.getName()
                + " point.world=" + point.world());
            markerService.showMarker(player, point);
        } else {
            logger.warning("QuestGPS: activateWaypoint → markerService 为 null，跳过标记");
        }
        notifyPositionListener(player, point);
        return true;
    }

    private String effectiveStyle(String configuredStyleId) {
        return waypointRuntime.resolveStyleId(configuredStyleId, configuration.navigation().waypointStyleId(), "QuestGPS");
    }

    private String effectiveTitle(String configuredTitle, String fallbackTitle) {
        return safe(configuredTitle).isBlank() ? blankTo(fallbackTitle, "任务导航") : configuredTitle.trim();
    }

    private String buildWaypointId(String questId, String taskId) {
        String base = configuration.navigation().questIdPrefix() + sanitize(questId);
        return taskId == null || taskId.isBlank() ? base : base + "__" + sanitize(taskId);
    }

    private static String sanitize(String value) {
        return safe(value).replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 追踪模式枚举。
     * <p>
     * 区分当前追踪的是任务级还是子任务级导航目标。
     */
    public enum TrackingMode {
        NONE,
        QUEST,
        TASK
    }

    /**
     * 玩家导航追踪状态快照。
     *
     * @param active     是否正在追踪
     * @param mode       追踪模式
     * @param questId    任务 ID
     * @param taskId     子任务 ID，任务级追踪时为空
     * @param waypointId 路径点标识符
     * @param label      追踪目标显示名称
     */
    public record TrackingState(
        boolean active,
        TrackingMode mode,
        String questId,
        String taskId,
        String waypointId,
        String label
    ) {

        public static TrackingState none() {
            return new TrackingState(false, TrackingMode.NONE, "", "", "", "");
        }

        public boolean matchesQuest(String questId) {
            return active && mode == TrackingMode.QUEST && this.questId.equalsIgnoreCase(safe(questId));
        }

        public boolean matchesTask(String questId, String taskId) {
            return active
                && mode == TrackingMode.TASK
                && this.questId.equalsIgnoreCase(safe(questId))
                && this.taskId.equalsIgnoreCase(safe(taskId));
        }
    }

    /**
     * 导航坐标点。
     *
     * @param waypointId 路径点标识符
     * @param title      路径点标题
     * @param styleId    路径点样式 ID
     * @param world      世界名称
     * @param x          X 坐标
     * @param y          Y 坐标
     * @param z          Z 坐标
     * @param mapLabel   地图标记标签
     */
    public record NavigationPoint(
        String waypointId,
        String title,
        String styleId,
        String world,
        double x,
        double y,
        double z,
        String mapLabel
    ) {
    }

    /**
     * 路径点运行时接口，抽象路径点桥接的增删与样式解析操作。
     */
    interface WaypointRuntime {
        boolean initialize(String ownerLabel);

        void shutdown();

        String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel);

        boolean addWaypoint(Player player, String waypointId, String title, String styleId, double x, double y, double z);

        boolean removeWaypoint(Player player, String waypointId, boolean animated);
    }

    private static final class BridgeWaypointRuntime implements WaypointRuntime {

        private final WaypointBridgeAPI bridge;

        private BridgeWaypointRuntime(WaypointBridgeAPI bridge) {
            this.bridge = bridge;
        }

        @Override
        public boolean initialize(String ownerLabel) {
            return bridge.initialize(ownerLabel);
        }

        @Override
        public void shutdown() {
            bridge.shutdown();
        }

        @Override
        public String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel) {
            return bridge.resolveStyleId(preferredStyleId, fallbackStyleId, ownerLabel);
        }

        @Override
        public boolean addWaypoint(Player player, String waypointId, String title, String styleId, double x, double y, double z) {
            return bridge.addWaypoint(player, waypointId, title, styleId, x, y, z);
        }

        @Override
        public boolean removeWaypoint(Player player, String waypointId, boolean animated) {
            return bridge.removeWaypoint(player, waypointId, animated);
        }
    }
}


