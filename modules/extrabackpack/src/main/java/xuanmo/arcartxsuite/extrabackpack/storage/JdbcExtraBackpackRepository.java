package xuanmo.arcartxsuite.extrabackpack.storage;

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
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.StorageDialect;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository.ExtraBackpackSlotRecord;

public final class JdbcExtraBackpackRepository extends AbstractModuleRepository implements ExtraBackpackRepository {

    private final StorageConfiguration configuration;

    public JdbcExtraBackpackRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-ExtraBackpack", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection connection) throws SQLException {
        createTables(connection);
    }

    @Override
    public List<String> playerDataTables() {
        return List.of("extra_backpack_slots", "extra_backpack_capacity");
    }

    @Override
    public List<String> allTables() {
        return List.of("extra_backpack_capacity", "extra_backpack_slots");
    }

    @Override
    public long loadExtraBackpackCapacity(UUID playerUuid, String categoryId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT capacity FROM extra_backpack_capacity WHERE player_uuid = ? AND category_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, categoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("capacity") : -1L;
            }
        }
    }

    @Override
    public void setExtraBackpackCapacity(UUID playerUuid, String categoryId, long capacity, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 upsertSql("extra_backpack_capacity", List.of("player_uuid", "category_id"), List.of("capacity", "updated_at"))
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, categoryId);
            statement.setLong(3, capacity);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    @Override
    public List<ExtraBackpackSlotRecord> loadExtraBackpackSlots(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT category_id, slot, item_data, updated_at FROM extra_backpack_slots WHERE player_uuid = ? ORDER BY category_id ASC, slot ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ExtraBackpackSlotRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new ExtraBackpackSlotRecord(
                        playerUuid,
                        resultSet.getString("category_id"),
                        resultSet.getInt("slot"),
                        resultSet.getString("item_data"),
                        resultSet.getLong("updated_at")
                    ));
                }
                return result;
            }
        }
    }

    @Override
    public void saveExtraBackpackSlots(UUID playerUuid, List<ExtraBackpackSlotRecord> items) throws SQLException {
        try (Connection connection = connection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM extra_backpack_slots WHERE player_uuid = ?");
                 PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO extra_backpack_slots (player_uuid, category_id, slot, item_data, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                delete.setString(1, playerUuid.toString());
                delete.executeUpdate();
                for (ExtraBackpackSlotRecord item : items) {
                    insert.setString(1, playerUuid.toString());
                    insert.setString(2, item.categoryId());
                    insert.setInt(3, item.slot());
                    insert.setString(4, item.itemData());
                    insert.setLong(5, item.updatedAt());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS extra_backpack_capacity (
                    player_uuid VARCHAR(36) NOT NULL,
                    category_id VARCHAR(64) NOT NULL,
                    capacity BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, category_id)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS extra_backpack_slots (
                    player_uuid VARCHAR(36) NOT NULL,
                    category_id VARCHAR(64) NOT NULL,
                    slot INTEGER NOT NULL,
                    item_data LONGTEXT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, category_id, slot)
                )
                """);
            if (configuration.dialect() == StorageDialect.SQLITE) {
                statement.execute("CREATE INDEX IF NOT EXISTS idx_extra_backpack_slots_player ON extra_backpack_slots(player_uuid, category_id, slot)");
            } else {
                try {
                    statement.execute("CREATE INDEX idx_extra_backpack_slots_player ON extra_backpack_slots(player_uuid, category_id, slot)");
                } catch (SQLException ignored) {
                    // Duplicate index or dialect-specific existence semantics.
                }
            }
        }
    }

    private String upsertSql(String table, List<String> keys, List<String> updateColumns) {
        List<String> all = new ArrayList<>(keys);
        all.addAll(updateColumns);
        String columns = String.join(", ", all);
        String placeholders = String.join(", ", java.util.Collections.nCopies(all.size(), "?"));
        if (configuration.dialect() == StorageDialect.SQLITE) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") ON CONFLICT(" + String.join(", ", keys) + ") DO UPDATE SET "
                + updateAssignments(updateColumns, true);
        }
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE "
            + updateAssignments(updateColumns, false);
    }

    private static String updateAssignments(List<String> columns, boolean sqlite) {
        List<String> assignments = new ArrayList<>();
        for (String column : columns) {
            assignments.add(column + (sqlite ? " = excluded." : " = VALUES(") + column + (sqlite ? "" : ")"));
        }
        return String.join(", ", assignments);
    }

    private Connection connection() throws SQLException {
        return getConnection();
    }

}
