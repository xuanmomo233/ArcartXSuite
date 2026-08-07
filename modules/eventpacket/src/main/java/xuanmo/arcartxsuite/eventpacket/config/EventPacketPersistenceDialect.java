package xuanmo.arcartxsuite.eventpacket.config;

/**
 * 事件包持久化方言枚举。
 * <p>
 * 标识当前使用的数据库类型（SQLite 或 MySQL），
 * 供 {@code JdbcEventPacketRepository} 选择对应的 SQL 方言。
 */
public enum EventPacketPersistenceDialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configKey;

    EventPacketPersistenceDialect(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 返回此方言在配置文件中使用的键名。
     *
     * @return 配置键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 从配置字符串解析方言，无法识别或为空时默认返回 {@link #SQLITE}。
     *
     * @param rawValue 原始配置值
     * @return 解析得到的方言枚举
     */
    public static EventPacketPersistenceDialect parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        for (EventPacketPersistenceDialect value : values()) {
            if (value.configKey.equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        return SQLITE;
    }
}
