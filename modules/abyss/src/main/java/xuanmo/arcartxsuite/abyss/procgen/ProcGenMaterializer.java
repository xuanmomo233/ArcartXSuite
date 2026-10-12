package xuanmo.arcartxsuite.abyss.procgen;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.DoorBox;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.DoorEdge;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.Layout;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.PlacedRoom;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenRoomAsset.DoorSpec;
import xuanmo.arcartxsuite.abyss.template.ProcGenConfig;

/**
 * 布局物化器：房间铺放 + 门洞切割 + 走廊桥接 + 锚点换算。
 *
 * <p>门洞模型：房间是密封盒体，相邻房之间的连接 = 两面朝墙上各切一个
 * door-width×door-height 的洞 + 两墙外表面之间填一段走廊管
 * （floor/wall/ceiling 三面壳 + 内空）。所有房中心在格位轴线上对齐，
 * 走廊天然直通、不可能对不齐。</p>
 */
public final class ProcGenMaterializer {

    private final Logger logger;

    public ProcGenMaterializer(Logger logger) { this.logger = logger; }

    /** 物化结果：运行期房间 + 全局锚点。 */
    public static final class Result {
        public final List<RoomRuntime> rooms = new ArrayList<>();
        public Location spawn;                       // 起点房出生点
    }

    /** 运行期房间（物化后）。 */
    public static final class RoomRuntime {
        public PlacedRoom placed;
        public int minX, minY, minZ, maxX, maxY, maxZ;   // 世界包围盒（含墙）
        public final List<DoorBox> doorBoxes = new ArrayList<>();
        public Location spawnLoc;                       // 起点房玩家出生点
        public int waveCount;
        public boolean contains(Location l) {
            return l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ
                && l.getBlockY() >= minY && l.getBlockY() <= maxY;
        }
    }

    /**
     * 物化布局到世界。
     * @return null = 粘贴失败（房间集/几何问题）
     */
    public @Nullable Result materialize(World world, Layout layout,
                                        ProcGenConfig cfg) {
        Result out = new Result();
        // 1) 铺房
        for (PlacedRoom pr : layout.rooms) {
            if (pr.asset.region() == null) {
                logger.warning("[abyss] procgen 房间无几何: " + pr.asset.name());
                return null;
            }
            pr.asset.region().pasteRotated(world, pr.worldX, pr.worldY, pr.worldZ,
                true, pr.rotDeg);
        }
        // 2) 门洞 + 走廊
        for (DoorEdge e : layout.edges) {
            cutDoorPair(world, e, cfg);
        }
        // 3) 运行态房间（包围盒 + 门洞登记）
        for (PlacedRoom pr : layout.rooms) {
            RoomRuntime rr = new RoomRuntime();
            rr.placed = pr;
            rr.minX = pr.worldX; rr.minY = pr.worldY; rr.minZ = pr.worldZ;
            rr.maxX = pr.worldX + pr.w - 1;
            rr.maxY = pr.worldY + pr.h - 1;
            rr.maxZ = pr.worldZ + pr.d - 1;
            rr.doorBoxes.addAll(pr.doors);
            rr.waveCount = countWaves(pr.asset);
            if (pr.asset.spawn() != null) {
                int[] s = pr.toWorld(pr.asset.spawn()[0], pr.asset.spawn()[1],
                    pr.asset.spawn()[2]);
                rr.spawnLoc = new Location(world, s[0] + 0.5, s[1], s[2] + 0.5);
            } else {
                rr.spawnLoc = new Location(world,
                    pr.worldX + pr.w / 2.0 + 0.5, pr.worldY + 1,
                    pr.worldZ + pr.d / 2.0 + 0.5);
            }
            out.rooms.add(rr);
        }
        if (layout.start != null) {
            int idx = layout.rooms.indexOf(layout.start);
            out.spawn = out.rooms.get(idx).spawnLoc;
        }
        return out;
    }

    // ── 门洞/走廊 ────────────────────────────────────────

