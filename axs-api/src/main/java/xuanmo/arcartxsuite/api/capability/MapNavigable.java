package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 地图导航能力接口（公开 capability）。
 * <p>
 * 由 Map 模块实现。地图是展示层与事件源：其他模块/外部插件推送外部目标点、
 * 接收导航点击回调、监测导航事件。外部目标的坐标与导航执行由来源全权负责。
 * <p>
 * 获取方式：
 * <ul>
 *   <li>外部插件：{@code AxsCapabilities.get(MapNavigable.class)}</li>
 *   <li>AXS 模块：{@code getCapability(MapNavigable.class)}</li>
 * </ul>
 * <p>
 * 监测事件（EventBus topic，可经公开的 EventBusCapability 订阅）：
 * <ul>
 *   <li>{@code axs.map.nav_started} —— 玩家开始导航（payload 含 type/id/world）</li>
 *   <li>{@code axs.map.nav_cleared} —— 玩家停止导航</li>
 *   <li>{@code axs.map.external_navigate} —— 面板点击外部目标"导航"</li>
 *   <li>{@code axs.map.external_nav_cancel} —— 面板点击外部目标"取消"</li>
 * </ul>
 * <p>
 * 互斥约定：同一时间一个玩家只有一个活跃导航。来源开始自身导航时应调用
 * {@link #clearTrack} 清除地图导航；地图开始自身导航时会发布
 * {@code axs.map.nav_started}，来源订阅后应停止自身导航。
 */
@ApiStability.Stable
@PublicCapability
public interface MapNavigable {

    /**
     * 设置/更新外部导航目标（{@link MapExternalTargetSpec} 全字段版本）。
     * <p>
     * 外部目标在地图上渲染为紫色标记，不占用玩家路径点数量上限。
     *
     * @param player 目标玩家
     * @param spec   目标规格
     * @param select 是否同时设置为当前选中
     */
    void upsertExternalTarget(
        @NotNull Player player,
        @NotNull MapExternalTargetSpec spec,
        boolean select
    );

    /**
     * 批量设置外部导航目标（一次视图同步，适用于推送多个目标）。
     *
     * @param player 目标玩家
     * @param specs  目标规格列表
     */
    void upsertExternalTargets(@NotNull Player player, @NotNull List<MapExternalTargetSpec> specs);

    /**
     * 用给定集合原子替换某来源的全部外部目标（reconcile）。
     * <p>
     * 与"先 {@link #clearExternalTargets} 再 {@link #upsertExternalTargets}"的区别：
     * 单次视图同步，且选中态按最终集合回收——仍存在的目标保持选中不闪断。
     * 适合来源周期性全量推送"当前所有目标"的场景。
     *
     * @param player 目标玩家
     * @param source 来源标识
     * @param specs  最终目标集合（空列表 = 清空该来源全部目标）
     */
    void reconcileExternalTargets(
        @NotNull Player player,
        @NotNull String source,
        @NotNull List<MapExternalTargetSpec> specs
    );

    /**
     * 清除指定来源的所有外部导航目标。
     *
     * @param player   目标玩家
     * @param source   来源标识；空字符串表示清除全部来源
     * @param syncView 是否立即同步视图
     */
    void clearExternalTargets(@NotNull Player player, @NotNull String source, boolean syncView);

    /**
     * 标记外部目标是否正被来源导航（驱动面板"导航"/"取消"按钮切换）。
     *
     * @param player    目标玩家
     * @param targetId  目标 id
     * @param navigated true = 来源正在导航（按钮显示"取消"）
     */
    void setExternalNavigated(@NotNull Player player, @NotNull String targetId, boolean navigated);

    /**
     * 注册外部目标导航回调（按 source 归属）。
     * <p>
     * 玩家在地图面板点击该来源目标的"导航"/"取消"时回调 handler。
     * 同一 source 重复注册会覆盖旧 handler。
     *
     * @param owner   归属插件（用于插件禁用时自动注销回调）
     * @param source  来源标识（须与 spec 中的 source 一致）
     * @param handler 导航回调
     */
    void registerNavigationSource(
        @NotNull org.bukkit.plugin.Plugin owner,
        @NotNull String source,
        @NotNull MapNavigationHandler handler
    );

    /**
     * 注销指定来源的导航回调。
     *
     * @param source 来源标识
     */
    void unregisterNavigationSource(@NotNull String source);

    /**
     * 清除玩家当前的地图导航（锚点/路径点/外部目标追踪）。
     * <p>
     * 来源开始自身导航时调用，实现导航互斥。
     *
     * @param player 目标玩家
     */
    void clearTrack(@NotNull Player player);

    /**
     * 为玩家打开地图菜单并选中指定目标。
     * <p>
     * targetId 依次匹配外部目标 → 玩家路径点 → 标记点，命中则切到目标所在世界
     * 并选中；未命中或为空时按默认世界打开。
     *
     * @param player   目标玩家
     * @param targetId 目标 id（可为空字符串）
     */
    void openMenuFor(@NotNull Player player, @NotNull String targetId);
}
