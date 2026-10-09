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
 * 图块拼接迷宫生成器（roguelike 房间的工业级做法）：
 * 房间是**预制尺寸的完整图块**，随机性在拼接而非雕刻。
 * <p>
 * 每层 = 5×5 网格上随机长出的一块连通多边形（polyomino）；
 * 每个格位放一间 40×40 大房（内空 38×38，宽阔战斗空间）；
 * 相邻格共享墙壁，共享墙上随机位切 4 宽门洞——
 * 门对齐由构造保证，胖多边形自带环路，无走廊概念。
 * <p>
 * 入口房压多边形直径一端、Boss 房压另一端（图上 BFS 最远跳数），
 * 支线房按概率增删。内饰按 feature 在确定性变体中轮换
 * （prefab 字段可挂 {@code regions/} 手建模板覆盖程序化内饰）。
 * <p>
 * 仅做纯数学规划（不触碰世界）——{@link #plan} 产出的
 * {@link GeneratedLayout} 由 {@link PlanPainter} 统一写块。
 */
public final class OrganicGenerator {

    private final java.util.logging.Logger logger;

    /** 格位键基数（grid ≤ 256 时无碰撞）。 */
    private static final int KEY = 257;

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
            FloorPlan fp = planFloor(world, floor, fi, fs, rng, fc);
            layout.putFloor(fp, world);
        }
        return layout;
    }

    // ═══ 层生成 ═══

    private FloorPlan planFloor(World world, Floor floor, int fi,
                                GenSpec spec, Random rng, FloorConfig fc) {
        FloorPlan fp = new FloorPlan(fi);
        int ox = fi * fc.defaults().roomSpacing(), oz = 0;

        // ── 1. 牌组：保底 + 增删 + boss 兜底 ──
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

        // ── 2. 多边形生长 + 直径定位 entry/boss ──
        int tile = spec.tileSize();
        List<int[]> tiles = polyomino(spec.grid(), active.size(), rng);
        Map<Integer, List<int[]>> adj = adjacency(tiles);
        String entryRef = featureOf(fc, active, "entry", active.get(0));
        String bossRef = floor.bossRoom();
        // 直径两端：任一顶点 BFS 最深点 a，再从 a BFS 最深点 b → (a,b) 即最长链
        int[][] ep = diameterEndpoints(tiles, adj, rng);
        int[] entryTile = ep[0], bossTile = ep[1];
        // 分配 ref → 格位：entry/boss 定死，其余洗牌
        Map<Integer, String> assign = new LinkedHashMap<>();
        int ek = pkTile(entryTile), bk = pkTile(bossTile);
        List<String> rest = new ArrayList<>(active);
        rest.remove(entryRef);
        if (bossRef != null) rest.remove(bossRef);
        Collections.shuffle(rest, rng);
        List<int[]> freeTiles = new ArrayList<>(tiles);
        freeTiles.removeIf(t -> pkTile(t) == ek || pkTile(t) == bk);
        assign.put(ek, entryRef);
        if (bossRef != null) assign.put(bk, bossRef);
        for (int i = 0; i < rest.size() && i < freeTiles.size(); i++) {
            assign.put(pkTile(freeTiles.get(i)), rest.get(i));
        }

        // ── 3. 物化房间：内空/壳环/bbox ──
        Map<Integer, RoomPlan> tileRoom = new HashMap<>();
        for (int[] t : tiles) {
            String ref = assign.get(pkTile(t));
            if (ref == null) continue;
            RoomTemplate tpl = fc.rooms().get(ref);
            int h = isTall(tpl, spec) ? spec.tallHeight() : spec.baseHeight();
            RoomPlan rp = new RoomPlan(ref, tpl.type(),
                spec.floorY(), spec.floorY() + h + 1);
            int x0 = ox + t[0] * tile, z0 = oz + t[1] * tile;
            for (int x = x0; x < x0 + tile; x++) {
                for (int z = z0; z < z0 + tile; z++) {
                    boolean border = x == x0 || z == z0
                        || x == x0 + tile - 1 || z == z0 + tile - 1;
                    (border ? rp.wall : rp.floor).add(RoomPlan.pack(x, z));
                }
            }
            rp.minX = x0; rp.maxX = x0 + tile - 1;
            rp.minZ = z0; rp.maxZ = z0 + tile - 1;
            tileRoom.put(pkTile(t), rp);
            fp.rooms.put(ref, rp);
            fp.growBounds(rp);
        }

        // ── 4. 共享墙切门：相邻格即通路，门位随机偏移 ──
        for (Map.Entry<Integer, List<int[]>> e : adj.entrySet()) {
            int[] ta = unpack(e.getKey());
            RoomPlan ra = tileRoom.get(e.getKey());
            if (ra == null) continue;
            for (int[] tb : e.getValue()) {
                RoomPlan rb = tileRoom.get(pkTile(tb));
                if (rb == null) continue;
                if (pkTile(tb) < e.getKey()) continue;   // 每条边只切一次
                int seam, lo, hi;
                boolean alongZ;
                int dh = spec.doorHalf();
                if (tb[0] == ta[0] + 1) {               // B 在 A 东
                    seam = ox + tb[0] * tile - 1;       // A 东墙线
                    lo = oz + ta[1] * tile + dh + 6;    // 门孔离墙端留≥6格墙垛
                    hi = oz + ta[1] * tile + tile - 6 - dh;
                    alongZ = true;
                } else if (tb[1] == ta[1] + 1) {        // B 在 A 南
                    seam = oz + tb[1] * tile - 1;
                    lo = ox + ta[0] * tile + dh + 6;
                    hi = ox + ta[0] * tile + tile - 6 - dh;
                    alongZ = false;
                } else continue;                        // tb 是西/北侧（反向已处理）
                // 门心偏移；double-door 概率切第二道门（共享墙更通透）
                List<Integer> centers = new ArrayList<>();
                centers.add(lo + rng.nextInt(hi - lo + 1));
                if (rng.nextDouble() < spec.doubleDoor()) {
                    int c2 = lo + rng.nextInt(hi - lo + 1);
                    if (Math.abs(c2 - centers.get(0)) > dh * 2 + 4) {
                        centers.add(c2);
                    }
                }
                Set<RoomPlan.Pos> cells = new HashSet<>();
                for (int c : centers) {
                    for (int i = -dh; i <= dh; i++) {
                        for (int d = 0; d <= 1; d++) {      // 两墙层
                            int dx = alongZ ? seam + d : c + i;
                            int dz = alongZ ? c + i : seam + d;
                            for (int y = spec.floorY() + 1;
                                 y <= spec.floorY() + 4; y++) {
                                cells.add(new RoomPlan.Pos(dx, y, dz));
                            }
                        }
                    }
                }
                RoomPlan.DoorPlan da = new RoomPlan.DoorPlan(rb.ref);
                RoomPlan.DoorPlan db = new RoomPlan.DoorPlan(ra.ref);
                da.cells.addAll(cells); db.cells.addAll(cells);
                ra.doors.add(da); rb.doors.add(db);
            }
        }

        // ── 5. 房内锚点/刷怪点 ──
        for (RoomPlan rp : fp.rooms.values()) {
            furnishPlan(rp, fc.rooms().get(rp.ref), spec, rng, world);
        }

        // ── 6. Boss 封门 + 门内侧陷阱带 ──
        if (bossRef != null) {
            RoomPlan boss = fp.rooms.get(bossRef);
            if (boss != null) {
                for (RoomPlan.DoorPlan d : boss.doors) d.sealed = true;
                if (!boss.doors.isEmpty()) {
                    // 门内侧 2..9 格带：前半魂沙后半岩浆
                    int[] dc = doorCentroid(boss.doors.get(0));
                    List<RoomPlan.Pos> near = new ArrayList<>();
                    for (long pk : boss.floor) {
                        int x = px(pk), z = pz(pk);
                        double d = Math.hypot(x - dc[0], z - dc[1]);
                        if (d >= 2 && d <= 9) {
                            near.add(new RoomPlan.Pos(x, spec.floorY(), z));
                        }
                    }
                    near.sort((a, b) -> Double.compare(
                        Math.hypot(a.x() - dc[0], a.z() - dc[1]),
                        Math.hypot(b.x() - dc[0], b.z() - dc[1])));
                    int half = near.size() / 2;
                    fp.zones.put(floor.id() + ".boss_corridor_soul",
                        new ArrayList<>(near.subList(0, half)));
                    fp.zones.put(floor.id() + ".boss_corridor_magma",
                        new ArrayList<>(near.subList(half, near.size())));
                }
            }
        }

        // 层入口点 = entry 房 spawn 锚点
        RoomPlan entry = fp.rooms.get(entryRef);
        if (entry != null) fp.entrySpawn = entry.anchor("spawn");
        return fp;
    }

    // ═══ 多边形 ═══

    private static int pkTile(int[] t) { return t[0] * KEY + t[1]; }
    private static int[] unpack(int k) { return new int[]{k / KEY, k % KEY}; }

    /** 网格多边形生长：起点随机，每步在连通前沿均匀取一格。 */
    private static List<int[]> polyomino(int g, int n, Random rng) {
        List<int[]> cells = new ArrayList<>();
        Set<Integer> in = new HashSet<>();
        int[] s = {rng.nextInt(g), rng.nextInt(g)};
        cells.add(s); in.add(pkTile(s));
        while (cells.size() < n) {
            List<int[]> frontier = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (int[] c : cells) {
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    if (nx < 0 || nz < 0 || nx >= g || nz >= g) continue;
                    int k = pkTile(new int[]{nx, nz});
                    if (in.contains(k) || seen.contains(k)) continue;
                    seen.add(k); frontier.add(new int[]{nx, nz});
                }
            }
            if (frontier.isEmpty()) break;   // 网格塞满（不会发生：GRID=5≥房间数上限）
            int[] p = frontier.get(rng.nextInt(frontier.size()));
            cells.add(p); in.add(pkTile(p));
        }
        return cells;
    }

    /** 邻接表：格位键 → 四邻格位列表。 */
    private static Map<Integer, List<int[]>> adjacency(List<int[]> cells) {
        Set<Integer> in = new HashSet<>();
        for (int[] c : cells) in.add(pkTile(c));
        Map<Integer, List<int[]>> out = new HashMap<>();
        for (int[] c : cells) {
            List<int[]> l = new ArrayList<>();
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int[] nb = {c[0] + d[0], c[1] + d[1]};
                if (in.contains(pkTile(nb))) l.add(nb);
            }
            out.put(pkTile(c), l);
        }
        return out;
    }

    /** BFS 层数：src → 各格跳数。 */
    private static Map<Integer, Integer> tileBfs(Map<Integer, List<int[]>> adj, int[] src) {
        Map<Integer, Integer> dist = new HashMap<>();
        Deque<int[]> q = new ArrayDeque<>();
        dist.put(pkTile(src), 0);
        q.add(src);
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int d = dist.get(pkTile(c));
            for (int[] nb : adj.getOrDefault(pkTile(c), List.of())) {
                if (!dist.containsKey(pkTile(nb))) {
                    dist.put(pkTile(nb), d + 1);
                    q.add(nb);
                }
            }
        }
        return dist;
    }

    /** 多边形直径两端：双 BFS（任一点最深 → 该点再最深）。 */
    private static int[][] diameterEndpoints(List<int[]> tiles,
                                             Map<Integer, List<int[]>> adj, Random rng) {
        int[] a = tiles.get(rng.nextInt(tiles.size()));
        Map<Integer, Integer> d1 = tileBfs(adj, a);
        a = farthest(tiles, d1);
        Map<Integer, Integer> d2 = tileBfs(adj, a);
        int[] b = farthest(tiles, d2);
        return new int[][]{a, b};
    }

    private static int[] farthest(List<int[]> tiles, Map<Integer, Integer> dist) {
        int[] best = tiles.get(0); int bd = -1;
        for (int[] t : tiles) {
            int d = dist.getOrDefault(pkTile(t), 0);
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }

    private static int[] doorCentroid(RoomPlan.DoorPlan d) {
        if (d == null || d.cells.isEmpty()) return new int[]{0, 0};
        long sx = 0, sz = 0;
        for (RoomPlan.Pos p : d.cells) { sx += p.x(); sz += p.z(); }
        return new int[]{(int) (sx / d.cells.size()), (int) (sz / d.cells.size())};
    }

    private static int px(long pk) { return (int) (pk >> 32); }
    private static int pz(long pk) { return (int) pk; }

    // ═══ 房内布置 ═══

    private void furnishPlan(RoomPlan rp, @Nullable RoomTemplate tpl,
                             GenSpec spec, Random rng, World w) {
        String feature = tpl != null && tpl.gen() != null && tpl.gen().feature() != null
            ? tpl.gen().feature() : featureDefault(rp.type);
        int fy = rp.floorY;
        int cx = (rp.minX + rp.maxX) / 2, cz = (rp.minZ + rp.maxZ) / 2;
        rp.anchors.put("center", new Location(w, cx + 0.5, fy + 1, cz + 0.5));
        rp.anchors.put("spawn", spawnOf(rp, w));
        // 刷怪点：10 个内空格（最小间距 5）
        rp.mobSpots.addAll(scatter(rp, 10, 5, rng, w));

        switch (feature) {
            case "altar" -> rp.anchors.put("altar",
                new Location(w, cx, fy + 1, cz));
            case "chest" -> rp.anchors.put("chest",
                new Location(w, cx, fy + 1, cz));
            case "shop" -> {
                rp.anchors.put("merchant", new Location(w, cx + 0.5, fy + 1, cz + 3.5, 180f, 0f));
                rp.anchors.put("counter", new Location(w, cx - 3, fy + 1, cz + 2));
            }
            case "puzzle" -> {
                // 拉杆挂北墙（内空北排 z=minZ+1，贴 minZ 墙线），
                // 位次距中 ±3/±9；若撞上北墙门洞开口则向外挪
                Set<Integer> blockedX = new HashSet<>();
                for (RoomPlan.DoorPlan d : rp.doors) {
                    for (RoomPlan.Pos p : d.cells) {
                        if (p.z() == rp.minZ) blockedX.add(p.x());
                    }
                }
                int lz = rp.minZ + 1, ly = fy + 2;
                int[] xs = {cx - 9, cx - 3, cx + 3, cx + 9};
                for (int i = 0; i < 4; i++) {
                    int x = xs[i];
                    while (blockedX.contains(x) && x < rp.maxX - 2) x++;
                    rp.anchors.put("lever_" + (i + 1), new Location(w, x, ly, lz));
                }
            }
            case "boss" -> {
                int[] far = farthestFloorFromDoors(rp);
                Location c = new Location(w, far[0], fy + 1, far[1]);
                rp.exitMin = new Location(w, far[0] - 1, fy + 1, far[1] - 1);
                rp.exitMax = new Location(w, far[0] + 2, fy + 4, far[1] + 2);
                rp.anchors.put("exit", c);
                rp.anchors.put("boss", rp.anchors.get("center"));
            }
            case "entry" -> {
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
        // 取第一门内侧格，面向房心
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

    /** 房内散布采样：n 个内空格，两两曼哈顿距 ≥ gap（尽力而为 40 轮）。 */
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

    /** 离全部门洞最远的内空格（出口平台位）。 */
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