    /**
     * 在相邻两房的朝墙上切门洞 + 填走廊管。
     * axis 'x'：a 在 b 西侧——a 东墙 + b 西墙；'z' 同理南北。
     */
    private void cutDoorPair(World world, DoorEdge e, ProcGenConfig cfg) {
        PlacedRoom a = e.a, b = e.b;
        int dw = cfg.corridor().doorWidth();
        int dh = cfg.corridor().doorHeight();
        int floorY = Math.min(a.worldY, b.worldY);

        // 门洞沿墙中心：默认取格轴心（两房同轴对齐）；有 doors 定制时用 spec 的
        // 沿墙偏移——resolveDoor 返回的是世界绝对沿轴坐标
        if (e.axis == 'x') {
            int cx = cellAxisZ(a, b);
            int awx = a.worldX + a.w - 1;    // a 东墙面
            int bwx = b.worldX;            // b 西墙面
            int[] da = resolveDoor(a, 'e', cx, dw, dh);
            int[] db = resolveDoor(b, 'w', cx, dw, dh);
            cutHole(world, awx, floorY + 1, da[1], da[0], da[2], 'x');
            cutHole(world, bwx, floorY + 1, db[1], db[0], db[2], 'x');
            a.doors.add(doorBox(awx, floorY + 1, da[1], da[0], da[2], 'x'));
            b.doors.add(doorBox(bwx, floorY + 1, db[1], db[0], db[2], 'x'));
            // 走廊中心对齐 a 侧门心（默认门即格轴心 cx；定制门对齐自己一侧）
            corridorTube(world, awx + 1, bwx - 1, floorY, da[1], dw, dh, 'x', cfg);
        } else {
            int cz = cellAxisX(a, b);
            int anz = a.worldZ + a.d - 1;    // a 南墙面
            int bnz = b.worldZ;            // b 北墙面
            int[] da = resolveDoor(a, 's', cz, dw, dh);
            int[] db = resolveDoor(b, 'n', cz, dw, dh);
            cutHole(world, da[1], floorY + 1, anz, da[0], da[2], 'z');
            cutHole(world, db[1], floorY + 1, bnz, db[0], db[2], 'z');
            a.doors.add(doorBox(anz, floorY + 1, da[1], da[0], da[2], 'z'));
            b.doors.add(doorBox(bnz, floorY + 1, db[1], db[0], db[2], 'z'));
            corridorTube(world, anz + 1, bnz - 1, floorY, da[1], dw, dh, 'z', cfg);
        }
    }

    /** 世界坐标门洞盒（沿轴坐标 center，wallPlane 为垂直轴上的墙面）。 */
    private static DoorBox doorBox(int wallPlane, int yBase, int center,
                                   int w, int h, char axis) {
        int lo = center - w / 2, hi = center + (w - 1) / 2;
        return axis == 'x'
            ? new DoorBox(wallPlane, yBase, lo, wallPlane, yBase + h - 1, hi)
            : new DoorBox(lo, yBase, wallPlane, hi, yBase + h - 1, wallPlane);
    }

    /** 两房共享的轴线坐标（x 边共用 z 轴，z 边共用 x 轴）——房中心对齐的副产物。 */
    private static int cellAxisZ(PlacedRoom a, PlacedRoom b) {
        // 同一行格位 → 两房世界 z 中心相等
        return a.worldZ + a.d / 2;   // （两房居中同一格轴，取 a 即可）
    }

    private static int cellAxisX(PlacedRoom a, PlacedRoom b) {
        return a.worldX + a.w / 2;
    }

    /**
     * 解析某房某面墙（世界朝向 n/s/e/w）上的门洞参数。
     * @param axisCenter 该面墙所在轴的格中心世界坐标（默认门心）
     * @return {width, 门洞中心世界沿轴坐标, height}
     */
    private int[] resolveDoor(PlacedRoom pr, char worldWall, int axisCenter,
                              int dw, int dh) {
        for (DoorSpec spec : pr.asset.doors()) {
            // 定制门的局部墙名随旋转换算到世界朝向
            String worldDir = rotateWall(spec.wall(), pr.rotDeg);
            if (!worldDir.equals(String.valueOf(worldWall))) continue;
            // spec.center 是局部沿墙偏移（墙起点为原点）→ 旋转后世界绝对坐标
            int along = rotateDoorAlong(pr, spec, worldWall);
            int worldAlong = worldWall == 'n' || worldWall == 's'
                ? pr.worldX + along : pr.worldZ + along;
            return new int[]{spec.w(), worldAlong, spec.h()};
        }
        return new int[]{dw, axisCenter, dh};
    }

