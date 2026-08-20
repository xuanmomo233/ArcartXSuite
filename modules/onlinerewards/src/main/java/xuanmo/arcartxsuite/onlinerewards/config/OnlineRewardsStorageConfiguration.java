package xuanmo.arcartxsuite.onlinerewards.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * 存储层配置。
 * <p>
 * 属于 {@code onlinerewards} 模块，描述存储模式（SQLite/MySQL）及连接参数，
 * 通过 {@link #toDescriptor()} 转换为宿主统一的 {@code StorageDescriptor}。
 *
 * @param shared          是否使用共享存储模式（true=共享本体连接，false=自建连接）
 * @param mode            存储模式（sqlite / mysql），仅在 shared=false 时生效
 * @param sqliteFileName  SQLite 数据库文件名
 * @param host            MySQL 主机地址
 * @param port            MySQL 端口
 * @param database        MySQL 数据库名
 * @param username        MySQL 用户名
 * @param password        MySQL 密码
 * @param tablePrefix     MySQL 表名前缀
 * @param poolSize        连接池大小
 */
public record OnlineRewardsStorageConfiguration(
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
     * 从配置节加载存储配置。
     * <p>
     * shared=true（默认）时使用全部默认值，不读取其余字段；
     * shared=false 时读取 mode/sqlite/mysql 等自建存储字段。
     *
     * @param section storage 配置节，可为 {@code null}
     * @return 解析后的存储配置
     */
    public static OnlineRewardsStorageConfiguration load(@Nullable ConfigurationSection section) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
        boolean shared = section.getBoolean("shared", true);
        if (shared) {
            return new OnlineRewardsStorageConfiguration(true, "sqlite", "online-rewards.db",
                "127.0.0.1", 3306, "arcartxsuite", "root", "",
                "axs_onlinerewards_", 2);
        }
        return new OnlineRewardsStorageConfiguration(false,
            section.getString("mode", "sqlite"),
            section.getString("sqlite.file", "online-rewards.db"),
            section.getString("mysql.host", "127.0.0.1"),
            Math.max(1, section.getInt("mysql.port", 3306)),
            section.getString("mysql.database", "arcartxsuite"),
            section.getString("mysql.username", "root"),
            section.getString("mysql.password", ""),
            section.getString("mysql.table-prefix", "axs_onlinerewards_"),
            Math.max(1, section.getInt("pool-size", 2))
        );
    }

    /**
     * 将本配置转换为宿主统一的存储描述符。
     *
     * @return 对应模式的 {@code StorageDescriptor}
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
     * 当 shared=false 时返回 true（模块独立连接，自建存储）；
     * shared=true 时返回 false（使用本体共享数据源）。
     *
     * @return {@code true} 表示模块应自建连接池
     */
    public boolean hasOverride() {
        return !shared;
    }
}
