package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 邮件操作日志条目，记录玩家发送、领取、删除等操作的审计信息。
 * <p>
 * 关联模块：mail。存储于 {@code mail_logs} 表。
 *
 * @param id        日志唯一 ID
 * @param playerUuid 玩家 UUID
 * @param type      日志类型（如 {@code send}、{@code claim}、{@code delete}）
 * @param content   日志内容
 * @param createdAt 创建时间
 */
public record MailLogEntry(
    long id,
    UUID playerUuid,
    String type,
    String content,
    Instant createdAt
) {
}
