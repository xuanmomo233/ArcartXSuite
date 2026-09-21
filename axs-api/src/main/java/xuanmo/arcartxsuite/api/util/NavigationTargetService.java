package xuanmo.arcartxsuite.api.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;

/**
 * 导航目标编排服务 — 多目标并行导航的共享状态管理层。
 * <p>
 * 由模块持有（每模块一个实例），复用 {@link WaypointBridgeAPI} 与
 * {@link NavigationRouteRenderer} 渲染器（{@link NavigationMarkerService} 模型标记、
 * {@link NavigationTextureMarkerService} 贴图提示，可经 {@link #addRouteRenderer} 并存）完成客户端同步。职责：
 * <ul>
 *   <li>多目标追踪：同一玩家的目标集合以 navId 为键并行登记，各自做位置解析与到达判定</li>
 *   <li>单路标单路线：为避免多路标/多路线误导玩家，仅"最新激活且同世界"的目标
 *       下发客户端路标与路径标记链；其余目标仅保留数据追踪（HUD/地图标记/到达判定不受影响）</li>
 *   <li>Diff 推送：仅在目标新增/坐标变化/标题变化/删除时发对应增删包，不做全量重建</li>
 *   <li>动态目标：目标可携带 resolver（NPC / 任务坐标等），每轮刷新重新解析位置</li>
 *   <li>跨世界处理：目标世界与玩家不同时抑制推送并按间隔回调提示</li>
 *   <li>到达判定滞回：进入 finishRange 触发一次到达回调/命令钩子；须离开
 *       finishRange + rearmBuffer 才重新武装。导航本身保持到显式取消</li>
 *   <li>描述模板化：titleTemplate 支持 %key% 占位符与内置 %distance%/%world%/%x%/%y%/%z%/%label%</li>
 * </ul>
 *
 * @since 1.6.0
 */
public final class NavigationTargetService {

    /** 目标状态事件回调（跨世界提示 / 到达 / 位置变更）。 */
    @FunctionalInterface
    public interface TargetCallback {
        void onEvent(Player player, NavigationTarget target, Location resolved);
    }

    /**
     * 服务运行参数。
     *
     * @param refreshTicks          主刷新间隔（tick，默认 20）
     * @param worldHintIntervalMillis 跨世界提示节流间隔（毫秒，默认 30000）
     * @param rearmBuffer           默认滞回缓冲（格，默认 3.0；目标可经 rearmBuffer 覆盖）
     * @param resolverCacheMillis   动态位置解析缓存（毫秒，默认 1500；降低 NPC/任务查询频率）
     * @param waypoints             是否向客户端推送路标（false 时仅保留追踪态/到达判定/路径标记，不发路标包）
     */
    public record Config(
        int refreshTicks,
        long worldHintIntervalMillis,
        double rearmBuffer,
        long resolverCacheMillis,
        boolean waypoints
    ) {
        public static Config defaults() {
            return new Config(20, 30_000L, 3.0, 1_500L, true);
        }
    }

    private final JavaPlugin plugin;
    private final WaypointBridgeAPI waypointBridge;
    private final Logger logger;
    private final Config config;
    /** 路径渲染器列表（模型标记/贴图提示等可并存），tick 线程只读遍历。 */
    private final java.util.List<NavigationRouteRenderer> routeRenderers = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 玩家 → 期望目标集合（navId → 状态，LinkedHashMap 保序：后注册在后）。 */
    private final ConcurrentMap<UUID, LinkedHashMap<String, TargetState>> desiredTargets = new ConcurrentHashMap<>();
    /** 玩家 → 已推送签名（navId → 客户端当前路标内容）。 */
    private final ConcurrentMap<UUID, Map<String, PushedTarget>> pushedTargets = new ConcurrentHashMap<>();
    /** 玩家 → (navId → 上次跨世界提示时间)。 */
    private final ConcurrentMap<UUID, Map<String, Long>> worldHintTimestamps = new ConcurrentHashMap<>();
    /** 玩家 → (navId → 解析缓存)。 */
    private final ConcurrentMap<UUID, Map<String, CachedLocation>> resolverCache = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;
    private volatile TargetCallback worldHintHandler;
    private volatile TargetCallback arriveHandler;
    private volatile TargetCallback positionChangedHandler;

    public NavigationTargetService(
        JavaPlugin plugin,
        WaypointBridgeAPI waypointBridge,
        NavigationMarkerService markerService,
        Logger logger,
        Config config
    ) {
        this.plugin = plugin;
        this.waypointBridge = waypointBridge;
        if (markerService != null) {
            routeRenderers.add(markerService);
        }
        this.logger = logger;
        this.config = config == null ? Config.defaults() : config;
    }

