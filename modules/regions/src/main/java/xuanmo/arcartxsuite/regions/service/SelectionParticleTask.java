package xuanmo.arcartxsuite.regions.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** 区域边框单次最大粒子数 */
    private static final int MAX_REGION_EDGE_PARTICLES = 400;
    /** 区域边框显示的视距（方块半径，X/Z 平面） */
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
                    Color.AQUA, MAX_SELECTION_EDGE_PARTICLES);
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
    private void spawnBoxEdges(Player player, World world,
                                int x1, int y1, int z1, int x2, int y2, int z2,
                                Color color, int maxParticles) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
        // 12 条棱总长
        long totalLen = 4L * dx + 4L * dy + 4L * dz;
        if (totalLen <= 0) return;
        // 采样步长：保证总粒子数 <= maxParticles
        double step = Math.max(1.0, (double) totalLen / maxParticles);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);

        // 4 条 X 方向棱（y/z 取 min/max 组合）
        for (int y : new int[]{minY, maxY}) {
            for (int z : new int[]{minZ, maxZ}) {
                for (double x = minX; x <= maxX + 0.001; x += step) {
                    player.spawnParticle(Particle.REDSTONE, new Location(world, x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, 0, dust);
                }
            }
        }
        // 4 条 Y 方向棱（x/z 取 min/max 组合）
        for (int x : new int[]{minX, maxX}) {
            for (int z : new int[]{minZ, maxZ}) {
                for (double y = minY; y <= maxY + 0.001; y += step) {
                    player.spawnParticle(Particle.REDSTONE, new Location(world, x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, 0, dust);
                }
            }
        }
        // 4 条 Z 方向棱（x/y 取 min/max 组合）
        for (int x : new int[]{minX, maxX}) {
            for (int y : new int[]{minY, maxY}) {
                for (double z = minZ; z <= maxZ + 0.001; z += step) {
                    player.spawnParticle(Particle.REDSTONE, new Location(world, x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    // ─── 区域边框常驻粒子 ───

    private void tickRegionBorders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 仅对显式开启的玩家显示（避免默认全员刷粒子）
            if (!regionBorderToggled.contains(player.getUniqueId())) continue;
            // admin 自动看到所有区域；普通玩家只看自己拥有/参与的
            boolean isAdmin = player.hasPermission("axs.regions.admin");
            World world = player.getWorld();
            int pcx = player.getLocation().getBlockX();
            int pcz = player.getLocation().getBlockZ();

            for (Region region : regionManager.getAllRegions()) {
                if (!region.world().equals(world.getName())) continue;
                // 视距裁剪：区域 AABB 与玩家视距圆是否相交
                if (!withinView(region, pcx, pcz, REGION_VIEW_RADIUS)) continue;
                // 权限：admin 看所有，普通玩家看自己拥有/参与的
                if (!isAdmin && !region.isMember(player.getUniqueId())) continue;
                // 颜色：自己拥有=黄，admin 看所有=紫
                Color color = isAdmin ? Color.FUCHSIA : Color.YELLOW;
                spawnBoxEdges(player, world,
                    region.minX(), region.minY(), region.minZ(),
                    region.maxX(), region.maxY(), region.maxZ(),
                    color, MAX_REGION_EDGE_PARTICLES);
            }
        }
    }

    /**
     * 区域 AABB（仅 X/Z 平面）是否落在玩家视距圆内。
     */
    private boolean withinView(Region region, int pcx, int pcz, int radius) {
        // 找区域 AABB 上离玩家最近的点
        int nearestX = Math.max(region.minX(), Math.min(pcx, region.maxX()));
        int nearestZ = Math.max(region.minZ(), Math.min(pcz, region.maxZ()));
        int dx = nearestX - pcx, dz = nearestZ - pcz;
        return dx * dx + dz * dz <= radius * radius;
    }
}
