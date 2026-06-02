package xuanmo.arcartxsuite.questgps.service;

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
import xuanmo.arcartxsuite.bridge.AdyeshachNpcBridge;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 导航路径标记服务 — 使用 Adyeshach 私有临时实体 + ArcartX 模型实现沿路径的导航可视化。
 * <p>
 * 特点:
 * - A* 寻路，智能绕开障碍物/液体/危险方块
 * - 沿路径等间距放置多个模型标记实体
 * - 定时更新：随玩家移动重新计算路径并传送/增减标记
 * - 实体复用池：尽量传送现有实体，减少创建/销毁开销
 */
public final class QuestGpsMarkerService {

    private static final String MARKER_ID_PREFIX = "questgps_path_";

    private final JavaPlugin plugin;
    private final AdyeshachNpcBridge npcBridge;
    private final QuestGpsModuleConfiguration.MarkerDefaults config;
    private final Logger logger;
    private final boolean debug;
    private final ConcurrentMap<UUID, PlayerPathState> playerPaths = new ConcurrentHashMap<>();

    private QuestGpsPathfinder pathfinder;
    private BukkitTask updateTask;
    private boolean available;

    public QuestGpsMarkerService(
        JavaPlugin plugin,
        AdyeshachNpcBridge npcBridge,
        QuestGpsModuleConfiguration.MarkerDefaults config,
        Logger logger,
        boolean debug
    ) {
        this.plugin = plugin;
        this.npcBridge = npcBridge;
        this.config = config;
        this.logger = logger;
        this.debug = debug;
    }

    public void start() {
        if (!config.enabled()) {
            logger.info("QuestGPS 导航标记: 已在配置中禁用 (marker.enabled=false)。");
            available = false;
            return;
        }
        if (npcBridge == null) {
            logger.warning("QuestGPS 导航标记: npcBridge 为 null，标记功能将跳过。");
            available = false;
            return;
        }
        if (!npcBridge.isAvailable()) {
            logger.warning("QuestGPS 导航标记: Adyeshach 不可用 (npcBridge.isAvailable()=false)，标记功能将跳过。");
            available = false;
            return;
        }
        available = true;
        pathfinder = new QuestGpsPathfinder(config.pathMaxIterations());

        // 启动定时更新任务
        int ticks = Math.max(1, config.pathUpdateTicks());
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, ticks, ticks);

