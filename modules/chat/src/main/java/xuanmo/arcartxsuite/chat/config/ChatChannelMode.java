package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;

public enum ChatChannelMode {
    NORMAL,
    GLOBAL,
    PRIVATE,
    STAFF;

    public static ChatChannelMode parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "global" -> GLOBAL;
            case "private" -> PRIVATE;
            case "staff" -> STAFF;
            default -> NORMAL;
        };
    }
}
