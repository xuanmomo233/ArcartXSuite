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
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPendingReward;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;

/**
 * 基于 JDBC 的 {@link FishingRepository} 实现。
 * <p>
 * 关联模块：{@code fishing}。使用 SQLite/MySQL 存储，维护以下表：
 * <ul>
 *   <li>{@code axs_fishing_season} — 赛季数据（按 seasonId 隔离）</li>
 *   <li>{@code axs_fishing_permanent} — 永久数据（跨赛季保留）</li>
 *   <li>{@code axs_fishing_collection} — 图鉴条目（跨赛季保留）</li>
 *   <li>{@code axs_fishing_pending_rewards} — 待发放奖励（离线玩家补发）</li>
 * </ul>
 */
public final class JdbcFishingRepository extends AbstractModuleRepository implements FishingRepository {

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
                        claimed_rewards TEXT NOT NULL DEFAULT '',
                        fatigue INTEGER NOT NULL DEFAULT 0,
                        claimed_max_fatigue_reward INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, season_id))
                    """);
                // 兼容旧表：若列不存在则添加（ALTER TABLE 忽略异常）
                tryExecute(conn, "ALTER TABLE axs_fishing_season ADD COLUMN fatigue INTEGER NOT NULL DEFAULT 0");
                tryExecute(conn, "ALTER TABLE axs_fishing_season ADD COLUMN claimed_max_fatigue_reward INTEGER NOT NULL DEFAULT 0");

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
                    CREATE TABLE IF NOT EXISTS axs_fishing_pending_rewards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        season_id TEXT NOT NULL,
                        reward_type TEXT NOT NULL,
                        currency_id TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL DEFAULT 0,
                        item_data TEXT NOT NULL DEFAULT '',
                        command TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL)
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
                        timestamp INTEGER NOT NULL)
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
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, category, task_id, assigned_date))
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
                        claimed_rewards TEXT NOT NULL,
                        fatigue INT NOT NULL DEFAULT 0,
                        claimed_max_fatigue_reward INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, season_id))
                    """);
                // 兼容旧表：若列不存在则添加（ALTER TABLE 忽略异常）
                tryExecute(conn, "ALTER TABLE axs_fishing_season ADD COLUMN fatigue INT NOT NULL DEFAULT 0");
                tryExecute(conn, "ALTER TABLE axs_fishing_season ADD COLUMN claimed_max_fatigue_reward INT NOT NULL DEFAULT 0");

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
                    CREATE TABLE IF NOT EXISTS axs_fishing_pending_rewards (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        season_id VARCHAR(64) NOT NULL,
                        reward_type VARCHAR(32) NOT NULL,
                        currency_id VARCHAR(64) NOT NULL DEFAULT '',
                        amount DOUBLE NOT NULL DEFAULT 0,
                        item_data TEXT NOT NULL,
                        command TEXT NOT NULL,
                        description TEXT NOT NULL,
                        created_at BIGINT NOT NULL)
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
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, category, task_id, assigned_date))
                    """);
            }
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("axs_fishing_season", "axs_fishing_permanent", "axs_fishing_collection", "axs_fishing_pending_rewards", "axs_fishing_catch_records", "axs_fishing_bait_queue", "axs_fishing_task_progress");
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    @Override
    protected List<String> allTables() {
        return List.of("axs_fishing_season", "axs_fishing_permanent", "axs_fishing_collection", "axs_fishing_pending_rewards", "axs_fishing_bait_queue", "axs_fishing_task_progress");
    }

    // ─── 赛季数据 ────────────────────────────────────────────────

    @Override
    public @NotNull FishingSeasonData loadSeasonData(@NotNull UUID uuid, @NotNull String seasonId) {
        // C7: 先尝试查询含新列的 SQL，若旧表无新列则回退到不含新列的 SQL
        String sqlWithNewCols = "SELECT level, current_xp, talent_points, unspent_talent_points, talents, claimed_rewards, "
            + "fatigue, claimed_max_fatigue_reward "
            + "FROM axs_fishing_season WHERE uuid = ? AND season_id = ?";
        String sqlLegacy = "SELECT level, current_xp, talent_points, unspent_talent_points, talents, claimed_rewards "
            + "FROM axs_fishing_season WHERE uuid = ? AND season_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlWithNewCols)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishingSeasonData(
                        uuid, seasonId,
                        rs.getInt("level"),
                        rs.getInt("current_xp"),
                        rs.getInt("talent_points"),
                        rs.getInt("unspent_talent_points"),
                        parseTalentMap(rs.getString("talents")),
                        parseStringSet(rs.getString("claimed_rewards")),
                        rs.getInt("fatigue"),
                        rs.getBoolean("claimed_max_fatigue_reward")
                    );
                }
            }
        } catch (SQLException e) {
            // 旧表可能无 fatigue/claimed_max_fatigue_reward 列，回退到不含新列的查询
            logger.fine("含新列查询失败，尝试旧表查询: " + e.getMessage());
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlLegacy)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, seasonId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new FishingSeasonData(
                            uuid, seasonId,
                            rs.getInt("level"),
                            rs.getInt("current_xp"),
                            rs.getInt("talent_points"),
                            rs.getInt("unspent_talent_points"),
                            parseTalentMap(rs.getString("talents")),
                            parseStringSet(rs.getString("claimed_rewards")),
                            0, false // 旧表无疲惫值，默认 0 和 false
                        );
                    }
                }
            } catch (SQLException e2) {
                logger.warning("加载赛季数据失败 [" + seasonId + "]: " + e2.getMessage());
            }
        }
        return FishingSeasonData.empty(uuid, seasonId);
    }

    @Override
    public void saveSeasonData(@NotNull FishingSeasonData data) {
        String sql = !isMysql()
            ? "INSERT INTO axs_fishing_season (uuid, season_id, level, current_xp, "
            + "talent_points, unspent_talent_points, talents, claimed_rewards, fatigue, claimed_max_fatigue_reward, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, season_id) DO UPDATE SET level=excluded.level, current_xp=excluded.current_xp, "
            + "talent_points=excluded.talent_points, unspent_talent_points=excluded.unspent_talent_points, "
            + "talents=excluded.talents, claimed_rewards=excluded.claimed_rewards, "
            + "fatigue=excluded.fatigue, claimed_max_fatigue_reward=excluded.claimed_max_fatigue_reward, "
            + "updated_at=excluded.updated_at"
            : "INSERT INTO axs_fishing_season (uuid, season_id, level, current_xp, "
            + "talent_points, unspent_talent_points, talents, claimed_rewards, fatigue, claimed_max_fatigue_reward, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE level=VALUES(level), current_xp=VALUES(current_xp), "
            + "talent_points=VALUES(talent_points), unspent_talent_points=VALUES(unspent_talent_points), "
            + "talents=VALUES(talents), claimed_rewards=VALUES(claimed_rewards), "
            + "fatigue=VALUES(fatigue), claimed_max_fatigue_reward=VALUES(claimed_max_fatigue_reward), "
            + "updated_at=VALUES(updated_at)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setString(2, data.seasonId());
            ps.setInt(3, data.level());
            ps.setInt(4, data.currentXp());
            ps.setInt(5, data.talentPoints());
            ps.setInt(6, data.unspentTalentPoints());
            ps.setString(7, serializeTalentMap(data.talents()));
            ps.setString(8, serializeStringSet(data.claimedRewards()));
            ps.setInt(9, data.fatigue());
            ps.setBoolean(10, data.claimedMaxFatigueReward());
            ps.setLong(11, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存赛季数据失败: " + e.getMessage());
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

    // ─── 永久数据 ────────────────────────────────────────────────

    @Override
    public @NotNull FishingPermanentData loadPermanentData(@NotNull UUID uuid) {
        String sql = "SELECT total_caught, perfect_catches, treasure_caught FROM axs_fishing_permanent WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            logger.warning("加载永久数据失败: " + e.getMessage());
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

    // ─── 待发放奖励 ──────────────────────────────────────────────

    @Override
    public @NotNull List<FishingPendingReward> loadPendingRewards(@NotNull UUID uuid) {
        List<FishingPendingReward> result = new ArrayList<>();
        String sql = "SELECT id, season_id, reward_type, currency_id, amount, item_data, command, description, created_at "
            + "FROM axs_fishing_pending_rewards WHERE uuid = ? ORDER BY created_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FishingPendingReward(
                        rs.getLong("id"),
                        uuid,
                        rs.getString("season_id"),
                        rs.getString("reward_type"),
                        rs.getString("currency_id"),
                        rs.getDouble("amount"),
                        rs.getString("item_data"),
                        rs.getString("command"),
                        rs.getString("description"),
                        rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("加载待发放奖励失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public @NotNull java.util.List<java.util.UUID> loadAllPendingPlayerUuids() {
        java.util.List<java.util.UUID> result = new java.util.ArrayList<>();
        String sql = "SELECT DISTINCT uuid FROM axs_fishing_pending_rewards";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    result.add(java.util.UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    // 跳过格式错误的 UUID
                }
            }
        } catch (SQLException e) {
            // 旧表可能不存在，静默处理
            logger.fine("加载所有 pending 玩家 UUID 失败（表可能不存在）: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void savePendingReward(@NotNull FishingPendingReward reward) {
        String sql = "INSERT INTO axs_fishing_pending_rewards "
            + "(uuid, season_id, reward_type, currency_id, amount, item_data, command, description, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reward.uuid().toString());
            ps.setString(2, reward.seasonId());
            ps.setString(3, reward.rewardType());
            ps.setString(4, reward.currencyId() != null ? reward.currencyId() : "");
            ps.setDouble(5, reward.amount());
            ps.setString(6, reward.itemData() != null ? reward.itemData() : "");
            ps.setString(7, reward.command() != null ? reward.command() : "");
            ps.setString(8, reward.description());
            ps.setLong(9, reward.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存待发放奖励失败: " + e.getMessage());
        }
    }

    @Override
    public void deletePendingReward(long id) {
        String sql = "DELETE FROM axs_fishing_pending_rewards WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("删除待发放奖励失败: " + e.getMessage());
        }
    }

    // ─── 捕获记录 ────────────────────────────────────────────────

    @Override
    public void saveCatchRecord(@NotNull FishingCatchRecord record) {
        String sql = "INSERT INTO axs_fishing_catch_records "
            + "(uuid, fish_id, fish_name, rarity, size, perfect, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.playerUuid().toString());
            ps.setString(2, record.fishId());
            ps.setString(3, record.fishName());
            ps.setString(4, record.rarity());
            ps.setInt(5, record.size());
            ps.setInt(6, record.perfect() ? 1 : 0);
            ps.setLong(7, record.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存捕获记录失败: " + e.getMessage());
        }
    }

    @Override
    @NotNull
    public List<FishingCatchRecord> loadCatchRecords(@NotNull UUID uuid, int limit) {
        List<FishingCatchRecord> result = new ArrayList<>();
        String sql = "SELECT id, fish_id, fish_name, rarity, size, perfect, timestamp "
            + "FROM axs_fishing_catch_records WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FishingCatchRecord(
                        rs.getLong("id"),
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

    private static Set<String> parseStringSet(String csv) {
        Set<String> set = new HashSet<>();
        if (csv == null || csv.isBlank()) return set;
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }

    private static String serializeStringSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        return String.join(",", set);
    }

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
    public void saveBaitSlot(@NotNull BaitQueueEntry entry) {
        if (entry.amount() <= 0) {
            String sql = "DELETE FROM axs_fishing_bait_queue WHERE uuid=? AND slot_index=?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.playerUuid().toString());
                ps.setInt(2, entry.slotIndex());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("删除饵料槽位失败 uuid=" + entry.playerUuid() + " slot=" + entry.slotIndex() + ": " + e.getMessage());
            }
            return;
        }
        String sql = "INSERT INTO axs_fishing_bait_queue (uuid, slot_index, bait_id, amount) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT(uuid, slot_index) DO UPDATE SET bait_id=excluded.bait_id, amount=excluded.amount";
        if (isMysql()) {
            sql = "INSERT INTO axs_fishing_bait_queue (uuid, slot_index, bait_id, amount) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE bait_id=VALUES(bait_id), amount=VALUES(amount)";
        }
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.playerUuid().toString());
            ps.setInt(2, entry.slotIndex());
            ps.setString(3, entry.baitId());
            ps.setInt(4, entry.amount());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存饵料槽位失败 uuid=" + entry.playerUuid() + " slot=" + entry.slotIndex() + ": " + e.getMessage());
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
    public @NotNull List<xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance> loadTaskProgress(@NotNull UUID uuid) {
        String sql = "SELECT category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date"
            + " FROM axs_fishing_task_progress WHERE uuid=?";
        List<xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
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
                    list.add(new xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance(
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
            logger.warning("加载任务进度失败 uuid=" + uuid + ": " + e.getMessage());
        }
        return list;
    }

    @Override
    public void saveTaskProgress(@NotNull xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance instance) {
        String sql = "INSERT INTO axs_fishing_task_progress"
            + " (uuid, category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT(uuid, category, task_id, assigned_date) DO UPDATE SET"
            + " target_count=excluded.target_count, current_progress=excluded.current_progress,"
            + " completed=excluded.completed, reward_claimed=excluded.reward_claimed, updated_at=excluded.updated_at";
        if (isMysql()) {
            sql = "INSERT INTO axs_fishing_task_progress"
                + " (uuid, category, task_id, target_count, current_progress, completed, reward_claimed, assigned_date, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE target_count=VALUES(target_count), current_progress=VALUES(current_progress),"
                + " completed=VALUES(completed), reward_claimed=VALUES(reward_claimed), updated_at=VALUES(updated_at)";
        }
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instance.playerUuid().toString());
            ps.setString(2, instance.category().name());
            ps.setString(3, instance.templateId());
            ps.setInt(4, instance.targetCount());
            ps.setInt(5, instance.currentProgress());
            ps.setInt(6, instance.completed() ? 1 : 0);
            ps.setInt(7, instance.rewardClaimed() ? 1 : 0);
            ps.setString(8, instance.assignedDate().toString());
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存任务进度失败 uuid=" + instance.playerUuid() + " task=" + instance.templateId() + ": " + e.getMessage());
        }
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

    @Override
    public void purgeTaskProgressByCategory(@NotNull String category) {
        String sql = "DELETE FROM axs_fishing_task_progress WHERE category=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("按分类清理任务进度失败 category=" + category + ": " + e.getMessage());
        }
    }
}
