package xuanmo.arcartxsuite.qqbot.config;

public record QQBotStorageConfig(
    String mode,
    String sqliteFile,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    String mysqlTablePrefix,
    int poolSize
) {
    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(mode);
    }

    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (isMysql()) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, mysqlTablePrefix);
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
    }
}
