package xuanmo.arcartxsuite.menu.config;

import java.util.Locale;

public enum MenuActionType {
    COMMAND,
    CONSOLE,
    MESSAGE,
    OPEN,
    CLOSE,
    PAGE,
    SOUND,
    NONE;

    public static MenuActionType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "command", "cmd", "player" -> COMMAND;
            case "console", "op" -> CONSOLE;
            case "message", "msg", "tell" -> MESSAGE;
            case "open", "menu" -> OPEN;
            case "close" -> CLOSE;
            case "page" -> PAGE;
            case "sound" -> SOUND;
            default -> NONE;
        };
    }
}
