package xuanmo.arcartxsuite.abyss.design;

import java.util.List;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/** 设计模式坐标工具："x,y,z" / "x,y,z,yaw,pitch" / region 两角 解析与格式化。 */
public final class DesignCoords {

    private DesignCoords() { }

    /** 解析 "x,y,z"（或前 3 段）为 int[3]；失败返回 null。 */
    public static @Nullable int[] point(String s) {
        if (s == null || s.isBlank()) return null;
        String[] v = s.trim().split(",");
        if (v.length < 3) return null;
        try {
            return new int[]{
                (int) Double.parseDouble(v[0].trim()),
                (int) Double.parseDouble(v[1].trim()),
                (int) Double.parseDouble(v[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析 region 列表（两角 "x,y,z"）为 int[6]；不足两角返回 null。 */
    public static @Nullable int[] box(List<String> region) {
        if (region == null || region.size() < 2) return null;
        int[] a = point(region.get(0));
        int[] b = point(region.get(1));
        if (a == null || b == null) return null;
        return new int[]{a[0], a[1], a[2], b[0], b[1], b[2]};
    }

    /** region 两角格式化为 ["x1,y1,z1","x2,y2,z2"]（自动 min/max 排序）。 */
    public static List<String> boxStrings(int ax, int ay, int az, int bx, int by, int bz) {
        return List.of(
            Math.min(ax, bx) + "," + Math.min(ay, by) + "," + Math.min(az, bz),
            Math.max(ax, bx) + "," + Math.max(ay, by) + "," + Math.max(az, bz));
    }

    public static String pointStr(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static String pointStr(int[] p) {
        return p[0] + "," + p[1] + "," + p[2];
    }

    /** box 中心点（标记显示用）。 */
    public static int[] center(int[] box) {
        return new int[]{(box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2};
    }

    /** 点是否在 box 内（含边界）。 */
    public static boolean inBox(int[] box, int x, int y, int z) {
        return x >= Math.min(box[0], box[3]) && x <= Math.max(box[0], box[3])
            && y >= Math.min(box[1], box[4]) && y <= Math.max(box[1], box[4])
            && z >= Math.min(box[2], box[5]) && z <= Math.max(box[2], box[5]);
    }

    /** origin 相对坐标转绝对（floors region 模式房间坐标语义）。 */
    public static int[] offsetBy(int[] origin, int[] rel) {
        return new int[]{origin[0] + rel[0], origin[1] + rel[1], origin[2] + rel[2]};
    }

    public static int[] boxOffsetBy(int[] origin, int[] box) {
        return new int[]{
            origin[0] + box[0], origin[1] + box[1], origin[2] + box[2],
            origin[0] + box[3], origin[1] + box[4], origin[2] + box[5]};
    }
}
