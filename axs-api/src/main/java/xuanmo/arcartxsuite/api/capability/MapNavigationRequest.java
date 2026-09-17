package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 一次"导航点击"的完整描述（公开契约），由地图组装后交给 {@link MapNavigationDelegate}。
 * <p>
 * 委托方无法自行解析地图自有目标的坐标（玩家路径点/标记点是地图私有数据），
 * 因此请求里携带全部定位信息；外部目标的坐标来源同理内嵌。
 *
 * @param targetType 目标类型：{@code "anchor"} | {@code "waypoint"} | {@code "external"}
 * @param targetId   目标唯一标识（锚点 id / 路径点 id / 外部目标 id）
 * @param worldId    目标所在世界 ID（地图 worlds 配置中的世界名）
 * @param title      显示名称（锚点显示名 / 路径点名 / 外部目标标题）
 * @param x          X 坐标
 * @param y          Y 坐标
 * @param z          Z 坐标
 * @param source     原始来源标识：地图自有目标固定 {@code "map"}；外部目标为其注册时的 source
 */
@ApiStability.Stable
public record MapNavigationRequest(
    @NotNull String targetType,
    @NotNull String targetId,
    @NotNull String worldId,
    @NotNull String title,
    double x,
    double y,
    double z,
    @NotNull String source
) {

    public static final String TYPE_ANCHOR = "anchor";
    public static final String TYPE_WAYPOINT = "waypoint";
    public static final String TYPE_EXTERNAL = "external";

    /** 地图自有目标使用的 source 值。 */
    public static final String SOURCE_MAP = "map";
}
