package xuanmo.arcartxsuite.menu.config;

import java.util.Locale;
import java.util.logging.Logger;

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
    SCRIPT_JS,
    SCRIPT_ARIA,
    NONE;

    private static final Logger LOGGER = Logger.getLogger(MenuActionType.class.getName());

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
            case "js" -> SCRIPT_JS;
            case "aria" -> SCRIPT_ARIA;
            default -> {
                LOGGER.warning("未知 Menu 动作关键字，已降级为 NONE: " + raw);
                yield NONE;
            }
        };
    }
}
