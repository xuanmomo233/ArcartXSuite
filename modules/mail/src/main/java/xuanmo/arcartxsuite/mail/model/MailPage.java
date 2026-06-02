package xuanmo.arcartxsuite.mail.model;

import java.util.List;

public record MailPage<T>(
    List<T> entries,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {
    public static <T> MailPage<T> empty(int page, int pageSize) {
        return new MailPage<>(List.of(), Math.max(1, page), Math.max(1, pageSize), 0, 1);
    }
}
