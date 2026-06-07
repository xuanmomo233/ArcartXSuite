package xuanmo.arcartxsuite.mail.model;

import java.time.Duration;
import java.util.List;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

public record MailPresetDefinition(
    String id,
    boolean enabled,
    String displayName,
    String subject,
    String body,
    Duration expiresAfter,
    List<MailAttachment> attachments,
    List<String> claimCommands,
    List<ScriptCondition> claimConditions,
    List<MailPresetCdkDefinition> cdks
) {
}
