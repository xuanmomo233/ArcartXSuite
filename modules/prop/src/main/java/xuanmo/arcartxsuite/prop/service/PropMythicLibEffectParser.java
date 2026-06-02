package xuanmo.arcartxsuite.prop.service;

import java.util.Optional;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;

public final class PropMythicLibEffectParser {

    private PropMythicLibEffectParser() {
    }

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

    public static boolean supportsType(String rawType) {
        if (rawType == null) {
            return false;
        }
        String normalized = rawType.trim().toLowerCase(java.util.Locale.ROOT);
        return "ml".equals(normalized) || "mythiclib".equals(normalized);
    }
}