        logger.info("QuestGPS 导航标记已启动: model=" + config.modelId() + " scale=" + config.scale()
            + " interval=" + config.pathInterval() + " maxMarkers=" + config.pathMaxMarkers()
            + " updateTicks=" + ticks);
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideMarker(player);
        }
        playerPaths.clear();
        available = false;
    }

    /**
     * 设置导航目标，启动路径标记跟踪。
     */
    public void showMarker(Player player, QuestGpsNavigationService.NavigationPoint point) {
        if (!available || player == null || point == null) {
            return;
        }
        // 移除旧路径
        hideMarker(player);

        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            world = player.getWorld();
        }

        Location goal = new Location(world, point.x(), point.y(), point.z());
        PlayerPathState state = new PlayerPathState(goal, new ArrayList<>(), new ArrayList<>());
        playerPaths.put(player.getUniqueId(), state);

        if (debug) {
            logger.info("QuestGPS 路径标记: 开始追踪 player=" + player.getName()
                + " goal=(" + point.x() + "," + point.y() + "," + point.z() + ")");
        }

        // 立即计算一次路径
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!player.isOnline()) return;
            computeAndApplyPath(player, state);
        });
    }

    /**
     * 移除玩家的所有路径标记。
     */
    public void hideMarker(Player player) {
        if (player == null) return;
        PlayerPathState state = playerPaths.remove(player.getUniqueId());
        if (state == null) return;

        // 标记取消，阻止异步回调继续创建实体
        state.cancelled = true;

        for (String markerId : state.markerIds) {
            npcBridge.removePrivateMarker(player, markerId);
        }
        state.markerIds.clear();
        state.markerEntities.clear();
    }

    public boolean isAvailable() {
        return available;
    }

    // ==================== 定时更新 ====================

    private void tickUpdate() {
        for (var entry : playerPaths.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                // 正确清理实体（不能只移除 Map 条目）
                PlayerPathState offlineState = playerPaths.remove(entry.getKey());
                if (offlineState != null) {
                    offlineState.cancelled = true;
                    // 实体清理需要在线玩家，离线时标记取消即可，实体随玩家退出自动消失
                }
                continue;
            }
            PlayerPathState state = entry.getValue();
            if (state.cancelled) continue;
            // 异步计算路径，主线程应用实体操作
            final Player p = player;
            final PlayerPathState s = state;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (p.isOnline() && !s.cancelled) {
                    computeAndApplyPath(p, s);
                }
            });
        }
    }

    private void computeAndApplyPath(Player player, PlayerPathState state) {
        try {
            computeAndApplyPathInternal(player, state);
        } catch (Exception ex) {
            if (debug) {
                logger.warning("QuestGPS 路径计算异常: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }
        }
    }

    private void computeAndApplyPathInternal(Player player, PlayerPathState state) {
        Location playerLoc = player.getLocation();
        Location goal = state.goal;

        // 世界安全检查
        if (playerLoc.getWorld() == null || goal.getWorld() == null
            || !playerLoc.getWorld().equals(goal.getWorld())) {
            Bukkit.getScheduler().runTask(plugin, () -> removeExcessMarkers(player, state, 0));
            return;
        }

        double distance = playerLoc.distance(goal);
        if (distance > config.pathMaxDistance()) {
            // 超出最大距离，清除路径标记
            Bukkit.getScheduler().runTask(plugin, () -> removeExcessMarkers(player, state, 0));
            return;
        }

        // A* 寻路（异步线程）
        List<Location> rawPath = pathfinder.findPath(playerLoc, goal);
        if (rawPath.isEmpty()) {
            // 寻路完全失败，显示短距离方向指引（不穿墙）
            rawPath = shortDirectionalPath(playerLoc, goal);
        }

        // 简化 + 采样
        List<Location> simplified = QuestGpsPathfinder.simplifyPath(rawPath);
        List<Location> sampled = QuestGpsPathfinder.samplePath(simplified, config.pathInterval(), config.pathMaxMarkers());

        // 应用 Y 偏移 + 计算朝向（每个标记面向下一个路径点）
        double yOffset = config.yOffset();
        List<Location> finalPoints = new ArrayList<>(sampled.size());
        for (int i = 0; i < sampled.size(); i++) {
            Location loc = sampled.get(i).clone().add(0, yOffset, 0);
            // 计算朝向：当前点 → 下一个点（最后一个点朝向目标）
            Location next = (i + 1 < sampled.size()) ? sampled.get(i + 1) : goal;
            double dx = next.getX() - sampled.get(i).getX();
            double dz = next.getZ() - sampled.get(i).getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            loc.setYaw(yaw);
            finalPoints.add(loc);
        }

        // 回到主线程操作实体
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || state.cancelled) return;
            applyPathMarkers(player, state, finalPoints);
        });
    }

    /**
     * 主线程：对比现有标记与新路径点，传送/新建/删除标记。
     */
    private void applyPathMarkers(Player player, PlayerPathState state, List<Location> points) {
        int needed = points.size();
        int existing = state.markerIds.size();

        // 1. 传送现有标记到新位置
        int toTeleport = Math.min(needed, existing);
        for (int i = 0; i < toTeleport; i++) {
            npcBridge.teleportMarker(player, state.markerIds.get(i), points.get(i));
        }

        // 2. 需要更多标记：新建
        if (needed > existing) {
            for (int i = existing; i < needed; i++) {
                String markerId = MARKER_ID_PREFIX + player.getUniqueId().toString().substring(0, 8) + "_" + i;
                Object entity = npcBridge.spawnPrivateMarker(player, markerId, points.get(i));
                if (entity == null) continue;
                state.markerIds.add(markerId);
                state.markerEntities.add(entity);

                // 延迟应用模型
                final Object ent = entity;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    npcBridge.applyModelForPlayer(player, ent, config.modelId(), config.scale());
                    if (config.animation() != null && !config.animation().isBlank()) {
                        npcBridge.applyAnimationForPlayer(player, ent, config.animation(), 1.0, 200, -1);
                    }
                }, 2L);
            }
        }

        // 3. 标记过多：删除多余的
        removeExcessMarkers(player, state, needed);
    }

    private void removeExcessMarkers(Player player, PlayerPathState state, int keepCount) {
        while (state.markerIds.size() > keepCount) {
            int last = state.markerIds.size() - 1;
            npcBridge.removePrivateMarker(player, state.markerIds.get(last));
            state.markerIds.remove(last);
            if (last < state.markerEntities.size()) {
                state.markerEntities.remove(last);
            }
        }
    }

    /**
     * 当 A* 完全无法寻路时，生成一条短距离方向指引路径（不穿墙，最多 5 格）。
     * 仅指示方向，不引导玩家走进障碍物。
     */
    private List<Location> shortDirectionalPath(Location start, Location goal) {
        List<Location> path = new ArrayList<>();
        path.add(start.clone());
        double dx = goal.getX() - start.getX();
        double dz = goal.getZ() - start.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) {
            return path;
        }
        double nx = dx / len;
        double nz = dz / len;
        World world = start.getWorld();
        for (int i = 1; i <= 5; i++) {
            double px = start.getX() + nx * i;
            double pz = start.getZ() + nz * i;
            int bx = (int) Math.floor(px);
            int bz = (int) Math.floor(pz);
            int by = start.getBlockY();
            // 简单障碍检测：目标位置的方块是否是固体
            if (world != null) {
                org.bukkit.block.Block block = world.getBlockAt(bx, by, bz);
                org.bukkit.block.Block headBlock = world.getBlockAt(bx, by + 1, bz);
                if (block.getType().isSolid() || headBlock.getType().isSolid()) {
                    break; // 遇到障碍停止
                }
            }
            path.add(new Location(world, px, start.getY(), pz));
        }
        return path;
    }

    // ==================== 玩家状态 ====================

    private static final class PlayerPathState {
        final Location goal;
        final List<String> markerIds;
        final List<Object> markerEntities;
        volatile boolean cancelled;

        PlayerPathState(Location goal, List<String> markerIds, List<Object> markerEntities) {
            this.goal = goal;
            this.markerIds = markerIds;
            this.markerEntities = markerEntities;
        }
    }
}
