package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

/**
 * 消息奖励的发送目标枚举，属于 entitytracker 模块的 boss 伤害排名奖励子系统。
 * <p>
 * 定义消息类奖励的接收对象：指定玩家、全服广播、控制台。
 */
public enum BossDamageRewardMessageTarget {
    PLAYER("player"),
    BROADCAST("broadcast"),
    CONSOLE("console");

    private final String configKey;

    BossDamageRewardMessageTarget(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该目标在配置中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置文本解析消息目标，无法识别时默认返回 {@link #PLAYER}。
     *
     * @param rawValue 原始配置文本
     * @return 匹配的目标
     */
    public static BossDamageRewardMessageTarget parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "broadcast", "all" -> BROADCAST;
            case "console" -> CONSOLE;
            default -> PLAYER;
        };
    }
}

