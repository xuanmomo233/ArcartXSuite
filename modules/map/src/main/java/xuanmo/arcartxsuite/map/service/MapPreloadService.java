package xuanmo.arcartxsuite.map.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.PolygonRegion;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;
import xuanmo.arcartxsuite.map.model.MapOperationResult;

/**
 * 地图预加载域：区块覆盖几何计算（圆/多边形 → 64² MapChunk 网格）、预加载版本 bump
 * 持久化（data/map/preload-bumps.properties）、世界中心点解析。
 * <p>自 {@link MapService} 提取，持有主类引用回调 configuration/plugin/logger/世界解析。
 */
final class MapPreloadService {

    private final MapService service;

    MapPreloadService(MapService service) {
        this.service = service;
    }


    // ─── 预加载版本手动刷新（/axs map refresh <worldId>）────────────
    //
    // 语义：bump 计数持久化在 data/map/preload-bumps.properties，有效版本 =
    // hash(配置版本, bump)。配置改动 → 基线哈希变 → 版本自然变；手动刷新 →
    // bump+1 → 版本变。重启后 bump 不丢，版本不回落，避免二次全量重拉。
    private final Map<String, Integer> preloadVersionBumps = new ConcurrentHashMap<>();


    /** 世界预加载覆盖的 MapChunk 坐标集合（64² 网格，逐项 {cx,cz}）。
     *  圆形部分实时取当前 spawn（/mv setspawn 即生效），
     *  多边形部分按 WorldDefinition 缓存（配置重载后新实例自然换键）。 */
    private final Map<WorldDefinition, Set<Long>> preloadRegionCoordCache = new ConcurrentHashMap<>();
    private record Coverage(double x, double z, Set<Long> chunks) { }
    private final Map<WorldDefinition, Coverage> coverageCache = new ConcurrentHashMap<>();


    /** 世界预加载区域 → 多边形并集（preload.radius 圆心=配置 center 或出生点链，16 边形近似）。 */
    List<List<int[]>> preloadRegionPoints(WorldDefinition world) {
        List<List<int[]>> out = new ArrayList<>(service.toRegionPoints(world.preloadRegions()));
        if (world.preloadRadius() > 0) {
            Location center = resolveWorldCenter(world);
            if (center != null) {
                out.add(circlePoints(center.getX(), center.getZ(), world.preloadRadius()));
            }
        }
        return out;
    }


    File preloadBumpFile() {
        return new File(service.plugin.getDataFolder(), "data/map/preload-bumps.properties");
    }


