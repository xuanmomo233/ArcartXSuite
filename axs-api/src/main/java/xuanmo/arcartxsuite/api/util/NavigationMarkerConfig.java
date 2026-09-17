package xuanmo.arcartxsuite.api.util;

/**
 * 导航标记配置接口。
 * <p>
 * 各模块自行实现此接口以提供标记配置参数。
 *
 * @since 1.5.0
 */
public interface NavigationMarkerConfig {

    boolean enabled();

    String modelId();

    double scale();

    String defaultState();

    String animation();

    double yOffset();

    double pathInterval();

    int pathMaxMarkers();

    int pathUpdateTicks();

    double pathMaxDistance();

    int pathMaxIterations();

    /**
     * 距离滞回缓冲（格）：路径标记因超出 {@link #pathMaxDistance()} 被隐藏后，
     * 玩家必须回到 {@code pathMaxDistance - pathReshowBuffer} 以内才重新显示，
     * 避免在边界抖动导致标记反复出现/消失。
     *
     * @return 重显示缓冲距离，默认 8.0
     * @since 1.6.0
     */
    default double pathReshowBuffer() {
        return 8.0;
    }
}
