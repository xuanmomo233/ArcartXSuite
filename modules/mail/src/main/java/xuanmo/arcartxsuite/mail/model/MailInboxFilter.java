package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

/**
 * 收件箱筛选模式枚举，用于按来源或状态过滤邮件列表。
 * <p>
 * 关联模块：mail。
 */
public enum MailInboxFilter {
    ALL,
    UNREAD,
    CLAIMABLE,
    SYSTEM,
    PLAYER,
    PRESET,
    CDK;

    /**
     * 从配置原始值解析筛选模式，空白或无法识别时默认返回 {@link #ALL}。
     *
     * @param rawValue 配置原始字符串
     * @return 解析得到的筛选模式
     */
    public static MailInboxFilter parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ALL;
        }
    }
}
