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
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.StorageDialect;

public final class JdbcWarehouseRepository extends AbstractModuleRepository implements WarehouseRepository {

    private final StorageConfiguration configuration;

    public JdbcWarehouseRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-Warehouse", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        migrateSchema(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("warehouse_personal", "warehouse_bank_balances", "warehouse_fixed_deposits", "warehouse_security", "warehouse_shared_members");
    }

    @Override
    protected List<String> allTables() {
        return List.of(
            "warehouse_personal",
            "warehouse_slots",
            "warehouse_bank_balances",
            "warehouse_fixed_deposits",
            "warehouse_shared",
            "warehouse_shared_members",
            "warehouse_security",
            "warehouse_pending_transfers"
        );
    }

    @Override
    public List<WarehouseRecord> loadPersonalWarehouses(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void upsertPersonalWarehouse(UUID playerUuid, String warehouseId, int level, String customName, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_personal", List.of("player_uuid", "warehouse_id"), List.of("level", "custom_name", "updated_at")))) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, warehouseId);
            statement.setInt(3, level);
            statement.setString(4, customName == null ? "" : customName);
            statement.setLong(5, updatedAt);
            statement.executeUpdate();
        }
    }

    @Override
    public List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) throws SQLException {
        try (Connection connection = connection();
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
    public Optional<SlotItemRecord> loadSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public Optional<SlotItemRecord> findSlotByHash(String ownerType, String ownerId, String warehouseId, String itemHash) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void upsertSlot(SlotItemRecord item) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void deleteSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public Map<String, BigDecimal> loadBankBalances(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void setBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_bank_balances", List.of("player_uuid", "currency_id"), List.of("balance", "updated_at")))) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, currencyId);
            statement.setString(3, amount.toPlainString());
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    @Override
    public void creditBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sql = configuration.dialect() == StorageDialect.SQLITE
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
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, currencyId);
            statement.setString(3, amount.toPlainString());
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean debitBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        String sql = "UPDATE warehouse_bank_balances SET balance = balance - ?, updated_at = ?"
            + " WHERE player_uuid = ? AND currency_id = ? AND CAST(balance AS REAL) >= ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, amount.toPlainString());
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, currencyId);
            statement.setString(5, amount.toPlainString());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<BigDecimal> claimFixedDepositAtomic(String depositId, UUID playerUuid, long now) throws SQLException {
        try (Connection connection = connection()) {
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

        try (Connection connection = connection()) {
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
        String sql = configuration.dialect() == StorageDialect.SQLITE
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

    @Override
    public void createFixedDeposit(FixedDepositRecord deposit) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public List<FixedDepositRecord> loadFixedDeposits(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void markFixedDepositClaimed(String depositId, long claimedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_fixed_deposits SET claimed = ?, claimed_at = ? WHERE id = ?"
             )) {
            statement.setBoolean(1, true);
            statement.setLong(2, claimedAt);
            statement.setString(3, depositId);
            statement.executeUpdate();
        }
    }

    @Override
    public void createSharedWarehouse(SharedWarehouseRecord warehouse) throws SQLException {
        try (Connection connection = connection()) {
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

    @Override
    public void updateSharedWarehouseLevel(String sharedId, int level, long capacity, long updatedAt) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void updateSharedWarehouseName(String sharedId, String name, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET name = ?, updated_at = ? WHERE id = ?"
             )) {
            statement.setString(1, name);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateSharedWarehouseShowcase(String sharedId, boolean showcaseEnabled, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET showcase_enabled = ?, updated_at = ? WHERE id = ?"
             )) {
            statement.setBoolean(1, showcaseEnabled);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.executeUpdate();
        }
    }

    @Override
    public void updatePersonalWarehouseName(UUID playerUuid, String warehouseId, String customName, long updatedAt) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void updatePersonalWarehouseShowcase(UUID playerUuid, String warehouseId, boolean showcaseEnabled, long updatedAt) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void deletePersonalWarehouse(UUID playerUuid, String warehouseId) throws SQLException {
        try (Connection connection = connection()) {
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

    @Override
    public void transferSharedWarehouse(String sharedId, UUID previousOwnerUuid, UUID newOwnerUuid, long updatedAt) throws SQLException {
        try (Connection connection = connection()) {
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

    @Override
    public void upsertPendingTransfer(PendingTransfer transfer) throws SQLException {
        try (Connection connection = connection()) {
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

    @Override
    public Optional<PendingTransfer> loadPendingTransfer(String sharedId) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public List<PendingTransfer> loadPendingTransfers(UUID targetUuid) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public void deletePendingTransfer(String sharedId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM warehouse_pending_transfers WHERE shared_id = ?")) {
            statement.setString(1, sharedId);
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteSharedWarehouse(String sharedId) throws SQLException {
        try (Connection connection = connection()) {
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

    @Override
    public List<SharedWarehouseRecord> loadSharedWarehouses(UUID playerUuid) throws SQLException {
        String sql = """
            SELECT s.id, s.owner_uuid, s.name, s.level, s.capacity, s.created_at, s.updated_at, m.role, s.showcase_enabled
            FROM warehouse_shared s
            JOIN warehouse_shared_members m ON s.id = m.shared_id
            WHERE m.player_uuid = ?
            ORDER BY s.created_at DESC
            """;
        try (Connection connection = connection();
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

    @Override
    public List<SharedWarehouseRecord> loadSharedWarehousesByOwner(UUID ownerUuid) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public List<SharedMemberRecord> loadSharedMembers(String sharedId) throws SQLException {
        try (Connection connection = connection();
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

    @Override
    public int countOwnedSharedWarehouses(UUID ownerUuid) throws SQLException {
        return count("SELECT COUNT(*) FROM warehouse_shared WHERE owner_uuid = ?", ownerUuid.toString());
    }

    @Override
    public int countSharedMembers(String sharedId) throws SQLException {
        return count("SELECT COUNT(*) FROM warehouse_shared_members WHERE shared_id = ?", sharedId);
    }

    @Override
    public void upsertSharedMember(String sharedId, UUID playerUuid, String role, long updatedAt) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_shared_members", List.of("shared_id", "player_uuid"), List.of("role", "updated_at")))) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    @Override
    public void removeSharedMember(String sharedId, UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_shared_members WHERE shared_id = ? AND player_uuid = ? AND role <> 'owner'"
             )) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<SecurityRecord> loadSecurity(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_security WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                    ? Optional.of(new SecurityRecord(playerUuid, resultSet.getString("salt"), resultSet.getString("hash"), resultSet.getString("encrypted_password"), resultSet.getLong("updated_at")))
                    : Optional.empty();
            }
        }
    }

    @Override
    public void saveSecurity(SecurityRecord security) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_security", List.of("player_uuid"), List.of("salt", "hash", "encrypted_password", "updated_at")))) {
            statement.setString(1, security.playerUuid().toString());
            statement.setString(2, security.saltBase64());
            statement.setString(3, security.hashBase64());
            statement.setString(4, security.encryptedPassword());
            statement.setLong(5, security.updatedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public void clearSecurity(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM warehouse_security WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

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
            if (configuration.dialect() == StorageDialect.SQLITE) {
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
                CREATE TABLE IF NOT EXISTS warehouse_security (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    salt VARCHAR(128) NOT NULL,
                    hash VARCHAR(256) NOT NULL,
                    encrypted_password TEXT NOT NULL,
                    updated_at BIGINT NOT NULL
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
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
