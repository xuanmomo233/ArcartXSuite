package xuanmo.arcartxsuite.mail.model;

public record MailInboxQuery(
    MailInboxFilter filter,
    int page,
    int pageSize
) {
    public MailInboxQuery {
        filter = filter == null ? MailInboxFilter.ALL : filter;
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
    }

    public MailInboxQuery withFilter(MailInboxFilter nextFilter) {
        return new MailInboxQuery(nextFilter, 1, pageSize);
    }

    public MailInboxQuery withPage(int nextPage) {
        return new MailInboxQuery(filter, nextPage, pageSize);
    }
}
