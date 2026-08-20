package xuanmo.arcartxsuite.title.config;

/**
 * 称号模块存储配置。
 * <p>
 * 封装 SQLite/MySQL 的连接参数与连接池大小，
 * 可通过 {@link #toDescriptor()} 转换为引擎统一的存储描述符。
 *
 * @param mode            存储模式（sqlite / mysql）
 * @param sqliteFileName  SQLite 文件名
 * @param host            MySQL 主机地址
 * @param port            MySQL 端口
 * @param database        MySQL 数据库名
 * @param username        MySQL 用户名
 * @param password        MySQL 密码
 * @param tablePrefix     MySQL 表名前缀
 * @param poolSize        连接池大小
 */
public record TitleStorageConfiguration(
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
     * 将存储配置转换为引擎统一的存储描述符。
     *
     * @return 根据 {@link #mode} 生成对应的 SQLite 或 MySQL 描述符
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
     * 当 mode=mysql 时返回 true（模块独立连接 MySQL，向后兼容旧配置）；
     * mode=sqlite 时返回 false（使用本体共享数据源，各模块仍用各自的 .db 文件）。
     *
     * @return {@code true} 表示模块应自建连接池（旧路径）
     * @since 1.4.0
     */
    public boolean hasOverride() {
        return "mysql".equalsIgnoreCase(mode);
    }
}
