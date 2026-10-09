package xuanmo.arcartxsuite.abyss.gen;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * 程序化生成参数（floors.yml {@code defaults.gen} 全局默认 + {@code floors[].gen} 单层覆盖）。
 *
 * <pre>
 * defaults:
 *   gen:
 *     cell-pitch: 64          # 布点单元间距（有机房之间最小间隔）
 *     room-radius: [12, 17]   # 洞穴半径范围（内空直径≈2r，bbox 含墙再 +3）
 *     corridor-width: 5       # 走廊内空宽（奇数居中）
 *     extra-edges: 0.35       # 最小生成树之外的环边概率（0=纯树，越高环路越多）
 *     ca-fill: 0.46           # 元胞自动机初始填充率
 *     ca-smooth: 4            # 平滑迭代轮数
 *     floor-y: 60             # 地板层 y（内空自此向上）
 *     base-height: 14         # 普通房内空高
 *     tall-height: 22         # 挑高房（tall:true 的房）内空高
 *     palette: moss           # 默认调色板（可被 floors[].palette 覆盖）
 *     seed: 0                 # 0=每实例随机种子；>0=固定种子（调试用）
 * </pre>
 */
public record GenSpec(
    int cellPitch,
    int radiusMin,
    int radiusMax,
    int corridorWidth,
    double extraEdges,
    double caFill,
    int caSmooth,
    int floorY,
    int baseHeight,
    int tallHeight,
    String palette,
    long seed
) {

    public static final GenSpec DEFAULT = new GenSpec(
        64, 12, 17, 5, 0.35, 0.46, 4, 60, 14, 22, "moss", 0L);

    /** 调色板：墙体/地板/次地板/灯/柱块/廊墙。 */
    public record Palette(Material shell, Material floor, Material sub,
                          Material light, Material pillar, Material corrWall) { }

    public static Palette paletteOf(@Nullable String name) {
        String n = name == null ? "moss" : name.toLowerCase();
        return switch (n) {
            case "deepslate" -> new Palette(Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE,
                Material.CRACKED_DEEPSLATE_BRICKS, Material.SHROOMLIGHT,
                Material.BLACKSTONE, Material.DEEPSLATE_BRICKS);
            case "nether" -> new Palette(Material.NETHER_BRICKS, Material.BLACKSTONE,
                Material.CRYING_OBSIDIAN, Material.PEARLESCENT_FROGLIGHT,
                Material.OBSIDIAN, Material.NETHER_BRICKS);
            case "sandstone" -> new Palette(Material.SANDSTONE, Material.SMOOTH_SANDSTONE,
                Material.CUT_SANDSTONE, Material.SEA_LANTERN,
                Material.CHISELED_SANDSTONE, Material.SANDSTONE);
            case "ice" -> new Palette(Material.PACKED_ICE, Material.BLUE_ICE,
                Material.SNOW_BLOCK, Material.SEA_LANTERN,
                Material.PACKED_ICE, Material.PACKED_ICE);
            default -> new Palette(Material.MOSSY_STONE_BRICKS, Material.POLISHED_ANDESITE,
                Material.MOSSY_COBBLESTONE, Material.GLOWSTONE,
                Material.CHISELED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS);
        };
    }

    private static int[] intRange(ConfigurationSection s, String key, int lo, int hi) {
        var list = s.getIntegerList(key);
        if (list.size() >= 2) return new int[]{list.get(0), list.get(1)};
        if (list.size() == 1) return new int[]{list.get(0), list.get(0)};
        String str = s.getString(key, null);
        if (str != null && str.contains("..")) {
            String[] v = str.split("\\.\\.");
            try {
                return new int[]{Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim())};
            } catch (NumberFormatException ignored) { }
        }
        return new int[]{lo, hi};
    }

    public static GenSpec load(@Nullable ConfigurationSection s, GenSpec base) {
        if (s == null) return base;
        int[] r = intRange(s, "room-radius", base.radiusMin, base.radiusMax);
        if (r[1] < r[0]) { int t = r[0]; r[0] = r[1]; r[1] = t; }
        return new GenSpec(
            s.getInt("cell-pitch", s.getInt("cell_pitch", base.cellPitch)),
            r[0], r[1],
            s.getInt("corridor-width", s.getInt("corridor_width", base.corridorWidth)),
            s.getDouble("extra-edges", s.getDouble("extra_edges", base.extraEdges)),
            s.getDouble("ca-fill", s.getDouble("ca_fill", base.caFill)),
            s.getInt("ca-smooth", s.getInt("ca_smooth", base.caSmooth)),
            s.getInt("floor-y", s.getInt("floor_y", base.floorY)),
            s.getInt("base-height", s.getInt("base_height", base.baseHeight)),
            s.getInt("tall-height", s.getInt("tall_height", base.tallHeight)),
            s.getString("palette", base.palette),
            s.getLong("seed", base.seed)
        );
    }
}
