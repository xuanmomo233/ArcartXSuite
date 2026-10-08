package xuanmo.arcartxsuite.questgps.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI;
import xuanmo.arcartxsuite.api.util.NavigationMarkerService;
import xuanmo.arcartxsuite.api.util.NavigationTarget;
import xuanmo.arcartxsuite.api.util.NavigationTargetService;
import xuanmo.arcartxsuite.api.util.NavigationTextureMarkerService;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahTrackerBridge;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * QuestGPS 导航服务。
 * <p>
 * 属于 {@code questgps} 模块，负责管理玩家任务/子任务的路径点追踪、
 * 导航标记模型显示以及 Chemdah/Adyeshach 动态坐标解析。
 * 支持 overlay（配置坐标）、chemdah（任务系统动态坐标）、npc（Adyeshach 实体定位）
 * 三种坐标来源；玩家可同时追踪多个目标（追踪态/到达判定并行），
 * 但屏幕路标与路径标记链只跟随"最新激活且同世界"的目标（避免多路标误导玩家）。
 * 坐标推送、到达滞回判定、跨世界抑制由共享 {@link NavigationTargetService} 完成。
 */
public final class QuestGpsNavigationService {

    private final QuestGpsModuleConfiguration configuration;
    private final WaypointRuntime waypointRuntime;
    private final WaypointBridgeAPI waypointBridge;
    private final WorldTextureBridgeAPI worldTextureBridge;
    private final Logger logger;
    private final JavaPlugin plugin;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final ChemdahTrackerBridge trackerBridge;
    /** 玩家 → (trackKey → 追踪状态)，同一玩家可并行追踪多个目标。 */
    private final ConcurrentMap<java.util.UUID, LinkedHashMap<String, TrackingState>> trackingStates = new ConcurrentHashMap<>();
    /** 玩家 → (trackKey → 最近解析的导航点)。 */
    private final ConcurrentMap<java.util.UUID, Map<String, NavigationPoint>> trackingPoints = new ConcurrentHashMap<>();

    private boolean runtimeReady;
    private NavigationMarkerService markerService;
    private NavigationTextureMarkerService textureMarkerService;
    private NavigationTargetService targetService;
    /** 导航坐标变更监听（地图模块同步外部目标坐标用），可空。 */
    private volatile PositionListener positionListener;

    /**
     * 导航坐标变更监听器：激活/刷新路径点时回调，携带追踪状态便于按任务区分。
     */
    public interface PositionListener {
        void onPositionUpdate(Player player, TrackingState state, NavigationPoint point);
    }

