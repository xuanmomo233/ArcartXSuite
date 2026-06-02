package xuanmo.arcartxsuite.onlinerewards.config;

public enum OnlineRewardsPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    OnlineRewardsPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static OnlineRewardsPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        for (OnlineRewardsPersistenceDialect value : values()) {
            if (value.configKey.equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        return SQLITE;
    }
}
