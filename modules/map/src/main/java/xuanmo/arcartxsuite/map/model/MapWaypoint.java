package xuanmo.arcartxsuite.map.model;

public record MapWaypoint(
    String waypointId,
    String name,
    String world,
    double x,
    double y,
    double z,
    long createdAt,
    long updatedAt
) {
}
