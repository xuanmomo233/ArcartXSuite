package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;

/**
 * 聊天频道模式枚举。
 * <p>
 * NORMAL — 普通频道（本地或全图）；GLOBAL — 全局跨服频道；
 * PRIVATE — 私聊频道；STAFF — 管理员频道。
 */
public enum ChatChannelMode {
    NORMAL,
    GLOBAL,
    PRIVATE,
    STAFF;

    /**
     * 将字符串解析为频道模式，无法识别时默认返回 {@link #NORMAL}。
     *
     * @param rawValue 原始字符串（如 "global"、"private"、"staff"）
     * @return 对应的频道模式
     */
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
