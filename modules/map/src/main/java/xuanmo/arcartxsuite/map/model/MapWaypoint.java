package xuanmo.arcartxsuite.map.model;

/**
 * 玩家自定义路径点记录，存储玩家在地图上创建的标记点。
 * <p>
 * 关联模块：map。由 {@code JdbcMapRepository} 持久化到数据库，
 * 每个玩家在每个世界中可创建有限数量的路径点（受配置限制）。
 *
 * @param waypointId 路径点唯一标识
 * @param name       路径点名称
 * @param world      所在世界 ID
 * @param x          X 坐标
 * @param y          Y 坐标
 * @param z          Z 坐标
 * @param createdAt  创建时间戳（毫秒）
 * @param updatedAt  更新时间戳（毫秒）
 */
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
