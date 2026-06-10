package xuanmo.arcartxsuite.fishing.model;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaterArea {

    private final String name;
    private final String displayName;
    private final AreaType type;
    private final String worldName;
    private final double[] center;      // [x, y, z] for circle
    private final double radius;        // for circle
    private final double[] min;         // [x, y, z] for rectangle
    private final double[] max;         // [x, y, z] for rectangle
    private final String fishPool;
    private final String treasurePool;
    private final double difficultyModifier;
    private final Map<String, Double> baitMultipliers;
    private final String requirePermission;

    public WaterArea(@NotNull String name, @NotNull String displayName, @NotNull AreaType type,
                     @Nullable String worldName, double @Nullable [] center, double radius,
                     double @Nullable [] min, double @Nullable [] max,
                     @NotNull String fishPool, @NotNull String treasurePool,
                     double difficultyModifier, @NotNull Map<String, Double> baitMultipliers,
                     @Nullable String requirePermission) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.worldName = worldName;
        this.center = center;
        this.radius = radius;
        this.min = min;
        this.max = max;
        this.fishPool = fishPool;
        this.treasurePool = treasurePool;
        this.difficultyModifier = difficultyModifier;
        this.baitMultipliers = baitMultipliers;
        this.requirePermission = requirePermission;
    }

    public @NotNull String name() { return name; }
    public @NotNull String displayName() { return displayName; }
    public @NotNull AreaType type() { return type; }
    public @Nullable String worldName() { return worldName; }
    public double @Nullable [] center() { return center; }
    public double radius() { return radius; }
    public double @Nullable [] min() { return min; }
    public double @Nullable [] max() { return max; }
    public @NotNull String fishPool() { return fishPool; }
    public @NotNull String treasurePool() { return treasurePool; }
    public double difficultyModifier() { return difficultyModifier; }
    public @NotNull Map<String, Double> baitMultipliers() { return baitMultipliers; }
    public @Nullable String requirePermission() { return requirePermission; }

    public boolean contains(@NotNull Location loc) {
        if (worldName != null && !worldName.isEmpty()) {
            World world = loc.getWorld();
            if (world == null || !world.getName().equals(worldName)) {
                return false;
            }
        }
        return switch (type) {
            case CIRCLE -> containsCircle(loc);
            case RECTANGLE -> containsRectangle(loc);
            case DEFAULT -> true;
        };
    }

    private boolean containsCircle(Location loc) {
        if (center == null) return false;
        double dx = loc.getX() - center[0];
        double dy = loc.getY() - center[1];
        double dz = loc.getZ() - center[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius;
    }

    private boolean containsRectangle(Location loc) {
        if (min == null || max == null) return false;
        return loc.getX() >= min[0] && loc.getX() <= max[0]
            && loc.getY() >= min[1] && loc.getY() <= max[1]
            && loc.getZ() >= min[2] && loc.getZ() <= max[2];
    }

    public enum AreaType {
        CIRCLE, RECTANGLE, DEFAULT
    }
}
