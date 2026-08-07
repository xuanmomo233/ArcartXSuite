package xuanmo.arcartxsuite.title.config;

/**
 * 称号模块存储配置。
 * <p>
 * 封装 SQLite/MySQL 的连接参数与连接池大小，
 * 可通过 {@link #toDescriptor()} 转换为引擎统一的存储描述符。
 *
 * @param dialect            持久化方言
 * @param sqliteFileName     SQLite 文件名
 * @param mysqlHost          MySQL 主机地址
 * @param mysqlPort          MySQL 端口
 * @param mysqlDatabase      MySQL 数据库名
 * @param mysqlUsername      MySQL 用户名
 * @param mysqlPassword      MySQL 密码
 * @param connectionPoolSize 连接池大小
 */
public record TitleStorageConfiguration(
    TitlePersistenceDialect dialect,
    String sqliteFileName,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int connectionPoolSize
) {
    /**
     * 将存储配置转换为引擎统一的存储描述符。
     *
     * @return 根据 {@link #dialect} 生成对应的 SQLite 或 MySQL 描述符
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == TitlePersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }
}
