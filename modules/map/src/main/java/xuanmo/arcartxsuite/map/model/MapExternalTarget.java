package xuanmo.arcartxsuite.map.model;

public record MapExternalTarget(
    String targetId,
    String source,
    String worldId,
    String title,
    String description,
    double x,
    double y,
    double z,
    int sortOrder
) {
}
