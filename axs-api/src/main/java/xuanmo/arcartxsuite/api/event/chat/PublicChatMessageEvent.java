package xuanmo.arcartxsuite.api.event.chat;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Main-thread notification after Chat accepts a message for an unrestricted, server-wide public channel.
 * Private, staff, ranged and permission-limited channels never publish this event.
 * Relays must use this event rather than reading cancelled raw chat events.
 */
public final class PublicChatMessageEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String channelId;
    private final String message;
    public PublicChatMessageEvent(Player player, String channelId, String message) {
        this.player = Objects.requireNonNull(player);
        this.channelId = Objects.requireNonNull(channelId);
        this.message = Objects.requireNonNull(message);
    }
    public Player player() { return player; }
    public String channelId() { return channelId; }
    public String message() { return message; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
