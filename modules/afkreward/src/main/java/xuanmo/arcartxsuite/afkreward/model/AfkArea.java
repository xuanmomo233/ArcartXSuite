package xuanmo.arcartxsuite.afkreward.model;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record AfkArea(
    String id,
    String name,
    boolean enabled,
    String world,
    String rewardType,
    List<Point> points,
    Location teleport,
    boolean manualEnabled,
    double rewardWeight,
    RewardConfig reward,
    MultiplierConfig multiplier
) {
    public record Point(int x, int z) {}

    public record RewardConfig(
        int roundMinutes,
        MaxConfig max,
        PlayerLimitConfig player,
        OverflowConfig overflowToMail,
        AfkRewardType type
    ) {
        public record MaxConfig(boolean enabled, int limit) {}
        public record PlayerLimitConfig(boolean enabled, int limit) {}
        public record OverflowConfig(boolean enable) {}
    }

    public record MultiplierConfig(
        boolean enable,
        double base,
        double weekend,
        String combine,
        List<ScheduleConfig> schedules
    ) {}

    public record ScheduleConfig(
        String days, String start, String end, double multiplier
    ) {}

    /**
     * 射线法判断点是否在多边形内。
     */
    public boolean contains(int x, int z) {
        if (points == null || points.size() < 3) return false;
        boolean inside = false;
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point pi = points.get(i);
            Point pj = points.get(j);
            boolean intersect = ((pi.z > z) != (pj.z > z))
                && (x < (pj.x - pi.x) * (z - pi.z) / (double) (pj.z - pi.z) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean contains(String worldName, int x, int z) {
        return this.world.equals(worldName) && contains(x, z);
    }

    /**
     * 判断该区域是否支持原地挂机（配置了传送点）。
     */
    public boolean hasTeleport() {
        return teleport != null;
    }

    /**
     * 从配置数据构建传送点 Location。
     */
    public static Location buildTeleport(String worldName, double x, double y, double z, float yaw, float pitch) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
