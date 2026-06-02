package xuanmo.arcartxsuite.eventpacket.config;

public enum EventPacketPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    EventPacketPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static EventPacketPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        for (EventPacketPersistenceDialect value : values()) {
            if (value.configKey.equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        return SQLITE;
    }
}
