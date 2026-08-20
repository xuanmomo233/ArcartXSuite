package xuanmo.arcartxsuite.eventpacket.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * 事件包存储层配置记录。
 * <p>
 * 封装 SQLite/MySQL 连接所需的全部参数，可通过 {@link #toDescriptor()}
 * 转换为宿主统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
 *
 * @param shared          是否共享存储模式（true=共享本体数据源，false=模块自建连接池）
 * @param mode            存储模式（sqlite / mysql），仅在 shared=false 时有意义
 * @param sqliteFileName  SQLite 数据库文件名
 * @param host            MySQL 主机地址
 * @param port            MySQL 端口
 * @param database        MySQL 数据库名
 * @param username        MySQL 用户名
 * @param password        MySQL 密码
 * @param tablePrefix     MySQL 表名前缀
 * @param poolSize        连接池大小
 */
public record EventPacketStorageConfiguration(
    boolean shared,
    String mode,
    String sqliteFileName,
    String host,
    int port,
    String database,
    String username,
    String password,
    String tablePrefix,
    int poolSize
) {
    public boolean isSqlite() {
        return !"mysql".equalsIgnoreCase(mode);
    }

    /**
     * 将此配置转换为宿主存储描述符。
     *
     * @return 根据模式生成的 SQLite 或 MySQL 存储描述符
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (!isSqlite()) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                host, port, database, username, password, poolSize, tablePrefix);
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }

    /**
     * 判断是否为覆盖模式（模块自建连接池）。
     * <p>
     * 当 shared=false 时返回 true（模块独立连接，自建存储模式）；
     * shared=true 时返回 false（使用本体共享数据源）。
     *
     * @return {@code true} 表示模块应自建连接池
     * @since 1.4.0
     */
    public boolean hasOverride() {
        return !shared;
    }

    /**
     * 从配置节加载存储配置。
     * <p>
     * shared=true（默认）时使用默认值，不读 mode/sqlite/mysql；
     * shared=false 时读取自建存储模式的全部字段。
     *
     * @param section storage 配置节，为 {@code null} 时按共享模式处理
     * @return 解析后的存储配置实例
     */
    public static EventPacketStorageConfiguration load(ConfigurationSection section) {
        if (section == null) section = new MemoryConfiguration();
        boolean shared = section.getBoolean("shared", true);
        // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
        if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
            shared = false;
        }
        if (shared) {
            return new EventPacketStorageConfiguration(true, "sqlite", "eventpacket.db",
                "127.0.0.1", 3306, "arcartxsuite", "root", "",
                "axs_eventpacket_", 2);
        }
        return new EventPacketStorageConfiguration(false,
            section.getString("mode", "sqlite"),
            nullToEmpty(section.getString("sqlite.file", "eventpacket.db")).trim(),
            nullToEmpty(section.getString("mysql.host", "127.0.0.1")).trim(),
            Math.max(1, section.getInt("mysql.port", 3306)),
            nullToEmpty(section.getString("mysql.database", "arcartxsuite")).trim(),
            nullToEmpty(section.getString("mysql.username", "root")).trim(),
            nullToEmpty(section.getString("mysql.password", "")).trim(),
            nullToEmpty(section.getString("mysql.table-prefix", "axs_eventpacket_")).trim(),
            Math.max(1, section.getInt("pool-size", 2))
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
