package xuanmo.arcartxsuite.rgb.service;

import java.util.List;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbColor;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbEntry;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbRenderOptions;

public final class ArcartRgbRenderer {

    private ArcartRgbRenderer() {
    }

    public static String render(ArcartRgbEntry entry, long animationStep) {
        if (entry == null || !entry.active()) {
            return "";
        }
        return renderText(entry.text(), entry.shineEnabled(), entry.gradientColors(), animationStep, ArcartRgbRenderOptions.fromEntry(entry));
    }

    public static String renderText(
        String text,
        boolean shine,
        List<ArcartRgbColor> colors,
        long animationStep,
        ArcartRgbRenderOptions options
    ) {
        String actualText = text == null ? "" : text;
        List<ArcartRgbColor> palette = colors == null ? List.of() : colors;
        if (actualText.isEmpty() || palette.isEmpty()) {
            return actualText;
        }

        ArcartRgbRenderOptions actualOptions = options == null
            ? new ArcartRgbRenderOptions(0L, 2, new ArcartRgbColor(255, 255, 255), 0.55D)
            : options;
        int[] codePoints = actualText.codePoints().toArray();
        if (codePoints.length == 0) {
            return "";
        }

        int paletteShift = palette.isEmpty() ? 0 : (int) Math.floorMod(animationStep, palette.size());
        int shineCenter = resolveShineCenter(codePoints.length, shine, actualOptions, animationStep);
        StringBuilder builder = new StringBuilder(codePoints.length * 12);

        for (int index = 0; index < codePoints.length; index++) {
            double position = codePoints.length == 1 ? 0.0D : (double) index / (double) (codePoints.length - 1);
            ArcartRgbColor color = resolveGradientColor(palette, position, paletteShift);
            if (shine) {
                double shineAmount = resolveShineAmount(index, shineCenter, actualOptions.shineWidth(), actualOptions.shineStrength());
                if (shineAmount > 0.0D) {
                    color = color.blend(actualOptions.shineColor(), shineAmount);
                }
            }
            builder.append(color.arcartCode()).appendCodePoint(codePoints[index]);
        }
        builder.append("§r");
        return builder.toString();
    }

    public static String renderAtTime(ArcartRgbEntry entry, long currentTimeMillis) {
        return render(entry, resolveAnimationStep(entry, currentTimeMillis));
    }

    public static String renderTextAtTime(
        String text,
        boolean shine,
        List<ArcartRgbColor> colors,
        long currentTimeMillis,
        ArcartRgbRenderOptions options
    ) {
        return renderText(text, shine, colors, resolveAnimationStep(options, currentTimeMillis), options);
    }

    static long resolveAnimationStep(ArcartRgbEntry entry, long currentTimeMillis) {
        if (entry == null || entry.switchIntervalTicks() <= 0L) {
            return 0L;
        }
        long intervalMillis = entry.switchIntervalTicks() * 50L;
        if (intervalMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, currentTimeMillis / intervalMillis);
    }

    static long resolveAnimationStep(ArcartRgbRenderOptions options, long currentTimeMillis) {
        if (options == null || options.switchIntervalTicks() <= 0L) {
            return 0L;
        }
        long intervalMillis = options.switchIntervalTicks() * 50L;
        if (intervalMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, currentTimeMillis / intervalMillis);
    }

    private static ArcartRgbColor resolveGradientColor(List<ArcartRgbColor> palette, double position, int paletteShift) {
        if (palette.isEmpty()) {
            return new ArcartRgbColor(255, 255, 255);
        }
        if (palette.size() == 1) {
            return palette.get(0);
        }

        double scaled = Math.max(0.0D, Math.min(1.0D, position)) * (palette.size() - 1);
        int leftIndex = (int) Math.floor(scaled);
        int rightIndex = Math.min(palette.size() - 1, leftIndex + 1);
        double progress = scaled - leftIndex;
        ArcartRgbColor left = palette.get(Math.floorMod(leftIndex + paletteShift, palette.size()));
        ArcartRgbColor right = palette.get(Math.floorMod(rightIndex + paletteShift, palette.size()));
        return ArcartRgbColor.lerp(left, right, progress);
    }

    private static int resolveShineCenter(int textLength, boolean shine, ArcartRgbRenderOptions options, long animationStep) {
        if (!shine) {
            return Integer.MIN_VALUE;
        }
        if (options.switchIntervalTicks() <= 0L) {
            return textLength / 2;
        }
        int travelLength = textLength + options.shineWidth() * 2;
        return (int) Math.floorMod(animationStep, Math.max(1, travelLength)) - options.shineWidth();
    }

    private static double resolveShineAmount(int index, int shineCenter, int shineWidth, double shineStrength) {
        int distance = Math.abs(index - shineCenter);
        if (distance > shineWidth) {
            return 0.0D;
        }
        double falloff = 1.0D - ((double) distance / (shineWidth + 1.0D));
        return Math.max(0.0D, Math.min(1.0D, falloff * shineStrength));
    }
}
