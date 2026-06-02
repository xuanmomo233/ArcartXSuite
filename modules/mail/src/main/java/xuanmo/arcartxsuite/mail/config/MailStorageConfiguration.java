package xuanmo.arcartxsuite.mail.config;

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
    public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
        if (dialect == MailPersistenceDialect.MYSQL) {
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
        }
        return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
    }
}
