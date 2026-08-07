package xuanmo.arcartxsuite.mail.config;

/**
 * 邮件存储配置，封装 SQLite 文件名或 MySQL 连接信息及连接池大小。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code storage} 节解析，
 * 通过 {@link #toDescriptor()} 转换为引擎统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
 *
 * @param dialect           持久化方言
 * @param sqliteFileName    SQLite 数据库文件名
 * @param mysqlHost         MySQL 主机地址
 * @param mysqlPort         MySQL 端口
 * @param mysqlDatabase     MySQL 数据库名
 * @param mysqlUsername     MySQL 用户名
 * @param mysqlPassword     MySQL 密码
 * @param connectionPoolSize 连接池大小
 */
public record MailStorageConfiguration(
    MailPersistenceDialect dialect,
    String sqliteFileName,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int connectionPoolSize
) {
    /**
     * 将本配置转换为引擎统一的存储描述符。
     *
     * @return 根据 {@link #dialect} 生成的 SQLite 或 MySQL 存储描述符
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == MailPersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }
}
