package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

/**
 * 邮件状态枚举，表示邮件的生命周期阶段。
 * <p>
 * 关联模块：mail。
 */
public enum MailStatus {
    UNREAD,
    READ,
    CLAIM_PENDING,
    CLAIM_REVIEW,
    CLAIMED,
    DELETED,
    EXPIRED;

    /**
     * 从配置原始值解析邮件状态，空白或无法识别时默认返回 {@link #UNREAD}。
     *
     * @param rawValue 配置原始字符串
     * @return 解析得到的状态
     */
    public static MailStatus parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UNREAD;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNREAD;
        }
    }
}
