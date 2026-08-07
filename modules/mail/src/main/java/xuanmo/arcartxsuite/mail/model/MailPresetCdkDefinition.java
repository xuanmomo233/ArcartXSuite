package xuanmo.arcartxsuite.mail.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 预设内嵌 CDK 定义，表示某个预设邮件关联的兑换码及其领取限制与过期策略。
 * <p>
 * 关联模块：mail。作为 {@link MailPresetDefinition#cdks()} 的元素。
 *
 * @param code         兑换码
 * @param enabled      是否启用
 * @param maxClaims    最大可领取次数
 * @param expiresAt    绝对过期时间，{@code null} 表示不限
 * @param expiresAfter 相对过期时长，{@code null} 表示不限
 */
public record MailPresetCdkDefinition(
    String code,
    boolean enabled,
    int maxClaims,
    Instant expiresAt,
    Duration expiresAfter
) {
}
