package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

public enum BossDamageRewardMessageTarget {
    PLAYER("player"),
    BROADCAST("broadcast"),
    CONSOLE("console");

    private final String configKey;

    BossDamageRewardMessageTarget(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static BossDamageRewardMessageTarget parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "broadcast", "all" -> BROADCAST;
            case "console" -> CONSOLE;
            default -> PLAYER;
        };
    }
}

