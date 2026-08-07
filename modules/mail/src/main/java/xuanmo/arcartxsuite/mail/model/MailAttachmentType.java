package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

/**
 * 邮件附件类型枚举，区分物品、Vault 货币与自定义货币附件。
 * <p>
 * 关联模块：mail。
 */
public enum MailAttachmentType {
    ITEM,
    VAULT,
    CURRENCY;

    /**
     * 从配置原始值解析附件类型，空白或无法识别时默认返回 {@link #ITEM}。
     *
     * @param rawValue 配置原始字符串
     * @return 解析得到的附件类型
     */
    public static MailAttachmentType parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ITEM;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ITEM;
        }
    }
}
