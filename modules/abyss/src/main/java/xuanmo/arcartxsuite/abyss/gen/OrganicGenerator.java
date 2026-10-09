package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.FloorConfig;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.Floor;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomTemplate;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomType;

/**
 * 洞窟群迷宫生成器：一张 CA 大洞穴网络 + 洞壁上凸出的洞室房间。
 * <p>
 * 仅做纯数学规划（不触碰世界）——{@link #plan} 产出的 {@link GeneratedLayout}
 * 由 {@link PlanPainter} 统一写块。生成顺序：
 * <ol>
 *   <li>洞穴本体：CA 一次成型（边墙封死→多轮平滑→取最大连通域）——
 *       蜿蜒隧道与天然洞厅天然互连，它就是迷宫本体，不再需要"走廊"概念</li>
 *   <li>牌组：保底 rooms + optional-rooms 概率增删 + boss-room 兜底必在</li>
 *   <li>洞室：每房在洞穴边缘选一个外凸点，CA 团块向洞外膨胀；
 *       团块与洞域的重叠弧收窄成 ~{@code corridor-width} 宽门洞——
 *       门是被刻意"切"出来的开口，永不出现被墙堵死的门位</li>
 *   <li>锚点：按 feature 产出 center/spawn/altar/chest/merchant/lever_n/exit 等</li>
 *   <li>障碍区：Boss 门洞外的洞域带产出 soul/magma 命名区</li>
 * </ol>
 */
public final class OrganicGenerator {

    private final java.util.logging.Logger logger;

    public OrganicGenerator(java.util.logging.Logger logger) {
        this.logger = logger;
    }

    /** 洞穴内空净高（洞盖在 floorY+CAVE_H）。 */
    private static final int CAVE_H = 7;

    // ═══ 入口 ═══

    /** 纯数学生成全部 GEN 层（不写世界）。 */
    public @Nullable GeneratedLayout plan(AbyssInstance instance, World world) {
        FloorConfig fc = instance.getTemplate().floors();
        if (fc == null || fc.floors().isEmpty()) return null;
        boolean anyGen = fc.floors().stream()
            .anyMatch(f -> f.source() == FloorConfig.Source.GEN);
        if (!anyGen) return null;

        GenSpec spec = fc.defaults().gen();
        long seed = spec.seed() != 0 ? spec.seed()
            : instance.getInstanceId().getMostSignificantBits()
                ^ ThreadLocalRandom.current().nextLong();
        Random rng = new Random(seed);
        logger.info("[abyss] 生成器种子 " + seed + "（实例 " + instance.getInstanceId() + "）");

        GeneratedLayout layout = new GeneratedLayout();
        for (int fi = 0; fi < fc.floors().size(); fi++) {
            Floor floor = fc.floors().get(fi);
            if (floor.source() != FloorConfig.Source.GEN) continue;
            GenSpec fs = floor.gen() != null ? floor.gen() : spec;
            FloorPlan fp = planFloor(world, floor, fi, fs, rng, fc);
            layout.putFloor(fp, world);
        }
        return layout;
    }

    // ═══ 层生成 ═══

