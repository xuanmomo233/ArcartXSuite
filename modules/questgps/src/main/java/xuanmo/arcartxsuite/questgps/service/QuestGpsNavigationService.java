package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
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
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.NavigationPointDefinition;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.QuestDefinition;

/**
 * QuestGPS 导航服务。
 * <p>
 * 属于 {@code questgps} 模块，负责管理玩家任务/子任务的路径点追踪、
 * 导航标记模型显示以及 Chemdah/Adyeshach 动态坐标解析。
 * 支持 view（配置坐标）、chemdah（任务系统动态坐标）、npc（Adyeshach 实体定位）
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
    private final ConcurrentMap<UUID, TrackingSnapshot> trackingStates = new ConcurrentHashMap<>();
    /** 玩家 → (trackKey → 最近解析的导航点)。 */
    private final ConcurrentMap<java.util.UUID, Map<String, NavigationPoint>> trackingPoints = new ConcurrentHashMap<>();

    private record TrackingSnapshot(Map<String, TrackingState> entries, Map<String, TrackingState> primaryByQuest) {
        private TrackingSnapshot(LinkedHashMap<String, TrackingState> entries) {
            this(Collections.unmodifiableMap(entries), latestByQuest(entries));
        }

        private static Map<String, TrackingState> latestByQuest(Map<String, TrackingState> entries) {
            Map<String, TrackingState> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (TrackingState state : entries.values()) result.put(state.questId(), state);
            return Collections.unmodifiableMap(result);
        }
    }

    private boolean runtimeReady;
    private NavigationMarkerService markerService;
    private NavigationTextureMarkerService textureMarkerService;
    private NavigationTargetService targetService;
    /** 导航坐标变更监听（地图模块同步外部目标坐标用），可空。 */
    private volatile PositionListener positionListener;

    /**
     * 导航坐标变更监听器。坐标失效时 {@code point} 为 null。
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
        if (listener == null || player == null) {
            return;
        }
        TrackingState state = trackingStates(player).get(trackKey);
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
                NavigationPoint point = resolved == null ? null : trackingPoint(player, context.trackKey()).orElse(null);
                notifyPositionListener(player, context.trackKey(), point);
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
     * @return 当前只读追踪快照
     */
    public Map<String, TrackingState> trackingStates(Player player) {
        if (player == null) {
            return Map.of();
        }
        var snapshot = trackingStates.get(player.getUniqueId());
        return snapshot == null ? Map.of() : snapshot.entries();
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
        var snapshot = trackingStates.get(player.getUniqueId());
        return snapshot != null && snapshot.primaryByQuest().containsKey(questId.trim());
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
     * @param player             玩家，为 {@code null} 时仅检查 view 配置
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先检查的子任务 ID 列表
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasQuestPoint(Player player, String questId, List<String> prioritizedTaskIds) {
        QuestDefinition quest = navigableQuest(questId);
        return quest != null && (hasViewQuestPoint(quest, prioritizedTaskIds) || canResolveNative(player)
            && trackerBridge.resolveQuestLocation(player, quest.id(), prioritizedTaskIds).isPresent());
    }

    /**
     * 判断指定子任务是否存在可用的导航坐标点。
     *
     * @param player  玩家，为 {@code null} 时仅检查 view 配置
     * @param questId 任务 ID
     * @param taskId  子任务 ID
     * @return 存在可用坐标点时返回 {@code true}
     */
    public boolean hasTaskPoint(Player player, String questId, String taskId) {
        QuestDefinition quest = navigableQuest(questId);
        return quest != null && (viewTaskPoint(quest, taskId) != null || canResolveNative(player)
            && trackerBridge.resolveTaskLocation(player, quest.id(), taskId).isPresent());
    }

    private boolean canResolveNative(Player player) {
        return chemdahNavigationEnabled() && player != null && player.isOnline();
    }

    /**
     * 判断任务追踪能否建立追踪态（与 {@link #trackQuest} 的实际成功条件一致）。
     * <p>
     * 与 {@link #hasQuestPoint} 的区别：本方法判断"能否追踪"，而非"有无坐标点"。
     * Chemdah 配置了原生坐标来源时允许先建立追踪态，稍后解析动态目标；
     * 只有展示组件提供坐标时也可以独立建立导航。
     *
     * @param player             玩家
     * @param questId            任务 ID
     * @param prioritizedTaskIds 优先解析的子任务 ID 列表
     * @return 调用 {@code trackQuest} 可建立追踪态时返回 {@code true}
     */
    public boolean questTrackable(Player player, String questId, List<String> prioritizedTaskIds) {
        QuestDefinition quest = navigableQuest(questId);
        return quest != null && player != null && player.isOnline()
            && (hasViewQuestPoint(quest, prioritizedTaskIds)
                || chemdahNavigationEnabled() && trackerBridge.hasQuestCenter(quest.id(), prioritizedTaskIds));
    }

    /**
     * 判断子任务追踪能否建立追踪态（与 {@link #trackTask} 的实际成功条件一致）。
     * 语义同 {@link #questTrackable}。
     */
    public boolean taskTrackable(Player player, String questId, String taskId) {
        QuestDefinition quest = navigableQuest(questId);
        return quest != null && player != null && player.isOnline()
            && (viewTaskPoint(quest, taskId) != null
                || chemdahNavigationEnabled() && trackerBridge.hasTaskCenter(quest.id(), taskId));
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
     * @return 成功建立 Suite 追踪时返回 {@code true}
     */
    public boolean trackQuest(Player player, String questId, String questName, List<String> prioritizedTaskIds) {
        if (!runtimeReady() || !questTrackable(player, questId, prioritizedTaskIds)) return false;
        Optional<NavigationPoint> point = resolveQuestPoint(player, questId, prioritizedTaskIds, questName);
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
     * @return 成功建立 Suite 追踪时返回 {@code true}
     */
    public boolean trackTask(Player player, String questId, String taskId, String questName, String taskName) {
        if (!runtimeReady() || !taskTrackable(player, questId, taskId)) return false;
        Optional<NavigationPoint> point = resolveTaskPoint(player, questId, taskId, taskName);
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
        String waypointId = buildWaypointId(questId, taskId);
        String label = mode == TrackingMode.TASK ? taskName : questName;
        String title = effectiveTitle(point == null ? "" : point.title(), label);

        TrackContext context = new TrackContext(
            trackKey, mode, questId, taskId, title,
            prioritizedTaskIds == null ? List.of() : List.copyOf(prioritizedTaskIds)
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

        var newState = new TrackingState(true, mode, safe(questId), mode == TrackingMode.TASK ? safe(taskId) : "",
            waypointId, title, questName == null || questName.isBlank() ? title : questName);
        trackingStates.compute(player.getUniqueId(), (ignored, previous) -> {
            var entries = previous == null ? new LinkedHashMap<String, TrackingState>() : new LinkedHashMap<>(previous.entries());
            entries.remove(trackKey);
            entries.put(trackKey, newState);
            return new TrackingSnapshot(entries);
        });
        if (targetService != null) {
            targetService.setTarget(player, builder.build());
        } else if (point != null) {
            trackingPoints.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>()).put(trackKey, point);
            // 目标编排服务不可用时（如测试替身）：直接下发路标，无 diff/滞回/钩子
            if (configuration.navigation().waypoint().enabled()
                && !waypointRuntime.addWaypoint(player, waypointId, title, point.styleId(), point.x(), point.y(), point.z())) {
                removeState(player.getUniqueId(), trackKey);
                return false;
            }
        }
        return true;
    }

    private void removeState(UUID uuid, String trackKey) {
        detach(uuid, (key, state) -> key.equals(trackKey));
        Map<String, NavigationPoint> points = trackingPoints.get(uuid);
        if (points != null) {
            points.remove(trackKey);
            if (points.isEmpty()) {
                trackingPoints.remove(uuid);
            }
        }
    }

    /**
     * 动态目标实时解析：chemdah → view/npc 链，供 {@link NavigationTarget} resolver 调用。
     * 同时把解析出的最新 NavigationPoint 写回 trackingPoints（地图外部目标同步读取）。
     */
    private Location resolveLive(Player player, TrackContext context) {
        Optional<NavigationPoint> resolved = context.mode() == TrackingMode.TASK
            ? resolveTaskPoint(player, context.questId(), context.taskId(), context.label())
            : resolveQuestPoint(player, context.questId(), context.prioritizedTaskIds(), context.label());
        NavigationPoint point = resolved.orElse(null);
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
        String key = trackKey(questId, taskId);
        return stopTargets(player, detach(player.getUniqueId(), (id, state) -> id.equals(key)));
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
        String needle = questId.trim();
        return stopTargets(player, detach(player.getUniqueId(), (key, state) -> state.questId().equalsIgnoreCase(needle)));
    }

    /** 供地图预览未追踪的目标。NPC 优先使用实时位置，静态坐标可作为兜底。 */
    Optional<NavigationPoint> resolveMapPoint(NavigationPoint point) {
        if (!point.npcId().isBlank() && npcBridge != null && npcBridge.isAvailable()) {
            var location = npcBridge.locateByName(point.npcId()).orElse(null);
            if (location != null && location.getWorld() != null) return Optional.of(point.withPosition(location));
        }
        return point.world().isBlank() ? Optional.empty() : Optional.of(point);
    }

    private List<TrackingState> detach(UUID playerId, BiPredicate<String, TrackingState> matches) {
        var removed = new ArrayList<TrackingState>();
        trackingStates.computeIfPresent(playerId, (ignored, snapshot) -> {
            LinkedHashMap<String, TrackingState> remaining = null;
            for (var entry : snapshot.entries().entrySet()) {
                if (!matches.test(entry.getKey(), entry.getValue())) continue;
                if (remaining == null) remaining = new LinkedHashMap<>(snapshot.entries());
                removed.add(remaining.remove(entry.getKey()));
            }
            if (remaining == null) return snapshot;
            return remaining.isEmpty() ? null : new TrackingSnapshot(remaining);
        });
        return removed;
    }

    private boolean stopTargets(Player player, List<TrackingState> removed) {
        if (removed.isEmpty()) return false;
        UUID playerId = player.getUniqueId();
        Map<String, NavigationPoint> points = trackingPoints.get(playerId);
        if (points != null) {
            for (TrackingState state : removed) points.remove(state.trackKey());
            if (points.isEmpty()) trackingPoints.remove(playerId, points);
        }
        for (TrackingState state : removed) {
            if (targetService != null) {
                targetService.removeTarget(player, state.waypointId());
            } else if (!state.waypointId().isBlank()) {
                waypointRuntime.removeWaypoint(player, state.waypointId(), false);
            }
        }
        return true;
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
        var snapshot = trackingStates.remove(player.getUniqueId());
        trackingPoints.remove(player.getUniqueId());
        if (targetService != null) {
            targetService.clearTargets(player);
        } else if (snapshot != null) {
            for (TrackingState state : snapshot.entries().values()) {
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
        var snapshot = trackingStates.get(player.getUniqueId());
        return snapshot == null ? Optional.empty() : Optional.ofNullable(snapshot.primaryByQuest().get(questId.trim()));
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

    private Optional<NavigationPoint> resolveQuestPoint(Player player, String questId,
            List<String> prioritizedTaskIds, String fallbackTitle) {
        QuestDefinition quest = navigableQuest(questId);
        if (quest == null) return Optional.empty();
        if (prioritizedTaskIds != null) {
            for (String taskId : prioritizedTaskIds) {
                NavigationPointDefinition point = viewTaskPoint(quest, taskId);
                if (point != null) return Optional.of(withQuestTeleport(quest, toNavigationPoint(questId, taskId, point, fallbackTitle)));
            }
        }
        NavigationPointDefinition point = quest.navigation().point();
        if (point != null) return Optional.of(toNavigationPoint(questId, "", point, fallbackTitle));
        if (!canResolveNative(player)) return Optional.empty();
        return trackerBridge.resolveQuestLocation(player, quest.id(), prioritizedTaskIds)
            .map(location -> fromNativeLocation(questId, "", fallbackTitle, location));
    }

    private Optional<NavigationPoint> resolveTaskPoint(Player player, String questId, String taskId, String fallbackTitle) {
        QuestDefinition quest = navigableQuest(questId);
        if (quest == null) return Optional.empty();
        NavigationPointDefinition point = viewTaskPoint(quest, taskId);
        if (point != null) return Optional.of(withQuestTeleport(quest, toNavigationPoint(questId, taskId, point, fallbackTitle)));
        if (!canResolveNative(player)) return Optional.empty();
        return trackerBridge.resolveTaskLocation(player, quest.id(), taskId)
            .map(location -> withQuestTeleport(quest, fromNativeLocation(questId, taskId, fallbackTitle, location)));
    }

    private static boolean hasViewQuestPoint(QuestDefinition quest, List<String> prioritizedTaskIds) {
        if (quest.navigation().point() != null) return true;
        if (prioritizedTaskIds != null) {
            for (String taskId : prioritizedTaskIds) {
                if (viewTaskPoint(quest, taskId) != null) return true;
            }
        }
        return false;
    }

    private static NavigationPointDefinition viewTaskPoint(QuestDefinition quest, String taskId) {
        var task = quest.task(taskId);
        return task == null ? null : task.navigation();
    }

    private NavigationPoint fromNativeLocation(String questId, String taskId, String fallbackTitle, Location location) {
        String title = effectiveTitle("", fallbackTitle);
        return new NavigationPoint(
            buildWaypointId(questId, taskId),
            title,
            effectiveStyle(""),
            location.getWorld() == null ? "" : location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            title,
            "",
            -1,
            List.of(),
            null
        );
    }

    /** 目标点未声明 teleport 时回退到任务级 navigation.point.teleport。 */
    private static NavigationPoint withQuestTeleport(QuestDefinition quest, NavigationPoint point) {
        if (point.teleport() != null) {
            return point;
        }
        NavigationPointDefinition questPoint = quest.navigation().point();
        QuestGpsModuleConfiguration.TeleportPointDefinition teleport =
            questPoint == null ? null : questPoint.teleport();
        return teleport == null ? point : point.withTeleport(teleport);
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

    private boolean chemdahNavigationEnabled() {
        return trackerBridge != null && trackerBridge.available();
    }

    private QuestDefinition navigableQuest(String questId) {
        if (!runtimeReady()) return null;
        var quest = configuration.quest(questId);
        return quest != null && quest.enabled() && quest.navigation().enabled() ? quest : null;
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
        List<String> prioritizedTaskIds
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
