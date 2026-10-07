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

    /** 个人仓库额外购买槽位数原子递增（可为负值，结果不小于 0）。 */
    void addPersonalExtraSlots(UUID playerUuid, String warehouseId, long delta, long updatedAt) throws SQLException;

    /** 直接设置个人仓库额外购买槽位数。 */
    void setPersonalExtraSlots(UUID playerUuid, String warehouseId, long extraSlots, long updatedAt) throws SQLException;

    void updatePersonalWarehouseName(UUID playerUuid, String warehouseId, String customName, long updatedAt) throws SQLException;

    void updatePersonalWarehouseShowcase(UUID playerUuid, String warehouseId, boolean showcaseEnabled, long updatedAt) throws SQLException;

    void deletePersonalWarehouse(UUID playerUuid, String warehouseId) throws SQLException;

    List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) throws SQLException;

    /**
     * 加载槽位摘要：包含除 {@code item_data} 序列化大字段外的全部列。
     * <p>
     * 返回记录的 {@link SlotItemRecord#itemData()} 为空串——仅供列表展示、搜索、
     * 合并扫描等元数据场景使用，严禁回传给 {@link #upsertSlot}（会清空物品数据）。
     */
    List<SlotItemRecord> loadSlotSummaries(String ownerType, String ownerId, String warehouseId) throws SQLException;

    Optional<SlotItemRecord> loadSlot(String ownerType, String ownerId, String warehouseId, int slot) throws SQLException;

    Optional<SlotItemRecord> findSlotByHash(String ownerType, String ownerId, String warehouseId, String itemHash) throws SQLException;

    void upsertSlot(SlotItemRecord item) throws SQLException;

    /** 仅更新槽位数量与时间戳，不重写物品数据大字段（聚合合并/部分取出用）。 */
    void updateSlotAmount(String ownerType, String ownerId, String warehouseId, int slot, long amount, long updatedAt) throws SQLException;

    /** 仅更新槽位分类与时间戳（分类规则变更后的懒重归类用）。 */
    void updateSlotCategory(String ownerType, String ownerId, String warehouseId, int slot, String categoryId, long updatedAt) throws SQLException;

    /** 回写展示用 lore 文本（旧行 lore_text 为 NULL 时的惰性回填用）。 */
    void updateSlotLore(String ownerType, String ownerId, String warehouseId, int slot, String loreText, long updatedAt) throws SQLException;

    /** 槽位占用数（已用格数），不加载物品数据。 */
    long countSlots(String ownerType, String ownerId, String warehouseId) throws SQLException;

    /** 物品总件数（amount 求和），不加载物品数据。 */
    long sumAmount(String ownerType, String ownerId, String warehouseId) throws SQLException;

    /** 指定分类下的物品总件数，不加载物品数据。 */
    long sumAmountByCategory(String ownerType, String ownerId, String warehouseId, String categoryId) throws SQLException;

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

    /**
     * 删除已领取的定期存款记录。
     *
     * @return {@code true} 表示删除成功；记录不存在或未领取时返回 {@code false}
     */
    boolean deleteFixedDeposit(String depositId, UUID playerUuid) throws SQLException;

    void createSharedWarehouse(SharedWarehouseRecord warehouse) throws SQLException;

    void updateSharedWarehouseLevel(String sharedId, int level, long capacity, long updatedAt) throws SQLException;

    void updateSharedWarehouseName(String sharedId, String name, long updatedAt) throws SQLException;

    void updateSharedWarehouseShowcase(String sharedId, boolean showcaseEnabled, long updatedAt) throws SQLException;

    void transferSharedWarehouse(String sharedId, UUID previousOwnerUuid, UUID newOwnerUuid, long updatedAt) throws SQLException;

    void upsertPendingTransfer(PendingTransfer transfer) throws SQLException;

    Optional<PendingTransfer> loadPendingTransfer(String sharedId) throws SQLException;

    List<PendingTransfer> loadPendingTransfers(UUID targetUuid) throws SQLException;

    void deletePendingTransfer(String sharedId) throws SQLException;

    void deleteSharedWarehouse(String sharedId) throws SQLException;

    List<SharedWarehouseRecord> loadSharedWarehouses(UUID playerUuid) throws SQLException;

    /** 按共享仓库 ID 查询单条记录（含成员角色），避免加载全部列表。 */
    Optional<SharedWarehouseRecord> loadSharedWarehouse(UUID playerUuid, String sharedId) throws SQLException;

    List<SharedWarehouseRecord> loadSharedWarehousesByOwner(UUID ownerUuid) throws SQLException;

    List<SharedMemberRecord> loadSharedMembers(String sharedId) throws SQLException;

    int countOwnedSharedWarehouses(UUID ownerUuid) throws SQLException;

    int countSharedMembers(String sharedId) throws SQLException;

    void upsertSharedMember(String sharedId, UUID playerUuid, String role, long updatedAt) throws SQLException;

    void removeSharedMember(String sharedId, UUID playerUuid) throws SQLException;

    /** 加载玩家个人设置（自动入库/怪物战利品/入库通知）。 */
    Optional<PlayerSettingsRecord> loadPlayerSettings(UUID playerUuid) throws SQLException;

    /** 保存或更新玩家个人设置。 */
    void savePlayerSettings(UUID playerUuid, boolean autoPickup, boolean autoMythic, boolean autoNotify, long updatedAt) throws SQLException;

    void close();

    record WarehouseRecord(UUID playerUuid, String warehouseId, int level, String customName, boolean showcaseEnabled, long updatedAt, long extraSlots) {
    }

    /**
     * @param loreText 展示用 lore 文本（存入时写入，{@code \n} 分行）；
     *                 {@code null} 表示历史遗留行未回填
     */
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
        long updatedAt,
        String loreText
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

    record PendingTransfer(String sharedId, UUID fromOwnerUuid, UUID targetUuid, long createdAt, long expiresAt) {
    }

    record SharedMemberRecord(String sharedId, UUID playerUuid, String role, long updatedAt) {
    }

    record PlayerSettingsRecord(UUID playerUuid, boolean autoPickup, boolean autoMythic, boolean autoNotify, long updatedAt) {
    }

}
