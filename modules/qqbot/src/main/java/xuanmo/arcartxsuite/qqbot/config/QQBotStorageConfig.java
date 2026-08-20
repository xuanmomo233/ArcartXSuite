package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的数据存储配置 record。
 * <p>
 * 关联模块：{@code qqbot}。支持 SQLite 与 MySQL 两种模式，
 * 定义连接参数、表名前缀及连接池大小，并可转换为
 * {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor} 供存储层使用。
 */
public record QQBotStorageConfig(
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
