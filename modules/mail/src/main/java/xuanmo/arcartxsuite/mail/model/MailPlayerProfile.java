package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.UUID;

public record MailPlayerProfile(
    UUID playerUuid,
    String lastKnownName,
    Instant lastSendAt,
    Instant lastSeenAt,
    String lastServer
) {
}
