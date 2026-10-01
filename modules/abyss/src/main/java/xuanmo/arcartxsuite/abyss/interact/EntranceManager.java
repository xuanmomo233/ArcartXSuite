package xuanmo.arcartxsuite.abyss.interact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/**
 * 世界入口点管理器：主世界/场景里的物理入口（右键方块/区域进本）。
 * <p>
 * 与副本内 interact 系统的关系：interact 只活在实例内（玩家必须在副本中才命中），
 * 本类管的是<b>实例外</b>的入口点击——玩家不在副本时由监听器转发到这里。
 * <p>
 * 配置在每个地牢的 {@code config.yml} 顶层 {@code entrances:} 列表；
 * 模板 reload 时 {@link #rebuild} 重建索引。
 */
public final class EntranceManager {

    /** 命中结果：地牢 + 入口定义。 */
    public record EntranceHit(AbyssTemplate template, AbyssTemplate.EntrancePoint point) { }

    /** 区域索引项（区域少，线性遍历足够）。 */
    private record RegionRef(String world, int x1, int y1, int z1,
                             int x2, int y2, int z2, EntranceHit hit) { }

    /** 单点索引：world -> "x,y,z" -> hit。 */
    private final Map<String, Map<String, EntranceHit>> points = new ConcurrentHashMap<>();
    private final List<RegionRef> regions = new ArrayList<>();
    private final Logger logger;

    public EntranceManager(Logger logger) {
        this.logger = logger;
    }

    /** 模板 reload 后重建全部入口索引。 */
    public synchronized void rebuild(Collection<AbyssTemplate> templates) {
        points.clear();
        regions.clear();
        for (AbyssTemplate t : templates) {
            for (AbyssTemplate.EntrancePoint ep : t.entrances()) {
                if (ep.world() == null || ep.world().isBlank()) continue;
                EntranceHit hit = new EntranceHit(t, ep);
                if (ep.location() != null && !ep.location().isBlank()) {
                    int[] c = parsePoint(ep.location());
                    if (c == null) {
                        logger.warning("[abyss] 地牢 " + t.id() + " 入口坐标无效: " + ep.location());
                    } else {
                        points.computeIfAbsent(ep.world(), k -> new ConcurrentHashMap<>())
                            .put(c[0] + "," + c[1] + "," + c[2], hit);
                    }
                }
                if (ep.region() != null && !ep.region().isBlank()) {
                    int[] r = parseRegion(ep.region());
                    if (r == null) {
                        logger.warning("[abyss] 地牢 " + t.id() + " 入口区域无效: " + ep.region());
                    } else {
                        regions.add(new RegionRef(ep.world(),
                            r[0], r[1], r[2], r[3], r[4], r[5], hit));
                    }
                }
            }
        }
    }

    /** 命中检查：先单点再区域。 */
    public @Nullable EntranceHit match(Block block) {
        if (block == null) return null;
        String world = block.getWorld().getName();
        Map<String, EntranceHit> wm = points.get(world);
        if (wm != null) {
            EntranceHit hit = wm.get(block.getX() + "," + block.getY() + "," + block.getZ());
            if (hit != null) return hit;
        }
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (RegionRef r : regions) {
            if (!r.world().equals(world)) continue;
            if (x >= r.x1() && x <= r.x2() && y >= r.y1() && y <= r.y2()
                && z >= r.z1() && z <= r.z2()) {
                return r.hit();
            }
        }
        return null;
    }

    public int size() {
        int n = regions.size();
        for (Map<String, EntranceHit> m : points.values()) n += m.size();
        return n;
    }

    private static int[] parsePoint(String s) {
        try {
            String[] p = s.trim().split(",");
            if (p.length < 3) return null;
            return new int[]{
                Integer.parseInt(p[0].trim()),
                Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "x1,y1,z1;x2,y2,z2" → [minX,minY,minZ,maxX,maxY,maxZ]。 */
    private static int[] parseRegion(String s) {
        String[] halves = s.trim().split(";");
        if (halves.length != 2) return null;
        int[] a = parsePoint(halves[0]);
        int[] b = parsePoint(halves[1]);
        if (a == null || b == null) return null;
        return new int[]{
            Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }
}
