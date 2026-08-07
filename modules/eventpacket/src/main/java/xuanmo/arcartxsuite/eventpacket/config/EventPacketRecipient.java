package xuanmo.arcartxsuite.eventpacket.config;

import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * 事件包动作的目标接收者枚举。
 * <p>
 * 定义信号/动作分发的玩家范围：仅自身、全部在线玩家或除自身外的在线玩家。
 */
public enum EventPacketRecipient {
    SELF("self"),
    ALL_ONLINE("all-online"),
    OTHERS("others");

    private final String configValue;

    EventPacketRecipient(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 返回此接收者在配置文件中使用的值。
     *
     * @return 配置值字符串
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 根据接收者类型从在线玩家集合中解析实际目标玩家列表。
     *
     * @param subject       触发主体玩家，可为 {@code null}
     * @param onlinePlayers 当前在线玩家集合
     * @return 解析后的目标玩家列表
     */
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

    /**
     * 从配置字符串解析接收者类型，支持多种别名写法。
     *
     * @param raw 原始配置值
     * @return 解析得到的接收者枚举，无法识别时返回 {@code null}
     */
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
