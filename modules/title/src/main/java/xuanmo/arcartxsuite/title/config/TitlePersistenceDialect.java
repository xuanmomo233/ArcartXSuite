package xuanmo.arcartxsuite.title.config;

import java.util.Locale;

public enum TitlePersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    TitlePersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static TitlePersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (TitlePersistenceDialect value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return SQLITE;
    }
}