    /**
     * 设置导航坐标更新监听：激活/刷新路径点时回调，用于地图外部目标坐标跟随。
     *
     * @param listener 监听器，{@code null} 清除
     */
    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    private void notifyPositionListener(Player player, String trackKey, NavigationPoint point) {
        PositionListener listener = positionListener;
        if (listener == null || player == null || point == null) {
            return;
        }
        Map<String, TrackingState> states = trackingStates.get(player.getUniqueId());
        TrackingState state = states == null ? null : states.get(trackKey);
        if (state == null) {
            return;
        }
        try {
            listener.onPositionUpdate(player, state, point);
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
     * 共享导航编排服务（diff 推送/到达判定/跨世界处理）。start() 且导航就绪后非空。
     *
     * @return 目标服务实例，未就绪时返回 null
     */
    public NavigationTargetService targetService() {
        return targetService;
    }

    /**
     * 构造导航服务，内部将 {@link WaypointBridgeAPI} 包装为 {@link BridgeWaypointRuntime}。
     *
     * @param plugin         宿主插件实例
     * @param logger         日志器
     * @param configuration  模块配置
     * @param waypointBridge 路径点桥接 API
     * @param npcBridge      Adyeshach NPC 桥接 API，用于导航标记与 NPC 动态目标
     * @param trackerBridge  Chemdah 追踪桥接，用于动态坐标解析
     * @param worldTextureBridge 世界贴图桥接 API，用于贴图路径提示（可空，空时贴图导航不启用）
     */
    public QuestGpsNavigationService(
        JavaPlugin plugin,
        Logger logger,
        QuestGpsModuleConfiguration configuration,
        WaypointBridgeAPI waypointBridge,
        AdyeshachNpcBridgeAPI npcBridge,
        ChemdahTrackerBridge trackerBridge,
        WorldTextureBridgeAPI worldTextureBridge
    ) {
        this(
            plugin,
            configuration,
            new BridgeWaypointRuntime(waypointBridge),
            waypointBridge,
            worldTextureBridge,
            logger,
            npcBridge,
            trackerBridge
        );
    }

    QuestGpsNavigationService(
        JavaPlugin plugin,
        QuestGpsModuleConfiguration configuration,
        WaypointRuntime waypointRuntime,
        WaypointBridgeAPI waypointBridge,
        WorldTextureBridgeAPI worldTextureBridge,
        Logger logger,
        AdyeshachNpcBridgeAPI npcBridge,
        ChemdahTrackerBridge trackerBridge
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.waypointRuntime = waypointRuntime;
        this.waypointBridge = waypointBridge;
        this.worldTextureBridge = worldTextureBridge;
        this.logger = logger;
        this.npcBridge = npcBridge;
        this.trackerBridge = trackerBridge;
    }

    /** 启动导航运行时、标记服务与共享目标编排服务。 */
    public void start() {
        runtimeReady = configuration.navigation().enabled() && waypointRuntime.initialize("QuestGPS 导航");
        if (configuration.navigation().modelMarker().enabled()) {
            if (configuration.debug()) logger.info("QuestGPS: 正在初始化导航标记 (Adyeshach + ArcartX)...");
            npcBridge.setDebug(configuration.debug());
            boolean adyAvailable = npcBridge.initialize();
            if (configuration.debug()) logger.info("QuestGPS: AdyeshachNpcBridgeAPI.initialize() = " + adyAvailable);
            markerService = new NavigationMarkerService(
                plugin, npcBridge, configuration.navigation().modelMarker(), logger,
                configuration.debug(), "questgps_path_"
            );
            markerService.start();
        } else {
            logger.info("QuestGPS: 导航标记已在配置中禁用 (navigation.model-marker.enabled=false)");
        }
        if (configuration.navigation().textureMarker().enabled()) {
            if (worldTextureBridge == null) {
                logger.warning("QuestGPS: 贴图导航已启用但 WorldTexture 桥接为 null，贴图提示将跳过。");
            } else {
                textureMarkerService = new NavigationTextureMarkerService(
                    plugin, worldTextureBridge, configuration.navigation().textureMarker(), logger,
                    configuration.debug(), "questgps_tex_"
                );
                textureMarkerService.start();
            }
        }
        if (runtimeReady && waypointBridge != null) {
            targetService = new NavigationTargetService(
                plugin,
                waypointBridge,
                markerService,
                logger,
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
            targetService.setPositionChangedHandler((player, target, resolved) -> {
                Object attachment = target.attachment();
                if (!(attachment instanceof TrackContext context)) {
                    return;
                }
                NavigationPoint point = trackingPoint(player, context.trackKey()).orElse(null);
                if (point != null) {
                    notifyPositionListener(player, context.trackKey(), point);
                }
            });
            targetService.start();
        }
    }

    /** 关闭导航服务，清除所有玩家追踪并释放运行时资源。 */
    public void shutdown() {
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (chemdahNavigationEnabled()) {
                trackerBridge.stopAll(player);
            }
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
     * 玩家全部追踪状态（trackKey → 状态，激活顺序）。
     *
     * @param player 玩家
     * @return 追踪状态映射的副本
     */
    public Map<String, TrackingState> trackingStates(Player player) {
        if (player == null) {
            return Map.of();
        }
        Map<String, TrackingState> states = trackingStates.get(player.getUniqueId());
        // LinkedHashMap 保序：values() 顺序 = 激活顺序，"主追踪"取最后
        return states == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /** @return 玩家是否有任意追踪中的目标 */
    public boolean hasAnyTracking(Player player) {
        return player != null && !trackingStates(player).isEmpty();
    }

    /** @return 指定任务是否有任意追踪中的目标（任务级或子任务级） */
    public boolean isTrackingQuest(Player player, String questId) {
        if (player == null || questId == null) {
            return false;
        }
        String needle = questId.trim().toLowerCase(Locale.ROOT);
        for (TrackingState state : trackingStates(player).values()) {
            if (state.questId().equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    /** @return 指定子任务是否追踪中 */
    public boolean isTrackingTask(Player player, String questId, String taskId) {
        return player != null && trackingStates(player).containsKey(trackKey(questId, taskId));
    }

    /** @return 玩家追踪中的任务 ID 集合（含任务级与子任务级所属任务） */
    public java.util.Set<String> trackedQuestIds(Player player) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (TrackingState state : trackingStates(player).values()) {
            result.add(state.questId());
        }
        return result;
    }

    /**
     * 获取指定追踪键的最近解析坐标点。
     *
     * @param player   玩家
     * @param trackKey 追踪键（questId 或 questId|taskId）
     * @return 导航坐标点
     */
    public Optional<NavigationPoint> trackingPoint(Player player, String trackKey) {
        if (player == null || trackKey == null) {
            return Optional.empty();
        }
        Map<String, NavigationPoint> points = trackingPoints.get(player.getUniqueId());
        return points == null ? Optional.empty() : Optional.ofNullable(points.get(trackKey));
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
     * 判断任务追踪能否建立追踪态（与 {@link #trackQuest} 的实际成功条件一致）。
     * <p>
     * 与 {@link #hasQuestPoint} 的区别：本方法判断"能否追踪"，而非"有无坐标点"。
     * Chemdah 追踪在线时（chemdah/hybrid 模式），{@code trackQuest} 允许无坐标点
     * 激活——追踪态照样建立，HUD 卡片照常显示（仅隐藏导航行）；overlay 独存模式
     * 下无坐标点则激活必然失败，此时才要求 overlay 点真实存在。
     *
     * @param player             玩家
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先解析的子任务 ID 列表
     * @return 调用 {@code trackQuest} 可建立追踪态时返回 {@code true}
     */
    public boolean questTrackable(Player player, String questId, List<String> prioritizedTaskIds) {
        if (!runtimeReady() || player == null || !player.isOnline()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            return true;
        }
        return hasOverlayQuestPoint(questId, prioritizedTaskIds);
    }

    /**
     * 判断子任务追踪能否建立追踪态（与 {@link #trackTask} 的实际成功条件一致）。
     * 语义同 {@link #questTrackable}。
     */
    public boolean taskTrackable(Player player, String questId, String taskId) {
        if (!runtimeReady() || player == null || !player.isOnline()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            return true;
        }
        return hasOverlayTaskPoint(questId, taskId);
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
     * 为玩家开启任务级导航追踪（与其他目标并行）。
     *
     * @param player             玩家
     * @param questId            任务 ID
     * @param questName          任务名称（用于路径点标题）
     * @param prioritizedTaskIds 优先解析的子任务 ID 列表
     * @return 成功激活或进入 Chemdah 追踪时返回 {@code true}
     */
    public boolean trackQuest(Player player, String questId, String questName, List<String> prioritizedTaskIds) {
        if (player == null || !player.isOnline() || !runtimeReady()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            trackerBridge.startQuestTracking(player);
        }
        Optional<NavigationPoint> point = resolveQuestPoint(player, questId, prioritizedTaskIds, questName);
        if (point.isEmpty() && !chemdahNavigationEnabled()) {
            return false;
        }
        return activate(player, TrackingMode.QUEST, questId, "", questName, "", prioritizedTaskIds, point.orElse(null));
    }

    /**
     * 为玩家开启子任务级导航追踪（与其他目标并行）。
     *
     * @param player    玩家
     * @param questId   任务 ID
     * @param taskId    子任务 ID
     * @param questName 任务名称（%quest% 占位符）
     * @param taskName  子任务名称（用于路径点标题）
     * @return 成功激活或进入 Chemdah 追踪时返回 {@code true}
     */
    public boolean trackTask(Player player, String questId, String taskId, String questName, String taskName) {
        if (player == null || !player.isOnline() || !runtimeReady()) {
            return false;
        }
        if (chemdahNavigationEnabled()) {
            trackerBridge.startQuestTracking(player);
        }
        Optional<NavigationPoint> point = resolveTaskPoint(player, questId, taskId, taskName);
        if (point.isEmpty() && !chemdahNavigationEnabled()) {
            return false;
        }
        return activate(player, TrackingMode.TASK, questId, taskId, questName, taskName, List.of(), point.orElse(null));
    }

    private boolean activate(
        Player player,
        TrackingMode mode,
        String questId,
        String taskId,
        String questName,
        String taskName,
        List<String> prioritizedTaskIds,
        NavigationPoint point
    ) {
        String trackKey = trackKey(questId, taskId);
        String waypointId = point == null ? buildWaypointId(questId, taskId) : point.waypointId();
        String label = mode == TrackingMode.TASK ? taskName : questName;
        String title = effectiveTitle(point == null ? "" : point.title(), label);

        TrackContext context = new TrackContext(
            trackKey, mode, questId, taskId, title,
            prioritizedTaskIds == null ? List.of() : List.copyOf(prioritizedTaskIds),
            point
        );

        NavigationTarget.Builder builder = NavigationTarget.builder(waypointId)
            .title(title)
            .styleId(point == null ? effectiveStyle("") : point.styleId())
            .resolver(p -> resolveLive(p, context))
            .attachment(context)
            .placeholder("label", title)
            .placeholder("quest", questName == null || questName.isBlank() ? title : questName)
            .placeholder("objective", taskName == null ? "" : taskName);
        if (point != null && !point.world().isBlank()) {
            builder.world(point.world()).position(point.x(), point.y(), point.z());
        }
        String template = configuration.navigation().waypoint().titleTemplateText();
        if (!template.isBlank()) {
            builder.titleTemplate(template);
        }
        double finishRange = point != null && point.finishRange() >= 0
            ? point.finishRange()
            : configuration.navigation().finishRange();
        if (finishRange > 0) {
            builder.finishRange(finishRange);
        }
        List<String> finishCommands = point != null && !point.finishCommands().isEmpty()
            ? point.finishCommands()
            : configuration.navigation().finishCommands();
        builder.finishCommands(finishCommands);

        trackingStates.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>())
            .put(trackKey, new TrackingState(true, mode, safe(questId),
                mode == TrackingMode.TASK ? safe(taskId) : "", waypointId, title,
                questName == null || questName.isBlank() ? title : questName));
        if (point != null) {
            trackingPoints.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>())
                .put(trackKey, point);
        }
        if (targetService != null) {
            targetService.setTarget(player, builder.build());
        } else if (point != null && configuration.navigation().waypoint().enabled()) {
            // 目标编排服务不可用时（如测试替身）：直接下发路标，无 diff/滞回/钩子
            if (!waypointRuntime.addWaypoint(player, waypointId, title, point.styleId(), point.x(), point.y(), point.z())) {
                removeState(player.getUniqueId(), trackKey);
                return false;
            }
        }
        return true;
    }

    private void removeState(java.util.UUID uuid, String trackKey) {
        Map<String, TrackingState> states = trackingStates.get(uuid);
        if (states != null) {
            states.remove(trackKey);
            if (states.isEmpty()) {
                trackingStates.remove(uuid);
            }
        }
        Map<String, NavigationPoint> points = trackingPoints.get(uuid);
        if (points != null) {
            points.remove(trackKey);
            if (points.isEmpty()) {
                trackingPoints.remove(uuid);
            }
        }
    }

    /**
     * 动态目标实时解析：chemdah → overlay/npc 链，供 {@link NavigationTarget} resolver 调用。
     * 同时把解析出的最新 NavigationPoint 写回 trackingPoints（地图外部目标同步读取）。
     */
    private Location resolveLive(Player player, TrackContext context) {
        Optional<NavigationPoint> resolved = context.mode() == TrackingMode.TASK
            ? resolveTaskPoint(player, context.questId(), context.taskId(), context.label())
            : resolveQuestPoint(player, context.questId(), context.prioritizedTaskIds(), context.label());
        NavigationPoint point = resolved.orElse(context.initialPoint());
        Map<String, NavigationPoint> points = trackingPoints.get(player.getUniqueId());
        if (point == null) {
            if (points != null) {
                points.remove(context.trackKey());
            }
            return null;
        }
        Location location = null;
        if (!point.npcId().isBlank() && npcBridge != null && npcBridge.isAvailable()) {
            location = npcBridge.locateByName(point.npcId()).orElse(null);
        }
        if (location == null && !point.world().isBlank()) {
            World world = findWorld(point.world());
            if (world != null) {
                location = new Location(world, point.x(), point.y(), point.z());
            }
        }
        if (location == null) {
            if (points != null) {
                points.remove(context.trackKey());
            }
            return null;
        }
        NavigationPoint effective = point.withPosition(location);
        if (points != null) {
            points.put(context.trackKey(), effective);
        } else {
            trackingPoints.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>())
                .put(context.trackKey(), effective);
        }
        return location;
    }

    /**
     * 取消玩家单个追踪目标。
     *
     * @param player 玩家
     * @param questId 任务 ID
     * @param taskId 子任务 ID（空 = 任务级目标）
     * @return 存在该追踪并已移除时返回 {@code true}
     */
    public boolean untrack(Player player, String questId, String taskId) {
        if (player == null) {
            return false;
        }
        java.util.UUID uuid = player.getUniqueId();
        LinkedHashMap<String, TrackingState> states = trackingStates.get(uuid);
        String key = trackKey(questId, taskId);
        TrackingState removed = states == null ? null : states.remove(key);
        if (removed == null) {
            return false;
        }
        Map<String, NavigationPoint> points = trackingPoints.get(uuid);
        if (points != null) {
            points.remove(key);
            if (points.isEmpty()) {
                trackingPoints.remove(uuid);
            }
        }
        if (targetService != null) {
            targetService.removeTarget(player, removed.waypointId());
        } else if (!removed.waypointId().isBlank()) {
            waypointRuntime.removeWaypoint(player, removed.waypointId(), false);
        }
        if (states != null && states.isEmpty()) {
            trackingStates.remove(uuid);
            if (chemdahNavigationEnabled()) {
                trackerBridge.stopAll(player);
            }
        }
        return true;
    }

    /**
     * 取消指定任务的全部追踪目标（任务级 + 其所有子任务级）。
     *
     * @return 是否存在该任务的追踪并被移除
     */
    public boolean untrackQuest(Player player, String questId) {
        if (player == null || questId == null) {
            return false;
        }
        String needle = questId.trim().toLowerCase(Locale.ROOT);
        List<String> keys = trackingStates(player).entrySet().stream()
            .filter(entry -> entry.getValue().questId().equalsIgnoreCase(needle))
            .map(Map.Entry::getKey)
            .toList();
        boolean removed = false;
        for (String key : keys) {
            TrackingState state = trackingStates(player).get(key);
            if (state != null) {
                removed |= untrack(player, state.questId(), state.taskId());
            }
        }
        return removed;
    }

    /**
     * 清除玩家的全部导航追踪。
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
        Map<String, TrackingState> states = trackingStates.remove(player.getUniqueId());
        trackingPoints.remove(player.getUniqueId());
        if (targetService != null) {
            targetService.clearTargets(player);
        } else if (states != null) {
            for (TrackingState state : states.values()) {
                if (!state.waypointId().isBlank()) {
                    waypointRuntime.removeWaypoint(player, state.waypointId(), false);
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

    /**
     * 指定任务最近激活的追踪状态（同任务存在多个目标时取最后注册的）。
     *
     * @param player  玩家
     * @param questId 任务 ID
     * @return 最近追踪状态
     */
    public Optional<TrackingState> primaryStateForQuest(Player player, String questId) {
        if (player == null || questId == null) {
            return Optional.empty();
        }
        TrackingState latest = null;
        for (TrackingState state : trackingStates(player).values()) {
            if (state.questId().equalsIgnoreCase(questId.trim())) {
                latest = state;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** 玩家退出时清理追踪与目标状态（不派发包）。 */
    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        trackingStates.remove(player.getUniqueId());
        trackingPoints.remove(player.getUniqueId());
        if (targetService != null) {
            targetService.handleQuit(player);
        }
        if (markerService != null) {
            markerService.hideMarker(player);
        }
        if (textureMarkerService != null) {
            textureMarkerService.hideMarker(player);
        }
    }

    /**
     * 定位 Adyeshach NPC 所在世界名（预览跨世界追踪用）。
     *
     * @param npcId NPC 名/ID
     * @return 世界名，找不到时返回空串
     */
    public String locateNpcWorld(String npcId) {
        if (npcBridge == null || npcId == null || npcId.isBlank()) {
            return "";
        }
        return npcBridge.locateByName(npcId)
            .map(location -> location.getWorld() == null ? "" : location.getWorld().getName())
            .orElse("");
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
            return chemdahPoint.map(point -> withQuestTeleport(questId, point));
        }
        if (!overlayNavigationEnabled()) {
            return Optional.empty();
        }
        return resolveOverlayQuestPoint(questId, prioritizedTaskIds, fallbackTitle)
            .map(point -> withQuestTeleport(questId, point));
    }

    private Optional<NavigationPoint> resolveTaskPoint(Player player, String questId, String taskId, String fallbackTitle) {
        if (!runtimeReady()) {
            return Optional.empty();
        }
        Optional<NavigationPoint> chemdahPoint = resolveChemdahPoint(player, questId, taskId, fallbackTitle);
        if (chemdahPoint.isPresent()) {
            return chemdahPoint.map(point -> withQuestTeleport(questId, point));
        }
        if (!overlayNavigationEnabled()) {
            return Optional.empty();
        }
        return resolveOverlayTaskPoint(questId, taskId, fallbackTitle)
            .map(point -> withQuestTeleport(questId, point));
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
                effectiveTitle("", fallbackTitle),
                "",
                -1,
                List.of(),
                null
            ));
    }

    /** 目标点未声明 teleport 时回退到任务级 navigation.point.teleport。 */
    private NavigationPoint withQuestTeleport(String questId, NavigationPoint point) {
        if (point.teleport() != null) {
            return point;
        }
        QuestGpsModuleConfiguration.QuestDefinition quest = configuration.quest(questId);
        QuestGpsModuleConfiguration.NavigationPointDefinition questPoint =
            quest == null ? null : quest.navigation().point();
        QuestGpsModuleConfiguration.TeleportPointDefinition teleport =
            questPoint == null ? null : questPoint.teleport();
        return teleport == null ? point : point.withTeleport(teleport);
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
            blankTo(colored(point.mapLabel()), title),
            point.npc(),
            point.finishRange(),
            point.finishCommands(),
            point.teleport()
        );
    }

    private String effectiveStyle(String configuredStyleId) {
        return waypointRuntime.resolveStyleId(configuredStyleId, configuration.navigation().waypoint().styleId(), "QuestGPS");
    }

    private String effectiveTitle(String configuredTitle, String fallbackTitle) {
        String resolved = safe(configuredTitle).isBlank() ? blankTo(fallbackTitle, "任务导航") : configuredTitle.trim();
        return MessageProvider.translateColors(resolved);
    }

    private static String colored(String value) {
        return value == null || value.isEmpty() ? "" : MessageProvider.translateColors(value);
    }

    private String buildWaypointId(String questId, String taskId) {
        String base = configuration.navigation().questIdPrefix() + sanitize(questId);
        return taskId == null || taskId.isBlank() ? base : base + "__" + sanitize(taskId);
    }

    private static String trackKey(String questId, String taskId) {
        String base = safe(questId).toLowerCase(Locale.ROOT);
        return safe(taskId).isBlank() ? base : base + "|" + safe(taskId).toLowerCase(Locale.ROOT);
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

    /** 世界查找：精确匹配优先，其次大小写不敏感（配置的世界名可能大小写不一致）。 */
    private static World findWorld(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(name.trim())) {
                return w;
            }
        }
        return null;
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
     * 追踪上下文（NavigationTarget.attachment），供 resolver/回调反查。
     */
    private record TrackContext(
        String trackKey,
        TrackingMode mode,
        String questId,
        String taskId,
        String label,
        List<String> prioritizedTaskIds,
        NavigationPoint initialPoint
    ) {
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
     * @param questName  所属任务显示名（多追踪 HUD 卡片标题用）
     */
    public record TrackingState(
        boolean active,
        TrackingMode mode,
        String questId,
        String taskId,
        String waypointId,
        String label,
        String questName
    ) {

        public boolean matchesTask(String questId, String taskId) {
            return active
                && mode == TrackingMode.TASK
                && this.questId.equalsIgnoreCase(safe(questId))
                && this.taskId.equalsIgnoreCase(safe(taskId));
        }

        /** 该状态在追踪表中的键（questId 或 questId|taskId，小写规范化）。 */
        public String trackKey() {
            return QuestGpsNavigationService.trackKey(questId, taskId);
        }
    }

    /**
     * 导航坐标点。
     *
     * @param waypointId     路径点标识符
     * @param title          路径点标题
     * @param styleId        路径点样式 ID
     * @param world          世界名称
     * @param x              X 坐标
     * @param y              Y 坐标
     * @param z              Z 坐标
     * @param mapLabel       地图标记标签
     * @param npcId          Adyeshach NPC 名/ID（非空时跟随 NPC 实时位置）
     * @param finishRange    到达触发半径（&lt; 0 继承全局配置）
     * @param finishCommands 到达命令（空列表继承全局配置）
     * @param teleport       跨世界传送点（可空）
     */
    public record NavigationPoint(
        String waypointId,
        String title,
        String styleId,
        String world,
        double x,
        double y,
        double z,
        String mapLabel,
        String npcId,
        double finishRange,
        List<String> finishCommands,
        QuestGpsModuleConfiguration.TeleportPointDefinition teleport
    ) {

        /** 返回以动态定位坐标覆盖本点坐标/世界的副本。 */
        public NavigationPoint withPosition(Location location) {
            return new NavigationPoint(
                waypointId, title, styleId,
                location.getWorld() == null ? world : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                mapLabel, npcId, finishRange, finishCommands, teleport
            );
        }

        /** 返回 teleport 字段替换后的副本。 */
        public NavigationPoint withTeleport(QuestGpsModuleConfiguration.TeleportPointDefinition teleport) {
            return new NavigationPoint(
                waypointId, title, styleId, world, x, y, z, mapLabel, npcId, finishRange, finishCommands, teleport
            );
        }
    }

    /**
     * 路径点运行时接口，抽象路径点桥接的初始化与样式解析操作。
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
