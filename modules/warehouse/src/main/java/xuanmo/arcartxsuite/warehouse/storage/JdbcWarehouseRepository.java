package xuanmo.arcartxsuite.warehouse.storage;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.StorageConfiguration;

/**
 * 仓库模块的 JDBC 存储实现。
 * <p>
 * 继承 {@link AbstractModuleRepository} 并实现 {@link WarehouseRepository}，
 * 负责个人仓库、槽位物品、银行余额、定期存款、共享仓库及其成员、待处理转让等全部数据的持久化。
 * 支持 MySQL 与 SQLite 两种方言，自动建表与增量迁移，多步写操作使用事务保证原子性。
 * 关联模块：warehouse。
 */
public final class JdbcWarehouseRepository extends AbstractModuleRepository implements WarehouseRepository {

    private final StorageConfiguration configuration;

    /**
     * 构造 JDBC 仓库存储。
     *
     * @param dataFolder    模块数据目录
     * @param configuration 存储配置（连接信息、方言等）
     * @param logger        日志记录器
     */
    public JdbcWarehouseRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-Warehouse", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    /**
     * 共享模式构造方法（1.4.0+）。
     * 使用本体统一数据源，模块只需提供 SQLite 文件名。
     * <p>
     * 仓库模块的表名已内置 {@code warehouse_} 前缀，因此 tablePrefix 传入空字符串。
     *
     * @param storageManager 本体数据源管理器
     * @param moduleId       模块 ID
     * @param sqliteFileName SQLite 文件名（SQLite 模式下使用）
     * @param logger         日志记录器
     * @since 1.4.0
     */
    public JdbcWarehouseRepository(StorageManager storageManager, String moduleId,
                                    String sqliteFileName, Logger logger) {
        super("AXS-Warehouse", storageManager, moduleId, "", sqliteFileName, logger);
        this.configuration = null;
    }

