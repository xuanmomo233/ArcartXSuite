package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

public enum BossSortMode {
    SPAWN_ORDER("spawn-order"),
    DISTANCE("distance"),
    HEALTH_PERCENT("health-percent"),
    PRIORITY("priority"),
    HYBRID("hybrid");

    private final String configKey;

    BossSortMode(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static BossSortMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SPAWN_ORDER;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (BossSortMode mode : values()) {
            if (mode.configKey.equals(normalized)) {
                return mode;
            }
        }
        return SPAWN_ORDER;
    }
}

