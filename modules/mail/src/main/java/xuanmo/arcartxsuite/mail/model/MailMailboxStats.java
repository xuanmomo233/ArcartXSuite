package xuanmo.arcartxsuite.mail.model;

/**
 * 邮箱统计信息，汇总当前玩家的邮件总数、未读数与可领取数。
 * <p>
 * 关联模块：mail。由 {@link xuanmo.arcartxsuite.mail.service.MailService#loadStats} 查询。
 *
 * @param totalCount     邮件总数
 * @param unreadCount    未读邮件数
 * @param claimableCount 可领取邮件数
 */
public record MailMailboxStats(
    int totalCount,
    int unreadCount,
    int claimableCount
) {
    /**
     * 返回全零的空统计信息。
     *
     * @return 空统计
     */
    public static MailMailboxStats empty() {
        return new MailMailboxStats(0, 0, 0);
    }
}
