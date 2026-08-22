package xuanmo.arcartxsuite.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.bukkit.configuration.file.FileConfiguration;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/**
 * 宿主统一数据源管理器实现。
 * <p>
 * 在本体启动时根据 config.yml 的全局 {@code storage} 节创建唯一一个 HikariCP 连接池（MySQL 模式），
 * 或按模块名分配独立 SQLite 连接（SQLite 模式）。
 * <p>
 * <h3>MySQL 模式</h3>
 * 所有模块共享同一个 HikariCP 连接池，各模块用 {@code tablePrefix} 隔离表。
 * 连接数从 N×poolSize 降为 1 个池。
 * <p>
 * <h3>SQLite 模式</h3>
 * 各模块仍使用各自的 {@code <moduleId>.db} 文件，本体按模块名缓存对应的 HikariCP 连接池
 * （maximumPoolSize=2，支持 WAL 模式下的并发读 + 串行写）。数据不合并，旧 {@code .db} 文件零迁移。
 *
 * @since 1.4.0
 */
public final class StorageManagerImpl implements StorageManager {

    private final File dataFolder;
    private final StorageDescriptor descriptor;
    private final HikariDataSource globalDataSource;
    private final Map<String, HikariDataSource> sqliteModuleSources = new ConcurrentHashMap<>();
    /** 自建模式（override）连接池，按 moduleId 缓存。 */
    private final Map<String, HikariDataSource> selfManagedSources = new ConcurrentHashMap<>();
    private volatile HikariDataSource hostSqliteSource;
    /** JDBC 连接参数（从 config.yml 读取，用于构建 MySQL JDBC URL）。 */
    private final JdbcParams jdbcParams;

    /**
     * 从本体 config.yml 的 {@code storage} 节创建数据源管理器。
     *
     * @param config     本体配置
     * @param dataFolder 本体数据目录（plugins/ArcartXSuite/）
     * @throws SQLException 连接池初始化失败
     */
    public StorageManagerImpl(FileConfiguration config, File dataFolder) throws SQLException {
        this.dataFolder = dataFolder;
        this.descriptor = readDescriptor(config);
        this.jdbcParams = JdbcParams.fromConfig(config);
        this.globalDataSource = descriptor.isMysql() ? createMysqlPool(descriptor) : null;
    }

