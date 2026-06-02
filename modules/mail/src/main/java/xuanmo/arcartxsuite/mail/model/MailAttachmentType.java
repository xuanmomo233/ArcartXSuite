package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

public enum MailAttachmentType {
    ITEM,
    VAULT,
    CURRENCY;

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
