package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

public record ChatPlayerProfile(
    UUID playerUuid,
    String lastKnownName,
    Instant lastSeenAt,
    String lastServer
) {
}
