package xuanmo.arcartxsuite.mail.model;

import java.util.List;

/**
 * 分页结果，封装当前页条目列表与分页元信息。
 * <p>
 * 关联模块：mail。泛型 {@code T} 通常为 {@link MailMessage} 或 {@link MailLogEntry}。
 *
 * @param entries    当前页条目列表
 * @param page       当前页码
 * @param pageSize   每页条数
 * @param totalItems 总条目数
 * @param totalPages 总页数
 * @param <T>        条目类型
 */
public record MailPage<T>(
    List<T> entries,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {
    /**
     * 构造空分页结果。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param <T>      条目类型
     * @return 空分页
     */
    public static <T> MailPage<T> empty(int page, int pageSize) {
        return new MailPage<>(List.of(), Math.max(1, page), Math.max(1, pageSize), 0, 1);
    }
}
