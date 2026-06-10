package xuanmo.arcartxsuite.fishing.storage;

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
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingPlayerData;

public final class JdbcFishingRepository extends AbstractModuleRepository implements FishingRepository {

    private final StorageConfiguration configuration;
    private final Logger logger;

    public JdbcFishingRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-Fishing", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
        this.logger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_player ("
                + "uuid TEXT PRIMARY KEY,"
                + "level INTEGER NOT NULL DEFAULT 1,"
                + "total_xp INTEGER NOT NULL DEFAULT 0,"
                + "total_caught INTEGER NOT NULL DEFAULT 0,"
                + "perfect_catches INTEGER NOT NULL DEFAULT 0,"
                + "treasure_caught INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_fishing_collection ("
                + "uuid TEXT NOT NULL,"
                + "fish_id TEXT NOT NULL,"
                + "caught_count INTEGER NOT NULL DEFAULT 0,"
                + "max_size INTEGER NOT NULL DEFAULT 0,"
                + "first_catch_at INTEGER NOT NULL,"
                + "PRIMARY KEY (uuid, fish_id))");
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("axs_fishing_player", "axs_fishing_collection");
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    @Override
    protected List<String> allTables() {
        return List.of("axs_fishing_player", "axs_fishing_collection");
    }

    @Override
    public @NotNull FishingPlayerData loadPlayerData(@NotNull UUID uuid) {
        String sql = "SELECT level, total_xp, total_caught, perfect_catches, treasure_caught FROM axs_fishing_player WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishingPlayerData(
                        uuid,
                        rs.getInt("level"),
                        rs.getInt("total_xp"),
                        rs.getInt("total_caught"),
                        rs.getInt("perfect_catches"),
                        rs.getInt("treasure_caught")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("加载玩家钓鱼数据失败: " + e.getMessage());
        }
        return FishingPlayerData.empty(uuid);
    }

    @Override
    public void savePlayerData(@NotNull FishingPlayerData data) {
        String sql = "INSERT INTO axs_fishing_player (uuid, level, total_xp, total_caught, perfect_catches, treasure_caught, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid) DO UPDATE SET level=excluded.level, total_xp=excluded.total_xp, "
            + "total_caught=excluded.total_caught, perfect_catches=excluded.perfect_catches, "
            + "treasure_caught=excluded.treasure_caught, updated_at=excluded.updated_at";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setInt(2, data.level());
            ps.setInt(3, data.totalXp());
            ps.setInt(4, data.totalCaught());
            ps.setInt(5, data.perfectCatches());
            ps.setInt(6, data.treasureCaught());
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("保存玩家钓鱼数据失败: " + e.getMessage());
        }
    }

    @Override
    public FishCollectionEntry loadCollectionEntry(@NotNull UUID playerUuid, @NotNull String fishId) {
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

}
