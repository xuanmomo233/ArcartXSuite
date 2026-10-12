package xuanmo.arcartxsuite.abyss.procgen;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenRoomAsset.RoomKind;
import xuanmo.arcartxsuite.abyss.template.RegionTemplate;

/**
 * 房间集库：扫描 {@code data/abyss/room-sets/<集>/} 下构件对
 * （{@code xxx.schem}|{@code xxx.region.yml} + {@code xxx.yml}），
 * 按房型分组供布局生成器抽取。全局共享池——多副本可复用同一房间集。
 */
public final class RoomSetLibrary {

    /** 已加载的房间集：集名 → (构件名 → asset)。 */
    private final Map<String, Map<String, ProcGenRoomAsset>> sets = new ConcurrentHashMap<>();
    private final File rootDir;
    private final Logger logger;

    public RoomSetLibrary(File moduleDataFolder, Logger logger) {
        this.rootDir = new File(moduleDataFolder, "room-sets");
        this.logger = logger;
    }

    /** 清缓存重扫（reload 时调用）。 */
    public void reload() { sets.clear(); }

    private static boolean worldEditAvailable() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
            || org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
    }

    /**
     * 取房间集（懒加载+缓存）。
     * @return null = 房间集不存在或无有效房间
     */
    public @Nullable Map<String, ProcGenRoomAsset> get(String setName) {
        return sets.computeIfAbsent(setName, this::scan);
    }

    /** 按房型分组。 */
    public Map<RoomKind, List<ProcGenRoomAsset>> byKind(String setName) {
        Map<String, ProcGenRoomAsset> set = get(setName);
        Map<RoomKind, List<ProcGenRoomAsset>> out = new LinkedHashMap<>();
        if (set == null) return out;
        for (ProcGenRoomAsset a : set.values()) {
            out.computeIfAbsent(a.kind(), k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    private Map<String, ProcGenRoomAsset> scan(String setName) {
        Map<String, ProcGenRoomAsset> out = new LinkedHashMap<>();
        File dir = new File(rootDir, setName);
        if (!dir.isDirectory()) {
            logger.warning("[abyss] 房间集不存在: " + dir.getPath());
            return out;
        }
        File[] files = dir.listFiles();
        if (files == null) return out;
        // 先索引几何文件
        Map<String, File> schems = new HashMap<>();
        Map<String, File> regions = new HashMap<>();
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".schem")) {
                schems.put(f.getName().substring(0, n.length() - 6), f);
            } else if (n.endsWith(".region.yml")) {
                regions.put(f.getName().substring(0, n.length() - 11), f);
            }
        }
        // 元数据 yml（非 .region.yml）
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".yml") || n.endsWith(".region.yml")) continue;
            String base = f.getName().substring(0, n.length() - 4);
            File schem = schems.get(base);
            File regionFile = regions.get(base);
            RegionTemplate region = null;
            if (schem != null) {
                // schem → RegionTemplate 统一内部表示（与 region-yml 同路径铺放）
                if (worldEditAvailable()) {
                    region = SchemVolumeLoader.load(schem, logger);
                    if (region == null) {
                        logger.warning("[abyss] 房间 schem 转换失败 " + base);
                    }
                } else {
                    logger.warning("[abyss] 房间 " + base
                        + " 是 .schem，需要 WorldEdit/FAWE（未检测到，跳过）");
                }
            } else if (regionFile != null) {
                try {
                    region = RegionTemplate.load(regionFile);
                } catch (Throwable t) {
                    logger.warning("[abyss] 房间 region 加载失败 " + base
                        + ": " + t.getMessage());
                }
            }
            // 几何必须存在（schem 转换失败/region 加载失败 → 跳过该房间）
            if (region == null) {
                if (schem == null && regionFile == null) {
                    logger.warning("[abyss] 房间缺几何文件（需要 " + base
                        + ".schem 或 " + base + ".region.yml）");
                }
                continue;
            }
            ProcGenRoomAsset asset;
            try {
                asset = ProcGenRoomAsset.load(base, f, null, region);
            } catch (Throwable t) {
                logger.warning("[abyss] 房间元数据解析失败 " + base
                    + ": " + t.getMessage());
                continue;
            }
            out.put(base, asset);
        }
        if (out.isEmpty()) {
            logger.warning("[abyss] 房间集为空: " + setName);
        }
        return out;
    }
}
