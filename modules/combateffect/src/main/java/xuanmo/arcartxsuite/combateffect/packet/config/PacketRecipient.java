package xuanmo.arcartxsuite.combateffect.packet.config;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public enum PacketRecipient {
    ATTACKER("attacker"),
    TARGET("target");

    private final String configValue;

    PacketRecipient(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public Player resolve(Player attacker, LivingEntity target) {
        return switch (this) {
            case ATTACKER -> attacker;
            case TARGET -> target instanceof Player p ? p : null;
        };
    }

    public static PacketRecipient fromConfig(String value) {
        if (value == null) {
            return null;
        }
        for (PacketRecipient recipient : values()) {
            if (recipient.configValue.equalsIgnoreCase(value.trim())) {
                return recipient;
            }
        }
        return null;
    }
}
