package xuanmo.arcartxsuite.entitytracker.config;

import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * EntityTracker 存储配置。
 * <p>
 * 封装共享模式开关与 SQLite/MySQL 的连接参数与连接池大小，
 * 可通过 {@link #toDescriptor()} 转换为引擎统一的存储描述符。
 *
 * @param shared          是否使用共享存储模式（true=共享本体数据源，false=自建连接池）
 * @param mode            存储模式（sqlite/mysql），仅在 shared=false 时生效
 * @param sqliteFileName  SQLite 文件名
 * @param host            MySQL 主机地址
 * @param port            MySQL 端口
 * @param database        MySQL 数据库名
 * @param username        MySQL 用户名
 * @param password        MySQL 密码
 * @param tablePrefix     MySQL 表名前缀
 * @param poolSize        连接池大小
 */
public record EntityTrackerStorageConfiguration(
    boolean shared,
    String mode, String sqliteFileName,
    String host, int port, String database,
    String username, String password, String tablePrefix, int poolSize
) {
    public boolean isSqlite() {
        return !"mysql".equalsIgnoreCase(mode);
    }

    public static EntityTrackerStorageConfiguration defaults() {
        return new EntityTrackerStorageConfiguration(true, "sqlite", "entitytracker.db",
            "localhost", 3306, "arcartxsuite", "root", "", "", 5);
    }

    public StorageDescriptor toDescriptor() {
        if (!isSqlite()) {
            return StorageDescriptor.mysql(host, port, database, username, password, poolSize, tablePrefix);
        }
        return StorageDescriptor.sqlite(sqliteFileName);
    }

    /**
     * 判断是否为覆盖模式（模块自建连接池）。
     * <p>
     * 当 shared=false 时返回 true（模块自建独立连接池）；
     * shared=true 时返回 false（使用本体共享数据源）。
     *
     * @return {@code true} 表示模块应自建连接池
     */
    public boolean hasOverride() {
        return !shared;
    }

    /**
     * 从 yml 的 storage 节加载配置。
     * <p>
     * shared=true（默认）时使用全部默认值，不读取其余字段；
     * shared=false 时读取 mode/sqlite/mysql 等自建存储字段。
     *
     * @param section storage 配置节，为 null 时返回默认值（shared=true）
     * @return 解析后的存储配置
     */
    public static EntityTrackerStorageConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        boolean shared = section.getBoolean("shared", true);
        if (shared) {
            return defaults();
        }
        return new EntityTrackerStorageConfiguration(false,
            section.getString("mode", "sqlite"),
            section.getString("sqlite.file", "entitytracker.db"),
            section.getString("mysql.host", "localhost"),
            Math.max(1, section.getInt("mysql.port", 3306)),
            section.getString("mysql.database", "arcartxsuite"),
            section.getString("mysql.username", "root"),
            section.getString("mysql.password", ""),
            section.getString("mysql.table-prefix", ""),
            Math.max(1, section.getInt("pool-size", 5))
        );
    }
}
