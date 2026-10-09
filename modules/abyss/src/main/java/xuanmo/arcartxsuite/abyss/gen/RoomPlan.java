package xuanmo.arcartxsuite.abyss.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomType;

/**
 * 生成器产出的单房计划：有机洞穴 footprint + 门洞 + 锚点 + 刷怪点。
 * <p>
 * 坐标全部为世界绝对值（生成时已物化）。{@code floor} 为洞穴内空列集合
 * （x,z 打包），{@code wall} 为紧贴内空的壳体列。锚点按角色命名
 * （center/spawn/altar/chest/merchant/lever_1..4/exit/guide 等），
 * interact/NPC/怪组/引导通过 {@code anchor:<房ref>.<名>} 解析。
 */
public final class RoomPlan {

    /** 三维方块位（世界绝对）。 */
    public record Pos(int x, int y, int z) {
        public Location loc(World w) { return new Location(w, x + 0.5, y, z + 0.5); }
    }

    /** 门洞：洞口格集合 + 通往的房间 ref + 是否初始封死（Boss 门）。 */
    public static final class DoorPlan {
        public final List<Pos> cells = new ArrayList<>();     // 洞口方块位
        public final String linkedRoom;                       // 对面房 ref
        public boolean sealed;                                // true=生成时封墙
        DoorPlan(String linkedRoom) { this.linkedRoom = linkedRoom; }
    }

    public final String ref;
    public final RoomType type;
    /** bbox：含壳体的包围盒（进出判定/虚空基准用它）。 */
    public int minX, minZ, maxX, maxZ;
    public final int floorY;          // 地板层
    public final int ceilY;           // 屏障盖层（内空 = floorY+1 .. ceilY-1）
    /** 内空列集合（pack x,z）。 */
    public final Set<Long> floor = new HashSet<>();
    /** 壳体列集合（紧贴内空的墙柱）。 */
    public final Set<Long> wall = new HashSet<>();
    public final List<DoorPlan> doors = new ArrayList<>();
    /** 房内刷怪采样点（waves location "auto" 从中随机取）。 */
    public final List<Location> mobSpots = new ArrayList<>();
    /** 命名锚点（interact/NPC/guide/机关绑定）。 */
    public final Map<String, Location> anchors = new LinkedHashMap<>();
    @Nullable public Location exitMin, exitMax;   // Boss 出口传送区

    public RoomPlan(String ref, RoomType type, int floorY, int ceilY) {
        this.ref = ref;
        this.type = type;
        this.floorY = floorY;
        this.ceilY = ceilY;
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public Location center(World w) {
        return anchors.getOrDefault("center",
            new Location(w, (minX + maxX) / 2.0 + 0.5, floorY + 1, (minZ + maxZ) / 2.0 + 0.5));
    }

    public @Nullable Location anchor(String name) {
        return anchors.get(name);
    }
}
