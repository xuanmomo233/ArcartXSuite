package xuanmo.arcartxsuite.title.config;

import java.util.Locale;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

public record TitleMythicLibConfiguration(
    boolean enabled,
    String sourcePrefix
) {
    public TitleMythicLibConfiguration {
        sourcePrefix = sanitizePrefix(sourcePrefix);
    }

    public String displayModifierName(String statId) {
        return sourcePrefix + "_DISPLAY_" + MythicLibStatKeyNormalizer.normalize(statId);
    }

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
