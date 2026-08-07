package xuanmo.arcartxsuite.rgb.config;

/**
 * RGB 渲染选项，从条目中提取的渲染时参数。
 *
 * @param switchIntervalTicks 颜色切换间隔（tick，至少 0）
 * @param shineWidth          流光宽度（至少 1）
 * @param shineColor          流光颜色，null 时默认白色
 * @param shineStrength       流光强度 [0, 1]
 */
public record ArcartRgbRenderOptions(
    long switchIntervalTicks,
    int shineWidth,
    ArcartRgbColor shineColor,
    double shineStrength
) {

    private static final ArcartRgbColor DEFAULT_SHINE_COLOR = new ArcartRgbColor(255, 255, 255);

    public ArcartRgbRenderOptions {
        switchIntervalTicks = Math.max(0L, switchIntervalTicks);
        shineWidth = Math.max(1, shineWidth);
        shineColor = shineColor == null ? DEFAULT_SHINE_COLOR : shineColor;
        shineStrength = clamp(shineStrength);
    }

    /** 从条目构造渲染选项。 */
    public static ArcartRgbRenderOptions fromEntry(ArcartRgbEntry entry) {
        return new ArcartRgbRenderOptions(
            entry.switchIntervalTicks(),
            entry.shineWidth(),
            entry.shineColor(),
            entry.shineStrength()
        );
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
