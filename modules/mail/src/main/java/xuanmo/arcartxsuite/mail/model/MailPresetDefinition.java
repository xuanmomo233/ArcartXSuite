package xuanmo.arcartxsuite.mail.model;

import java.time.Duration;
import java.util.List;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

/**
 * 预设邮件定义，定义一个可重复分发的邮件模板（主题、正文、附件、领取命令与 CDK）。
 * <p>
 * 关联模块：mail。从 {@code presets/} 目录的 yml 文件加载。
 *
 * @param id             预设唯一 ID
 * @param enabled        是否启用
 * @param displayName    显示名称
 * @param subject        主题
 * @param body           正文
 * @param expiresAfter   分发后的过期时长，{@code null} 表示永不过期
 * @param attachments    附件列表
 * @param claimCommands  领取时执行的命令列表
 * @param claimConditions 领取条件列表
 * @param cdks           关联的 CDK 兑换码列表
 */
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
