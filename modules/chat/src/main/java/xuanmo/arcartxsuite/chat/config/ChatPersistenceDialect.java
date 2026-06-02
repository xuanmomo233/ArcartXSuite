package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;

public enum ChatPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    ChatPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static ChatPersistenceDialect parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return "mysql".equals(normalized) ? MYSQL : SQLITE;
    }
}