    /**
     * 追加一个路径渲染器（如 {@link NavigationTextureMarkerService} 贴图提示）。
     * 与构造传入的模型标记渲染器并存，显示/隐藏调用会广播到全部渲染器。
     * 一般在 {@link #start()} 前后注册均可，线程安全。
     *
     * @param renderer 渲染器实例，{@code null} 忽略
     * @since 1.7.0
     */
    public void addRouteRenderer(NavigationRouteRenderer renderer) {
        if (renderer != null) {
            routeRenderers.add(renderer);
        }
    }

    public void start() {
        int ticks = Math.max(1, config.refreshTicks());
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            cleanupPlayer(player.getUniqueId(), player);
        }
        desiredTargets.clear();
        pushedTargets.clear();
        worldHintTimestamps.clear();
        resolverCache.clear();
    }

    /** 玩家退出时清理全部目标与缓存（不发包——客户端已离线）。 */
    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        desiredTargets.remove(uuid);
        pushedTargets.remove(uuid);
        worldHintTimestamps.remove(uuid);
        resolverCache.remove(uuid);
        hideAllRoutes(player);
    }

    /**
     * 注册/更新导航目标（同 navId 覆盖）。立即尝试推送一次，不等下个刷新周期。
     *
     * @param player 目标玩家
     * @param target 目标描述
     * @return 目标合法且已登记时返回 {@code true}
     */
    public boolean setTarget(Player player, NavigationTarget target) {
        if (player == null || !player.isOnline() || target == null || target.navId().isBlank()) {
            return false;
        }
        LinkedHashMap<String, TargetState> targets =
            desiredTargets.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        targets.put(target.navId(), new TargetState(target));
        // 立即推送一次（调用方一般在主线程的 UI 回调中）
        refreshPlayer(player, targets);
        return true;
    }

    /**
     * 移除单个导航目标（路标 + 路径标记一并清除）。
     *
     * @return 目标存在并已移除时返回 {@code true}
     */
    public boolean removeTarget(Player player, String navId) {
        if (player == null || navId == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        LinkedHashMap<String, TargetState> targets = desiredTargets.get(uuid);
        boolean removed = targets != null && targets.remove(navId) != null;
        if (targets != null && targets.isEmpty()) {
            desiredTargets.remove(uuid);
        }
        removePushed(player, navId);
        Map<String, CachedLocation> cache = resolverCache.get(uuid);
        if (cache != null) {
            cache.remove(navId);
        }
        Map<String, Long> hinted = worldHintTimestamps.get(uuid);
        if (hinted != null) {
            hinted.remove(navId);
        }
        return removed;
    }

    /**
     * 清除玩家的全部导航目标。
     *
     * @return 被移除的目标数量
     */
    public int clearTargets(Player player) {
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        LinkedHashMap<String, TargetState> targets = desiredTargets.remove(uuid);
        Map<String, PushedTarget> pushed = pushedTargets.remove(uuid);
        worldHintTimestamps.remove(uuid);
        resolverCache.remove(uuid);
        Set<String> navIds = pushed != null ? Set.copyOf(pushed.keySet()) : Set.of();
        for (String navId : navIds) {
            dropWaypoint(player, navId);
        }
        hideAllRoutes(player);
        return targets == null ? 0 : targets.size();
    }

    /** @return 玩家当前登记中的目标 id 集合（插入序） */
    public Set<String> activeTargetIds(Player player) {
        if (player == null) {
            return Set.of();
        }
        LinkedHashMap<String, TargetState> targets = desiredTargets.get(player.getUniqueId());
        return targets == null ? Set.of() : Set.copyOf(targets.keySet());
    }

    /** @return 指定目标是否登记中 */
    public boolean isNavigating(Player player, String navId) {
        if (player == null || navId == null) {
            return false;
        }
        LinkedHashMap<String, TargetState> targets = desiredTargets.get(player.getUniqueId());
        return targets != null && targets.containsKey(navId);
    }

    /**
     * 最近注册的目标 id（即当前路径标记跟随的目标）。
     *
     * @return 目标 id，无目标时返回空
     */
    public Optional<String> primaryTargetId(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        LinkedHashMap<String, TargetState> targets = desiredTargets.get(player.getUniqueId());
        if (targets == null || targets.isEmpty()) {
            return Optional.empty();
        }
        String last = null;
        for (String navId : targets.keySet()) {
            last = navId;
        }
        return Optional.ofNullable(last);
    }

    /**
     * 目标最近一轮解析出的位置（未解析过/无法定位时为空）。
     *
     * @return 最近解析位置
     */
    public Optional<Location> resolvedLocation(Player player, String navId) {
        if (player == null || navId == null) {
            return Optional.empty();
        }
        LinkedHashMap<String, TargetState> targets = desiredTargets.get(player.getUniqueId());
        TargetState state = targets == null ? null : targets.get(navId);
        Location resolved = state == null ? null : state.lastResolved;
        return Optional.ofNullable(resolved == null ? null : resolved.clone());
    }

    /** 跨世界提示回调（目标在其他世界、被抑制显示时节流触发）。 */
    public void setWorldHintHandler(TargetCallback handler) {
        this.worldHintHandler = handler;
    }

    /** 到达回调（进入 finishRange 边沿触发一次；导航保持不取消）。 */
    public void setArriveHandler(TargetCallback handler) {
        this.arriveHandler = handler;
    }

    /** 目标解析位置变化回调（动态目标移动时触发，供外部目标同步等场景）。 */
    public void setPositionChangedHandler(TargetCallback handler) {
        this.positionChangedHandler = handler;
    }

    // ==================== 刷新主循环 ====================

    private void tick() {
        for (Map.Entry<UUID, LinkedHashMap<String, TargetState>> entry : desiredTargets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                cleanupPlayer(entry.getKey(), null);
                continue;
            }
            refreshPlayer(player, entry.getValue());
        }
        pushedTargets.keySet().removeIf(uuid -> !desiredTargets.containsKey(uuid));
    }

    private void refreshPlayer(Player player, LinkedHashMap<String, TargetState> targets) {
        UUID uuid = player.getUniqueId();
        Map<String, PushedTarget> pushed = pushedTargets.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());

        // 1) 移除差分：已推送但不再登记的目标（先快照 key，避免 removeIf 谓词内改 map）
        for (String navId : new ArrayList<>(pushed.keySet())) {
            if (!targets.containsKey(navId)) {
                pushed.remove(navId);
                dropWaypoint(player, navId);
                hideRoute(player, navId);
            }
        }

        String playerWorld = player.getWorld() == null ? "" : player.getWorld().getName();
        Location playerLoc = player.getLocation();

        // 2) 第一轮：解析所有目标位置；确定主目标（最新激活且同世界的目标）
        String routeNavId = null;
        for (Map.Entry<String, TargetState> entry : targets.entrySet()) {
            TargetState state = entry.getValue();
            Location resolved = resolve(player, entry.getKey(), state);
            state.lastResolved = resolved;
            if (resolved != null && sameWorld(resolved, playerWorld)) {
                routeNavId = entry.getKey();
            }
        }

        // 3) 第二轮：主目标 diff 推送路标+路径标记；其余目标仅做位置上报与到达判定（不显示信标/路线）
        for (Map.Entry<String, TargetState> entry : targets.entrySet()) {
            String navId = entry.getKey();
            TargetState state = entry.getValue();
            NavigationTarget target = state.target;
            Location resolved = state.lastResolved;

            if (resolved == null) {
                suppressPushed(player, navId, pushed);
                continue;
            }
            if (!sameWorld(resolved, playerWorld)) {
                suppressPushed(player, navId, pushed);
                maybeWorldHint(player, navId, target, resolved);
                continue;
            }

            boolean primary = navId.equals(routeNavId);
            if (!primary) {
                // 非主目标：只保留数据追踪（位置上报 + 到达判定），不渲染路标/路线，避免误导玩家
                suppressPushed(player, navId, pushed);
                maybeFirePositionChanged(player, target, state, resolved);
                updateArrival(player, target, state, resolved, playerLoc.distance(resolved));
                continue;
            }

            double distance = playerLoc.distance(resolved);
            String title = renderTitle(target, resolved, distance);
            PushedTarget next = new PushedTarget(
                resolved.getWorld().getName(), resolved.getX(), resolved.getY(), resolved.getZ(),
                target.styleId(), title
            );
            PushedTarget prev = pushed.get(navId);
            if (prev == null) {
                if (pushWaypoint(player, navId, title, target.styleId(), resolved)) {
                    pushed.put(navId, next);
                }
                maybeFirePositionChanged(player, target, state, resolved);
            } else if (!prev.equals(next)) {
                if (prev.sameGeometry(next)) {
                    // 仅标题变化（如 %distance% 刷新）：同 id 重发即可覆盖
                    pushWaypoint(player, navId, title, target.styleId(), resolved);
                } else {
                    dropWaypoint(player, navId);
                    pushWaypoint(player, navId, title, target.styleId(), resolved);
                }
                maybeFirePositionChanged(player, target, state, resolved);
                pushed.put(navId, next);
            }

            showRoute(player, navId, resolved);

            updateArrival(player, target, state, resolved, distance);
        }
    }

    /** 位置变化上报：与上次上报位置相比世界不同或位移超过阈值时触发。 */
    private void maybeFirePositionChanged(Player player, NavigationTarget target, TargetState state, Location resolved) {
        Location prev = state.lastReported;
        String prevWorld = prev == null || prev.getWorld() == null ? "" : prev.getWorld().getName();
        String nextWorld = resolved.getWorld() == null ? "" : resolved.getWorld().getName();
        boolean changed = prev == null
            || !prevWorld.equalsIgnoreCase(nextWorld)
            || prev.distanceSquared(resolved) > 0.05 * 0.05;
        if (!changed) {
            return;
        }
        state.lastReported = resolved.clone();
        firePositionChanged(player, target, resolved);
    }

    // ==================== 内部实现 ====================

    private Location resolve(Player player, String navId, TargetState state) {
        NavigationTarget target = state.target;
        if (target.resolver() == null) {
            return staticLocation(target);
        }
        UUID uuid = player.getUniqueId();
        Map<String, CachedLocation> cache = resolverCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        CachedLocation cached = cache.get(navId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at < config.resolverCacheMillis()) {
            return cached.location;
        }
        Location resolved;
        try {
            resolved = target.resolver().apply(player);
        } catch (Throwable e) {
            logger.warning("导航目标解析失败 navId=" + navId + ": " + e.getMessage());
            resolved = null;
        }
        cache.put(navId, new CachedLocation(now, resolved));
        return resolved;
    }

    /** 推送路标（waypoints 关闭时视为成功但不发包，保持 pushed 状态机一致）。 */
    private boolean pushWaypoint(Player player, String navId, String title, String styleId, Location resolved) {
        return !config.waypoints()
            || waypointBridge.addWaypoint(player, navId, title, styleId,
                resolved.getX(), resolved.getY(), resolved.getZ());
    }

    /** 下路标（waypoints 关闭时不发包）。 */
    private void dropWaypoint(Player player, String navId) {
        if (config.waypoints()) {
            waypointBridge.removeWaypoint(player, navId, false);
        }
    }

    private Location staticLocation(NavigationTarget target) {
        World world = findWorld(target.world());
        if (world == null) {
            return null;
        }
        return new Location(world, target.x(), target.y(), target.z());
    }

    /** 世界查找：精确匹配优先，其次大小写不敏感（map 模块 worldId 为 normalize 小写）。 */
    private static World findWorld(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(name)) {
                return w;
            }
        }
        return null;
    }

    private boolean sameWorld(Location location, String playerWorld) {
        return location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(playerWorld);
    }

    private void suppressPushed(Player player, String navId, Map<String, PushedTarget> pushed) {
        if (pushed.remove(navId) != null) {
            dropWaypoint(player, navId);
        }
        hideRoute(player, navId);
    }

    private void removePushed(Player player, String navId) {
        Map<String, PushedTarget> pushed = pushedTargets.get(player.getUniqueId());
        if (pushed != null) {
            pushed.remove(navId);
            if (pushed.isEmpty()) {
                pushedTargets.remove(player.getUniqueId());
            }
        }
        dropWaypoint(player, navId);
        hideRoute(player, navId);
    }

    /** 向全部路径渲染器下发目标（模型标记/贴图提示并存）。 */
    private void showRoute(Player player, String navId, Location resolved) {
        String worldName = resolved.getWorld() == null ? "" : resolved.getWorld().getName();
        for (NavigationRouteRenderer renderer : routeRenderers) {
            renderer.showMarker(player, navId, worldName,
                resolved.getX(), resolved.getY(), resolved.getZ());
        }
    }

    /** 移除全部路径渲染器的指定分组。 */
    private void hideRoute(Player player, String navId) {
        for (NavigationRouteRenderer renderer : routeRenderers) {
            renderer.hideMarker(player, navId);
        }
    }

    /** 移除全部路径渲染器的所有分组。 */
    private void hideAllRoutes(Player player) {
        for (NavigationRouteRenderer renderer : routeRenderers) {
            renderer.hideMarker(player);
        }
    }

    private void maybeWorldHint(Player player, String navId, NavigationTarget target, Location resolved) {
        TargetCallback handler = worldHintHandler;
        if (handler == null || config.worldHintIntervalMillis() <= 0) {
            return;
        }
        Map<String, Long> hinted = worldHintTimestamps.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = hinted.get(navId);
        if (last != null && now - last < config.worldHintIntervalMillis()) {
            return;
        }
        hinted.put(navId, now);
        try {
            handler.onEvent(player, target, resolved);
        } catch (Throwable e) {
            logger.warning("跨世界提示回调失败 navId=" + navId + ": " + e.getMessage());
        }
    }

    private void updateArrival(Player player, NavigationTarget target, TargetState state, Location resolved, double distance) {
        if (target.finishRange() <= 0) {
            return;
        }
        double rearm = target.rearmBuffer() >= 0 ? target.rearmBuffer() : config.rearmBuffer();
        if (state.armed && distance <= target.finishRange()) {
            state.armed = false;
            runFinishCommands(player, target, resolved);
            TargetCallback handler = arriveHandler;
            if (handler != null) {
                try {
                    handler.onEvent(player, target, resolved);
                } catch (Throwable e) {
                    logger.warning("到达回调失败 navId=" + target.navId() + ": " + e.getMessage());
                }
            }
        } else if (!state.armed && distance > target.finishRange() + rearm) {
            state.armed = true;
        }
    }

    private void runFinishCommands(Player player, NavigationTarget target, Location resolved) {
        List<String> commands = target.finishCommands();
        if (commands.isEmpty()) {
            return;
        }
        String worldName = resolved.getWorld() == null ? "" : resolved.getWorld().getName();
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = raw.trim()
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName())
                .replace("%world%", worldName)
                .replace("%x%", String.valueOf((int) resolved.getX()))
                .replace("%y%", String.valueOf((int) resolved.getY()))
                .replace("%z%", String.valueOf((int) resolved.getZ()));
            // 占位符展开后校验：玩家名/世界名理论上无分隔符，但占位符值可被配置注入，
            // 最终命令统一过校验——命中分隔符拒绝执行
            try {
                command = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(command);
            } catch (IllegalArgumentException e) {
                logger.warning("到达命令含分隔符已拒绝 navId=" + target.navId() + " [" + command + "]");
                continue;
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable e) {
                logger.warning("到达命令执行失败 [" + command + "]: " + e.getMessage());
            }
        }
    }

    private void firePositionChanged(Player player, NavigationTarget target, Location resolved) {
        TargetCallback handler = positionChangedHandler;
        if (handler == null) {
            return;
        }
        try {
            handler.onEvent(player, target, resolved);
        } catch (Throwable e) {
            logger.warning("位置变更回调失败 navId=" + target.navId() + ": " + e.getMessage());
        }
    }

    private String renderTitle(NavigationTarget target, Location resolved, double distance) {
        String template = target.titleTemplate();
        if (template == null || template.isBlank()) {
            return target.title();
        }
        String worldName = resolved.getWorld() == null ? target.world() : resolved.getWorld().getName();
        String rendered = template
            .replace("%label%", target.title())
            .replace("%world%", worldName)
            .replace("%x%", String.valueOf((int) resolved.getX()))
            .replace("%y%", String.valueOf((int) resolved.getY()))
            .replace("%z%", String.valueOf((int) resolved.getZ()))
            .replace("%distance%", String.valueOf(Math.round(distance)));
        for (Map.Entry<String, String> entry : target.placeholders().entrySet()) {
            rendered = rendered.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return rendered;
    }

    private void cleanupPlayer(UUID uuid, Player playerOrNull) {
        Map<String, PushedTarget> pushed = pushedTargets.remove(uuid);
        desiredTargets.remove(uuid);
        worldHintTimestamps.remove(uuid);
        resolverCache.remove(uuid);
        if (playerOrNull != null && playerOrNull.isOnline()) {
            if (pushed != null) {
                for (String navId : pushed.keySet()) {
                    dropWaypoint(playerOrNull, navId);
                }
            }
            hideAllRoutes(playerOrNull);
        }
    }

    private record PushedTarget(String world, double x, double y, double z, String styleId, String title) {
        /** 仅标题变化时为 true（世界/坐标/样式一致）。 */
        boolean sameGeometry(PushedTarget other) {
            return world.equalsIgnoreCase(other.world)
                && Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && styleId.equalsIgnoreCase(other.styleId);
        }
    }

    private static final class TargetState {
        final NavigationTarget target;
        volatile boolean armed = true;
        volatile Location lastResolved;
        /** 上次已上报给 positionChangedHandler 的位置（变化检测用）。 */
        volatile Location lastReported;

        TargetState(NavigationTarget target) {
            this.target = target;
        }
    }

    private record CachedLocation(long at, Location location) {
    }
}
