package xuanmo.arcartxsuite.mail.config;

import java.util.Locale;

public enum MailPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    MailPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static MailPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (MailPersistenceDialect value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return SQLITE;
    }
}
