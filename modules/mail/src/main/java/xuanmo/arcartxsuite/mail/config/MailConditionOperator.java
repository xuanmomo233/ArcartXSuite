package xuanmo.arcartxsuite.mail.config;

import java.util.Locale;

public enum MailConditionOperator {
    EQ("eq"),
    NE("ne"),
    CONTAINS("contains"),
    REGEX("regex"),
    GTE("gte"),
    LTE("lte");

    private final String configKey;

    MailConditionOperator(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static MailConditionOperator parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return EQ;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (MailConditionOperator value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return EQ;
    }
}
