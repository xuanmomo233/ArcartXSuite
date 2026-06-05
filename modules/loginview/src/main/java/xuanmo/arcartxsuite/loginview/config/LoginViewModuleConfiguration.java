package xuanmo.arcartxsuite.loginview.config;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record LoginViewModuleConfiguration(
    boolean debug,
    AuthMode authMode,
    BypassWelcomeConfiguration bypassWelcome,
    QqBindingConfiguration qqBinding,
    UiConfiguration ui,
    TermsOfServiceConfiguration termsOfService,
    SecurityConfiguration security,
    StorageConfiguration storage,
    MigrationConfiguration migration,
    Messages messages
) {

    public static LoginViewModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        ConfigurationSection securitySection = configuration.getConfigurationSection("security");
        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        ConfigurationSection migrationSection = configuration.getConfigurationSection("authme-migration");
        ConfigurationSection messagesSection = configuration.getConfigurationSection("messages");
        ConfigurationSection bypassWelcomeSection = configuration.getConfigurationSection("auth.bypass-welcome");
        ConfigurationSection qqBindingSection = configuration.getConfigurationSection("qq-binding");
        ConfigurationSection termsOfServiceSection = configuration.getConfigurationSection("terms-of-service");

        return new LoginViewModuleConfiguration(
            configuration.getBoolean("debug", false),
            AuthMode.parse(configuration.getString("auth.mode", "standalone"), logger),
            BypassWelcomeConfiguration.load(bypassWelcomeSection),
            QqBindingConfiguration.load(qqBindingSection),
            UiConfiguration.load(uiSection),
            TermsOfServiceConfiguration.load(termsOfServiceSection),
            SecurityConfiguration.load(securitySection),
            StorageConfiguration.load(storageSection),
            MigrationConfiguration.load(migrationSection),
            Messages.load(messagesSection)
        );
    }

    private static String string(ConfigurationSection section, String path, String fallback) {
        if (section == null) {
            return fallback;
        }
        String value = section.getString(path, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static List<String> strings(ConfigurationSection section, String path, List<String> fallback) {
        if (section == null) {
            return fallback;
        }
        if (section.isList(path)) {
            List<String> list = section.getStringList(path);
            return list == null || list.isEmpty() ? fallback : list;
        }
        String value = section.getString(path);
        if (value != null && !value.trim().isEmpty()) {
            return List.of(value.trim());
        }
        return fallback;
    }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }

    private static int integer(ConfigurationSection section, String path, int fallback, int min) {
        return Math.max(min, section == null ? fallback : section.getInt(path, fallback));
    }

    private static long longValue(ConfigurationSection section, String path, long fallback, long min) {
        return Math.max(min, section == null ? fallback : section.getLong(path, fallback));
    }

    public record BypassWelcomeConfiguration(
        String message
    ) {
        private static BypassWelcomeConfiguration load(ConfigurationSection section) {
            return new BypassWelcomeConfiguration(
                string(section, "message", "&a\u8eab\u4efd\u5df2\u9a8c\u8bc1\uff0c\u6b22\u8fce\u56de\u6765\u3002")
            );
        }
    }

    public record QqBindingConfiguration(
        boolean enabled,
        boolean microsoftRequireBind,
        boolean littleskinRequireBind,
        List<String> bindPrompt
    ) {
        private static QqBindingConfiguration load(ConfigurationSection section) {
            return new QqBindingConfiguration(
                bool(section, "enabled", true),
                bool(section, "microsoft-require-bind", false),
                bool(section, "littleskin-require-bind", true),
                strings(section, "bind-prompt", List.of(
                    "&e你尚未绑定 QQ",
                    "&7请在 QQ 群发送: #绑定 {name}",
                    "&7获取验证码后在下方输入"
                ))
            );
        }
    }

    public enum AuthMode {
        AUTHME("authme"),
        STANDALONE("standalone");

        private final String configKey;

        AuthMode(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public static AuthMode parse(String rawValue, Logger logger) {
            String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
            for (AuthMode mode : values()) {
                if (mode.configKey.equals(normalized)) {
                    return mode;
                }
            }
            if (logger != null) {
                logger.warning("LoginView auth.mode 无效(" + rawValue + ")，已回退为 standalone。");
            }
            return STANDALONE;
        }
    }

    public record UiConfiguration(
        String uiId,
        String packetId,
        String uiFile,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles,
        long openDelayTicks,
        boolean closeOnLogin
    ) {
        private static UiConfiguration load(ConfigurationSection section) {
            return new UiConfiguration(
                string(section, "ui-id", "AXS:LoginView"),
                string(section, "packet-id", "AXS_loginview"),
                sanitizeUiFile(string(section, "ui-file", "login_view.yml")),
                bool(section, "register-ui-on-enable", true),
                bool(section, "overwrite-ui-files", false),
                longValue(section, "open-delay-ticks", 20L, 0L),
                bool(section, "close-on-login", true)
            );
        }

        public String relativeUiPath() {
            return "ui/" + uiFile;
        }
    }

    public record SecurityConfiguration(
        int minPasswordLength,
        int maxPasswordLength,
        int maxAttempts,
        boolean kickOnMaxAttempts,
        boolean lockMovement,
        boolean lockChat,
        boolean lockCommands,
        String allowCommandsPrefix,
        boolean rehashMigratedPasswordOnLogin,
        int sessionTtlMinutes,
        boolean sessionStrictIp
    ) {
        private static SecurityConfiguration load(ConfigurationSection section) {
            return new SecurityConfiguration(
                integer(section, "min-password-length", 6, 1),
                integer(section, "max-password-length", 64, 8),
                integer(section, "max-attempts", 5, 1),
                bool(section, "kick-on-max-attempts", true),
                bool(section, "lock-movement", true),
                bool(section, "lock-chat", true),
                bool(section, "lock-commands", true),
                string(section, "allow-commands-prefix", "login,register,l,reg,AXS"),
                bool(section, "rehash-migrated-password-on-login", true),
                integer(section, "session-ttl-minutes", 30, 0),
                bool(section, "session-strict-ip", false)
            );
        }
    }

    public record StorageConfiguration(
        StorageDialect dialect,
        String sqliteFileName,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String tablePrefix
    ) {
        private static StorageConfiguration load(ConfigurationSection section) {
            return new StorageConfiguration(
                StorageDialect.parse(section == null ? null : section.getString("mode", "sqlite")),
                string(section, "sqlite.file", "loginview.db"),
                string(section, "mysql.host", "127.0.0.1"),
                integer(section, "mysql.port", 3306, 1),
                string(section, "mysql.database", "minecraft"),
                string(section, "mysql.username", "root"),
                string(section, "mysql.password", ""),
                sanitizeIdentifier(string(section, "table-prefix", "AXS_loginview_"), "AXS_loginview_")
            );
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == StorageDialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, 4, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    public enum StorageDialect {
        SQLITE("sqlite"),
        MYSQL("mysql");

        private final String configKey;

        StorageDialect(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public static StorageDialect parse(String rawValue) {
            return "mysql".equalsIgnoreCase(rawValue == null ? "" : rawValue.trim()) ? MYSQL : SQLITE;
        }
    }

    public record MigrationConfiguration(
        String jdbcUrl,
        String username,
        String password,
        String table,
        String nameColumn,
        String realNameColumn,
        String passwordColumn,
        String saltColumn,
        String lastIpColumn,
        String lastLoginColumn,
        String registrationDateColumn,
        String registrationIpColumn,
        String emailColumn,
        String importedHashAlgorithm,
        int batchSize
    ) {
        private static MigrationConfiguration load(ConfigurationSection section) {
            return new MigrationConfiguration(
                string(section, "source.jdbc-url", "jdbc:sqlite:plugins/AuthMe/AuthMe.db"),
                string(section, "source.username", ""),
                string(section, "source.password", ""),
                sanitizeIdentifier(string(section, "source.table", "authme"), "authme"),
                sanitizeIdentifier(string(section, "columns.name", "username"), "username"),
                sanitizeIdentifier(string(section, "columns.real-name", "realname"), "realname"),
                sanitizeIdentifier(string(section, "columns.password", "password"), "password"),
                sanitizeIdentifier(string(section, "columns.salt", "salt"), "salt"),
                sanitizeIdentifier(string(section, "columns.last-ip", "ip"), "ip"),
                sanitizeIdentifier(string(section, "columns.last-login", "lastlogin"), "lastlogin"),
                sanitizeIdentifier(string(section, "columns.registration-date", "regdate"), "regdate"),
                sanitizeIdentifier(string(section, "columns.registration-ip", "regip"), "regip"),
                sanitizeIdentifier(string(section, "columns.email", "email"), "email"),
                string(section, "imported-hash-algorithm", "AUTHME_BCRYPT"),
                integer(section, "batch-size", 200, 1)
            );
        }
    }

    public record TermsOfServiceConfiguration(
        boolean enabled
    ) {
        private static TermsOfServiceConfiguration load(ConfigurationSection section) {
            return new TermsOfServiceConfiguration(
                bool(section, "enabled", true)
            );
        }
    }

    public record Messages(
        String titleLogin,
        String titleRegister,
        String loginSuccess,
        String registerSuccess,
        String changeSuccess,
        String passwordMismatch,
        String passwordTooShort,
        String passwordTooLong,
        String wrongPassword,
        String alreadyRegistered,
        String notRegistered,
        String authmeUnavailable,
        String locked,
        String kicked
    ) {
        private static Messages load(ConfigurationSection section) {
            return new Messages(
                string(section, "title-login", "登录服务器"),
                string(section, "title-register", "注册账号"),
                string(section, "login-success", "&a登录成功。"),
                string(section, "register-success", "&a注册完成，已自动登录。"),
                string(section, "change-success", "&a密码已修改。"),
                string(section, "password-mismatch", "&c两次输入的密码不一致。"),
                string(section, "password-too-short", "&c密码太短。"),
                string(section, "password-too-long", "&c密码太长。"),
                string(section, "wrong-password", "&c密码错误。"),
                string(section, "already-registered", "&c你已经注册过账号。"),
                string(section, "not-registered", "&e你还没有注册，请先设置密码。"),
                string(section, "authme-unavailable", "&c当前配置为 AuthMe 兼容模式，但 AuthMe 不可用。"),
                string(section, "locked", "&e请先完成登录。"),
                string(section, "kicked", "&c密码错误次数过多。")
            );
        }
    }

    private static String sanitizeIdentifier(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized.matches("[A-Za-z0-9_]+") ? normalized : fallback;
    }

    private static String sanitizeUiFile(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.contains("/") || normalized.contains("\\") || !normalized.endsWith(".yml")) {
            return "login_view.yml";
        }
        return normalized;
    }
}