    private StorageDescriptor readDescriptor(FileConfiguration config) {
        String mode = config.getString("storage.mode", "sqlite");
        if ("mysql".equalsIgnoreCase(mode)) {
            return StorageDescriptor.mysql(
                config.getString("storage.mysql.host", "127.0.0.1"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "arcartxsuite"),
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""),
                config.getInt("storage.pool-size", 8),
                ""  // 全局共享池不使用 tablePrefix，各模块自带前缀
            );
        }
        // SQLite 模式：全局 descriptor 标记为非 MySQL，各模块按需创建独立连接
        return StorageDescriptor.sqlite("axs_global.db");
    }

    private HikariDataSource createMysqlPool(StorageDescriptor desc) throws SQLException {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("AXS-Global");
        hc.setMinimumIdle(2);
        hc.setMaximumPoolSize(desc.poolSize());
        hc.setAutoCommit(true);
        String jdbcUrl = jdbcParams.buildUrl(desc.host(), desc.port(), desc.database());
        hc.setJdbcUrl(jdbcUrl);
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setUsername(desc.username());
        hc.setPassword(desc.password());
        try {
            return new HikariDataSource(hc);
        } catch (Exception e) {
            throw new SQLException("AXS 全局 MySQL 连接池初始化失败: " + e.getMessage(), e);
        }
    }

    private HikariDataSource createSqlitePool(String moduleId, String sqliteFileName) throws SQLException {
        String fileName = (sqliteFileName != null && !sqliteFileName.isBlank())
            ? sqliteFileName
            : (moduleId != null ? moduleId + ".db" : "axs_module.db");
        // SQLite 文件放在模块数据子目录下（plugins/ArcartXSuite/data/<moduleId>/），
        // 与旧自建模式路径保持一致，旧 .db 文件零迁移
        String dirId = moduleId != null ? moduleId : "default";
        File moduleDir = new File(dataFolder, "data" + File.separator + dirId);
        File sqliteFile = new File(moduleDir, fileName);
        File parent = sqliteFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("无法创建 SQLite 数据目录: " + parent.getAbsolutePath());
        }
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("AXS-SQLite-" + moduleId);
        hc.setMinimumIdle(1);
        hc.setMaximumPoolSize(2);
        hc.setAutoCommit(true);
        hc.setConnectionTimeout(10000);
        hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        hc.setDriverClassName("org.sqlite.JDBC");
        hc.setConnectionTestQuery("SELECT 1");
        try {
            HikariDataSource ds = new HikariDataSource(hc);
            // SQLite PRAGMA 优化
            try (Connection conn = ds.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA busy_timeout=5000");
            }
            return ds;
        } catch (Exception e) {
            throw new SQLException("AXS SQLite 连接池初始化失败 (" + fileName + "): " + e.getMessage(), e);
        }
    }

    @Override
    public DataSource getDataSource() {
        return globalDataSource;
    }

    @Override
    public StorageDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public boolean isAvailable() {
        if (descriptor.isMysql()) {
            return globalDataSource != null && !globalDataSource.isClosed();
        }
        // SQLite 模式：只要有至少一个模块数据源可用即为可用，或尚未创建任何模块源时也返回 true
        // （模块调用 initialize() 时会按需创建）
        if (sqliteModuleSources.isEmpty()) {
            return true;
        }
        return sqliteModuleSources.values().stream().anyMatch(ds -> !ds.isClosed());
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (descriptor.isMysql()) {
            if (globalDataSource == null || globalDataSource.isClosed()) {
                throw new SQLException("AXS 全局 MySQL 数据源不可用");
            }
            return globalDataSource.getConnection();
        }
        throw new SQLException("SQLite 模式下请使用 getModuleDataSource(moduleId, fileName) 获取模块独立连接");
    }

    @Override
    public DataSource getModuleDataSource(String moduleId, String sqliteFileName) throws SQLException {
        if (descriptor.isMysql()) {
            return globalDataSource;
        }
        // SQLite：按模块名缓存独立连接池
        String key = moduleId != null ? moduleId : "default";
        HikariDataSource existing = sqliteModuleSources.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        synchronized (sqliteModuleSources) {
            existing = sqliteModuleSources.get(key);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            HikariDataSource ds = createSqlitePool(key, sqliteFileName);
            sqliteModuleSources.put(key, ds);
            return ds;
        }
    }

    @Override
    public void closeModuleDataSource(String moduleId) {
        String key = moduleId != null ? moduleId : "default";
        // 先关闭自建模式连接池（如果存在）
        HikariDataSource selfManaged = selfManagedSources.remove(key);
        if (selfManaged != null && !selfManaged.isClosed()) {
            selfManaged.close();
        }
        // MySQL 共享模式：全局共享池，不关闭（由本体 onDisable 统一管理）
        if (descriptor.isMysql()) {
            return;
        }
        // SQLite 共享模式：关闭并移除该模块的独立连接池，释放文件锁
        HikariDataSource ds = sqliteModuleSources.remove(key);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    @Override
    public DataSource getHostDataSource() throws SQLException {
        if (descriptor.isMysql()) {
            if (globalDataSource == null || globalDataSource.isClosed()) {
                throw new SQLException("AXS 全局 MySQL 数据源不可用");
            }
            return globalDataSource;
        }
        // SQLite 模式：懒创建宿主专用 axs_host.db 连接池
        HikariDataSource existing = hostSqliteSource;
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        synchronized (this) {
            existing = hostSqliteSource;
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            // 宿主专用 SQLite 文件放在 data/host/ 目录下
            HikariDataSource ds = createSqlitePool("host", "axs_host.db");
            hostSqliteSource = ds;
            return ds;
        }
    }

    @Override
    public Connection getHostConnection() throws SQLException {
        return getHostDataSource().getConnection();
    }

    @Override
    public DataSource resolveModuleDataSource(String moduleId, String sqliteFileName,
                                               File moduleDataFolder,
                                               StorageDescriptor overrideDescriptor) throws SQLException {
        String key = moduleId != null ? moduleId : "default";

        // ── 自建模式：根据 overrideDescriptor 创建独立连接池 ──
        if (overrideDescriptor != null) {
            HikariDataSource existing = selfManagedSources.get(key);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            synchronized (selfManagedSources) {
                existing = selfManagedSources.get(key);
                if (existing != null && !existing.isClosed()) {
                    return existing;
                }
                HikariDataSource ds = createSelfManagedPool(key, overrideDescriptor, moduleDataFolder);
                selfManagedSources.put(key, ds);
                return ds;
            }
        }

        // ── 共享模式 ──
        if (descriptor.isMysql()) {
            if (globalDataSource == null || globalDataSource.isClosed()) {
                throw new SQLException("AXS 全局 MySQL 数据源不可用");
            }
            return globalDataSource;
        }
        // SQLite 共享：按模块名分配独立连接池
        return getModuleDataSource(key, sqliteFileName);
    }

    /**
     * 创建自建模式连接池（模块配置了独立 storage 覆盖）。
     * <p>
     * SQLite 文件放在 moduleDataFolder 下（保持旧路径，零迁移）；
     * MySQL 使用 overrideDescriptor 中的连接参数。
     */
    private HikariDataSource createSelfManagedPool(String moduleId, StorageDescriptor desc, File moduleDataFolder) throws SQLException {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("AXS-" + moduleId);
        hc.setMinimumIdle(1);
        hc.setAutoCommit(true);

        if (desc.isMysql()) {
            if (desc.host() == null || desc.host().isBlank()) {
                throw new SQLException("MySQL 配置缺失 [AXS-" + moduleId + "]: storage.mysql.host 为空。"
                    + "请在模块配置中填写 MySQL 连接信息，或将 storage.mode 改回 sqlite 使用共享模式。");
            }
            if (desc.database() == null || desc.database().isBlank()) {
                throw new SQLException("MySQL 配置缺失 [AXS-" + moduleId + "]: storage.mysql.database 为空。"
                    + "请在模块配置中填写 MySQL 连接信息，或将 storage.mode 改回 sqlite 使用共享模式。");
            }
            hc.setMaximumPoolSize(desc.poolSize());
            String jdbcUrl = jdbcParams.buildUrl(desc.host(), desc.port(), desc.database());
            hc.setJdbcUrl(jdbcUrl);
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setUsername(desc.username());
            hc.setPassword(desc.password());
        } else {
            if (moduleDataFolder != null && !moduleDataFolder.exists() && !moduleDataFolder.mkdirs()) {
                throw new SQLException("无法创建数据目录: " + moduleDataFolder.getAbsolutePath());
            }
            File sqliteFile = new File(moduleDataFolder, desc.sqliteFileName());
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("无法创建 SQLite 数据目录: " + parent.getAbsolutePath());
            }
            hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(2);
            hc.setConnectionTimeout(10000);
            hc.setConnectionTestQuery("SELECT 1");
        }

        try {
            HikariDataSource ds = new HikariDataSource(hc);
            if (!desc.isMysql()) {
                // SQLite PRAGMA 优化
                try (Connection conn = ds.getConnection();
                     java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA busy_timeout=5000");
                }
            }
            return ds;
        } catch (Exception e) {
            String hint = desc.isMysql()
                ? "MySQL 连接失败 [AXS-" + moduleId + "]: 请检查模块配置中的 storage.mysql 连接参数"
                  + "（host=" + desc.host() + ", port=" + desc.port()
                  + ", database=" + desc.database() + ", username=" + desc.username() + "）。"
                  + "如果尚未配置 MySQL，请将 storage.mode 改回 sqlite 使用共享模式。"
                : "SQLite 数据源初始化失败 [AXS-" + moduleId + "]: " + e.getMessage();
            throw new SQLException(hint + " 原始错误: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭所有数据源（本体 onDisable 时调用）。
     */
    public void shutdown() {
        if (globalDataSource != null && !globalDataSource.isClosed()) {
            globalDataSource.close();
        }
        for (HikariDataSource ds : sqliteModuleSources.values()) {
            if (!ds.isClosed()) {
                ds.close();
            }
        }
        sqliteModuleSources.clear();
        for (HikariDataSource ds : selfManagedSources.values()) {
            if (!ds.isClosed()) {
                ds.close();
            }
        }
        selfManagedSources.clear();
        if (hostSqliteSource != null && !hostSqliteSource.isClosed()) {
            hostSqliteSource.close();
            hostSqliteSource = null;
        }
    }

    /**
     * JDBC 连接参数，从 config.yml 的 {@code storage.mysql} 节读取。
     * <p>
     * 支持细粒度配置（use-ssl / allow-public-key-retrieval / server-timezone 等）
     * 以及自定义 {@code jdbc-params} 字符串（直接拼接到 URL 末尾，优先级最高）。
     */
    static final class JdbcParams {
        final boolean useSsl;
        final boolean allowPublicKeyRetrieval;
        final String serverTimezone;
        final String characterEncoding;
        final boolean useUnicode;
        final String customParams;

        private JdbcParams(boolean useSsl, boolean allowPublicKeyRetrieval,
                           String serverTimezone, String characterEncoding,
                           boolean useUnicode, String customParams) {
            this.useSsl = useSsl;
            this.allowPublicKeyRetrieval = allowPublicKeyRetrieval;
            this.serverTimezone = serverTimezone;
            this.characterEncoding = characterEncoding;
            this.useUnicode = useUnicode;
            this.customParams = customParams;
        }

        static JdbcParams fromConfig(FileConfiguration config) {
            String prefix = "storage.mysql.";
            return new JdbcParams(
                config.getBoolean(prefix + "use-ssl", true),
                config.getBoolean(prefix + "allow-public-key-retrieval", false),
                config.getString(prefix + "server-timezone", "UTC"),
                config.getString(prefix + "character-encoding", "UTF-8"),
                config.getBoolean(prefix + "use-unicode", true),
                config.getString(prefix + "jdbc-params", "")
            );
        }

        /** 构建 JDBC URL：基础参数 + 自定义参数（自定义参数追加到末尾，可覆盖基础项）。 */
        String buildUrl(String host, int port, String database) {
            StringBuilder url = new StringBuilder();
            url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
            url.append("?useSSL=").append(useSsl);
            url.append("&allowPublicKeyRetrieval=").append(allowPublicKeyRetrieval);
            url.append("&serverTimezone=").append(serverTimezone);
            url.append("&characterEncoding=").append(characterEncoding);
            url.append("&useUnicode=").append(useUnicode);
            if (customParams != null && !customParams.isBlank()) {
                String trimmed = customParams.trim();
                if (!trimmed.startsWith("&")) {
                    url.append("&");
                }
                url.append(trimmed);
            }
            return url.toString();
        }
    }
}