    private FloorPlan planFloor(World world, Floor floor, int fi,
                                GenSpec spec, Random rng, FloorConfig fc) {
        FloorPlan fp = new FloorPlan(fi);
        int spacing = fc.defaults().roomSpacing();
        // 洞穴栅格：层带 fi*200+8 起，宽≤176 高 148（洞室外凸余量含在带内）
        int W = Math.min(176, spacing - 24), H = 148;
        int bx = fi * spacing + 8, bz = 4;

        // ── 1. 大洞穴网络 ──
        Set<Long> cave = cavern(bx, bz, W, H, spec, rng);
        if (cave.isEmpty()) {
            logger.warning("[abyss] gen 层 " + floor.id() + " 洞穴生成失败——层为空");
            return fp;
        }
        RoomPlan caveRoom = new RoomPlan("cave", RoomType.SAFE,
            spec.floorY(), spec.floorY() + CAVE_H);
        caveRoom.floor.addAll(cave);
        caveRoom.wall.addAll(ring(cave, 2));
        boundsOf(caveRoom);
        fp.cave = caveRoom;
        fp.growBounds(caveRoom);

        // ── 2. 牌组：保底 + 增删 + boss 兜底 ──
        List<String> deck = new ArrayList<>(floor.roomRefs());
        for (Map.Entry<String, Double> e : floor.optionalRooms().entrySet()) {
            if (!deck.contains(e.getKey()) && rng.nextDouble() < e.getValue()) {
                deck.add(e.getKey());
            }
        }
        if (floor.bossRoom() != null && !deck.contains(floor.bossRoom())) {
            deck.add(floor.bossRoom());
        }
        List<String> active = new ArrayList<>();
        for (String ref : deck) {
            if (fc.rooms().get(ref) == null) {
                logger.warning("[abyss] gen 牌组引用缺失房间 " + ref + "（层 " + floor.id() + "）");
                continue;
            }
            active.add(ref);
        }
        if (active.isEmpty()) return fp;

        String entryRef = featureOf(fc, active, "entry", active.get(0));
        String bossRef = floor.bossRoom();
        Map<String, Integer> radii = new HashMap<>();
        for (String ref : active) {
            int[] rad = radiusOf(fc.rooms().get(ref), spec);
            radii.put(ref, rad[0] + (rad[1] > rad[0]
                ? rng.nextInt(rad[1] - rad[0] + 1) : 0));
        }
        // 放置顺序：entry → 其余洗牌 → boss（boss 要拿入口门洞做 BFS 最远离点）
        List<String> order = new ArrayList<>(active);
        order.remove(entryRef);
        if (bossRef != null) order.remove(bossRef);
        Collections.shuffle(order, rng);

        // ── 3. 洞室外凸 ──
        List<Long> edge = edgeCells(cave);
        if (edge.isEmpty()) {
            logger.warning("[abyss] gen 层 " + floor.id() + " 洞穴无边缘——洞室无处安放");
            return fp;
        }
        Map<String, RoomPlan> placed = new LinkedHashMap<>();
        int[] caveCentroid = centroid(cave);
        // 入口：洞穴西南象限边缘
        placeChamber(world, fp, floor, fc, spec, rng, cave, edge, placed, radii,
            entryRef, pickBy(e -> px(e) < caveCentroid[0] && pz(e) > caveCentroid[1], edge),
            null, bx, bx + W, bz, bz + H);
        RoomPlan entry = placed.get(entryRef);
        // 其余房：随机边缘点，洞口彼此尽量错开
        for (String ref : order) {
            placeChamber(world, fp, floor, fc, spec, rng, cave, edge, placed, radii,
                ref, null, entry, bx, bx + W, bz, bz + H);
        }
        // Boss：沿洞穴 BFS 距入口门最远的边缘带
        if (bossRef != null && !placed.containsKey(bossRef)) {
            Map<Long, Integer> dist = bfsFrom(entry, cave);
            List<Long> farEdge = new ArrayList<>();
            int maxD = 0;
            for (long pk : edge) {
                int d = dist.getOrDefault(pk, 0);
                maxD = Math.max(maxD, d);
            }
            for (long pk : edge) {
                if (dist.getOrDefault(pk, 0) >= maxD * 0.6) farEdge.add(pk);
            }
            placeChamber(world, fp, floor, fc, spec, rng, cave, farEdge, placed, radii,
                bossRef, null, entry, bx, bx + W, bz, bz + H);
        }

        // ── 4. 房内锚点/刷怪点/装饰规划 ──
        for (RoomPlan rp : fp.rooms.values()) {
            furnishPlan(rp, fc.rooms().get(rp.ref), spec, rng, world);
        }

        // ── 5. Boss 封门 + 门洞外洞域障碍带 ──
        if (bossRef != null && placed.containsKey(bossRef)) {
            RoomPlan boss = placed.get(bossRef);
            for (RoomPlan.DoorPlan d : boss.doors) d.sealed = true;
            // 洞域内贴近 Boss 门的一段 = 陷阱带（前半魂沙后半岩浆）
            if (!boss.doors.isEmpty()) {
                int[] dc = doorCentroid(boss.doors.get(0));
                List<RoomPlan.Pos> near = new ArrayList<>();
                for (long pk : cave) {
                    int x = (int) (pk >> 32), z = (int) (long) pk;
                    double d = Math.hypot(x - dc[0], z - dc[1]);
                    if (d >= 3 && d <= 15) {
                        near.add(new RoomPlan.Pos(x, spec.floorY(), z));
                    }
                }
                near.sort((a, b) -> Double.compare(
                    Math.hypot(a.x() - dc[0], a.z() - dc[1]),
                    Math.hypot(b.x() - dc[0], b.z() - dc[1])));
                int half = near.size() / 2;
                fp.zones.put(floor.id() + ".boss_corridor_soul",
                    near.subList(0, half));
                fp.zones.put(floor.id() + ".boss_corridor_magma",
                    near.subList(half, near.size()));
            }
        }

        // 层入口点 = entry 房 spawn 锚点
        if (entry != null) fp.entrySpawn = entry.anchor("spawn");
        return fp;
    }

