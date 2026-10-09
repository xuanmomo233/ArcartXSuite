package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import xuanmo.arcartxsuite.abyss.template.FloorConfig;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.Floor;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomTemplate;

/**
 * 计划绘制器：把 {@link GeneratedLayout} 写进实例世界。
 * <p>
 * void 世界前提：不写空气——只落地板/壳体墙/屏障盖/内饰实体格，
 * 工作量与现行 world-init 同级（~10⁵ 块，先经区块预载）。
 * 写入顺序：房壳→走廊→门洞→封门→内饰。
 */
public final class PlanPainter {

    private final java.util.logging.Logger logger;

    public PlanPainter(java.util.logging.Logger logger) {
        this.logger = logger;
    }

    public void paint(World world, GeneratedLayout layout, FloorConfig fc) {
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
            // 1. 洞穴本体（大 CA 洞窟：地板+壳环+屏障盖）——先铺，洞室门框后盖
            if (fp.cave != null) writes += paintRoom(world, fp.cave, pal);
            // 2. 洞室（外凸团块：地板+壳环+屏障盖；门框格也在 wall 集里一并落）
            for (RoomPlan r : fp.rooms.values()) writes += paintRoom(world, r, pal);
            // 3. 封门（Boss 房门再填回壳体材质）
            for (RoomPlan r : fp.rooms.values()) {
                for (RoomPlan.DoorPlan d : r.doors) {
                    if (!d.sealed) continue;
                    for (RoomPlan.Pos p : d.cells) {
                        set(world, p.x(), p.y(), p.z(), pal.shell()); writes++;
                    }
                }
            }
            // 4. 内饰/家具/锚点实体块
            for (RoomPlan r : fp.rooms.values()) {
                RoomTemplate tpl = fc.rooms().get(r.ref);
                writes += furnish(world, fp, r, tpl, pal);
            }
        }
        logger.info("[abyss] 生成器绘制完成：" + writes + " 块，用时 "
            + (System.currentTimeMillis() - t0) + "ms");
    }

    // ═══ 房壳/洞壳 ═══

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
                        GenSpec.Palette pal) {
        String feature = tpl != null && tpl.gen() != null && tpl.gen().feature() != null
            ? tpl.gen().feature() : "plain";
        int n = 0;
        Random rng = new Random(r.ref.hashCode() * 31L + fp.floorIndex);
        int fy = r.floorY;
        Location c = r.center(w);
        int cx = c.getBlockX(), cz = c.getBlockZ();

        switch (feature) {
            case "entry" -> {
                n += carpetCross(w, r, cx, cz, fy);
                n += scatterBlocks(w, r, rng, 4, 3, pal.sub(), fy, 1);   // 碎柱点缀
            }
            case "combat", "elite" -> {
                n += scatterBlocks(w, r, rng, 5, 5, pal.sub(), fy, 3);   // 掩体柱
                if ("elite".equals(feature)) {
                    for (int i = 0; i < 4; i++) {
                        int[] cell = pick(r, rng);
                        if (cell == null) break;
                        set(w, cell[0], r.ceilY - 1, cell[1], Material.CHAIN);
                        set(w, cell[0], r.ceilY - 2, cell[1], Material.CHAIN);
                        n += 2;
                    }
                }
            }
            case "altar" -> {
                Location a = r.anchor("altar");
                if (a != null) {
                    int ax = a.getBlockX(), az = a.getBlockZ();
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++)
                            if (Math.abs(dx) + Math.abs(dz) <= 3) {
                                set(w, ax + dx, fy, az + dz, Material.LAPIS_BLOCK); n++;
                            }
                    for (int[] dd : new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
                        set(w, ax + dd[0], fy, az + dd[1], Material.OBSIDIAN); n++;
                    }
                    set(w, ax, fy + 1, az, Material.ENCHANTING_TABLE); n++;
                    for (int[] dd : new int[][]{{2,2},{-2,2},{2,-2},{-2,-2}}) {
                        set(w, ax + dd[0], fy + 1, az + dd[1], Material.END_ROD); n++;
                    }
                }
            }
            case "chest" -> {
                Location ch = r.anchor("chest");
                if (ch != null) {
                    int bx = ch.getBlockX(), bz = ch.getBlockZ();
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            set(w, bx + dx, fy, bz + dz, Material.GOLD_BLOCK); n++;
                        }
                    set(w, bx, fy + 1, bz, Material.CHEST); n++;
                    n += scatterBlocks(w, r, rng, 3, 4, Material.EMERALD_BLOCK, fy, 0);
                }
            }
            case "shop" -> {
                Location ct = r.anchor("counter");
                if (ct != null) {
                    int bx = ct.getBlockX(), bz = ct.getBlockZ();
                    for (int dx = 0; dx <= 4; dx++) {
                        set(w, bx + dx, fy + 1, bz, Material.SPRUCE_PLANKS); n++;
                        set(w, bx + dx, fy + 1, bz - 1,
                            dx % 2 == 0 ? Material.GOLD_BLOCK : Material.REDSTONE_BLOCK); n++;
                    }
                    set(w, bx - 1, fy + 1, bz, Material.BARREL); n++;
                    set(w, bx + 5, fy + 1, bz, Material.BARREL); n++;
                }
            }
            case "puzzle" -> {
                // 拉杆挂内墙：lever_N 锚点指向洞内格，墙在其北侧 —— 面朝南（朝房内）
                Material[] glasses = {Material.RED_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
                    Material.CYAN_STAINED_GLASS, Material.LIME_STAINED_GLASS};
                for (int i = 1; i <= 4; i++) {
                    Location lv = r.anchor("lever_" + i);
                    if (lv == null) continue;
                    int lx = lv.getBlockX(), ly = lv.getBlockY(), lz = lv.getBlockZ();
                    set(w, lx, fy, lz, Material.REDSTONE_WIRE); n++;
                    var lv2 = w.getBlockAt(lx, ly, lz);
                    lv2.setType(Material.LEVER, false);
                    var data = lv2.getBlockData();
                    if (data instanceof Switch sw) {
                        sw.setFace(Switch.Face.WALL);
                        sw.setFacing(BlockFace.SOUTH);   // 贴北墙、柄朝南
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
                n += scatterBlocks(w, r, rng, 4, 6, pal.pillar(), fy, 5);
            }
            default -> { }
        }
        return n;
    }

    /** 地毯十字：房内沿中心两轴铺毯。 */
    private int carpetCross(World w, RoomPlan r, int cx, int cz, int fy) {
        int n = 0;
        for (int dx = -5; dx <= 5; dx++) {
            if (r.floor.contains(RoomPlan.pack(cx + dx, cz))) {
                set(w, cx + dx, fy + 1, cz, Material.RED_CARPET); n++;
            }
        }
        for (int dz = -5; dz <= 5; dz++) {
            if (r.floor.contains(RoomPlan.pack(cx, cz + dz))) {
                set(w, cx, fy + 1, cz + dz, Material.RED_CARPET); n++;
            }
        }
        return n;
    }

    /** 内景散点：n 个洞内格放 h 高柱块（h=0 单层）。 */
    private int scatterBlocks(World w, RoomPlan r, Random rng, int n, int minGap,
                              Material mat, int fy, int h) {
        int writes = 0, tries = 0;
        List<int[]> placed = new ArrayList<>();
        List<Long> cells = new ArrayList<>(r.floor);
        while (tries++ < n * 60 && placed.size() < n) {
            long pk = cells.get(rng.nextInt(cells.size()));
            int x = (int) (pk >> 32), z = (int) (long) pk;
            boolean ok = true;
            for (int[] p : placed) {
                if (Math.abs(p[0] - x) + Math.abs(p[1] - z) < minGap) { ok = false; break; }
            }
            if (!ok) continue;
            placed.add(new int[]{x, z});
            for (int dy = 1; dy <= h + 1; dy++) {
                set(w, x, fy + dy, z, mat); writes++;
            }
        }
        return writes;
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
