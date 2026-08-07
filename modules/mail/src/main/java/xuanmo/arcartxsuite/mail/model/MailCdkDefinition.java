package xuanmo.arcartxsuite.mail.model;

import java.time.Instant;

/**
 * CDK 兑换码定义，记录一个兑换码关联的预设、领取上限、已领取次数与过期时间。
 * <p>
 * 关联模块：mail。由管理命令创建，存储于 {@code mail_cdk_definitions} 表。
 *
 * @param code         兑换码
 * @param presetId     关联的预设邮件 ID
 * @param maxClaims    最大可领取次数
 * @param claimedCount 已领取次数
 * @param expiresAt    过期时间，{@code null} 表示永不过期
 * @param enabled      是否启用
 * @param createdBy    创建者名称
 * @param createdAt    创建时间
 * @param updatedAt    最后更新时间
 */
public record MailCdkDefinition(
    String code,
    String presetId,
    int maxClaims,
    int claimedCount,
    Instant expiresAt,
    boolean enabled,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * 判断该兑换码是否已过期。
     *
     * @param now 当前时间
     * @return {@code true} 表示已过期
     */
    public boolean expired(Instant now) {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }
}
