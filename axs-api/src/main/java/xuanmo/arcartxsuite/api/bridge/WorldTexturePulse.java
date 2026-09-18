package xuanmo.arcartxsuite.api.bridge;

/**
 * WorldTexture 缩放脉冲动画参数（ArcartX {@code WorldTextureBuilder.addScaleAnimation} 的桥接描述）。
 * <p>
 * 用于贴图路径导航等场景的循环缩放提示；{@code delay} 可按路径点序号错开，
 * 形成沿路径传播的波浪效果。
 *
 * @param from     起始缩放倍率
 * @param to       目标缩放倍率
 * @param duration 单段动画时长（客户端动画单位，与 ArcartX 动画时长一致）
 * @param delay    动画开始前延迟（同单位）
 * @param loop     是否循环播放
 * @since 1.7.0
 */
public record WorldTexturePulse(
    double from,
    double to,
    int duration,
    int delay,
    boolean loop
) {

    /**
     * 循环脉冲的便捷构造。
     *
     * @param from     起始缩放倍率
     * @param to       目标缩放倍率
     * @param duration 单段动画时长
     * @param delay    动画开始前延迟
     * @return 循环脉冲参数
     */
    public static WorldTexturePulse looping(double from, double to, int duration, int delay) {
        return new WorldTexturePulse(from, to, duration, delay, true);
    }
}
