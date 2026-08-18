package xuanmo.arcartxsuite.fishing.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
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

    private final StorageConfiguration configuration;
    private final Logger logger;

    public JdbcFishingRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-Fishing", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
        this.logger = logger;
    }

    public JdbcFishingRepository(StorageManager storageManager, String moduleId,
                                 String tablePrefix, String sqliteFileName, Logger logger) {
        super("AXS-Fishing", storageManager, moduleId, tablePrefix, sqliteFileName, logger);
        this.configuration = null;
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 赛季数据表（按 seasonId 隔离）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_season ("
                + "uuid TEXT NOT NULL,"
                + "season_id TEXT NOT NULL,"
                + "level INTEGER NOT NULL DEFAULT 1,"
                + "current_xp INTEGER NOT NULL DEFAULT 0,"
                + "talent_points INTEGER NOT NULL DEFAULT 0,"
                + "unspent_talent_points INTEGER NOT NULL DEFAULT 0,"
                + "talents TEXT NOT NULL DEFAULT '',"
                + "claimed_rewards TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY (uuid, season_id))");

            // 永久数据表（跨赛季保留）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_permanent ("
                + "uuid TEXT PRIMARY KEY,"
                + "total_caught INTEGER NOT NULL DEFAULT 0,"
                + "perfect_catches INTEGER NOT NULL DEFAULT 0,"
                + "treasure_caught INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL)");

            // 图鉴表（跨赛季保留）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_collection ("
                + "uuid TEXT NOT NULL,"
                + "fish_id TEXT NOT NULL,"
                + "caught_count INTEGER NOT NULL DEFAULT 0,"
                + "max_size INTEGER NOT NULL DEFAULT 0,"
                + "first_catch_at INTEGER NOT NULL,"
                + "PRIMARY KEY (uuid, fish_id))");

            // 待发放奖励表
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_pending_rewards ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uuid TEXT NOT NULL,"
                + "season_id TEXT NOT NULL,"
                + "reward_type TEXT NOT NULL,"
                + "currency_id TEXT NOT NULL DEFAULT '',"
                + "amount REAL NOT NULL DEFAULT 0,"
                + "item_data TEXT NOT NULL DEFAULT '',"
                + "command TEXT NOT NULL DEFAULT '',"
                + "description TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL)");
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("axs_fishing_season", "axs_fishing_permanent", "axs_fishing_collection", "axs_fishing_pending_rewards");
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    @Override
    protected List<String> allTables() {
        return List.of("axs_fishing_season", "axs_fishing_permanent", "axs_fishing_collection", "axs_fishing_pending_rewards");
    }

    // ─── 赛季数据 ────────────────────────────────────────────────

    @Override
    public @NotNull FishingSeasonData loadSeasonData(@NotNull UUID uuid, @NotNull String seasonId) {
        String sql = "SELECT level, current_xp, talent_points, unspent_talent_points, talents, claimed_rewards "
            + "FROM axs_fishing_season WHERE uuid = ? AND season_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
                        parseStringSet(rs.getString("talents")),
                        parseStringSet(rs.getString("claimed_rewards"))
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("加载赛季数据失败 [" + seasonId + "]: " + e.getMessage());
        }
        return FishingSeasonData.empty(uuid, seasonId);
    }

    @Override
    public void saveSeasonData(@NotNull FishingSeasonData data) {
        String sql = "INSERT INTO axs_fishing_season (uuid, season_id, level, current_xp, "
            + "talent_points, unspent_talent_points, talents, claimed_rewards, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, season_id) DO UPDATE SET level=excluded.level, current_xp=excluded.current_xp, "
            + "talent_points=excluded.talent_points, unspent_talent_points=excluded.unspent_talent_points, "
            + "talents=excluded.talents, claimed_rewards=excluded.claimed_rewards, updated_at=excluded.updated_at";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setString(2, data.seasonId());
            ps.setInt(3, data.level());
            ps.setInt(4, data.currentXp());
            ps.setInt(5, data.talentPoints());
            ps.setInt(6, data.unspentTalentPoints());
            ps.setString(7, serializeStringSet(data.talents()));
            ps.setString(8, serializeStringSet(data.claimedRewards()));
            ps.setLong(9, System.currentTimeMillis());
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
        String sql = "INSERT INTO axs_fishing_permanent (uuid, total_caught, perfect_catches, treasure_caught, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid) DO UPDATE SET total_caught=excluded.total_caught, "
            + "perfect_catches=excluded.perfect_catches, treasure_caught=excluded.treasure_caught, "
            + "updated_at=excluded.updated_at";
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
        String sql = "INSERT INTO axs_fishing_collection (uuid, fish_id, caught_count, max_size, first_catch_at) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, fish_id) DO UPDATE SET caught_count=excluded.caught_count, "
            + "max_size=excluded.max_size, first_catch_at=excluded.first_catch_at";
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
}
