package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

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
    public boolean unread() {
        return status == MailStatus.UNREAD;
    }

    public boolean claimable() {
        return status == MailStatus.UNREAD || status == MailStatus.READ;
    }

    public boolean deleted() {
        return status == MailStatus.DELETED;
    }

    public boolean expired() {
        return status == MailStatus.EXPIRED;
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean hasClaimCommands() {
        return claimCommands != null && !claimCommands.isEmpty();
    }
}
