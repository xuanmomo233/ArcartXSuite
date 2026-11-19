package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Server-side bridge for moderated messages from a trusted external chat connector. */
@PublicCapability
public interface ChatExternalRelay {
    /**
     * Submit on the server thread. The stage completes there after moderation, routing and local delivery.
     * True indicates a processed message, including when nobody was eligible to receive it.
     * False indicates rejection, unavailability or retirement.
     */
    @NotNull CompletionStage<Boolean> receive(String channelId, @Nullable UUID linkedPlayerId, String format,
                                             String message, Set<UUID> mentions);
}
