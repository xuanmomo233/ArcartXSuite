package xuanmo.arcartxsuite.mail.config;

/**
 * CDK 兑换安全配置，控制兑换冷却与失败锁定策略。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code cdk} 节解析。
 *
 * @param requirePermission 是否要求 {@code arcartxsuite.mail.cdk} 权限才能兑换
 * @param cooldownSeconds   每次兑换失败后对该玩家的冷却时间（秒）；0 表示不冷却
 * @param maxFailAttempts   连续失败达到此次数后临时锁定该玩家的 CDK 兑换功能；0 表示不锁定
 * @param lockoutMinutes    触发锁定后的锁定时长（分钟）
 */
public record MailCdkConfiguration(
    boolean requirePermission,
    int cooldownSeconds,
    int maxFailAttempts,
    int lockoutMinutes
) {
}
