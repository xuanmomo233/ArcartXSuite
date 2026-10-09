package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * 单层生成计划：房间集 + 走廊路径 + 命名障碍区 + 层入口点。
 * <p>
 * 走廊记录为中轴折线（{@link Corridor}）供引导/障碍区引用；
 * {@code zones} 为生成器产出的命名点位集（如 Boss 走廊陷阱带），
 * obstacles.yml 用 {@code zone: <名>} 绑定。
 */
public final class FloorPlan {

    /** 走廊：两房之间的通道。cells=地面列集合，mid=中点（引导锚点）。 */
    public static final class Corridor {
        public final String a, b;                 // 两房 ref
        public final List<RoomPlan.Pos> cells = new ArrayList<>();  // 廊内地面格
        public java.util.Set<Long> band;          // 带宽列集（门洞探测用）
        public final java.util.List<int[]> path = new java.util.ArrayList<>(); // 有序折线路径
        public RoomPlan.Pos mid;                  // 中点
        Corridor(String a, String b) { this.a = a; this.b = b; }
        public String key() { return a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a; }
    }

    public final int floorIndex;
    /** ref → 房计划（含本次抽到/未抽到的取舍结果：只含实际生成的房）。 */
    public final Map<String, RoomPlan> rooms = new LinkedHashMap<>();
    public final List<Corridor> corridors = new ArrayList<>();
    /** 命名障碍区：zone 名 → 方块位列表（obstacles.yml zone: 绑定）。 */
    public final Map<String, List<RoomPlan.Pos>> zones = new LinkedHashMap<>();
    /** 层入口站位（=入口房 spawn）。 */
    public @Nullable Location entrySpawn;
    /** 生成区包围盒（区块预载用）。 */
    public int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    public int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public FloorPlan(int floorIndex) { this.floorIndex = floorIndex; }

    public void growBounds(RoomPlan r) {
        minX = Math.min(minX, r.minX); maxX = Math.max(maxX, r.maxX);
        minZ = Math.min(minZ, r.minZ); maxZ = Math.max(maxZ, r.maxZ);
    }

    public @Nullable RoomPlan room(String ref) { return rooms.get(ref); }

    /** 走廊锚点："corr.<a>-<b>" → 廊道中点。 */
    public @Nullable Location corridorAnchor(String key, World w) {
        for (Corridor c : corridors) {
            if (c.key().equals(key) && c.mid != null) return c.mid.loc(w);
        }
        return null;
    }
}
