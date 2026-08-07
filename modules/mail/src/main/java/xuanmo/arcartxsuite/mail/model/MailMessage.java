package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

/**
 * 邮件消息，表示一封完整邮件的持久化模型。
 * <p>
 * 关联模块：mail。存储于 {@code mail_entries} 表，附件通过 {@link MailAttachment} 关联。
 *
 * @param id              邮件唯一 ID
 * @param ownerUuid       收件玩家 UUID
 * @param senderUuid      发件玩家 UUID（系统邮件可为 {@code null}）
 * @param senderName      发件人显示名
 * @param sourceType      邮件来源类型
 * @param presetId        关联的预设 ID（仅 {@link MailSourceType#PRESET} 时有效）
 * @param cdkCode         关联的 CDK 兑换码（仅 {@link MailSourceType#CDK} 时有效）
 * @param subject         主题
 * @param body            正文
 * @param status          邮件状态
 * @param attachments     附件列表
 * @param claimCommands   领取时执行的命令列表
 * @param claimConditions 领取条件列表
 * @param createdAt       创建时间
 * @param expiresAt       过期时间，{@code null} 表示永不过期
 * @param updatedAt       最后更新时间
 * @param claimedAt       领取时间
 * @param deletedAt       删除时间
 */
public record MailMessage(
    long id,
    UUID ownerUuid,
    UUID senderUuid,
    String senderName,
    MailSourceType sourceType,
    String presetId,
    String cdkCode,
    String subject,
    String body,
    MailStatus status,
    List<MailAttachment> attachments,
    List<String> claimCommands,
    List<ScriptCondition> claimConditions,
    Instant createdAt,
    Instant expiresAt,
    Instant updatedAt,
    Instant claimedAt,
    Instant deletedAt
) {
    /**
     * 判断该邮件是否未读。
     *
     * @return {@code true} 表示未读
     */
    public boolean unread() {
        return status == MailStatus.UNREAD;
    }

    /**
     * 判断该邮件是否可领取（未读或已读且未领取）。
     *
     * @return {@code true} 表示可领取
     */
    public boolean claimable() {
        return status == MailStatus.UNREAD || status == MailStatus.READ;
    }

    /**
     * 判断该邮件是否已删除。
     *
     * @return {@code true} 表示已删除
     */
    public boolean deleted() {
        return status == MailStatus.DELETED;
    }

    /**
     * 判断该邮件是否已过期。
     *
     * @return {@code true} 表示已过期
     */
    public boolean expired() {
        return status == MailStatus.EXPIRED;
    }

    /**
     * 判断该邮件是否包含附件。
     *
     * @return {@code true} 表示有附件
     */
    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    /**
     * 判断该邮件是否包含领取命令。
     *
     * @return {@code true} 表示有领取命令
     */
    public boolean hasClaimCommands() {
        return claimCommands != null && !claimCommands.isEmpty();
    }
}
