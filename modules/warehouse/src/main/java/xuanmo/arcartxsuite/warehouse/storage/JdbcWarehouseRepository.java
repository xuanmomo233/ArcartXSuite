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
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * 仓库模块的 JDBC 存储实现。
 * <p>
 * 继承 {@link AbstractModuleRepository} 并实现 {@link WarehouseRepository}，
 * 负责个人仓库、槽位物品、银行余额、定期存款、共享仓库及其成员、待处理转让等全部数据的持久化。
 * 支持 MySQL 与 SQLite 两种方言，自动建表与增量迁移，多步写操作使用事务保证原子性。
 * 关联模块：warehouse。
 */
public final class JdbcWarehouseRepository extends AbstractModuleRepository implements WarehouseRepository {

    @Override
    public boolean applySlotChanges(List<SlotChange> changes) throws SQLException {
        return materialMutation(changes, null, null, null, null, 0);
    }

    @Override
    public boolean applySharedSlotChanges(List<SlotChange> changes, SharedActor actor) throws SQLException {
        return materialMutation(changes, null, null, actor, null, 0);
    }

    @Override
    public boolean stageInventoryTransfer(MaterialTransfer transfer, List<SlotChange> changes, SharedActor actor) throws SQLException {
        boolean withdrawal = INVENTORY_WITHDRAW_RESERVED.equals(transfer.state());
        if (!INVENTORY_TRANSFER.equals(transfer.adapter()) || changes.isEmpty()
            || (!withdrawal && !INVENTORY_DEPOSIT.equals(transfer.state()))) {
            return false;
        }
        return materialMutation(changes, transfer, null, actor, null, 0);
    }

    @Override
    public boolean settleInventoryTransfer(MaterialTransfer transfer, List<SlotChange> changes, SharedActor actor,
                                           boolean restored) throws SQLException {
        boolean validState = restored ? INVENTORY_WITHDRAW_RESERVED.equals(transfer.state())
            : INVENTORY_WITHDRAW.equals(transfer.state()) || INVENTORY_DEPOSIT.equals(transfer.state());
        if (!INVENTORY_TRANSFER.equals(transfer.adapter()) || changes.isEmpty()
            || !validState) return false;
        return materialMutation(changes, null, null, actor, null, 0, null, new InventorySettlement(transfer, restored));
    }

    private record InventorySettlement(MaterialTransfer transfer, boolean restored) { }

    @Override public boolean escrowMaterials(List<SlotChange> changes, MaterialTransfer transfer) throws SQLException {
        return materialMutation(changes, transfer, null, null, null, 0);
    }

    @Override public boolean restoreHeldMaterialTransfer(MaterialTransfer transfer, List<SlotChange> reverse) throws SQLException {
        return materialMutation(reverse, null, transfer, null, null, 0);
    }

    @Override public boolean commitGroundDeposit(List<SlotChange> changes, MaterialTransfer transfer, long storedAmount)
        throws SQLException {
        if (storedAmount <= 0 || changes.isEmpty()) return false;
        long total = 0;
        for (SlotChange change : changes) {
            if (!matchesGroundTarget(change.before(), transfer) || !matchesGroundTarget(change.after(), transfer)
                || change.after() == null) return false;
            long delta = change.after().amount() - (change.before() == null ? 0 : change.before().amount());
            if (delta <= 0) return false;
            total += delta;
        }
        if (total != storedAmount) return false;
        return materialMutation(changes, null, null, null, transfer, storedAmount);
    }

    private static boolean matchesGroundTarget(SlotItemRecord row, MaterialTransfer transfer) {
        return row == null || ("personal".equals(row.ownerType()) && transfer.ownerId().equals(row.ownerId())
            && transfer.warehouseId().equals(row.warehouseId()));
    }

    @Override
    public boolean commitRewardDeposit(List<SlotChange> changes, MaterialTransfer receipt) throws SQLException {
        if (!"reward-overflow-v1".equals(receipt.adapter())) return false;
        if ("REWARD_DECLINED".equals(receipt.state())) {
            return changes.isEmpty() && materialMutation(changes, receipt, null, null, null, 0);
        }
        if (!"REWARD_STORED".equals(receipt.state()) || changes.isEmpty()) return false;
        for (var change : changes) {
            if (change.after() == null || !matchesGroundTarget(change.before(), receipt)
                || !matchesGroundTarget(change.after(), receipt)
                || change.after().amount() <= (change.before() == null ? 0 : change.before().amount())) return false;
        }
        return materialMutation(changes, receipt, null, null, null, 0);
    }

    private boolean materialMutation(List<SlotChange> changes, MaterialTransfer create, MaterialTransfer restore,
                                     SharedActor actor, MaterialTransfer groundDeposit, long storedAmount) throws SQLException {
        return materialMutation(changes, create, restore, actor, groundDeposit, storedAmount, null);
    }

    private boolean materialMutation(List<SlotChange> changes, MaterialTransfer create, MaterialTransfer restore,
                                     SharedActor actor, MaterialTransfer groundDeposit, long storedAmount,
                                     GroundSource source) throws SQLException {
        return materialMutation(changes, create, restore, actor, groundDeposit, storedAmount, source, null);
    }

