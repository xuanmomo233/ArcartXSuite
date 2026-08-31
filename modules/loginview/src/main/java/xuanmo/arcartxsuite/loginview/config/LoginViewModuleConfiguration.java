package xuanmo.arcartxsuite.loginview.config;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * LoginView 模块的不可变配置聚合体，从 {@code ArcartXLoginView.yml} 解析而来。
 * <p>
 * 包含调试开关、认证模式、QQ 绑定、UI、服务条款、安全、存储、AuthMe 迁移、
 * 消息文本与登录出生点等子配置；各子配置以嵌套 record 形式组织，
 * 通过 {@link #load(FileConfiguration, Logger)} 统一加载。
 *
 * @param debug         是否开启调试日志
 * @param authMode      认证模式（standalone / authme）
 * @param bypassWelcome 免登欢迎语配置
 * @param qqBinding     QQ 绑定配置
 * @param ui            UI 相关配置
 * @param termsOfService 服务条款配置
 * @param security      安全策略配置
 * @param storage       存储后端配置
 * @param migration     AuthMe 数据迁移配置
 * @param messages      消息文本配置
 * @param spawnOnLogin  登录后传送出生点配置
 */
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
    Messages messages,
    SpawnOnLoginConfiguration spawnOnLogin
) {

    /**
     * 从主配置文件加载模块配置，消息节取自 {@code messages} 段。
     *
     * @param configuration Bukkit 配置文件
     * @param logger        日志器，用于记录回退警告
     * @return 解析后的配置实例
     */
    public static LoginViewModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, configuration.getConfigurationSection("messages"), logger);
    }

    /**
     * 从主配置文件加载模块配置，消息节由调用方显式提供。
     *
     * @param configuration   Bukkit 配置文件
     * @param messagesSection 消息配置节，可为 {@code null}
     * @param logger          日志器，用于记录回退警告
     * @return 解析后的配置实例
     */
    public static LoginViewModuleConfiguration load(
            FileConfiguration configuration,
            ConfigurationSection messagesSection,
            Logger logger) {
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        ConfigurationSection securitySection = configuration.getConfigurationSection("security");
        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        ConfigurationSection migrationSection = configuration.getConfigurationSection("authme-migration");
        ConfigurationSection bypassWelcomeSection = configuration.getConfigurationSection("auth.bypass-welcome");
        ConfigurationSection qqBindingSection = configuration.getConfigurationSection("qq-binding");
        ConfigurationSection spawnOnLoginSection = configuration.getConfigurationSection("spawn-on-login");
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
            Messages.load(messagesSection),
            SpawnOnLoginConfiguration.load(spawnOnLoginSection)
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

    private static double doubleValue(ConfigurationSection section, String path, double fallback) {
        return section == null ? fallback : section.getDouble(path, fallback);
    }

    /**
     * 免登欢迎语配置（玩家已验证时展示的提示文本）。
     *
     * @param message 欢迎语，支持颜色代码
     */
    public record BypassWelcomeConfiguration(
        String message
    ) {
        private static BypassWelcomeConfiguration load(ConfigurationSection section) {
            return new BypassWelcomeConfiguration(
                string(section, "message", "&a\u8eab\u4efd\u5df2\u9a8c\u8bc1\uff0c\u6b22\u8fce\u56de\u6765\u3002")
            );
        }
    }

    /**
     * QQ 绑定配置，控制微软/LittleSkin 账号是否强制绑定 QQ。
     *
     * @param enabled             是否启用 QQ 绑定流程
     * @param microsoftRequireBind 微软账号是否强制绑定 QQ
     * @param littleskinRequireBind LittleSkin 账号是否强制绑定 QQ
     * @param bindPrompt          未绑定时向玩家展示的提示文本列表
     */
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

    /**
     * 认证模式枚举：{@code STANDALONE}（独立存储）或 {@code AUTHME}（委托 AuthMe）。
     */
    public enum AuthMode {
        AUTHME("authme"),
        STANDALONE("standalone");

        private final String configKey;

        AuthMode(String configKey) {
            this.configKey = configKey;
        }

        /**
         * 该模式在配置文件中使用的键名。
         *
         * @return 配置键名
         */
        public String configKey() {
            return configKey;
        }

        /**
         * 从配置原始字符串解析认证模式，无法识别时回退为 {@link #STANDALONE}。
         *
         * @param rawValue 原始配置值
         * @param logger   日志器，非 {@code null} 时记录回退警告
         * @return 解析得到的认证模式
         */
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

    /**
     * UI 相关配置，控制登录界面的注册 ID、文件路径与打开行为。
     *
     * @param uiId              UI 注册标识
     * @param packetId          客户端包通道标识
     * @param uiFile            UI 配置文件名（位于 {@code ui/} 目录下）
     * @param registerUiOnEnable 启用时是否自动注册 UI
     * @param overwriteUiFiles  是否覆盖已存在的 UI 文件
     * @param openDelayTicks    打开 UI 前的延迟（tick）
     * @param closeOnLogin      登录成功后是否自动关闭 UI
     */
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
                string(section, "ui-id", "AXS:login_view"),
                string(section, "packet-id", "AXS_loginview"),
                sanitizeUiFile(string(section, "ui-file", "login_view.yml")),
                bool(section, "register-ui-on-enable", true),
                bool(section, "overwrite-ui-files", false),
                longValue(section, "open-delay-ticks", 20L, 0L),
                bool(section, "close-on-login", true)
            );
        }

        /**
         * 返回 UI 文件相对于 {@code ui/} 目录的路径。
         *
         * @return 相对路径字符串
         */
        public String relativeUiPath() {
            return "ui/" + uiFile;
        }
    }

    /**
     * 安全策略配置，涵盖密码长度、尝试次数、锁定行为与 session 过期。
     *
     * @param minPasswordLength         最小密码长度
     * @param maxPasswordLength         最大密码长度
     * @param maxAttempts               最大密码错误尝试次数
     * @param kickOnMaxAttempts         达到最大尝试次数后是否踢出
     * @param lockMovement              未登录时是否锁定移动
     * @param lockChat                  未登录时是否锁定聊天
     * @param lockCommands              未登录时是否锁定命令
     * @param allowCommandsPrefix       允许执行的命令前缀列表（逗号分隔）
     * @param rehashMigratedPasswordOnLogin 迁移账号登录时是否重新哈希密码
     * @param sessionTtlMinutes         session 有效期（分钟），0 表示不持久化
     * @param sessionStrictIp           session 是否严格校验 IP
     */
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

    /**
     * 存储后端配置，支持共享模式与自建 SQLite/MySQL 两种模式。
     *
     * @param shared          是否使用共享存储模式（true=共享本体连接，false=自建连接）
     * @param mode            存储模式（sqlite / mysql），仅在 shared=false 时生效
     * @param sqliteFileName  SQLite 文件名
     * @param host            MySQL 主机地址
     * @param port            MySQL 端口
     * @param database        MySQL 数据库名
     * @param username        MySQL 用户名
     * @param password        MySQL 密码
     * @param tablePrefix     表名前缀
     * @param poolSize        连接池大小
     */
    public record StorageConfiguration(
        boolean shared,
        String mode,
        String sqliteFileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int poolSize
    ) {
        private static StorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "loginview.db",
                    "127.0.0.1", 3306, "minecraft", "root", "",
                    "AXS_loginview_", 4);
            }
            return new StorageConfiguration(false,
                string(section, "mode", "sqlite"),
                string(section, "sqlite.file", "loginview.db"),
                string(section, "mysql.host", "127.0.0.1"),
                integer(section, "mysql.port", 3306, 1),
                string(section, "mysql.database", "minecraft"),
                string(section, "mysql.username", "root"),
                string(section, "mysql.password", ""),
                sanitizeIdentifier(string(section, "mysql.table-prefix", "AXS_loginview_"), "AXS_loginview_"),
                integer(section, "pool-size", 4, 1)
            );
        }

        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        /**
         * 将本配置转换为宿主统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
         *
         * @return 存储描述符
         */
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 shared=false 时返回 true（模块独立连接，自建存储）；
         * shared=true 时返回 false（使用本体共享数据源）。
         *
         * @return {@code true} 表示模块应自建连接池
         */
        public boolean hasOverride() {
            return !shared;
        }
    }

    /**
     * AuthMe 数据迁移配置，描述源数据库连接与列映射关系。
     *
     * @param jdbcUrl                 源数据库 JDBC 地址
     * @param username                源数据库用户名
     * @param password                源数据库密码
     * @param table                   源数据表名
     * @param nameColumn              玩家名列
     * @param realNameColumn          真实名列
     * @param passwordColumn          密码哈希列
     * @param saltColumn              盐列
     * @param lastIpColumn            最后登录 IP 列
     * @param lastLoginColumn         最后登录时间列
     * @param registrationDateColumn  注册时间列
     * @param registrationIpColumn    注册 IP 列
     * @param emailColumn             邮箱列
     * @param importedHashAlgorithm   导入后标注的哈希算法
     * @param batchSize               进度上报的批次大小
     */
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

    /**
     * 服务条款配置。
     *
     * @param enabled 是否启用服务条款确认
     */
    public record TermsOfServiceConfiguration(
        boolean enabled
    ) {
        private static TermsOfServiceConfiguration load(ConfigurationSection section) {
            return new TermsOfServiceConfiguration(
                bool(section, "enabled", true)
            );
        }
    }

    /**
     * 登录后传送出生点配置。
     *
     * @param enabled 是否启用登录后传送
     * @param world   目标世界名
     * @param x       X 坐标
     * @param y       Y 坐标
     * @param z       Z 坐标
     * @param yaw     偏航角
     * @param pitch   俯仰角
     */
    public record SpawnOnLoginConfiguration(
        boolean enabled,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        private static SpawnOnLoginConfiguration load(ConfigurationSection section) {
            return new SpawnOnLoginConfiguration(
                bool(section, "enabled", false),
                string(section, "world", "world"),
                doubleValue(section, "x", 0.0),
                doubleValue(section, "y", 64.0),
                doubleValue(section, "z", 0.0),
                (float) doubleValue(section, "yaw", 0.0),
                (float) doubleValue(section, "pitch", 0.0)
            );
        }
    }

    /**
     * 消息文本配置，集中管理 LoginView 各场景下的玩家可见提示。
     *
     * @param titleLogin        登录界面标题
     * @param titleRegister     注册界面标题
     * @param loginSuccess      登录成功提示
     * @param registerSuccess   注册成功提示
     * @param changeSuccess     改密成功提示
     * @param passwordMismatch  两次密码不一致提示
     * @param passwordTooShort  密码过短提示
     * @param passwordTooLong   密码过长提示
     * @param wrongPassword     密码错误提示
     * @param alreadyRegistered 已注册提示
     * @param notRegistered     未注册提示
     * @param authmeUnavailable AuthMe 不可用提示
     * @param locked            未登录锁定提示
     * @param kicked            尝试次数过多踢出提示
     */
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
