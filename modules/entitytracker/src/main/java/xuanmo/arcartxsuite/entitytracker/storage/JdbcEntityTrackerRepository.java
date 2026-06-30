package xuanmo.arcartxsuite.entitytracker.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * EntityTracker 标准 JDBC 数据仓库。
 * <p>
 * 目前主要支持 SQLite；MySQL 支持将在后续版本中添加。
 */
public final class JdbcEntityTrackerRepository extends AbstractModuleRepository {

    private final Logger logger;
    private final boolean mysql;

    public JdbcEntityTrackerRepository(File dataFolder, FileConfiguration config, Logger logger) {
        super("AXS-EntityTracker", dataFolder, parseDescriptor(config), logger);
        this.logger = logger;
        this.mysql = getDescriptor().isMysql();
    }

    private static StorageDescriptor parseDescriptor(FileConfiguration config) {
        ConfigurationSection dbSection = config.getConfigurationSection("database");
        boolean isMysql = "mysql".equalsIgnoreCase(
            dbSection != null ? dbSection.getString("type", "sqlite") : "sqlite");
        if (isMysql) {
            return StorageDescriptor.mysql(
                dbSection.getString("mysql.host", "localhost"),
                dbSection.getInt("mysql.port", 3306),
                dbSection.getString("mysql.database", "arcartxsuite"),
                dbSection.getString("mysql.username", "root"),
                dbSection.getString("mysql.password", ""),
                dbSection.getInt("mysql.pool-size", 5),
                ""
            );
        }
        return StorageDescriptor.sqlite(
            dbSection != null ? dbSection.getString("sqlite.file", "entitytracker.db") : "entitytracker.db");
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        executeInitScript(conn);
    }

    private void executeInitScript(Connection conn) throws SQLException {
        String script = readResource("sql/init_tables.sql");
        if (script == null || script.isBlank()) {
            logger.warning("EntityTracker SQL 初始化脚本未找到");
            return;
        }
        if (mysql) {
            script = script.replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
            script = script.replace("INSERT OR IGNORE", "INSERT IGNORE");
            script = script.replace("INSERT OR REPLACE", "REPLACE INTO");
            script = script.replace("CREATE INDEX IF NOT EXISTS", "CREATE INDEX");
        }
        String[] statements = script.split(";");
        for (String raw : statements) {
            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    sb.append(line).append('\n');
                }
            }
            String sql = sb.toString().trim();
            if (sql.isEmpty()) continue;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                String msg = e.getMessage().toLowerCase();
                boolean ignore = msg.contains("already exists")
                    || (mysql && msg.contains("duplicate key name"));
                if (!ignore) {
                    logger.warning("EntityTracker SQL 初始化警告: " + e.getMessage());
                }
            }
        }
        logger.info("EntityTracker 数据库表初始化完成 (" + (mysql ? "MySQL" : "SQLite") + ")");
    }

    private String readResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            logger.warning("读取 SQL 资源失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(
            "player_dkp",
            "dkp_transaction_records",
            "drop_allocation_records",
            "roll_participation_records",
            "player_boss_best_damage",
            "offline_reward_storage",
            "ranking_reward_records"
        );
    }

    @Override
    protected List<String> allTables() {
        return List.of(
            "boss_kill_records",
            "boss_drop_statistics",
            "player_dkp",
            "dkp_transaction_records",
            "drop_allocation_records",
            "roll_participation_records",
            "player_boss_best_damage",
            "cross_server_boss_rankings",
            "ranking_reward_configs",
            "ranking_reward_records",
            "ranking_periods",
            "offline_reward_storage",
            "entitytracker_version"
        );
    }

    // ─── 供外部服务/DAO 直接使用的数据迁移能力 ───

    public @NotNull MigrationResult migrateTo(@NotNull StorageDescriptor target, boolean overwrite) {
        return migrateData(target, overwrite);
    }
}
