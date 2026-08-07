package xuanmo.arcartxsuite.prop.service;

import java.util.Optional;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

/**
 * MythicLib 效果行解析器。
 * <p>
 * 工具类，将 {@code ml|statId:value} 或 {@code mythiclib|statId:value} 格式的效果行
 * 解析为 {@link PropMythicLibEffect}。
 */
public final class PropMythicLibEffectParser {

    private PropMythicLibEffectParser() {
    }

    /**
     * 解析效果行为 {@link PropMythicLibEffect}。
     *
     * @param effectLine 效果行，格式为 {@code type|statId:value}
     * @return 解析成功时包含效果对象，格式无效或数值无法解析时返回空
     */
    public static Optional<PropMythicLibEffect> parse(String effectLine) {
        if (effectLine == null || effectLine.isBlank()) {
            return Optional.empty();
        }

        String[] split = effectLine.split("\\|", 2);
        if (split.length != 2 || !supportsType(split[0])) {
            return Optional.empty();
        }

        String payload = split[1].trim();
        int separatorIndex = payload.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= payload.length() - 1) {
            return Optional.empty();
        }

        String statId = MythicLibStatKeyNormalizer.normalize(payload.substring(0, separatorIndex));
        if (statId.isBlank()) {
            return Optional.empty();
        }

        try {
            double value = Double.parseDouble(payload.substring(separatorIndex + 1).trim());
            return Optional.of(new PropMythicLibEffect(statId, value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * 判断效果类型标识是否为 MythicLib 类型（{@code ml} 或 {@code mythiclib}）。
     *
     * @param rawType 原始类型字符串
     * @return {@code true} 表示支持
     */
    public static boolean supportsType(String rawType) {
        if (rawType == null) {
            return false;
        }
        String normalized = rawType.trim().toLowerCase(java.util.Locale.ROOT);
        return "ml".equals(normalized) || "mythiclib".equals(normalized);
    }
}
