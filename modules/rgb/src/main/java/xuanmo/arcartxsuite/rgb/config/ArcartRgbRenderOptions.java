package xuanmo.arcartxsuite.rgb.config;

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
