package xuanmo.arcartxsuite.abyss.region;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.RegionTemplate;

/**
 * 区域模板库：服主选区状态 + 模板文件存取 + 运行时缓存。
 * <p>
 * 存储路径：{@code <地牢目录>/regions/<名>.yml}（随地牢目录一起部署/迁移）。
 * paste_region 脚本动作按 {@code 地牢id/名} 缓存已解析模板。
 */
public final class RegionLibrary {

    private static final Pattern NAME_RE = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    /** 服主选区：pos2 未取时 y2=MIN_VALUE（两角可任意顺序标）。 */
    public record Sel(String world, int x1, int y1, int z1,
                      int x2, int y2, int z2) {
        public boolean complete() { return y2 != Integer.MIN_VALUE; }
    }

    private final Map<UUID, Sel> selections = new ConcurrentHashMap<>();
    private final Map<String, RegionTemplate> cache = new ConcurrentHashMap<>();
    private final Logger logger;

    public RegionLibrary(Logger logger) {
        this.logger = logger;
    }

    // ═══ 选区 ═══

    /** 标记玩家当前方块坐标为角点（which=1|2）。 */
    public Sel mark(Player p, int which) {
        Sel cur = selections.get(p.getUniqueId());
        String w = p.getWorld().getName();
        int bx = p.getLocation().getBlockX(), by = p.getLocation().getBlockY(),
            bz = p.getLocation().getBlockZ();
        Sel sel = (cur == null || !w.equals(cur.world()))
            ? (which == 1 ? new Sel(w, bx, by, bz, 0, Integer.MIN_VALUE, 0)
                          : new Sel(w, 0, Integer.MIN_VALUE, 0, bx, by, bz))
            : (which == 1 ? new Sel(w, bx, by, bz, cur.x2(), cur.y2(), cur.z2())
                          : new Sel(w, cur.x1(), cur.y1(), cur.z1(), bx, by, bz));
        selections.put(p.getUniqueId(), sel);
        return sel;
    }

    public @Nullable Sel selection(UUID player) {
        return selections.get(player);
    }

    public void clearSelection(UUID player) {
        selections.remove(player);
    }

    // ═══ 存取 ═══

    private static File regionsDir(AbyssTemplate tpl) {
        return new File(tpl.dir(), "regions");
    }

    public static boolean validName(String name) {
        return name != null && NAME_RE.matcher(name).matches();
    }

    /** 用玩家当前选区快照并落盘；返回错误文案（成功为 null）。 */
    public @Nullable String save(AbyssTemplate tpl, String name, Player p) {
        if (!validName(name)) return "模板名仅允许 [A-Za-z0-9_-] 且 ≤32 字符";
        Sel sel = selections.get(p.getUniqueId());
        if (sel == null || !sel.complete()) {
            return "先用 /axs abyss region pos1|pos2 标好两个角";
        }
        if (!p.getWorld().getName().equals(sel.world())) {
            return "你已离开选区所在世界（" + sel.world() + "）";
        }
        RegionTemplate tpl2;
        try {
            tpl2 = RegionTemplate.capture(p.getWorld(),
                sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2());
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        File f = new File(regionsDir(tpl), name + ".yml");
        try {
            tpl2.save(f);
        } catch (Exception e) {
            logger.warning("[abyss] 区域模板保存失败 " + f + ": " + e);
            return "写文件失败: " + e.getMessage();
        }
        cache.put(key(tpl.id(), name), tpl2);
        return null;
    }

    /** 按地牢+名加载（缓存优先，文件兜底）；不存在返回 null。 */
    public @Nullable RegionTemplate load(AbyssTemplate tpl, String name) {
        if (!validName(name)) return null;
        return cache.computeIfAbsent(key(tpl.id(), name),
            k -> RegionTemplate.load(new File(regionsDir(tpl), name + ".yml")));
    }

    /** 列出模板名（不含后缀）。 */
    public List<String> list(AbyssTemplate tpl) {
        List<String> out = new ArrayList<>();
        File dir = regionsDir(tpl);
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".yml")) : null;
        if (files != null) {
            for (File f : files) out.add(f.getName().replaceFirst("\\.yml$", ""));
        }
        out.sort(String::compareTo);
        return out;
    }

    public boolean remove(AbyssTemplate tpl, String name) {
        if (!validName(name)) return false;
        cache.remove(key(tpl.id(), name));
        return new File(regionsDir(tpl), name + ".yml").delete();
    }

    /** 模板重载时清该地牢的模板缓存（文件可能已更新）。 */
    public void evict(String dungeonId) {
        String prefix = dungeonId + "/";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String key(String dungeonId, String name) {
        return dungeonId + "/" + name;
    }
}
