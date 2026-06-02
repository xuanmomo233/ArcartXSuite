package xuanmo.arcartxsuite.mail.model;

import java.time.Duration;
import java.util.List;

public record MailPresetDefinition(
    String id,
    boolean enabled,
    String displayName,
    String subject,
    String body,
    Duration expiresAfter,
    List<MailAttachment> attachments,
    List<String> claimCommands,
    List<MailCondition> claimConditions,
    List<MailPresetCdkDefinition> cdks
) {
}
