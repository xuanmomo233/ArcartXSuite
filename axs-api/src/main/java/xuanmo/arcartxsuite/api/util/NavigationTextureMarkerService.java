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
import xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WorldTexturePulse;

/**
 * 导航贴图标记服务 — 沿 A* 寻路结果向单个玩家下发 ArcartX 世界贴图（WorldTexture）路径提示。
 * <p>
 * 与 {@link NavigationMarkerService}（Adyeshach 实体 + 模型）互补：
 * 贴图渲染不需要实体，仅走单玩家 {@code WORLD_TEXTURE} 包，开销更低且天然玩家私有。
 * 特性:
 * - A* 寻路，智能绕开障碍物/液体/危险方块（与模型标记共用 {@link NavigationPathfinder}）
 * - 沿路径等间距放置贴图，可按序错开脉冲延迟形成波浪动效
 * - 定时更新：随玩家移动重算路径并按序号 diff 重发贴图（同 id 覆盖）
 * - 支持多模块共享：每个模块用不同的 textureIdPrefix 区分
 * - 支持多目标并行：同一玩家可同时存在多条贴图链，按 markerKey 区分
 * - 距离滞回：超出 {@link NavigationTextureMarkerConfig#pathMaxDistance()} 隐藏后，
 *   须回到 {@code maxDistance - pathReshowBuffer} 以内才重新显示，防止边界抖动
 * - 原地重定向：同一 markerKey 再次 {@link #showMarker} 只更新目标点
 *
 * @since 1.7.0
 */
public final class NavigationTextureMarkerService implements NavigationRouteRenderer {

    /** 位置漂移重发阈值（平方距离，避免每个 tick 全量重发）。 */
    private static final double RESPAWN_DISTANCE_SQ = 0.3 * 0.3;
    /** yaw 变化重发阈值（度）。 */
    private static final float RESPAWN_YAW_DELTA = 5f;

    private final JavaPlugin plugin;
    private final WorldTextureBridgeAPI textureBridge;
    private final NavigationTextureMarkerConfig config;
    private final Logger logger;
    private final boolean debug;
    private final String textureIdPrefix;
    /** 玩家 → (markerKey → 路径状态)，支持同一玩家多条并行贴图链。 */
    private final ConcurrentMap<UUID, ConcurrentMap<String, PlayerPathState>> playerPaths = new ConcurrentHashMap<>();

    private NavigationRouteCache routeCache = new NavigationRouteCache();
    private NavigationPathfinder pathfinder;
    private BukkitTask updateTask;
    private boolean available;

    /**
     * 创建导航贴图标记服务。
     *
     * @param plugin          插件实例
     * @param textureBridge   世界贴图桥接（提供单玩家 spawn/remove）
     * @param config          贴图标记配置
     * @param logger          日志器
     * @param debug           是否调试模式
     * @param textureIdPrefix 贴图 ID 前缀（用于区分不同模块的贴图）
     */
    public NavigationTextureMarkerService(
        JavaPlugin plugin,
        WorldTextureBridgeAPI textureBridge,
        NavigationTextureMarkerConfig config,
        Logger logger,
        boolean debug,
        String textureIdPrefix
    ) {
        this.plugin = plugin;
        this.textureBridge = textureBridge;
        this.config = config;
        this.logger = logger;
        this.debug = debug;
        this.textureIdPrefix = textureIdPrefix;
    }

    public void start() {
        if (!config.enabled()) {
            logger.info("导航贴图标记: 已在配置中禁用 (texture-marker.enabled=false)。");
            available = false;
            return;
        }
        if (textureBridge == null || !textureBridge.isAvailable()) {
            logger.warning("导航贴图标记: WorldTexture 桥接不可用，贴图提示将跳过。");
            available = false;
            return;
        }
        available = true;
        pathfinder = new NavigationPathfinder(config.pathMaxIterations());

        int ticks = Math.max(1, config.pathUpdateTicks());
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, ticks, ticks);

