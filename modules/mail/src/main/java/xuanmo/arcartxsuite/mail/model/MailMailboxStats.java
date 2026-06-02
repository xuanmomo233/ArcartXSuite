package xuanmo.arcartxsuite.mail.model;

public record MailMailboxStats(
    int totalCount,
    int unreadCount,
    int claimableCount
) {
    public static MailMailboxStats empty() {
        return new MailMailboxStats(0, 0, 0);
    }
}
