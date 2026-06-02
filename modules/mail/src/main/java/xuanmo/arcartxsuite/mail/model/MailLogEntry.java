package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.UUID;

public record MailLogEntry(
    long id,
    UUID playerUuid,
    String type,
    String content,
    Instant createdAt
) {
}
