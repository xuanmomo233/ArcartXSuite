package xuanmo.arcartxsuite.menu.config;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * 菜单动作类型枚举，定义按钮点击时可执行的动作种类。
 * <p>
 * 包括执行命令、发送消息、打开/关闭菜单、翻页、播放音效、分发信号、运行脚本等。
 */
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

    /**
     * 将文本关键字解析为动作类型，未知关键字降级为 {@link #NONE} 并记录警告。
     *
     * @param raw    原始关键字文本
     * @param logger 模块 logger，用于记录未知关键字警告
     * @return 对应的动作类型枚举值
     */
    public static MenuActionType parse(String raw, Logger logger) {
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
                if (logger != null) {
                    logger.warning("未知 Menu 动作关键字，已降级为 NONE: " + raw);
                }
                yield NONE;
            }
        };
    }
}
