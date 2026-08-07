package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 玩家禁言记录，记录禁言操作的全部元数据。
 * <p>
 * 由 {@link ChatRepository} 持久化，{@link ChatService} 在内存中缓存以供实时判定。
 *
 * @param playerUuid 被禁言玩家 UUID
 * @param mutedBy    执行禁言的操作者名称
 * @param reason     禁言原因
 * @param createdAt  禁言创建时间
 * @param expiresAt  禁言过期时间，{@code null} 表示永久禁言
 */
public record ChatMuteRecord(
    UUID playerUuid,
    String mutedBy,
    String reason,
    Instant createdAt,
    Instant expiresAt
) {

    /**
     * 判断禁言在指定时间点是否仍然生效。
     *
     * @param now 判定时间点
     * @return {@code true} 表示未过期（永久禁言始终返回 {@code true}）
     */
    public boolean active(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    /**
     * 返回剩余禁言时长的中文可读文本（如 "30分"、"永久"）。
     *
     * @param now 判定时间点
     * @return 剩余时间描述字符串
     */
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
