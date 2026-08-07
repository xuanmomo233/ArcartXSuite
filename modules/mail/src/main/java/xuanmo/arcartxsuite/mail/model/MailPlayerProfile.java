package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 玩家邮件档案，记录玩家最近已知名称、最后发送/在线时间与所在服务器。
 * <p>
 * 关联模块：mail。存储于 {@code mail_player_profiles} 表。
 *
 * @param playerUuid    玩家 UUID
 * @param lastKnownName 最近已知名称
 * @param lastSendAt    最后发送邮件时间
 * @param lastSeenAt    最后在线时间
 * @param lastServer    最后所在服务器标识
 */
public record MailPlayerProfile(
    UUID playerUuid,
    String lastKnownName,
    Instant lastSendAt,
    Instant lastSeenAt,
    String lastServer
) {
}
