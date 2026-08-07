package xuanmo.arcartxsuite.chat.config;

import java.util.Locale;

/**
 * 聊天持久化方言枚举，区分 SQLite 与 MySQL 两种存储后端。
 */
public enum ChatPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    ChatPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    /**
     * @return 该方言在配置文件中对应的键名（"sqlite" 或 "mysql"）
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 将字符串解析为持久化方言，无法识别时默认返回 {@link #SQLITE}。
     *
     * @param rawValue 原始字符串（"sqlite" 或 "mysql"）
     * @return 对应的方言
     */
    public static ChatPersistenceDialect parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return "mysql".equals(normalized) ? MYSQL : SQLITE;
    }
}
