package xuanmo.arcartxsuite.lottery.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;

public class JdbcLotteryRepository extends AbstractModuleRepository implements LotteryRepository {

    private final StorageConfiguration config;
    private final Logger logger;
    private boolean sqlite;

    private String tGachaState;
    private String tGachaLog;
    private String tCaseState;
    private String tCaseLog;

    public JdbcLotteryRepository(File dataFolder, StorageConfiguration config, Logger logger) {
        super("AXS-Lottery", dataFolder, config.toDescriptor(), logger);
        this.config = config;
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        sqlite = !config.toDescriptor().isMysql();
        String prefix = config.tablePrefix();
        tGachaState = prefix + "gacha_state";
        tGachaLog = prefix + "gacha_log";
        tCaseState = prefix + "case_state";
        tCaseLog = prefix + "case_log";
        createTables(conn);
        logger.info("[Lottery] " + (sqlite ? "SQLite" : "MySQL") + " 存储已初始化，表前缀: " + prefix);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tGachaState, tGachaLog, tCaseState, tCaseLog);
    }

    @Override
    protected List<String> allTables() {
        return List.of(tGachaState, tGachaLog, tCaseState, tCaseLog);
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (sqlite) {
                createTablesSqlite(stmt);
            } else {
                createTablesMysql(stmt);
            }
        }
    }

    private void createTablesSqlite(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGachaState + " ("
            + "player_uuid TEXT NOT NULL,"
            + "pool_id TEXT NOT NULL,"
            + "pity_5star INTEGER NOT NULL DEFAULT 0,"
            + "pity_4star INTEGER NOT NULL DEFAULT 0,"
            + "guaranteed_up INTEGER NOT NULL DEFAULT 0,"
            + "fate_points INTEGER NOT NULL DEFAULT 0,"
            + "fate_target TEXT,"
            + "PRIMARY KEY (player_uuid, pool_id)"
            + ")");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGachaLog + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "player_uuid TEXT NOT NULL,"
            + "pool_id TEXT NOT NULL,"
            + "pull_time INTEGER NOT NULL,"
            + "pull_count INTEGER NOT NULL DEFAULT 1,"
            + "items_json TEXT NOT NULL,"
            + "pity_at_pull INTEGER NOT NULL DEFAULT 0,"
            + "is_guaranteed INTEGER NOT NULL DEFAULT 0"
            + ")");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gacha_log_player_pool ON " + tGachaLog + " (player_uuid, pool_id)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCaseState + " ("
            + "player_uuid TEXT NOT NULL,"
            + "pool_id TEXT NOT NULL,"
            + "open_count INTEGER NOT NULL DEFAULT 0,"
            + "last_open_time INTEGER,"
            + "PRIMARY KEY (player_uuid, pool_id)"
            + ")");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCaseLog + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "player_uuid TEXT NOT NULL,"
            + "pool_id TEXT NOT NULL,"
            + "open_time INTEGER NOT NULL,"
            + "item_id TEXT NOT NULL,"
            + "rarity TEXT NOT NULL,"
            + "is_stattrak INTEGER NOT NULL DEFAULT 0,"
            + "wear_value REAL,"
            + "wear_tier TEXT"
            + ")");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_case_log_player_pool ON " + tCaseLog + " (player_uuid, pool_id)");
    }

    private void createTablesMysql(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGachaState + " ("
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "pool_id VARCHAR(64) NOT NULL,"
            + "pity_5star INT NOT NULL DEFAULT 0,"
            + "pity_4star INT NOT NULL DEFAULT 0,"
            + "guaranteed_up BOOLEAN NOT NULL DEFAULT FALSE,"
            + "fate_points INT NOT NULL DEFAULT 0,"
            + "fate_target VARCHAR(64),"
            + "PRIMARY KEY (player_uuid, pool_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGachaLog + " ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "pool_id VARCHAR(64) NOT NULL,"
            + "pull_time BIGINT NOT NULL,"
            + "pull_count INT NOT NULL DEFAULT 1,"
            + "items_json TEXT NOT NULL,"
            + "pity_at_pull INT NOT NULL DEFAULT 0,"
            + "is_guaranteed BOOLEAN NOT NULL DEFAULT FALSE,"
            + "INDEX idx_player_pool (player_uuid, pool_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCaseState + " ("
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "pool_id VARCHAR(64) NOT NULL,"
            + "open_count INT NOT NULL DEFAULT 0,"
            + "last_open_time BIGINT,"
            + "PRIMARY KEY (player_uuid, pool_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCaseLog + " ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "pool_id VARCHAR(64) NOT NULL,"
            + "open_time BIGINT NOT NULL,"
            + "item_id VARCHAR(64) NOT NULL,"
            + "rarity VARCHAR(32) NOT NULL,"
            + "is_stattrak BOOLEAN NOT NULL DEFAULT FALSE,"
            + "wear_value DOUBLE,"
            + "wear_tier VARCHAR(32),"
            + "INDEX idx_player_pool (player_uuid, pool_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    // ─── Gacha State CRUD ─────────────────────────────────

    @Override
    public PlayerGachaState loadGachaState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String sql = "SELECT pity_5star, pity_4star, guaranteed_up, fate_points, fate_target FROM " + tGachaState + " WHERE player_uuid = ? AND pool_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerGachaState(
                        playerUuid, poolId,
                        rs.getInt("pity_5star"),
                        rs.getInt("pity_4star"),
                        rs.getBoolean("guaranteed_up"),
                        rs.getInt("fate_points"),
                        rs.getString("fate_target")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("[Lottery] 加载 Gacha 状态失败: " + e.getMessage());
        }
        return PlayerGachaState.empty(playerUuid, poolId);
    }

    @Override
    public void saveGachaState(@NotNull PlayerGachaState state) {
        String sql = "INSERT INTO " + tGachaState + " (player_uuid, pool_id, pity_5star, pity_4star, guaranteed_up, fate_points, fate_target) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE pity_5star = VALUES(pity_5star), pity_4star = VALUES(pity_4star), guaranteed_up = VALUES(guaranteed_up), fate_points = VALUES(fate_points), fate_target = VALUES(fate_target)";
        if (sqlite) {
            sql = "INSERT INTO " + tGachaState + " (player_uuid, pool_id, pity_5star, pity_4star, guaranteed_up, fate_points, fate_target) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, pool_id) DO UPDATE SET pity_5star = excluded.pity_5star, pity_4star = excluded.pity_4star, guaranteed_up = excluded.guaranteed_up, fate_points = excluded.fate_points, fate_target = excluded.fate_target";
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.playerUuid().toString());
            ps.setString(2, state.poolId());
            ps.setInt(3, state.pity5star());
            ps.setInt(4, state.pity4star());
            ps.setBoolean(5, state.guaranteedUp());
            ps.setInt(6, state.fatePoints());
            ps.setString(7, state.fateTarget());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 保存 Gacha 状态失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteGachaState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String sql = "DELETE FROM " + tGachaState + " WHERE player_uuid = ? AND pool_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 删除 Gacha 状态失败: " + e.getMessage());
        }
    }

    // ─── Case State CRUD ──────────────────────────────────

    @Override
    public PlayerCaseState loadCaseState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String sql = "SELECT open_count, last_open_time FROM " + tCaseState + " WHERE player_uuid = ? AND pool_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerCaseState(
                        playerUuid, poolId,
                        rs.getInt("open_count"),
                        rs.getLong("last_open_time")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("[Lottery] 加载 Case 状态失败: " + e.getMessage());
        }
        return PlayerCaseState.empty(playerUuid, poolId);
    }

    @Override
    public void saveCaseState(@NotNull PlayerCaseState state) {
        String sql = "INSERT INTO " + tCaseState + " (player_uuid, pool_id, open_count, last_open_time) "
            + "VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE open_count = VALUES(open_count), last_open_time = VALUES(last_open_time)";
        if (sqlite) {
            sql = "INSERT INTO " + tCaseState + " (player_uuid, pool_id, open_count, last_open_time) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, pool_id) DO UPDATE SET open_count = excluded.open_count, last_open_time = excluded.last_open_time";
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.playerUuid().toString());
            ps.setString(2, state.poolId());
            ps.setInt(3, state.openCount());
            ps.setLong(4, state.lastOpenTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 保存 Case 状态失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteCaseState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String sql = "DELETE FROM " + tCaseState + " WHERE player_uuid = ? AND pool_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 删除 Case 状态失败: " + e.getMessage());
        }
    }

    // ─── Logs ─────────────────────────────────────────────

    @Override
    public void logGachaPull(@NotNull UUID playerUuid, @NotNull String poolId, int pullCount,
                              @NotNull String itemsJson, int pityAtPull, boolean guaranteed) {
        String sql = "INSERT INTO " + tGachaLog + " (player_uuid, pool_id, pull_time, pull_count, items_json, pity_at_pull, is_guaranteed) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setInt(4, pullCount);
            ps.setString(5, itemsJson);
            ps.setInt(6, pityAtPull);
            ps.setBoolean(7, guaranteed);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 记录 Gacha 日志失败: " + e.getMessage());
        }
    }

    @Override
    public void logCaseOpen(@NotNull UUID playerUuid, @NotNull String poolId,
                             @NotNull String itemId, @NotNull String rarity,
                             boolean stattrak, double wearValue, @NotNull String wearTier) {
        String sql = "INSERT INTO " + tCaseLog + " (player_uuid, pool_id, open_time, item_id, rarity, is_stattrak, wear_value, wear_tier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, itemId);
            ps.setString(5, rarity);
            ps.setBoolean(6, stattrak);
            ps.setDouble(7, wearValue);
            ps.setString(8, wearTier);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[Lottery] 记录 Case 日志失败: " + e.getMessage());
        }
    }

    @Override
    public @NotNull List<GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        List<GachaLogEntry> result = new ArrayList<>();
        String sql = "SELECT id, pull_time, pull_count, items_json, pity_at_pull, is_guaranteed FROM " + tGachaLog
            + " WHERE player_uuid = ? AND pool_id = ? ORDER BY pull_time DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new GachaLogEntry(
                        rs.getLong("id"),
                        rs.getLong("pull_time"),
                        rs.getInt("pull_count"),
                        rs.getString("items_json"),
                        rs.getInt("pity_at_pull"),
                        rs.getBoolean("is_guaranteed")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("[Lottery] 查询 Gacha 历史失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public @NotNull List<CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        List<CaseLogEntry> result = new ArrayList<>();
        String sql = "SELECT id, open_time, item_id, rarity, is_stattrak, wear_value, wear_tier FROM " + tCaseLog
            + " WHERE player_uuid = ? AND pool_id = ? ORDER BY open_time DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CaseLogEntry(
                        rs.getLong("id"),
                        rs.getLong("open_time"),
                        rs.getString("item_id"),
                        rs.getString("rarity"),
                        rs.getBoolean("is_stattrak"),
                        rs.getDouble("wear_value"),
                        rs.getString("wear_tier")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("[Lottery] 查询 Case 历史失败: " + e.getMessage());
        }
        return result;
    }
}
