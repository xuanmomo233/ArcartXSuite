package xuanmo.arcartxsuite.fishing.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.util.PointInPolygon;

/**
 * 水域定义，描述一片可钓鱼的多边形区域及其鱼类、宝藏、难度公式等属性。
 * <p>
 * 关联模块：{@code fishing}。每个水域由一个 {@code water-<id>.yml} 文件定义，
 * 边界使用 2D 多边形（x, z 坐标列表），通过 {@link #contains(Location)} 判断玩家位置是否落在水域内。
 * 默认水域（{@code water-default.yml}）无多边形边界，匹配所有未命中指定水域的位置。
 *
 * @param id                 水域唯一标识
 * @param displayName        显示名称（支持颜色代码）
 * @param worldName          所在世界名称（null = 不限世界）
 * @param enabled            是否启用
 * @param vertices           多边形顶点列表（空列表 = 默认水域，匹配所有位置）
 * @param fishes             鱼类 ID 列表（直接引用 fishes/ 目录下的鱼种）
 * @param treasures          宝藏 ID 列表（直接引用 treasures/ 目录下的宝藏）
 * @param difficultyFormula  难度修正公式（ARIA 表达式，变量 {@code global.level} = 玩家钓鱼等级）
 * @param minPlayerLevel     最低钓鱼等级（低于此等级无法在此水域钓鱼）
 * @param maxPlayerLevel     最高钓鱼等级（高于此等级无法在此水域钓鱼，0 = 不限）
 * @param baitMultipliers    饵料在该水域的额外倍率
 * @param requirePermission  进入此水域钓鱼所需权限（null = 无限制）
 */
public record WaterArea(
    @NotNull String id,
    @NotNull String displayName,
    @Nullable String worldName,
    boolean enabled,
    @NotNull List<PointInPolygon.Vertex> vertices,
    @NotNull List<String> fishes,
    @NotNull List<String> treasures,
    @NotNull String difficultyFormula,
    int minPlayerLevel,
    int maxPlayerLevel,
    @NotNull Map<String, Double> baitMultipliers,
    @Nullable String requirePermission,
    @NotNull List<School> schools,
    @NotNull List<ObstacleZone> obstacleZones,
    @NotNull List<CurrentZone> currentZones
) {
    public WaterArea {
        obstacleZones = List.copyOf(obstacleZones);
        currentZones = List.copyOf(currentZones);
    }

    public record ObstacleZone(String id, String displayName, List<PointInPolygon.Vertex> vertices,
                               double trackMin, double trackMax, int warningTicks, double tensionPerTick) {
        public ObstacleZone {
            vertices = List.copyOf(vertices);
            if (vertices.stream().distinct().count() < 3 || !(trackMin >= 0 && trackMin < trackMax && trackMax <= 1)
                || warningTicks < 0 || !Double.isFinite(tensionPerTick) || tensionPerTick <= 0) {
                throw new IllegalArgumentException("障碍区坐标、危险带或张力参数无效 " + id);
            }
        }

        public boolean containsTrackPosition(double position) {
            return position >= trackMin && position <= trackMax;
        }
    }

    /** 在已解析的水域中选择第一个命中浮漂 XZ 方块坐标的障碍区。 */
    public @Nullable ObstacleZone obstacleAt(Location hook) {
        int x = hook.getBlockX();
        int z = hook.getBlockZ();
        for (ObstacleZone zone : obstacleZones) {
            if (PointInPolygon.contains(zone.vertices(), x, z)) return zone;
        }
        return null;
    }

    public record CurrentZone(String id, String displayName, List<PointInPolygon.Vertex> vertices,
                              double trackMin, double trackMax, double driftPerTick, int carryTicks) {
        public CurrentZone {
            vertices = List.copyOf(vertices);
            if (vertices.stream().distinct().count() < 3 || !(trackMin >= 0 && trackMin < trackMax && trackMax <= 1)
                || !(Math.abs(driftPerTick) > 0 && Math.abs(driftPerTick) <= 0.05) || carryTicks <= 0) {
                throw new IllegalArgumentException("水流区坐标、水层、漂移或持续时间无效 " + id);
            }
        }
    }

    public @Nullable CurrentZone currentAt(Location hook) {
        int x = hook.getBlockX();
        int z = hook.getBlockZ();
        for (CurrentZone zone : currentZones) {
            if (PointInPolygon.contains(zone.vertices(), x, z)) return zone;
        }
        return null;
    }

    public record School(String id, double x, double y, double z, double radius, long activeTicks,
                         long intervalTicks, boolean rainOnly, Map<String, Double> weights,
                         double shoreX, double shoreY, double shoreZ) {
        public School {
            weights = Map.copyOf(weights);
            if (!Double.isFinite(radius) || radius <= 0 || radius > 64
                || activeTicks <= 0 || intervalTicks < activeTicks
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(shoreX) || !Double.isFinite(shoreY) || !Double.isFinite(shoreZ)
                || weights.values().stream().anyMatch(weight -> !Double.isFinite(weight) || weight <= 0 || weight > 100)) {
                throw new IllegalArgumentException("鱼讯范围、时长、坐标或权重无效 " + id);
            }
        }
    }

    // ─── bounding box 缓存（record 不能有实例字段，用外部 Map） ──
    private record Bbox(int minX, int maxX, int minZ, int maxZ) {}
    private static final ConcurrentHashMap<WaterArea, Bbox> BBOX_CACHE = new ConcurrentHashMap<>();

    /**
     * 是否为默认水域（无多边形边界，匹配所有位置）。
     */
    public boolean isDefault() {
        return vertices == null || vertices.isEmpty();
    }

    /**
     * 判断玩家位置是否落在水域内。
     * <p>
     * 默认水域（无顶点）始终返回 true。
     * 指定水域先做世界名过滤和 bounding box 快速排除，再做精确多边形检测。
     *
     * @param loc 玩家位置
     * @return true 如果在水域内
     */
    public boolean contains(@NotNull Location loc) {
        if (isDefault()) return true;
        if (worldName != null && !worldName.isEmpty()) {
            World world = loc.getWorld();
            if (world == null || !world.getName().equalsIgnoreCase(worldName)) {
                return false;
            }
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        // bounding box 快速排除
        Bbox bbox = BBOX_CACHE.computeIfAbsent(this, wa -> wa.computeBbox());
        if (x < bbox.minX() || x > bbox.maxX() || z < bbox.minZ() || z > bbox.maxZ()) {
            return false;
        }
        return PointInPolygon.contains(vertices, x, z);
    }

    private Bbox computeBbox() {
        if (vertices == null || vertices.size() < 3) {
            return new Bbox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PointInPolygon.Vertex v : vertices) {
            if (v.x() < minX) minX = v.x();
            if (v.x() > maxX) maxX = v.x();
            if (v.z() < minZ) minZ = v.z();
            if (v.z() > maxZ) maxZ = v.z();
        }
        return new Bbox(minX, maxX, minZ, maxZ);
    }
}
