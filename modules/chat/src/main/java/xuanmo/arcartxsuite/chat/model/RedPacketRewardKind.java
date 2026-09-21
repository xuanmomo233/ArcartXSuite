package xuanmo.arcartxsuite.chat.model;

import java.util.Locale;

/**
 * 红包奖励种类。
 */
public enum RedPacketRewardKind {
    /** 货币奖励（通过 CurrencyBridge 入账）。 */
    CURRENCY,
    /** 物品奖励（Base64 序列化 ItemStack，经 ItemRewardDispatcher 发放）。 */
    ITEM,
    /** 指令奖励（控制台执行，{player} 占位符替换为领取者名）。 */
    COMMAND;

    /**
     * 按名称解析奖励种类（不区分大小写）。
     *
     * @param rawValue 原始输入
     * @return 奖励种类，无法识别时返回 {@code null}
     */
    public static RedPacketRewardKind parse(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