    void loadPreloadVersionBumps() {
        File file = preloadBumpFile();
        if (!file.isFile()) {
            return;
        }
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                try {
                    preloadVersionBumps.put(MapService.normalizeId(key), Integer.parseInt(props.getProperty(key).trim()));
                } catch (NumberFormatException ignored) {
                    // 单条非法跳过
                }
            }
        } catch (Exception e) {
            service.logger.warning("读取预加载版本刷新记录失败: " + e.getMessage());
        }
    }


    void savePreloadVersionBumps() {
        File file = preloadBumpFile();
        file.getParentFile().mkdirs();
        java.util.Properties props = new java.util.Properties();
        for (Map.Entry<String, Integer> e : preloadVersionBumps.entrySet()) {
            props.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            props.store(out, "map preload version bumps (manual refresh counters)");
        } catch (Exception e) {
            service.logger.warning("写入预加载版本刷新记录失败: " + e.getMessage());
        }
    }


    /**
     * 世界有效预加载版本号：手动 bump 优先于配置哈希基线。
     * payload 下发与 terrainPush sourceVersion 必须同源，否则客户端永判过期。
     */
    int effectivePreloadVersion(WorldDefinition world) {
        Integer bump = preloadVersionBumps.get(world.id());
        if (bump == null) {
            return world.preloadVersion();
        }
        return Math.max(1, java.util.Objects.hash(world.preloadVersion(), bump) & 0x7FFFFFFF);
    }


    /**
     * 管理员手动刷新某世界的预加载版本：bump+1 持久化。
     * 下次快照下发新版本号，客户端发现 sourceVersion 过期自动重拉。
     */
    MapOperationResult refreshPreloadVersion(String worldId) {
        WorldDefinition world = service.configuration.worlds().get(MapService.normalizeId(worldId));
        if (world == null) {
            return MapOperationResult.failure(service.message("refresh.world-unknown", worldId));
        }
        if (!world.hasPreload()) {
            return MapOperationResult.failure(service.message("refresh.world-no-preload", world.id()));
        }
        int bump = preloadVersionBumps.merge(world.id(), 1, Integer::sum);
        savePreloadVersionBumps();
        return MapOperationResult.success(
            service.message("refresh.done", world.id(), effectivePreloadVersion(world)));
    }


    /**
     * 世界中心点：配置 preload.center 优先，否则 ess→Bukkit 出生点链。
     * 双用途：预加载圆心 + 客户端浏览非当前世界的默认视野中心。
     */
    Location resolveWorldCenter(WorldDefinition world) {
        World bukkitWorld = MapService.resolveWorld(world.id());
        if (world.hasPreloadCenter()) {
            return new Location(bukkitWorld, world.preloadCenterX(), 0, world.preloadCenterZ());
        }
        return service.resolveWorldSpawn(bukkitWorld);
    }


    List<int[]> preloadChunkCoords(WorldDefinition world) {
        Set<Long> coords = preloadPackedChunkCoords(world);
        List<int[]> out = new ArrayList<>(coords.size());
        for (long packed : coords) out.add(new int[]{(int) (packed >> 32), (int) packed});
        return out;
    }

    Set<Long> preloadPackedChunkCoords(WorldDefinition world) {
        Location center = world.preloadRadius() > 0 ? resolveWorldCenter(world) : null;
        double x = center == null ? Double.NaN : center.getX();
        double z = center == null ? Double.NaN : center.getZ();
        Coverage cached = coverageCache.get(world);
        if (cached != null && Double.compare(cached.x(), x) == 0 && Double.compare(cached.z(), z) == 0) {
            return cached.chunks();
        }
        Set<Long> coords = new HashSet<>();
        if (center != null) {
            collectCircleChunks(coords, x, z, world.preloadRadius());
        }
        coords.addAll(preloadRegionChunkCoords(world));
        Set<Long> result = Set.copyOf(coords);
        coverageCache.put(world, new Coverage(x, z, result));
        return result;
    }


    /** 多边形预加载区域覆盖的块坐标（打包 long），按 WorldDefinition 缓存。 */
    Set<Long> preloadRegionChunkCoords(WorldDefinition world) {
        Set<Long> cached = preloadRegionCoordCache.get(world);
        if (cached != null) {
            return cached;
        }
        Set<Long> coords = new HashSet<>();
        for (PolygonRegion region : world.preloadRegions()) {
            collectPolygonChunks(coords, region.points());
        }
        preloadRegionCoordCache.putIfAbsent(world, coords);
        return coords;
    }


    /**
     * 圆形区域覆盖到的 MapChunk 集合。判定用"块中心距 ≤ r + 块对角线半长"
     * （多收边缘块无害——推送量略大但不漏）。
     */
    static void collectCircleChunks(Set<Long> out, double cx, double cz, double radius) {
        int min = (int) Math.floor((cx - radius) / 64.0);
        int max = (int) Math.floor((cx + radius) / 64.0);
        int zMin = (int) Math.floor((cz - radius) / 64.0);
        int zMax = (int) Math.floor((cz + radius) / 64.0);
        double limit = radius + 45.3; // 64/2 * √2 ≈ 45.25
        double limitSq = limit * limit;
        for (int ccx = min; ccx <= max; ccx++) {
            for (int ccz = zMin; ccz <= zMax; ccz++) {
                double dx = ccx * 64.0 + 32 - cx;
                double dz = ccz * 64.0 + 32 - cz;
                if (dx * dx + dz * dz <= limitSq) {
                    out.add(((long) ccx << 32) | (ccz & 0xffffffffL));
                }
            }
        }
    }


    /**
     * 多边形区域覆盖到的 MapChunk 集合：包围盒内逐块判定——
     * 块中心在多边形内、或任一顶点落在块内、或多边形任一边中点落在块内。
     */
    static void collectPolygonChunks(Set<Long> out, List<int[]> points) {
        if (points == null || points.size() < 3) {
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        for (int ccx = (int) Math.floor(minX / 64.0); ccx <= (int) Math.floor(maxX / 64.0); ccx++) {
            for (int ccz = (int) Math.floor(minZ / 64.0); ccz <= (int) Math.floor(maxZ / 64.0); ccz++) {
                int bx = ccx * 64, bz = ccz * 64;
                if (pointInPolygon(points, bx + 32.0, bz + 32.0) || chunkTouchesPolygon(points, bx, bz)) {
                    out.add(((long) ccx << 32) | (ccz & 0xffffffffL));
                }
            }
        }
    }


    /** Segment/rectangle clipping catches long, thin polygons crossing a chunk. */
    static boolean chunkTouchesPolygon(List<int[]> points, int bx, int bz) {
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] a = points.get(i), b = points.get(j);
            double enter = 0, exit = 1;
            boolean intersects = true;
            for (int axis = 0; axis < 2; axis++) {
                double origin = a[axis];
                double delta = (double) b[axis] - origin;
                double min = axis == 0 ? bx : bz;
                double max = min + 64;
                if (delta == 0) {
                    if (origin < min || origin > max) { intersects = false; break; }
                } else {
                    double t0 = (min - origin) / delta, t1 = (max - origin) / delta;
                    enter = Math.max(enter, Math.min(t0, t1));
                    exit = Math.min(exit, Math.max(t0, t1));
                    if (enter > exit) { intersects = false; break; }
                }
            }
            if (intersects) return true;
        }
        return false;
    }


    /** 射线法判点是否在多边形内。 */
    static boolean pointInPolygon(List<int[]> pts, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
            double xi = pts.get(i)[0], zi = pts.get(i)[1];
            double xj = pts.get(j)[0], zj = pts.get(j)[1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }


    /** 圆 → 16 边形顶点列表（与 MapSnapshotBuilder 的 circleToPolygon 同款，service 侧自用）。 */
    static List<int[]> circlePoints(double cx, double cz, double radius) {
        List<int[]> points = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            points.add(new int[]{
                (int) Math.round(cx + radius * Math.cos(angle)),
                (int) Math.round(cz + radius * Math.sin(angle))
            });
        }
        return points;
    }
}
