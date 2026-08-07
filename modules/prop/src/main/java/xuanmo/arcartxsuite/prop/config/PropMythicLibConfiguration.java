package xuanmo.arcartxsuite.prop.config;

import java.util.Locale;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

/**
 * MythicLib 集成配置模型。
 * <p>
 * 从模块主配置的 {@code mythiclib} 节解析，管理属性修改器的来源前缀与键名生成规则。
 *
 * @param sourcePrefix 来源前缀，用于生成修改器键名，默认 {@code "AXS_PROP"}
 */
public record PropMythicLibConfiguration(
    String sourcePrefix
) {
    public PropMythicLibConfiguration {
        sourcePrefix = sanitizePrefix(sourcePrefix);
    }

    /**
     * 生成属性修改器的唯一键。
     *
     * @param propId 道具 ID
     * @param statId 属性标识
     * @return 格式为 {@code 前缀_道具ID_属性ID} 的键名
     */
    public String modifierKey(String propId, String statId) {
        return sourcePrefix + "_" + sanitizeSegment(propId) + "_" + MythicLibStatKeyNormalizer.normalize(statId);
    }

    /**
     * 生成属性修改器的显示名称，当前与 {@link #modifierKey} 一致。
     *
     * @param propId 道具 ID
     * @param statId 属性标识
     * @return 修改器名称
     */
    public String modifierName(String propId, String statId) {
        return modifierKey(propId, statId);
    }

    private static String sanitizePrefix(String sourcePrefix) {
        if (sourcePrefix == null || sourcePrefix.isBlank()) {
            return "AXS_PROP";
        }
        String sanitized = sanitizeSegment(sourcePrefix);
        return sanitized.isBlank() ? "AXS_PROP" : sanitized;
    }

    private static String sanitizeSegment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        String sanitized = rawValue
            .trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_", "")
            .replaceAll("_$", "");
        return sanitized.isBlank() ? "" : sanitized.toUpperCase(Locale.ROOT);
    }
}
