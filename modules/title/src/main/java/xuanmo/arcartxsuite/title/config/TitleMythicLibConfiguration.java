package xuanmo.arcartxsuite.title.config;

import java.util.Locale;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

/**
 * MythicLib 属性桥接配置。
 * <p>
 * 定义称号在 MythicLib 中使用的属性修饰器名称前缀，
 * 并根据 statId 生成展示与收集两种修饰器名称。
 *
 * @param sourcePrefix 修饰器名称前缀，为空时默认 {@code "AXS_TITLE"}，非字母数字字符会被替换为下划线
 */
public record TitleMythicLibConfiguration(
    String sourcePrefix
) {
    public TitleMythicLibConfiguration {
        sourcePrefix = sanitizePrefix(sourcePrefix);
    }

    /**
     * @param statId MythicLib 属性键
     * @return 展示属性的修饰器名称（前缀 + {@code _DISPLAY_} + 规范化 statId）
     */
    public String displayModifierName(String statId) {
        return sourcePrefix + "_DISPLAY_" + MythicLibStatKeyNormalizer.normalize(statId);
    }

    /**
     * @param statId MythicLib 属性键
     * @return 收集属性的修饰器名称（前缀 + {@code _COLLECTION_} + 规范化 statId）
     */
    public String collectionModifierName(String statId) {
        return sourcePrefix + "_COLLECTION_" + MythicLibStatKeyNormalizer.normalize(statId);
    }

    private static String sanitizePrefix(String sourcePrefix) {
        if (sourcePrefix == null || sourcePrefix.isBlank()) {
            return "AXS_TITLE";
        }
        String sanitized = sourcePrefix
            .trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_", "")
            .replaceAll("_$", "");
        return sanitized.isBlank() ? "AXS_TITLE" : sanitized.toUpperCase(Locale.ROOT);
    }
}
