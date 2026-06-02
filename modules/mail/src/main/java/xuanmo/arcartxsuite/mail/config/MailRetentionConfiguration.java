package xuanmo.arcartxsuite.mail.config;

public record MailRetentionConfiguration(
    long cleanupIntervalTicks,
    int defaultExpireAfterDays,
    int claimedRetentionDays,
    int deletedRetentionDays,
    boolean allowDeleteWithUnclaimedAttachments
) {
}
