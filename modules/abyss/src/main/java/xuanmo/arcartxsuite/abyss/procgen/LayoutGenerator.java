package xuanmo.arcartxsuite.abyss.procgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenRoomAsset.RoomKind;
import xuanmo.arcartxsuite.abyss.template.ProcGenConfig;

/**
 * 布局生成器：网格布点 + 随机拼装。
 *
 * <p>模型（与 ProcGen 同构）：每间房占一个网格格位，房几何按格位中心居
 * 中铺放——相邻格位共享墙轴向对齐（两房中心同轴 → 面对面墙的墙心天然
 * 共线），门洞开在各房间朝墙的墙心，走廊桥接两墙外表面之间的空隙。
 * 这样房间尺寸可以各不相同而门永远对齐，不存在"墙堵门"。</p>
 *
 * <p>主链：起点 → N 个战斗房随机游走 → Boss；支线房（商店/宝藏/精英/事件/
 * 休息）挂接到主链随机格位的空闲邻居。每间房随机 0/90/180/270 旋转
 * （房内相对坐标随旋转一并换算）。</p>
 */
public final class LayoutGenerator {

    /** 一个已布点的房间实例。 */
    public static final class PlacedRoom {
        public final ProcGenRoomAsset asset;
        public final int cellI, cellJ;       // 网格坐标
        public final int rotDeg;             // 0/90/180/270 顺时针
        public final int worldX, worldY, worldZ;  // 旋转后 footprint 的最小角
        public final int w, h, d;            // 旋转后尺寸
        public final boolean mainChain;      // 主链房（start/combat/boss）vs 支线
        public final List<DoorBox> doors = new ArrayList<>();   // 本房墙上的门洞盒
        public final Map<String, int[]> anchors = new LinkedHashMap<>(); // 世界坐标锚点缓存
        public PlacedRoom(ProcGenRoomAsset a, int i, int j, int rot,
                          int x, int y, int z, int w, int h, int d, boolean main) {
            this.asset = a; this.cellI = i; this.cellJ = j; this.rotDeg = rot;
            this.worldX = x; this.worldY = y; this.worldZ = z;
            this.w = w; this.h = h; this.d = d; this.mainChain = main;
        }
        /** 房内相对坐标 → 世界坐标（随旋转换算）。 */
        public int[] toWorld(int lx, int ly, int lz) {
            int[] p = asset.region() != null
                ? asset.region().rotatePoint(lx, ly, lz, rotDeg)
                : new int[]{lx, ly, lz};
            return new int[]{worldX + p[0], worldY + p[1], worldZ + p[2]};
        }
    }

