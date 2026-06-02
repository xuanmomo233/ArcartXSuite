package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

public enum MailInboxFilter {
    ALL,
    UNREAD,
    CLAIMABLE,
    SYSTEM,
    PLAYER,
    PRESET,
    CDK;

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
