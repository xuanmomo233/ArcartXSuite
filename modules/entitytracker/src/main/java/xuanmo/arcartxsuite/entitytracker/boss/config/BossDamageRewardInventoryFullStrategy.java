package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

public enum BossDamageRewardInventoryFullStrategy {
    DROP("drop"),
    FAIL("fail"),
    SILENT_DROP("silent-drop");

    private final String configKey;

    BossDamageRewardInventoryFullStrategy(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static BossDamageRewardInventoryFullStrategy parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fail" -> FAIL;
            case "silent-drop", "silent_drop", "silentdrop" -> SILENT_DROP;
            default -> DROP;
        };
    }
}

