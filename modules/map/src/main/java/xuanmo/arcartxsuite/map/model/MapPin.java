package xuanmo.arcartxsuite.map.model;

/**
 * 玩家自定义标记点，只有 X/Z 坐标（无 Y），在地图上显示一个点。
 * <p>
 * 与 {@link MapWaypoint} 不同，标记点没有 Y 坐标，不能导航，
 * 只用于在地图上标记一个位置。由 {@code JdbcMapRepository} 持久化到数据库。
 *
 * @param pinId      标记点唯一标识
 * @param name       标记点名称
 * @param description 标记点描述
 * @param world      所在世界 ID
 * @param x          X 坐标
 * @param z          Z 坐标
 * @param createdAt  创建时间戳（毫秒）
 * @param updatedAt  更新时间戳（毫秒）
 */
public record MapPin(
    String pinId,
    String name,
    String description,
    String world,
    double x,
    double z,
    long createdAt,
    long updatedAt
) {
}
