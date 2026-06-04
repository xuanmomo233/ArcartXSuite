package xuanmo.arcartxsuite.loginview.storage;

import java.util.UUID;

public record LoginViewSession(
    UUID uuid,
    String playerName,
    String ip,
    long createdAt,
    long expiresAt
) {}
