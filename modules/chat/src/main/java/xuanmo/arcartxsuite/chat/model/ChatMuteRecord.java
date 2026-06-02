package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

public record ChatMuteRecord(
    UUID playerUuid,
    String mutedBy,
    String reason,
    Instant createdAt,
    Instant expiresAt
) {

    public boolean active(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public String remainingText(Instant now) {
        if (expiresAt == null) {
            return "永久";
        }
        long remainingMillis = Math.max(0L, expiresAt.toEpochMilli() - now.toEpochMilli());
        long remainingSeconds = remainingMillis / 1000L;
        if (remainingSeconds < 60L) {
            return remainingSeconds + "秒";
        }
        if (remainingSeconds < 3600L) {
            return (remainingSeconds / 60L) + "分";
        }
        if (remainingSeconds < 86400L) {
            return (remainingSeconds / 3600L) + "小时";
        }
        return (remainingSeconds / 86400L) + "天";
    }
}
