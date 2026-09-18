package xuanmo.arcartxsuite.api.util;

import org.bukkit.entity.Player;

/**
 * 导航路径渲染器 — 沿寻路结果向单个玩家渲染路径提示的抽象。
 * <p>
 * 现有实现：
 * <ul>
 *   <li>{@link NavigationMarkerService} — Adyeshach 私有实体 + ArcartX 模型的 3D 路径标记</li>
 *   <li>{@link NavigationTextureMarkerService} — ArcartX 世界贴图（WorldTexture）的 2D 贴图提示</li>
 * </ul>
 * {@link NavigationTargetService} 可同时挂载多个渲染器：模型导航与贴图导航互补、独立开关。
 *
 * @since 1.7.0
 */
public interface NavigationRouteRenderer {

    /**
     * 设置/重定向导航目标，启动该分组的路径渲染。
     * 同一 markerKey 重复调用为原地重定向，不重建已渲染内容。
     *
     * @param player    玩家
     * @param markerKey 标记分组键（同一玩家可有多条并行路径渲染）
     * @param world     目标世界名
     * @param x         目标 X
     * @param y         目标 Y
     * @param z         目标 Z
     */
    void showMarker(Player player, String markerKey, String world, double x, double y, double z);

    /**
     * 移除玩家指定分组的路径渲染。
     *
     * @param player    玩家
     * @param markerKey 标记分组键
     */
    void hideMarker(Player player, String markerKey);

    /**
     * 移除玩家的全部路径渲染（全部分组）。
     *
     * @param player 玩家
     */
    void hideMarker(Player player);

    /**
     * 玩家指定分组当前是否有活跃路径渲染。
     *
     * @param player    玩家
     * @param markerKey 标记分组键
     * @return 存在且未取消时返回 {@code true}
     */
    boolean hasMarker(Player player, String markerKey);

    /**
     * 渲染器是否可用（依赖桥接初始化成功且配置启用）。
     *
     * @return 可用时返回 {@code true}
     */
    boolean isAvailable();
}
