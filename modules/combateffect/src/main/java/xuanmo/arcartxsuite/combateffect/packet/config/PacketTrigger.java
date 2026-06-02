package xuanmo.arcartxsuite.combateffect.packet.config;

public enum PacketTrigger {
    KILL("kill"),
    ATTACK("attack"),
    DEATH("death"),
    COMBO("combo"),
    MANUAL("manual"),
    KEYBIND("keybind"),
    STATE("state"),
    CONTROLLER("controller");

    private final String configValue;

    PacketTrigger(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static PacketTrigger fromConfig(String value) {
        if (value == null) {
            return null;
        }
        for (PacketTrigger trigger : values()) {
            if (trigger.configValue.equalsIgnoreCase(value.trim())) {
                return trigger;
            }
        }
        return null;
    }
}
