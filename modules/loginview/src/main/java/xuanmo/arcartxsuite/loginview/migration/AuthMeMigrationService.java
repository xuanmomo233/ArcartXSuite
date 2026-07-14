package xuanmo.arcartxsuite.loginview.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration.MigrationConfiguration;
import xuanmo.arcartxsuite.loginview.storage.LoginViewRepository;
import xuanmo.arcartxsuite.loginview.storage.MigratedAuthMeAccount;
import xuanmo.arcartxsuite.api.message.MessageProvider;

public final class AuthMeMigrationService {

    private final MigrationConfiguration configuration;
    private final LoginViewRepository repository;
    private final MessageProvider messages;

    public AuthMeMigrationService(MigrationConfiguration configuration, LoginViewRepository repository,
                                  MessageProvider messages) {
        this.configuration = configuration;
        this.repository = repository;
        this.messages = messages;
    }

    public MigrationResult migrate(CommandSender sender, boolean dryRun) throws SQLException {
        int scanned = 0;
        int imported = 0;
        int skipped = 0;
        try (Connection connection = openSourceConnection();
             PreparedStatement statement = connection.prepareStatement(buildSelectSql());
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                scanned++;
                String playerName = resultSet.getString("player_name");
                String passwordHash = resultSet.getString("password_hash");
                if (blank(playerName) || blank(passwordHash)) {
                    skipped++;
                    continue;
                }
                if (!dryRun) {
                    repository.importAccount(new MigratedAuthMeAccount(
                        playerName,
                        resultSet.getString("real_name"),
                        passwordHash,
                        configuration.importedHashAlgorithm(),
                        resultSet.getString("email"),
                        resultSet.getString("registration_ip"),
                        resultSet.getString("last_ip"),
                        resultSet.getLong("registered_at"),
                        resultSet.getLong("last_login_at")
                    ));
                }
                imported++;
                if (sender != null && imported % configuration.batchSize() == 0) {
                    sender.sendMessage(messages.get("migrate.progress", scanned, imported));
                }
            }
        }
        return new MigrationResult(scanned, imported, skipped, dryRun);
    }

    private Connection openSourceConnection() throws SQLException {
        Properties properties = new Properties();
        if (!blank(configuration.username())) {
            properties.setProperty("user", configuration.username());
        }
        if (!blank(configuration.password())) {
            properties.setProperty("password", configuration.password());
        }
        return properties.isEmpty()
            ? DriverManager.getConnection(configuration.jdbcUrl())
            : DriverManager.getConnection(configuration.jdbcUrl(), properties);
    }

    private String buildSelectSql() {
        return "SELECT "
            + configuration.nameColumn() + " AS player_name,"
            + configuration.realNameColumn() + " AS real_name,"
            + configuration.passwordColumn() + " AS password_hash,"
            + configuration.emailColumn() + " AS email,"
            + configuration.registrationIpColumn() + " AS registration_ip,"
            + configuration.lastIpColumn() + " AS last_ip,"
            + configuration.registrationDateColumn() + " AS registered_at,"
            + configuration.lastLoginColumn() + " AS last_login_at"
            + " FROM " + configuration.table();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record MigrationResult(int scanned, int imported, int skipped, boolean dryRun) {
    }
}
