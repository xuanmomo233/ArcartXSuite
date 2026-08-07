package xuanmo.arcartxsuite.rgb.config;

import java.util.Locale;
import java.util.Objects;

/**
 * RGB 颜色记录，支持 6 位十六进制解析、颜色插值与 ArcartX 颜色码输出。
 *
 * @param red   红色通道（0-255）
 * @param green 绿色通道（0-255）
 * @param blue  蓝色通道（0-255）
 */
public record ArcartRgbColor(int red, int green, int blue) {

    public ArcartRgbColor {
        validateChannel(red, "red");
        validateChannel(green, "green");
        validateChannel(blue, "blue");
    }

    /**
     * 从字符串解析颜色，支持 "#RRGGBB"、"§#RRGGBB"、"RRGGBB" 三种格式。
     *
     * @param rawValue 原始值
     * @return 颜色实例
     * @throws IllegalArgumentException 非法格式或超出通道范围时抛出
     */
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

    /**
     * 在两个颜色之间按进度插值。
     *
     * @param start    起始颜色
     * @param end      结束颜色
     * @param progress 插值进度 [0, 1]，超出会夹取
     * @return 插值后的颜色
     */
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

    /** 返回 ArcartX 客户端识别的颜色码（§#RRGGBB）。 */
    public String arcartCode() {
        return "§#" + hex();
    }

    /** 返回大写十六进制表示（如 "FF00AA"）。 */
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