        logger.info("导航贴图标记已启动: texture=" + config.texture() + " size=" + config.width() + "x" + config.height()
            + " facing=" + config.facing() + " interval=" + config.pathInterval()
            + " maxMarkers=" + config.pathMaxMarkers() + " updateTicks=" + ticks);
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
     * 设置导航目标，启动贴图路径跟踪（默认分组）。
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
     * 设置导航目标，启动贴图路径跟踪。
     * <p>
     * 同一 {@code markerKey} 重复调用为原地重定向：更新目标点，下一次定时更新自动重算路径。
     *
     * @param player    玩家
     * @param markerKey 标记分组键
     * @param world     目标世界
     * @param x         目标 X
     * @param y         目标 Y
     * @param z         目标 Z
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
            existing.goal = new Location(w, x, y, z);
            existing.suppressedByDistance = false;
            if (debug) {
                logger.info("路径贴图: 重定向 player=" + player.getName()
                    + " key=" + key + " goal=(" + x + "," + y + "," + z + ")");
            }
            return;
        }

        PlayerPathState state = new PlayerPathState(new Location(w, x, y, z), key);
        paths.put(key, state);

        if (debug) {
            logger.info("路径贴图: 开始追踪 player=" + player.getName()
                + " key=" + key + " goal=(" + x + "," + y + "," + z + ")");
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!available || state.cancelled || !player.isOnline() || Bukkit.getPlayer(playerId) != player
                || paths.get(key) != state) return;
            computeAndApplyPath(player, state);
        });
    }

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
        removeAllTextures(player, state);
    }

    @Override
    public void hideMarker(Player player) {
        if (player == null) return;
        ConcurrentMap<String, PlayerPathState> paths = playerPaths.remove(player.getUniqueId());
        if (paths == null) return;

        for (PlayerPathState state : paths.values()) {
            cancel(player.getUniqueId(), state);
            removeAllTextures(player, state);
        }
    }

    private void cancel(UUID playerId, PlayerPathState state) {
        state.cancelled = true;
        routeCache.remove(playerId, state.markerKeySegment, pathfinder);
    }

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
                logger.warning("贴图路径计算异常: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }
        }
    }

    private void computeAndApplyPathInternal(Player player, PlayerPathState state) {
        Location playerLoc = player.getLocation();
        Location goal = state.goal;

        if (playerLoc.getWorld() == null || goal.getWorld() == null
            || !playerLoc.getWorld().equals(goal.getWorld())) {
            state.renderedRoute = null;
            removeExcessTextures(player, state, 0);
            return;
        }

        double distance = playerLoc.distance(goal);
        if (distance > config.pathMaxDistance()) {
            if (!state.suppressedByDistance) {
                state.suppressedByDistance = true;
                state.renderedRoute = null;
                removeExcessTextures(player, state, 0);
            }
            return;
        }
        // 距离滞回：已抑制状态须回到 maxDistance - reshowBuffer 以内才恢复
        double reshowDistance = Math.max(0.0, config.pathMaxDistance() - config.pathReshowBuffer());
        if (state.suppressedByDistance && distance > reshowDistance) {
            return;
        }
        state.suppressedByDistance = false;

        List<Location> simplified = routeCache.route(player.getUniqueId(), state.markerKeySegment,
            pathfinder, playerLoc, goal);
        if (state.renderedRoute == simplified) return;
        List<Location> sampled = NavigationPathfinder.sampleOrientedPath(
            simplified, config.pathInterval(), config.pathMaxMarkers());

        double yOffset = config.yOffset();
        List<TexturePoint> finalPoints = new ArrayList<>(sampled.size());
        for (Location point : sampled) {
            finalPoints.add(new TexturePoint(point.getX(), point.getY() + yOffset, point.getZ(), point.getYaw()));
        }

        if (!player.isOnline() || state.cancelled) return;
        state.renderedRoute = null;
        if (applyPathTextures(player, state, goal.getWorld(), finalPoints)) state.renderedRoute = simplified;
    }

    /**
     * 按序号 diff 应用贴图：同序号位置/yaw 变化超过阈值时以同 id 重发（客户端覆盖语义），
     * 数量收缩时移除尾部多余贴图。
     */
    private boolean applyPathTextures(Player player, PlayerPathState state, World world, List<TexturePoint> points) {
        int needed = points.size();
        boolean applied = true;
        for (int i = 0; i < needed; i++) {
            TexturePoint next = points.get(i);
            SpawnedTexture current = i < state.spawned.size() ? state.spawned.get(i) : null;
            if (current != null && !current.shouldRespawn(world, next)) {
                continue;
            }
            String textureId = textureId(player, state.markerKeySegment, i);
            WorldTexturePulse pulse = config.pulseEnabled()
                ? WorldTexturePulse.looping(config.pulseFrom(), config.pulseTo(),
                    config.pulseDuration(), i * config.pulseStagger())
                : null;
            boolean sent = textureBridge.spawnForPlayer(
                player,
                textureId,
                new Location(world, next.x(), next.y(), next.z()),
                config.texture(),
                config.width(),
                config.height(),
                config.orientToPath() ? next.yaw() : 0f,
                config.pitch(),
                config.facing(),
                config.glowing(),
                config.lifeTime(),
                pulse
            );
            if (!sent) {
                applied = false;
                if (current == null) break;
                continue;
            }
            SpawnedTexture spawned = new SpawnedTexture(textureId, world, next);
            if (current == null) {
                state.spawned.add(spawned);
            } else {
                state.spawned.set(i, spawned);
            }
        }

        removeExcessTextures(player, state, needed);
        return applied;
    }

    private void removeExcessTextures(Player player, PlayerPathState state, int keepCount) {
        while (state.spawned.size() > keepCount) {
            int last = state.spawned.size() - 1;
            textureBridge.removeForPlayer(player, state.spawned.get(last).textureId());
            state.spawned.remove(last);
        }
    }

    private void removeAllTextures(Player player, PlayerPathState state) {
        removeExcessTextures(player, state, 0);
    }

    private String textureId(Player player, String markerKeySegment, int index) {
        return textureIdPrefix + player.getUniqueId().toString().substring(0, 8)
            + "_" + markerKeySegment + "_" + index;
    }

    private static String sanitizeKey(String markerKey) {
        if (markerKey == null || markerKey.isBlank()) {
            return DEFAULT_MARKER_KEY;
        }
        String sanitized = markerKey.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return sanitized.length() > 32 ? sanitized.substring(0, 32) : sanitized;
    }

    // ==================== 玩家状态 ====================

    /** 未指定 markerKey 时的默认分组（与 {@link NavigationMarkerService#DEFAULT_MARKER_KEY} 一致）。 */
    private static final String DEFAULT_MARKER_KEY = NavigationMarkerService.DEFAULT_MARKER_KEY;

    /** 期望渲染的路径点（采样后坐标 + 朝向）。 */
    private record TexturePoint(double x, double y, double z, float yaw) {
    }

    /** 已下发的贴图记录：用于 diff 判定是否需要重发。 */
    private record SpawnedTexture(String textureId, World world, TexturePoint point) {

        boolean shouldRespawn(World nextWorld, TexturePoint next) {
            if (!world.equals(nextWorld)) return true;
            double dx = point.x() - next.x();
            double dy = point.y() - next.y();
            double dz = point.z() - next.z();
            if (dx * dx + dy * dy + dz * dz > RESPAWN_DISTANCE_SQ) {
                return true;
            }
            return Math.abs(point.yaw() - next.yaw()) > RESPAWN_YAW_DELTA;
        }
    }

    private static final class PlayerPathState {
        volatile Location goal;
        final List<SpawnedTexture> spawned = new ArrayList<>();
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
