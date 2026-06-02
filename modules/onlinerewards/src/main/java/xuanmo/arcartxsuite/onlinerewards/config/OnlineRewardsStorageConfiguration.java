package xuanmo.arcartxsuite.onlinerewards.config;

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
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == OnlineRewardsPersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }
}
