package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;

public record MailCdkDefinition(
    String code,
    String presetId,
    int maxClaims,
    int claimedCount,
    Instant expiresAt,
    boolean enabled,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean expired(Instant now) {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }
}
