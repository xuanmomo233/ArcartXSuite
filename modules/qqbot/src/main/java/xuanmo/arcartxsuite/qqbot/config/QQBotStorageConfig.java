package xuanmo.arcartxsuite.qqbot.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * QQBot 模块的数据存储配置 record。
 * <p>
 * 关联模块：{@code qqbot}。支持共享存储与自建存储两种模式：
 * <ul>
 *   <li>{@code shared=true}（默认）：共享存储模式，复用本体统一数据源，无需配置连接参数。</li>
 *   <li>{@code shared=false}：自建存储模式，模块自行创建独立 HikariCP 连接池，
 *       支持 SQLite 与 MySQL 两种方言。</li>
 * </ul>
 * 定义连接参数、表名前缀及连接池大小，并可转换为
 * {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor} 供存储层使用。
 *
 * @param shared         是否使用共享存储模式（true=共享本体数据源，false=自建连接池）
 * @param mode           存储模式：{@code sqlite} 或 {@code mysql}（仅 shared=false 时生效）
 * @param sqliteFileName SQLite 数据库文件名（仅 shared=false 时生效）
 * @param host           MySQL 主机地址（仅 shared=false 时生效）
 * @param port           MySQL 端口（仅 shared=false 时生效）
 * @param database       MySQL 数据库名（仅 shared=false 时生效）
 * @param username       MySQL 用户名（仅 shared=false 时生效）
 * @param password       MySQL 密码（仅 shared=false 时生效）
 * @param tablePrefix    MySQL 表名前缀（仅 shared=false 时生效）
 * @param poolSize       连接池大小（仅 shared=false 时生效）
 */
public record QQBotStorageConfig(
    boolean shared,
    /** 存储模式：{@code sqlite} 或 {@code mysql} */
    String mode,
    /** SQLite 数据库文件名 */
    String sqliteFileName,
    /** MySQL 主机地址 */
    String host,
    /** MySQL 端口 */
    int port,
    /** MySQL 数据库名 */
    String database,
    /** MySQL 用户名 */
    String username,
    /** MySQL 密码 */
    String password,
    /** MySQL 表名前缀 */
    String tablePrefix,
    /** 连接池大小 */
    int poolSize
) {
    /**
     * 判断当前存储模式是否为 SQLite（非 mysql 均视为 SQLite）。
     *
     * @return {@code true} 表示使用 SQLite
     */
    public boolean isSqlite() {
        return !"mysql".equalsIgnoreCase(mode);
    }

    /**
     * 将本配置转换为存储描述符，供存储层创建连接。
     *
     * @return 对应的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
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
     * 当 shared=false 时返回 true（模块自建独立连接池）；
     * shared=true 时返回 false（使用本体共享数据源）。
     *
     * @return {@code true} 表示模块应自建连接池
     */
    public boolean hasOverride() {
        return !shared;
    }

    /**
     * 从 YAML storage 节解析存储配置。
     * <p>
     * shared=true（默认）时返回共享模式配置，其余字段使用默认值；
     * shared=false 时读取 mode/sqlite/mysql 等自建存储字段。
     *
     * @param section storage 配置节，为 null 时使用空配置（按默认 shared=true 处理）
     * @return 解析后的 {@link QQBotStorageConfig} 实例
     */
    public static QQBotStorageConfig load(@Nullable ConfigurationSection section) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
        Object sharedValue = section.get("shared");
        if (sharedValue != null && !(sharedValue instanceof Boolean)
            && !(sharedValue instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)))) {
            throw new IllegalArgumentException("storage.shared 必须为 true 或 false");
        }
        boolean shared = sharedValue == null || Boolean.parseBoolean(String.valueOf(sharedValue));
        // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
        if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
            shared = false;
        }
        if (shared) {
            return new QQBotStorageConfig(true, "sqlite", "qqbot.db",
                "127.0.0.1", 3306, "arcartxsuite", "root", "",
                "axs_qqbot_", 4);
        }
        String mode = section.getString("mode");
        if (mode == null || !("mysql".equalsIgnoreCase(mode) || "sqlite".equalsIgnoreCase(mode))) {
            throw new IllegalArgumentException("独立存储必须明确配置 storage.mode: mysql 或 sqlite；请核对原数据库后填写，禁止自动切库");
        }
        String file = section.getString("sqlite.file", "qqbot.db");
        if (file == null || file.isBlank() || file.contains("/") || file.contains("\\") || file.contains(":")) {
            throw new IllegalArgumentException("storage.sqlite.file 必须为模块目录内的文件名");
        }
        String prefix = section.getString("mysql.table-prefix", "axs_qqbot_");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,48}")) throw new IllegalArgumentException("非法的QQBot表前缀");
        int port = section.getInt("mysql.port", 3306);
        int pool = section.getInt("pool-size", 4);
        if (port < 1 || port > 65535 || pool < 1 || pool > 32) throw new IllegalArgumentException("数据库端口或连接池大小超出范围");
        return new QQBotStorageConfig(false,
            mode,
            file,
            section.getString("mysql.host", "127.0.0.1"),
            port,
            section.getString("mysql.database", "arcartxsuite"),
            section.getString("mysql.username", "root"),
            section.getString("mysql.password", ""),
            prefix,
            pool
        );
    }
}
