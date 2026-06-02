package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

public enum MailSourceType {
    SYSTEM,
    PLAYER,
    PRESET,
    CDK;

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
