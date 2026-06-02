package xuanmo.arcartxsuite.rgb.config;

import java.util.List;

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
