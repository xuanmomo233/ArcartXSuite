package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * 有机迷宫生成器：洞穴化房间 + 随机拓扑 + 蜿蜒走廊。
 * <p>
 * 仅做纯数学规划（不触碰世界）——{@link #plan} 产出的 {@link GeneratedLayout}
 * 由 {@link PlanPainter} 统一写块。生成顺序：
 * <ol>
 *   <li>牌组：保底 rooms + optional-rooms 概率增删 + boss-room 兜底必在</li>
 *   <li>布点：网格洗牌分配 + 格内抖动（entry 压角、boss 取最远离格）</li>
 *   <li>形状：每房元胞自动机洞穴——随机半径 → 平滑 → 取含中心最大连通域</li>
 *   <li>拓扑：Prim 随机最小生成树保底连通 + 近邻环边（extra-edges）</li>
 *   <li>走廊：门洞对门洞的折线（随机折向/折点），洞穿墙体即门</li>
 *   <li>锚点：按 feature 产出 center/spawn/altar/chest/merchant/lever_n/exit 等</li>
 *   <li>障碍区：Boss 入口走廊分带产出 soul/magma 命名区</li>
 * </ol>
 */
public final class OrganicGenerator {

    private final java.util.logging.Logger logger;

    public OrganicGenerator(java.util.logging.Logger logger) {
        this.logger = logger;
    }

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
            FloorPlan fp = planFloor(instance, world, floor, fi, fs, rng, fc);
            layout.putFloor(fp, world);
        }
        return layout;
    }

    // ═══ 层生成 ═══

    private FloorPlan planFloor(AbyssInstance instance, World world, Floor floor,
                                int fi, GenSpec spec, Random rng, FloorConfig fc) {
        FloorPlan fp = new FloorPlan(fi);
        int spacing = fc.defaults().roomSpacing();
        int bandX0 = fi * spacing;
        // 生成带：bandX0+8 .. bandX0+168，z 8..152（pitch 56 网格约 3×3 变体）
        int bandW = Math.min(160, spacing - 40);
        int bandH = 144;
        int bandZ0 = 8;

        // ── 1. 牌组：保底 + 增删 + boss ──
        List<String> deck = new ArrayList<>(floor.roomRefs());
        Map<String, Double> opt = floor.optionalRooms();
        for (Map.Entry<String, Double> e : opt.entrySet()) {
            if (!deck.contains(e.getKey()) && rng.nextDouble() < e.getValue()) {
                deck.add(e.getKey());
            }
        }
        if (floor.bossRoom() != null && !deck.contains(floor.bossRoom())) {
            deck.add(floor.bossRoom());
        }
        List<String> active = new ArrayList<>();
        for (String ref : deck) {
            RoomTemplate tpl = fc.rooms().get(ref);
            if (tpl == null) {
                logger.warning("[abyss] gen 牌组引用缺失房间 " + ref + "（层 " + floor.id() + "）");
                continue;
            }
            active.add(ref);
        }
        int n = active.size();
        if (n == 0) return fp;

        // ── 2. 自由布点：拒绝采样 + 最小间距（bbox 半径和 + 廊道间隙）──
        // entry 压西南带、bss 取最远候选；普通房全随机。
        String entryRef = featureOf(fc, active, "entry", active.get(0));
        String bossRef = floor.bossRoom();
        Map<String, RoomPlan> placed = new LinkedHashMap<>();
        Map<String, double[]> centers = new HashMap<>();
        Map<String, Integer> radii = new HashMap<>();
        for (String ref : active) {
            int[] rad = radiusOf(fc.rooms().get(ref), spec);
            radii.put(ref, rad[0] + (rad[1] > rad[0]
                ? rng.nextInt(rad[1] - rad[0] + 1) : 0));
        }
        // 放置顺序：entry → boss → 其余洗牌
        List<String> placementOrder = new ArrayList<>(active);
        placementOrder.remove(entryRef);
        if (bossRef != null) placementOrder.remove(bossRef);
        Collections.shuffle(placementOrder, rng);
        List<String> seq = new ArrayList<>();
        seq.add(entryRef);
        if (bossRef != null) seq.add(bossRef);
        seq.addAll(placementOrder);

        int ex0 = bandX0 + 8, ex1 = bandX0 + bandW, ez0 = bandZ0, ez1 = bandZ0 + bandH;
        for (String ref : seq) {
            int R = radii.get(ref) + 4;     // bbox 半径（含壳体余量）
            double[] pos = null;
            int tries = ref.equals(entryRef) || ref.equals(bossRef) ? 240 : 120;
            double bestScore = -1;
            for (int t = 0; t < tries && pos == null; t++) {
                double cx, cz;
                if (ref.equals(entryRef)) {
                    // 入口房压西南角（band 左下 1/3 区）
                    cx = ex0 + R + rng.nextDouble() * ((ex1 - ex0) / 3.0 - R);
                    cz = ez1 - (ez1 - ez0) / 3.0 + rng.nextDouble() * ((ez1 - ez0) / 3.0 - R);
                } else if (ref.equals(bossRef)) {
                    // Boss 房候选点取"离入口最远"
                    double[] e = centers.get(entryRef);
                    cx = ex0 + R + rng.nextDouble() * (ex1 - ex0 - 2 * R);
                    cz = ez0 + R + rng.nextDouble() * (ez1 - ez0 - 2 * R);
                    if (e != null) {
                        double d = Math.hypot(cx - e[0], cz - e[1]);
                        if (d < (ex1 - ex0) * 0.45) continue;   // 保底远距离
                        if (d < bestScore && rng.nextDouble() < 0.7) continue;
                        bestScore = Math.max(bestScore, d);
                    }
                } else {
                    cx = ex0 + R + rng.nextDouble() * (ex1 - ex0 - 2 * R);
                    cz = ez0 + R + rng.nextDouble() * (ez1 - ez0 - 2 * R);
                }
                // 最小间距：两房 bbox 半径和 + 廊道缓冲 8
                boolean ok = true;
                for (Map.Entry<String, double[]> o : centers.entrySet()) {
                    double need = (R + radii.get(o.getKey()) + 4) + 8.0;
                    if (Math.hypot(cx - o.getValue()[0], cz - o.getValue()[1]) < need) {
                        ok = false; break;
                    }
                }
                if (ok) pos = new double[]{cx, cz};
            }
            if (pos == null) {
                // 兜底 1：缩半径随机重试
                R = radii.get(ref) * 3 / 4 + 3;
                for (int t = 0; t < 120 && pos == null; t++) {
                    double cx = ex0 + R + rng.nextDouble() * (ex1 - ex0 - 2 * R);
                    double cz = ez0 + R + rng.nextDouble() * (ez1 - ez0 - 2 * R);
                    boolean ok = true;
                    for (Map.Entry<String, double[]> o : centers.entrySet()) {
                        double need = (R + radii.get(o.getKey()) + 4) + 6.0;
                        if (Math.hypot(cx - o.getValue()[0], cz - o.getValue()[1]) < need) {
                            ok = false; break;
                        }
                    }
                    if (ok) pos = new double[]{cx, cz};
                }
            }
            if (pos == null) {
                // 兜底 2（保底房不可丢）：全带网格扫描，间距放宽到 3
                R = Math.max(10, radii.get(ref) / 2) + 4;
                for (int gx = ex0 + R; gx <= ex1 - R && pos == null; gx += 4) {
                    for (int gz = ez0 + R; gz <= ez1 - R && pos == null; gz += 4) {
                        boolean ok = true;
                        for (Map.Entry<String, double[]> o : centers.entrySet()) {
                            double need = (R + radii.get(o.getKey()) + 4) + 3.0;
                            if (Math.hypot(gx - o.getValue()[0], gz - o.getValue()[1]) < need) {
                                ok = false; break;
                            }
                        }
                        if (ok) pos = new double[]{gx, gz};
                    }
                }
            }
            if (pos == null) {
                logger.warning("[abyss] gen 层 " + floor.id() + " 房 " + ref
                    + " 无处安放——已跳过");
                continue;
            }
            centers.put(ref, pos);
            RoomTemplate tpl = fc.rooms().get(ref);
            int height = isTall(tpl, spec) ? spec.tallHeight() : spec.baseHeight();
            RoomPlan rp = new RoomPlan(ref, tpl.type(),
                spec.floorY(), spec.floorY() + height + 1);
            carved(rp, (int) Math.round(pos[0]), (int) Math.round(pos[1]),
                Math.min(radii.get(ref), R - 4), spec, rng);   // 兜底缩径生效
            placed.put(ref, rp);
            fp.rooms.put(ref, rp);
            fp.growBounds(rp);
        }

        // ── 3. 拓扑：Prim 随机生成树 + 近邻环边 ──
        List<String[]> edges = spanningEdges(centers, rng);
        Set<String> linked = new HashSet<>();
        for (String[] e : edges) linked.add(e[0] + "|" + e[1]);
        for (String a : centers.keySet()) {
            for (String b : centers.keySet()) {
                if (a.compareTo(b) >= 0 || linked.contains(a + "|" + b)) continue;
                double d = dist(centers.get(a), centers.get(b));
                if (d < spec.cellPitch() * 1.4 && rng.nextDouble() < spec.extraEdges()) {
                    edges.add(new String[]{a, b});
                }
            }
        }

        // ── 4. 走廊 + 门洞（先建全部廊道，再统一探测门洞：穿第三房壳也正确登记）──
        for (String[] e : edges) {
            buildCorridor(fp, placed.get(e[0]), placed.get(e[1]), spec, rng, world);
        }
        assignDoors(fp);

        // ── 5. 房内锚点/刷怪点/装饰规划 ──
        for (RoomPlan rp : fp.rooms.values()) {
            RoomTemplate tpl = fc.rooms().get(rp.ref);
            furnishPlan(rp, tpl, spec, rng, world);
        }

        // ── 6. Boss 封门 + 入口走廊障碍区 ──
        if (bossRef != null) {
            RoomPlan boss = placed.get(bossRef);
            for (RoomPlan.DoorPlan d : boss.doors) d.sealed = true;
            // Boss 入口廊障碍带：取首条连 boss 的走廊
            for (FloorPlan.Corridor c : fp.corridors) {
                if (!c.a.equals(bossRef) && !c.b.equals(bossRef)) continue;
                List<RoomPlan.Pos> soul = new ArrayList<>();
                List<RoomPlan.Pos> magma = new ArrayList<>();
                int half = c.cells.size() / 2;
                for (int i = 0; i < c.cells.size(); i++) {
                    RoomPlan.Pos cell = c.cells.get(i);
                    (i < half ? soul : magma).add(cell);
                }
                fp.zones.put(floor.id() + ".boss_corridor_soul", soul);
                fp.zones.put(floor.id() + ".boss_corridor_magma", magma);
                break;
            }
        }

        // 层入口点 = entry 房 spawn 锚点
        RoomPlan entry = fp.rooms.get(entryRef);
        if (entry != null) fp.entrySpawn = entry.anchor("spawn");
        return fp;
    }

    // ═══ 形状：CA 洞穴 ═══

    /** 雕出洞穴 footprint + 壳体环。中心强制留空。 */
    private void carved(RoomPlan rp, int cx, int cz, int rad, GenSpec spec, Random rng) {
        int R = rad + 4;   // 掩码半幅（含壳体余量）
        int n = 2 * R + 1;
        boolean[][] m = new boolean[n][n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double d = Math.hypot(x - R, z - R);
                // 边界概率随距衰减——越远越不可能成洞
                double p = spec.caFill() - Math.max(0, d - rad * 0.55) * 0.09;
                m[x][z] = d <= rad + 2 && rng.nextDouble() < p;
            }
        }
        // 中心 5×5 强制成洞（锚点保底落点）
        for (int x = R - 2; x <= R + 2; x++)
            for (int z = R - 2; z <= R + 2; z++) m[x][z] = true;

        for (int it = 0; it < spec.caSmooth(); it++) {
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
            // 中心保护区不被平滑掉
            for (int x = R - 2; x <= R + 2; x++)
                for (int z = R - 2; z <= R + 2; z++) nx[x][z] = true;
            m = nx;
        }

        // 含中心的最大连通域
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
        // footprint = 连通域；wall = 与洞相邻的壳体环（2 层厚保证隔绝不漏光）
        Set<Long> cav = new HashSet<>();
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++)
                if (seen[x][z]) cav.add(RoomPlan.pack(cx + x - R, cz + z - R));
        Set<Long> ring = new HashSet<>();
        for (int t = 0; t < 2; t++) {
            Set<Long> expand = new HashSet<>(cav);
            expand.addAll(ring);
            for (long pk : expand) {
                int x = (int) (pk >> 32), z = (int) (long) pk;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long k = RoomPlan.pack(x + dx, z + dz);
                        if (!cav.contains(k)) ring.add(k);
                    }
                }
            }
            ring.removeAll(cav);
        }
        rp.floor.addAll(cav);
        rp.wall.addAll(ring);
        // bbox = 环外扩
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long pk : ring) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        rp.minX = minX; rp.maxX = maxX; rp.minZ = minZ; rp.maxZ = maxZ;
    }

    // ═══ 拓扑 ═══

    /** Prim 随机生成树：权重 = 距离 × 抖动，保证全图连通且不撞设计意图。 */
    private List<String[]> spanningEdges(Map<String, double[]> centers, Random rng) {
        List<String[]> edges = new ArrayList<>();
        if (centers.size() < 2) return edges;
        Set<String> in = new HashSet<>();
        List<String> rest = new ArrayList<>(centers.keySet());
        in.add(rest.remove(0));
        while (!rest.isEmpty()) {
            String bestA = null, bestB = null;
            double bestW = Double.MAX_VALUE;
            for (String a : in) {
                for (String b : rest) {
                    double w = dist(centers.get(a), centers.get(b))
                        * (0.6 + rng.nextDouble() * 0.8);
                    if (w < bestW) { bestW = w; bestA = a; bestB = b; }
                }
            }
            edges.add(new String[]{bestA, bestB});
            in.add(bestB);
            rest.remove(bestB);
        }
        return edges;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    // ═══ 走廊 ═══

    /**
     * A↔B 走廊：各取朝向对方的壳体点为门位，其间折线连通。
     * 廊道列（5 宽）落盘进 {@code corridor.cells}（y=floorY 的地面层），
     * 穿越壳体的环格记入双方 DoorPlan（锁门格集）。
     */
    private void buildCorridor(FloorPlan fp, RoomPlan a, RoomPlan b,
                               GenSpec spec, Random rng, World world) {
        int[] pa = wallPointToward(a, centerOf(b));
        int[] pb = wallPointToward(b, centerOf(a));
        if (pa == null || pb == null) {
            logger.warning("[abyss] 走廊无门位: " + a.ref + "↔" + b.ref);
            return;
        }
        FloorPlan.Corridor corr = new FloorPlan.Corridor(a.ref, b.ref);
        int w = Math.max(3, spec.corridorWidth() | 1);   // 奇数宽
        int half = w / 2;

        // 折线：pa → bend → pb（随机先 x 还是先 z，带折点抖动）
        List<int[]> path = new ArrayList<>();
        boolean xFirst = rng.nextBoolean();
        int bx = xFirst ? pb[0] : pa[0];
        int bz = xFirst ? pa[1] : pb[1];
        // 折点在直交点上再抖一段，让走廊有弯
        if (xFirst) bz += rng.nextInt(9) - 4; else bx += rng.nextInt(9) - 4;
        lineCells(path, pa[0], pa[1], bx, bz);
        lineCells(path, bx, bz, pb[0], pb[1]);

        // 折线 → 带宽列集（Chebyshev 扩 w/2，斜角收窄防肥）
        Set<Long> band = new HashSet<>();
        for (int[] c : path) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > half) continue;   // 斜角收窄
                    band.add(RoomPlan.pack(c[0] + dx, c[1] + dz));
                }
            }
        }
        corr.band = band;
        for (long pk : band) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            corr.cells.add(new RoomPlan.Pos(x, spec.floorY(), z));
        }
        // 中点取折线路径中位（cells 是 Set 序，不能取 cells[size/2]）
        if (!path.isEmpty()) {
            int[] mc = path.get(path.size() / 2);
            corr.mid = new RoomPlan.Pos(mc[0], spec.floorY(), mc[1]);
        }
        fp.corridors.add(corr);
    }

    /**
     * 门洞后处理：每条廊带 × 每间房，壳体环 ∩ 廊带的列 → 门洞格集。
     * <p>不限制在走廊声明连接的两房——廊道若穿过第三房壳体同样开门，
     * 保证锁门填充能封住任何真实开口（否则穿出洞会被当成无门可锁的破绽）。</p>
     */
    private void assignDoors(FloorPlan fp) {
        for (FloorPlan.Corridor c : fp.corridors) {
            for (RoomPlan r : fp.rooms.values()) {
                // 穿第三房壳体时 linkedRoom 记走廊键（两端房都不对）
                String linked = r.ref.equals(c.a) ? c.b
                    : (r.ref.equals(c.b) ? c.a : "corr." + c.key());
                RoomPlan.DoorPlan d = new RoomPlan.DoorPlan(linked);
                for (long pk : r.wall) {
                    if (!c.band.contains(pk)) continue;
                    int x = (int) (pk >> 32), z = (int) (long) pk;
                    for (int y = r.floorY + 1; y <= r.floorY + 4; y++) {
                        d.cells.add(new RoomPlan.Pos(x, y, z));
                    }
                }
                if (!d.cells.isEmpty()) r.doors.add(d);
            }
        }
    }

    /** 找 wall 环上朝 target 方向最外的壳体列。 */
    private @Nullable int[] wallPointToward(RoomPlan r, double[] target) {
        double cx = (r.minX + r.maxX) / 2.0, cz = (r.minZ + r.maxZ) / 2.0;
        double dx = target[0] - cx, dz = target[1] - cz;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) { dx = 1; dz = 0; len = 1; }
        dx /= len; dz /= len;
        int[] best = null;
        double bestP = -Double.MAX_VALUE;
        for (long pk : r.wall) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            double p = (x - cx) * dx + (z - cz) * dz;
            if (p > bestP) { bestP = p; best = new int[]{x, z}; }
        }
        return best;
    }

    private static double[] centerOf(RoomPlan r) {
        return new double[]{(r.minX + r.maxX) / 2.0, (r.minZ + r.maxZ) / 2.0};
    }

    /** 直线步进（Bresenham 近似）。 */
    private static void lineCells(List<int[]> out, int x0, int z0, int x1, int z1) {
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = dx - dz, x = x0, z = z0;
        while (true) {
            out.add(new int[]{x, z});
            if (x == x1 && z == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
        }
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
        // 刷怪点：8 个洞内随机格（最小间距 4）
        rp.mobSpots.addAll(scatter(rp, 10, 4, rng, w));

        switch (feature) {
            case "altar" -> {
                int[] a = nearestFloor(rp, cc[0], cc[1]);
                rp.anchors.put("altar", new Location(w, a[0], fy + 1, a[1]));
            }
            case "chest" -> rp.anchors.put("chest",
                new Location(w, cc[0], fy + 1, cc[1]));
            case "shop" -> {
                rp.anchors.put("merchant", new Location(w, cc[0], fy + 1, cc[1] + 3));
                rp.anchors.put("counter", new Location(w, cc[0] - 4, fy + 1, cc[1]));
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
                // 出口传送区：房内离首个门最远一侧的平台（4×4）
                int[] far = farthestFloorFromDoors(rp);
                Location c = new Location(w, far[0], fy + 1, far[1]);
                rp.exitMin = new Location(w, far[0] - 1, fy + 1, far[1] - 1);
                rp.exitMax = new Location(w, far[0] + 2, fy + 4, far[1] + 2);
                rp.anchors.put("exit", c);
                rp.anchors.put("boss", rp.anchors.get("center"));
            }
            case "entry" -> {
                // 向导 NPC：spawn 旁 3 格，面向 spawn
                Location sp = rp.anchors.get("spawn");
                Location g = sp.clone().add(2, 0, 2);
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
            int x = (int) (pk >> 32), z = (int) (long) pk;
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
        // 按 z 分组扫描：floor 列里找 (x,z-1)∈wall 的最长连续 x 段
        Map<Integer, List<Integer>> byZ = new HashMap<>();
        for (long pk : rp.floor) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
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
            int cx = (int) (pk >> 32), cz = (int) (long) pk;
            int d = Math.abs(cx - x) + Math.abs(cz - z);
            if (d < bd) { bd = d; best = pk; }
        }
        if (best == -1) return new int[]{x, z};
        return new int[]{(int) (best >> 32), (int) best};
    }

    /** 离全部门洞最远的洞内格（出口平台位）。 */
    private int[] farthestFloorFromDoors(RoomPlan rp) {
        Set<Long> dc = new HashSet<>();
        for (RoomPlan.DoorPlan d : rp.doors)
            for (RoomPlan.Pos p : d.cells) dc.add(RoomPlan.pack(p.x(), p.z()));
        long best = -1; int bd = -1;
        for (long pk : rp.floor) {
            int cx = (int) (pk >> 32), cz = (int) (long) pk;
            int d = Integer.MAX_VALUE;
            for (long dp : dc) {
                int dx = (int) (dp >> 32), dz = (int) (long) dp;
                d = Math.min(d, Math.abs(cx - dx) + Math.abs(cz - dz));
            }
            if (d > bd) { bd = d; best = pk; }
        }
        if (best == -1) return new int[]{(rp.minX + rp.maxX) / 2, (rp.minZ + rp.maxZ) / 2};
        return new int[]{(int) (best >> 32), (int) best};
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

    private static double jitter(Random rng, double range) {
        return range > 0 ? (rng.nextDouble() * 2 - 1) * range : 0;
    }
}
