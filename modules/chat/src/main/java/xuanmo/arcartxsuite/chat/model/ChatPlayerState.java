package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record ChatPlayerState(
    UUID playerUuid,
    String currentChannelId,
    boolean acceptsPrivateMessages,
    boolean acceptsMentions,
    boolean socialSpyEnabled,
    Set<UUID> ignoredPlayers,
    Instant updatedAt
) {

    public ChatPlayerState {
        ignoredPlayers = Collections.unmodifiableSet(new LinkedHashSet<>(ignoredPlayers == null ? Set.of() : ignoredPlayers));
    }

    public static ChatPlayerState createDefault(UUID playerUuid, String defaultChannelId) {
        return new ChatPlayerState(playerUuid, defaultChannelId, true, true, false, Set.of(), Instant.now());
    }

    public ChatPlayerState withCurrentChannel(String currentChannelId, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    public ChatPlayerState withAcceptsPrivateMessages(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, value, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    public ChatPlayerState withAcceptsMentions(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, value, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    public ChatPlayerState withSocialSpyEnabled(boolean value, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, value, ignoredPlayers, updatedAt);
    }

    public ChatPlayerState withIgnoredPlayers(Set<UUID> ignoredPlayers, Instant updatedAt) {
        return new ChatPlayerState(playerUuid, currentChannelId, acceptsPrivateMessages, acceptsMentions, socialSpyEnabled, ignoredPlayers, updatedAt);
    }

    public boolean ignores(UUID targetUuid) {
        return targetUuid != null && ignoredPlayers.contains(targetUuid);
    }
}
