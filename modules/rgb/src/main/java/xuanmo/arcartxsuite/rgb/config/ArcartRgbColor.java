package xuanmo.arcartxsuite.rgb.config;

import java.util.Locale;
import java.util.Objects;

public record ArcartRgbColor(int red, int green, int blue) {

    public ArcartRgbColor {
        validateChannel(red, "red");
        validateChannel(green, "green");
        validateChannel(blue, "blue");
    }

    public static ArcartRgbColor parse(String rawValue) {
        String normalized = normalize(rawValue);
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("RGB 颜色必须是 6 位十六进制: " + rawValue);
        }
        try {
            return new ArcartRgbColor(
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("无效的 RGB 颜色值: " + rawValue, exception);
        }
    }

    public static ArcartRgbColor lerp(ArcartRgbColor start, ArcartRgbColor end, double progress) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        double clamped = clamp(progress);
        return new ArcartRgbColor(
            blendChannel(start.red, end.red, clamped),
            blendChannel(start.green, end.green, clamped),
            blendChannel(start.blue, end.blue, clamped)
        );
    }

    public ArcartRgbColor blend(ArcartRgbColor target, double amount) {
        return lerp(this, target, amount);
    }

    public String arcartCode() {
        return "§#" + hex();
    }

    public String hex() {
        return String.format(Locale.ROOT, "%02X%02X%02X", red, green, blue);
    }

    private static String normalize(String rawValue) {
        String normalized = Objects.requireNonNullElse(rawValue, "").trim();
        if (normalized.startsWith("§#")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static int blendChannel(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    private static void validateChannel(int value, String channel) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(channel + " must be between 0 and 255");
        }
    }
}
