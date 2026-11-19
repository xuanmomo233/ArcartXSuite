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
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * EntityTracker 标准 JDBC 数据仓库。
 * <p>
 * 支持 SQLite 与 MySQL。
 */
public final class JdbcEntityTrackerRepository extends AbstractModuleRepository {

    private final Logger logger;

    public JdbcEntityTrackerRepository(DataSource dataSource, StorageDescriptor descriptor,
                                       File dataFolder, Logger logger) {
        super("AXS-EntityTracker", dataSource, descriptor, dataFolder, logger);
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        executeInitScript(conn);
        try (Statement statement = conn.createStatement()) {
            String engine = isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
            statement.execute("CREATE TABLE IF NOT EXISTS entitytracker_kill_receipts ("
                + "kill_key VARCHAR(64) PRIMARY KEY,kill_id BIGINT,delivery_plan " + (isMysql()?"LONGTEXT":"TEXT")
                + " NOT NULL,handed_off INTEGER NOT NULL DEFAULT 0)" + engine);
            statement.execute("CREATE TABLE IF NOT EXISTS entitytracker_boss_totals ("
                + "boss_id VARCHAR(100) NOT NULL,server_name VARCHAR(100) NOT NULL,kill_count BIGINT NOT NULL,"
                + "PRIMARY KEY (boss_id,server_name))" + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS entitytracker_period_scores (
                    boss_id VARCHAR(100) NOT NULL,
                    kill_id VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(100),
                    boss_name VARCHAR(200),
                    damage INTEGER NOT NULL,
                    settled_at BIGINT NOT NULL,
                    PRIMARY KEY (boss_id, kill_id, player_uuid)
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS entitytracker_reward_batches (
                    boss_id VARCHAR(100) NOT NULL,
                    reward_type VARCHAR(20) NOT NULL,
                    period_start_ms BIGINT NOT NULL,
                    PRIMARY KEY (boss_id, reward_type, period_start_ms)
                )
                """ + engine);
            statement.execute((isMysql() ? "INSERT IGNORE " : "INSERT OR IGNORE ") + """
                INTO ranking_reward_configs (id, reward_type, ranking_type, rank_start, rank_end, reward_name, enabled)
                VALUES (-1, 'configured', 'boss_damage', 1, 1, 'Boss YAML ranking rewards', FALSE)
                """);
            addRewardColumn(conn, statement, "next_action", "INTEGER NOT NULL DEFAULT 0");
            addRewardColumn(conn, statement, "claim_attempt", "VARCHAR(36)");
            addRewardColumn(conn, statement, "batch_start_ms", "BIGINT");
            addRewardColumn(conn, statement, "reward_plan", isMysql() ? "LONGTEXT" : "TEXT");
            createIndex(statement, "idx_period_scores_time", "entitytracker_period_scores", "boss_id, settled_at");
            createIndex(statement, "idx_period_scores_player", "entitytracker_period_scores", "boss_id, player_uuid, settled_at");
            createIndex(statement, "idx_period_reward_pending", "ranking_reward_records", "boss_id, reward_type, batch_start_ms, status");
            createIndex(statement, "idx_kill_handoff", "entitytracker_kill_receipts", "handed_off, kill_id");
            createIndex(statement, "idx_kill_boss_server", "boss_kill_records", "boss_id, server_name");
        }
    }

    private void createIndex(Statement statement, String name, String table, String columns) throws SQLException {
        try {
            statement.execute("CREATE INDEX " + (isMysql() ? "" : "IF NOT EXISTS ") + name
                + " ON " + table + " (" + columns + ")");
        } catch (SQLException failure) {
            if (!isMysql() || failure.getErrorCode() != 1061) throw failure;
        }
    }

    private static void addRewardColumn(Connection conn, Statement statement, String column, String type) throws SQLException {
        if (columnExists(conn, "ranking_reward_records", column)) return;
        try {
            statement.execute("ALTER TABLE ranking_reward_records ADD COLUMN " + column + " " + type);
        } catch (SQLException concurrentUpgrade) {
            if (!columnExists(conn, "ranking_reward_records", column)) throw concurrentUpgrade;
        }
    }

    private void executeInitScript(Connection conn) throws SQLException {
        String script = readResource("sql/init_tables.sql");
        if (script == null || script.isBlank()) {
            logger.warning("EntityTracker SQL 初始化脚本未找到");
            return;
        }
        if (isMysql()) {
            script = script.replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INT PRIMARY KEY AUTO_INCREMENT");
            script = script.replace("INSERT OR IGNORE", "INSERT IGNORE");
            script = script.replace("INSERT OR REPLACE", "REPLACE");
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
                    || (isMysql() && msg.contains("duplicate key name"));
                if (!ignore) {
                    logger.warning("EntityTracker SQL 初始化警告: " + e.getMessage());
                }
            }
        }
        logger.info("EntityTracker 数据库表初始化完成 (" + (isMysql() ? "MySQL" : "SQLite") + ")");
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (var rows = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            return rows.next();
        }
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
            "entitytracker_period_scores",
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
            "entitytracker_period_scores",
            "entitytracker_reward_batches",
            "entitytracker_kill_receipts",
            "entitytracker_boss_totals",
            "offline_reward_storage",
            "entitytracker_version"
        );
    }

    // ─── 供外部服务/DAO 直接使用的数据迁移能力 ───

    public @NotNull MigrationResult migrateTo(@NotNull StorageDescriptor target, boolean overwrite) {
        return migrateData(target, overwrite);
    }
}
