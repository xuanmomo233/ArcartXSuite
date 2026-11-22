package xuanmo.arcartxsuite.fishing.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance;

/**
 * 基于 JDBC 的 {@link FishingRepository} 实现。
 * <p>
 * 关联模块：{@code fishing}。使用 SQLite/MySQL 存储，维护以下表：
 * <ul>
 *   <li>{@code axs_fishing_season} — 赛季数据（按 seasonId 隔离）</li>
 *   <li>{@code axs_fishing_permanent} — 永久数据（跨赛季保留）</li>
 *   <li>{@code axs_fishing_collection} — 图鉴条目（跨赛季保留）</li>
 * </ul>
 */
public final class JdbcFishingRepository extends AbstractModuleRepository implements FishingRepository {
    private static final Gson JSON = new Gson();
    private static final java.lang.reflect.Type REWARD_BATCH = new TypeToken<List<PendingReward>>() {}.getType();

    public JdbcFishingRepository(DataSource dataSource, StorageDescriptor descriptor,
                                 File dataFolder, Logger logger) {
        super("AXS-Fishing", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!isMysql()) {
                // ── SQLite 建表 ──────────────────────────────────────
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_season (
                        uuid TEXT NOT NULL,
                        season_id TEXT NOT NULL,
                        level INTEGER NOT NULL DEFAULT 1,
                        current_xp INTEGER NOT NULL DEFAULT 0,
                        talent_points INTEGER NOT NULL DEFAULT 0,
                        unspent_talent_points INTEGER NOT NULL DEFAULT 0,
                        talents TEXT NOT NULL DEFAULT '',
                        fatigue INTEGER NOT NULL DEFAULT 0,
                        claimed_max_fatigue_reward INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, season_id))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_permanent (
                        uuid TEXT PRIMARY KEY,
                        total_caught INTEGER NOT NULL DEFAULT 0,
                        perfect_catches INTEGER NOT NULL DEFAULT 0,
                        treasure_caught INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL)
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_collection (
                        uuid TEXT NOT NULL,
                        fish_id TEXT NOT NULL,
                        caught_count INTEGER NOT NULL DEFAULT 0,
                        max_size INTEGER NOT NULL DEFAULT 0,
                        first_catch_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, fish_id))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_catch_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        fish_id TEXT NOT NULL,
                        fish_name TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        size INTEGER NOT NULL DEFAULT 0,
                        perfect INTEGER NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL,
                        event_id TEXT NOT NULL UNIQUE)
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_bait_queue (
                        uuid TEXT NOT NULL,
                        slot_index INTEGER NOT NULL,
                        bait_id TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY (uuid, slot_index))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_task_progress (
                        uuid TEXT NOT NULL,
                        category TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        target_count INTEGER NOT NULL DEFAULT 1,
                        current_progress INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        reward_claimed INTEGER NOT NULL DEFAULT 0,
                        assigned_date TEXT NOT NULL,
                        period_key TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, category, task_id, period_key))
                    """);
            } else {
                // ── MySQL 建表 ───────────────────────────────────────
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_season (
                        uuid VARCHAR(36) NOT NULL,
                        season_id VARCHAR(64) NOT NULL,
                        level INT NOT NULL DEFAULT 1,
                        current_xp INT NOT NULL DEFAULT 0,
                        talent_points INT NOT NULL DEFAULT 0,
                        unspent_talent_points INT NOT NULL DEFAULT 0,
                        talents TEXT NOT NULL,
                        fatigue INT NOT NULL DEFAULT 0,
                        claimed_max_fatigue_reward INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, season_id))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_permanent (
                        uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        total_caught INT NOT NULL DEFAULT 0,
                        perfect_catches INT NOT NULL DEFAULT 0,
                        treasure_caught INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL)
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_collection (
                        uuid VARCHAR(36) NOT NULL,
                        fish_id VARCHAR(64) NOT NULL,
                        caught_count INT NOT NULL DEFAULT 0,
                        max_size INT NOT NULL DEFAULT 0,
                        first_catch_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, fish_id))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_catch_records (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        fish_id VARCHAR(64) NOT NULL,
                        fish_name VARCHAR(128) NOT NULL,
                        rarity VARCHAR(16) NOT NULL,
                        size INT NOT NULL DEFAULT 0,
                        perfect TINYINT NOT NULL DEFAULT 0,
                        timestamp BIGINT NOT NULL,
                        event_id VARCHAR(36) NOT NULL UNIQUE,
                        INDEX idx_catch_uuid (uuid),
                        INDEX idx_catch_time (timestamp))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_bait_queue (
                        uuid VARCHAR(36) NOT NULL,
                        slot_index INT NOT NULL,
                        bait_id VARCHAR(64) NOT NULL,
                        amount INT NOT NULL DEFAULT 1,
                        PRIMARY KEY (uuid, slot_index))
                    """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS axs_fishing_task_progress (
                        uuid VARCHAR(36) NOT NULL,
                        category VARCHAR(16) NOT NULL,
                        task_id VARCHAR(64) NOT NULL,
                        target_count INT NOT NULL DEFAULT 1,
                        current_progress INT NOT NULL DEFAULT 0,
                        completed TINYINT NOT NULL DEFAULT 0,
                        reward_claimed TINYINT NOT NULL DEFAULT 0,
                        assigned_date VARCHAR(10) NOT NULL,
                        period_key VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, category, task_id, period_key))
                    """);
            }
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_fishing_reward_delivery (
                    uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    reward_key VARCHAR(64) NOT NULL,
                    step INT NOT NULL,
                    delivered INT NOT NULL,
                    PRIMARY KEY (uuid, season_id, reward_key, step))
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_fishing_reward_plans (
                    uuid VARCHAR(36) NOT NULL,
                    season_id VARCHAR(64) NOT NULL,
                    reward_key VARCHAR(64) NOT NULL,
                    owner VARCHAR(64) NOT NULL,
                    payload LONGTEXT NOT NULL,
                    PRIMARY KEY (uuid, season_id, reward_key))
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_fishing_sales (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    request_id VARCHAR(36) NOT NULL,
                    owner VARCHAR(64) NOT NULL,
                    node_id VARCHAR(36) NOT NULL,
                    server_boot BIGINT NOT NULL,
                    source_session VARCHAR(36) NOT NULL,
                    currency VARCHAR(64) NOT NULL,
                    amount VARCHAR(64) NOT NULL,
                    fish %s NOT NULL,
                    state VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL%s)
                """.formatted(isMysql() ? "LONGTEXT" : "TEXT",
                        isMysql() ? ", INDEX idx_fishing_sale_recovery (node_id, state, uuid)" : ""));
            if (!isMysql()) stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_fishing_sale_recovery ON axs_fishing_sales(node_id, state, uuid)
                """);
            String deliveryIndex = isMysql() ? ", INDEX idx_fishing_delivery_retry (owner, retry_at)" : "";
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_fishing_catch_delivery (
                    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    owner VARCHAR(64) NOT NULL,
                    treasure INT NOT NULL,
                    payload %s NOT NULL,
                    retry_at BIGINT NOT NULL%s)
                """.formatted(isMysql() ? "LONGTEXT" : "TEXT", deliveryIndex));
            if (!isMysql()) stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_fishing_delivery_retry ON axs_fishing_catch_delivery(owner, retry_at)
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS axs_fishing_bait_deposits (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    request_id VARCHAR(36) NOT NULL,
                    node_id VARCHAR(36) NOT NULL,
                    server_boot BIGINT NOT NULL,
                    slot_index INT NOT NULL,
                    bait_id VARCHAR(64) NOT NULL,
                    amount INT NOT NULL%s)
                """.formatted(isMysql() ? ", INDEX idx_fishing_bait_deposit_node (node_id, uuid)" : ""));
            if (!isMysql()) stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_fishing_bait_deposit_node ON axs_fishing_bait_deposits(node_id, uuid)
                """);
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return allTables();
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    @Override
    protected List<String> allTables() {
        return List.of("axs_fishing_season", "axs_fishing_permanent", "axs_fishing_collection",
            "axs_fishing_catch_records", "axs_fishing_bait_queue",
            "axs_fishing_task_progress", "axs_fishing_reward_delivery", "axs_fishing_reward_plans", "axs_fishing_catch_delivery",
            "axs_fishing_bait_deposits", "axs_fishing_sales");
    }

    @Override
    public boolean reserveFishSale(FishSale sale) throws SQLException {
        String sql = """
            INSERT INTO axs_fishing_sales (uuid, request_id, owner, node_id, server_boot, source_session, currency, amount, fish, state, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sale.player().toString());
            ps.setString(2, sale.request().toString());
            ps.setString(3, sale.owner());
            ps.setString(4, sale.node());
            ps.setLong(5, sale.boot());
            ps.setString(6, sale.sourceSession().toString());
            ps.setString(7, sale.currency());
            ps.setString(8, sale.amount());
            ps.setString(9, JSON.toJson(sale.fish(), REWARD_BATCH));
            ps.setLong(10, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        } catch (SQLException failure) {
            // The only uniqueness constraint is the active sale for this player.
            if (isMysql() ? failure.getErrorCode() == 1062 : failure.getErrorCode() == 19) return false;
            throw failure;
        }
    }

    @Override
    public boolean beginFishSalePayment(FishSale sale) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("""
                UPDATE axs_fishing_sales SET state='PAYING' WHERE uuid=? AND request_id=? AND node_id=? AND state='RESERVED'
                """)) {
            ps.setString(1, sale.player().toString());
            ps.setString(2, sale.request().toString());
            ps.setString(3, sale.node());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public void cancelUnstartedFishSale(FishSale sale) throws SQLException {
        if (!recoverUnpaidFishSale(sale, false)) throw new SQLException("售鱼预约已变化: " + sale.request());
    }

    @Override
    public void finishFishSale(FishSale sale, boolean paid) throws SQLException {
        if (!closeFishSale(sale, "PAYING", !paid)) throw new SQLException("售鱼预约已变化: " + sale.request());
    }

    @Override
    public List<FishSale> reservedFishSales(String node, String after, int limit) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT uuid, request_id, owner, server_boot, source_session, currency, amount, fish FROM axs_fishing_sales
                WHERE node_id=? AND state='RESERVED' AND uuid>? ORDER BY uuid LIMIT ?
                """)) {
            ps.setString(1, node);
            ps.setString(2, after);
            ps.setInt(3, limit);
            try (ResultSet rows = ps.executeQuery()) {
                var sales = new ArrayList<FishSale>();
                while (rows.next()) {
                    sales.add(new FishSale(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                        rows.getString(3), node, rows.getLong(4), UUID.fromString(rows.getString(5)),
                        rows.getString(6), rows.getString(7), JSON.fromJson(rows.getString(8), REWARD_BATCH)));
                }
                return sales;
            }
        }
    }

    @Override
    public boolean recoverUnpaidFishSale(FishSale sale, boolean removedFromInventory) throws SQLException {
        return closeFishSale(sale, "RESERVED", removedFromInventory);
    }

    private boolean closeFishSale(FishSale sale, String state, boolean returnFish) throws SQLException {
        return transaction(conn -> {
            if (!deleteFishSale(conn, sale, state)) return false;
            if (returnFish) {
                // Closing the sale and enqueuing the exact fish are one commit.
                insertDelivery(conn, sale.request(), sale.player(), sale.owner(), false, sale.fish(), System.currentTimeMillis());
            }
            return true;
        });
    }

    private boolean deleteFishSale(Connection conn, FishSale sale, String state) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM axs_fishing_sales WHERE uuid=? AND request_id=? AND node_id=? AND state=?
                """)) {
            ps.setString(1, sale.player().toString());
            ps.setString(2, sale.request().toString());
            ps.setString(3, sale.node());
            ps.setString(4, state);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public String reserveRewardPlan(UUID uuid, String seasonId, String rewardKey, String owner, String payload)
            throws SQLException {
        String sql = (isMysql() ? "INSERT IGNORE" : "INSERT")
            + " INTO axs_fishing_reward_plans (uuid, season_id, reward_key, owner, payload)"
            + " SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM axs_fishing_reward_delivery"
            + " WHERE uuid=? AND season_id=? AND reward_key=? AND delivered=0)"
            + (isMysql() ? "" : " ON CONFLICT(uuid, season_id, reward_key) DO NOTHING");
        try (Connection conn = getConnection()) {
            if (payload != null) {
                try (var insert = conn.prepareStatement(sql)) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, seasonId);
                    insert.setString(3, rewardKey);
                    insert.setString(4, owner);
                    insert.setString(5, payload);
                    insert.setString(6, uuid.toString());
                    insert.setString(7, seasonId);
                    insert.setString(8, rewardKey);
                    if (insert.executeUpdate() == 1) return payload;
                }
            }
            try (var select = conn.prepareStatement("""
                    SELECT owner, payload FROM axs_fishing_reward_plans WHERE uuid=? AND season_id=? AND reward_key=?
                    """)) {
                select.setString(1, uuid.toString());
                select.setString(2, seasonId);
                select.setString(3, rewardKey);
                try (var row = select.executeQuery()) {
                    return row.next() && owner.equals(row.getString(1)) ? row.getString(2) : null;
                }
            }
        }
    }

    @Override
    public RewardReservation reserveReward(UUID uuid, String seasonId, String rewardKey, int step) throws SQLException {
        try (Connection conn = getConnection()) {
            if (insertRewardReceipt(conn, uuid, seasonId, rewardKey, step, false)) return RewardReservation.ACQUIRED;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT delivered FROM axs_fishing_reward_delivery WHERE uuid=? AND season_id=? AND reward_key=? AND step=?")) {
                bindRewardKey(ps, uuid, seasonId, rewardKey, step);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1) ? RewardReservation.DELIVERED : RewardReservation.IN_DOUBT;
                }
            }
        }
    }

    @Override
    public void finishReward(UUID uuid, String seasonId, String rewardKey, int step, boolean delivered) throws SQLException {
        String sql = delivered ? "UPDATE axs_fishing_reward_delivery SET delivered=1"
            : "DELETE FROM axs_fishing_reward_delivery";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                sql + " WHERE uuid=? AND season_id=? AND reward_key=? AND step=? AND delivered=0")) {
            bindRewardKey(ps, uuid, seasonId, rewardKey, step);
            if (ps.executeUpdate() == 1) return;
            if (delivered) {
                try (var select = conn.prepareStatement(
                        "SELECT delivered FROM axs_fishing_reward_delivery WHERE uuid=? AND season_id=? AND reward_key=? AND step=?")) {
                    bindRewardKey(select, uuid, seasonId, rewardKey, step);
                    try (var row = select.executeQuery()) {
                        if (row.next() && row.getBoolean(1)) return;
                    }
                }
            }
            throw new SQLException("奖励发放记录已变化");
        }
    }

    @Override
    public void markRewardComplete(UUID uuid, String seasonId, String rewardKey) throws SQLException {
        try (Connection conn = getConnection()) {
            insertRewardReceipt(conn, uuid, seasonId, rewardKey, -1, true);
        }
    }

    @Override
    public Set<String> completedRewardKeys(UUID uuid, String seasonId) {
        Set<String> keys = new HashSet<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT reward_key FROM axs_fishing_reward_delivery WHERE uuid=? AND season_id=? AND step=-1 AND delivered=1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) keys.add(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.warning("读取钓鱼奖励领取状态失败 uuid=" + uuid + ": " + e.getMessage());
        }
        return keys;
    }

    private boolean insertRewardReceipt(Connection conn, UUID uuid, String seasonId, String key, int step,
                                        boolean delivered) throws SQLException {
        String sql = (isMysql() ? "INSERT IGNORE" : "INSERT")
            + " INTO axs_fishing_reward_delivery (uuid, season_id, reward_key, step, delivered) VALUES (?, ?, ?, ?, ?)"
            + (isMysql() ? "" : " ON CONFLICT(uuid, season_id, reward_key, step) DO NOTHING");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRewardKey(ps, uuid, seasonId, key, step);
            ps.setBoolean(5, delivered);
            return ps.executeUpdate() == 1;
        }
    }

    private static void bindRewardKey(PreparedStatement ps, UUID uuid, String seasonId, String key, int step)
            throws SQLException {
        ps.setString(1, uuid.toString());
        ps.setString(2, seasonId);
        ps.setString(3, key);
        ps.setInt(4, step);
    }

    // ─── 赛季数据 ────────────────────────────────────────────────

    @Override
    public @NotNull FishingSeasonData loadSeasonData(@NotNull UUID uuid, @NotNull String seasonId) {
        try (Connection conn = getConnection()) {
            return loadSeasonData(conn, uuid, seasonId, false);
        } catch (SQLException e) {
            logger.warning("加载赛季数据失败 [" + seasonId + "]: " + e.getMessage());
            return FishingSeasonData.empty(uuid, seasonId);
        }
    }

    @Override
    public DisplayData loadDisplayData(UUID uuid, String seasonId) throws SQLException {
        String sql = """
            SELECT s.level, s.current_xp, s.talent_points, s.unspent_talent_points, s.fatigue,
                   p.total_caught, p.perfect_catches, p.treasure_caught, f.fish_id, f.caught_count
            FROM (SELECT ? AS profile_id) lookup_key
            LEFT JOIN axs_fishing_season s ON s.uuid = lookup_key.profile_id AND s.season_id = ?
            LEFT JOIN axs_fishing_permanent p ON p.uuid = lookup_key.profile_id
            LEFT JOIN axs_fishing_collection f ON f.uuid = lookup_key.profile_id
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Fishing display query returned no profile row");
                var counts = new HashMap<String, Integer>();
                var data = new DisplayData(rs.getInt("level"), rs.getInt("current_xp"), rs.getInt("talent_points"),
                    rs.getInt("unspent_talent_points"), rs.getInt("fatigue"), rs.getInt("total_caught"),
                    rs.getInt("perfect_catches"), rs.getInt("treasure_caught"), counts);
                do {
                    String fishId = rs.getString("fish_id");
                    if (fishId != null) counts.put(fishId, rs.getInt("caught_count"));
                } while (rs.next());
                return data;
            }
        }
    }

    private FishingSeasonData loadSeasonData(Connection conn, UUID uuid, String seasonId, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT level, current_xp, talent_points, unspent_talent_points, talents,"
            + " fatigue, claimed_max_fatigue_reward FROM axs_fishing_season WHERE uuid=? AND season_id=?"
            + (forUpdate && isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishingSeasonData(uuid, seasonId, rs.getInt("level"), rs.getInt("current_xp"),
                        rs.getInt("talent_points"), rs.getInt("unspent_talent_points"),
                        parseTalentMap(rs.getString("talents")),
                        rs.getInt("fatigue"), rs.getBoolean("claimed_max_fatigue_reward"));
                }
            }
        }
        return FishingSeasonData.empty(uuid, seasonId);
    }

    @Override
    public void saveSeasonData(@NotNull FishingSeasonData data) {
        try (Connection conn = getConnection()) {
            saveSeasonData(conn, data);
        } catch (SQLException e) {
            logger.warning("保存赛季数据失败: " + e.getMessage());
        }
    }

    @Override
    public SeasonChange updateSeason(UUID uuid, String seasonId, UnaryOperator<FishingSeasonData> update) throws SQLException {
        return transaction(conn -> mutateSeason(conn, uuid, seasonId, update));
    }

    private <T> T transaction(Transaction<T> operation) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operation.run(conn);
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

    @FunctionalInterface
    private interface Transaction<T> {
        T run(Connection conn) throws SQLException;
    }

    private SeasonChange mutateSeason(Connection conn, UUID uuid, String seasonId,
                                      UnaryOperator<FishingSeasonData> update) throws SQLException {
        // Acquire a write lock even for the first action in a season. SQLite takes a writer lock;
        // MySQL takes the row lock before the read and keeps it until the enclosing transaction commits.
        String sql = """
            INSERT INTO axs_fishing_season (uuid, season_id, level, talents, updated_at) VALUES (?, ?, 0, '', ?)
            """ + (isMysql() ? " ON DUPLICATE KEY UPDATE updated_at=updated_at" : " ON CONFLICT(uuid, season_id) DO NOTHING");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        FishingSeasonData before = loadSeasonData(conn, uuid, seasonId, true);
        FishingSeasonData after = update.apply(before);
        if (!uuid.equals(after.uuid()) || !seasonId.equals(after.seasonId())) {
            throw new IllegalArgumentException("Fishing season mutation changed its player or season");
        }
        if (!after.equals(before)) saveSeasonData(conn, after);
        return new SeasonChange(before, after);
    }

    private void saveSeasonData(Connection conn, FishingSeasonData data) throws SQLException {
        String sql = !isMysql()
            ? "INSERT INTO axs_fishing_season (uuid, season_id, level, current_xp, "
            + "talent_points, unspent_talent_points, talents, fatigue, claimed_max_fatigue_reward, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, season_id) DO UPDATE SET level=excluded.level, current_xp=excluded.current_xp, "
            + "talent_points=excluded.talent_points, unspent_talent_points=excluded.unspent_talent_points, "
            + "talents=excluded.talents, "
            + "fatigue=excluded.fatigue, claimed_max_fatigue_reward=excluded.claimed_max_fatigue_reward, "
            + "updated_at=excluded.updated_at"
            : "INSERT INTO axs_fishing_season (uuid, season_id, level, current_xp, "
            + "talent_points, unspent_talent_points, talents, fatigue, claimed_max_fatigue_reward, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE level=VALUES(level), current_xp=VALUES(current_xp), "
            + "talent_points=VALUES(talent_points), unspent_talent_points=VALUES(unspent_talent_points), "
            + "talents=VALUES(talents), "
            + "fatigue=VALUES(fatigue), claimed_max_fatigue_reward=VALUES(claimed_max_fatigue_reward), "
            + "updated_at=VALUES(updated_at)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setString(2, data.seasonId());
            ps.setInt(3, data.level());
            ps.setInt(4, data.currentXp());
            ps.setInt(5, data.talentPoints());
            ps.setInt(6, data.unspentTalentPoints());
            ps.setString(7, serializeTalentMap(data.talents()));
            ps.setInt(8, data.fatigue());
            ps.setBoolean(9, data.claimedMaxFatigueReward());
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public void clearSeasonData(@NotNull String seasonId) {
        String sql = "DELETE FROM axs_fishing_season WHERE season_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seasonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("清除赛季数据失败 [" + seasonId + "]: " + e.getMessage());
        }
    }

    @Override
    public void recoverFatigue(UUID uuid, String seasonId, int expectedFatigue, int newFatigue) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE axs_fishing_season SET fatigue=? WHERE uuid=? AND season_id=? AND fatigue=?")) {
            ps.setInt(1, newFatigue);
            ps.setString(2, uuid.toString());
            ps.setString(3, seasonId);
            ps.setInt(4, expectedFatigue);
            ps.executeUpdate();
        }
    }

    // ─── 永久数据 ────────────────────────────────────────────────

    @Override
    public @NotNull FishingPermanentData loadPermanentData(@NotNull UUID uuid) {
        try (Connection conn = getConnection()) {
            return loadPermanentData(conn, uuid);
        } catch (SQLException e) {
            logger.warning("加载永久数据失败: " + e.getMessage());
            return FishingPermanentData.empty(uuid);
        }
    }

    private FishingPermanentData loadPermanentData(Connection conn, UUID uuid) throws SQLException {
        String sql = "SELECT total_caught, perfect_catches, treasure_caught FROM axs_fishing_permanent WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishingPermanentData(
                        uuid,
                        rs.getInt("total_caught"),
                        rs.getInt("perfect_catches"),
                        rs.getInt("treasure_caught")
                    );
                }
            }
        }
        return FishingPermanentData.empty(uuid);
    }

    @Override
    public void savePermanentData(@NotNull FishingPermanentData data) {
        String sql = !isMysql()
            ? "INSERT INTO axs_fishing_permanent (uuid, total_caught, perfect_catches, treasure_caught, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid) DO UPDATE SET total_caught=excluded.total_caught, "
            + "perfect_catches=excluded.perfect_catches, treasure_caught=excluded.treasure_caught, "
            + "updated_at=excluded.updated_at"
            : "INSERT INTO axs_fishing_permanent (uuid, total_caught, perfect_catches, treasure_caught, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE total_caught=VALUES(total_caught), "
            + "perfect_catches=VALUES(perfect_catches), treasure_caught=VALUES(treasure_caught), "
            + "updated_at=VALUES(updated_at)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setInt(2, data.totalCaught());
            ps.setInt(3, data.perfectCatches());
            ps.setInt(4, data.treasureCaught());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存永久数据失败: " + e.getMessage());
        }
    }

    // ─── 图鉴 ────────────────────────────────────────────────────

    @Override
    public @Nullable FishCollectionEntry loadCollectionEntry(@NotNull UUID playerUuid, @NotNull String fishId) {
        String sql = "SELECT caught_count, max_size, first_catch_at FROM axs_fishing_collection WHERE uuid = ? AND fish_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, fishId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishCollectionEntry(
                        playerUuid, fishId,
                        rs.getInt("caught_count"),
                        rs.getInt("max_size"),
                        rs.getLong("first_catch_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("加载图鉴条目失败: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveCollectionEntry(@NotNull FishCollectionEntry entry) {
        String sql = !isMysql()
            ? "INSERT INTO axs_fishing_collection (uuid, fish_id, caught_count, max_size, first_catch_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, fish_id) DO UPDATE SET caught_count=excluded.caught_count, "
            + "max_size=excluded.max_size, first_catch_at=excluded.first_catch_at"
            : "INSERT INTO axs_fishing_collection (uuid, fish_id, caught_count, max_size, first_catch_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE caught_count=VALUES(caught_count), "
            + "max_size=VALUES(max_size), first_catch_at=VALUES(first_catch_at)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.playerUuid().toString());
            ps.setString(2, entry.fishId());
            ps.setInt(3, entry.caughtCount());
            ps.setInt(4, entry.maxSize());
            ps.setLong(5, entry.firstCatchAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存图鉴条目失败: " + e.getMessage());
        }
    }

    @Override
    public @NotNull List<FishCollectionEntry> loadCollection(@NotNull UUID playerUuid) {
        List<FishCollectionEntry> result = new ArrayList<>();
        String sql = "SELECT fish_id, caught_count, max_size, first_catch_at FROM axs_fishing_collection WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FishCollectionEntry(
                        playerUuid,
                        rs.getString("fish_id"),
                        rs.getInt("caught_count"),
                        rs.getInt("max_size"),
                        rs.getLong("first_catch_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("加载图鉴数据失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public int countCollection(UUID playerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM axs_fishing_collection WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException error) {
            logger.warning("读取钓鱼图鉴数量失败: " + error.getMessage());
            return 0;
        }
    }

    // ─── 捕获记录 ────────────────────────────────────────────────

    @Override
    public void saveCatchRecord(@NotNull FishingCatchRecord record) {
        saveCatchRecordConfirmed(record);
    }

    @Override
    public boolean saveCatchRecordConfirmed(@NotNull FishingCatchRecord record) {
        try (Connection conn = getConnection()) {
            insertCatchRecord(conn, record, true);
            return true;
        } catch (SQLException e) {
            logger.warning("保存捕获记录失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CatchSettlement settleCatch(FishingCatchRecord record, String seasonId,
                                       UnaryOperator<FishingSeasonData> updateSeason, @Nullable CatchRewards rewards)
            throws SQLException {
        return transaction(conn -> {
            // A duplicate local capture raises a constraint error before any counters can change.
            insertCatchRecord(conn, record, false);
            UUID uuid = record.playerUuid();
            SeasonChange season = mutateSeason(conn, uuid, seasonId, updateSeason);
            boolean firstCatch = incrementCollection(conn, record);
            incrementCatches(conn, record);
            var delivery = rewards == null ? null : insertCatchDelivery(conn, record, rewards, firstCatch);
            return new CatchSettlement(season, loadPermanentData(conn, uuid), firstCatch, delivery);
        });
    }

    private boolean incrementCollection(Connection conn, FishingCatchRecord record) throws SQLException {
        String sql = """
            INSERT INTO axs_fishing_collection (uuid, fish_id, caught_count, max_size, first_catch_at)
            VALUES (?, ?, 1, ?, ?)
            """ + (isMysql()
                ? " ON DUPLICATE KEY UPDATE caught_count=caught_count+1, max_size=GREATEST(max_size, VALUES(max_size))"
                : " ON CONFLICT(uuid, fish_id) DO UPDATE SET caught_count=caught_count+1, max_size=MAX(max_size, excluded.max_size)");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.playerUuid().toString());
            ps.setString(2, record.fishId());
            ps.setInt(3, record.size());
            ps.setLong(4, record.timestamp());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT caught_count FROM axs_fishing_collection WHERE uuid=? AND fish_id=?")) {
            ps.setString(1, record.playerUuid().toString());
            ps.setString(2, record.fishId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private void incrementCatches(Connection conn, FishingCatchRecord record) throws SQLException {
        String sql = """
            INSERT INTO axs_fishing_permanent (uuid, total_caught, perfect_catches, updated_at) VALUES (?, 1, ?, ?)
            """ + (isMysql()
                ? " ON DUPLICATE KEY UPDATE total_caught=total_caught+1, perfect_catches=perfect_catches+VALUES(perfect_catches), updated_at=VALUES(updated_at)"
                : " ON CONFLICT(uuid) DO UPDATE SET total_caught=total_caught+1, perfect_catches=perfect_catches+excluded.perfect_catches, updated_at=excluded.updated_at");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.playerUuid().toString());
            ps.setInt(2, record.perfect() ? 1 : 0);
            ps.setLong(3, record.timestamp());
            ps.executeUpdate();
        }
    }

    private CatchDelivery insertCatchDelivery(Connection conn, FishingCatchRecord record, CatchRewards rewards,
                                              boolean firstCatch) throws SQLException {
        var batch = rewards.rewards().stream().filter(choice -> firstCatch || !choice.firstOnly())
            .map(ConditionalReward::reward).toList();
        return insertDelivery(conn, record.eventId(), record.playerUuid(), rewards.owner(), rewards.treasure(), batch, record.timestamp());
    }

    private CatchDelivery insertDelivery(Connection conn, UUID request, UUID player, String owner, boolean treasure,
                                         List<PendingReward> batch, long timestamp) throws SQLException {
        if (batch.isEmpty()) throw new IllegalArgumentException("Fishing delivery is empty");
        var delivery = new CatchDelivery(request, player, owner, treasure, batch);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO axs_fishing_catch_delivery (event_id, uuid, owner, treasure, payload, retry_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, delivery.eventId().toString());
            ps.setString(2, delivery.player().toString());
            ps.setString(3, delivery.owner());
            ps.setBoolean(4, delivery.treasure());
            ps.setString(5, JSON.toJson(batch, REWARD_BATCH));
            ps.setLong(6, timestamp);
            ps.executeUpdate();
        }
        return delivery;
    }

    @Override
    public List<CatchDelivery> pendingCatchDeliveries(String owner, long now, int limit) throws SQLException {
        var deliveries = new ArrayList<CatchDelivery>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT event_id, uuid, treasure, payload FROM axs_fishing_catch_delivery
                WHERE owner=? AND retry_at<=? ORDER BY retry_at LIMIT ?
                """)) {
            ps.setString(1, owner);
            ps.setLong(2, now);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<PendingReward> batch = JSON.fromJson(rs.getString("payload"), REWARD_BATCH);
                    deliveries.add(new CatchDelivery(UUID.fromString(rs.getString("event_id")),
                        UUID.fromString(rs.getString("uuid")), owner, rs.getBoolean("treasure"), batch));
                }
            }
        }
        return deliveries;
    }

    @Override
    public void deferCatchDelivery(CatchDelivery delivery, long retryAt) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE axs_fishing_catch_delivery SET retry_at=? WHERE event_id=? AND owner=?")) {
            ps.setLong(1, retryAt);
            ps.setString(2, delivery.eventId().toString());
            ps.setString(3, delivery.owner());
            ps.executeUpdate();
        }
    }

    @Override
    public void finishCatchDelivery(CatchDelivery delivery) throws SQLException {
        transaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM axs_fishing_catch_delivery WHERE event_id=? AND owner=? AND uuid=?")) {
                ps.setString(1, delivery.eventId().toString());
                ps.setString(2, delivery.owner());
                ps.setString(3, delivery.player().toString());
                if (ps.executeUpdate() != 1 || !delivery.treasure()) return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE axs_fishing_permanent SET treasure_caught=treasure_caught+1, updated_at=? WHERE uuid=?")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, delivery.player().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private void insertCatchRecord(Connection conn, FishingCatchRecord record, boolean allowReplay) throws SQLException {
        String sql = "INSERT INTO axs_fishing_catch_records "
            + "(uuid, fish_id, fish_name, rarity, size, perfect, timestamp, event_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        if (allowReplay) {
            sql += isMysql() ? " ON DUPLICATE KEY UPDATE event_id=event_id" : " ON CONFLICT(event_id) DO NOTHING";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.playerUuid().toString());
            ps.setString(2, record.fishId());
            ps.setString(3, record.fishName());
            ps.setString(4, record.rarity());
            ps.setInt(5, record.size());
            ps.setInt(6, record.perfect() ? 1 : 0);
            ps.setLong(7, record.timestamp());
            ps.setString(8, record.eventId().toString());
            ps.executeUpdate();
        }
    }

    @Override
    @NotNull
    public List<FishingCatchRecord> loadCatchRecords(@NotNull UUID uuid, int limit) {
        List<FishingCatchRecord> result = new ArrayList<>();
        String sql = "SELECT id, event_id, fish_id, fish_name, rarity, size, perfect, timestamp "
            + "FROM axs_fishing_catch_records WHERE uuid = ? ORDER BY timestamp DESC, id DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FishingCatchRecord(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("event_id")),
                        uuid,
                        rs.getString("fish_id"),
                        rs.getString("fish_name"),
                        rs.getString("rarity"),
                        rs.getInt("size"),
                        rs.getInt("perfect") != 0,
                        rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("加载捕获记录失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void clearCatchRecords(@NotNull UUID uuid) {
        String sql = "DELETE FROM axs_fishing_catch_records WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("清除捕获记录失败: " + e.getMessage());
        }
    }

    // ─── 工具方法 ────────────────────────────────────────────────

    /**
     * 解析天赋节点 ID → 等级的映射。
     * 存储格式：{@code "node1:2,node2:1,node3:5"}（ID:等级，逗号分隔）。
     * 兼容旧格式（无冒号视为 1 级）。
     */
    private static Map<String, Integer> parseTalentMap(String csv) {
        Map<String, Integer> map = new HashMap<>();
        if (csv == null || csv.isBlank()) return map;
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                String id = trimmed.substring(0, colonIdx);
                try {
                    int level = Integer.parseInt(trimmed.substring(colonIdx + 1));
                    map.put(id, level);
                } catch (NumberFormatException e) {
                    map.put(id, 1); // 解析失败回退为 1 级
                }
            } else {
                // 兼容旧格式：无冒号视为 1 级
                map.put(trimmed, 1);
            }
        }
        return map;
    }

    /**
     * 序列化天赋节点 ID → 等级的映射为 CSV 字符串。
     */
    private static String serializeTalentMap(java.util.Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    // ─── 饵料队列 ──────────────────────────────────────────────────

    @Override
    public BaitEdit loadBaitEdit(UUID uuid, String seasonId, int first, int second) throws SQLException {
        try (Connection conn = getConnection()) {
            return loadBaitEdit(conn, uuid, seasonId, first, second, false);
        }
    }

    private BaitEdit loadBaitEdit(Connection conn, UUID uuid, String seasonId, int first, int second, boolean lock)
            throws SQLException {
        var season = loadSeasonData(conn, uuid, seasonId, lock);
        var a = new BaitQueueEntry(uuid, first, "", 0);
        var b = new BaitQueueEntry(uuid, second, "", 0);
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT slot_index, bait_id, amount FROM axs_fishing_bait_queue
                WHERE uuid=? AND slot_index IN (?,?) ORDER BY slot_index
                """ + (lock && isMysql() ? " FOR UPDATE" : ""))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, first);
            ps.setInt(3, second);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var entry = new BaitQueueEntry(uuid, rs.getInt("slot_index"), rs.getString("bait_id"), rs.getInt("amount"));
                    if (entry.slotIndex() == first) a = entry;
                    if (entry.slotIndex() == second) b = entry;
                }
            }
        }
        return new BaitEdit(season, a, b);
    }

    @Override
    public boolean swapBaitEdit(BaitEdit expected) throws SQLException {
        return transaction(conn -> {
            if (!matchesBaitEdit(conn, expected) || touchesBaitDeposit(conn, expected.first(), expected.second())) return false;
            if (!swapBaitSlots(conn, expected.first(), expected.second())) {
                conn.rollback();
                return false;
            }
            return true;
        });
    }

    @Override
    public boolean reserveBaitDeposit(BaitEdit expected, BaitDeposit deposit) throws SQLException {
        var slot = expected.first();
        if (!slot.playerUuid().equals(deposit.player()) || slot.slotIndex() != deposit.slot()
                || deposit.amount() <= 0 || deposit.bait().isBlank()
                || slot.amount() > 0 && !slot.baitId().equals(deposit.bait())
                || (long) slot.amount() + deposit.amount() > Integer.MAX_VALUE) return false;
        return transaction(conn -> {
            if (!matchesBaitEdit(conn, expected) || hasBaitDeposit(conn, deposit.player())) return false;
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO axs_fishing_bait_deposits (uuid, request_id, node_id, server_boot, slot_index, bait_id, amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, deposit.player().toString());
                ps.setString(2, deposit.request().toString());
                ps.setString(3, deposit.node());
                ps.setLong(4, deposit.boot());
                ps.setInt(5, deposit.slot());
                ps.setString(6, deposit.bait());
                ps.setInt(7, deposit.amount());
                return ps.executeUpdate() == 1;
            }
        });
    }

    private boolean hasBaitDeposit(Connection conn, UUID player) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT slot_index FROM axs_fishing_bait_deposits WHERE uuid=?" + (isMysql() ? " FOR UPDATE" : ""))) {
            ps.setString(1, player.toString());
            try (ResultSet rows = ps.executeQuery()) { return rows.next(); }
        }
    }

    private boolean touchesBaitDeposit(Connection conn, BaitQueueEntry first, BaitQueueEntry second) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM axs_fishing_bait_deposits WHERE uuid=? AND slot_index IN (?,?)
                """ + (isMysql() ? " FOR UPDATE" : ""))) {
            ps.setString(1, first.playerUuid().toString());
            ps.setInt(2, first.slotIndex());
            ps.setInt(3, second.slotIndex());
            try (ResultSet rows = ps.executeQuery()) { return rows.next(); }
        }
    }

    @Override
    public List<BaitDeposit> pendingBaitDeposits(String node, String after, int limit) throws SQLException {
        var result = new ArrayList<BaitDeposit>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT uuid, request_id, server_boot, slot_index, bait_id, amount FROM axs_fishing_bait_deposits
                WHERE node_id=? AND uuid>? ORDER BY uuid LIMIT ?
                """)) {
            ps.setString(1, node);
            ps.setString(2, after);
            ps.setInt(3, limit);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    result.add(new BaitDeposit(UUID.fromString(rows.getString("uuid")),
                        UUID.fromString(rows.getString("request_id")), node, rows.getLong("server_boot"),
                        rows.getInt("slot_index"), rows.getString("bait_id"), rows.getInt("amount")));
                }
            }
        }
        return result;
    }

    @Override
    public boolean publishBaitDeposit(BaitDeposit deposit) throws SQLException {
        return transaction(conn -> {
            var current = readBaitSlot(conn, deposit);
            if (current != null && !current.baitId().equals(deposit.bait())) return false;
            long amount = (current == null ? 0L : current.amount()) + deposit.amount();
            if (amount <= 0 || amount > Integer.MAX_VALUE || !deleteBaitDeposit(conn, deposit)) return false;
            var replacement = new BaitQueueEntry(deposit.player(), deposit.slot(), deposit.bait(), (int) amount);
            if (!replaceBaitSlot(conn, current, replacement)) {
                conn.rollback();
                return false;
            }
            return true;
        });
    }

    private @Nullable BaitQueueEntry readBaitSlot(Connection conn, BaitDeposit deposit) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT bait_id, amount FROM axs_fishing_bait_queue WHERE uuid=? AND slot_index=?
                """ + (isMysql() ? " FOR UPDATE" : ""))) {
            ps.setString(1, deposit.player().toString());
            ps.setInt(2, deposit.slot());
            try (ResultSet rows = ps.executeQuery()) {
                return rows.next() ? new BaitQueueEntry(deposit.player(), deposit.slot(), rows.getString("bait_id"),
                    rows.getInt("amount")) : null;
            }
        }
    }

    @Override
    public boolean cancelBaitDeposit(BaitDeposit deposit) throws SQLException {
        try (Connection conn = getConnection()) {
            return deleteBaitDeposit(conn, deposit);
        }
    }

    private boolean deleteBaitDeposit(Connection conn, BaitDeposit deposit) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM axs_fishing_bait_deposits WHERE uuid=? AND request_id=? AND node_id=?
                AND server_boot=? AND slot_index=? AND bait_id=? AND amount=?
                """)) {
            ps.setString(1, deposit.player().toString());
            ps.setString(2, deposit.request().toString());
            ps.setString(3, deposit.node());
            ps.setLong(4, deposit.boot());
            ps.setInt(5, deposit.slot());
            ps.setString(6, deposit.bait());
            ps.setInt(7, deposit.amount());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean withdrawBait(BaitEdit expected, int amount, UUID request, String owner, PendingReward reward) throws SQLException {
        var first = expected.first();
        UUID player = first.playerUuid();
        if (amount <= 0 || amount > first.amount() || !player.equals(reward.playerUuid())
                || !"fishing".equals(reward.sourceModule()) || reward.type() != PendingReward.Type.ITEM_SERIALIZED) return false;
        return transaction(conn -> {
            if (!matchesBaitEdit(conn, expected) || !replaceBaitSlot(conn, first,
                    new BaitQueueEntry(player, first.slotIndex(), first.baitId(), first.amount() - amount))) return false;
            insertDelivery(conn, request, player, owner, false, List.of(reward), System.currentTimeMillis());
            return true;
        });
    }

    private boolean matchesBaitEdit(Connection conn, BaitEdit expected) throws SQLException {
        var season = expected.season();
        return expected.equals(loadBaitEdit(conn, season.uuid(), season.seasonId(), expected.first().slotIndex(),
            expected.second().slotIndex(), true));
    }

    @Override
    public BiteProfile loadBiteProfile(UUID uuid, String seasonId) throws SQLException {
        try (Connection conn = getConnection()) {
            return loadBiteProfile(conn, uuid, seasonId, false);
        }
    }

    @Override
    public boolean confirmBite(BiteProfile expected, boolean consumeBait, BooleanSupplier stillOpen) throws SQLException {
        var season = expected.season();
        if (!consumeBait) {
            return expected.equals(loadBiteProfile(season.uuid(), season.seasonId())) && stillOpen.getAsBoolean();
        }
        return transaction(conn -> {
            if (!expected.equals(loadBiteProfile(conn, season.uuid(), season.seasonId(), true)) || !stillOpen.getAsBoolean()) return false;
            var head = expected.baitHead();
            return head != null && head.amount() > 0
                && replaceBaitSlot(conn, head, new BaitQueueEntry(head.playerUuid(), head.slotIndex(), head.baitId(), head.amount() - 1));
        });
    }

    private BiteProfile loadBiteProfile(Connection conn, UUID uuid, String seasonId, boolean lock) throws SQLException {
        var season = loadSeasonData(conn, uuid, seasonId, lock);
        String sql = "SELECT slot_index, bait_id, amount FROM axs_fishing_bait_queue"
            + " WHERE uuid=? ORDER BY slot_index ASC LIMIT 1" + (lock && isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                var bait = rs.next() ? new BaitQueueEntry(uuid, rs.getInt("slot_index"), rs.getString("bait_id"),
                    rs.getInt("amount")) : null;
                return new BiteProfile(season, bait);
            }
        }
    }

    @Override
    public @NotNull List<BaitQueueEntry> loadBaitQueue(@NotNull UUID uuid) {
        List<BaitQueueEntry> queue = new ArrayList<>();
        String sql = "SELECT slot_index, bait_id, amount FROM axs_fishing_bait_queue"
            + " WHERE uuid=? ORDER BY slot_index ASC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    queue.add(new BaitQueueEntry(uuid,
                        rs.getInt("slot_index"),
                        rs.getString("bait_id"),
                        rs.getInt("amount")));
                }
            }
        } catch (SQLException e) {
            logger.warning("加载饵料队列失败 uuid=" + uuid + ": " + e.getMessage());
        }
        return queue;
    }

    @Override
    public boolean replaceBaitSlot(@Nullable BaitQueueEntry expected, @NotNull BaitQueueEntry replacement) {
        try (Connection conn = getConnection()) {
            return replaceBaitSlot(conn, expected, replacement);
        } catch (SQLException e) {
            logger.warning("保存饵料槽位失败 uuid=" + replacement.playerUuid() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean swapBaitSlots(@NotNull BaitQueueEntry first, @NotNull BaitQueueEntry second) {
        if (first.slotIndex() > second.slotIndex()) {
            return swapBaitSlots(second, first);
        }
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (touchesBaitDeposit(conn, first, second) || !swapBaitSlots(conn, first, second)) {
                    conn.rollback();
                    return false;
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.warning("交换饵料槽位失败 uuid=" + first.playerUuid() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean swapBaitSlots(Connection conn, BaitQueueEntry first, BaitQueueEntry second) throws SQLException {
        if (first.slotIndex() > second.slotIndex()) return swapBaitSlots(conn, second, first);
        return replaceBaitSlot(conn, first, new BaitQueueEntry(first.playerUuid(), first.slotIndex(), second.baitId(), second.amount()))
            && replaceBaitSlot(conn, second, new BaitQueueEntry(second.playerUuid(), second.slotIndex(), first.baitId(), first.amount()));
    }

    private boolean replaceBaitSlot(Connection conn, @Nullable BaitQueueEntry expected, BaitQueueEntry replacement)
            throws SQLException {
        if (expected == null || expected.amount() <= 0) {
            if (replacement.amount() <= 0) return true;
            String sql = "INSERT INTO axs_fishing_bait_queue (uuid, slot_index, bait_id, amount) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, replacement.playerUuid().toString());
                ps.setInt(2, replacement.slotIndex());
                ps.setString(3, replacement.baitId());
                ps.setInt(4, replacement.amount());
                return ps.executeUpdate() == 1;
            }
        }

        // 无变化的交换也要验证旧值，避免写入另一槽时基于过期快照。
        boolean deleting = replacement.amount() <= 0;
        boolean unchanged = replacement.equals(expected);
        String where = " WHERE uuid=? AND slot_index=? AND bait_id=? AND amount=?";
        String sql = deleting ? "DELETE FROM axs_fishing_bait_queue" + where
            : unchanged ? "SELECT 1 FROM axs_fishing_bait_queue" + where + (isMysql() ? " FOR UPDATE" : "")
            : "UPDATE axs_fishing_bait_queue SET bait_id=?, amount=?" + where;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            if (!deleting && !unchanged) {
                ps.setString(index++, replacement.baitId());
                ps.setInt(index++, replacement.amount());
            }
            ps.setString(index++, expected.playerUuid().toString());
            ps.setInt(index++, expected.slotIndex());
            ps.setString(index++, expected.baitId());
            ps.setInt(index, expected.amount());
            if (!unchanged) return ps.executeUpdate() == 1;
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void clearBaitQueue(@NotNull UUID uuid) {
        String sql = "DELETE FROM axs_fishing_bait_queue WHERE uuid=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("清空饵料队列失败 uuid=" + uuid + ": " + e.getMessage());
        }
    }

    // ─── 任务进度 ────────────────────────────────────────────────

    @Override
    public @NotNull List<PlayerTaskInstance> loadTaskProgress(@NotNull UUID uuid, String seasonId) {
        String sql = "SELECT category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date"
            + " FROM axs_fishing_task_progress WHERE uuid=? AND (category<>'SEASON' OR period_key=?)";
        List<PlayerTaskInstance> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory category;
                    try {
                        category = xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory.valueOf(rs.getString("category"));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    java.time.LocalDate assignedDate;
                    try {
                        assignedDate = java.time.LocalDate.parse(rs.getString("assigned_date"));
                    } catch (Exception ex) {
                        continue;
                    }
                    list.add(new PlayerTaskInstance(
                        uuid,
                        uuid + "|" + category + "|" + rs.getString("task_id") + "|" + assignedDate,
                        rs.getString("task_id"),
                        category,
                        rs.getInt("target_count"),
                        rs.getInt("current_progress"),
                        rs.getInt("completed") != 0,
                        rs.getInt("reward_claimed") != 0,
                        assignedDate));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("加载任务进度失败 uuid=" + uuid, e);
        }
        return list;
    }

    @Override
    public void saveTaskProgress(@NotNull PlayerTaskInstance instance, String seasonId) {
        try (Connection conn = getConnection()) {
            saveTaskProgress(conn, instance, seasonId);
        } catch (SQLException e) {
            logger.warning("保存任务进度失败 uuid=" + instance.playerUuid() + " task=" + instance.templateId() + ": " + e.getMessage());
        }
    }

    private void saveTaskProgress(Connection conn, PlayerTaskInstance instance, String seasonId) throws SQLException {
        String sql = "INSERT INTO axs_fishing_task_progress"
            + " (uuid, category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date, period_key, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT(uuid, category, task_id, period_key) DO UPDATE SET"
            + " current_progress=MAX(current_progress, excluded.current_progress),"
            + " completed=MAX(completed, excluded.completed), reward_claimed=MAX(reward_claimed, excluded.reward_claimed),"
            + " updated_at=MAX(updated_at, excluded.updated_at)";
        if (isMysql()) {
            sql = "INSERT INTO axs_fishing_task_progress"
                + " (uuid, category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date, period_key, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE current_progress=GREATEST(current_progress, VALUES(current_progress)),"
                + " completed=GREATEST(completed, VALUES(completed)), reward_claimed=GREATEST(reward_claimed, VALUES(reward_claimed)),"
                + " updated_at=GREATEST(updated_at, VALUES(updated_at))";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instance.playerUuid().toString());
            ps.setString(2, instance.category().name());
            ps.setString(3, instance.templateId());
            ps.setInt(4, instance.targetCount());
            ps.setInt(5, instance.currentProgress());
            ps.setInt(6, instance.completed() ? 1 : 0);
            ps.setInt(7, instance.rewardClaimed() ? 1 : 0);
            ps.setString(8, instance.assignedDate().toString());
            ps.setString(9, taskPeriodKey(instance, seasonId));
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public @Nullable TaskRewardResult completeTask(PlayerTaskInstance instance, String seasonId, int xp,
                                                  XpCurve curve, int maxLevel) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 分配写入可能仍在队列里。若任务行尚未创建，先补建未完成记录。
                saveTaskProgress(conn, instance.withProgress(0), seasonId);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE axs_fishing_task_progress"
                        + " SET current_progress=target_count, completed=1, reward_claimed=1, updated_at=?"
                        + " WHERE uuid=? AND category=? AND task_id=? AND period_key=? AND completed=0 AND reward_claimed=0")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, instance.playerUuid().toString());
                    ps.setString(3, instance.category().name());
                    ps.setString(4, instance.templateId());
                    ps.setString(5, taskPeriodKey(instance, seasonId));
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return new TaskRewardResult(false, 0, 0);
                    }
                }
                SeasonChange change = mutateSeason(conn, instance.playerUuid(), seasonId,
                    before -> before.withXpAdded(xp, curve, maxLevel));
                conn.commit();
                return new TaskRewardResult(true, change.before().level(), change.after().level());
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            logger.warning("发放任务经验失败 uuid=" + instance.playerUuid() + " task=" + instance.templateId() + ": " + e.getMessage());
            return null;
        }
    }

    private static String taskPeriodKey(PlayerTaskInstance instance, String seasonId) {
        return switch (instance.category()) {
            case DAILY -> instance.assignedDate().toString();
            case WEEKLY -> instance.assignedDate().with(java.time.DayOfWeek.MONDAY).toString();
            case SEASON -> seasonId;
        };
    }

    @Override
    public void purgeTaskProgressBefore(@NotNull String category, @NotNull String assignedDateBefore) {
        String sql = "DELETE FROM axs_fishing_task_progress WHERE category=? AND assigned_date<?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, assignedDateBefore);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("清理过期任务进度失败 category=" + category + ": " + e.getMessage());
        }
    }

}
