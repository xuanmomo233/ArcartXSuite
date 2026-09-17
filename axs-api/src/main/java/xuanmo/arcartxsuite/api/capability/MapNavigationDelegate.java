package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 地图全局导航委托（公开契约）。
 * <p>
 * 第三方插件通过 {@code MapNavigable.setNavigationDelegate(plugin, delegate)} 注册后，
 * 玩家在地图上点击任意"导航"（锚点 / 路径点 / 外部目标）都会先经过此接口；
 * （标记点 pin 设计上无 Y 坐标、不可导航，不经此接口）
 * 返回 {@code false} 表示不接，交还给原有处理（自有目标 → 地图内置导航；
 * 外部目标 → 其 source 注册的 {@link MapNavigationHandler}）。
 * <p>
 * 契约：
 * <ul>
 *   <li>导航执行（信标/路径/追踪）完全由委托方负责，地图不建客户端路径点</li>
 *   <li>委托接管后地图记录一条委托导航态，面板"导航"按钮切换为"取消"</li>
 *   <li>玩家点"取消"或地图侧互斥清除时回调 {@link #onCancelRequest}</li>
 *   <li>外部目标被委托接管后，委托方应另调
 *       {@code MapNavigable.setExternalNavigated(player, targetId, true/false)}
 *       驱动该目标行的按钮切换</li>
 *   <li>同一时间全服只有一个委托；重复注册由后者覆盖</li>
 * </ul>
 */
@ApiStability.Stable
public interface MapNavigationDelegate {

    /**
     * 玩家请求开始导航。
     *
     * @param player  发起玩家
     * @param request 目标完整描述（类型/坐标/世界/标题）
     * @return {@code true} 表示已接管导航（地图记录委托态）；
     *         {@code false} 表示不接，交还给原有处理方
     */
    boolean onNavigateRequest(@NotNull Player player, @NotNull MapNavigationRequest request);

    /**
     * 该委托态被终止（玩家点"取消"、互斥清除、来源接管、退出/关闭）。
     * <p>
     * 委托方应停止自身导航表现；实现需幂等——委托方主动结束时地图也会回调此方法。
     *
     * @param player  玩家
     * @param request 当时的导航请求（坐标为尽力重解，可能与发起时不同）
     */
    default void onCancelRequest(@NotNull Player player, @NotNull MapNavigationRequest request) {
    }
}
