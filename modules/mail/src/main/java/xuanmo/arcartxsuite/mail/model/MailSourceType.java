package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

/**
 * 邮件来源类型枚举，区分系统、玩家、预设与 CDK 发送的邮件。
 * <p>
 * 关联模块：mail。
 */
public enum MailSourceType {
    SYSTEM,
    PLAYER,
    PRESET,
    CDK;

    /**
     * 从配置原始值解析来源类型，空白或无法识别时默认返回 {@link #SYSTEM}。
     *
     * @param rawValue 配置原始字符串
     * @return 解析得到的来源类型
     */
    public static MailSourceType parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SYSTEM;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SYSTEM;
        }
    }
}
