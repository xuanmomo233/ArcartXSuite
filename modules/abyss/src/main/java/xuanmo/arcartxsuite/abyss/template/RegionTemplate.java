package xuanmo.arcartxsuite.abyss.template;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * 区域模板：服主在某世界圈选的方块快照（调色板 + 逐层短索引，base64 存 YAML）。
 * <p>
 * 管理路径：{@code /axs abyss region pos1|pos2} 标角 →
 * {@code /axs abyss region save <地牢> <名>} 存到
 * {@code abysses/<地牢>/regions/<名>.yml}。
 * 使用路径：world-init 等脚本段内 {@code paste_region <名> <x,y,z> [full]}。
 * <p>
 * 调色板存 BlockData.getAsString()——保留楼梯/栅栏/玻璃的朝向与状态；
 * 不含方块实体内容（箱子物品、刷怪笼配置等纯 NBT 数据不迁移）。
 */
public final class RegionTemplate {

    /** 选区体积上限（防误圈整片地形写爆磁盘）。 */
    public static final int MAX_VOLUME = 131072;
    private static final String[] AIR_DATA = {
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air"};

    private final int width;
    private final int height;
    private final int depth;
    private final List<String> palette;
    /** layers[y]：w×d×2 字节，短索引小端序，x 最快（idx = (z*w + x)*2）。 */
    private final byte[][] layers;

    private RegionTemplate(int width, int height, int depth,
                           List<String> palette, byte[][] layers) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.palette = palette;
        this.layers = layers;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }
    public int paletteSize() { return palette.size(); }
    public int volume() { return width * height * depth; }

    // ═══ 采集 ═══

    /**
     * 快照世界内矩形区域（两角自动归一；须主线程调用）。
     * @throws IllegalArgumentException 体积超上限
     */
    public static RegionTemplate capture(World world,
                                         int x1, int y1, int z1,
                                         int x2, int y2, int z2) {
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int w = Math.abs(x2 - x1) + 1, h = Math.abs(y2 - y1) + 1, d = Math.abs(z2 - z1) + 1;
        if ((long) w * h * d > MAX_VOLUME) {
            throw new IllegalArgumentException("选区体积 " + ((long) w * h * d)
                + " 超上限 " + MAX_VOLUME);
        }
        List<String> palette = new ArrayList<>();
        Map<String, Integer> pidx = new HashMap<>();
        byte[][] layers = new byte[h][w * d * 2];
        for (int y = 0; y < h; y++) {
            byte[] layer = layers[y];
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    String data = world.getBlockAt(minX + x, minY + y, minZ + z)
                        .getBlockData().getAsString();
                    Integer idx = pidx.get(data);
                    if (idx == null) {
                        if (palette.size() >= 65536) {
                            throw new IllegalArgumentException(
                                "选区方块种类超上限（65536）");
                        }
                        idx = palette.size();
                        palette.add(data);
                        pidx.put(data, idx);
                    }
                    int off = (z * w + x) * 2;
                    layer[off] = (byte) (idx & 0xFF);
                    layer[off + 1] = (byte) ((idx >> 8) & 0xFF);
                }
            }
        }
        return new RegionTemplate(w, h, d, palette, layers);
    }

    // ═══ 铺放 ═══

    /**
     * 在目标世界以 (ox,oy,oz) 为西北底角铺放本模板。
     * @param withAir true=连空气一起覆盖（挖空腔体）；false=跳过 AIR（结构嵌入地形）
     * @return 实际放置的方块数
     */
    public int paste(World world, int ox, int oy, int oz, boolean withAir) {
        BlockData[] resolved = new BlockData[palette.size()];
        boolean[] isAir = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            isAir[i] = isAirData(palette.get(i));
            resolved[i] = resolve(palette.get(i));
        }
        int placed = 0;
        for (int y = 0; y < height; y++) {
            byte[] layer = layers[y];
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int off = (z * width + x) * 2;
                    int idx = (layer[off] & 0xFF) | ((layer[off + 1] & 0xFF) << 8);
                    if (idx >= palette.size() || resolved[idx] == null) continue;
                    if (!withAir && isAir[idx]) continue;
                    Block b = world.getBlockAt(ox + x, oy + y, oz + z);
                    b.setBlockData(resolved[idx], false);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static boolean isAirData(String s) {
        for (String a : AIR_DATA) {
            if (s.equals(a)) return true;
        }
        return false;
    }

    /** BlockData 串解析：全串失败时退回材质名部分（跨版本字段不兼容兜底）。 */
    private static @Nullable BlockData resolve(String data) {
        try {
            return Bukkit.createBlockData(data);
        } catch (IllegalArgumentException ignored) { }
        int br = data.indexOf('[');
        String name = br > 0 ? data.substring(0, br) : data;
        Material mat = Material.matchMaterial(name);
        return mat != null && mat.isBlock() ? mat.createBlockData() : null;
    }

    // ═══ 持久化 ═══

    /** 写到 YAML 文件（父目录自动建）。 */
    public void save(File file) throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("size", width + "," + height + "," + depth);
        yml.set("palette", palette);
        List<String> enc = new ArrayList<>(layers.length);
        for (byte[] layer : layers) {
            enc.add(Base64.getEncoder().encodeToString(layer));
        }
        yml.set("layers", enc);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        yml.save(file);
    }

    /** 从 YAML 读入；文件缺失/损坏返回 null。 */
    public static @Nullable RegionTemplate load(File file) {
        if (!file.isFile()) return null;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String[] dim = String.valueOf(yml.getString("size", "")).split(",");
        if (dim.length != 3) return null;
        int w, h, d;
        try {
            w = Integer.parseInt(dim[0].trim());
            h = Integer.parseInt(dim[1].trim());
            d = Integer.parseInt(dim[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (w <= 0 || h <= 0 || d <= 0 || (long) w * h * d > MAX_VOLUME) return null;
        List<String> palette = yml.getStringList("palette");
        List<String> enc = yml.getStringList("layers");
        if (enc.size() != h) return null;
        byte[][] layers = new byte[h][];
        for (int i = 0; i < h; i++) {
            byte[] layer = Base64.getDecoder().decode(
                enc.get(i).getBytes(StandardCharsets.ISO_8859_1));
            if (layer.length != w * d * 2) return null;
            layers[i] = layer;
        }
        return new RegionTemplate(w, h, d, palette, layers);
    }
}
