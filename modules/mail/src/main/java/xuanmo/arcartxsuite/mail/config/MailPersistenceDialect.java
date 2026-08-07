package xuanmo.arcartxsuite.mail.config;

import java.util.Locale;

/**
 * 邮件持久化方言，标识当前使用的数据库类型（SQLite 或 MySQL）。
 * <p>
 * 关联模块：mail。由 {@code storage.mode} 配置项解析。
 */
public enum MailPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    MailPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回该方言在配置文件中对应的键名。
     *
     * @return 配置键名（如 {@code "sqlite"} 或 {@code "mysql"}）
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置原始值解析方言，空白或无法识别时默认返回 {@link #SQLITE}。
     *
     * @param rawValue 配置原始字符串
     * @return 解析得到的方言枚举
     */
    public static MailPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (MailPersistenceDialect value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return SQLITE;
    }
}
