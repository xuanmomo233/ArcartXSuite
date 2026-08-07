package xuanmo.arcartxsuite.onlinerewards.config;

/**
 * 存储层配置。
 * <p>
 * 属于 {@code onlinerewards} 模块，描述数据库方言（SQLite/MySQL）及连接参数，
 * 通过 {@link #toDescriptor()} 转换为宿主统一的 {@code StorageDescriptor}。
 *
 * @param dialect            持久化方言
 * @param sqliteFileName     SQLite 数据库文件名
 * @param mysqlHost          MySQL 主机地址
 * @param mysqlPort          MySQL 端口
 * @param mysqlDatabase      MySQL 数据库名
 * @param mysqlUsername      MySQL 用户名
 * @param mysqlPassword      MySQL 密码
 * @param connectionPoolSize 连接池大小
 */
public record OnlineRewardsStorageConfiguration(
    OnlineRewardsPersistenceDialect dialect,
    String sqliteFileName,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int connectionPoolSize
) {
    /**
     * 将本配置转换为宿主统一的存储描述符。
     *
     * @return 对应方言的 {@code StorageDescriptor}
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == OnlineRewardsPersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }
}
