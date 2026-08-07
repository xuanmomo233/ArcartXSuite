package xuanmo.arcartxsuite.eventpacket.config;

/**
 * 事件包存储层配置记录。
 * <p>
 * 封装 SQLite/MySQL 连接所需的全部参数，可通过 {@link #toDescriptor()}
 * 转换为宿主统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
 *
 * @param dialect            持久化方言（SQLite 或 MySQL）
 * @param sqliteFileName     SQLite 数据库文件名
 * @param mysqlHost          MySQL 主机地址
 * @param mysqlPort          MySQL 端口
 * @param mysqlDatabase      MySQL 数据库名
 * @param mysqlUsername      MySQL 用户名
 * @param mysqlPassword      MySQL 密码
 * @param connectionPoolSize 连接池大小
 */
public record EventPacketStorageConfiguration(
    EventPacketPersistenceDialect dialect,
    String sqliteFileName,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int connectionPoolSize
) {
    /**
     * 将此配置转换为宿主存储描述符。
     *
     * @return 根据方言生成的 SQLite 或 MySQL 存储描述符
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == EventPacketPersistenceDialect.MYSQL) {
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
        return dialect == EventPacketPersistenceDialect.MYSQL;
    }
}
