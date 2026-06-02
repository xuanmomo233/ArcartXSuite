package xuanmo.arcartxsuite.mail.model;

import java.time.Duration;
import java.time.Instant;

public record MailPresetCdkDefinition(
    String code,
    boolean enabled,
    int maxClaims,
    Instant expiresAt,
    Duration expiresAfter
) {
}
