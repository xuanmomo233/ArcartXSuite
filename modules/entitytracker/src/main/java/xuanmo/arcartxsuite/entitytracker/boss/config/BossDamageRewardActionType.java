package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

/**
 * 奖励动作类型枚举，属于 entitytracker 模块的 boss 伤害排名奖励子系统。
 * <p>
 * 定义可用的奖励发放方式：物品发放（NeigeItems/MythicItems/Overture）、
 * 命令执行、消息发送、邮件发送、信号派发。
 */
public enum BossDamageRewardActionType {
    NEIGE_ITEMS("neigeitems"),
    MYTHIC_ITEMS("mythicitems"),
    OVERTURE_ITEMS("overture"),
    COMMAND("command"),
    MESSAGE("message"),
    MAIL("mail"),
    SIGNAL("signal");

    private final String configKey;

    BossDamageRewardActionType(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该类型在配置中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置文本解析奖励动作类型，支持常见别名。
     *
     * @param rawValue 原始配置文本
     * @return 匹配的类型，无法识别时返回 {@code null}
     */
    public static BossDamageRewardActionType parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "neigeitems", "neigeitem" -> NEIGE_ITEMS;
            case "mythicitems", "mythicitem" -> MYTHIC_ITEMS;
            case "overture", "overtureitems", "overtureitem" -> OVERTURE_ITEMS;
            case "command", "cmd" -> COMMAND;
            case "message", "msg" -> MESSAGE;
            case "mail" -> MAIL;
            case "signal" -> SIGNAL;
            default -> null;
        };
    }
}

