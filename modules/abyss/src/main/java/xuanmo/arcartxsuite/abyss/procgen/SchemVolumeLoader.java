package xuanmo.arcartxsuite.abyss.procgen;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.RegionTemplate;

/**
 * .schem → {@link RegionTemplate} 转换器。
 *
 * <p>加载时经 WorldEdit/FAWE 剪贴板 API 读取方块，转成区域模板内部表示，
 * 之后与 region-yml 走同一条铺放/旋转路径（不依赖 WE 的变换语义，
 * BlockEntity 内容不迁移——与 region 快照同限制）。</p>
 *
 * <p>WorldEdit 缺席时返回 null（schem 房间被跳过，region 房间不受影响）。</p>
 */
public final class SchemVolumeLoader {

    private SchemVolumeLoader() { }

    public static @Nullable RegionTemplate load(File schemFile, Logger logger) {
        try {
            return Inner.load(schemFile);
        } catch (Throwable t) {
            logger.warning("[abyss] schem 读取失败 " + schemFile.getName()
                + ": " + t.getMessage());
            return null;
        }
    }

    /** WE 类型集中在这个内部类——仅在 WE/FAWE 启用时才应被调用。 */
    private static final class Inner {
        static RegionTemplate load(File file) throws Exception {
            com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format =
                com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
            if (format == null) {
                format = com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
                    .SPONGE_SCHEMATIC;
            }
            com.sk89q.worldedit.extent.clipboard.Clipboard cb;
            try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader r =
                     format.getReader(new FileInputStream(file))) {
                cb = r.read();
            }
            com.sk89q.worldedit.regions.Region region = cb.getRegion();
            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            int w = region.getWidth(), h = region.getHeight(), d = region.getLength();
            List<String> palette = new ArrayList<>();
            Map<String, Integer> pidx = new HashMap<>();
            byte[][] layers = new byte[h][w * d * 2];
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    for (int x = 0; x < w; x++) {
                        // getBlock 返回 BlockStateHolder；toBaseBlock 转 BaseBlock
                        // 后 adapt 保留完整 BlockData（朝向等属性）
                        String data;
                        try {
                            data = com.sk89q.worldedit.bukkit.BukkitAdapter
                                .adapt(cb.getBlock(min.add(x, y, z)).toBaseBlock())
                                .getAsString();
                        } catch (Throwable t) {
                            // 兜底：材质级转换（丢属性但不丢块）
                            data = com.sk89q.worldedit.bukkit.BukkitAdapter
                                .adapt(cb.getBlock(min.add(x, y, z)).getBlockType())
                                .createBlockData().getAsString();
                        }
                        Integer idx = pidx.get(data);
                        if (idx == null) {
                            idx = palette.size();
                            palette.add(data);
                            pidx.put(data, idx);
                        }
                        int off = (z * w + x) * 2;
                        layers[y][off] = (byte) (idx & 0xFF);
                        layers[y][off + 1] = (byte) ((idx >> 8) & 0xFF);
                    }
                }
            }
            return RegionTemplate.of(w, h, d, palette, layers);
        }
    }
}
