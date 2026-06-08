package xuanmo.arcartxsuite.combateffect.display.config;

import java.util.Locale;

public enum CombatDisplayDamageSourceMode {
    AUTO,
    MYTHICLIB,
    CRANEATTRIBUTE,
    ATTRIBUTEPLUS,
    SYMPHONY,
    BUKKIT;

    public static CombatDisplayDamageSourceMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AUTO;
        }
        String normalized = rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return CombatDisplayDamageSourceMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}

