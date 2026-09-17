package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 地图外部目标导航回调。
 * <p>
 * 来源模块/插件通过 {@code MapNavigable.registerNavigationSource(source, handler)}
 * 注册后，玩家在地图详情面板点击该来源目标的"导航"/"取消"按钮时，由地图回调此接口。
 * 导航的实际执行（信标、路径标记、追踪状态）由来源全权负责；地图只做展示与转发。
 * <p>
 * 导航状态变化后来源应调用 {@code MapNavigable.setExternalNavigated} 回推，
 * 使地图面板按钮在"导航"/"取消"之间正确切换。
 */
public interface MapNavigationHandler {

    /**
     * 玩家请求开始导航到该外部目标。
     *
     * @param player   发起玩家
     * @param targetId 目标 id（即 spec 中的 targetId）
     * @return {@code true} 表示来源已接管导航；{@code false} 地图向玩家提示导航失败
     */
    boolean onNavigateRequest(@NotNull Player player, @NotNull String targetId);

    /**
     * 玩家请求取消对该外部目标的导航。
     * <p>
     * 来源应停止自身导航（信标/路径标记/追踪状态），但目标标记本身保留在地图上。
     *
     * @param player   发起玩家
     * @param targetId 目标 id
     */
    default void onCancelRequest(@NotNull Player player, @NotNull String targetId) {
    }
}
