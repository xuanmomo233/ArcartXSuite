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
    CLIENT_PACKET("client-packet"),
    QUEST_ACCEPT("quest-accept"),
    QUEST_COMPLETE("quest-complete"),
    QUEST_FAIL("quest-fail"),
    OBJECTIVE_COMPLETE("objective-complete"),
    OBJECTIVE_CONTINUE("objective-continue"),
    OBJECTIVE_RESTART("objective-restart"),
    CHEMDAH_LEVEL_CHANGE("chemdah-level-change"),
    SCRIPT_JS("script-js"),
    SCRIPT_ARIA("script-aria");

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

    public boolean chemdahTrigger() {
        return this == QUEST_ACCEPT || this == QUEST_COMPLETE || this == QUEST_FAIL
            || this == OBJECTIVE_COMPLETE || this == OBJECTIVE_CONTINUE || this == OBJECTIVE_RESTART
            || this == CHEMDAH_LEVEL_CHANGE;
    }

    public boolean signalFilterable() {
        return this == COMMAND_SIGNAL || chemdahTrigger();
    }

    public boolean scriptTrigger() {
        return this == SCRIPT_JS || this == SCRIPT_ARIA;
    }

    public boolean isObjectiveTrigger() {
        return this == OBJECTIVE_COMPLETE || this == OBJECTIVE_CONTINUE || this == OBJECTIVE_RESTART;
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
