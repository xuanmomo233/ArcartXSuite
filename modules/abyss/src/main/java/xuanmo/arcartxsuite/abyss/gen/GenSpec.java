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
 *     tile-size: 40           # 图块边长（含边界墙线；内空 = tile-size-2）
 *     grid: 5                 # 网格阶（grid×grid 格位供多边形生长）
 *     door-half: 2            # 门洞半宽（开口宽 = 2*door-half+1，高 4，深 2）
 *     double-door: 0.25       # 共享墙切第二道门的概率
 *     floor-y: 60             # 地板层 y（内空自此向上）
 *     base-height: 14         # 普通房内空高
 *     tall-height: 22         # 挑高房（tall:true 的房）内空高
 *     palette: moss           # 默认调色板（可被 floors[].palette 覆盖）
 *     seed: 0                 # 0=每实例随机种子；&gt;0=固定种子（调试用）
 * </pre>
 */
public record GenSpec(
    int tileSize,
    int grid,
    int doorHalf,
    double doubleDoor,
    int floorY,
    int baseHeight,
    int tallHeight,
    String palette,
    long seed
) {

    public static final GenSpec DEFAULT = new GenSpec(
        40, 5, 2, 0.25, 60, 14, 22, "moss", 0L);

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

    public static GenSpec load(@Nullable ConfigurationSection s, GenSpec base) {
        if (s == null) return base;
        return new GenSpec(
            s.getInt("tile-size", s.getInt("tile_size", base.tileSize)),
            s.getInt("grid", base.grid),
            s.getInt("door-half", s.getInt("door_half", base.doorHalf)),
            s.getDouble("double-door", s.getDouble("double_door", base.doubleDoor)),
            s.getInt("floor-y", s.getInt("floor_y", base.floorY)),
            s.getInt("base-height", s.getInt("base_height", base.baseHeight)),
            s.getInt("tall-height", s.getInt("tall_height", base.tallHeight)),
            s.getString("palette", base.palette),
            s.getLong("seed", base.seed)
        );
    }
}
