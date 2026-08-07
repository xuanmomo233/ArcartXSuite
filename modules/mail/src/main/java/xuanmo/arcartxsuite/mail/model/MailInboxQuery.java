package xuanmo.arcartxsuite.mail.model;

/**
 * 收件箱分页查询参数，封装筛选模式、页码与每页条数。
 * <p>
 * 关联模块：mail。紧凑构造器会对参数做防御性规范化。
 *
 * @param filter   筛选模式，{@code null} 时视为 {@link MailInboxFilter#ALL}
 * @param page     页码（最小 1）
 * @param pageSize 每页条数（最小 1）
 */
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

    /**
     * 切换筛选模式并重置到第 1 页。
     *
     * @param nextFilter 新的筛选模式
     * @return 新的查询参数
     */
    public MailInboxQuery withFilter(MailInboxFilter nextFilter) {
        return new MailInboxQuery(nextFilter, 1, pageSize);
    }

    /**
     * 切换页码，保持筛选模式与每页条数不变。
     *
     * @param nextPage 新页码
     * @return 新的查询参数
     */
    public MailInboxQuery withPage(int nextPage) {
        return new MailInboxQuery(filter, nextPage, pageSize);
    }
}