    /** 一面墙上的门洞盒（房间内边界面，1 格厚）。 */
    public static final class DoorBox {
        public final int x1, y1, z1, x2, y2, z2;   // 世界坐标闭区间
        public DoorBox(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.x1 = Math.min(x1, x2); this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2); this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
        }
    }

    /** 相邻格位对的门/走廊边。 */
    public static final class DoorEdge {
        public final PlacedRoom a, b;
        /** 'x' = a 在 b 西侧（沿 x 轴相邻）；'z' = a 在 b 北侧。 */
        public final char axis;
        public DoorEdge(PlacedRoom a, PlacedRoom b, char axis) {
            this.a = a; this.b = b; this.axis = axis;
        }
    }

    /** 布局产物。 */
    public static final class Layout {
        public final List<PlacedRoom> rooms = new ArrayList<>();
        public final List<DoorEdge> edges = new ArrayList<>();
        public @Nullable PlacedRoom start;
        public @Nullable PlacedRoom boss;
        public int baseY;
    }

    // ── 生成 ─────────────────────────────────────────────

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * 生成布局。
     * @param byKind   房型 → 构件池（权重抽取）
     * @param cfg      procgen.yml 配置
     * @param seed     布局种子
     * @param baseY    地板基准 y（世界坐标）
     * @param originX/Z 布局原点（世界坐标，网格 (0,0) 的格中心）
     */
    public static @Nullable Layout generate(Map<RoomKind, List<ProcGenRoomAsset>> byKind,
                                            ProcGenConfig cfg, long seed,
                                            int baseY, int originX, int originZ) {
        // 房型数量：counts 覆盖 → length 档位
        Map<RoomKind, Integer> want = resolveCounts(cfg, seed);

        // 起步校验：start/boss/combat 必需
        if (byKind.getOrDefault(RoomKind.START, List.of()).isEmpty()
            || byKind.getOrDefault(RoomKind.BOSS, List.of()).isEmpty()
            || byKind.getOrDefault(RoomKind.COMBAT, List.of()).isEmpty()) {
            return null;
        }
        int combatN = want.getOrDefault(RoomKind.COMBAT, 4);
        want.put(RoomKind.START, 1);
        want.put(RoomKind.BOSS, 1);

        // 网格 pitch：最大 footprint + 走廊间距 + 2 格余量
        int maxSpan = 1;
        for (List<ProcGenRoomAsset> pool : byKind.values()) {
            for (ProcGenRoomAsset a : pool) {
                if (a.region() != null) {
                    maxSpan = Math.max(maxSpan,
                        Math.max(a.region().width(), a.region().depth()));
                }
            }
        }
        int pitch = maxSpan + Math.max(0, cfg.corridor().length()) + 2;

        // 多次尝试（游走自陷时重开）
        for (int attempt = 0; attempt < 30; attempt++) {
            Layout l = tryGenerate(byKind, want, combatN, pitch, baseY,
                originX, originZ, new Random(seed + attempt * 7919L));
            if (l != null) return l;
        }
        return null;
    }

    private static @Nullable Layout tryGenerate(
            Map<RoomKind, List<ProcGenRoomAsset>> byKind,
            Map<RoomKind, Integer> want, int combatN, int pitch,
            int baseY, int originX, int originZ, Random rnd) {

        Layout layout = new Layout();
        layout.baseY = baseY;
        Map<Long, PlacedRoom> cells = new HashMap<>();

        // ── 主链：start → combat×N → boss ──
        int ci = 0, cj = 0;
        PlacedRoom start = place(layout, cells, byKind, RoomKind.START,
            ci, cj, pitch, baseY, originX, originZ, rnd, true);
        if (start == null) return null;
        layout.start = start;
        List<PlacedRoom> chain = new ArrayList<>();
        chain.add(start);
        for (int i = 0; i < combatN; i++) {
            int[] next = freeNeighbor(cells, ci, cj, rnd);
            if (next == null) return null;   // 游走自陷 → 外层重试
            ci = next[0]; cj = next[1];
            PlacedRoom r = place(layout, cells, byKind, RoomKind.COMBAT,
                ci, cj, pitch, baseY, originX, originZ, rnd, true);
            if (r == null) return null;
            chain.add(r);
        }
        int[] bossCell = freeNeighbor(cells, ci, cj, rnd);
        if (bossCell == null) return null;
        PlacedRoom boss = place(layout, cells, byKind, RoomKind.BOSS,
            bossCell[0], bossCell[1], pitch, baseY, originX, originZ, rnd, true);
        if (boss == null) return null;
        layout.boss = boss;
        chain.add(boss);

        // ── 支线房挂接到主链随机格（不挂 start/boss）──
        List<PlacedRoom> attachable = new ArrayList<>(chain);
        attachable.remove(0);                 // 去 start
        attachable.remove(attachable.size() - 1); // 去 boss
        if (attachable.isEmpty()) attachable = new ArrayList<>(chain);
        for (RoomKind kind : new RoomKind[]{
                RoomKind.SHOP, RoomKind.TREASURE, RoomKind.ELITE,
                RoomKind.EVENT, RoomKind.REST}) {
            int n = want.getOrDefault(kind, 0);
            for (int i = 0; i < n; i++) {
                List<PlacedRoom> shuffled = new ArrayList<>(attachable);
                Collections.shuffle(shuffled, rnd);
                boolean placed = false;
                for (PlacedRoom host : shuffled) {
                    int[] cell = freeNeighbor(cells, host.cellI, host.cellJ, rnd);
                    if (cell == null) continue;
                    PlacedRoom r = place(layout, cells, byKind, kind,
                        cell[0], cell[1], pitch, baseY, originX, originZ, rnd, false);
                    if (r != null) { placed = true; break; }
                }
                if (!placed) break; // 支线房塞不下就跳过剩余
            }
        }

        // ── 门边：所有相邻格对 ──
        for (PlacedRoom a : layout.rooms) {
            for (int[] dir : DIRS) {
                PlacedRoom b = cells.get(key(a.cellI + dir[0], a.cellJ + dir[1]));
                if (b == null) continue;
                if (a.hashCode() <= b.hashCode()) { // 每对只记一次
                    layout.edges.add(new DoorEdge(a, b, dir[0] != 0 ? 'x' : 'z'));
                }
            }
        }
        return layout;
    }

    private static long key(int i, int j) {
        return (((long) i) << 32) | (j & 0xFFFFFFFFL);
    }

    private static @Nullable int[] freeNeighbor(Map<Long, PlacedRoom> cells,
                                                int i, int j, Random rnd) {
        List<int[]> dirs = new ArrayList<>(List.of(DIRS));
        Collections.shuffle(dirs, rnd);
        for (int[] d : dirs) {
            if (!cells.containsKey(key(i + d[0], j + d[1]))) {
                return new int[]{i + d[0], j + d[1]};
            }
        }
        return null;
    }

    private static @Nullable PlacedRoom place(
            Layout layout, Map<Long, PlacedRoom> cells,
            Map<RoomKind, List<ProcGenRoomAsset>> byKind, RoomKind kind,
            int ci, int cj, int pitch, int baseY, int ox, int oz,
            Random rnd, boolean main) {
        List<ProcGenRoomAsset> pool = byKind.getOrDefault(kind, List.of());
        if (pool.isEmpty()) return null;
        ProcGenRoomAsset asset = weightedPick(pool, rnd);
        int rot = rnd.nextInt(4) * 90;
        int[] size = asset.region() != null
            ? asset.region().rotatedSize(rot)
            : asset.size();   // schem 转换后 region 必非空
        if (size == null) return null;
        int w = size[0], h = size[1], d = size[2];
        // 格中心 = 世界 (ox + i*pitch, baseY, oz + j*pitch)；房按中心居中
        int cx = ox + ci * pitch, cz = oz + cj * pitch;
        int x = cx - w / 2, z = cz - d / 2;
        PlacedRoom pr = new PlacedRoom(asset, ci, cj, rot, x, baseY, z,
            w, h, d, main);
        cells.put(key(ci, cj), pr);
        layout.rooms.add(pr);
        return pr;
    }

    private static ProcGenRoomAsset weightedPick(List<ProcGenRoomAsset> pool,
                                                 Random rnd) {
        int total = 0;
        for (ProcGenRoomAsset a : pool) total += Math.max(1, a.weight());
        int roll = rnd.nextInt(Math.max(1, total));
        for (ProcGenRoomAsset a : pool) {
            roll -= Math.max(1, a.weight());
            if (roll < 0) return a;
        }
        return pool.get(pool.size() - 1);
    }

    /** counts 覆盖 → length 档位（short/standard/long 的房型数量表）。 */
    private static Map<RoomKind, Integer> resolveCounts(ProcGenConfig cfg,
                                                        long seed) {
        Map<RoomKind, Integer> out = new LinkedHashMap<>();
        Random rnd = new Random(seed ^ 0x9E3779B9L);
        String len = cfg.length() == null ? "standard" : cfg.length().toLowerCase();
        switch (len) {
            case "short", "短线" -> {
                out.put(RoomKind.COMBAT, 3); out.put(RoomKind.SHOP, 1);
                out.put(RoomKind.TREASURE, 1); out.put(RoomKind.ELITE, 0);
                out.put(RoomKind.EVENT, rnd.nextInt(2)); out.put(RoomKind.REST, 0);
            }
            case "long", "长线" -> {
                out.put(RoomKind.COMBAT, 6 + rnd.nextInt(2));
                out.put(RoomKind.SHOP, 1);
                out.put(RoomKind.TREASURE, 1 + rnd.nextInt(2));
                out.put(RoomKind.ELITE, 1); out.put(RoomKind.EVENT, 1);
                out.put(RoomKind.REST, 1);
            }
            default -> {
                out.put(RoomKind.COMBAT, 4 + rnd.nextInt(2));
                out.put(RoomKind.SHOP, 1); out.put(RoomKind.TREASURE, 1);
                out.put(RoomKind.ELITE, rnd.nextInt(2));
                out.put(RoomKind.EVENT, rnd.nextInt(2));
                out.put(RoomKind.REST, rnd.nextInt(2));
            }
        }
        // counts 覆盖（数字或 "a-b"）
        for (var e : cfg.counts().entrySet()) {
            try {
                RoomKind k = RoomKind.valueOf(e.getKey().toUpperCase());
                out.put(k, parseCount(e.getValue(), rnd));
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    private static int parseCount(String v, Random rnd) {
        if (v == null) return 0;
        String s = v.trim();
        if (s.contains("-")) {
            String[] p = s.split("-", 2);
            int lo = parseInt(p[0], 0), hi = parseInt(p[1], lo);
            return lo + rnd.nextInt(Math.max(1, hi - lo + 1));
        }
        return parseInt(s, 0);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }
}
