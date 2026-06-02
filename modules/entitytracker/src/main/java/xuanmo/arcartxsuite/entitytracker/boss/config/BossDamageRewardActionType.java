package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

public enum BossDamageRewardActionType {
    NEIGE_ITEMS("neigeitems"),
    MYTHIC_ITEMS("mythicitems"),
    OVERTURE_ITEMS("overture"),
    COMMAND("command"),
    MESSAGE("message"),
    MAIL("mail"),
    SIGNAL("signal");

    private final String configKey;

    BossDamageRewardActionType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static BossDamageRewardActionType parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "neigeitems", "neigeitem" -> NEIGE_ITEMS;
            case "mythicitems", "mythicitem" -> MYTHIC_ITEMS;
            case "overture", "overtureitems", "overtureitem" -> OVERTURE_ITEMS;
            case "command", "cmd" -> COMMAND;
            case "message", "msg" -> MESSAGE;
            case "mail" -> MAIL;
            case "signal" -> SIGNAL;
            default -> null;
        };
    }
}

