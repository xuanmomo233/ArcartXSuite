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
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository.ExtraBackpackSlotRecord;

/**
 * {@link ExtraBackpackRepository} 的 JDBC 实现，继承 {@link AbstractModuleRepository}。
 * <p>
 * 基于 HikariCP 连接池提供 SQLite/MySQL 双方言适配，管理 {@code extra_backpack_capacity}
 * 与 {@code extra_backpack_slots} 两张表，负责扩展背包容量与槽位物品的持久化读写。
 * 同时接入宿主的玩家数据清除（{@code /axs purge}）与跨源迁移（{@code /axs migrate}）能力。
 */
public final class JdbcExtraBackpackRepository extends AbstractModuleRepository implements ExtraBackpackRepository {

    private final @Nullable StorageConfiguration configuration;

    /**
     * 构造仓库实例。
     *
     * @param dataFolder    模块数据目录，SQLite 文件存放于此
     * @param configuration 存储配置
     * @param logger        模块日志
     */
    public JdbcExtraBackpackRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-ExtraBackpack", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    /**
     * 共享模式构造方法（1.4.0+）。
     * 使用本体统一数据源，模块只需提供表前缀和 SQLite 文件名。
     *
     * @param storageManager 本体数据源管理器
     * @param moduleId       模块 ID
     * @param tablePrefix    表前缀
     * @param sqliteFileName SQLite 文件名（SQLite 模式下使用）
     * @param logger         日志记录器
     * @since 1.4.0
     */
    public JdbcExtraBackpackRepository(StorageManager storageManager, String moduleId,
                                       String tablePrefix, String sqliteFileName, Logger logger) {
        super("AXS-ExtraBackpack", storageManager, moduleId, tablePrefix, sqliteFileName, logger);
        this.configuration = null;
    }

    /**
     * 初始化时创建容量表、槽位表及索引。
     *
     * @param connection 数据库连接
     * @throws SQLException 建表失败时抛出
     */
    @Override
    protected void onInitialize(Connection connection) throws SQLException {
        createTables(connection);
    }

    /**
     * 返回存储玩家数据的表名列表。
     *
     * @return 包含 {@code extra_backpack_slots} 与 {@code extra_backpack_capacity}
     */
    @Override
    public List<String> playerDataTables() {
        return List.of("extra_backpack_slots", "extra_backpack_capacity");
    }

    /**
     * 返回本仓库负责的全部表名列表，供跨源迁移使用。
     *
     * @return 包含 {@code extra_backpack_capacity} 与 {@code extra_backpack_slots}
     */
    @Override
    public List<String> allTables() {
        return List.of("extra_backpack_capacity", "extra_backpack_slots");
    }

    /**
     * {@inheritDoc}
     * <p>
     * 查询 {@code extra_backpack_capacity} 表，不存在记录时返回 {@code -1}。
     */
    @Override
    public long loadExtraBackpackCapacity(UUID playerUuid, String categoryId) throws SQLException {
        try (Connection connection = getConnection();
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

    /**
     * {@inheritDoc}
     * <p>
     * 使用 upsert 语法（SQLite {@code ON CONFLICT} / MySQL {@code ON DUPLICATE KEY}）写入容量。
     */
    @Override
    public void setExtraBackpackCapacity(UUID playerUuid, String categoryId, long capacity, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
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

    /**
     * {@inheritDoc}
     * <p>
     * 查询 {@code extra_backpack_slots} 表，结果按分类与槽位号升序排列。
     */
    @Override
    public List<ExtraBackpackSlotRecord> loadExtraBackpackSlots(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
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

    /**
     * {@inheritDoc}
     * <p>
     * 以事务方式先删除该玩家全部槽位记录，再批量插入新记录；失败时回滚。
     */
    @Override
    public void saveExtraBackpackSlots(UUID playerUuid, List<ExtraBackpackSlotRecord> items) throws SQLException {
        try (Connection connection = getConnection()) {
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

    /**
     * 关闭仓库，委托 {@link #shutdown()} 释放连接池。
     */
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
            if (!isMysql()) {
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
        if (!isMysql()) {
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

}
