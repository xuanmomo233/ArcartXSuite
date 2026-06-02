package xuanmo.arcartxsuite.eventpacket.config;

public enum EventPacketTrigger {
    JOIN("join"),
    FIRST_JOIN("first-join"),
    QUIT("quit"),
    PAPI_INCREASE("papi-increase"),
    PAPI_DECREASE("papi-decrease"),
    PAPI_THRESHOLD("placeholder-threshold"),
    MOB_KILL_COUNT("mob-kill-count"),
    COMMAND_SIGNAL("command-signal"),
    CLIENT_PACKET("client-packet");

    private final String configValue;

    EventPacketTrigger(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public boolean papiTrigger() {
        return this == PAPI_INCREASE || this == PAPI_DECREASE || this == PAPI_THRESHOLD;
    }

    public static EventPacketTrigger parse(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase().replace('_', '-');
        if ("placeholder-increase".equals(normalized)) {
            return PAPI_INCREASE;
        }
        if ("placeholder-decrease".equals(normalized)) {
            return PAPI_DECREASE;
        }
        for (EventPacketTrigger value : values()) {
            if (value.configValue.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
