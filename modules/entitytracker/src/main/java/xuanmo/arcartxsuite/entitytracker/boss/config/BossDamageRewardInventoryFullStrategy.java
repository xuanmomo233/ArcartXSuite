package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

/**
 * 背包满时的奖励处理策略枚举，属于 entitytracker 模块的 boss 伤害排名奖励子系统。
 * <p>
 * 定义当玩家背包空间不足时奖励物品的处理方式：
 * 掉落地面、发放失败、静默丢弃。
 */
public enum BossDamageRewardInventoryFullStrategy {
    DROP("drop"),
    FAIL("fail"),
    SILENT_DROP("silent-drop");

    private final String configKey;

    BossDamageRewardInventoryFullStrategy(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该策略在配置中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置文本解析背包满策略，无法识别时默认返回 {@link #DROP}。
     *
     * @param rawValue 原始配置文本
     * @return 匹配的策略
     */
    public static BossDamageRewardInventoryFullStrategy parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fail" -> FAIL;
            case "silent-drop", "silent_drop", "silentdrop" -> SILENT_DROP;
            default -> DROP;
        };
    }
}

