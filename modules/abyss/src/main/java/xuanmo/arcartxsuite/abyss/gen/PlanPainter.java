package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import xuanmo.arcartxsuite.abyss.template.FloorConfig;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.Floor;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomTemplate;
import xuanmo.arcartxsuite.abyss.template.RegionTemplate;

/**
 * 计划绘制器：把 {@link GeneratedLayout} 写进实例世界。
 * <p>
 * void 世界前提：不写空气——只落地板/壳体墙/屏障盖/内饰实体格。
 * 图块模式：房间是方形大房（内空宽阔），内饰按 feature 在
 * 确定性布局变体中轮换；{@code gen.prefab} 指定 {@code regions/<名>.yml}
 * 时直接铺放手建预制件（跳过 AIR 叠加内饰），覆盖程序化变体。
 */
public final class PlanPainter {

    private final java.util.logging.Logger logger;

    public PlanPainter(java.util.logging.Logger logger) {
        this.logger = logger;
    }

    public void paint(World world, GeneratedLayout layout, FloorConfig fc,
                      Function<String, RegionTemplate> prefabs) {
        long t0 = System.currentTimeMillis();
        int writes = 0;
        List<Map.Entry<Integer, FloorPlan>> floors = new ArrayList<>(layout.floorMap().entrySet());
        floors.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, FloorPlan> e : floors) {
            FloorPlan fp = e.getValue();
            Floor floor = fc.floors().get(fp.floorIndex);
            GenSpec spec = floor.gen() != null ? floor.gen() : fc.defaults().gen();
            GenSpec.Palette pal = GenSpec.paletteOf(
                floor.palette() != null ? floor.palette() : spec.palette());
            // 1. 房壳（内空地板 + 边界墙 + 屏障盖）
            for (RoomPlan r : fp.rooms.values()) writes += paintRoom(world, r, pal);
            // 2. 封门（Boss 房开口填壳体）
            for (RoomPlan r : fp.rooms.values()) {
                for (RoomPlan.DoorPlan d : r.doors) {
                    if (!d.sealed) continue;
                    for (RoomPlan.Pos p : d.cells) {
                        set(world, p.x(), p.y(), p.z(), pal.shell()); writes++;
                    }
                }
            }
            // 3. 内饰/预制件/锚点实体块
            for (RoomPlan r : fp.rooms.values()) {
                RoomTemplate tpl = fc.rooms().get(r.ref);
                writes += furnish(world, fp, r, tpl, pal, prefabs);
            }
        }
        logger.info("[abyss] 生成器绘制完成：" + writes + " 块，用时 "
            + (System.currentTimeMillis() - t0) + "ms");
    }

    // ═══ 房壳 ═══

    private int paintRoom(World w, RoomPlan r, GenSpec.Palette pal) {
        int n = 0;
        // 内空列：地板 + 屏障盖（空气已是 void 默认，不写）
        for (long pk : r.floor) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            set(w, x, r.floorY, z, pal.floor()); n++;
            set(w, x, r.ceilY, z, Material.BARRIER); n++;
        }
        // 壳体列：墙柱地板层到盖层下 + 顶盖
        for (long pk : r.wall) {
            int x = (int) (pk >> 32), z = (int) (long) pk;
            for (int y = r.floorY; y < r.ceilY; y++) {
                set(w, x, y, z, pal.shell()); n++;
            }
            set(w, x, r.ceilY, z, Material.BARRIER); n++;
        }
        return n;
    }

    // ═══ 内饰 ═══

    private int furnish(World w, FloorPlan fp, RoomPlan r, RoomTemplate tpl,
                        GenSpec.Palette pal, Function<String, RegionTemplate> prefabs) {
        String feature = tpl != null && tpl.gen() != null && tpl.gen().feature() != null
            ? tpl.gen().feature() : "plain";
        // prefab 预制件优先：手建内饰覆盖程序化变体
        String pf = tpl != null && tpl.gen() != null ? tpl.gen().prefab() : null;
        int n = 0;
        if (pf != null) {
            RegionTemplate rt = prefabs != null ? prefabs.apply(pf) : null;
            int interior = r.maxX - r.minX - 1;   // 内空边长（38）
            if (rt == null) {
                logger.warning("[abyss] gen 房 " + r.ref + " prefab " + pf + " 未找到——退回程序化内饰");
            } else if (rt.width() > interior || rt.depth() > interior
                || rt.height() > r.ceilY - r.floorY) {
                logger.warning("[abyss] gen 房 " + r.ref + " prefab " + pf + " 尺寸 "
                    + rt.width() + "x" + rt.depth() + "x" + rt.height()
                    + " 超出内空 " + interior + "——退回程序化内饰");
            } else {
                // 居中铺放（跳过 AIR 保留壳体）
                int px = r.minX + 1 + (interior - rt.width()) / 2;
                int pz = r.minZ + 1 + (interior - rt.depth()) / 2;
                n += rt.paste(w, px, r.floorY, pz, false);
            }
        }
        Random rng = new Random(r.ref.hashCode() * 31L + fp.floorIndex);
        int fy = r.floorY;
        int cx = (r.minX + r.maxX) / 2, cz = (r.minZ + r.maxZ) / 2;

        if (pf == null) {
            switch (feature) {
                case "entry" -> {
                    n += carpetCross(w, r, cx, cz, fy);
                    n += cornerPlanters(w, r, fy, pal);
                }
                case "combat" -> n += combatVariant(w, r, rng, pal, fy);
                case "elite" -> {
                    n += combatVariant(w, r, rng, pal, fy);
                    for (int i = 0; i < 4; i++) {
                        int[] cell = pick(r, rng);
                        if (cell == null) break;
                        set(w, cell[0], r.ceilY - 1, cell[1], Material.CHAIN);
                        set(w, cell[0], r.ceilY - 2, cell[1], Material.CHAIN);
                        n += 2;
                    }
                }
                case "altar" -> {
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++)
                            if (Math.abs(dx) + Math.abs(dz) <= 3) {
                                set(w, cx + dx, fy, cz + dz, Material.LAPIS_BLOCK); n++;
                            }
                    for (int[] dd : new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
                        set(w, cx + dd[0], fy, cz + dd[1], Material.OBSIDIAN); n++;
                    }
                    set(w, cx, fy + 1, cz, Material.ENCHANTING_TABLE); n++;
                    for (int[] dd : new int[][]{{2,2},{-2,2},{2,-2},{-2,-2}}) {
                        set(w, cx + dd[0], fy + 1, cz + dd[1], Material.END_ROD); n++;
                    }
                }
                case "chest" -> {
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            set(w, cx + dx, fy, cz + dz, Material.GOLD_BLOCK); n++;
                        }
                    set(w, cx, fy + 1, cz, Material.CHEST); n++;
                    for (int[] dd : new int[][]{{3,0},{-3,0},{0,3},{0,-3}}) {
                        set(w, cx + dd[0], fy + 1, cz + dd[1], Material.EMERALD_BLOCK); n++;
                    }
                }
                case "shop" -> {
                    Location ct = r.anchor("counter");
                    if (ct != null) {
                        int bx = ct.getBlockX(), bz = ct.getBlockZ();
                        for (int dx = 0; dx <= 6; dx++) {
                            set(w, bx + dx, fy + 1, bz, Material.SPRUCE_PLANKS); n++;
                            if (dx % 2 == 0) {
                                set(w, bx + dx, fy + 1, bz - 1, Material.GOLD_BLOCK); n++;
                            }
                        }
                        set(w, bx - 1, fy + 1, bz, Material.BARREL); n++;
                        set(w, bx + 7, fy + 1, bz, Material.BARREL); n++;
                    }
                    n += carpetCross(w, r, cx, cz, fy);
                }
                case "puzzle" -> {
                    // 拉杆挂北墙：lever_N 锚点指向内空北排，墙在北侧——面朝南
                    Material[] glasses = {Material.RED_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
                        Material.CYAN_STAINED_GLASS, Material.LIME_STAINED_GLASS};
                    for (int i = 1; i <= 4; i++) {
                        Location lv = r.anchor("lever_" + i);
                        if (lv == null) continue;
                        int lx = lv.getBlockX(), ly = lv.getBlockY(), lz = lv.getBlockZ();
                        set(w, lx, fy + 1, lz, Material.REDSTONE_WIRE); n++;
                        var lv2 = w.getBlockAt(lx, ly, lz);
                        lv2.setType(Material.LEVER, false);
                        var data = lv2.getBlockData();
                        if (data instanceof Switch sw) {
                            sw.setFace(Switch.Face.WALL);
                            sw.setFacing(BlockFace.SOUTH);
                            lv2.setBlockData(sw, false);
                        }
                        n++;
                        set(w, lx, ly + 2, lz - 1, glasses[i - 1]); n++;
                    }
                }
                case "boss" -> {
                    Location ex = r.anchor("exit");
                    if (ex != null) {
                        int exX = ex.getBlockX(), exZ = ex.getBlockZ();
                        for (int dx = -1; dx <= 2; dx++)
                            for (int dz = -1; dz <= 2; dz++) {
                                set(w, exX + dx, fy, exZ + dz, Material.GOLD_BLOCK); n++;
                            }
                    }
                    // 环形柱阵（挑高房更气派；门口留道）
                    for (int i = 0; i < 6; i++) {
                        int[] cell = pick(r, rng);
                        if (cell == null || nearDoor(r, cell[0], cell[1])) continue;
                        int hgt = Math.min(6, r.ceilY - fy - 3);
                        for (int dy = 1; dy <= hgt; dy++) {
                            set(w, cell[0], fy + dy, cell[1], pal.pillar()); n++;
                        }
                    }
                }
                default -> { }
            }
        }
        // feature 家具之外：prefab 房也补挂通用锚点装饰？——不做，prefab 为全权
        return n;
    }

    /** (x,z) 是否贴近任一门洞（门内侧留道，装饰不得堵门）。 */
    private static boolean nearDoor(RoomPlan r, int x, int z) {
        for (RoomPlan.DoorPlan d : r.doors) {
            for (RoomPlan.Pos p : d.cells) {
                if (Math.abs(p.x() - x) + Math.abs(p.z() - z) <= 4) return true;
            }
        }
        return false;
    }

    /** 战斗房内饰变体：柱阵 / 掩体矮墙 / 中央台座 / 散石阵（门口留道）。 */
    private int combatVariant(World w, RoomPlan r, Random rng,
                              GenSpec.Palette pal, int fy) {
        int n = 0;
        int cx = (r.minX + r.maxX) / 2, cz = (r.minZ + r.maxZ) / 2;
        switch (rng.nextInt(4)) {
            case 0 -> {  // 四柱阵：±8 位 3 高柱
                for (int[] o : new int[][]{{8,8},{8,-8},{-8,8},{-8,-8}}) {
                    if (nearDoor(r, cx + o[0], cz + o[1])) continue;
                    for (int dy = 1; dy <= 3; dy++) {
                        set(w, cx + o[0], fy + dy, cz + o[1], pal.pillar()); n++;
                    }
                }
            }
            case 1 -> {  // 十字掩体：中心向外四段 1 高矮墙
                for (int[] dir : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    for (int i = 4; i <= 8; i++) {
                        if (nearDoor(r, cx + dir[0] * i, cz + dir[1] * i)) continue;
                        set(w, cx + dir[0] * i, fy + 1, cz + dir[1] * i, pal.sub()); n++;
                    }
                }
            }
            case 2 -> {  // 中央台座：7×7 垫高 1 层 + 四角柱
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        set(w, cx + dx, fy, cz + dz, pal.sub()); n++;
                    }
                for (int[] o : new int[][]{{10,10},{10,-10},{-10,10},{-10,-10}}) {
                    if (nearDoor(r, cx + o[0], cz + o[1])) continue;
                    for (int dy = 1; dy <= 3; dy++) {
                        set(w, cx + o[0], fy + dy, cz + o[1], pal.pillar()); n++;
                    }
                }
            }
            default -> { // 散石阵：8 块 1-2 高散石
                for (int i = 0; i < 8; i++) {
                    int[] cell = pick(r, rng);
                    if (cell == null || nearDoor(r, cell[0], cell[1])) continue;
                    int h = 1 + rng.nextInt(2);
                    for (int dy = 1; dy <= h; dy++) {
                        set(w, cell[0], fy + dy, cell[1], pal.sub()); n++;
                    }
                }
            }
        }
        return n;
    }

    /** 地毯十字：房内沿中心两轴铺毯。 */
    private int carpetCross(World w, RoomPlan r, int cx, int cz, int fy) {
        int n = 0;
        for (int dx = -7; dx <= 7; dx++) {
            if (r.floor.contains(RoomPlan.pack(cx + dx, cz))) {
                set(w, cx + dx, fy + 1, cz, Material.RED_CARPET); n++;
            }
        }
        for (int dz = -7; dz <= 7; dz++) {
            if (r.floor.contains(RoomPlan.pack(cx, cz + dz))) {
                set(w, cx, fy + 1, cz + dz, Material.RED_CARPET); n++;
            }
        }
        return n;
    }

    /** 四角花坛：每个内角 2×2 苔石座 + 树叶。 */
    private int cornerPlanters(World w, RoomPlan r, int fy, GenSpec.Palette pal) {
        int n = 0;
        int x0 = r.minX + 2, x1 = r.maxX - 2, z0 = r.minZ + 2, z1 = r.maxZ - 2;
        for (int[] c : new int[][]{{x0,z0},{x0,z1},{x1,z0},{x1,z1}}) {
            if (nearDoor(r, c[0], c[1]) || nearDoor(r, c[0] + 1, c[1] + 1)) continue;
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) {
                    set(w, c[0] + dx, fy + 1, c[1] + dz, pal.sub()); n++;
                }
            set(w, c[0], fy + 2, c[1], Material.AZALEA); n++;
        }
        return n;
    }

    private static int[] pick(RoomPlan r, Random rng) {
        if (r.floor.isEmpty()) return null;
        List<Long> l = new ArrayList<>(r.floor);
        long pk = l.get(rng.nextInt(l.size()));
        return new int[]{(int) (pk >> 32), (int) pk};
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
