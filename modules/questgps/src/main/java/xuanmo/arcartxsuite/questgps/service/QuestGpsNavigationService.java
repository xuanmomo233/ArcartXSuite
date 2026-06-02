package xuanmo.arcartxsuite.questgps.service;

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
import xuanmo.arcartxsuite.bridge.AdyeshachNpcBridge;
import xuanmo.arcartxsuite.bridge.ArcartXWaypointBridge;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

public final class QuestGpsNavigationService {

    private final QuestGpsModuleConfiguration configuration;
    private final WaypointRuntime waypointRuntime;
    private final Logger logger;
    private final JavaPlugin plugin;
    private final ConcurrentMap<java.util.UUID, TrackingState> trackingStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<java.util.UUID, NavigationPoint> trackingPoints = new ConcurrentHashMap<>();

    private boolean runtimeReady;
    private QuestGpsMarkerService markerService;

    public QuestGpsNavigationService(
        JavaPlugin plugin,
        QuestGpsModuleConfiguration configuration
    ) {
        this(
            plugin,
            configuration,
            new BridgeWaypointRuntime(new ArcartXWaypointBridge(plugin)),
            plugin.getLogger()
        );
    }

    QuestGpsNavigationService(
        JavaPlugin plugin,
        QuestGpsModuleConfiguration configuration,
        WaypointRuntime waypointRuntime,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.waypointRuntime = waypointRuntime;
        this.logger = logger;
    }

    public void start() {
        runtimeReady = configuration.navigation().enabled() && waypointRuntime.initialize("QuestGPS 导航");
        if (configuration.navigation().marker().enabled()) {
            if (configuration.debug()) logger.info("QuestGPS: 正在初始化导航标记 (Adyeshach + ArcartX)...");
            AdyeshachNpcBridge npcBridge = new AdyeshachNpcBridge(plugin);
            npcBridge.setDebug(configuration.debug());
            boolean adyAvailable = npcBridge.initialize();
            if (configuration.debug()) logger.info("QuestGPS: AdyeshachNpcBridge.initialize() = " + adyAvailable);
            markerService = new QuestGpsMarkerService(
                plugin, npcBridge, configuration.navigation().marker(), logger, configuration.debug()
            );
            markerService.start();
        } else {
            logger.info("QuestGPS: 导航标记已在配置中禁用 (navigation.marker.enabled=false)");
        }
    }

    public void shutdown() {
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

    public boolean runtimeReady() {
        return configuration.navigation().enabled() && runtimeReady;
    }

    public boolean removeOnFinish() {
        return configuration.navigation().removeOnFinish();
    }

    public TrackingState trackingState(Player player) {
        if (player == null) {
            return TrackingState.none();
        }
        return trackingStates.getOrDefault(player.getUniqueId(), TrackingState.none());
    }

    public Optional<NavigationPoint> trackingPoint(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(trackingPoints.get(player.getUniqueId()));
    }

    public boolean hasQuestPoint(String questId, List<String> prioritizedTaskIds) {
        return runtimeReady() && resolveQuestPoint(questId, prioritizedTaskIds, "").isPresent();
    }

    public boolean hasTaskPoint(String questId, String taskId) {
        return runtimeReady() && resolveTaskPoint(questId, taskId, "").isPresent();
    }

    public boolean trackQuest(Player player, String questId, String questName, List<String> prioritizedTaskIds) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Optional<NavigationPoint> point = resolveQuestPoint(questId, prioritizedTaskIds, questName);
        return point.filter(navigationPoint -> activateWaypoint(player, TrackingMode.QUEST, questId, "", navigationPoint)).isPresent();
    }

    public boolean trackTask(Player player, String questId, String taskId, String taskName) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Optional<NavigationPoint> point = resolveTaskPoint(questId, taskId, taskName);
        return point.filter(navigationPoint -> activateWaypoint(player, TrackingMode.TASK, questId, taskId, navigationPoint)).isPresent();
    }

    public void clearTracking(Player player, boolean silent) {
        if (player == null) {
            return;
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
            logger.warning("QuestGPS 删除导航失败: " + state.waypointId());
        }
    }

    public void clearTrackingForQuest(Player player, String questId) {
        TrackingState state = trackingState(player);
        if (state.matchesQuest(questId) || state.questId().equalsIgnoreCase(safe(questId))) {
            clearTracking(player, true);
        }
    }

    private Optional<NavigationPoint> resolveQuestPoint(
        String questId,
        List<String> prioritizedTaskIds,
        String fallbackTitle
    ) {
        if (!runtimeReady()) {
            return Optional.empty();
        }
        QuestGpsModuleConfiguration.QuestDefinition quest = configuration.quest(questId);
        if (quest == null || !quest.navigation().enabled()) {
            return Optional.empty();
        }
        if (prioritizedTaskIds != null) {
            for (String taskId : prioritizedTaskIds) {
                Optional<NavigationPoint> taskPoint = resolveTaskPoint(questId, taskId, fallbackTitle);
                if (taskPoint.isPresent()) {
                    return taskPoint;
                }
            }
        }
        QuestGpsModuleConfiguration.NavigationPointDefinition point = quest.navigation().point();
        return point == null ? Optional.empty() : Optional.of(toNavigationPoint(questId, "", point, fallbackTitle));
    }

    private Optional<NavigationPoint> resolveTaskPoint(String questId, String taskId, String fallbackTitle) {
        if (!runtimeReady()) {
            return Optional.empty();
        }
        QuestGpsModuleConfiguration.TaskDefinition task = configuration.task(questId, taskId);
        if (task == null || task.navigation() == null) {
            return Optional.empty();
        }
        return Optional.of(toNavigationPoint(questId, taskId, task.navigation(), fallbackTitle));
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

    public enum TrackingMode {
        NONE,
        QUEST,
        TASK
    }

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

    interface WaypointRuntime {
        boolean initialize(String ownerLabel);

        void shutdown();

        String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel);

        boolean addWaypoint(Player player, String waypointId, String title, String styleId, double x, double y, double z);

        boolean removeWaypoint(Player player, String waypointId, boolean animated);
    }

    private static final class BridgeWaypointRuntime implements WaypointRuntime {

        private final ArcartXWaypointBridge bridge;

        private BridgeWaypointRuntime(ArcartXWaypointBridge bridge) {
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
