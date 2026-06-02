package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

public enum MailStatus {
    UNREAD,
    READ,
    CLAIMED,
    DELETED,
    EXPIRED;

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
