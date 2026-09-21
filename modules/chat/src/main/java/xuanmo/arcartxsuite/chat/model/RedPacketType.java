package xuanmo.arcartxsuite.chat.model;

import java.util.Locale;

/**
 * 红包类型。
 * <p>
 * {@link #NORMAL} 普通红包（货币均分）、{@link #LUCKY} 拼手气红包（货币随机拆分）、
 * {@link #POOL} 预设奖励池红包（货币/物品/指令混合，池内随机抽取）。
 */
public enum RedPacketType {
    /** 普通红包：总金额按份数均分。 */
    NORMAL,
    /** 拼手气红包：总金额按二倍均值法随机拆分。 */
    LUCKY,
    /** 奖励池红包：从预设奖励池中随机抽取奖励。 */
    POOL;

    /**
     * 按名称解析红包类型（不区分大小写，支持常用别名）。
     *
     * @param rawValue 原始输入
     * @return 红包类型，无法识别时返回 {@code null}
     */
    public static RedPacketType parse(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "normal", "common", "even" -> NORMAL;
            case "lucky", "random", "luck" -> LUCKY;
            case "pool", "preset" -> POOL;
            default -> null;
        };
    }
}
