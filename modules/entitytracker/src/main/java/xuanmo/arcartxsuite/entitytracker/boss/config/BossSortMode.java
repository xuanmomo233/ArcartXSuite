package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

/**
 * Boss 列表排序模式枚举，属于 entitytracker 模块的 boss 追踪子系统。
 * <p>
 * 定义 HUD 中多个 Boss 条目的排列方式：生成顺序、距离、血量百分比、优先级、混合模式。
 */
public enum BossSortMode {
    SPAWN_ORDER("spawn-order"),
    DISTANCE("distance"),
    HEALTH_PERCENT("health-percent"),
    PRIORITY("priority"),
    HYBRID("hybrid");

    private final String configKey;

    BossSortMode(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该模式在配置中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置文本解析排序模式，空值或无法识别时默认返回 {@link #SPAWN_ORDER}。
     *
     * @param rawValue 原始配置文本
     * @return 匹配的排序模式
     */
    public static BossSortMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SPAWN_ORDER;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (BossSortMode mode : values()) {
            if (mode.configKey.equals(normalized)) {
                return mode;
            }
        }
        return SPAWN_ORDER;
    }
}

