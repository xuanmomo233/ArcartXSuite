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
    String sqliteFile,
    /** MySQL 主机地址 */
    String mysqlHost,
    /** MySQL 端口 */
    int mysqlPort,
    /** MySQL 数据库名 */
    String mysqlDatabase,
    /** MySQL 用户名 */
    String mysqlUsername,
    /** MySQL 密码 */
    String mysqlPassword,
    /** MySQL 表名前缀 */
    String mysqlTablePrefix,
    /** 连接池大小 */
    int poolSize
) {
    /**
     * 判断当前存储模式是否为 MySQL。
     *
     * @return {@code true} 表示使用 MySQL
     */
    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(mode);
    }

    /**
     * 将本配置转换为存储描述符，供存储层创建连接。
     *
     * @return 对应的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
     */
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (isMysql()) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, mysqlTablePrefix);
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
    }
}
