package xuanmo.arcartxsuite.onlinerewards.config;

/**
 * 持久化方言枚举。
 * <p>
 * 属于 {@code onlinerewards} 模块，标识存储层使用 SQLite 还是 MySQL，
 * 通过 {@link #parse(String)} 从配置字符串安全解析。
 */
public enum OnlineRewardsPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    OnlineRewardsPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该方言在配置中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置字符串解析方言，无法识别时回退到 {@link #SQLITE}。
     *
     * @param rawValue 原始配置字符串
     * @return 解析得到的方言
     */
    public static OnlineRewardsPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        for (OnlineRewardsPersistenceDialect value : values()) {
            if (value.configKey.equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        return SQLITE;
    }
}
