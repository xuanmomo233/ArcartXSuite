package xuanmo.arcartxsuite.api.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;

/**
 * 导航路径标记服务 — 使用 Adyeshach 私有临时实体 + ArcartX 模型实现沿路径的导航可视化。
 * <p>
 * 特性:
 * - A* 寻路，智能绕开障碍物/液体/危险方块
 * - 沿路径等间距放置多个模型标记实体
 * - 定时更新：随玩家移动重新计算路径并传送/增减标记
 * - 实体复用池：尽量传送现有实体，减少创建/销毁开销
 * - 支持多模块共享：每个模块用不同的 markerIdPrefix 区分
 * - 支持多目标并行：同一玩家可同时存在多条标记链，按 markerKey 区分
 * - 距离滞回：超出 {@link NavigationMarkerConfig#pathMaxDistance()} 隐藏后，
 *   须回到 {@code maxDistance - pathReshowBuffer} 以内才重新显示，防止边界抖动
 * - 原地重定向：同一 markerKey 再次 {@link #showMarker} 只更新目标点，保留实体复用
 *
 * @since 1.5.0
 */
public final class NavigationMarkerService implements NavigationRouteRenderer {

    /** 未指定 markerKey 时的默认分组（旧单目标行为）。 */
    public static final String DEFAULT_MARKER_KEY = "default";

    private final JavaPlugin plugin;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final NavigationMarkerConfig config;
    private final Logger logger;
    private final boolean debug;
    private final String markerIdPrefix;
    /** 玩家 → (markerKey → 路径状态)，支持同一玩家多条并行路径标记。 */
    private final ConcurrentMap<UUID, ConcurrentMap<String, PlayerPathState>> playerPaths = new ConcurrentHashMap<>();

    private NavigationRouteCache routeCache = new NavigationRouteCache();
    private NavigationPathfinder pathfinder;
    private BukkitTask updateTask;
    private boolean available;

    /**
     * 创建导航标记服务。
     *
     * @param plugin          插件实例
     * @param npcBridge       Adyeshach NPC 桥接
     * @param config          标记配置
     * @param logger          日志器
     * @param debug           是否调试模式
     * @param markerIdPrefix  标记 ID 前缀（用于区分不同模块的标记）
     */
    public NavigationMarkerService(
        JavaPlugin plugin,
        AdyeshachNpcBridgeAPI npcBridge,
        NavigationMarkerConfig config,
        Logger logger,
        boolean debug,
        String markerIdPrefix
    ) {
        this.plugin = plugin;
        this.npcBridge = npcBridge;
        this.config = config;
        this.logger = logger;
        this.debug = debug;
        this.markerIdPrefix = markerIdPrefix;
    }

    public void start() {
        if (!config.enabled()) {
            logger.info("导航标记: 已在配置中禁用 (marker.enabled=false)。");
            available = false;
            return;
        }
        if (npcBridge == null) {
            logger.warning("导航标记: npcBridge 为 null，标记功能将跳过。");
            available = false;
            return;
        }
        if (!npcBridge.isAvailable()) {
            logger.warning("导航标记: Adyeshach 不可用 (npcBridge.isAvailable()=false)，标记功能将跳过。");
            available = false;
            return;
        }
        available = true;
        pathfinder = new NavigationPathfinder(config.pathMaxIterations());

        int ticks = Math.max(1, config.pathUpdateTicks());
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, ticks, ticks);

