package xuanmo.arcartxsuite.mail.config;

/**
 * 邮件保留策略配置，控制过期清理周期、默认过期天数与已领取/已删除邮件的保留时长。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code retention} 节解析。
 *
 * @param cleanupIntervalTicks              定时清理任务执行间隔（tick）
 * @param defaultExpireAfterDays            邮件默认过期天数
 * @param claimedRetentionDays              已领取邮件的保留天数
 * @param deletedRetentionDays              已删除邮件的保留天数
 * @param allowDeleteWithUnclaimedAttachments 是否允许删除含未领取附件的邮件
 */
public record MailRetentionConfiguration(
    long cleanupIntervalTicks,
    int defaultExpireAfterDays,
    int claimedRetentionDays,
    int deletedRetentionDays,
    boolean allowDeleteWithUnclaimedAttachments
) {
}
