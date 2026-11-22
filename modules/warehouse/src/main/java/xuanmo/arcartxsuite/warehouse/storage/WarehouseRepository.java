package xuanmo.arcartxsuite.warehouse.storage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository {

    void initialize() throws SQLException;

    List<WarehouseRecord> loadPersonalWarehouses(UUID playerUuid) throws SQLException;

    void upsertPersonalWarehouse(UUID playerUuid, String warehouseId, int level, String customName, long updatedAt) throws SQLException;

    void ensureDefaultPersonalWarehouse(UUID playerUuid, String warehouseId, long now) throws SQLException;

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

    List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId, List<Integer> slots) throws SQLException;

    Set<String> loadPins(UUID playerUuid, String warehouseId) throws SQLException;

    void setPin(UUID playerUuid, String warehouseId, String itemHash, boolean pinned) throws SQLException;

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

    /** 一次提交槽位变更；旧数量或物品已变化时整批回滚，避免跨服覆盖与部分提交。 */
    boolean applySlotChanges(List<SlotChange> changes) throws SQLException;
    boolean applySharedSlotChanges(List<SlotChange> changes, SharedActor actor) throws SQLException;

    String INVENTORY_TRANSFER = "warehouse-inventory-v1";
    String INVENTORY_DEPOSIT = "MANUAL_DEPOSIT";
    String INVENTORY_WITHDRAW_RESERVED = "MANUAL_RESERVED";
    String INVENTORY_WITHDRAW = "MANUAL_WITHDRAW";
    String INVENTORY_DONE = "MANUAL_DONE";

    boolean stageInventoryTransfer(MaterialTransfer transfer, List<SlotChange> changes, SharedActor actor) throws SQLException;
    boolean settleInventoryTransfer(MaterialTransfer transfer, List<SlotChange> changes, SharedActor actor,
                                    boolean restored) throws SQLException;
    Optional<MaterialTransfer> pendingInventoryTransfer(UUID player, String serverId) throws SQLException;

    boolean acquireSharedLease(String sharedId, UUID player, UUID token, String name, String node, long durationMs)
        throws SQLException;

    boolean renewSharedLease(String sharedId, UUID player, UUID token, long durationMs) throws SQLException;

    void releaseSharedLease(String sharedId, UUID player, UUID token) throws SQLException;

    Optional<SharedLease> sharedLease(String sharedId) throws SQLException;
    List<SharedActivity> sharedActivity(String warehouseId, UUID owner, String member, String item,
                                        long since, int offset, int limit) throws SQLException;
    boolean escrowMaterials(List<SlotChange> changes, MaterialTransfer transfer) throws SQLException;
    boolean commitGroundDeposit(List<SlotChange> changes, MaterialTransfer transfer, long storedAmount) throws SQLException;
    boolean commitRewardDeposit(List<SlotChange> changes, MaterialTransfer receipt) throws SQLException;
    boolean transitionMaterialTransfer(String operationId, String ownerId, String from, String to, String note) throws SQLException;
    boolean restoreHeldMaterialTransfer(MaterialTransfer transfer, List<SlotChange> reverse) throws SQLException;
    List<MaterialTransfer> oldMaterialTransfers(String serverId, String runId, String afterId, int limit) throws SQLException;
    List<MaterialTransfer> materialTransfers(String ownerId, int limit) throws SQLException;
    java.util.Optional<MaterialTransfer> materialTransfer(String id, String ownerId) throws SQLException;
    default GroundSource groundSource(String operationId) throws SQLException { return null; }
    default boolean prepareGroundSource(MaterialTransfer transfer, GroundSource source) throws SQLException { return false; }
    default boolean finishGroundSource(GroundSource source, boolean committed) throws SQLException { return false; }
    default boolean supportsParallelGroundSettlement() { return false; }

    record GroundSource(String id, String owner, String world, String entity, int chunkX, int chunkZ,
                        int original, int stored, String state) {
        public int remaining() { return original - stored; }
    }

    record MaterialTransfer(String id, String ownerId, String warehouseId, String serverId, String runId,
        String state, String adapter, String target, String evidence, String payload, long createdAt, long updatedAt, String note) {
        public MaterialTransfer withState(String next, String nextNote) {
            return new MaterialTransfer(id, ownerId, warehouseId, serverId, runId, next, adapter, target, evidence,
                payload, createdAt, updatedAt, nextNote);
        }
    }

    record SlotChange(SlotItemRecord before, SlotItemRecord after) { }

    record SharedActor(UUID id, String name, boolean discarded, UUID leaseToken) { }

    record SharedLease(UUID player, UUID token, String name, String node) { }
    record SharedActivity(String actor, String action, String item, long amount, long time) { }

    Map<String, BigDecimal> loadBankBalances(UUID playerUuid) throws SQLException;

    void setBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException;

    /** 原子增加银行余额（记录不存在则创建）。 */
    void creditBankBalance(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException;

    record BankTransfer(String id, UUID playerUuid, String currencyId, BigDecimal amount, long createdAt) { }

    /** Reserve bank funds and retain a receipt until the external credit is confirmed. */
    boolean beginBankWithdrawal(BankTransfer withdrawal) throws SQLException;

    /** Close a confirmed withdrawal. Only a definite rejection restores the reserved funds. */
    void finishBankWithdrawal(BankTransfer withdrawal, boolean credited) throws SQLException;

    void beginBankDeposit(BankTransfer deposit) throws SQLException;

    /** Consume the original receipt in the same transaction as the bank credit. */
    void creditBankDeposit(BankTransfer deposit) throws SQLException;

    /** Only an unconsumed deposit receipt can authorize a refund. */
    boolean reserveBankDepositRefund(BankTransfer deposit) throws SQLException;

    void closeBankDeposit(BankTransfer deposit, boolean refunded) throws SQLException;

    /**
     * 原子领取定期存款：标记 claimed 并将本息入账。
     *
     * @return 实际入账金额；不可领取时返回 {@link Optional#empty()}
     */
    java.util.Optional<BigDecimal> claimFixedDepositAtomic(String depositId, UUID playerUuid, long now) throws SQLException;

    boolean purchaseFixedDeposit(FixedDepositRecord deposit) throws SQLException;

    List<FixedDepositRecord> loadFixedDeposits(UUID playerUuid) throws SQLException;

    void markFixedDepositClaimed(String depositId, long claimedAt) throws SQLException;

    /**
     * 删除已领取的定期存款记录。
     *
     * @return {@code true} 表示删除成功；记录不存在或未领取时返回 {@code false}
     */
    boolean deleteFixedDeposit(String depositId, UUID playerUuid) throws SQLException;

    void createSharedWarehouse(SharedWarehouseRecord warehouse) throws SQLException;

    void updateSharedWarehouseLevel(String sharedId, UUID ownerUuid, int expectedLevel, int level, long capacity, long updatedAt) throws SQLException;

    void updateSharedWarehouseName(String sharedId, UUID ownerUuid, String name, long updatedAt) throws SQLException;

    void updateSharedWarehouseShowcase(String sharedId, UUID ownerUuid, boolean showcaseEnabled, long updatedAt) throws SQLException;

    void transferSharedWarehouse(String sharedId, UUID previousOwnerUuid, UUID newOwnerUuid, long updatedAt) throws SQLException;

    void upsertPendingTransfer(PendingTransfer transfer) throws SQLException;

    Optional<PendingTransfer> loadPendingTransfer(String sharedId) throws SQLException;

    List<PendingTransfer> loadPendingTransfers(UUID targetUuid) throws SQLException;

    void deletePendingTransfer(String sharedId) throws SQLException;

    void deleteSharedWarehouse(String sharedId, UUID ownerUuid) throws SQLException;

    default void deleteSharedWarehouse(String sharedId, UUID ownerUuid, UUID leaseToken, long leaseDurationMs) throws SQLException {
        throw new SQLException("Shared warehouse deletion requires lease support");
    }

    List<SharedWarehouseRecord> loadSharedWarehouses(UUID playerUuid) throws SQLException;

    /** 按共享仓库 ID 查询单条记录（含成员角色），避免加载全部列表。 */
    Optional<SharedWarehouseRecord> loadSharedWarehouse(UUID playerUuid, String sharedId) throws SQLException;

    List<SharedWarehouseRecord> loadSharedWarehousesByOwner(UUID ownerUuid) throws SQLException;

    List<SharedMemberRecord> loadSharedMembers(String sharedId) throws SQLException;

    int countOwnedSharedWarehouses(UUID ownerUuid) throws SQLException;

    int countSharedMembers(String sharedId) throws SQLException;

    void upsertSharedMember(String sharedId, UUID ownerUuid, UUID playerUuid, String role, int maxMembers, long updatedAt) throws SQLException;

    void removeSharedMember(String sharedId, UUID ownerUuid, UUID playerUuid) throws SQLException;

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