        logger.info("导航标记已启动: model=" + config.modelId() + " scale=" + config.scale()
            + " interval=" + config.pathInterval() + " maxMarkers=" + config.pathMaxMarkers()
            + " updateTicks=" + ticks);
    }

    public void shutdown() {
        available = false;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideMarker(player);
        }
        playerPaths.forEach((playerId, paths) -> paths.values().forEach(state -> cancel(playerId, state)));
        playerPaths.clear();
    }

    void setRouteCache(NavigationRouteCache routeCache) {
        if (playerPaths.isEmpty()) {
            this.routeCache = routeCache;
        }
    }

    /**
     * 设置导航目标，启动路径标记跟踪（默认分组）。
     *
     * @param player 玩家
     * @param world  目标世界
     * @param x      目标 X
     * @param y      目标 Y
     * @param z      目标 Z
     */
    public void showMarker(Player player, String world, double x, double y, double z) {
        showMarker(player, DEFAULT_MARKER_KEY, world, x, y, z);
    }

    /**
     * 设置导航目标，启动路径标记跟踪。
     * <p>
     * 同一 {@code markerKey} 重复调用为原地重定向：更新目标点并复用已有标记实体，
     * 下一次定时更新时自动重算路径，不会闪烁重建。
     *
     * @param player    玩家
     * @param markerKey 标记分组键（同一玩家可有多条并行路径标记，按此键区分/取消）
     * @param world     目标世界
     * @param x         目标 X
     * @param y         目标 Y
     * @param z         目标 Z
     * @since 1.6.0
     */
    @Override
    public void showMarker(Player player, String markerKey, String world, double x, double y, double z) {
        if (!available || player == null) {
            return;
        }
        String key = sanitizeKey(markerKey);

        World w = Bukkit.getWorld(world);
        if (w == null) {
            w = player.getWorld();
        }

        UUID playerId = player.getUniqueId();
        ConcurrentMap<String, PlayerPathState> paths =
            playerPaths.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        PlayerPathState existing = paths.get(key);
        if (existing != null && !existing.cancelled) {
            if (NavigationRouteCache.samePosition(existing.goal, w, x, y, z)) return;
            // 原地重定向：仅更新目标点，实体复用，下个 tick 自动重算
            existing.goal = new Location(w, x, y, z);
            existing.suppressedByDistance = false;
            if (debug) {
                logger.info("路径标记: 重定向 player=" + player.getName()
                    + " key=" + key + " goal=(" + x + "," + y + "," + z + ")");
            }
            return;
        }

        PlayerPathState state = new PlayerPathState(new Location(w, x, y, z), key);
        paths.put(key, state);

        if (debug) {
            logger.info("路径标记: 开始追踪 player=" + player.getName()
                + " key=" + key + " goal=(" + x + "," + y + "," + z + ")");
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!available || state.cancelled || !player.isOnline() || Bukkit.getPlayer(playerId) != player
                || paths.get(key) != state) return;
            computeAndApplyPath(player, state);
        });
    }

    /**
     * 移除玩家指定分组的路径标记。
     *
     * @param player    玩家
     * @param markerKey 标记分组键
     * @since 1.6.0
     */
    @Override
    public void hideMarker(Player player, String markerKey) {
        if (player == null) return;
        ConcurrentMap<String, PlayerPathState> paths = playerPaths.get(player.getUniqueId());
        if (paths == null) return;
        PlayerPathState state = paths.remove(sanitizeKey(markerKey));
        if (paths.isEmpty()) {
            playerPaths.remove(player.getUniqueId());
        }
        if (state == null) return;

        cancel(player.getUniqueId(), state);
        removeExcessMarkers(player, state, 0);
    }

    /**
     * 移除玩家的所有路径标记（全部分组）。
     */
    @Override
    public void hideMarker(Player player) {
        if (player == null) return;
        ConcurrentMap<String, PlayerPathState> paths = playerPaths.remove(player.getUniqueId());
        if (paths == null) return;

        for (PlayerPathState state : paths.values()) {
            cancel(player.getUniqueId(), state);
            removeExcessMarkers(player, state, 0);
        }
    }

    private void cancel(UUID playerId, PlayerPathState state) {
        state.cancelled = true;
        routeCache.remove(playerId, state.markerKeySegment, pathfinder);
    }

    /**
     * 玩家指定分组当前是否有活跃路径标记。
     *
     * @param player    玩家
     * @param markerKey 标记分组键
     * @return 存在且未取消时返回 {@code true}
     * @since 1.6.0
     */
    @Override
    public boolean hasMarker(Player player, String markerKey) {
        if (player == null) return false;
        ConcurrentMap<String, PlayerPathState> paths = playerPaths.get(player.getUniqueId());
        if (paths == null) return false;
        PlayerPathState state = paths.get(sanitizeKey(markerKey));
        return state != null && !state.cancelled;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    // ==================== 定时更新 ====================

    private void tickUpdate() {
        for (var playerEntry : playerPaths.entrySet()) {
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player == null || !player.isOnline()) {
                ConcurrentMap<String, PlayerPathState> offlinePaths = playerPaths.remove(playerEntry.getKey());
                if (offlinePaths != null) {
                    for (PlayerPathState state : offlinePaths.values()) {
                        cancel(playerEntry.getKey(), state);
                    }
                }
                continue;
            }
            for (var entry : playerEntry.getValue().entrySet()) {
                PlayerPathState state = entry.getValue();
                if (state.cancelled) continue;
                // tickUpdate 本身已在定时任务（主线程）中运行，无需嵌套调度
                if (player.isOnline() && !state.cancelled) {
                    computeAndApplyPath(player, state);
                }
            }
        }
    }

    private void computeAndApplyPath(Player player, PlayerPathState state) {
        try {
            computeAndApplyPathInternal(player, state);
        } catch (Exception ex) {
            if (debug) {
                logger.warning("路径计算异常: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }
        }
    }

    private void computeAndApplyPathInternal(Player player, PlayerPathState state) {
        Location playerLoc = player.getLocation();
        Location goal = state.goal;

        if (playerLoc.getWorld() == null || goal.getWorld() == null
            || !playerLoc.getWorld().equals(goal.getWorld())) {
            state.renderedRoute = null;
            removeExcessMarkers(player, state, 0);
            return;
        }

        double distance = playerLoc.distance(goal);
        if (distance > config.pathMaxDistance()) {
            if (!state.suppressedByDistance) {
                state.suppressedByDistance = true;
                state.renderedRoute = null;
                removeExcessMarkers(player, state, 0);
            }
            return;
        }
        // 距离滞回：已抑制状态须回到 maxDistance - reshowBuffer 以内才恢复，
        // 防止玩家在最大距离边界抖动时标记链反复出现/消失。
        double reshowDistance = Math.max(0.0, config.pathMaxDistance() - config.pathReshowBuffer());
        if (state.suppressedByDistance && distance > reshowDistance) {
            return;
        }
        state.suppressedByDistance = false;

        List<Location> simplified = routeCache.route(player.getUniqueId(), state.markerKeySegment,
            pathfinder, playerLoc, goal);
        if (state.renderedRoute == simplified) return;
        List<Location> points = NavigationPathfinder.sampleOrientedPath(
            simplified, config.pathInterval(), config.pathMaxMarkers());

        double yOffset = config.yOffset();
        for (Location point : points) {
            point.add(0, yOffset, 0);
        }

        // 已在主线程中，无需嵌套调度
        if (!player.isOnline() || state.cancelled) return;
        state.renderedRoute = null;
        if (applyPathMarkers(player, state, points)) state.renderedRoute = simplified;
    }

    private boolean applyPathMarkers(Player player, PlayerPathState state, List<Location> points) {
        int needed = points.size();
        boolean applied = true;
        for (int i = 0; i < needed; i++) {
            Location point = points.get(i);
            if (i < state.markers.size()) {
                SpawnedMarker current = state.markers.get(i);
                if (current.sent().equals(point)) continue;
                Location sent = point.clone();
                if (npcBridge.teleportMarker(player, current.id(), point)) {
                    state.markers.set(i, new SpawnedMarker(current.id(), current.entity(), sent));
                } else {
                    applied = false;
                }
                continue;
            }

            UUID playerId = player.getUniqueId();
            String markerId = markerIdPrefix + playerId.toString().substring(0, 8) + "_" + state.markerKeySegment + "_" + i;
            Location sent = point.clone();
            Object entity = npcBridge.spawnPrivateMarker(player, markerId, point);
            if (entity == null) {
                applied = false;
                break;
            }
            state.markers.add(new SpawnedMarker(markerId, entity, sent));

            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!available || state.cancelled || !player.isOnline() || Bukkit.getPlayer(playerId) != player
                    || index >= state.markers.size() || state.markers.get(index).entity() != entity) return;
                npcBridge.applyModelForPlayer(player, entity, config.modelId(), config.scale());
                if (config.animation() != null && !config.animation().isBlank()) {
                    npcBridge.applyAnimationForPlayer(player, entity, config.animation(), 1.0, 200, -1);
                }
            }, 2L);
        }

        removeExcessMarkers(player, state, needed);
        return applied;
    }

    private void removeExcessMarkers(Player player, PlayerPathState state, int keepCount) {
        while (state.markers.size() > keepCount) {
            int last = state.markers.size() - 1;
            npcBridge.removePrivateMarker(player, state.markers.get(last).id());
            state.markers.remove(last);
        }
    }

    private static String sanitizeKey(String markerKey) {
        if (markerKey == null || markerKey.isBlank()) {
            return DEFAULT_MARKER_KEY;
        }
        String sanitized = markerKey.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return sanitized.length() > 32 ? sanitized.substring(0, 32) : sanitized;
    }

    // ==================== 玩家状态 ====================

    private record SpawnedMarker(String id, Object entity, Location sent) {}

    private static final class PlayerPathState {
        volatile Location goal;
        final List<SpawnedMarker> markers = new ArrayList<>();
        final String markerKeySegment;
        List<Location> renderedRoute;
        volatile boolean cancelled;
        /** 距离滞回：true 表示已因超出最大距离被抑制，须进入重显示距离内才恢复。 */
        volatile boolean suppressedByDistance;

        PlayerPathState(Location goal, String markerKeySegment) {
            this.goal = goal;
            this.markerKeySegment = markerKeySegment;
        }
    }
}
