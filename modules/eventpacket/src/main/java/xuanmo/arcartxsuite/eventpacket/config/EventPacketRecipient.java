package xuanmo.arcartxsuite.eventpacket.config;

import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Player;

public enum EventPacketRecipient {
    SELF("self"),
    ALL_ONLINE("all-online"),
    OTHERS("others");

    private final String configValue;

    EventPacketRecipient(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public List<Player> resolve(Player subject, Collection<? extends Player> onlinePlayers) {
        return switch (this) {
            case SELF -> subject == null ? List.of() : List.of(subject);
            case ALL_ONLINE -> onlinePlayers.stream()
                .filter(Player::isOnline)
                .map(player -> (Player) player)
                .toList();
            case OTHERS -> subject == null
                ? onlinePlayers.stream()
                    .filter(Player::isOnline)
                    .map(player -> (Player) player)
                    .toList()
                : onlinePlayers.stream()
                    .filter(Player::isOnline)
                    .filter(player -> !player.getUniqueId().equals(subject.getUniqueId()))
                    .map(player -> (Player) player)
                    .toList();
        };
    }

    public static EventPacketRecipient parse(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase().replace('_', '-');
        return switch (normalized) {
            case "self", "subject", "player" -> SELF;
            case "all-online", "all" -> ALL_ONLINE;
            case "others", "other-online", "other-players" -> OTHERS;
            default -> null;
        };
    }
}
