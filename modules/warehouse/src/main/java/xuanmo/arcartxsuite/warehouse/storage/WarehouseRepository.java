package xuanmo.arcartxsuite.warehouse.storage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository {

    void initialize() throws SQLException;

    List<WarehouseRecord> loadPersonalWarehouses(UUID playerUuid) throws SQLException;

    void upsertPersonalWarehouse(UUID playerUuid, String warehouseId, int level, String customName, long updatedAt) throws SQLException;

    void updatePersonalWarehouseName(UUID playerUuid, String warehouseId, String customName, long updatedAt) throws SQLException;

    void updatePersonalWarehouseShowcase(UUID playerUuid, String warehouseId, boolean showcaseEnabled, long updatedAt) throws SQLException;

    List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) throws SQLException;

    Optional<SlotItemRecord> loadSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException;

    Optional<SlotItemRecord> findSlotByHash(String ownerType, String ownerId, String warehouseId, String itemHash) throws SQLException;

    void upsertSlot(SlotItemRecord item) throws SQLException;

    void deleteSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException;

    Map<String, BigDecimal> loadBankBalances(UUID playerUuid) throws SQLException;

    void setBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException;

    /** 原子增加银行余额（记录不存在则创建）。 */
    void creditBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException;

    /**
     * 原子扣减银行余额。
     *
     * @return {@code true} 表示扣减成功
     */
    boolean debitBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException;

    /**
     * 原子领取定期存款：标记 claimed 并将本息入账。
     *
     * @return 实际入账金额；不可领取时返回 {@link Optional#empty()}
     */
    java.util.Optional<BigDecimal> claimFixedDepositAtomic(String depositId, UUID playerUuid, long now) throws SQLException;

    void createFixedDeposit(FixedDepositRecord deposit) throws SQLException;

    List<FixedDepositRecord> loadFixedDeposits(UUID playerUuid) throws SQLException;

    void markFixedDepositClaimed(String depositId, long claimedAt) throws SQLException;

    void createSharedWarehouse(SharedWarehouseRecord warehouse) throws SQLException;

    void updateSharedWarehouseLevel(String sharedId, int level, long capacity, long updatedAt) throws SQLException;

    void updateSharedWarehouseName(String sharedId, String name, long updatedAt) throws SQLException;

    void updateSharedWarehouseShowcase(String sharedId, boolean showcaseEnabled, long updatedAt) throws SQLException;

    void transferSharedWarehouse(String sharedId, UUID previousOwnerUuid, UUID newOwnerUuid, long updatedAt) throws SQLException;

    void deleteSharedWarehouse(String sharedId) throws SQLException;

    List<SharedWarehouseRecord> loadSharedWarehouses(UUID playerUuid) throws SQLException;

    List<SharedWarehouseRecord> loadSharedWarehousesByOwner(UUID ownerUuid) throws SQLException;

    List<SharedMemberRecord> loadSharedMembers(String sharedId) throws SQLException;

    int countOwnedSharedWarehouses(UUID ownerUuid) throws SQLException;

    int countSharedMembers(String sharedId) throws SQLException;

    void upsertSharedMember(String sharedId, UUID playerUuid, String role, long updatedAt) throws SQLException;

    void removeSharedMember(String sharedId, UUID playerUuid) throws SQLException;

    Optional<SecurityRecord> loadSecurity(UUID playerUuid) throws SQLException;

    void saveSecurity(SecurityRecord security) throws SQLException;

    void clearSecurity(UUID playerUuid) throws SQLException;

    void close();

    record WarehouseRecord(UUID playerUuid, String warehouseId, int level, String customName, boolean showcaseEnabled, long updatedAt) {
    }

    record SlotItemRecord(
        String ownerType,
        String ownerId,
        String warehouseId,
        int slot,
        String itemHash,
        String categoryId,
        String displayName,
        String materialId,
        String searchText,
        String pinyin,
        String initials,
        String itemData,
        String itemJson,
        long amount,
        long createdAt,
        long updatedAt
    ) {
    }

    record FixedDepositRecord(
        String id,
        UUID playerUuid,
        String productId,
        String currencyId,
        BigDecimal principal,
        BigDecimal interestRate,
        long createdAt,
        long maturesAt,
        boolean claimed,
        long claimedAt
    ) {
    }

    record SharedWarehouseRecord(
        String id,
        UUID ownerUuid,
        String name,
        int level,
        long capacity,
        long createdAt,
        long updatedAt,
        String viewerRole,
        boolean showcaseEnabled
    ) {
    }

    record SharedMemberRecord(String sharedId, UUID playerUuid, String role, long updatedAt) {
    }

    record SecurityRecord(
        UUID playerUuid,
        String saltBase64,
        String hashBase64,
        String encryptedPassword,
        long updatedAt
    ) {
    }
}