    private boolean materialMutation(List<SlotChange> changes, MaterialTransfer create, MaterialTransfer restore,
                                     SharedActor actor, MaterialTransfer groundDeposit, long storedAmount,
                                     GroundSource source, InventorySettlement inventorySettlement) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean stageInventory = create != null && INVENTORY_TRANSFER.equals(create.adapter());
                boolean stageDeposit = stageInventory && INVENTORY_DEPOSIT.equals(create.state());
                boolean restoreWithdrawal = inventorySettlement != null && inventorySettlement.restored();
                boolean completeWithdrawal = inventorySettlement != null
                    && INVENTORY_WITHDRAW.equals(inventorySettlement.transfer().state());
                if (stageInventory) {
                    if (!inventoryScopeExists(connection, create, changes, actor, true)
                        || inventoryTransferPending(connection, create)) {
                        connection.rollback();
                        return false;
                    }
                }
                if (inventorySettlement != null && !settleInventory(connection, inventorySettlement, changes, actor)) {
                    connection.rollback();
                    return false;
                }
                if (create != null && "REWARD_STORED".equals(create.state())) {
                    try (PreparedStatement owner = connection.prepareStatement(
                            "SELECT warehouse_id FROM warehouse_personal WHERE player_uuid=? AND warehouse_id=?"
                                + (isMysql() ? " FOR UPDATE" : ""))) {
                        owner.setString(1, create.ownerId());
                        owner.setString(2, create.warehouseId());
                        try (ResultSet row = owner.executeQuery()) {
                            if (!row.next()) { connection.rollback(); return false; }
                        }
                    }
                }
                if (groundDeposit != null) {
                    if ("ground-source-v1".equals(groundDeposit.adapter())) {
                        try (PreparedStatement claim = connection.prepareStatement(
                            "UPDATE warehouse_ground_sources SET state='COMMITTED' WHERE id=? AND owner_id=? AND stored_amount=? AND state='PREPARED'")) {
                            claim.setString(1, groundDeposit.id()); claim.setString(2, groundDeposit.ownerId());
                            claim.setLong(3, storedAmount);
                            if (claim.executeUpdate() != 1) { connection.rollback(); return false; }
                        }
                    }
                    try (PreparedStatement guard = connection.prepareStatement(
                        "UPDATE warehouse_material_transfers SET state='PICKUP_DONE',updated_at=?,note=?"
                            + " WHERE id=? AND owner_id=? AND state='PICKUP'")) {
                        guard.setLong(1, System.currentTimeMillis());
                        guard.setString(2, Long.toString(storedAmount));
                        guard.setString(3, groundDeposit.id());
                        guard.setString(4, groundDeposit.ownerId());
                        if (guard.executeUpdate() != 1) { connection.rollback(); return false; }
                    }
                    try (PreparedStatement warehouse = connection.prepareStatement(
                        "SELECT warehouse_id FROM warehouse_personal WHERE player_uuid=? AND warehouse_id=?"
                            + (isMysql() ? " FOR UPDATE" : ""))) {
                        warehouse.setString(1, groundDeposit.ownerId());
                        warehouse.setString(2, groundDeposit.warehouseId());
                        try (ResultSet owner = warehouse.executeQuery()) {
                            if (!owner.next()) { connection.rollback(); return false; }
                        }
                    }
                }
                if (restore != null) {
                    try (PreparedStatement scope = connection.prepareStatement("SELECT warehouse_id FROM warehouse_personal WHERE player_uuid=? AND warehouse_id=?")) {
                        scope.setString(1,restore.ownerId());scope.setString(2,restore.warehouseId());
                        try (ResultSet owner=scope.executeQuery()) { if(!owner.next()){connection.rollback();return false;} }
                    }
                    try (PreparedStatement guard = connection.prepareStatement("UPDATE warehouse_material_transfers SET state='RETURNED',updated_at=?,note='未执行交付，已原位回仓' WHERE id=? AND owner_id=? AND state='HELD'")) {
                        guard.setLong(1, System.currentTimeMillis()); guard.setString(2, restore.id()); guard.setString(3, restore.ownerId());
                        if (guard.executeUpdate() != 1) { connection.rollback(); return false; }
                    }
                }
                if (actor != null && inventorySettlement == null && !lockSharedMutation(connection, changes, actor)) {
                    connection.rollback();
                    return false;
                }
                for (SlotChange change : stageDeposit || completeWithdrawal ? List.<SlotChange>of() : changes) {
                    SlotItemRecord before = change.before();
                    SlotItemRecord after = change.after();
                    if (actor == null && ((before != null && "shared".equals(before.ownerType()))
                        || (after != null && "shared".equals(after.ownerType())))) {
                        connection.rollback();
                        return false;
                    }
                    if (before == null) {
                        try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO warehouse_slots (owner_type,owner_id,warehouse_id,slot,item_hash,category_id,display_name,material_id,search_text,pinyin,initials,item_data,item_json,amount,created_at,updated_at,lore_text) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                            bindSlot(statement, after);
                            try {
                                statement.executeUpdate();
                            } catch (SQLException conflict) {
                                if (inventorySettlement == null || conflict.getErrorCode() != (isMysql() ? 1062 : 19)) {
                                    throw conflict;
                                }
                                connection.rollback();
                                return false;
                            }
                        }
                    } else {
                        boolean delete = after == null;
                        try (PreparedStatement statement = connection.prepareStatement(delete
                            ? "DELETE FROM warehouse_slots WHERE owner_type=? AND owner_id=? AND warehouse_id=? AND slot=? AND item_hash=? AND amount=? AND created_at=?"
                            : "UPDATE warehouse_slots SET amount=?,updated_at=? WHERE owner_type=? AND owner_id=? AND warehouse_id=? AND slot=? AND item_hash=? AND amount=? AND created_at=?")) {
                            int i = 1;
                            if (!delete) { statement.setLong(i++, after.amount()); statement.setLong(i++, after.updatedAt()); }
                            statement.setString(i++, before.ownerType());
                            statement.setString(i++, before.ownerId());
                            statement.setString(i++, before.warehouseId());
                            statement.setInt(i++, before.slot());
                            statement.setString(i++, before.itemHash());
                            statement.setLong(i++, before.amount());
                            statement.setLong(i, before.createdAt());
                            if (statement.executeUpdate() != 1) {
                                connection.rollback();
                                return false;
                            }
                        }
                    }
                }
                if (create != null) {
                    if (source != null) {
                        try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO warehouse_ground_sources(id,owner_id,world_id,entity_id,chunk_x,chunk_z,original_amount,stored_amount,state) VALUES(?,?,?,?,?,?,?,?,'PREPARED')")) {
                            insert.setString(1,source.id()); insert.setString(2,source.owner());
                            insert.setString(3,source.world()); insert.setString(4,source.entity());
                            insert.setInt(5,source.chunkX()); insert.setInt(6,source.chunkZ());
                            insert.setInt(7,source.original()); insert.setInt(8,source.stored()); insert.executeUpdate();
                        }
                    }
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO warehouse_material_transfers (id,owner_id,warehouse_id,server_id,run_id,state,adapter,target_id,evidence,payload,created_at,updated_at,note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        insert.setString(1,create.id()); insert.setString(2,create.ownerId()); insert.setString(3,create.warehouseId());
                        insert.setString(4,create.serverId()); insert.setString(5,create.runId()); insert.setString(6,create.state());
                        insert.setString(7,create.adapter()); insert.setString(8,create.target()); insert.setString(9,create.evidence());
                        insert.setString(10,create.payload()); insert.setLong(11,create.createdAt()); insert.setLong(12,create.updatedAt()); insert.setString(13,create.note());
                        insert.executeUpdate();
                    }
                }
                if (actor != null && !stageInventory && !restoreWithdrawal
                    && (!completeWithdrawal || sharedWarehouseExists(connection, inventorySettlement.transfer().warehouseId()))) {
                    appendSharedActivity(connection, changes, actor);
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private boolean inventoryScopeExists(Connection connection, MaterialTransfer transfer, List<SlotChange> changes,
                                         SharedActor actor, boolean requireWarehouse) throws SQLException {
        if ("shared".equals(transfer.target())) {
            return actor != null && actor.id().toString().equals(transfer.ownerId()) && changes.stream().allMatch(change ->
                sharedSlotIn(change.before(), transfer.warehouseId()) && sharedSlotIn(change.after(), transfer.warehouseId()));
        }
        if (!"personal".equals(transfer.target()) || actor != null || changes.stream().anyMatch(change ->
            !matchesGroundTarget(change.before(), transfer) || !matchesGroundTarget(change.after(), transfer))) return false;
        if (!requireWarehouse) return true;
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT warehouse_id FROM warehouse_personal WHERE player_uuid=? AND warehouse_id=?"
                + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, transfer.ownerId());
            query.setString(2, transfer.warehouseId());
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private boolean sharedWarehouseExists(Connection connection, String warehouseId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT id FROM warehouse_shared WHERE id=?" + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, warehouseId);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private boolean inventoryTransferPending(Connection connection, MaterialTransfer transfer) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT id FROM warehouse_material_transfers WHERE owner_id=? AND server_id=? AND adapter=? "
                + "AND state IN ('MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW','REVIEW') LIMIT 1"
                + (isMysql() ? " FOR UPDATE" : ""))) {
            query.setString(1, transfer.ownerId());
            query.setString(2, transfer.serverId());
            query.setString(3, INVENTORY_TRANSFER);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private boolean settleInventory(Connection connection, InventorySettlement settlement, List<SlotChange> changes,
                                    SharedActor actor) throws SQLException {
        MaterialTransfer transfer = settlement.transfer();
        boolean completeWithdrawal = INVENTORY_WITHDRAW.equals(transfer.state()) && !settlement.restored();
        if (!inventoryScopeExists(connection, transfer, changes, actor, !completeWithdrawal)
            || (!completeWithdrawal && actor != null && !sharedWarehouseExists(connection, transfer.warehouseId()))) {
            return false;
        }
        try (PreparedStatement guard = connection.prepareStatement(
            "UPDATE warehouse_material_transfers SET state=?,updated_at=?,note=? "
                + "WHERE id=? AND owner_id=? AND server_id=? AND state=? AND adapter=?")) {
            guard.setString(1, settlement.restored() ? "RETURNED" : INVENTORY_DONE);
            guard.setLong(2, System.currentTimeMillis());
            guard.setString(3, settlement.restored() ? "背包未收到，已原位回仓" : "背包与仓库均已确认");
            guard.setString(4, transfer.id());
            guard.setString(5, transfer.ownerId());
            guard.setString(6, transfer.serverId());
            guard.setString(7, transfer.state());
            guard.setString(8, INVENTORY_TRANSFER);
            return guard.executeUpdate() == 1;
        }
    }

    private String databaseMillis() {
        return isMysql() ? "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)"
            : "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";
    }

    private boolean lockSharedMutation(Connection connection, List<SlotChange> changes, SharedActor actor)
        throws SQLException {
        if (changes.isEmpty()) return true;
        SlotItemRecord first = changes.get(0).before() == null ? changes.get(0).after() : changes.get(0).before();
        String id = first.ownerId();
        for (SlotChange change : changes) {
            if (!sharedSlotIn(change.before(), id) || !sharedSlotIn(change.after(), id)) return false;
        }
        // Acquire a write lock on the lease row before touching slots. A takeover cannot pass
        // the lease check until the slot changes and activity entries have committed.
        if (!renewSharedLease(connection, id, actor.id(), actor.leaseToken(), 60_000L)) return false;
        return hasSharedWriteRole(connection, id, actor.id(), true);
    }

    private static boolean sharedSlotIn(SlotItemRecord slot, String id) {
        return slot == null || ("shared".equals(slot.ownerType())
            && id.equals(slot.ownerId()) && id.equals(slot.warehouseId()));
    }

    private boolean hasSharedWriteRole(Connection connection, String id, UUID player, boolean lock) throws SQLException {
        String sql = """
            SELECT m.role FROM warehouse_shared_members m JOIN warehouse_shared s ON s.id = m.shared_id
            WHERE m.shared_id = ? AND m.player_uuid = ? AND m.role IN ('owner', 'member')
            """ + (lock && isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public boolean acquireSharedLease(String id, UUID player, UUID token, String name, String node, long durationMs)
        throws SQLException {
        String now = databaseMillis();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int acquired;
                try (PreparedStatement update = connection.prepareStatement("UPDATE warehouse_shared_leases"
                    + " SET actor_uuid=?,lease_token=?,actor_name=?,node_id=?,expires_at=" + now + "+?"
                    + " WHERE shared_id=? AND expires_at<=" + now)) {
                    update.setString(1, player.toString());
                    update.setString(2, token.toString());
                    update.setString(3, name);
                    update.setString(4, node);
                    update.setLong(5, durationMs);
                    update.setString(6, id);
                    acquired = update.executeUpdate();
                }
                if (acquired == 0) {
                    String insertSql = "INSERT INTO warehouse_shared_leases "
                        + "(shared_id,actor_uuid,lease_token,actor_name,node_id,expires_at) VALUES (?,?,?,?,?," + now + "+?)";
                    try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                        insert.setString(1, id);
                        insert.setString(2, player.toString());
                        insert.setString(3, token.toString());
                        insert.setString(4, name);
                        insert.setString(5, node);
                        insert.setLong(6, durationMs);
                        acquired = insert.executeUpdate();
                    } catch (SQLException duplicate) {
                        if (duplicate.getErrorCode() != (isMysql() ? 1062 : 19)) throw duplicate;
                    }
                }
                if (acquired != 1 || !hasSharedWriteRole(connection, id, player, true)) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    @Override
    public boolean renewSharedLease(String id, UUID player, UUID token, long durationMs) throws SQLException {
        try (Connection connection = getConnection()) {
            return renewSharedLease(connection, id, player, token, durationMs);
        }
    }

    private boolean renewSharedLease(Connection connection, String id, UUID player, UUID token, long durationMs)
        throws SQLException {
        String now = databaseMillis();
        try (PreparedStatement statement = connection.prepareStatement("UPDATE warehouse_shared_leases SET expires_at="
            + "CASE WHEN expires_at >= " + now + "+? THEN expires_at + 1 ELSE " + now + "+? END"
            + " WHERE shared_id=? AND actor_uuid=? AND lease_token=? AND expires_at>" + now
            + " AND EXISTS (SELECT 1 FROM warehouse_shared_members m JOIN warehouse_shared s ON s.id=m.shared_id"
            + " WHERE m.shared_id=warehouse_shared_leases.shared_id AND m.player_uuid=warehouse_shared_leases.actor_uuid"
            + " AND m.role IN ('owner','member'))")) {
            statement.setLong(1, durationMs);
            statement.setLong(2, durationMs);
            statement.setString(3, id);
            statement.setString(4, player.toString());
            statement.setString(5, token.toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void releaseSharedLease(String id, UUID player, UUID token) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_shared_leases WHERE shared_id=? AND actor_uuid=? AND lease_token=?")) {
            statement.setString(1, id);
            statement.setString(2, player.toString());
            statement.setString(3, token.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<SharedLease> sharedLease(String id) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT actor_uuid,lease_token,actor_name,node_id FROM warehouse_shared_leases WHERE shared_id=? AND expires_at>"
                     + databaseMillis())) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new SharedLease(UUID.fromString(result.getString(1)),
                    UUID.fromString(result.getString(2)), result.getString(3), result.getString(4))) : Optional.empty();
            }
        }
    }

    private void appendSharedActivity(Connection connection, List<SlotChange> changes, SharedActor actor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO warehouse_shared_activity (id,warehouse_id,actor_uuid,actor_name,action,item_name,material_id,amount,created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            long now = System.currentTimeMillis();
            for (SlotChange change : changes) {
                SlotItemRecord row = change.after() == null ? change.before() : change.after();
                if (!"shared".equals(row.ownerType())) throw new SQLException("记录对象不是共享仓库");
                long before = change.before() == null ? 0 : change.before().amount();
                long after = change.after() == null ? 0 : change.after().amount();
                long delta = after - before;
                if (delta == 0) continue;
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, row.warehouseId());
                statement.setString(3, actor.id().toString());
                statement.setString(4, actor.name());
                statement.setString(5, actor.discarded() ? "discard" : delta > 0 ? "deposit" : "withdraw");
                statement.setString(6, row.displayName());
                statement.setString(7, row.materialId());
                statement.setLong(8, Math.abs(delta));
                statement.setLong(9, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<SharedActivity> sharedActivity(String warehouseId, UUID owner, String member, String item,
                                               long since, int offset, int limit) throws SQLException {
        String sql = """
            SELECT a.actor_name, a.action, a.item_name, a.amount, a.created_at
            FROM warehouse_shared_activity a JOIN warehouse_shared s ON s.id = a.warehouse_id
            WHERE a.warehouse_id = ? AND s.owner_uuid = ? AND a.created_at >= ?
              AND (LOWER(a.actor_name) LIKE ? ESCAPE '!' OR a.actor_uuid LIKE ? ESCAPE '!')
              AND (LOWER(a.item_name) LIKE ? ESCAPE '!' OR LOWER(a.material_id) LIKE ? ESCAPE '!')
            ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?
            """;
        try (Connection connection = getConnection(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, warehouseId);
            query.setString(2, owner.toString());
            query.setLong(3, since);
            query.setString(4, activityFilter(member));
            query.setString(5, activityFilter(member));
            query.setString(6, activityFilter(item));
            query.setString(7, activityFilter(item));
            query.setInt(8, limit);
            query.setInt(9, offset);
            try (ResultSet rows = query.executeQuery()) {
                List<SharedActivity> result = new ArrayList<>();
                while (rows.next()) result.add(new SharedActivity(rows.getString(1), rows.getString(2),
                    rows.getString(3), rows.getLong(4), rows.getLong(5)));
                return List.copyOf(result);
            }
        }
    }

    private String activityFilter(String text) {
        return "%" + text.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @Override public boolean transitionMaterialTransfer(String id, String owner, String from, String to, String note) throws SQLException {
        try (Connection c=getConnection(); PreparedStatement q=c.prepareStatement("UPDATE warehouse_material_transfers SET state=?,updated_at=?,note=? WHERE id=? AND owner_id=? AND state=?")) {
            q.setString(1,to); q.setLong(2,System.currentTimeMillis()); q.setString(3,note); q.setString(4,id); q.setString(5,owner); q.setString(6,from);
            return q.executeUpdate()==1;
        }
    }

    @Override public boolean prepareGroundSource(MaterialTransfer transfer, GroundSource source) throws SQLException {
        return materialMutation(List.of(), transfer, null, null, null, 0, source);
    }

    @Override public GroundSource groundSource(String id) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT * FROM warehouse_ground_sources WHERE id=?")) {
            q.setString(1, id);
            try (ResultSet r = q.executeQuery()) {
                return r.next() ? new GroundSource(id, r.getString("owner_id"), r.getString("world_id"), r.getString("entity_id"),
                    r.getInt("chunk_x"), r.getInt("chunk_z"), r.getInt("original_amount"), r.getInt("stored_amount"), r.getString("state")) : null;
            }
        }
    }

    @Override public boolean finishGroundSource(GroundSource source, boolean committed) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement update = c.prepareStatement("UPDATE warehouse_ground_sources SET state=? WHERE id=? AND state=?");
                 PreparedStatement receipt = c.prepareStatement(
                     "UPDATE warehouse_material_transfers SET state=?,updated_at=?,note=? WHERE id=? AND owner_id=? AND state=?")) {
                update.setString(1, committed ? "SETTLED" : "RETURNED");
                update.setString(2, source.id());
                update.setString(3, committed ? "COMMITTED" : "PREPARED");
                if (update.executeUpdate() != 1) { c.rollback(); return false; }
                receipt.setString(1, committed ? "PICKUP_SETTLED" : "PICKUP_RETURN");
                receipt.setLong(2, System.currentTimeMillis());
                receipt.setString(3, committed ? source.stored() + " 件已入库，余量已确认" : "未入库，原掉落物已释放");
                receipt.setString(4, source.id()); receipt.setString(5, source.owner());
                receipt.setString(6, committed ? "PICKUP_DONE" : "PICKUP");
                if (receipt.executeUpdate() != 1) { c.rollback(); return false; }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    @Override
    public boolean supportsParallelGroundSettlement() {
        return isMysql();
    }

    private static MaterialTransfer readMaterialTransfer(ResultSet r) throws SQLException {
        return new MaterialTransfer(r.getString("id"),r.getString("owner_id"),r.getString("warehouse_id"),r.getString("server_id"),r.getString("run_id"),r.getString("state"),r.getString("adapter"),r.getString("target_id"),r.getString("evidence"),r.getString("payload"),r.getLong("created_at"),r.getLong("updated_at"),r.getString("note"));
    }

    @Override public java.util.Optional<MaterialTransfer> materialTransfer(String id, String owner) throws SQLException {
        try (Connection c=getConnection(); PreparedStatement q=c.prepareStatement("SELECT * FROM warehouse_material_transfers WHERE id=? AND owner_id=?")) {
            q.setString(1,id); q.setString(2,owner);
            try (ResultSet r=q.executeQuery()) { return r.next() ? java.util.Optional.of(readMaterialTransfer(r)) : java.util.Optional.empty(); }
        }
    }

    @Override public java.util.Optional<MaterialTransfer> pendingInventoryTransfer(UUID player, String serverId) throws SQLException {
        try (Connection connection = getConnection(); PreparedStatement query = connection.prepareStatement(
            "SELECT * FROM warehouse_material_transfers WHERE owner_id=? AND server_id=? AND adapter=? "
             + "AND state IN ('MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW') LIMIT 1")) {
            query.setString(1, player.toString());
            query.setString(2, serverId);
            query.setString(3, INVENTORY_TRANSFER);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? java.util.Optional.of(readMaterialTransfer(rows)) : java.util.Optional.empty();
            }
        }
    }

    @Override public List<MaterialTransfer> oldMaterialTransfers(String server, String run, String afterId, int limit) throws SQLException {
        try (Connection c=getConnection(); PreparedStatement q=c.prepareStatement(
                "SELECT * FROM warehouse_material_transfers WHERE server_id=? AND state IN ('HELD','APPLYING','APPLIED','MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW') "
                    + "AND (run_id<>? OR (state IN ('HELD','MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW') AND created_at<?)) AND id>? ORDER BY id LIMIT ?")) {
            q.setString(1,server); q.setString(2,run); q.setLong(3,System.currentTimeMillis()-30000L);
            q.setString(4,afterId); q.setInt(5,Math.min(100,Math.max(1,limit)));
            try (ResultSet r=q.executeQuery()) { List<MaterialTransfer> rows=new ArrayList<>(); while(r.next()) rows.add(readMaterialTransfer(r)); return rows; }
        }
    }

    @Override public List<MaterialTransfer> materialTransfers(String owner, int limit) throws SQLException {
        try (Connection c=getConnection(); PreparedStatement q=c.prepareStatement("SELECT * FROM warehouse_material_transfers WHERE owner_id=? AND state IN ('HELD','APPLYING','REVIEW','PICKUP','PICKUP_DONE','PICKUP_RETURN','MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW') ORDER BY created_at DESC LIMIT ?")) {
            q.setString(1,owner); q.setInt(2,Math.min(100,Math.max(1,limit)));
            try (ResultSet r=q.executeQuery()) { List<MaterialTransfer> rows=new ArrayList<>(); while(r.next()) rows.add(readMaterialTransfer(r)); return rows; }
        }
    }

    private static void bindSlot(PreparedStatement statement, SlotItemRecord item) throws SQLException {
        if (item == null || item.itemData() == null || item.itemData().isBlank() || item.amount() <= 0)
            throw new IllegalArgumentException("新槽位必须包含完整物品数据与正数量");
        statement.setString(1, item.ownerType()); statement.setString(2, item.ownerId());
        statement.setString(3, item.warehouseId()); statement.setInt(4, item.slot());
        statement.setString(5, item.itemHash()); statement.setString(6, item.categoryId());
        statement.setString(7, item.displayName()); statement.setString(8, item.materialId());
        statement.setString(9, item.searchText()); statement.setString(10, item.pinyin());
        statement.setString(11, item.initials()); statement.setString(12, item.itemData());
        statement.setString(13, item.itemJson()); statement.setLong(14, item.amount());
        statement.setLong(15, item.createdAt()); statement.setLong(16, item.updatedAt());
        statement.setString(17, item.loreText());
    }

    public JdbcWarehouseRepository(DataSource dataSource, StorageDescriptor descriptor,
                                   File dataFolder, Logger logger) {
        super("AXS-Warehouse", dataSource, descriptor, dataFolder, logger);
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
        if (!hasBankReceiptKind(conn)) {
            try (Statement statement = conn.createStatement()) {
                statement.execute("ALTER TABLE warehouse_bank_withdrawals ADD COLUMN receipt_kind VARCHAR(16) NOT NULL DEFAULT 'WITHDRAWAL'");
            } catch (SQLException failure) {
                if (!hasBankReceiptKind(conn)) throw failure;
            }
        }
    }

    private boolean hasBankReceiptKind(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null,
                "warehouse_bank_withdrawals", "%")) {
            while (columns.next()) {
                if ("warehouse_bank_withdrawals".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && "receipt_kind".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    /**
     * @return 包含玩家个人数据的表名列表，用于按玩家清理数据
     */
    @Override
    protected List<String> playerDataTables() {
        return allTables();
    }

    @Override
    protected int onPurgePlayerData(UUID playerUuid) throws SQLException {
        List<String> statements = List.of(
            "UPDATE warehouse_material_transfers SET state='REVIEW',note='玩家数据已清理，禁止自动补发' WHERE owner_id=? AND state IN ('HELD','APPLYING','APPLIED','MANUAL_DEPOSIT','MANUAL_RESERVED','MANUAL_WITHDRAW')",
            "DELETE FROM warehouse_slots WHERE owner_type='personal' AND owner_id=?",
            "DELETE FROM warehouse_slots WHERE owner_type='shared' AND owner_id IN (SELECT id FROM warehouse_shared WHERE owner_uuid=?)",
            "DELETE FROM warehouse_shared_members WHERE shared_id IN (SELECT id FROM warehouse_shared WHERE owner_uuid=?)",
            "DELETE FROM warehouse_shared_members WHERE player_uuid=?",
            "DELETE FROM warehouse_pending_transfers WHERE shared_id IN (SELECT id FROM warehouse_shared WHERE owner_uuid=?)",
            "DELETE FROM warehouse_pending_transfers WHERE from_owner_uuid=?",
            "DELETE FROM warehouse_pending_transfers WHERE to_uuid=?",
            "DELETE FROM warehouse_shared WHERE owner_uuid=?",
            "DELETE FROM warehouse_personal WHERE player_uuid=?",
            "DELETE FROM warehouse_bank_withdrawals WHERE player_uuid=?",
            "DELETE FROM warehouse_fixed_deposits WHERE player_uuid=?",
            "DELETE FROM warehouse_bank_balances WHERE player_uuid=?",
            "DELETE FROM warehouse_pins WHERE player_uuid=?",
            "DELETE FROM warehouse_player_settings WHERE player_uuid=?"
        );
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int total = 0;
                for (String sql : statements) {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, playerUuid.toString());
                        total += statement.executeUpdate();
                    }
                }
                connection.commit();
                return total;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
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
            "warehouse_bank_withdrawals",
            "warehouse_fixed_deposits",
            "warehouse_shared",
            "warehouse_shared_members",
            "warehouse_pending_transfers",
            "warehouse_player_settings",
            "warehouse_pins",
            "warehouse_material_transfers",
            "warehouse_ground_sources",
            "warehouse_shared_activity"
        );
    }

    @Override
    public java.util.Set<String> loadPins(UUID playerUuid, String warehouseId) throws SQLException {
        java.util.Set<String> pins = new java.util.HashSet<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT item_hash FROM warehouse_pins WHERE player_uuid=? AND warehouse_id=?")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, warehouseId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) pins.add(result.getString(1));
            }
        }
        return pins;
    }

    @Override
    public void setPin(UUID playerUuid, String warehouseId, String itemHash, boolean pinned) throws SQLException {
        List<String> key = List.of("player_uuid", "warehouse_id", "item_hash");
        String sql = pinned
            ? upsertSql("warehouse_pins", key, List.of("updated_at"))
            : "DELETE FROM warehouse_pins WHERE player_uuid=? AND warehouse_id=? AND item_hash=?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, warehouseId);
            statement.setString(3, itemHash);
            if (pinned) statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
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
                 "SELECT warehouse_id, level, custom_name, showcase_enabled, extra_slots, updated_at FROM warehouse_personal WHERE player_uuid = ? ORDER BY warehouse_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<WarehouseRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new WarehouseRecord(playerUuid, resultSet.getString("warehouse_id"), resultSet.getInt("level"), resultSet.getString("custom_name"), resultSet.getBoolean("showcase_enabled"), resultSet.getLong("updated_at"), resultSet.getLong("extra_slots")));
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

    @Override
    public void addPersonalExtraSlots(UUID playerUuid, String warehouseId, long delta, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_personal SET extra_slots = CASE WHEN extra_slots + ? < 0 THEN 0 ELSE extra_slots + ? END, updated_at = ? WHERE player_uuid = ? AND warehouse_id = ?"
             )) {
            statement.setLong(1, delta);
            statement.setLong(2, delta);
            statement.setLong(3, updatedAt);
            statement.setString(4, playerUuid.toString());
            statement.setString(5, warehouseId);
            statement.executeUpdate();
        }
    }

    @Override
    public void ensureDefaultPersonalWarehouse(UUID playerUuid, String warehouseId, long now) throws SQLException {
        String sql = "INSERT INTO warehouse_personal (player_uuid,warehouse_id,level,custom_name,updated_at) VALUES (?,?,1,'',?)"
            + (isMysql() ? " ON DUPLICATE KEY UPDATE warehouse_id = warehouse_id"
                : " ON CONFLICT(player_uuid,warehouse_id) DO NOTHING");
        try (Connection connection = getConnection(); PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, playerUuid.toString());
            insert.setString(2, warehouseId);
            insert.setLong(3, now);
            insert.executeUpdate();
        }
    }

    @Override
    public void setPersonalExtraSlots(UUID playerUuid, String warehouseId, long extraSlots, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_personal SET extra_slots = ?, updated_at = ? WHERE player_uuid = ? AND warehouse_id = ?"
             )) {
            statement.setLong(1, Math.max(0L, extraSlots));
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, warehouseId);
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

    @Override
    public List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId,
                                          List<Integer> slots) throws SQLException {
        if (slots.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(slots.size(), "?"));
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM warehouse_slots WHERE owner_type=? AND owner_id=? AND warehouse_id=? AND slot IN ("
                     + placeholders + ")")) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            for (int i = 0; i < slots.size(); i++) statement.setInt(i + 4, slots.get(i));
            try (ResultSet result = statement.executeQuery()) {
                List<SlotItemRecord> records = new ArrayList<>();
                while (result.next()) records.add(slot(result));
                return records;
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
     * 加载槽位摘要（不含 item_data 序列化大字段），用于列表展示/搜索/合并扫描等
     * 只需要元数据与展示字段的场景。返回记录的 {@code itemData} 为空串。
     */
    @Override
    public List<SlotItemRecord> loadSlotSummaries(String ownerType, String ownerId, String warehouseId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT owner_type, owner_id, warehouse_id, slot, item_hash, category_id, display_name, material_id, "
                     + "search_text, pinyin, initials, item_json, amount, created_at, updated_at, lore_text "
                     + "FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? ORDER BY slot ASC"
             )) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SlotItemRecord> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(slotSummary(resultSet));
                }
                return result;
            }
        }
    }

    /**
     * 插入或更新单个槽位物品记录。
     *
     * @param item 槽位物品记录，{@code itemData} 必须非空（防止摘要记录误写清空物品数据）
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void upsertSlot(SlotItemRecord item) throws SQLException {
        if (item.itemData() == null || item.itemData().isBlank()) {
            throw new IllegalArgumentException("upsertSlot 拒绝写入空 itemData（疑似摘要记录误写）: "
                + item.ownerType() + '/' + item.ownerId() + '/' + item.warehouseId() + '#' + item.slot());
        }
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql(
                 "warehouse_slots",
                 List.of("owner_type", "owner_id", "warehouse_id", "slot"),
                 List.of("item_hash", "category_id", "display_name", "material_id", "search_text", "pinyin", "initials", "item_data", "item_json", "amount", "created_at", "updated_at", "lore_text")
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
            statement.setString(17, item.loreText());
            statement.executeUpdate();
        }
    }

    @Override
    public void updateSlotAmount(String ownerType, String ownerId, String warehouseId, int slot, long amount, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_slots SET amount = ?, updated_at = ? WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND slot = ?"
             )) {
            statement.setLong(1, amount);
            statement.setLong(2, updatedAt);
            statement.setString(3, ownerType);
            statement.setString(4, ownerId);
            statement.setString(5, warehouseId);
            statement.setInt(6, slot);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateSlotCategory(String ownerType, String ownerId, String warehouseId, int slot, String categoryId, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_slots SET category_id = ?, updated_at = ? WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND slot = ?"
             )) {
            statement.setString(1, categoryId);
            statement.setLong(2, updatedAt);
            statement.setString(3, ownerType);
            statement.setString(4, ownerId);
            statement.setString(5, warehouseId);
            statement.setInt(6, slot);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateSlotLore(String ownerType, String ownerId, String warehouseId, int slot, String loreText, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_slots SET lore_text = ? WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ? AND slot = ?"
             )) {
            statement.setString(1, loreText == null ? "" : loreText);
            statement.setString(2, ownerType);
            statement.setString(3, ownerId);
            statement.setString(4, warehouseId);
            statement.setInt(5, slot);
            statement.executeUpdate();
        }
    }

    @Override
    public long countSlots(String ownerType, String ownerId, String warehouseId) throws SQLException {
        return aggregateSlots("COUNT(*)", ownerType, ownerId, warehouseId, null);
    }

    @Override
    public long sumAmount(String ownerType, String ownerId, String warehouseId) throws SQLException {
        return aggregateSlots("COALESCE(SUM(amount), 0)", ownerType, ownerId, warehouseId, null);
    }

    @Override
    public long sumAmountByCategory(String ownerType, String ownerId, String warehouseId, String categoryId) throws SQLException {
        return aggregateSlots("COALESCE(SUM(amount), 0)", ownerType, ownerId, warehouseId, categoryId);
    }

    private long aggregateSlots(String aggregate, String ownerType, String ownerId, String warehouseId, String categoryId) throws SQLException {
        String sql = "SELECT " + aggregate + " FROM warehouse_slots WHERE owner_type = ? AND owner_id = ? AND warehouse_id = ?"
            + (categoryId == null ? "" : " AND category_id = ?");
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, warehouseId);
            if (categoryId != null) {
                statement.setString(4, categoryId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
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
        if (!isMysql()) {
            // SQLite 无 DECIMAL：balance 是文本列，CAST REAL 浮点运算会丢精度。
            // 改为读-改-CAS 写循环：UPDATE ... WHERE balance=旧文本，并发写冲突重试
            creditBankBalanceSqlite(playerUuid, currencyId, amount, updatedAt);
            return;
        }
        String sql = """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    balance = CAST(balance AS DECIMAL(38,8)) + CAST(VALUES(balance) AS DECIMAL(38,8)),
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

    /** 余额 CAS 冲突重试上限 */
    private static final int BALANCE_CAS_MAX_RETRIES = 5;

    /** SQLite 分支：BigDecimal 精确读改写 + 文本级 CAS 防并发写冲突。 */
    private void creditBankBalanceSqlite(UUID playerUuid, String currencyId, BigDecimal amount, long updatedAt) throws SQLException {
        try (Connection connection = getConnection()) {
            for (int attempt = 0; attempt < BALANCE_CAS_MAX_RETRIES; attempt++) {
                String currentText = selectBankBalanceText(connection, playerUuid, currencyId);
                if (currentText == null) {
                    try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at) "
                            + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, currency_id) DO NOTHING")) {
                        insert.setString(1, playerUuid.toString());
                        insert.setString(2, currencyId);
                        insert.setString(3, amount.toPlainString());
                        insert.setLong(4, updatedAt);
                        if (insert.executeUpdate() > 0) {
                            return;
                        }
                        continue; // 并发插入冲突，重试
                    }
                }
                BigDecimal updated = decimal(currentText).add(amount);
                if (updateBankBalanceCas(connection, playerUuid, currencyId, currentText, updated, updatedAt)) {
                    return;
                }
            }
        }
        throw new SQLException("creditBankBalance CAS 重试耗尽: " + playerUuid + " " + currencyId);
    }

    /** 读取余额文本（无行返回 null）。 */
    private String selectBankBalanceText(Connection connection, UUID playerUuid, String currencyId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
            "SELECT balance FROM warehouse_bank_balances WHERE player_uuid = ? AND currency_id = ?")) {
            select.setString(1, playerUuid.toString());
            select.setString(2, currencyId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString("balance") : null;
            }
        }
    }

    /** CAS 更新余额：仅当当前值仍等于 expectedText 时写入。 */
    private boolean updateBankBalanceCas(Connection connection, UUID playerUuid, String currencyId,
                                         String expectedText, BigDecimal newValue, long updatedAt) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE warehouse_bank_balances SET balance = ?, updated_at = ? "
                + "WHERE player_uuid = ? AND currency_id = ? AND balance = ?")) {
            update.setString(1, newValue.toPlainString());
            update.setLong(2, updatedAt);
            update.setString(3, playerUuid.toString());
            update.setString(4, currencyId);
            update.setString(5, expectedText);
            return update.executeUpdate() > 0;
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
    private boolean debitBankBalanceOnConnection(Connection connection, UUID playerUuid, String currencyId,
                                                  BigDecimal amount, long updatedAt) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (!isMysql()) {
            // The caller has already acquired SQLite's write lock by inserting the corresponding record.
            String currentText = selectBankBalanceText(connection, playerUuid, currencyId);
            if (currentText == null) return false;
            BigDecimal current = decimal(currentText);
            if (current.compareTo(amount) < 0) return false;
            if (!updateBankBalanceCas(connection, playerUuid, currencyId, currentText, current.subtract(amount), updatedAt)) {
                throw new SQLException("银行余额在交易中发生变化");
            }
            return true;
        }
        String sql = """
                UPDATE warehouse_bank_balances
                SET balance = CAST(balance AS DECIMAL(38,8)) - CAST(? AS DECIMAL(38,8)), updated_at = ?
                WHERE player_uuid = ? AND currency_id = ? AND CAST(balance AS DECIMAL(38,8)) >= CAST(? AS DECIMAL(38,8))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, amount.toPlainString());
            statement.setLong(2, updatedAt);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, currencyId);
            statement.setString(5, amount.toPlainString());
            return statement.executeUpdate() > 0;
        }
    }

    @FunctionalInterface
    private interface BankMutation {
        boolean apply(Connection connection) throws SQLException;
    }

    private boolean bankTransaction(BankMutation mutation) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean changed = mutation.apply(connection);
                if (changed) connection.commit();
                else connection.rollback();
                return changed;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    @Override
    public boolean beginBankWithdrawal(BankTransfer withdrawal) throws SQLException {
        return bankTransaction(connection -> {
            insertBankReceipt(connection, withdrawal, BankReceiptKind.WITHDRAWAL);
            return debitBankBalanceOnConnection(connection, withdrawal.playerUuid(), withdrawal.currencyId(),
                withdrawal.amount(), withdrawal.createdAt());
        });
    }

    @Override
    public void finishBankWithdrawal(BankTransfer withdrawal, boolean credited) throws SQLException {
        if (!bankTransaction(connection -> {
            if (!deleteBankReceipt(connection, withdrawal, BankReceiptKind.WITHDRAWAL)) return false;
            if (!credited) creditBankBalanceOnConnection(connection, withdrawal.playerUuid(), withdrawal.currencyId(),
                withdrawal.amount(), System.currentTimeMillis());
            return true;
        })) throw new SQLException("提现凭证已变化: " + withdrawal.id());
    }

    private enum BankReceiptKind { WITHDRAWAL, DEPOSIT, DEPOSIT_REFUND }

    private static void insertBankReceipt(Connection connection, BankTransfer transfer, BankReceiptKind kind) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO warehouse_bank_withdrawals (id, player_uuid, currency_id, amount, receipt_kind, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            bindBankReceipt(statement, transfer, kind);
            statement.setLong(6, transfer.createdAt());
            statement.executeUpdate();
        }
    }

    private static void bindBankReceipt(PreparedStatement statement, BankTransfer transfer, BankReceiptKind kind) throws SQLException {
        statement.setString(1, transfer.id());
        statement.setString(2, transfer.playerUuid().toString());
        statement.setString(3, transfer.currencyId());
        statement.setString(4, transfer.amount().toPlainString());
        statement.setString(5, kind.name());
    }

    private static boolean deleteBankReceipt(Connection connection, BankTransfer transfer, BankReceiptKind kind) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM warehouse_bank_withdrawals
                WHERE id=? AND player_uuid=? AND currency_id=? AND amount=? AND receipt_kind=?
                """)) {
            bindBankReceipt(statement, transfer, kind);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void beginBankDeposit(BankTransfer deposit) throws SQLException {
        try (Connection connection = getConnection()) {
            insertBankReceipt(connection, deposit, BankReceiptKind.DEPOSIT);
        }
    }

    @Override
    public void creditBankDeposit(BankTransfer deposit) throws SQLException {
        if (!bankTransaction(connection -> {
            if (!deleteBankReceipt(connection, deposit, BankReceiptKind.DEPOSIT)) return false;
            creditBankBalanceOnConnection(connection, deposit.playerUuid(), deposit.currencyId(),
                deposit.amount(), System.currentTimeMillis());
            return true;
        })) throw new SQLException("存款凭证已变化: " + deposit.id());
    }

    @Override
    public boolean reserveBankDepositRefund(BankTransfer deposit) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE warehouse_bank_withdrawals SET receipt_kind='DEPOSIT_REFUND'
                 WHERE id=? AND player_uuid=? AND currency_id=? AND amount=? AND receipt_kind=?
                 """)) {
            bindBankReceipt(statement, deposit, BankReceiptKind.DEPOSIT);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void closeBankDeposit(BankTransfer deposit, boolean refunded) throws SQLException {
        try (Connection connection = getConnection()) {
            if (!deleteBankReceipt(connection, deposit, refunded ? BankReceiptKind.DEPOSIT_REFUND : BankReceiptKind.DEPOSIT)) {
                throw new SQLException("存款凭证已变化: " + deposit.id());
            }
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
        // SQLite 不支持 FOR UPDATE，且单连接池不能持有连接再进入备用方法申请第二条连接。
        if (!isMysql()) return claimFixedDepositAtomicWithoutForUpdate(depositId, playerUuid, now);
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
                    throw forUpdateUnsupported;
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
        if (!isMysql()) {
            // SQLite：事务内读-改-CAS 写；CAS 失败抛异常由外层事务回滚
            String currentText = selectBankBalanceText(connection, playerUuid, currencyId);
            if (currentText == null) {
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, currency_id) DO NOTHING")) {
                    insert.setString(1, playerUuid.toString());
                    insert.setString(2, currencyId);
                    insert.setString(3, amount.toPlainString());
                    insert.setLong(4, updatedAt);
                    if (insert.executeUpdate() > 0) {
                        return;
                    }
                }
                // 并发插入冲突：重读后再 CAS
                currentText = selectBankBalanceText(connection, playerUuid, currencyId);
                if (currentText == null) {
                    throw new SQLException("creditBankBalanceOnConnection 行状态异常: " + playerUuid + " " + currencyId);
                }
            }
            BigDecimal updated = decimal(currentText).add(amount);
            if (!updateBankBalanceCas(connection, playerUuid, currencyId, currentText, updated, updatedAt)) {
                throw new SQLException("creditBankBalanceOnConnection CAS 冲突: " + playerUuid + " " + currencyId);
            }
            return;
        }
        String sql = """
                INSERT INTO warehouse_bank_balances (player_uuid, currency_id, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    balance = CAST(balance AS DECIMAL(38,8)) + CAST(VALUES(balance) AS DECIMAL(38,8)),
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
    public boolean purchaseFixedDeposit(FixedDepositRecord deposit) throws SQLException {
        return bankTransaction(connection -> {
            insertFixedDeposit(connection, deposit);
            return debitBankBalanceOnConnection(connection, deposit.playerUuid(), deposit.currencyId(),
                deposit.principal(), deposit.createdAt());
        });
    }

    private void insertFixedDeposit(Connection connection, FixedDepositRecord deposit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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
     * 删除已领取的定期存款记录，WHERE 条件强制 claimed = true，防止误删未领取资产。
     *
     * @param depositId  存款 ID
     * @param playerUuid 玩家 UUID
     * @return {@code true} 表示删除成功；记录不存在、不属于该玩家或未领取时返回 {@code false}
     * @throws SQLException 删除失败时抛出
     */
    @Override
    public boolean deleteFixedDeposit(String depositId, UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_fixed_deposits WHERE id = ? AND player_uuid = ? AND claimed = ?"
             )) {
            statement.setString(1, depositId);
            statement.setString(2, playerUuid.toString());
            statement.setBoolean(3, true);
            return statement.executeUpdate() > 0;
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
    public void updateSharedWarehouseLevel(String sharedId, UUID ownerUuid, int expectedLevel, int level, long capacity, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET level = ?, capacity = ?, updated_at = ? WHERE id = ? AND owner_uuid = ? AND level = ?"
             )) {
            statement.setInt(1, level);
            statement.setLong(2, capacity);
            statement.setLong(3, updatedAt);
            statement.setString(4, sharedId);
            statement.setString(5, ownerUuid.toString());
            statement.setInt(6, expectedLevel);
            if (statement.executeUpdate() != 1) throw new SQLException("共享仓库所有权或等级已变化");
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
    public void updateSharedWarehouseName(String sharedId, UUID ownerUuid, String name, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET name = ?, updated_at = ? WHERE id = ? AND owner_uuid = ?"
             )) {
            statement.setString(1, name);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.setString(4, ownerUuid.toString());
            if (statement.executeUpdate() != 1) throw new SQLException("共享仓库所有权已变化");
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
    public void updateSharedWarehouseShowcase(String sharedId, UUID ownerUuid, boolean showcaseEnabled, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_shared SET showcase_enabled = ?, updated_at = ? WHERE id = ? AND owner_uuid = ?"
             )) {
            statement.setBoolean(1, showcaseEnabled);
            statement.setLong(2, updatedAt);
            statement.setString(3, sharedId);
            statement.setString(4, ownerUuid.toString());
            if (statement.executeUpdate() != 1) throw new SQLException("共享仓库所有权已变化");
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
                     "UPDATE warehouse_shared_members SET role = ?, updated_at = ? WHERE shared_id = ? AND player_uuid = ? AND role = 'owner'"
                 );
                 PreparedStatement newOwner = connection.prepareStatement(
                     "UPDATE warehouse_shared_members SET role = ?, updated_at = ? WHERE shared_id = ? AND player_uuid = ? AND role = 'member'"
                 )) {
                shared.setString(1, newOwnerUuid.toString());
                shared.setLong(2, updatedAt);
                shared.setString(3, sharedId);
                shared.setString(4, previousOwnerUuid.toString());
                if (shared.executeUpdate() != 1) throw new SQLException("共享仓库所有权已变化，转让已取消");

                oldOwner.setString(1, "member");
                oldOwner.setLong(2, updatedAt);
                oldOwner.setString(3, sharedId);
                oldOwner.setString(4, previousOwnerUuid.toString());
                if (oldOwner.executeUpdate() != 1) throw new SQLException("原所有者成员记录已变化，转让已取消");

                newOwner.setString(1, "owner");
                newOwner.setLong(2, updatedAt);
                newOwner.setString(3, sharedId);
                newOwner.setString(4, newOwnerUuid.toString());
                if (newOwner.executeUpdate() != 1) throw new SQLException("接收者不再是成员，转让已取消");
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
    public void deleteSharedWarehouse(String sharedId, UUID ownerUuid) throws SQLException {
        deleteSharedWarehouse(sharedId, ownerUuid, null, 0);
    }

    @Override
    public void deleteSharedWarehouse(String sharedId, UUID ownerUuid, UUID leaseToken, long leaseDurationMs) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement slots = connection.prepareStatement("DELETE FROM warehouse_slots WHERE owner_type = 'shared' AND owner_id = ?");
                 PreparedStatement members = connection.prepareStatement("DELETE FROM warehouse_shared_members WHERE shared_id = ?");
                 PreparedStatement pending = connection.prepareStatement("DELETE FROM warehouse_pending_transfers WHERE shared_id = ?");
                 PreparedStatement activity = connection.prepareStatement("DELETE FROM warehouse_shared_activity WHERE warehouse_id = ?");
                 PreparedStatement shared = connection.prepareStatement("DELETE FROM warehouse_shared WHERE id = ? AND owner_uuid = ?")) {
                if (leaseToken != null && !renewSharedLease(connection, sharedId, ownerUuid, leaseToken, leaseDurationMs)) {
                    throw new SQLException("共享仓库租约已变化，删除已取消");
                }
                slots.setString(1, sharedId);
                slots.executeUpdate();
                members.setString(1, sharedId);
                members.executeUpdate();
                pending.setString(1, sharedId);
                pending.executeUpdate();
                shared.setString(1, sharedId);
                shared.setString(2, ownerUuid.toString());
                if (shared.executeUpdate() != 1) throw new SQLException("共享仓库所有权已变化，删除已取消");
                activity.setString(1, sharedId);
                activity.executeUpdate();
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
    public void upsertSharedMember(String sharedId, UUID ownerUuid, UUID playerUuid, String role, int maxMembers, long updatedAt) throws SQLException {
        if (ownerUuid.equals(playerUuid) || !("member".equals(role) || "viewer".equals(role))) throw new SQLException("不可修改所有者角色");
        withSharedOwner(sharedId, ownerUuid, connection -> {
            try (PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN player_uuid=? THEN 1 ELSE 0 END) AS existing FROM warehouse_shared_members WHERE shared_id=?")) {
                count.setString(1, playerUuid.toString()); count.setString(2, sharedId);
                try (ResultSet rows = count.executeQuery()) {
                    if (rows.next() && rows.getInt("existing") == 0 && rows.getInt("total") >= maxMembers)
                        throw new SQLException("共享仓库成员数量已达上限");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(upsertSql("warehouse_shared_members", List.of("shared_id", "player_uuid"), List.of("role", "updated_at")))) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
            }
        });
    }

    /**
     * 移除共享仓库成员（owner 角色不可移除）。
     *
     * @param sharedId   共享仓库 ID
     * @param playerUuid 玩家 UUID
     * @throws SQLException 删除失败时抛出
     */
    @Override
    public void removeSharedMember(String sharedId, UUID ownerUuid, UUID playerUuid) throws SQLException {
        withSharedOwner(sharedId, ownerUuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM warehouse_shared_members WHERE shared_id = ? AND player_uuid = ? AND role <> 'owner'"
             )) {
            statement.setString(1, sharedId);
            statement.setString(2, playerUuid.toString());
            if (statement.executeUpdate() != 1) throw new SQLException("目标不再是可移除的成员");
            }
        });
    }

    @FunctionalInterface
    private interface SharedMutation { void apply(Connection connection) throws SQLException; }

    private void withSharedOwner(String sharedId, UUID ownerUuid, SharedMutation mutation) throws SQLException {
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String sql = "SELECT owner_uuid FROM warehouse_shared WHERE id=?" + (isMysql() ? " FOR UPDATE" : "");
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, sharedId);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next() || !ownerUuid.toString().equals(rows.getString(1))) throw new SQLException("共享仓库所有权已变化");
                    }
                }
                mutation.apply(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public java.util.Optional<WarehouseRepository.PlayerSettingsRecord> loadPlayerSettings(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT auto_pickup, auto_mythic, auto_notify, updated_at FROM warehouse_player_settings WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return java.util.Optional.of(new WarehouseRepository.PlayerSettingsRecord(
                        playerUuid,
                        resultSet.getBoolean("auto_pickup"),
                        resultSet.getBoolean("auto_mythic"),
                        resultSet.getBoolean("auto_notify"),
                        resultSet.getLong("updated_at")
                    ));
                }
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public void savePlayerSettings(UUID playerUuid, boolean autoPickup, boolean autoMythic, boolean autoNotify, long updatedAt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 upsertSql("warehouse_player_settings", List.of("player_uuid"), List.of("auto_pickup", "auto_mythic", "auto_notify", "updated_at"))
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setBoolean(2, autoPickup);
            statement.setBoolean(3, autoMythic);
            statement.setBoolean(4, autoNotify);
            statement.setLong(5, updatedAt);
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE warehouse_personal ADD COLUMN extra_slots BIGINT NOT NULL DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists.
        }
        try (Statement statement = connection.createStatement()) {
            // 展示用 lore 冗余列：旧行保持 NULL，读取时惰性反序列化回填
            statement.execute("ALTER TABLE warehouse_slots ADD COLUMN lore_text TEXT NULL");
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_shared_leases (
                    shared_id VARCHAR(64) PRIMARY KEY, actor_uuid VARCHAR(36) NOT NULL,
                    lease_token VARCHAR(36) NOT NULL, actor_name VARCHAR(64) NOT NULL,
                    node_id VARCHAR(128) NOT NULL, expires_at BIGINT NOT NULL
                )
                """ + (isMysql() ? " ENGINE=InnoDB" : ""));
            statement.execute("CREATE TABLE IF NOT EXISTS warehouse_material_transfers (id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, warehouse_id VARCHAR(64) NOT NULL, server_id VARCHAR(36) NOT NULL, run_id VARCHAR(36) NOT NULL, state VARCHAR(16) NOT NULL, adapter VARCHAR(64) NOT NULL, target_id VARCHAR(255) NOT NULL, evidence "
                + (isMysql() ? "LONGTEXT" : "TEXT") + " NOT NULL, payload " + (isMysql() ? "LONGTEXT" : "TEXT") + " NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, note VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS warehouse_ground_sources (id VARCHAR(36) PRIMARY KEY,"
                + "owner_id VARCHAR(36) NOT NULL, world_id VARCHAR(36) NOT NULL, entity_id VARCHAR(36) NOT NULL,"
                + "chunk_x INT NOT NULL, chunk_z INT NOT NULL, original_amount INT NOT NULL, stored_amount INT NOT NULL, state VARCHAR(16) NOT NULL)"
                + (isMysql() ? " ENGINE=InnoDB" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_shared_activity (
                    id VARCHAR(36) PRIMARY KEY, warehouse_id VARCHAR(64) NOT NULL,
                    actor_uuid VARCHAR(36) NOT NULL, actor_name VARCHAR(64) NOT NULL,
                    action VARCHAR(12) NOT NULL, item_name TEXT NOT NULL, material_id VARCHAR(128) NOT NULL,
                    amount BIGINT NOT NULL, created_at BIGINT NOT NULL
                )
                """);
            for (String index : List.of("CREATE INDEX warehouse_material_owner_idx ON warehouse_material_transfers(owner_id,state,created_at)", "CREATE INDEX warehouse_material_recovery_idx ON warehouse_material_transfers(server_id,state,created_at)",
                "CREATE INDEX warehouse_shared_activity_order_idx ON warehouse_shared_activity(warehouse_id,created_at,id)")) {
                try { statement.execute(index); }
                catch (SQLException exception) {
                    String message = String.valueOf(exception.getMessage()).toLowerCase(java.util.Locale.ROOT);
                    if (exception.getErrorCode() != 1061 && !message.contains("already exists")) throw exception;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_pins (
                    player_uuid VARCHAR(36) NOT NULL,
                    warehouse_id VARCHAR(64) NOT NULL,
                    item_hash VARCHAR(64) NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, warehouse_id, item_hash)
                )
                """);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_personal (
                    player_uuid VARCHAR(36) NOT NULL,
                    warehouse_id VARCHAR(64) NOT NULL,
                    level INTEGER NOT NULL,
                    custom_name VARCHAR(64) NOT NULL,
                    showcase_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    extra_slots BIGINT NOT NULL DEFAULT 0,
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
                    lore_text TEXT NULL,
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
                CREATE TABLE IF NOT EXISTS warehouse_bank_withdrawals (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    currency_id VARCHAR(64) NOT NULL,
                    amount VARCHAR(64) NOT NULL,
                    receipt_kind VARCHAR(16) NOT NULL DEFAULT 'WITHDRAWAL',
                    created_at BIGINT NOT NULL
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
            statement.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_player_settings (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    auto_pickup BOOLEAN NOT NULL DEFAULT FALSE,
                    auto_mythic BOOLEAN NOT NULL DEFAULT FALSE,
                    auto_notify BOOLEAN NOT NULL DEFAULT FALSE,
                    updated_at BIGINT NOT NULL
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
            resultSet.getLong("updated_at"),
            resultSet.getString("lore_text")
        );
    }

    /** 摘要记录映射：itemData 置空串（严禁回传 upsertSlot）。 */
    private SlotItemRecord slotSummary(ResultSet resultSet) throws SQLException {
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
            "",
            resultSet.getString("item_json"),
            resultSet.getLong("amount"),
            resultSet.getLong("created_at"),
            resultSet.getLong("updated_at"),
            resultSet.getString("lore_text")
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
