package xuanmo.arcartxsuite.menu.config;

import java.util.Locale;

public enum MenuActionType {
    COMMAND,
    PLAYER_OP,
    CONSOLE,
    MESSAGE,
    OPEN,
    CLOSE,
    PAGE,
    SOUND,
    SIGNAL,
    NONE;

    public static MenuActionType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "command" -> COMMAND;
            case "op" -> PLAYER_OP;
            case "console" -> CONSOLE;
            case "message" -> MESSAGE;
            case "open" -> OPEN;
            case "close" -> CLOSE;
            case "page" -> PAGE;
            case "sound" -> SOUND;
            case "signal" -> SIGNAL;
            default -> NONE;
        };
    }
}
