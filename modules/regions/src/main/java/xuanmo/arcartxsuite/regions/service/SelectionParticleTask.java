package xuanmo.arcartxsuite.regions.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.Selection;

/**
 * 选区与区域边框粒子可视化任务。
 * <p>
 * 周期性向玩家推送粒子：
 * <ul>
 *   <li>选区点粒子柱（pos1 红 / pos2 绿）</li>
 *   <li>选区边框粒子（选区完成后沿 12 条棱勾勒，蓝）</li>
 *   <li>已创建区域边框常驻粒子（仅 owner / admin 可见，按视距裁剪 + 节流）</li>
 * </ul>
 * 粒子总数受 {@link #MAX_SELECTION_EDGE_PARTICLES} 与 {@link #MAX_REGION_EDGE_PARTICLES} 限制，
 * 避免大体积选区/区域卡服。
 */
public final class SelectionParticleTask {

    /** 选区边框单次最大粒子数（沿 12 条棱均匀采样） */
    private static final int MAX_SELECTION_EDGE_PARTICLES = 600;
    /** 每位玩家和全服每轮区域边框预览的粒子预算。 */
    private static final int MAX_REGION_EDGE_PARTICLES = 400;
    private static final int MAX_REGION_PREVIEW_PARTICLES = 1600;
    /** 预览附近的区域棱边。 */
    private static final int REGION_VIEW_RADIUS = 48;
    /** 选区粒子任务周期（tick） */
    private static final long SELECTION_PERIOD = 8L;
    /** 区域边框粒子任务周期（tick） */
    private static final long REGION_PERIOD = 16L;

    private final JavaPlugin plugin;
    private final RegionManager regionManager;
    private final RegionsConfiguration config;

    private BukkitTask selectionTask;
    private BukkitTask regionTask;
    private int nextViewer;

    /** 显式开启区域边框可视化的玩家（/rg show 切换） */
    private final Set<UUID> regionBorderToggled = ConcurrentHashMap.newKeySet();

