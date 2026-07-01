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
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahTrackerBridge;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

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
            this.logger,
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
            chemdahRefreshTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshChemdahTrackingPositions, interval, interval).getTaskId();
        }
    }

    public void shutdown() {
        if (chemdahRefreshTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(chemdahRefreshTaskId);
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

    public boolean hasQuestPoint(String questId, List<String> prioritizedTaskIds) {
        return hasQuestPoint(null, questId, prioritizedTaskIds);
    }

    public boolean hasTaskPoint(String questId, String taskId) {
        return hasTaskPoint(null, questId, taskId);
    }

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


