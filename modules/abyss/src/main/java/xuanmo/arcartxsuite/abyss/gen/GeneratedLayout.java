package xuanmo.arcartxsuite.abyss.gen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * 实例级生成布局：本副本全部 GEN 层的生成结果。
 * <p>
 * 挂在 {@link xuanmo.arcartxsuite.abyss.instance.AbyssInstance} 上，
 * 为 {@code anchor:<房ref>.<名>} / {@code corr.<a>-<b>} / {@code zone:<名>}
 * 三类引用提供解析。房 ref 带层前缀（f1_* / f2_* / f3_*），跨层不冲突。
 */
public final class GeneratedLayout {

    /** floorIndex → FloorPlan */
    private final Map<Integer, FloorPlan> floors = new ConcurrentHashMap<>();
    /** "房ref.锚点名" → Location */
    private final Map<String, Location> anchors = new ConcurrentHashMap<>();
    /** "corr.<a>-<b>" → 走廊中点 Location */
    private final Map<String, Location> corridorAnchors = new ConcurrentHashMap<>();
    /** zone 名 → 方块位 */
    private final Map<String, List<RoomPlan.Pos>> zones = new ConcurrentHashMap<>();

    public void putFloor(FloorPlan fp, World w) {
        floors.put(fp.floorIndex, fp);
        for (RoomPlan r : fp.rooms.values()) {
            for (Map.Entry<String, Location> e : r.anchors.entrySet()) {
                anchors.put(r.ref + "." + e.getKey(), e.getValue());
            }
            anchors.putIfAbsent(r.ref + ".center", r.center(w));
        }
        for (FloorPlan.Corridor c : fp.corridors) {
            if (c.mid != null) corridorAnchors.put("corr." + c.key(), c.mid.loc(w));
        }
        zones.putAll(fp.zones);
    }

    public @Nullable FloorPlan floor(int index) { return floors.get(index); }

    public Map<Integer, FloorPlan> floorMap() { return floors; }

    /** 解析锚点引用：{@code <房ref>.<锚点名>} 或 {@code corr.<a>-<b>}。 */
    public @Nullable Location anchor(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("corr.")) return corridorAnchors.get(ref);
        return anchors.get(ref);
    }

    public @Nullable List<RoomPlan.Pos> zone(String name) { return zones.get(name); }

    public boolean isEmpty() { return floors.isEmpty(); }
}
