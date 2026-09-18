package xuanmo.arcartxsuite.api.util;

/**
 * 导航贴图标记配置接口。
 * <p>
 * 各模块自行实现此接口以提供贴图路径提示的渲染与寻路参数；
 * 与 {@link NavigationMarkerConfig}（模型标记）相互独立，可单独开关或同时启用。
 *
 * @since 1.7.0
 */
public interface NavigationTextureMarkerConfig {

    /** 是否启用贴图路径提示 */
    boolean enabled();

    /** 贴图资源 ID（ArcartX 客户端资源包中注册的文字/贴图标识） */
    String texture();

    /** 贴图渲染宽度 */
    double width();

    /** 贴图渲染高度 */
    double height();

    /** 是否始终面向玩家（billboard）；false = 按 yaw/pitch 定向（贴地箭头等） */
    boolean facing();

    /** 是否泛光 */
    boolean glowing();

    /** 贴图相对路径点的 Y 轴偏移（方块数） */
    double yOffset();

    /** 贴图俯仰角（度；贴地渲染通常为 90，facing=true 时一般忽略） */
    float pitch();

    /** 是否让贴图 yaw 朝向下一个路径点（贴地箭头指示方向） */
    boolean orientToPath();

    /** 贴图存活时长（客户端单位；-1 = 永续，直到显式移除） */
    int lifeTime();

    /** 是否启用循环缩放脉冲动画 */
    boolean pulseEnabled();

    /** 脉冲起始缩放倍率 */
    double pulseFrom();

    /** 脉冲目标缩放倍率 */
    double pulseTo();

    /** 脉冲单段时长（客户端动画单位） */
    int pulseDuration();

    /** 脉冲逐级延迟（每个路径点相对前一点的动画延迟，形成波浪效果；0 = 同步） */
    int pulseStagger();

    /** 路径贴图间距（方块数，沿路径每隔多少格放一个贴图） */
    double pathInterval();

    /** 路径贴图最大数量（超出后截断远端贴图） */
    int pathMaxMarkers();

    /** 路径更新间隔（tick，玩家移动后重新计算路径） */
    int pathUpdateTicks();

    /** 路径最大渲染距离（方块数，超出此距离不显示路径贴图） */
    double pathMaxDistance();

    /** A* 寻路最大迭代次数（防止服务端卡顿，越大路径越精确但越耗时） */
    int pathMaxIterations();

    /**
     * 距离滞回缓冲（格）：路径贴图因超出 {@link #pathMaxDistance()} 被隐藏后，
     * 玩家必须回到 {@code pathMaxDistance - pathReshowBuffer} 以内才重新显示，
     * 避免在边界抖动导致贴图反复出现/消失。
     *
     * @return 重显示缓冲距离，默认 8.0
     */
    default double pathReshowBuffer() {
        return 8.0;
    }
}
