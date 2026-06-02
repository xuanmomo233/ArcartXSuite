package xuanmo.arcartxsuite.map.model;

public record MapNavigationState(
    boolean active,
    TargetType targetType,
    String worldId,
    String targetId,
    String waypointId,
    String label,
    String source
) {

    public static MapNavigationState none() {
        return new MapNavigationState(false, TargetType.NONE, "", "", "", "", "");
    }

    public boolean matchesAnchor(String anchorId) {
        return active && targetType == TargetType.ANCHOR && safe(targetId).equalsIgnoreCase(safe(anchorId));
    }

    public boolean matchesWaypoint(String waypointId) {
        return active && targetType == TargetType.WAYPOINT && safe(targetId).equalsIgnoreCase(safe(waypointId));
    }

    public boolean matchesExternal(String targetId) {
        return active && targetType == TargetType.EXTERNAL && safe(this.targetId).equalsIgnoreCase(safe(targetId));
    }

    public enum TargetType {
        NONE,
        ANCHOR,
        WAYPOINT,
        EXTERNAL
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
