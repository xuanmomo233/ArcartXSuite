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
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;

/**
 * Lottery 模块基于 JDBC 的数据仓库实现。
 * <p>
 * 支持 SQLite 与 MySQL 两种存储后端，管理 GACHA 状态、CASE 状态、
 * 抽卡/开箱日志以及待领取奖励队列表。
 * 继承 {@link AbstractModuleRepository} 获得连接池与迁移能力。
 */
public class JdbcLotteryRepository extends AbstractModuleRepository implements LotteryRepository {

    private final Logger logger;

    private String tGachaState;
    private String tGachaLog;
    private String tCaseState;
    private String tCaseLog;
    private String tPendingClaim;
    private String tPoints;
    private String tExchange;

    public JdbcLotteryRepository(DataSource dataSource, StorageDescriptor descriptor,
                                 File dataFolder, Logger logger) {
        super("AXS-Lottery", dataSource, descriptor, dataFolder, logger);
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        String prefix = tablePrefix();
        tGachaState = prefix + "gacha_state";
        tGachaLog = prefix + "gacha_log";
        tCaseState = prefix + "case_state";
        tCaseLog = prefix + "case_log";
        tPendingClaim = prefix + "pending_claim";
        tPoints = prefix + "points";
        tExchange = prefix + "exchange";
        createTables(conn);
        logger.info("" + (!isMysql() ? "SQLite" : "MySQL") + " 存储已初始化，表前缀: " + prefix);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tGachaState, tGachaLog, tCaseState, tCaseLog, tPendingClaim, tPoints, tExchange);
    }

    @Override
    protected List<String> allTables() {
        return List.of(tGachaState, tGachaLog, tCaseState, tCaseLog, tPendingClaim, tPoints, tExchange);
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                createTablesSqlite(stmt);
            } else {
                createTablesMysql(stmt);
            }
        }
    }

    private void createTablesSqlite(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid TEXT NOT NULL,
                pool_id TEXT NOT NULL,
                pity_5star INTEGER NOT NULL DEFAULT 0,
                pity_4star INTEGER NOT NULL DEFAULT 0,
                guaranteed_up INTEGER NOT NULL DEFAULT 0,
                fate_points INTEGER NOT NULL DEFAULT 0,
                fate_target TEXT,
                PRIMARY KEY (player_uuid, pool_id)
            )
            """.formatted(tGachaState));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                pool_id TEXT NOT NULL,
                pull_time INTEGER NOT NULL,
                pull_count INTEGER NOT NULL DEFAULT 1,
                items_json TEXT NOT NULL,
                pity_at_pull INTEGER NOT NULL DEFAULT 0,
                is_guaranteed INTEGER NOT NULL DEFAULT 0
            )
            """.formatted(tGachaLog));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gacha_log_player_pool ON " + tGachaLog + " (player_uuid, pool_id)");

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid TEXT NOT NULL,
                pool_id TEXT NOT NULL,
                open_count INTEGER NOT NULL DEFAULT 0,
                last_open_time INTEGER,
                PRIMARY KEY (player_uuid, pool_id)
            )
            """.formatted(tCaseState));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                pool_id TEXT NOT NULL,
                open_time INTEGER NOT NULL,
                item_id TEXT NOT NULL,
                rarity TEXT NOT NULL
            )
            """.formatted(tCaseLog));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_case_log_player_pool ON " + tCaseLog + " (player_uuid, pool_id)");
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                pool_id TEXT NOT NULL,
                item_metadata TEXT NOT NULL,
                item_data TEXT,
                created_time INTEGER NOT NULL,
                claimed INTEGER NOT NULL DEFAULT 0,
                attempts INTEGER NOT NULL DEFAULT 0
            )
            """.formatted(tPendingClaim));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_claim_player ON " + tPendingClaim + " (player_uuid, claimed)");
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid TEXT NOT NULL,
                point_id TEXT NOT NULL,
                amount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, point_id)
            )
            """.formatted(tPoints));
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid TEXT NOT NULL,
                item_id TEXT NOT NULL,
                amount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, item_id)
            )
            """.formatted(tExchange));
    }

    private void createTablesMysql(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid VARCHAR(36) NOT NULL,
                pool_id VARCHAR(64) NOT NULL,
                pity_5star INT NOT NULL DEFAULT 0,
                pity_4star INT NOT NULL DEFAULT 0,
                guaranteed_up BOOLEAN NOT NULL DEFAULT FALSE,
                fate_points INT NOT NULL DEFAULT 0,
                fate_target VARCHAR(64),
                PRIMARY KEY (player_uuid, pool_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tGachaState));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                pool_id VARCHAR(64) NOT NULL,
                pull_time BIGINT NOT NULL,
                pull_count INT NOT NULL DEFAULT 1,
                items_json TEXT NOT NULL,
                pity_at_pull INT NOT NULL DEFAULT 0,
                is_guaranteed BOOLEAN NOT NULL DEFAULT FALSE,
                INDEX idx_player_pool (player_uuid, pool_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tGachaLog));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid VARCHAR(36) NOT NULL,
                pool_id VARCHAR(64) NOT NULL,
                open_count INT NOT NULL DEFAULT 0,
                last_open_time BIGINT,
                PRIMARY KEY (player_uuid, pool_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tCaseState));

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                pool_id VARCHAR(64) NOT NULL,
                open_time BIGINT NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                rarity VARCHAR(32) NOT NULL,
                INDEX idx_player_pool (player_uuid, pool_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tCaseLog));
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                pool_id VARCHAR(64) NOT NULL,
                item_metadata TEXT NOT NULL,
                item_data LONGTEXT,
                created_time BIGINT NOT NULL,
                claimed BOOLEAN NOT NULL DEFAULT FALSE,
                attempts INT NOT NULL DEFAULT 0,
                INDEX idx_pending_claim_player (player_uuid, claimed)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPendingClaim));
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid VARCHAR(36) NOT NULL,
                point_id VARCHAR(64) NOT NULL,
                amount BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, point_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tPoints));
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS %s (
                player_uuid VARCHAR(36) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, item_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(tExchange));
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
            logger.warning("加载 Gacha 状态失败: " + e.getMessage());
        }
        return PlayerGachaState.empty(playerUuid, poolId);
    }

    @Override
    public void saveGachaState(@NotNull PlayerGachaState state) {
        String sql = "INSERT INTO " + tGachaState + " (player_uuid, pool_id, pity_5star, pity_4star, guaranteed_up, fate_points, fate_target) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE pity_5star = VALUES(pity_5star), pity_4star = VALUES(pity_4star), guaranteed_up = VALUES(guaranteed_up), fate_points = VALUES(fate_points), fate_target = VALUES(fate_target)";
        if (!isMysql()) {
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
            logger.warning("保存 Gacha 状态失败: " + e.getMessage());
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
            logger.warning("删除 Gacha 状态失败: " + e.getMessage());
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
            logger.warning("加载 Case 状态失败: " + e.getMessage());
        }
        return PlayerCaseState.empty(playerUuid, poolId);
    }

    @Override
    public void saveCaseState(@NotNull PlayerCaseState state) {
        String sql = "INSERT INTO " + tCaseState + " (player_uuid, pool_id, open_count, last_open_time) "
            + "VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE open_count = VALUES(open_count), last_open_time = VALUES(last_open_time)";
        if (!isMysql()) {
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
            logger.warning("保存 Case 状态失败: " + e.getMessage());
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
            logger.warning("删除 Case 状态失败: " + e.getMessage());
        }
    }

    @Override
    public long enqueuePendingClaim(@NotNull UUID playerUuid, @NotNull String poolId,
                                    @NotNull String itemMetadata, @Nullable String itemData) {
        String sql = "INSERT INTO " + tPendingClaim
            + " (player_uuid, pool_id, item_metadata, item_data, created_time, claimed, attempts)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setString(3, itemMetadata);
            ps.setString(4, itemData);
            ps.setLong(5, System.currentTimeMillis());
            ps.setBoolean(6, false);
            ps.setInt(7, 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            logger.warning("保存待领取奖励失败: " + e.getMessage());
            return -1L;
        }
    }

    @Override
    public @NotNull List<PendingClaim> getPendingClaims(@NotNull UUID playerUuid) {
        List<PendingClaim> result = new ArrayList<>();
        String sql = "SELECT id, pool_id, item_metadata, item_data, created_time, attempts FROM "
            + tPendingClaim + " WHERE player_uuid = ? AND claimed = ? ORDER BY created_time ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setBoolean(2, false);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PendingClaim(
                        rs.getLong("id"), playerUuid, rs.getString("pool_id"),
                        rs.getString("item_metadata"), rs.getString("item_data"),
                        rs.getLong("created_time"), rs.getInt("attempts")));
                }
            }
        } catch (SQLException e) {
            logger.warning("查询待领取奖励失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void markPendingClaimClaimed(long claimId) {
        String sql = "UPDATE " + tPendingClaim + " SET claimed = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, true);
            ps.setLong(2, claimId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("更新待领取奖励失败: " + e.getMessage());
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
            logger.warning("记录 Gacha 日志失败: " + e.getMessage());
        }
    }

    @Override
    public void logCaseOpen(@NotNull UUID playerUuid, @NotNull String poolId,
                             @NotNull String itemId, @NotNull String rarity) {
        String sql = "INSERT INTO " + tCaseLog + " (player_uuid, pool_id, open_time, item_id, rarity) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, itemId);
            ps.setString(5, rarity);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("记录 Case 日志失败: " + e.getMessage());
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
            logger.warning("查询 Gacha 历史失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public @NotNull List<CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        List<CaseLogEntry> result = new ArrayList<>();
        String sql = "SELECT id, open_time, item_id, rarity FROM " + tCaseLog
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
                        rs.getString("rarity")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("查询 Case 历史失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public @NotNull java.util.Map<String, Long> getPoints(@NotNull UUID playerUuid) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        String sql = "SELECT point_id, amount FROM " + tPoints + " WHERE player_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("point_id"), rs.getLong("amount"));
            }
        } catch (SQLException e) {
            logger.warning("Failed to read points: " + e.getMessage());
        }
        return result;
    }

    @Override
    public long getPoint(@NotNull UUID playerUuid, @NotNull String pointId) {
        String sql = "SELECT amount FROM " + tPoints + " WHERE player_uuid = ? AND point_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, pointId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            logger.warning("Failed to read point: " + e.getMessage());
            return 0L;
        }
    }

    @Override
    public boolean addPoints(@NotNull UUID playerUuid, @NotNull String pointId, long amount) {
        if (amount < 0) return false;
        return changePoints(playerUuid, pointId, amount, false);
    }

    @Override
    public boolean subtractPoints(@NotNull UUID playerUuid, @NotNull String pointId, long amount) {
        if (amount < 0) return false;
        return changePoints(playerUuid, pointId, amount, true);
    }

    private boolean changePoints(UUID playerUuid, String pointId, long amount, boolean subtract) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String select = "SELECT amount FROM " + tPoints + " WHERE player_uuid = ? AND point_id = ?";
            long current = 0;
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, pointId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) current = rs.getLong(1);
                }
            }
            long next = subtract ? current - amount : current + amount;
            if (next < 0) {
                conn.rollback();
                return false;
            }
            String sql = (!isMysql())
                ? "INSERT INTO " + tPoints + " (player_uuid, point_id, amount) VALUES (?, ?, ?) "
                    + "ON CONFLICT(player_uuid, point_id) DO UPDATE SET amount = excluded.amount"
                : "INSERT INTO " + tPoints + " (player_uuid, point_id, amount) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, pointId);
                ps.setLong(3, next);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            logger.warning("Failed to change points: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getExchangeCount(@NotNull UUID playerUuid, @NotNull String itemId) {
        String sql = "SELECT amount FROM " + tExchange + " WHERE player_uuid = ? AND item_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.warning("Failed to read exchange count: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean incrementExchangeCount(@NotNull UUID playerUuid, @NotNull String itemId) {
        String sql = (!isMysql())
            ? "INSERT INTO " + tExchange + " (player_uuid, item_id, amount) VALUES (?, ?, 1) "
                + "ON CONFLICT(player_uuid, item_id) DO UPDATE SET amount = amount + 1"
            : "INSERT INTO " + tExchange + " (player_uuid, item_id, amount) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE amount = amount + 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Failed to increment exchange count: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean decrementExchangeCount(@NotNull UUID playerUuid, @NotNull String itemId) {
        String sql = "UPDATE " + tExchange + " SET amount = amount - 1 "
            + "WHERE player_uuid = ? AND item_id = ? AND amount > 0";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Failed to rollback exchange count: " + e.getMessage());
            return false;
        }
    }
}