    // ═══ 洞穴本体 ═══

    /**
     * CA 大洞穴：边墙封底的噪声场 → {@code smooth+1} 轮平滑 → 最大连通域。
     * 返回世界坐标列集（floorY 层地面）。
     */
    private Set<Long> cavern(int bx, int bz, int W, int H, GenSpec spec, Random rng) {
        boolean[][] m = new boolean[W][H];
        double fill = Math.min(0.52, spec.caFill() + 0.04);
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < H; z++) {
                // 边缘两圈强制实心（洞不贴生成带边——洞壁外还有洞室要凸出）
                m[x][z] = x >= 2 && z >= 2 && x < W - 2 && z < H - 2
                    && rng.nextDouble() < fill;
            }
        }
        int rounds = spec.caSmooth() + 1;
        for (int r = 0; r < rounds; r++) {
            boolean[][] nx = new boolean[W][H];
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < H; z++) {
                    int s = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int ax = x + dx, az = z + dz;
                            if (ax < 0 || az < 0 || ax >= W || az >= H || !m[ax][az]) s++;
                        }
                    }
                    nx[x][z] = s >= 5;
                }
            }
            m = nx;
        }
        // 最大连通域
        Set<Long> best = new HashSet<>();
        boolean[][] seen = new boolean[W][H];
        for (int sx = 0; sx < W; sx++) {
            for (int sz = 0; sz < H; sz++) {
                if (!m[sx][sz] || seen[sx][sz]) continue;
                Set<Long> comp = new HashSet<>();
                Deque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{sx, sz});
                seen[sx][sz] = true;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    comp.add(RoomPlan.pack(bx + c[0], bz + c[1]));
                    for (int[] dd : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int ax = c[0] + dd[0], az = c[1] + dd[1];
                        if (ax < 0 || az < 0 || ax >= W || az >= H
                            || seen[ax][az] || !m[ax][az]) continue;
                        seen[ax][az] = true;
                        q.add(new int[]{ax, az});
                    }
                }
                if (comp.size() > best.size()) best = comp;
            }
        }
        return best;
    }

    /** 洞域边界列集：有非洞邻居的洞格（洞室就从这些位置向外凸）。 */
    private static List<Long> edgeCells(Set<Long> cave) {
        List<Long> out = new ArrayList<>();
        for (long pk : cave) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            for (int[] dd : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                if (!cave.contains(RoomPlan.pack(x + dd[0], z + dd[1]))) {
                    out.add(pk);
                    break;
                }
            }
        }
        return out;
    }

    // ═══ 洞室外凸 ═══

    /**
     * 在候选边缘点集里给 {@code ref} 房找一个外凸位：
     * 房心 = 边缘点沿外推方向 R+3，CA 团块与洞域的重叠弧即天然开口，
     * 收窄成门洞格集后写进 RoomPlan。
     *
     * @param cand  候选边缘列（null=全部边缘随机）
     * @param entry 入口房（非 entry/boss 的房用它算洞口间距，防扎堆）
     */
    private void placeChamber(World world, FloorPlan fp, Floor floor, FloorConfig fc,
                              GenSpec spec, Random rng, Set<Long> cave,
                              List<Long> cand, Map<String, RoomPlan> placed,
                              Map<String, Integer> radii, String ref,
                              @Nullable List<Long> forced, @Nullable RoomPlan entry,
                              int bx0, int bx1, int bz0, int bz1) {
        if (ref == null) return;
        RoomTemplate tpl = fc.rooms().get(ref);
        int R = radii.getOrDefault(ref, spec.radiusMin());
        int doorW = Math.max(3, spec.corridorWidth());
        List<Long> pool = forced != null ? forced : cand;
        int tries = 240;    // 边缘点采样很轻，统一多给尝试次数

        for (int t = 0; t < tries; t++) {
            long e = pool.get(rng.nextInt(pool.size()));
            int ex = px(e), ez = pz(e);
            // 8 个方向各算一次，取"外凸度 + 开口宽度"综合最好的方向
            int[] bestDir = null;
            Set<Long> bestBlob = null;
            Set<Long> bestIface = null;
            int bestScore = -1;
            for (int[] d : DIRS8) {
                int cx = ex + d[0] * (R + 3), cz = ez + d[1] * (R + 3);
                if (cx - R - 3 < bx0 || cx + R + 3 > bx1
                    || cz - R - 3 < bz0 || cz + R + 3 > bz1) continue;
                Set<Long> blob = blobMask(cx, cz, R, rng);
                // 与他房地板/壳体不重叠
                if (collides(blob, placed)) continue;
                Set<Long> iface = iface(blob, cave);
                // 开口理想宽度 5~12 格：过窄=死胡同感，过宽=不是房间是豁口
                int score = Math.min(iface.size(), 12) - Math.abs(iface.size() - 8);
                // 外凸度：房内独有格占比越高越好
                long own = blob.stream().filter(p -> !cave.contains(p)).count();
                score += (int) (own * 4 / Math.max(1, blob.size()));
                // 洞口间距：与已放房的门洞拉开（防门口挤作一团）
                if (entry != null) {
                    int[] ec = doorCentroid(entry.doors.isEmpty() ? null : entry.doors.get(0));
                    if (ec != null) {
                        double dd = Math.hypot(cx - ec[0], cz - ec[1]);
                        score += (int) Math.min(dd / 4, 8);
                    }
                }
                if (score > bestScore) {
                    bestScore = score; bestDir = d; bestBlob = blob; bestIface = iface;
                }
            }
            if (bestBlob == null || bestIface.size() < 2) continue;

            // ── 物化该候选 ──
            Set<Long> door = pickDoor(bestIface, doorW, rng);
            Set<Long> frame = new HashSet<>(bestIface);
            frame.removeAll(door);

            RoomPlan rp = new RoomPlan(ref, tpl.type(),
                spec.floorY(),
                spec.floorY() + (isTall(tpl, spec) ? spec.tallHeight() : spec.baseHeight()) + 1);
            // 房本体 = 团块独有格 + 门洞格；被切走的 frame 洞侧格不进房
            for (long pk : bestBlob) {
                if (!cave.contains(pk) || door.contains(pk)) rp.floor.add(pk);
            }
            rp.floor.removeAll(frame);
            // 壳环 = 房外扩 2 圈，挖掉洞域（洞自己管自己的壳）与门洞，补回 frame
            Set<Long> wall = ring(rp.floor, 2);
            wall.removeAll(cave);
            wall.removeAll(rp.floor);
            wall.addAll(frame);
            rp.wall.addAll(wall);
            boundsOf(rp);
            // 门洞格集（锁门填 y+1..+4）
            RoomPlan.DoorPlan dp = new RoomPlan.DoorPlan("cave");
            for (long pk : door) {
                int x = px(pk), z = pz(pk);
                for (int y = spec.floorY() + 1; y <= spec.floorY() + 4; y++) {
                    dp.cells.add(new RoomPlan.Pos(x, y, z));
                }
            }
            if (!dp.cells.isEmpty()) rp.doors.add(dp);
            placed.put(ref, rp);
            fp.rooms.put(ref, rp);
            fp.growBounds(rp);
            return;
        }
        logger.warning("[abyss] gen 层 " + floor.id() + " 房 " + ref
            + " 洞室无处安放——已跳过");
    }

    /** 房与已放房的地板/壳体是否重叠。 */
    private static boolean collides(Set<Long> blob, Map<String, RoomPlan> placed) {
        for (RoomPlan o : placed.values()) {
            for (long pk : blob) {
                if (o.floor.contains(pk) || o.wall.contains(pk)) return true;
            }
        }
        return false;
    }

    /**
     * 房↔洞接触界面（8 邻域）：
     * 团块独有格里贴洞的 + 洞里贴团块的 + 团块∩洞的重叠格。
     * 全部封口、只留门洞段——斜向缝隙也一并堵死。
     */
    private static Set<Long> iface(Set<Long> blob, Set<Long> cave) {
        Set<Long> out = new HashSet<>();
        for (long pk : blob) {
            boolean touch = false;
            int x = px(pk), z = pz(pk);
            for (int[] d : DIRS8) {
                if (cave.contains(RoomPlan.pack(x + d[0], z + d[1]))) {
                    touch = true; break;
                }
            }
            if (touch || cave.contains(pk)) out.add(pk);
        }
        for (long pk : cave) {
            int x = px(pk), z = pz(pk);
            for (int[] d : DIRS8) {
                if (blob.contains(RoomPlan.pack(x + d[0], z + d[1]))) {
                    out.add(pk); break;
                }
            }
        }
        return out;
    }

    /** 界面里挑 ~w 个围绕质心的连续格作门洞（接口不足 w 时全给）。 */
    private static Set<Long> pickDoor(Set<Long> iface, int w, Random rng) {
        if (iface.size() <= w) return new HashSet<>(iface);
        // 质心
        double cx = 0, cz = 0;
        for (long pk : iface) { cx += px(pk); cz += pz(pk); }
        cx /= iface.size(); cz /= iface.size();
        List<Long> sorted = new ArrayList<>(iface);
        double fcx = cx, fcz = cz;
        sorted.sort((a, b) -> Double.compare(
            Math.hypot(px(a) - fcx, pz(a) - fcz),
            Math.hypot(px(b) - fcx, pz(b) - fcz)));
        // 从最近格种子向界面内 8 邻域扩展到 w 格
        Set<Long> door = new HashSet<>();
        Deque<Long> q = new ArrayDeque<>();
        q.add(sorted.get(0));
        while (!q.isEmpty() && door.size() < w) {
            long pk = q.poll();
            if (!iface.contains(pk) || door.contains(pk)) continue;
            door.add(pk);
            int x = px(pk), z = pz(pk);
            for (int[] d : DIRS8) q.add(RoomPlan.pack(x + d[0], z + d[1]));
        }
        return door;
    }

    // ═══ 几何小件 ═══

    private static final int[][] DIRS8 =
        {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    private static int px(long pk) { return (int) (pk >> 32); }
    private static int pz(long pk) { return (int) pk; }

    private interface Pick { boolean ok(long pk); }
    private static List<Long> pickBy(Pick f, List<Long> in) {
        List<Long> out = new ArrayList<>();
        for (long pk : in) if (f.ok(pk)) out.add(pk);
        return out.isEmpty() ? in : out;
    }

    /** CA 小团块：噪声场 → 平滑 → 含中心最大连通域 → 半径截断。 */
    private Set<Long> blobMask(int cx, int cz, int rad, Random rng) {
        int R = rad + 4, n = R * 2 + 1;
        boolean[][] m = new boolean[n][n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double p = 0.55;
                double d = Math.hypot(x - R, z - R);
                if (d > R - 3) p -= (d - (R - 3)) * 0.25;
                if (Math.abs(x - R) <= 2 && Math.abs(z - R) <= 2) p = 0.95;
                m[x][z] = rng.nextDouble() < p;
            }
        }
        for (int r = 0; r < 4; r++) {
            boolean[][] nx = new boolean[n][n];
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    int s = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            int ax = x + dx, az = z + dz;
                            if (ax < 0 || az < 0 || ax >= n || az >= n || m[ax][az]) s++;
                        }
                    }
                    nx[x][z] = s >= 5 || (m[x][z] && s >= 4);
                }
            }
            for (int x = R - 2; x <= R + 2; x++)
                for (int z = R - 2; z <= R + 2; z++) nx[x][z] = true;
            m = nx;
        }
        // 含中心最大连通域
        boolean[][] seen = new boolean[n][n];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{R, R});
        seen[R][R] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int ax = c[0] + dd[0], az = c[1] + dd[1];
                if (ax < 0 || az < 0 || ax >= n || az >= n || seen[ax][az] || !m[ax][az]) continue;
                seen[ax][az] = true;
                q.add(new int[]{ax, az});
            }
        }
        Set<Long> out = new HashSet<>();
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (seen[x][z] && Math.hypot(x - R, z - R) <= rad + 2) {
                    out.add(RoomPlan.pack(cx + x - R, cz + z - R));
                }
            }
        }
        return out;
    }

    /** 列集外扩 t 圈（Chebyshev）。 */
    private static Set<Long> ring(Set<Long> base, int t) {
        Set<Long> out = new HashSet<>();
        for (long pk : base) {
            int x = px(pk), z = pz(pk);
            for (int dx = -t; dx <= t; dx++) {
                for (int dz = -t; dz <= t; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != t) continue;
                    long k = RoomPlan.pack(x + dx, z + dz);
                    if (!base.contains(k)) out.add(k);
                }
            }
        }
        return out;
    }

    private static void boundsOf(RoomPlan rp) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long pk : rp.wall.isEmpty() ? rp.floor : rp.wall) {
            minX = Math.min(minX, px(pk)); maxX = Math.max(maxX, px(pk));
            minZ = Math.min(minZ, pz(pk)); maxZ = Math.max(maxZ, pz(pk));
        }
        rp.minX = minX; rp.maxX = maxX; rp.minZ = minZ; rp.maxZ = maxZ;
    }

    private static int[] centroid(Set<Long> cells) {
        long sx = 0, sz = 0;
        for (long pk : cells) { sx += px(pk); sz += pz(pk); }
        return new int[]{(int) (sx / cells.size()), (int) (sz / cells.size())};
    }

    private static @Nullable int[] doorCentroid(@Nullable RoomPlan.DoorPlan d) {
        if (d == null || d.cells.isEmpty()) return null;
        long sx = 0, sz = 0;
        for (RoomPlan.Pos p : d.cells) { sx += p.x(); sz += p.z(); }
        return new int[]{(int) (sx / d.cells.size()), (int) (sz / d.cells.size())};
    }

    /** 从入口房的全部门洞格出发在洞域上 BFS（洞穴内最短路径距离）。 */
    private static Map<Long, Integer> bfsFrom(@Nullable RoomPlan entry, Set<Long> cave) {
        Map<Long, Integer> dist = new HashMap<>();
        if (entry == null || entry.doors.isEmpty()) return dist;
        Deque<Long> q = new ArrayDeque<>();
        for (RoomPlan.Pos p : entry.doors.get(0).cells) {
            long pk = RoomPlan.pack(p.x(), p.z());
            if (cave.contains(pk)) { dist.put(pk, 0); q.add(pk); }
        }
        while (!q.isEmpty()) {
            long pk = q.poll();
            int d = dist.get(pk);
            int x = px(pk), z = pz(pk);
            for (int[] dd : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                long nk = RoomPlan.pack(x + dd[0], z + dd[1]);
                if (cave.contains(nk) && !dist.containsKey(nk)) {
                    dist.put(nk, d + 1);
                    q.add(nk);
                }
            }
        }
        return dist;
    }

    // ═══ 房内布置 ═══

    private void furnishPlan(RoomPlan rp, @Nullable RoomTemplate tpl,
                             GenSpec spec, Random rng, World w) {
        String feature = tpl != null && tpl.gen() != null && tpl.gen().feature() != null
            ? tpl.gen().feature() : featureDefault(rp.type);
        int fy = rp.floorY;
        int cx = (rp.minX + rp.maxX) / 2, cz = (rp.minZ + rp.maxZ) / 2;
        // center 锚点取最近洞格（质心未必在洞内）
        int[] cc = nearestFloor(rp, cx, cz);
        rp.anchors.put("center", new Location(w, cc[0] + 0.5, fy + 1, cc[1] + 0.5));
        // spawn：靠首个门洞的内侧格，面向中心
        rp.anchors.put("spawn", spawnOf(rp, w));
        // 刷怪点：10 个洞内随机格（最小间距 4）
        rp.mobSpots.addAll(scatter(rp, 10, 4, rng, w));

        switch (feature) {
            case "altar" -> {
                int[] a = nearestFloor(rp, cc[0], cc[1]);
                rp.anchors.put("altar", new Location(w, a[0], fy + 1, a[1]));
            }
            case "chest" -> rp.anchors.put("chest",
                new Location(w, cc[0], fy + 1, cc[1]));
            case "shop" -> {
                int[] m = nearestFloor(rp, cc[0], cc[1] + 3);
                int[] ct = nearestFloor(rp, cc[0] - 4, cc[1]);
                rp.anchors.put("merchant", new Location(w, m[0], fy + 1, m[1], 0f, 0f));
                rp.anchors.put("counter", new Location(w, ct[0], fy + 1, ct[1]));
            }
            case "puzzle" -> {
                // 拉杆挂内墙：找最长"内空列 + 北侧是墙"的横向墙段，沿段挂 4 杆
                int[] seg = longestNorthWallRun(rp);
                int y = fy + 2;
                for (int i = 0; i < 4; i++) {
                    int lx = seg[0] + 2 + i * 3;
                    rp.anchors.put("lever_" + (i + 1),
                        new Location(w, lx, y, seg[1]));
                }
            }
            case "boss" -> {
                // 出口传送区：房内离门最远一侧的平台（4×4）
                int[] far = farthestFloorFromDoors(rp);
                Location c = new Location(w, far[0], fy + 1, far[1]);
                rp.exitMin = new Location(w, far[0] - 1, fy + 1, far[1] - 1);
                rp.exitMax = new Location(w, far[0] + 2, fy + 4, far[1] + 2);
                rp.anchors.put("exit", c);
                rp.anchors.put("boss", rp.anchors.get("center"));
            }
            case "entry" -> {
                // 向导 NPC：spawn 旁 3 格（吸附洞内），面向 spawn
                Location sp = rp.anchors.get("spawn");
                int[] gp = nearestFloor(rp, sp.getBlockX() + 2, sp.getBlockZ() + 2);
                Location g = new Location(w, gp[0] + 0.5, fy + 1, gp[1] + 0.5);
                g.setYaw(yawToward(g, sp));
                rp.anchors.put("guide", g);
            }
            default -> { }
        }
    }

    private Location spawnOf(RoomPlan rp, World w) {
        if (rp.doors.isEmpty()) return rp.center(w);
        // 取第一门内侧格（贴门洞内空格）
        RoomPlan.Pos d = rp.doors.get(0).cells.get(0);
        int[] inner = nearestFloor(rp, d.x(), d.z());
        Location l = new Location(w, inner[0] + 0.5, rp.floorY + 1, inner[1] + 0.5);
        l.setYaw(yawToward(l, rp.center(w)));
        return l;
    }

    private static float yawToward(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** 房内散布采样：n 个洞格，两两曼哈顿距 ≥ gap（尽力而为 40 轮）。 */
    private List<Location> scatter(RoomPlan rp, int n, int gap, Random rng, World w) {
        List<Long> cells = new ArrayList<>(rp.floor);
        List<Location> out = new ArrayList<>();
        for (int t = 0; t < n * 40 && out.size() < n && !cells.isEmpty(); t++) {
            long pk = cells.get(rng.nextInt(cells.size()));
            int x = px(pk), z = pz(pk);
            boolean ok = true;
            for (Location l : out) {
                if (Math.abs(l.getBlockX() - x) + Math.abs(l.getBlockZ() - z) < gap) {
                    ok = false; break;
                }
            }
            if (ok) out.add(new Location(w, x + 0.5, rp.floorY + 1, z + 0.5));
        }
        return out;
    }

    /** 最长"内空格(x,z) 且北侧(x,z-1)是墙"的横向连续段——返回 {起点x, z, 长度}。 */
    private int[] longestNorthWallRun(RoomPlan rp) {
        int bestX = 0, bestZ = 0, bestLen = 0;
        Map<Integer, List<Integer>> byZ = new HashMap<>();
        for (long pk : rp.floor) {
            int x = px(pk), z = pz(pk);
            if (rp.wall.contains(RoomPlan.pack(x, z - 1))) {
                byZ.computeIfAbsent(z, k -> new ArrayList<>()).add(x);
            }
        }
        for (Map.Entry<Integer, List<Integer>> e : byZ.entrySet()) {
            List<Integer> xs = e.getValue();
            xs.sort(null);
            int run = 1;
            for (int i = 1; i < xs.size(); i++) {
                if (xs.get(i) == xs.get(i - 1) + 1) {
                    run++;
                    if (run > bestLen) {
                        bestLen = run;
                        bestZ = e.getKey();
                        bestX = xs.get(i) - run + 1;
                    }
                } else run = 1;
            }
        }
        // 兜底：无合适墙段时用 center 北侧外推（极端小房）
        if (bestLen < 12) {
            int cx = (rp.minX + rp.maxX) / 2, cz = (rp.minZ + rp.maxZ) / 2;
            int[] inner = nearestFloor(rp, cx, cz - 4);
            return new int[]{inner[0] - 5, inner[1], 11};
        }
        return new int[]{bestX, bestZ, bestLen};
    }

    private int[] nearestFloor(RoomPlan rp, int x, int z) {
        long best = -1; int bd = Integer.MAX_VALUE;
        for (long pk : rp.floor) {
            int cx = px(pk), cz = pz(pk);
            int d = Math.abs(cx - x) + Math.abs(cz - z);
            if (d < bd) { bd = d; best = pk; }
        }
        if (best == -1) return new int[]{x, z};
        return new int[]{px(best), pz(best)};
    }

    /** 离全部门洞最远的洞内格（出口平台位）。 */
    private int[] farthestFloorFromDoors(RoomPlan rp) {
        Set<Long> dc = new HashSet<>();
        for (RoomPlan.DoorPlan d : rp.doors)
            for (RoomPlan.Pos p : d.cells) dc.add(RoomPlan.pack(p.x(), p.z()));
        long best = -1; int bd = -1;
        for (long pk : rp.floor) {
            int cx = px(pk), cz = pz(pk);
            int d = Integer.MAX_VALUE;
            for (long dp : dc) {
                d = Math.min(d, Math.abs(cx - px(dp)) + Math.abs(cz - pz(dp)));
            }
            if (d > bd) { bd = d; best = pk; }
        }
        if (best == -1) return new int[]{(rp.minX + rp.maxX) / 2, (rp.minZ + rp.maxZ) / 2};
        return new int[]{px(best), pz(best)};
    }

    // ═══ 配置辅助 ═══

    private static String featureOf(FloorConfig fc, List<String> refs, String want, String def) {
        for (String r : refs) {
            RoomTemplate t = fc.rooms().get(r);
            if (t != null && t.gen() != null && want.equals(t.gen().feature())) return r;
        }
        return def;
    }

    private static int[] radiusOf(RoomTemplate tpl, GenSpec spec) {
        if (tpl != null && tpl.gen() != null) {
            int[] r = tpl.gen().radius();
            if (r != null && r.length >= 2) return r;
        }
        return new int[]{spec.radiusMin(), spec.radiusMax()};
    }

    private static boolean isTall(RoomTemplate tpl, GenSpec spec) {
        return tpl != null && tpl.gen() != null && tpl.gen().tall();
    }

    private static String featureDefault(RoomType t) {
        return switch (t) {
            case BOSS -> "boss";
            case SHOP -> "shop";
            default -> "plain";
        };
    }
}