    public SelectionParticleTask(JavaPlugin plugin, RegionManager regionManager, RegionsConfiguration config) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.config = config;
    }

    public void start() {
        selectionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSelection, SELECTION_PERIOD, SELECTION_PERIOD);
        regionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegionBorders, REGION_PERIOD, REGION_PERIOD);
    }

    public void shutdown() {
        if (selectionTask != null) { selectionTask.cancel(); selectionTask = null; }
        if (regionTask != null) { regionTask.cancel(); regionTask = null; }
        regionBorderToggled.clear();
    }

    /**
     * 切换某玩家的区域边框可视化开关。
     *
     * @return 切换后是否启用
     */
    public boolean toggleRegionBorder(Player player) {
        UUID id = player.getUniqueId();
        if (!regionBorderToggled.remove(id)) {
            regionBorderToggled.add(id);
            return true;
        }
        return false;
    }

    public boolean isRegionBorderEnabled(Player player) {
        return regionBorderToggled.contains(player.getUniqueId());
    }

    // ─── 选区粒子 ───

    private void tickSelection() {
        // 仅对有选区点设置的玩家推送
        for (Player player : Bukkit.getOnlinePlayers()) {
            Selection sel;
            try {
                sel = regionManager.getSelection(player.getUniqueId());
            } catch (Exception ignored) { continue; }
            if (sel == null || (!sel.isPos1Set() && !sel.isPos2Set())) continue;
            World world = player.getWorld();
            if (sel.world() == null || !sel.world().equals(world.getName())) continue;

            // pos1 粒子柱（红）
            if (sel.isPos1Set()) {
                spawnPillar(player, world, sel.x1() + 0.5, sel.y1(), sel.z1() + 0.5, Color.RED);
            }
            // pos2 粒子柱（绿）
            if (sel.isPos2Set()) {
                spawnPillar(player, world, sel.x2() + 0.5, sel.y2(), sel.z2() + 0.5, Color.LIME);
            }
            // 选区完成 → 边框粒子（蓝）
            if (sel.isComplete()) {
                spawnBoxEdges(player, world,
                    sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2(),
                    Color.AQUA, MAX_SELECTION_EDGE_PARTICLES, null);
            }
        }
    }

    /**
     * 在指定坐标 spawn 一根从方块底部到顶部的粒子柱（共 5 个点）。
     */
    private void spawnPillar(Player player, World world, double x, int blockY, double z, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        for (int i = 0; i <= 4; i++) {
            double y = blockY + i * 0.5;
            // 仅向该玩家发送，避免对其他人造成粒子噪音
            player.spawnParticle(Particle.REDSTONE, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * 沿立方体 12 条棱均匀采样粒子，限制总数避免卡服。
     */
    private int spawnBoxEdges(Player player, World world,
                              int x1, int y1, int z1, int x2, int y2, int z2,
                              Color color, int maxParticles, Location viewer) {
        int[] minimum = {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)};
        int[] maximum = {Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)};
        int[] center = viewer == null ? null : new int[]{viewer.getBlockX(), viewer.getBlockY(), viewer.getBlockZ()};
        var edges = new ArrayList<Edge>(12);
        long total = 0;
        for (int axis = 0; axis < 3; axis++) {
            int first = (axis + 1) % 3;
            int second = (axis + 2) % 3;
            int start = center == null ? minimum[axis] : Math.max(minimum[axis], center[axis] - REGION_VIEW_RADIUS);
            int end = center == null ? maximum[axis] : Math.min(maximum[axis], center[axis] + REGION_VIEW_RADIUS);
            if (start > end) continue;
            for (int a : new int[]{minimum[first], maximum[first]}) {
                for (int b : new int[]{minimum[second], maximum[second]}) {
                    if (center != null && (Math.abs((long) a - center[first]) > REGION_VIEW_RADIUS
                        || Math.abs((long) b - center[second]) > REGION_VIEW_RADIUS)) continue;
                    int[] point = new int[3];
                    point[axis] = start;
                    point[first] = a;
                    point[second] = b;
                    long length = (long) end - start + 1;
                    edges.add(new Edge(point[0], point[1], point[2], axis, length));
                    total += length;
                }
            }
        }

        int count = (int) Math.min(maxParticles, total);
        if (count == 0) return 0;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        int edgeIndex = 0;
        long edgeOffset = 0;
        for (int i = 0; i < count; i++) {
            long offset = i * total / count;
            Edge edge = edges.get(edgeIndex);
            while (offset >= edgeOffset + edge.length()) {
                edgeOffset += edge.length();
                edge = edges.get(++edgeIndex);
            }
            long step = offset - edgeOffset;
            player.spawnParticle(Particle.REDSTONE, new Location(world,
                edge.x() + 0.5 + (edge.axis() == 0 ? step : 0),
                edge.y() + 0.5 + (edge.axis() == 1 ? step : 0),
                edge.z() + 0.5 + (edge.axis() == 2 ? step : 0)), 1, 0, 0, 0, 0, dust);
        }
        return count;
    }

    private void tickRegionBorders() {
        var viewers = List.copyOf(regionBorderToggled);
        if (viewers.isEmpty()) return;
        int start = Math.floorMod(nextViewer, viewers.size());
        int remaining = MAX_REGION_PREVIEW_PARTICLES;
        int visited = 0;
        for (; visited < viewers.size() && remaining > 0; visited++) {
            Player player = Bukkit.getPlayer(viewers.get((start + visited) % viewers.size()));
            if (player == null) continue;
            remaining -= showNearbyBorders(player, Math.min(remaining, MAX_REGION_EDGE_PARTICLES));
        }
        nextViewer = (start + visited) % viewers.size();
    }

    private int showNearbyBorders(Player player, int budget) {
        Location viewer = player.getLocation();
        boolean admin = player.hasPermission("axs.regions.admin");
        var regions = regionManager.nearbyRegions(viewer, REGION_VIEW_RADIUS).stream()
            .filter(region -> admin || regionManager.hasAccess(region, player))
            .toList();
        Color color = admin ? Color.FUCHSIA : Color.YELLOW;
        int remaining = budget;
        for (int i = 0; i < regions.size() && remaining > 0; i++) {
            Region region = regions.get(i);
            int share = Math.max(1, remaining / (regions.size() - i));
            remaining -= spawnBoxEdges(player, viewer.getWorld(), region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ(), color, share, viewer);
        }
        return budget - remaining;
    }

    private record Edge(int x, int y, int z, int axis, long length) {}

}