    /** 局部墙名经旋转映射到世界朝向（n/s/e/w）。 */
    private static String rotateWall(String localWall, int rot) {
        String w = localWall.toLowerCase();
        int r = ((rot % 360) + 360) % 360;
        if (r == 0) return w;
        String[] order = {"n", "e", "s", "w"};   // 顺时针
        int idx = switch (w) {
            case "n", "north" -> 0; case "e", "east" -> 1;
            case "s", "south" -> 2; case "w", "west" -> 3;
            default -> -1;
        };
        if (idx < 0) return w;
        int shift = r / 90;
        return order[(idx + shift) % 4];
    }

    /**
     * 定制门沿墙偏移旋转：局部点 (墙, center) → 旋转后世界沿墙偏移。
     * 简化为：把 (w-1 或 0, center) 局部点经 rotatePoint 换算后取新沿轴坐标。
     */
    private static int rotateDoorAlong(PlacedRoom pr, DoorSpec spec, char worldWall) {
        var reg = pr.asset.region();
        if (reg == null) return 0;
        // 局部坐标：门洞中心点在沿墙轴上的位置 = spec.center（以墙起点为原点）
        // 构造局部 2D 点再旋转：墙轴 n/s 用 x，e/w 用 z；中心值沿轴计量
        int lw = reg.width(), ld = reg.depth();
        int lx, lz;
        switch (spec.wall().toLowerCase()) {
            case "n", "north" -> { lx = spec.center(); lz = 0; }
            case "s", "south" -> { lx = spec.center(); lz = ld - 1; }
            case "e", "east" -> { lx = lw - 1; lz = spec.center(); }
            default -> { lx = 0; lz = spec.center(); }
        }
        int[] p = reg.rotatePoint(lx, 0, lz, pr.rotDeg);
        // 旋转后该点所在世界墙的沿轴坐标（相对旋转后 footprint 原点）
        return switch (worldWall) {
            case 'n', 's' -> p[0];
            default -> p[2];
        };
    }

    /** 在 wall 平面切出门洞（宽×高，沿 axis 方向取截面）。 */
    private void cutHole(World world, int wallPlane, int yBase, int center,
                         int w, int h, char axis) {
        int hw = w / 2;
        for (int y = yBase; y < yBase + h; y++) {
            for (int o = -hw; o <= hw + (w % 2 == 0 ? -1 : 0); o++) {
                if (axis == 'x') {
                    world.getBlockAt(wallPlane, y, center + o).setType(Material.AIR, false);
                } else {
                    world.getBlockAt(center + o, y, wallPlane).setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * 走廊管：两墙外表面之间 (from..to) 的通道——地板/侧墙/天花三壳 + 内空。
     * axis 'x'：通道沿 x 走向，截面在 z；'z' 反之。
     */
    private void corridorTube(World world, int from, int to, int floorY,
                              int center, int dw, int dh, char axis,
                              ProcGenConfig cfg) {
        if (to < from) return;
        int hw = dw / 2;
        Material fl = cfg.corridor().floor();
        Material wl = cfg.corridor().wall();
        Material ce = cfg.corridor().ceiling();
        for (int t = from; t <= to; t++) {
            for (int o = -hw - 1; o <= hw + 1; o++) {
                for (int y = floorY; y <= floorY + dh + 1; y++) {
                    Material m;
                    if (y == floorY) m = fl;
                    else if (y == floorY + dh + 1) m = ce;
                    else if (o == -hw - 1 || o == hw + 1) m = wl;
                    else m = Material.AIR;
                    int bx = axis == 'x' ? t : center + o;
                    int bz = axis == 'x' ? center + o : t;
                    world.getBlockAt(bx, y, bz).setType(m, false);
                }
            }
        }
    }

    private static int countWaves(ProcGenRoomAsset asset) {
        int n = 0;
        for (List<ProcGenRoomAsset.RoomMob> w : asset.waves()) {
            if (w != null && !w.isEmpty()) n++;
        }
        return n;
    }
}
