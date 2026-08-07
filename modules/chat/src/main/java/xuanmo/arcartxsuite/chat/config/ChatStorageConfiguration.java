package xuanmo.arcartxsuite.chat.config;

/**
 * 聊天存储配置，封装 SQLite 文件名或 MySQL 连接参数及连接池大小。
 * <p>
 * 通过 {@link #toDescriptor} 转换为宿主通用的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}，
 * 供 {@link JdbcChatRepository} 创建数据库连接池。
 *
 * @param dialect            持久化方言（SQLite / MySQL）
 * @param sqliteFileName     SQLite 数据库文件名
 * @param mysqlHost          MySQL 主机地址
 * @param mysqlPort          MySQL 端口
 * @param mysqlDatabase      MySQL 数据库名
 * @param mysqlUsername      MySQL 用户名
 * @param mysqlPassword      MySQL 密码
 * @param connectionPoolSize 连接池大小
 */
public record ChatStorageConfiguration(
    ChatPersistenceDialect dialect,
    String sqliteFileName,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int connectionPoolSize
) {
    /**
     * 将本配置转换为宿主通用的存储描述符。
     *
     * @return 对应方言的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == ChatPersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }

    /**
     * 判断是否为覆盖模式（模块自建连接池）。
     * <p>
     * 当 dialect=MYSQL 时返回 true（模块独立连接 MySQL，向后兼容旧配置）；
     * dialect=SQLITE 时返回 false（使用本体共享数据源，各模块仍用各自的 .db 文件）。
     *
     * @return {@code true} 表示模块应自建连接池（旧路径）
     * @since 1.4.0
     */
    public boolean hasOverride() {
        return dialect == ChatPersistenceDialect.MYSQL;
    }
}
