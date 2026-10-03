package xuanmo.arcartxsuite.map.model;

/**
 * 玩家导航状态记录，描述当前正在追踪的目标信息。
 * <p>
 * 关联模块：map。由 {@code MapService} 维护，用于驱动 UI 上的追踪指示器
 * 和路径标记。目标类型包括锚点、自定义路径点和外部任务目标。
 *
 * @param active     是否正在导航
 * @param targetType 目标类型（锚点 / 路径点 / 外部目标 / 无）
 * @param worldId    目标所在世界 ID
 * @param targetId   目标唯一标识
 * @param waypointId 关联的客户端路径点 ID（若有）
 * @param label      导航显示标签
 * @param source     来源模块标识（外部目标时有效）
 */
public record MapNavigationState(
    boolean active,
    TargetType targetType,
    String worldId,
    String targetId,
    String waypointId,
    String label,
    String source
) {

    /**
     * 判断当前导航目标是否匹配指定锚点。
     *
     * @param anchorId 锚点 ID
     * @return 若导航活跃且目标类型为锚点且 ID 匹配则返回 {@code true}
     */
    public boolean matchesAnchor(String anchorId) {
        return active && targetType == TargetType.ANCHOR && safe(targetId).equalsIgnoreCase(safe(anchorId));
    }

    /**
     * 判断当前导航目标是否匹配指定路径点。
     *
     * @param waypointId 路径点 ID
     * @return 若导航活跃且目标类型为路径点且 ID 匹配则返回 {@code true}
     */
    public boolean matchesWaypoint(String waypointId) {
        return active && targetType == TargetType.WAYPOINT && safe(targetId).equalsIgnoreCase(safe(waypointId));
    }

    /**
     * 判断当前追踪目标是否匹配指定标记点。
     *
     * @param pinId 标记点 ID
     * @return 若追踪活跃且目标类型为标记点且 ID 匹配则返回 {@code true}
     */
    public boolean matchesPin(String pinId) {
        return active && targetType == TargetType.PIN && safe(targetId).equalsIgnoreCase(safe(pinId));
    }

    public enum TargetType {
        NONE,
        ANCHOR,
        WAYPOINT,
        EXTERNAL,
        /** 标记点追踪：纯地图标记态（不建导航路标，仅驱动地图边缘指示）。 */
        PIN
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
