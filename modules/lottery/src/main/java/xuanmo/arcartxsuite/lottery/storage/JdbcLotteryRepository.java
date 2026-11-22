package xuanmo.arcartxsuite.lottery.storage;

import com.google.gson.Gson;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/**
 * Lottery 模块基于 JDBC 的数据仓库实现。
 * <p>
 * 支持 SQLite 与 MySQL 两种存储后端，管理 GACHA 状态、CASE 状态、
 * 抽卡/开箱日志以及待领取奖励队列表。
 * 继承 {@link AbstractModuleRepository} 获得连接池与迁移能力。
 */
public class JdbcLotteryRepository extends AbstractModuleRepository implements LotteryRepository {

    private static final int VIEW_KEYS_PER_QUERY = 100;
    private final Logger logger;
    private static final Gson JSON = new Gson();

    private String tGachaState;
    private String tGachaLog;
    private String tGachaOperations;
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
        tGachaOperations = prefix + "gacha_operations";
        tCaseState = prefix + "case_state";
        tCaseLog = prefix + "case_log";
        tPendingClaim = prefix + "pending_claim";
        tPoints = prefix + "points";
        tExchange = prefix + "exchange";
        createTables(conn);
        ensureColumn(conn, tPendingClaim, "transfer_id", "VARCHAR(36)");
        ensureColumn(conn, tPendingClaim, "transfer_target", "VARCHAR(72)");
        ensureColumn(conn, tGachaOperations, "prizes", isMysql() ? "LONGTEXT" : "TEXT");
        ensureColumn(conn, tGachaOperations, "next_prize", "INTEGER NOT NULL DEFAULT 0");
        logger.info("" + (!isMysql() ? "SQLite" : "MySQL") + " 存储已初始化，表前缀: " + prefix);
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (var columns = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    private void ensureColumn(Connection conn, String table, String column, String type) throws SQLException {
        if (hasColumn(conn, table, column)) return;
        try (var stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException failure) {
            // Another node may have upgraded the shared table first.
            if (!hasColumn(conn, table, column)) throw failure;
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tGachaState, tGachaLog, tGachaOperations, tCaseState, tCaseLog, tPendingClaim, tPoints, tExchange);
    }

    @Override
    protected List<String> allTables() {
        return playerDataTables();
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    active_player VARCHAR(36) UNIQUE,
                    pool_id VARCHAR(64) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    details %s NOT NULL
                )
                """.formatted(tGachaOperations, isMysql() ? "MEDIUMTEXT" : "TEXT")
                + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
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
                attempts INTEGER NOT NULL DEFAULT 0,
                transfer_id VARCHAR(36),
                transfer_target VARCHAR(72)
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
                transfer_id VARCHAR(36),
                transfer_target VARCHAR(72),
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
    public PlayerGachaState loadGachaState(@NotNull UUID playerUuid, @NotNull String poolId) throws SQLException {
        try (Connection conn = getConnection()) {
            return loadGachaState(conn, playerUuid, poolId);
        }
    }

    private PlayerGachaState loadGachaState(Connection conn, UUID playerUuid, String poolId) throws SQLException {
        String sql = "SELECT pity_5star, pity_4star, guaranteed_up, fate_points, fate_target FROM " + tGachaState + " WHERE player_uuid = ? AND pool_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }
        return PlayerGachaState.empty(playerUuid, poolId);
    }

    @Override
    public GachaView loadGachaView(UUID playerId, String effectivePoolId, Set<String> pointIds) throws SQLException {
        if (pointIds.isEmpty()) return new GachaView(loadGachaState(playerId, effectivePoolId), Map.of());
        return readProjection(conn -> new GachaView(loadGachaState(conn, playerId, effectivePoolId),
            readAmounts(conn, playerId, pointIds, tPoints, "point_id", ResultSet::getLong)));
    }

    @Override
    public ExchangeView loadExchangeView(UUID playerId, Set<String> pointIds, Set<String> exchangeIds) throws SQLException {
        return readProjection(conn -> new ExchangeView(
            readAmounts(conn, playerId, pointIds, tPoints, "point_id", ResultSet::getLong),
            readAmounts(conn, playerId, exchangeIds, tExchange, "item_id", ResultSet::getInt)));
    }

    @FunctionalInterface
    private interface Projection<T> {
        T read(Connection conn) throws SQLException;
    }

    private <T> T readProjection(Projection<T> projection) throws SQLException {
        try (Connection conn = getConnection()) {
            if (isMysql()) conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                T result = projection.read(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private void saveGachaState(Connection conn, PlayerGachaState state) throws SQLException {
        String sql = "INSERT INTO " + tGachaState + " (player_uuid, pool_id, pity_5star, pity_4star, guaranteed_up, fate_points, fate_target) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE pity_5star = VALUES(pity_5star), pity_4star = VALUES(pity_4star), guaranteed_up = VALUES(guaranteed_up), fate_points = VALUES(fate_points), fate_target = VALUES(fate_target)";
        if (!isMysql()) {
            sql = "INSERT INTO " + tGachaState + " (player_uuid, pool_id, pity_5star, pity_4star, guaranteed_up, fate_points, fate_target) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid, pool_id) DO UPDATE SET pity_5star = excluded.pity_5star, pity_4star = excluded.pity_4star, guaranteed_up = excluded.guaranteed_up, fate_points = excluded.fate_points, fate_target = excluded.fate_target";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.playerUuid().toString());
            ps.setString(2, state.poolId());
            ps.setInt(3, state.pity5star());
            ps.setInt(4, state.pity4star());
            ps.setBoolean(5, state.guaranteedUp());
            ps.setInt(6, state.fatePoints());
            ps.setString(7, state.fateTarget());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteGachaState(@NotNull UUID playerUuid, @NotNull String poolId) throws SQLException {
        String sql = "DELETE FROM " + tGachaState + " WHERE player_uuid = ? AND pool_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean beginGachaOperation(UUID playerId, UUID operation, String poolId, String details) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tGachaOperations
                 + " (player_uuid, active_player, operation_id, pool_id, status, details) VALUES (?, ?, ?, ?, 'DEBITING', ?)")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, playerId.toString());
            ps.setString(3, operation.toString());
            ps.setString(4, poolId);
            ps.setString(5, details);
            return ps.executeUpdate() == 1;
        } catch (SQLException failure) {
            if (failure.getErrorCode() == (isMysql() ? 1062 : 19)) return false;
            throw failure;
        }
    }

    @Override
    public void commitGachaPull(UUID operation, PlayerGachaState state, String poolId,
                                int count, String items, boolean guaranteed, List<PoolItem> prizes,
                                Map<String, Long> pointCosts, Map<String, Long> pointRewards) throws SQLException {
        commitOperation(state.playerUuid(), operation, pointCosts, pointRewards, items, prizes, conn -> {
            saveGachaState(conn, state);
            logGachaPull(conn, state.playerUuid(), poolId, count, items, state.pity5star(), guaranteed);
        });
    }

    @Override
    public void commitCaseOpen(UUID playerId, UUID operation, String poolId, List<CaseResult> results,
                               Map<String, Long> pointCosts, Map<String, Long> pointRewards) throws SQLException {
        String items = results.stream().map(result -> result.item().key()).toList().toString();
        commitOperation(playerId, operation, pointCosts, pointRewards, items,
                results.stream().map(CaseResult::item).toList(), conn -> {
            String update = isMysql()
                ? "ON DUPLICATE KEY UPDATE open_count = open_count + VALUES(open_count), last_open_time = VALUES(last_open_time)"
                : "ON CONFLICT(player_uuid, pool_id) DO UPDATE SET open_count = open_count + excluded.open_count, last_open_time = excluded.last_open_time";
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tCaseState
                + " (player_uuid, pool_id, open_count, last_open_time) VALUES (?, ?, ?, ?) " + update)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, poolId);
                ps.setInt(3, results.size());
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
            for (CaseResult result : results) {
                logCaseOpen(conn, playerId, poolId, result.item().key(), result.rarity());
            }
        });
    }

    @FunctionalInterface
    private interface OperationWrite {
        void write(Connection conn) throws SQLException;
    }

    @Override
    public void commitExchange(UUID playerId, UUID operation, String itemId, int limit,
                               String prize, PoolItem reward, Map<String, Long> pointCosts) throws SQLException {
        commitOperation(playerId, operation, pointCosts, Map.of(), prize, List.of(reward), conn -> {
            // The active per-player operation row was locked earlier in this transaction.
            int count = 0;
            try (var select = conn.prepareStatement("SELECT amount FROM " + tExchange + " WHERE player_uuid = ? AND item_id = ?")) {
                select.setString(1, playerId.toString());
                select.setString(2, itemId);
                try (var rows = select.executeQuery()) { if (rows.next()) count = rows.getInt(1); }
            }
            if ((limit > 0 && count >= limit) || count == Integer.MAX_VALUE) throw new SQLException("兑换次数达到上限");
            try (var write = conn.prepareStatement("INSERT INTO " + tExchange + " (player_uuid, item_id, amount) VALUES (?, ?, 1) "
                + (isMysql() ? "ON DUPLICATE KEY UPDATE amount = amount + 1" : "ON CONFLICT(player_uuid, item_id) DO UPDATE SET amount = amount + 1"))) {
                write.setString(1, playerId.toString());
                write.setString(2, itemId);
                write.executeUpdate();
            }
        });
    }

    private void commitOperation(UUID playerId, UUID operation, Map<String, Long> pointCosts,
                                 Map<String, Long> pointRewards, String items, List<PoolItem> prizes,
                                 OperationWrite write) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (var save = conn.prepareStatement("UPDATE " + tGachaOperations
                        + " SET status='COMMITTED', prizes=?, next_prize=0"
                        + " WHERE active_player=? AND operation_id=? AND status='DEBITING'")) {
                    save.setString(1, JSON.toJson(prizes));
                    save.setString(2, playerId.toString());
                    save.setString(3, operation.toString());
                    if (save.executeUpdate() != 1) {
                        throw new SQLException("Lottery operation no longer owns the player reservation");
                    }
                }
                for (var cost : new java.util.TreeMap<>(pointCosts).entrySet()) {
                    if (!changePoints(conn, playerId, cost.getKey(), cost.getValue(), true)) {
                        throw new SQLException("奖池积分不足: " + cost.getKey());
                    }
                }
                for (var reward : new java.util.TreeMap<>(pointRewards).entrySet()) {
                    changePoints(conn, playerId, reward.getKey(), reward.getValue(), false);
                }
                write.write(conn);
                appendGachaEvidence(conn, playerId, operation, "COMMITTED",
                    "已提交：奖品=" + items + " 积分费用=" + pointCosts + " 奖励积分=" + pointRewards);
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    @Override
    public GachaOperation findGachaOperation(UUID playerId) throws SQLException {
        try (Connection conn = getConnection()) {
            return findGachaOperation(conn, playerId);
        }
    }

    private GachaOperation findGachaOperation(Connection conn, UUID playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT operation_id, pool_id, status, details FROM "
                 + tGachaOperations + " WHERE active_player = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new GachaOperation(UUID.fromString(rs.getString(1)), rs.getString(2),
                    rs.getString(3), rs.getString(4)) : null;
            }
        }
    }

    @Override
    public GachaOperation findGachaOperation(UUID playerId, UUID operation) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT pool_id, status, details FROM "
                 + tGachaOperations + " WHERE player_uuid=? AND operation_id=?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, operation.toString());
            try (ResultSet row = ps.executeQuery()) {
                return row.next() ? new GachaOperation(operation, row.getString(1), row.getString(2), row.getString(3)) : null;
            }
        }
    }

    @Override
    public PrizePlan loadCommittedPrizes(UUID playerId, UUID operation) throws SQLException {
        try (var connection = getConnection();
             var select = connection.prepareStatement("SELECT next_prize, prizes FROM " + tGachaOperations
                 + " WHERE active_player=? AND operation_id=? AND status='COMMITTED'")) {
            select.setString(1, playerId.toString());
            select.setString(2, operation.toString());
            try (var row = select.executeQuery()) {
                if (!row.next()) return null;
                String payload = row.getString(2);
                if (payload == null) return null;
                return new PrizePlan(row.getInt(1), List.of(JSON.fromJson(payload, PoolItem[].class)));
            }
        }
    }

    @Override
    public boolean beginPrize(UUID playerId, UUID operation, int index, String evidence) throws SQLException {
        return advancePrize(playerId, operation, index, evidence, false);
    }

    @Override
    public boolean completePrize(UUID playerId, UUID operation, int index, String evidence) throws SQLException {
        return advancePrize(playerId, operation, index, evidence, true);
    }

    private boolean advancePrize(UUID playerId, UUID operation, int index, String evidence, boolean completed)
            throws SQLException {
        String append = isMysql() ? "CONCAT(details, ?)" : "details || ?";
        String advance = completed ? "status='COMMITTED', next_prize=next_prize+1" : "status='DELIVERING'";
        String expected = completed ? "DELIVERING" : "COMMITTED";
        try (var connection = getConnection();
             var update = connection.prepareStatement("UPDATE " + tGachaOperations + " SET " + advance
                 + ", details=" + append + " WHERE active_player=? AND operation_id=? AND status=? AND next_prize=?")) {
            update.setString(1, "\n" + evidence);
            update.setString(2, playerId.toString());
            update.setString(3, operation.toString());
            update.setString(4, expected);
            update.setInt(5, index);
            return update.executeUpdate() == 1;
        }
    }

    @Override
    public List<RefundClaim> findRefundClaims(UUID playerId, UUID operation) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, item_metadata, claimed, transfer_id FROM "
                 + tPendingClaim + " WHERE player_uuid=? AND pool_id=? ORDER BY id")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, LotteryRepository.refundClaimPool(operation));
            List<RefundClaim> claims = new ArrayList<>();
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) claims.add(new RefundClaim(rows.getLong(1), rows.getString(2),
                    rows.getBoolean(3), rows.getString(4)));
            }
            return claims;
        }
    }

    @Override
    public boolean changeGachaOperation(UUID playerId, UUID operation, String expected, String next) throws SQLException {
        try (Connection conn = getConnection()) {
            return changeGachaOperation(conn, playerId, operation, expected, next);
        }
    }

    private boolean changeGachaOperation(Connection conn, UUID playerId, UUID operation,
                                          String expected, String next) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tGachaOperations
            + " SET status = ? WHERE active_player = ? AND operation_id = ? AND status = ?")) {
            ps.setString(1, next);
            ps.setString(2, playerId.toString());
            ps.setString(3, operation.toString());
            ps.setString(4, expected);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean finishGachaOperation(UUID playerId, UUID operation, String expected) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tGachaOperations
                 + " WHERE active_player = ? AND operation_id = ? AND status = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, operation.toString());
            ps.setString(3, expected);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean appendGachaEvidence(UUID playerId, UUID operation, String phase, String details) throws SQLException {
        try (Connection conn = getConnection()) {
            return appendGachaEvidence(conn, playerId, operation, phase, details);
        }
    }

    private boolean appendGachaEvidence(Connection conn, UUID playerId, UUID operation,
                                        String phase, String details) throws SQLException {
        String append = isMysql() ? "CONCAT(details, ?)" : "details || ?";
        try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tGachaOperations
            + " SET details = " + append + " WHERE active_player = ? AND operation_id = ? AND status = ?")) {
            ps.setString(1, "\n" + details);
            ps.setString(2, playerId.toString());
            ps.setString(3, operation.toString());
            ps.setString(4, phase);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean resolveGachaOperation(UUID playerId, UUID operation, String expected, String reason) throws SQLException {
        String append = isMysql() ? "CONCAT(details, ?)" : "details || ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE " + tGachaOperations
                 + " SET active_player = NULL, status = 'RESOLVED', details = " + append
                 + " WHERE active_player = ? AND operation_id = ? AND status = ?")) {
            ps.setString(1, "\n人工核对 " + java.time.Instant.now() + " [" + expected + "]：" + reason);
            ps.setString(2, playerId.toString());
            ps.setString(3, operation.toString());
            ps.setString(4, expected);
            return ps.executeUpdate() == 1;
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
    public List<PendingClaim> loadTransferableClaims(UUID player, String target, int limit) throws SQLException {
        String sql = "SELECT id, pool_id, item_metadata, item_data, created_time, attempts FROM " + tPendingClaim
            + " WHERE player_uuid = ? AND claimed = ? AND item_data IS NOT NULL AND item_data <> ''"
            + " AND (transfer_target IS NULL OR transfer_target = ?) ORDER BY id LIMIT ?";
        try (var conn = getConnection(); var read = conn.prepareStatement(sql)) {
            read.setString(1, player.toString());
            read.setBoolean(2, false);
            read.setString(3, target);
            read.setInt(4, limit);
            List<PendingClaim> claims = new ArrayList<>();
            try (var rows = read.executeQuery()) {
                while (rows.next()) claims.add(new PendingClaim(rows.getLong(1), player, rows.getString(2),
                    rows.getString(3), rows.getString(4), rows.getLong(5), rows.getInt(6)));
            }
            return claims;
        }
    }

    @Override
    public UUID prepareClaimTransfer(UUID player, long claimId, String target) throws SQLException {
        String bind = "UPDATE " + tPendingClaim + " SET transfer_id = ?, transfer_target = ?"
            + " WHERE id = ? AND player_uuid = ? AND claimed = ? AND transfer_id IS NULL AND transfer_target IS NULL";
        String read = "SELECT transfer_id FROM " + tPendingClaim
            + " WHERE id = ? AND player_uuid = ? AND claimed = ? AND transfer_target = ?";
        try (var conn = getConnection()) {
            if (!conn.getAutoCommit()) throw new SQLException("Lottery transfer requires durable autocommit");
            UUID proposed = UUID.randomUUID();
            try (var stmt = conn.prepareStatement(bind)) {
                stmt.setString(1, proposed.toString());
                stmt.setString(2, target);
                stmt.setLong(3, claimId);
                stmt.setString(4, player.toString());
                stmt.setBoolean(5, false);
                if (stmt.executeUpdate() == 1) return proposed;
            }
            try (var stmt = conn.prepareStatement(read)) {
                stmt.setLong(1, claimId);
                stmt.setString(2, player.toString());
                stmt.setBoolean(3, false);
                stmt.setString(4, target);
                try (var row = stmt.executeQuery()) {
                    if (!row.next()) return null;
                    String stored = row.getString(1);
                    if (stored == null) throw new SQLException("Lottery claim transfer ID missing");
                    return UUID.fromString(stored);
                }
            }
        }
    }

    @Override
    public boolean confirmClaimTransfer(UUID player, long claimId, String target, UUID transfer) throws SQLException {
        String sql = "UPDATE " + tPendingClaim + " SET claimed = ?"
            + " WHERE id = ? AND player_uuid = ? AND claimed = ? AND transfer_id = ? AND transfer_target = ?";
        try (var conn = getConnection(); var stmt = conn.prepareStatement(sql)) {
            if (!conn.getAutoCommit()) throw new SQLException("Lottery transfer confirmation requires durable autocommit");
            stmt.setBoolean(1, true);
            stmt.setLong(2, claimId);
            stmt.setString(3, player.toString());
            stmt.setBoolean(4, false);
            stmt.setString(5, transfer.toString());
            stmt.setString(6, target);
            return stmt.executeUpdate() == 1;
        }
    }

    @Override
    public int countPendingClaims(UUID player) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tPendingClaim + " WHERE player_uuid = ? AND claimed = ?";
        try (var conn = getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setBoolean(2, false);
            try (var row = stmt.executeQuery()) { return row.next() ? row.getInt(1) : 0; }
        }
    }

    // ─── Logs ─────────────────────────────────────────────

    private void logGachaPull(Connection conn, UUID playerUuid, String poolId, int pullCount,
                              String itemsJson, int pityAtPull, boolean guaranteed) throws SQLException {
        String sql = "INSERT INTO " + tGachaLog + " (player_uuid, pool_id, pull_time, pull_count, items_json, pity_at_pull, is_guaranteed) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setInt(4, pullCount);
            ps.setString(5, itemsJson);
            ps.setInt(6, pityAtPull);
            ps.setBoolean(7, guaranteed);
            ps.executeUpdate();
        }
    }

    @Override
    public void logCaseOpen(@NotNull UUID playerUuid, @NotNull String poolId,
                             @NotNull String itemId, @NotNull String rarity) {
        try (Connection conn = getConnection()) {
            logCaseOpen(conn, playerUuid, poolId, itemId, rarity);
        } catch (SQLException e) {
            logger.warning("记录 Case 日志失败: " + e.getMessage());
        }
    }

    private void logCaseOpen(Connection conn, UUID playerUuid, String poolId, String itemId, String rarity) throws SQLException {
        String sql = "INSERT INTO " + tCaseLog + " (player_uuid, pool_id, open_time, item_id, rarity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, poolId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, itemId);
            ps.setString(5, rarity);
            ps.executeUpdate();
        }
    }

    @Override
    public @NotNull List<GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        List<GachaLogEntry> result = new ArrayList<>();
        try {
            readGachaHistory(playerUuid, poolId, limit, result);
        } catch (SQLException e) {
            logger.warning("查询 Gacha 历史失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<GachaLogEntry> loadGachaHistory(UUID playerId, String poolId, int limit) throws SQLException {
        List<GachaLogEntry> result = new ArrayList<>();
        readGachaHistory(playerId, poolId, limit, result);
        return List.copyOf(result);
    }

    private void readGachaHistory(UUID playerUuid, String poolId, int limit, List<GachaLogEntry> result) throws SQLException {
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
        }
    }

    @Override
    public @NotNull List<CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        List<CaseLogEntry> result = new ArrayList<>();
        try {
            readCaseHistory(playerUuid, poolId, limit, result);
        } catch (SQLException e) {
            logger.warning("查询 Case 历史失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<CaseLogEntry> loadCaseHistory(UUID playerId, String poolId, int limit) throws SQLException {
        List<CaseLogEntry> result = new ArrayList<>();
        readCaseHistory(playerId, poolId, limit, result);
        return List.copyOf(result);
    }

    private void readCaseHistory(UUID playerUuid, String poolId, int limit, List<CaseLogEntry> result) throws SQLException {
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
        }
    }

    @Override
    public @NotNull java.util.Map<String, Long> getPoints(@NotNull UUID playerUuid) {
        try {
            return loadAllPointBalances(playerUuid);
        } catch (SQLException e) {
            logger.warning("Failed to read points: " + e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<String, Long> loadAllPointBalances(UUID playerId) throws SQLException {
        try (Connection conn = getConnection()) {
            Map<String, Long> result = new LinkedHashMap<>();
            readPointBalances(conn, playerId, result);
            return Collections.unmodifiableMap(result);
        }
    }

    @Override
    public Map<String, Long> loadPointBalances(UUID playerId, Set<String> pointIds) throws SQLException {
        if (pointIds.isEmpty()) return Map.of();
        if (pointIds.size() > VIEW_KEYS_PER_QUERY) {
            return readProjection(conn -> readAmounts(conn, playerId, pointIds, tPoints, "point_id", ResultSet::getLong));
        }
        try (Connection conn = getConnection()) {
            return readAmounts(conn, playerId, pointIds, tPoints, "point_id", ResultSet::getLong);
        }
    }

    private void readPointBalances(Connection conn, UUID playerId, Map<String, Long> result) throws SQLException {
        String sql = "SELECT point_id, amount FROM " + tPoints + " WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("point_id"), rs.getLong("amount"));
            }
        }
    }

    @FunctionalInterface
    private interface AmountReader<T> {
        T read(ResultSet row, int column) throws SQLException;
    }

    private <T> Map<String, T> readAmounts(Connection conn, UUID playerId, Set<String> requestedIds,
                                           String table, String idColumn, AmountReader<T> reader) throws SQLException {
        if (requestedIds.isEmpty()) return Map.of();
        List<String> ids = new ArrayList<>(requestedIds);
        Map<String, T> amounts = new LinkedHashMap<>();
        // Bind the requested ID as the result key and preserve the scalar lookup's column collation.
        String select = "SELECT ? AS requested_id, amount FROM " + table
            + " WHERE player_uuid = ? AND " + idColumn + " = ?";
        String player = playerId.toString();
        for (int from = 0; from < ids.size(); from += VIEW_KEYS_PER_QUERY) {
            int end = Math.min(from + VIEW_KEYS_PER_QUERY, ids.size());
            String sql = String.join(" UNION ALL ", Collections.nCopies(end - from, select));
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int parameter = 1;
                for (int i = from; i < end; i++) {
                    String id = ids.get(i);
                    ps.setString(parameter++, id);
                    ps.setString(parameter++, player);
                    ps.setString(parameter++, id);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) amounts.put(rs.getString(1), reader.read(rs, 2));
                }
            }
        }
        return Collections.unmodifiableMap(amounts);
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
            return changePoints(conn, playerUuid, pointId, amount, subtract);
        } catch (SQLException e) {
            logger.warning("Failed to change points: " + e.getMessage());
            return false;
        }
    }

    private boolean changePoints(Connection conn, UUID playerId, String pointId, long amount,
                                 boolean subtract) throws SQLException {
        if (amount == 0) return true;
        if (amount < 0) throw new SQLException("负数积分变更");
        if (subtract) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tPoints
                + " SET amount = amount - ? WHERE player_uuid = ? AND point_id = ? AND amount >= ?")) {
                ps.setLong(1, amount);
                ps.setString(2, playerId.toString());
                ps.setString(3, pointId);
                ps.setLong(4, amount);
                return ps.executeUpdate() == 1;
            }
        }
        String sql = "INSERT INTO " + tPoints + " (player_uuid, point_id, amount) VALUES (?, ?, ?)"
            + (isMysql() ? " ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
                         : " ON CONFLICT(player_uuid, point_id) DO UPDATE SET amount = amount + excluded.amount");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, pointId);
            ps.setLong(3, amount);
            ps.executeUpdate();
            return true;
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
