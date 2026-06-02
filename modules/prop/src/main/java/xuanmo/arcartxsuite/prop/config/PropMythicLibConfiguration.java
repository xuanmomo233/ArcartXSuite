package xuanmo.arcartxsuite.prop.config;

import java.util.Locale;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

public record PropMythicLibConfiguration(
    boolean enabled,
    String sourcePrefix
) {
    public PropMythicLibConfiguration {
        sourcePrefix = sanitizePrefix(sourcePrefix);
    }

    public String modifierKey(String propId, String statId) {
        return sourcePrefix + "_" + sanitizeSegment(propId) + "_" + MythicLibStatKeyNormalizer.normalize(statId);
    }

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
