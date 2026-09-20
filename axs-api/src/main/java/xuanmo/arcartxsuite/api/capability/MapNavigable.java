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
 *   <li>{@code axs.map.ready} —— 地图模块（重）载完成，按在线玩家逐个发布；
 *       外部目标/导航回调/委托已由地图自动续传恢复，来源可订阅此事件
 *       决定是否重推最新数据（如目标坐标已变化）</li>
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
     * <p>
     * 注意：{@link MapExternalTargetSpec#useMapNavigation()} 为 true 的目标由地图
     * 内部维护该标志，来源无需（也不应）再调用本方法。
     *
     * @param player    目标玩家
     * @param targetId  目标 id
     * @param navigated true = 来源正在导航（按钮显示"取消"）
     */
    void setExternalNavigated(@NotNull Player player, @NotNull String targetId, boolean navigated);

    /**
     * 由地图内置导航栈开始导航指定外部目标（"地图代导航"命令式入口）。
     * <p>
     * 与 spec 的 {@code useMapNavigation} 标志等价，适用于来源想在
     * {@link MapNavigationHandler#onNavigateRequest} 里按需决策的场景；
     * 也可独立调用。效果等同玩家在面板点击"导航"：
     * 地图建立客户端路标与路径标记、记录导航态、目标按钮切换为"取消"。
     * 到达判定/命令钩子/标题模板使用地图 {@code navigation.*} 配置。
     * <p>
     * 本方法不经过全局委托（委托只拦截面板点击）；取消时来源不会收到
     * {@code onCancelRequest}（该导航由地图执行）。
     *
     * @param player   目标玩家
     * @param targetId 已推送的外部目标 id
     * @return {@code true} 表示导航已开始（已在导航同目标也返回 true）；
     *         {@code false} 表示目标不存在或地图导航不可用
     */
    boolean navigateExternalTarget(@NotNull Player player, @NotNull String targetId);

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
     * 注册全局导航委托（全量接管导航点击）。
     * <p>
     * 注册后玩家在地图上点击任意"导航"（锚点/路径点/标记点/外部目标）都先回调
     * delegate；{@code onNavigateRequest} 返回 {@code false} 时交还原处理方。
     * 全服同一时间只有一个委托：重复注册由后者覆盖；插件禁用时自动注销。
     *
     * @param owner    归属插件（插件禁用时自动注销）
     * @param delegate 委托实现
     */
    void setNavigationDelegate(
        @NotNull org.bukkit.plugin.Plugin owner,
        @NotNull MapNavigationDelegate delegate
    );

    /**
     * 注销导航委托（仅当当前委托属于该插件时生效）。
     *
     * @param owner 注册时使用的插件实例
     */
    void clearNavigationDelegate(@NotNull org.bukkit.plugin.Plugin owner);

    /**
     * 当前是否已注册全局导航委托。
     *
     * @return {@code true} 表示所有导航点击会先经委托
     */
    boolean hasNavigationDelegate();

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
