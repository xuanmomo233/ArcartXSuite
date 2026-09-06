package xuanmo.arcartxsuite.mail.config;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 玩家发送邮件配置，控制发送开关、冷却、手续费、附件上限与审核策略。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code player-send} 节解析。
 *
 * @param enabled             是否允许玩家发送邮件
 * @param requirePermission   是否需要 {@code arcartxsuite.mail.use} 权限
 * @param cooldownSeconds     发送冷却时间（秒）
 * @param baseFee             每封邮件基础手续费
 * @param itemFee             每个物品附件额外手续费
 * @param feeCurrency         手续费货币 ID
 * @param attachmentTaxRates  各货币附件税率映射（key 为货币 ID 小写）
 * @param allowSelfSend       是否允许给自己发送邮件
 * @param allowOfflineSend    是否允许向离线玩家发送邮件
 * @param allowVaultAttachment 是否允许附加 Vault 货币
 * @param maxAttachments      单封邮件最大附件数
 * @param subjectMaxLength    主题最大长度
 * @param bodyMaxLength       正文最大长度
 * @param vaultDefaultIcon    玩家发送的 Vault 货币附件默认图标定义（与 menu 模块 IconDefinition 配置一致），为 null 时使用默认纸片图标
 */
public record MailPlayerSendConfiguration(
    boolean enabled,
    boolean requirePermission,
    int cooldownSeconds,
    double baseFee,
    double itemFee,
    String feeCurrency,
    Map<String, Double> attachmentTaxRates,
    boolean allowSelfSend,
    boolean allowOfflineSend,
    boolean allowVaultAttachment,
    int maxAttachments,
    int subjectMaxLength,
    int bodyMaxLength,
    @Nullable IconDefinition vaultDefaultIcon
) {
    /**
     * 查询指定货币的附件税率。
     *
     * @param currencyId 货币 ID（大小写不敏感）
     * @return 税率，未配置时返回 {@code 0.0}
     */
    public double attachmentTaxRate(String currencyId) {
        if (attachmentTaxRates == null || attachmentTaxRates.isEmpty()) {
            return 0.0D;
        }
        Double rate = attachmentTaxRates.get(currencyId == null ? "" : currencyId.trim().toLowerCase(java.util.Locale.ROOT));
        return rate == null ? 0.0D : Math.max(0.0D, rate);
    }
}
