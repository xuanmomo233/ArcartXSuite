package xuanmo.arcartxsuite.mail.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * 邮件存储配置，封装 SQLite 文件名或 MySQL 连接信息及连接池大小。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code storage} 节解析，
 * 通过 {@link #toDescriptor()} 转换为引擎统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
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
public record MailStorageConfiguration(
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
     * 将本配置转换为引擎统一的存储描述符。
     *
     * @return 根据 {@link #mode} 生成的 SQLite 或 MySQL 存储描述符
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
    public static MailStorageConfiguration load(ConfigurationSection section) {
        if (section == null) section = new MemoryConfiguration();
        boolean shared = section.getBoolean("shared", true);
        // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
        if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
            shared = false;
        }
        if (shared) {
            return new MailStorageConfiguration(true, "sqlite", "mail.db",
                "127.0.0.1", 3306, "arcartxsuite", "root", "",
                "axs_mail_", 4);
        }
        return new MailStorageConfiguration(false,
            section.getString("mode", "sqlite"),
            string(section.getString("sqlite.file", "mail.db")),
            string(section.getString("mysql.host", "127.0.0.1")),
            Math.max(1, section.getInt("mysql.port", 3306)),
            string(section.getString("mysql.database", "arcartxsuite")),
            string(section.getString("mysql.username", "root")),
            string(section.getString("mysql.password", "")),
            string(section.getString("mysql.table-prefix", "axs_mail_")),
            Math.max(1, section.getInt("pool-size", 4))
        );
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }
}
