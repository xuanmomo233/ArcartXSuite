package xuanmo.arcartxsuite.title.model;

import java.time.Instant;

public record PlayerOwnedTitle(
    String titleId,
    boolean hidden,
    Instant grantedAt,
    Instant activatesAt,
    Instant expiresAt,
    Instant updatedAt,
    String grantedBy
) {
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isActive(Instant now) {
        return activatesAt == null || !activatesAt.isAfter(now);
    }

    public boolean isEffective(Instant now) {
        return isActive(now) && !isExpired(now);
    }

    public long remainingMillis(Instant now) {
        if (expiresAt == null) {
            return -1L;
        }
        return Math.max(0L, expiresAt.toEpochMilli() - now.toEpochMilli());
    }

    public PlayerOwnedTitle withHidden(boolean hidden, Instant now) {
        return new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy);
    }

    public PlayerOwnedTitle refreshed(Instant grantedAt, Instant activatesAt, Instant expiresAt, Instant now, String grantedBy) {
        return new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy);
    }
}