    /**
     * 初始化回调：建表并执行增量迁移。
     *
     * @param conn 初始化连接
     * @throws SQLException 建表或迁移失败时抛出
     */
    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        migrateSchema(conn);
    }

    /**
     * @return 包含玩家个人数据的表名列表，用于按玩家清理数据
     */
    @Override
    protected List<String> playerDataTables() {
        return List.of("warehouse_personal", "warehouse_bank_balances", "warehouse_fixed_deposits", "warehouse_shared_members");
    }

    /**
     * @return 模块管理的全部表名列表，用于全量卸载
     */
    @Override
    protected List<String> allTables() {
        return List.of(
            "warehouse_personal",
            "warehouse_slots",
            "warehouse_bank_balances",
            "warehouse_fixed_deposits",
            "warehouse_shared",
            "warehouse_shared_members",
            "warehouse_pending_transfers"
        );
    }

    /**
     * 加载指定玩家的全部个人仓库记录。
     *
     * @param playerUuid 玩家 UUID
     * @return 仓库记录列表，按 warehouse_id 升序排列
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<WarehouseRecord> loadPersonalWarehouses(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT warehouse_id, level, custom_name, showcase_enabled, updated_at FROM warehouse_personal WHERE player_uuid = ? ORDER BY warehouse_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<WarehouseRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new WarehouseRecord(playerUuid, resultSet.getString("warehouse_id"), resultSet.getInt("level"), resultSet.getString("custom_name"), resultSet.getBoolean("showcase_enabled"), resultSet.getLong("updated_at")));
                }
                return result;
            }
        }
    }

    /**
     * 插入或更新个人仓库元数据（等级、自定义名称等）。
     *
     * @param playerUuid  玩家 UUID
     * @param warehouseId 仓库标识
     * @param level       仓库等级
     * @param customName  自定义名称，{@code null} 视为空串
     * @param updatedAt   更新时间戳
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void upsertPersonalWarehouse(UUID playerUuid, String warehouseId, int level, String customName, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_personal", List.of("player_uuid", "warehouse_id"), List.of("level", "custom_name", "updated_at")))) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, warehouseId);
            statement.setInt(3, level);
            statement.setString(4, customName == null ? "" : customName);
            statement.setLong(5, updatedAt);
            statement.executeUpdate();
        }
    }

    /**
     * 加载指定仓库的全部槽位物品记录，按槽位序号升序排列。
     *
     * @param ownerType   所有者类型（personal / shared）
     * @param ownerId     所有者 ID（玩家 UUID 或共享仓库 ID）
     * @param warehouseId 仓库标识
     * @return 槽位物品记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? ORDER BY slot ASC"
             )) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SlotItemRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(slot(resultSet));
                }
                return result;
            }
        }
    }

    @Override
    public List<SlotItemRecord> loadAllSlots() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_slots ORDER BY slot ASC"
             )) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SlotItemRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(slot(resultSet));
                }
                return result;
            }
        }
    }

    /**
     * 加载指定仓库的单个槽位物品记录。
     *
     * @param ownerType   所有者类型
     * @param ownerId     所有者 ID
     * @param warehouseId 仓库标识
     * @param slot        槽位序号
     * @return 槽位记录；不存在时返回 {@link Optional#empty()}
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Optional<SlotItemRecord> loadSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND slot = ?"
             )) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            statement.setInt(4, slot);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(slot(resultSet)) : Optional.empty();
            }
        }
    }

    /**
     * 按物品哈希查找仓库中首个匹配的槽位记录，用于物品合并/查重。
     *
     * @param ownerType   所有者类型
     * @param ownerId     所有者 ID
     * @param warehouseId 仓库标识
     * @param itemHash    物品哈希值
     * @return 匹配的槽位记录；不存在时返回 {@link Optional#empty()}
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Optional<SlotItemRecord> findSlotByHash(String ownerType, String ownerId, String warehouseId, String itemHash) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND item_hash = ? ORDER BY slot ASC LIMIT 1"
             )) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            statement.setString(4, itemHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(slot(resultSet)) : Optional.empty();
            }
        }
    }

    /**
     * 插入或更新单个槽位物品记录。
     *
     * @param item 槽位物品记录
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void upsertSlot(SlotItemRecord item) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql(
                 "warehouse_slots",
                 List.of("owner_type", "owner_id", "warehouse_id", "slot"),
                 List.of("item_hash", "category_id", "display_name", "material_id", "search_text", "pinyin", "initials", "item_data", "item_json", "amount", "created_at", "updated_at")
             ))) {
            statement.setString(1, item.ownerType());
            statement.setString(2, item.ownerId());
            statement.setString(3, item.warehouseId());
            statement.setInt(4, item.slot());
            statement.setString(5, item.itemHash());
            statement.setString(6, item.categoryId());
            statement.setString(7, item.displayName());
            statement.setString(8, item.materialId());
            statement.setString(9, item.searchText());
            statement.setString(10, item.pinyin());
            statement.setString(11, item.initials());
            statement.setString(12, item.itemData());
            statement.setString(13, item.itemJson());
            statement.setLong(14, item.amount());
            statement.setLong(15, item.createdAt());
            statement.setLong(16, item.updatedAt());
            statement.executeUpdate();
        }
    }

    /**
     * 删除指定仓库的单个槽位记录。
     *
     * @param ownerType   所有者类型
     * @param ownerId     所有者 ID
     * @param warehouseId 仓库标识
     * @param slot        槽位序号
     * @throws SQLException 删除失败时抛出
     */
    @Override
    public void deleteSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND slot = ?"
             )) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            statement.setInt(4, slot);
            statement.executeUpdate();
        }
    }

    /**
     * 加载指定玩家的全部银行余额，以货币 ID 为键。
     *
     * @param playerUuid 玩家 UUID
     * @return 货币 ID 到余额的映射
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Map<String, BigDecimal> loadBankBalances(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT currency_id, balance FROM warehouse_bank_balances WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                LinkedHashMap<String, BigDecimal> result = new LinkedHashMap<>();
                while (resultSet.next()) {
                    result.put(resultSet.getString("currency_id"), decimal(resultSet.getString("balance")));
                }
                return result;
            }
        }
    }

    /**
     * 直接设置指定货币的银行余额（覆盖原值）。
     *
     * @param playerUuid 玩家 UUID
     * @param currencyId 货币 ID
     * @param amount     目标余额
     * @param updatedAt  更新时间戳
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void setBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_bank_balances", List.of("player_uuid", "currency_id"), List.of("balance", "updated_at")))) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, currencyId);
            statement.setString(3, amount.toPlainString());
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    /**
     * 原子增加银行余额（记录不存在则创建）。
     *
     * @param playerUuid 玩家 UUID
     * @param currencyId 货币 ID
     * @param amount     增加金额，必须大于 0
     * @param updatedAt  更新时间戳
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void creditBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sql = !isMysql()
            ? """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, currency_id) DO UPDATE SET
                    balance = CAST(balance AS REAL) + CAST(excluded.balance AS REAL),
                    updated_at = excluded.updated_at
                """
            : """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    balance = balance + VALUES(balance),
                    updated_at = VALUES(updated_at)
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, currencyId);
            statement.setString(3, amount.toPlainString());
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    /**
     * 原子扣减银行余额，仅在余额充足时成功。
     *
     * @param playerUuid 玩家 UUID
     * @param currencyId 货币 ID
     * @param amount     扣减金额，必须大于 0
     * @param updatedAt  更新时间戳
     * @return {@code true} 表示扣减成功；余额不足或金额无效时返回 {@code false}
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public boolean debitBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        String sql = !isMysql()
            ? """
                UPDATE warehouse_bank_balances
                SET balance = CAST(balance AS REAL) - ?, updated_at = ?
                WHERE player_uuid = ? AND currency_id = ? AND CAST(balance AS REAL) >= ?
                """
            : """
                UPDATE warehouse_bank_balances
                SET balance = CAST(balance AS DECIMAL(38,8)) - ?, updated_at = ?
                WHERE player_uuid = ? AND currency_id = ? AND CAST(balance AS DECIMAL(38,8)) >= ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, amount.toPlainString());
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, currencyId);
            statement.setString(5, amount.toPlainString());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * 原子领取定期存款：在事务中标记 claimed 并将本息入账。
     * <p>
     * 优先使用 {@code FOR UPDATE} 行锁；SQLite 等不支持时降级为条件 UPDATE。
     *
     * @param depositId 存款 ID
     * @param playerUuid 玩家 UUID
     * @param now       当前时间戳
     * @return 实际入账金额；不可领取（未到期/已领取/不存在）时返回 {@link Optional#empty()}
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public Optional<BigDecimal> claimFixedDepositAtomic(String depositId, UUID playerUuid, long now) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                FixedDepositRecord deposit;
                try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, player_uuid, product_id, currency_id, principal, interest_rate, created_at, matures_at, claimed, claimed_at"
                        + " FROM warehouse_fixed_deposits WHERE id = ? AND player_uuid = ? FOR UPDATE"
                )) {
                    select.setString(1, depositId);
                    select.setString(2, playerUuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next() || rs.getBoolean("claimed") || rs.getLong("matures_at") > now) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        deposit = new FixedDepositRecord(
                            rs.getString("id"),
                            playerUuid,
                            rs.getString("product_id"),
                            rs.getString("currency_id"),
                            decimal(rs.getString("principal")),
                            decimal(rs.getString("interest_rate")),
                            rs.getLong("created_at"),
                            rs.getLong("matures_at"),
                            false,
                            0L
                        );
                    }
                } catch (SQLException forUpdateUnsupported) {
                    // SQLite 等不支持 FOR UPDATE：降级为条件 UPDATE
                    connection.rollback();
                    return claimFixedDepositAtomicWithoutForUpdate(depositId, playerUuid, now);
                }

                BigDecimal payout = deposit.principal()
                    .add(deposit.principal().multiply(deposit.interestRate()))
                    .setScale(8, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros();

                try (PreparedStatement mark = connection.prepareStatement(
                    "UPDATE warehouse_fixed_deposits SET claimed = ?, claimed_at = ? WHERE id = ? AND claimed = ?"
                )) {
                    mark.setBoolean(1, true);
                    mark.setLong(2, now);
                    mark.setString(3, depositId);
                    mark.setBoolean(4, false);
                    if (mark.executeUpdate() == 0) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }

                creditBankBalanceOnConnection(connection, playerUuid, deposit.currencyId(), payout, now);
                connection.commit();
                return Optional.of(payout);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Optional<BigDecimal> claimFixedDepositAtomicWithoutForUpdate(String depositId, UUID playerUuid, long now) throws SQLException {
        List<FixedDepositRecord> deposits = loadFixedDeposits(playerUuid);
        Optional<FixedDepositRecord> optional = deposits.stream()
            .filter(d -> d.id().equals(depositId) && !d.claimed() && d.maturesAt() <= now)
            .findFirst();
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        FixedDepositRecord deposit = optional.get();
        BigDecimal payout = deposit.principal()
            .add(deposit.principal().multiply(deposit.interestRate()))
            .setScale(8, java.math.RoundingMode.DOWN)
            .stripTrailingZeros();

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement mark = connection.prepareStatement(
                    "UPDATE warehouse_fixed_deposits SET claimed = ?, claimed_at = ? WHERE id = ? AND claimed = ? AND matures_at <= ?"
                )) {
                    mark.setBoolean(1, true);
                    mark.setLong(2, now);
                    mark.setString(3, depositId);
                    mark.setBoolean(4, false);
                    mark.setLong(5, now);
                    if (mark.executeUpdate() == 0) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                creditBankBalanceOnConnection(connection, playerUuid, deposit.currencyId(), payout, now);
                connection.commit();
                return Optional.of(payout);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void creditBankBalanceOnConnection(
        Connection connection,
        UUID playerUuid,
        String currencyId,
        BigDecimal amount,
        long updatedAt
    ) throws SQLException {
        String sql = !isMysql()
            ? """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, currency_id) DO UPDATE SET
                    balance = CAST(balance AS REAL) + CAST(excluded.balance AS REAL),
                    updated_at = excluded.updated_at
                """
            : """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    balance = balance + VALUES(balance),
                    updated_at = VALUES(updated_at)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, currencyId);
            statement.setString(3, amount.toPlainString());
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    /**
     * 创建一条定期存款记录。
     *
     * @param deposit 定期存款记录
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void createFixedDeposit(FixedDepositRecord deposit) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO warehouse_fixed_deposits (id, player_uuid, product_id, currency_id, principal, interest_rate, created_at, matures_at, claimed, claimed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            statement.setString(1, deposit.id());
            statement.setString(2, deposit.playerUuid().toString());
            statement.setString(3, deposit.productId());
            statement.setString(4, deposit.currencyId());
            statement.setString(5, deposit.principal().toPlainString());
            statement.setString(6, deposit.interestRate().toPlainString());
            statement.setLong(7, deposit.createdAt());
            statement.setLong(8, deposit.maturesAt());
            statement.setBoolean(9, deposit.claimed());
            statement.setLong(10, deposit.claimedAt());
            statement.executeUpdate();
        }
    }

    /**
     * 加载指定玩家的全部定期存款记录，未领取的排在前面。
     *
     * @param playerUuid 玩家 UUID
     * @return 定期存款记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<FixedDepositRecord> loadFixedDeposits(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_fixed_deposits WHERE player_uuid = ? ORDER BY claimed ASC, matures_at ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FixedDepositRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new FixedDepositRecord(
                        resultSet.getString("id"),
                        playerUuid,
                        resultSet.getString("product_id"),
                        resultSet.getString("currency_id"),
                        decimal(resultSet.getString("principal")),
                        decimal(resultSet.getString("interest_rate")),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("matures_at"),
                        resultSet.getBoolean("claimed"),
                        resultSet.getLong("claimed_at")
                    ));
                }
                return result;
            }
        }
    }

    /**
     * 将指定定期存款标记为已领取。
     *
     * @param depositId 存款 ID
     * @param claimedAt 领取时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void markFixedDepositClaimed(String depositId, long claimedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_fixed_deposits SET claimed = ?, claimed_at = ? WHERE id = ?"
             )) {
            statement.setBoolean(1, true);
            statement.setLong(2, claimedAt);
            statement.setString(3, depositId);
            statement.executeUpdate();
        }
    }

    /**
     * 创建共享仓库并同时将创建者写入成员表（角色 owner），在事务中执行。
     *
     * @param warehouse 共享仓库记录
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public void createSharedWarehouse(SharedWarehouseRecord warehouse) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement shared = connection.prepareStatement(
                     "INSERT INTO warehouse_shared (id, owner_uuid, name, level, capacity, created_at, updated_at, showcase_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                 );
                 PreparedStatement member = connection.prepareStatement(upsertSql("warehouse_shared_members", List.of("shared_id", "player_uuid"), List.of("role", "updated_at")))) {
                shared.setString(1, warehouse.id());
                shared.setString(2, warehouse.ownerUuid().toString());
                shared.setString(3, warehouse.name());
                shared.setInt(4, warehouse.level());
                shared.setLong(5, warehouse.capacity());
                shared.setLong(6, warehouse.createdAt());
                shared.setLong(7, warehouse.updatedAt());
                shared.setBoolean(8, warehouse.showcaseEnabled());
                shared.executeUpdate();
                member.setString(1, warehouse.id());
                member.setString(2, warehouse.ownerUuid().toString());
                member.setString(3, "owner");
                member.setLong(4, warehouse.updatedAt());
                member.executeUpdate();
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
     * 更新共享仓库等级与容量。
     *
     * @param sharedId  共享仓库 ID
     * @param level     新等级
     * @param capacity  新容量
     * @param updatedAt 更新时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void updateSharedWarehouseLevel(String sharedId, int level, long capacity, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET level = ?, capacity = ?, updated_at = ? WHERE id = ?"
             )) {
            statement.setInt(1, level);
            statement.setLong(2, capacity);
            statement.setLong(3, updatedAt);
            statement.setString(4, sharedId);
            statement.executeUpdate();
        }
    }

    /**
     * 更新共享仓库名称。
     *
     * @param sharedId  共享仓库 ID
     * @param name      新名称
     * @param updatedAt 更新时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void updateSharedWarehouseName(String sharedId, String name, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET name = ?, updated_at = ? WHERE id = ?"
             )) {
            statement.setString(1, name);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.executeUpdate();
        }
    }

    /**
     * 更新共享仓库展示柜开关状态。
     *
     * @param sharedId        共享仓库 ID
     * @param showcaseEnabled 是否启用展示柜
     * @param updatedAt       更新时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void updateSharedWarehouseShowcase(String sharedId, boolean showcaseEnabled, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET showcase_enabled = ?, updated_at = ? WHERE id = ?"
             )) {
            statement.setBoolean(1, showcaseEnabled);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.executeUpdate();
        }
    }

    /**
     * 更新个人仓库自定义名称。
     *
     * @param playerUuid  玩家 UUID
     * @param warehouseId 仓库标识
     * @param customName  新名称，{@code null} 视为空串
     * @param updatedAt   更新时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void updatePersonalWarehouseName(UUID playerUuid, String warehouseId, String customName, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_personal SET custom_name = ?, updated_at = ? WHERE player_uuid = ? AND warehouse_id = ?"
             )) {
            statement.setString(1, customName == null ? "" : customName);
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, warehouseId);
            statement.executeUpdate();
        }
    }

    /**
     * 更新个人仓库展示柜开关状态。
     *
     * @param playerUuid      玩家 UUID
     * @param warehouseId     仓库标识
     * @param showcaseEnabled 是否启用展示柜
     * @param updatedAt       更新时间戳
     * @throws SQLException 更新失败时抛出
     */
    @Override
    public void updatePersonalWarehouseShowcase(UUID playerUuid, String warehouseId, boolean showcaseEnabled, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_personal SET showcase_enabled = ?, updated_at = ? WHERE player_uuid = ? AND warehouse_id = ?"
             )) {
            statement.setBoolean(1, showcaseEnabled);
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, warehouseId);
            statement.executeUpdate();
        }
    }

    /**
     * 删除指定个人仓库及其全部槽位数据，在事务中执行。
     *
     * @param playerUuid  玩家 UUID
     * @param warehouseId 仓库标识
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public void deletePersonalWarehouse(UUID playerUuid, String warehouseId) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement slots = connection.prepareStatement(
                     "DELETE FROM warehouse_slots WHERE owner_type = 'personal' AND owner_id = ? AND warehouse_id = ?");
                 PreparedStatement personal = connection.prepareStatement(
                     "DELETE FROM warehouse_personal WHERE player_uuid = ? AND warehouse_id = ?")) {
                slots.setString(1, playerUuid.toString());
                slots.setString(2, warehouseId);
                slots.executeUpdate();
                personal.setString(1, playerUuid.toString());
                personal.setString(2, warehouseId);
                personal.executeUpdate();
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
     * 转让共享仓库所有权：更新仓库 owner 并交换新旧 owner 的成员角色，在事务中执行。
     *
     * @param sharedId         共享仓库 ID
     * @param previousOwnerUuid 原 owner UUID
     * @param newOwnerUuid     新 owner UUID
     * @param updatedAt        更新时间戳
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public void transferSharedWarehouse(String sharedId, UUID previousOwnerUuid, UUID newOwnerUuid, long updatedAt) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement shared = connection.prepareStatement(
                     "UPDATE warehouse_shared SET owner_uuid = ?, updated_at = ? WHERE id = ? AND owner_uuid = ?"
                 );
                 PreparedStatement oldOwner = connection.prepareStatement(
                     "UPDATE warehouse_shared_members SET role = ?, updated_at = ? WHERE shared_id = ? AND player_uuid = ?"
                 );
                 PreparedStatement newOwner = connection.prepareStatement(
                     "UPDATE warehouse_shared_members SET role = ?, updated_at = ? WHERE shared_id = ? AND player_uuid = ?"
                 )) {
                shared.setString(1, newOwnerUuid.toString());
                shared.setLong(2, updatedAt);
                shared.setString(3, sharedId);
                shared.setString(4, previousOwnerUuid.toString());
                shared.executeUpdate();

                oldOwner.setString(1, "member");
                oldOwner.setLong(2, updatedAt);
                oldOwner.setString(3, sharedId);
                oldOwner.setString(4, previousOwnerUuid.toString());
                oldOwner.executeUpdate();

                newOwner.setString(1, "owner");
                newOwner.setLong(2, updatedAt);
                newOwner.setString(3, sharedId);
                newOwner.setString(4, newOwnerUuid.toString());
                newOwner.executeUpdate();
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
     * 写入待处理转让记录（先删除同 sharedId 的旧记录再插入），在事务中执行。
     *
     * @param transfer 待处理转让记录
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public void upsertPendingTransfer(PendingTransfer transfer) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM warehouse_pending_transfers WHERE shared_id = ?");
                 PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO warehouse_pending_transfers (shared_id, from_owner_uuid, to_uuid, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                delete.setString(1, transfer.sharedId());
                delete.executeUpdate();
                insert.setString(1, transfer.sharedId());
                insert.setString(2, transfer.fromOwnerUuid().toString());
                insert.setString(3, transfer.targetUuid().toString());
                insert.setLong(4, transfer.createdAt());
                insert.setLong(5, transfer.expiresAt());
                insert.executeUpdate();
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
     * 按共享仓库 ID 加载单条待处理转让记录。
     *
     * @param sharedId 共享仓库 ID
     * @return 转让记录；不存在时返回 {@link Optional#empty()}
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Optional<PendingTransfer> loadPendingTransfer(String sharedId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT from_owner_uuid, to_uuid, created_at, expires_at FROM warehouse_pending_transfers WHERE shared_id = ?")) {
            statement.setString(1, sharedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(new PendingTransfer(sharedId,
                    UUID.fromString(resultSet.getString("from_owner_uuid")),
                    UUID.fromString(resultSet.getString("to_uuid")),
                    resultSet.getLong("created_at"), resultSet.getLong("expires_at")));
            }
        }
    }

    /**
     * 加载指定目标玩家的全部待处理转让记录，按创建时间升序排列。
     *
     * @param targetUuid 目标玩家 UUID
     * @return 转让记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<PendingTransfer> loadPendingTransfers(UUID targetUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT shared_id, from_owner_uuid, created_at, expires_at FROM warehouse_pending_transfers WHERE to_uuid = ? ORDER BY created_at ASC")) {
            statement.setString(1, targetUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PendingTransfer> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new PendingTransfer(resultSet.getString("shared_id"),
                        UUID.fromString(resultSet.getString("from_owner_uuid")), targetUuid,
                        resultSet.getLong("created_at"), resultSet.getLong("expires_at")));
                }
                return result;
            }
        }
    }

    /**
     * 删除指定共享仓库的待处理转让记录。
     *
     * @param sharedId 共享仓库 ID
     * @throws SQLException 删除失败时抛出
     */
    @Override
    public void deletePendingTransfer(String sharedId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM warehouse_pending_transfers WHERE shared_id = ?")) {
            statement.setString(1, sharedId);
            statement.executeUpdate();
        }
    }

    /**
     * 删除共享仓库及其全部关联数据（槽位、成员、待处理转让），在事务中执行。
     *
     * @param sharedId 共享仓库 ID
     * @throws SQLException 事务执行失败时抛出
     */
    @Override
    public void deleteSharedWarehouse(String sharedId) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement slots = connection.prepareStatement("DELETE FROM warehouse_slots WHERE owner_type = 'shared' AND owner_id = ?");
                 PreparedStatement members = connection.prepareStatement("DELETE FROM warehouse_shared_members WHERE shared_id = ?");
                 PreparedStatement pending = connection.prepareStatement("DELETE FROM warehouse_pending_transfers WHERE shared_id = ?");
                 PreparedStatement shared = connection.prepareStatement("DELETE FROM warehouse_shared WHERE id = ?")) {
                slots.setString(1, sharedId);
                slots.executeUpdate();
                members.setString(1, sharedId);
                members.executeUpdate();
                pending.setString(1, sharedId);
                pending.executeUpdate();
                shared.setString(1, sharedId);
                shared.executeUpdate();
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
     * 加载指定玩家参与的全部共享仓库记录（含其角色），按创建时间降序排列。
     *
     * @param playerUuid 玩家 UUID
     * @return 共享仓库记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<SharedWarehouseRecord> loadSharedWarehouses(UUID playerUuid) throws SQLException {
        String sql = """
            SELECT s.id, s.owner_uuid, s.name, s.level, s.capacity, s.created_at, s.updated_at, m.role, s.showcase_enabled
            FROM warehouse_shared s
            JOIN warehouse_shared_members m ON s.id = m.shared_id
            WHERE m.player_uuid = ?
            ORDER BY s.created_at DESC
            """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SharedWarehouseRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new SharedWarehouseRecord(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getLong("capacity"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        resultSet.getString("role"),
                        resultSet.getBoolean("showcase_enabled")
                    ));
                }
                return result;
            }
        }
    }

    /**
     * 按共享仓库 ID 查询单条记录（含成员角色），避免加载全部列表。
     *
     * @param playerUuid 玩家 UUID
     * @param sharedId   共享仓库 ID
     * @return 共享仓库记录；玩家非成员或仓库不存在时返回 {@link Optional#empty()}
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Optional<SharedWarehouseRecord> loadSharedWarehouse(UUID playerUuid, String sharedId) throws SQLException {
        String sql = """
            SELECT s.id, s.owner_uuid, s.name, s.level, s.capacity, s.created_at, s.updated_at, m.role, s.showcase_enabled
            FROM warehouse_shared s
            JOIN warehouse_shared_members m ON s.id = m.shared_id
            WHERE m.player_uuid = ? AND s.id = ?
            """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, sharedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new SharedWarehouseRecord(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getLong("capacity"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        resultSet.getString("role"),
                        resultSet.getBoolean("showcase_enabled")
                    ));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * 加载指定玩家拥有的全部共享仓库记录（角色固定为 owner），按创建时间降序排列。
     *
     * @param ownerUuid owner UUID
     * @return 共享仓库记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<SharedWarehouseRecord> loadSharedWarehousesByOwner(UUID ownerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, owner_uuid, name, level, capacity, created_at, updated_at, showcase_enabled FROM warehouse_shared WHERE owner_uuid = ? ORDER BY created_at DESC"
             )) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SharedWarehouseRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new SharedWarehouseRecord(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getLong("capacity"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        "owner",
                        resultSet.getBoolean("showcase_enabled")
                    ));
                }
                return result;
            }
        }
    }

    /**
     * 加载指定共享仓库的全部成员记录，按角色和更新时间排序。
     *
     * @param sharedId 共享仓库 ID
     * @return 成员记录列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<SharedMemberRecord> loadSharedMembers(String sharedId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT shared_id, player_uuid, role, updated_at FROM warehouse_shared_members WHERE shared_id = ? ORDER BY role ASC, updated_at ASC"
             )) {
            statement.setString(1, sharedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SharedMemberRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new SharedMemberRecord(
                        resultSet.getString("shared_id"),
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("role"),
                        resultSet.getLong("updated_at")
                    ));
                }
                return result;
            }
        }
    }

    /**
     * 统计指定玩家拥有的共享仓库数量。
     *
     * @param ownerUuid owner UUID
     * @return 仓库数量
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public int countOwnedSharedWarehouses(UUID ownerUuid) throws SQLException {
        return count("SELECT COUNT(*) FROM warehouse_shared WHERE owner_uuid = ?", ownerUuid.toString());
    }

    /**
     * 统计指定共享仓库的成员数量。
     *
     * @param sharedId 共享仓库 ID
     * @return 成员数量
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public int countSharedMembers(String sharedId) throws SQLException {
        return count("SELECT COUNT(*) FROM warehouse_shared_members WHERE shared_id = ?", sharedId);
    }

    /**
     * 插入或更新共享仓库成员记录（角色、更新时间）。
     *
     * @param sharedId   共享仓库 ID
     * @param playerUuid 玩家 UUID
     * @param role       成员角色（owner / member 等）
     * @param updatedAt  更新时间戳
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void upsertSharedMember(String sharedId, UUID playerUuid, String role, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_shared_members", List.of("shared_id", "player_uuid"), List.of("role", "updated_at")))) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    /**
     * 移除共享仓库成员（owner 角色不可移除）。
     *
     * @param sharedId   共享仓库 ID
     * @param playerUuid 玩家 UUID
     * @throws SQLException 删除失败时抛出
     */
    @Override
    public void removeSharedMember(String sharedId, UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_shared_members WHERE shared_id = ? AND player_uuid = ? AND role <> 'owner'"
             )) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    /**
     * 关闭存储，释放连接池等资源。
     */
    @Override
    public void close() {
        shutdown();
    }

    private void migrateSchema(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE warehouse_shared ADD COLUMN showcase_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        } catch (SQLException ignored) {
            // Column already exists or dialect does not support ADD COLUMN syntax.
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE warehouse_personal ADD COLUMN showcase_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        } catch (SQLException ignored) {
            // Column already exists or dialect does not support ADD COLUMN syntax.
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_personal (
                    player_uuid VARCHAR(36) NOT NULL,
                    warehouse_id VARCHAR(64) NOT NULL,
                    level INTEGER NOT NULL,
                    custom_name VARCHAR(64) NOT NULL,
                    showcase_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, warehouse_id)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_slots (
                    owner_type VARCHAR(16) NOT NULL,
                    owner_id VARCHAR(64) NOT NULL,
                    warehouse_id VARCHAR(64) NOT NULL,
                    slot INTEGER NOT NULL,
                    item_hash VARCHAR(64) NOT NULL,
                    category_id VARCHAR(64) NOT NULL,
                    display_name VARCHAR(255) NOT NULL,
                    material_id VARCHAR(128) NOT NULL,
                    search_text TEXT NOT NULL,
                    pinyin TEXT NOT NULL,
                    initials TEXT NOT NULL,
                    item_data LONGTEXT NOT NULL,
                    item_json LONGTEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (owner_type, owner_id, warehouse_id, slot)
                )
                """);
            if (!isMysql()) {
                statement.execute("CREATE INDEX IF NOT EXISTS idx_warehouse_slots_hash ON warehouse_slots(owner_type, owner_id, warehouse_id, item_hash)");
            } else {
                try {
                    statement.execute("CREATE INDEX idx_warehouse_slots_hash ON warehouse_slots(owner_type, owner_id, warehouse_id, item_hash)");
                } catch (SQLException ignored) {
                    // MySQL has no portable CREATE INDEX IF NOT EXISTS; duplicate index means the schema is already ready.
                }
            }
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_bank_balances (
                    player_uuid VARCHAR(36) NOT NULL,
                    currency_id VARCHAR(64) NOT NULL,
                    balance VARCHAR(64) NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, currency_id)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_fixed_deposits (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    product_id VARCHAR(64) NOT NULL,
                    currency_id VARCHAR(64) NOT NULL,
                    principal VARCHAR(64) NOT NULL,
                    interest_rate VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    matures_at BIGINT NOT NULL,
                    claimed BOOLEAN NOT NULL,
                    claimed_at BIGINT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_shared (
                    id VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(64) NOT NULL,
                    level INTEGER NOT NULL,
                    capacity BIGINT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    showcase_enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_shared_members (
                    shared_id VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (shared_id, player_uuid)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_pending_transfers (
                    shared_id VARCHAR(36) PRIMARY KEY,
                    from_owner_uuid VARCHAR(36) NOT NULL,
                    to_uuid VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """);
        }
    }

    private SlotItemRecord slot(ResultSet resultSet) throws SQLException {
        return new SlotItemRecord(
            resultSet.getString("owner_type"),
            resultSet.getString("owner_id"),
            resultSet.getString("warehouse_id"),
            resultSet.getInt("slot"),
            resultSet.getString("item_hash"),
            resultSet.getString("category_id"),
            resultSet.getString("display_name"),
            resultSet.getString("material_id"),
            resultSet.getString("search_text"),
            resultSet.getString("pinyin"),
            resultSet.getString("initials"),
            resultSet.getString("item_data"),
            resultSet.getString("item_json"),
            resultSet.getLong("amount"),
            resultSet.getLong("created_at"),
            resultSet.getLong("updated_at")
        );
    }

    private int count(String sql, String parameter) throws SQLException {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
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

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }
}
