package xuanmo.arcartxsuite.rgb.config;

import java.util.List;

/**
 * RGB 渐变文本条目配置。
 *
 * @param id                  条目 id
 * @param enabled             是否启用
 * @param text                原始文本
 * @param gradientColors      渐变颜色序列
 * @param shineEnabled        是否启用流光效果
 * @param switchIntervalTicks 颜色切换间隔（tick）
 * @param shineWidth          流光宽度
 * @param shineColor          流光颜色
 * @param shineStrength       流光强度 [0, 1]
 */
public record ArcartRgbEntry(
    String id,
    boolean enabled,
    String text,
    List<ArcartRgbColor> gradientColors,
    boolean shineEnabled,
    long switchIntervalTicks,
    int shineWidth,
    ArcartRgbColor shineColor,
    double shineStrength
) {

    public ArcartRgbEntry {
        text = text == null ? "" : text;
        gradientColors = List.copyOf(gradientColors);
        switchIntervalTicks = Math.max(0L, switchIntervalTicks);
        shineWidth = Math.max(1, shineWidth);
        shineStrength = clamp(shineStrength);
    }

    /** 判断条目是否处于活动状态（启用、文本非空、有颜色）。 */
    public boolean active() {
        return enabled && !text.isBlank() && !gradientColors.isEmpty();
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
