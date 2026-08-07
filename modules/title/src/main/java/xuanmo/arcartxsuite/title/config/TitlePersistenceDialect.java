package xuanmo.arcartxsuite.title.config;

import java.util.Locale;

/**
 * 称号模块持久化方言枚举。
 * <p>
 * 标识当前使用的数据库类型（SQLite 或 MySQL），
 * 影响 JDBC 仓储的建表语句与 Upsert 语法。
 */
public enum TitlePersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    TitlePersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    /**
     * @return 该方言在配置中使用的键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 将配置文本解析为持久化方言。
     *
     * @param rawValue 原始配置文本
     * @return 解析结果，无法识别时返回 {@link #SQLITE}
     */
    public static TitlePersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (TitlePersistenceDialect value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return SQLITE;
    }
}
