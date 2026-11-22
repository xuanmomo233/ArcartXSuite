package xuanmo.arcartxsuite.warehouse.storage;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseAtomicStorageTest {
    @TempDir Path temp;
    JdbcWarehouseRepository repository;
    UUID player = UUID.randomUUID();

    @BeforeEach void setup() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + temp.resolve("warehouse.db"));
        AtomicInteger connections = new AtomicInteger();
        DataSource singleConnection = mock(DataSource.class);
        when(singleConnection.getConnection()).thenAnswer(invocation -> {
            assertEquals(1, connections.incrementAndGet(), "SQLite 单连接路径不得嵌套申请连接");
            Connection connection = sqlite.getConnection();
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("close")) connections.decrementAndGet();
                try { return method.invoke(connection, args); }
                catch (java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
            });
        });
        repository = new JdbcWarehouseRepository(singleConnection, StorageDescriptor.sqlite("warehouse.db"), temp.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
    }

    SlotItemRecord slot(int index, long amount) {
        return slot(index, amount, 1);
    }

    SlotItemRecord slot(int index, long amount, long createdAt) {
        return new SlotItemRecord("personal", player.toString(), "personal", index, "hash", "all", "石头", "STONE", "石头", "shitou", "st",
            "serialized", "{}", amount, createdAt, createdAt, "");
    }

    @Test void insertAndCompareAndSetPreventReplay() throws Exception {
        SlotItemRecord original = slot(0, 64);
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(null, original))));
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(original, slot(0, 32)))));
        assertFalse(repository.applySlotChanges(List.of(new SlotChange(original, slot(0, 32)))));
        assertEquals(32, repository.loadSlot("personal", player.toString(), "personal", 0).orElseThrow().amount());
    }

    @Test void laterConflictRollsBackEarlierMerge() throws Exception {
        repository.upsertSlot(slot(0, 64));
        repository.upsertSlot(slot(1, 10));
        assertFalse(repository.applySlotChanges(List.of(new SlotChange(slot(0,64), slot(0,80)), new SlotChange(slot(1,9), null))));
        assertEquals(64, repository.loadSlot("personal", player.toString(), "personal", 0).orElseThrow().amount());
    }

    @Test void sharedActivityIsCommittedWithSlotChangesAndOnlyVisibleToCurrentOwner() throws Exception {
        UUID member = UUID.randomUUID();
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared", player, "共享仓库", 1, 72, 1, 1, "owner", true));
        repository.upsertSharedMember("shared", player, member, "member", 6, 1);
        var first = new SlotItemRecord("shared", "shared", "shared", 0, "hash", "all", "石头", "STONE",
            "", "", "", "serialized", "{}", 64, 1, 1, "");
        UUID lease = UUID.randomUUID();
        assertTrue(repository.acquireSharedLease("shared", member, lease, "Builder", "A", 60_000));
        var actor = new SharedActor(member, "Builder", false, lease);
        assertTrue(repository.applySharedSlotChanges(List.of(new SlotChange(null, first)), actor));
        assertTrue(repository.applySharedSlotChanges(List.of(new SlotChange(first, null)), actor));
        assertFalse(repository.applySharedSlotChanges(List.of(new SlotChange(first, null)), actor));
        var entries = repository.sharedActivity("shared", player, "build", "stone", 0, 0, 8);
        assertEquals(2, entries.size());
        assertEquals(java.util.Set.of("deposit", "withdraw"),
            entries.stream().map(SharedActivity::action).collect(java.util.stream.Collectors.toSet()));
        assertTrue(entries.stream().allMatch(entry -> entry.amount() == 64));
        assertTrue(repository.sharedActivity("shared", member, "", "", 0, 0, 8).isEmpty());
        assertTrue(repository.sharedActivity("shared", player, "%", "", 0, 0, 8).isEmpty());
        repository.transferSharedWarehouse("shared", player, member, 2);
        assertTrue(repository.sharedActivity("shared", player, "", "", 0, 0, 8).isEmpty());
        assertEquals(2, repository.sharedActivity("shared", member, "", "", 0, 0, 8).size());
    }

    @Test void onlyCurrentLeaseAndCurrentMembershipCanChangeSharedSlots() throws Exception {
        UUID member = UUID.randomUUID();
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared", player, "共享仓库", 1, 72, 1, 1, "owner", true));
        repository.upsertSharedMember("shared", player, member, "member", 6, 1);
        UUID old = UUID.randomUUID();
        UUID newToken = UUID.randomUUID();
        assertTrue(repository.acquireSharedLease("shared", player, old, "Owner", "A", 60_000));
        assertFalse(repository.acquireSharedLease("shared", member, newToken, "Builder", "B", 60_000));
        repository.releaseSharedLease("shared", player, old);
        assertTrue(repository.acquireSharedLease("shared", member, newToken, "Builder", "B", 60_000));
        repository.releaseSharedLease("shared", player, old);
        var row = new SlotItemRecord("shared", "shared", "shared", 0, "hash", "all", "石头", "STONE",
            "", "", "", "serialized", "{}", 32, 1, 1, "");
        assertFalse(repository.applySharedSlotChanges(List.of(new SlotChange(null, row)),
            new SharedActor(player, "Owner", false, old)));
        repository.removeSharedMember("shared", player, member);
        assertFalse(repository.renewSharedLease("shared", member, newToken, 60_000));
        assertFalse(repository.applySharedSlotChanges(List.of(new SlotChange(null, row)),
            new SharedActor(member, "Builder", false, newToken)));
        assertTrue(repository.loadSlots("shared", "shared", "shared").isEmpty());
    }

    @Test void sharedActivityPaginatesAndARejectedBatchCannotCreatePhantomHistory() throws Exception {
        repository.createSharedWarehouse(new SharedWarehouseRecord("history", player, "记录测试", 1, 72, 1, 1, "owner", true));
        UUID token = UUID.randomUUID();
        assertTrue(repository.acquireSharedLease("history", player, token, "HistoryOwner", "node", 60_000));
        var actor = new SharedActor(player, "HistoryOwner", false, token);
        SlotItemRecord current = null;
        for (int i = 0; i < 18; i++) {
            var next = new SlotItemRecord("shared", "history", "history", 0, "hash", "all", "橡木木板", "OAK_PLANKS",
                "", "", "", "serialized", "{}", i + 1, 1, i + 1, "");
            assertTrue(repository.applySharedSlotChanges(List.of(new SlotChange(current, next)), actor));
            current = next;
        }
        var first = repository.sharedActivity("history", player, "history", "oak", 0, 0, 8);
        var second = repository.sharedActivity("history", player, "", "木板", 0, 8, 8);
        var last = repository.sharedActivity("history", player, "", "", 0, 16, 8);
        assertEquals(8, first.size());
        assertEquals(8, second.size());
        assertEquals(2, last.size());
        assertTrue(first.stream().allMatch(row -> row.actor().equals("HistoryOwner") && row.amount() == 1));
        assertTrue(repository.sharedActivity("history", player, "", "", System.currentTimeMillis() + 60_000, 0, 8).isEmpty());
        var wrong = new SlotItemRecord("shared", "history", "history", 0, "hash", "all", "橡木木板", "OAK_PLANKS",
            "", "", "", "serialized", "{}", 17, 1, 17, "");
        var update = new SlotItemRecord("shared", "history", "history", 0, "hash", "all", "橡木木板", "OAK_PLANKS",
            "", "", "", "serialized", "{}", 19, 1, 19, "");
        assertFalse(repository.applySharedSlotChanges(List.of(new SlotChange(current, update), new SlotChange(wrong, null)), actor));
        assertEquals(18, repository.sharedActivity("history", player, "", "", 0, 0, 100).size());
        assertEquals(18, repository.loadSlot("shared", "history", "history", 0).orElseThrow().amount());
    }

    @Test void asyncGroundDepositCommitsReceiptWithSlotsAndCannotReplay() throws Exception {
        repository.ensureDefaultPersonalWarehouse(player, "personal", 1);
        String id = UUID.randomUUID().toString();
        var transfer = new MaterialTransfer(id, player.toString(), "personal", "local", "run", "PICKUP",
            "pickup", "entity", "32x STONE", "serialized", 1, 1, "prepared");
        assertTrue(repository.escrowMaterials(List.of(), transfer));
        assertFalse(repository.commitGroundDeposit(List.of(new SlotChange(slot(0, 16), slot(0, 48))), transfer, 32));
        assertEquals("PICKUP", repository.materialTransfer(id, player.toString()).orElseThrow().state());
        assertTrue(repository.commitGroundDeposit(List.of(new SlotChange(null, slot(0, 32))), transfer, 32));
        assertFalse(repository.commitGroundDeposit(List.of(new SlotChange(null, slot(1, 32))), transfer, 32));
        assertEquals("PICKUP_DONE", repository.materialTransfer(id, player.toString()).orElseThrow().state());
        assertEquals(32, repository.sumAmount("personal", player.toString(), "personal"));
    }

    @Test void recreatedSlotRejectsAnOlderSnapshotWithTheSameItemAndAmount() throws Exception {
        SlotItemRecord original = slot(0, 64, 1);
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(null, original))));
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(original, null))));
        SlotItemRecord replacement = slot(0, 64, 2);
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(null, replacement))));

        assertFalse(repository.applySlotChanges(List.of(new SlotChange(original, null))));
        assertFalse(repository.applySlotChanges(List.of(new SlotChange(original, slot(0, 32, 1)))));
        SlotItemRecord retained = repository.loadSlot("personal", player.toString(), "personal", 0).orElseThrow();
        assertEquals(64, retained.amount());
        assertEquals(2, retained.createdAt());
    }

    @Test void failedInsertRollsBackEarlierMerge() throws Exception {
        repository.upsertSlot(slot(0,64));
        assertThrows(SQLException.class, () -> repository.applySlotChanges(List.of(new SlotChange(slot(0,64), slot(0,80)), new SlotChange(null, slot(0,1)))));
        assertEquals(64, repository.loadSlot("personal", player.toString(), "personal", 0).orElseThrow().amount());
    }

    @Test void sqliteFixedDepositUsesOneConnectionAndPaysExactlyOnce() throws Exception {
        var deposit = new FixedDepositRecord("fixed", player, "demo", "money", new BigDecimal("100.01"), new BigDecimal("0.05"), 1, 2, false, 0);
        assertFalse(repository.purchaseFixedDeposit(deposit));
        assertTrue(repository.loadFixedDeposits(player).isEmpty());
        repository.creditBankBalance(player, "money", deposit.principal(), 1);
        assertTrue(repository.purchaseFixedDeposit(deposit));
        assertEquals(new BigDecimal("105.0105"), repository.claimFixedDepositAtomic("fixed", player, 3).orElseThrow());
        assertTrue(repository.claimFixedDepositAtomic("fixed", player, 3).isEmpty());
        assertEquals(0, new BigDecimal("105.0105").compareTo(repository.loadBankBalances(player).get("money")));
    }

    @Test void bankWithdrawalHoldsFundsAndCanOnlyRefundOnce() throws Exception {
        repository.creditBankBalance(player, "money", new BigDecimal("10"), 1);
        var withdrawal = new BankTransfer(UUID.randomUUID().toString(), player, "money", new BigDecimal("6"), 2);
        assertTrue(repository.beginBankWithdrawal(withdrawal));
        assertEquals(0, new BigDecimal("4").compareTo(repository.loadBankBalances(player).get("money")));

        repository.finishBankWithdrawal(withdrawal, false);
        assertThrows(SQLException.class, () -> repository.finishBankWithdrawal(withdrawal, false));
        assertEquals(0, new BigDecimal("10").compareTo(repository.loadBankBalances(player).get("money")));
    }

    @Test void playerPurgeRemovesSlotsAndPreferencesWithoutTouchingOtherPlayers() throws Exception {
        repository.upsertSlot(slot(0,64));
        repository.savePlayerSettings(player,true,true,true,1);
        UUID other=UUID.randomUUID();
        repository.savePlayerSettings(other,true,false,false,1);
        assertTrue(repository.deletePlayerData(player)>0);
        assertTrue(repository.loadSlots("personal",player.toString(),"personal").isEmpty());
        assertTrue(repository.loadPlayerSettings(player).isEmpty());
        assertTrue(repository.loadPlayerSettings(other).isPresent());
        repository.deleteAllPlayerData();
        assertTrue(repository.loadPlayerSettings(other).isEmpty());
    }
    @Test void deletionRequiresTheCurrentLeaseAsWellAsOwnership() throws Exception {
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared", player, "共享仓库", 1, 72, 1, 1, "owner", true));
        repository.upsertSlot(new SlotItemRecord("shared", "shared", "shared", 0, "hash", "all", "石头", "STONE",
            "", "", "", "serialized", "{}", 64, 1, 1, ""));
        UUID lease = UUID.randomUUID();
        assertTrue(repository.acquireSharedLease("shared", player, lease, "owner", "test", 60_000));
        assertThrows(SQLException.class, () -> repository.deleteSharedWarehouse("shared", player, UUID.randomUUID(), 60_000));
        assertEquals(1, repository.loadSlots("shared", "shared", "shared").size());
        repository.deleteSharedWarehouse("shared", player, lease, 60_000);
        assertTrue(repository.loadSharedWarehouse(player, "shared").isEmpty());
        assertTrue(repository.loadSlots("shared", "shared", "shared").isEmpty());
    }

    @Test void staleOwnerCannotTransferRolesOrDeleteWarehouseContents() throws Exception {
        UUID target=UUID.randomUUID(), third=UUID.randomUUID();
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared",player,"共享仓库",1,72,1,1,"owner",true));
        repository.upsertSharedMember("shared",player,target,"member",6,1);
        repository.upsertSharedMember("shared",player,third,"member",6,1);
        repository.upsertSlot(new SlotItemRecord("shared","shared","shared",0,"hash","all","石头","STONE","","","","serialized","{}",64,1,1,""));
        repository.transferSharedWarehouse("shared",player,target,2);
        assertThrows(SQLException.class,()->repository.transferSharedWarehouse("shared",player,third,3));
        assertEquals("owner",repository.loadSharedWarehouse(target,"shared").orElseThrow().viewerRole());
        assertEquals("member",repository.loadSharedWarehouse(third,"shared").orElseThrow().viewerRole());
        assertThrows(SQLException.class,()->repository.deleteSharedWarehouse("shared",player));
        assertEquals(64,repository.loadSlot("shared","shared","shared",0).orElseThrow().amount());
        repository.deleteSharedWarehouse("shared",target);
        assertTrue(repository.loadSlots("shared","shared","shared").isEmpty());
    }
    @Test void missingRecipientRollsBackOwnershipAndOldOwnerRole() throws Exception {
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared",player,"共享仓库",1,72,1,1,"owner",true));
        assertThrows(SQLException.class,()->repository.transferSharedWarehouse("shared",player,UUID.randomUUID(),2));
        var shared=repository.loadSharedWarehouse(player,"shared").orElseThrow();
        assertEquals(player,shared.ownerUuid());
        assertEquals("owner",shared.viewerRole());
    }
    @Test void staleUpgradeCannotApplyTwiceOrOverwriteTransferredWarehouse() throws Exception {
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared",player,"共享仓库",1,72,1,1,"owner",true));
        repository.updateSharedWarehouseLevel("shared",player,1,2,144,2);
        assertThrows(SQLException.class,()->repository.updateSharedWarehouseLevel("shared",player,1,2,144,3));
        assertThrows(SQLException.class,()->repository.updateSharedWarehouseLevel("shared",UUID.randomUUID(),2,3,216,4));
        assertEquals(2,repository.loadSharedWarehouse(player,"shared").orElseThrow().level());
    }
    @Test void memberLimitAllowsRoleEditsButRejectsNewMembersAndStaleOwners() throws Exception {
        UUID target=UUID.randomUUID(),third=UUID.randomUUID();
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared",player,"共享仓库",1,72,1,1,"owner",true));
        repository.upsertSharedMember("shared",player,target,"member",2,2);
        repository.upsertSharedMember("shared",player,target,"viewer",2,3);
        assertEquals("viewer",repository.loadSharedWarehouse(target,"shared").orElseThrow().viewerRole());
        assertThrows(SQLException.class,()->repository.upsertSharedMember("shared",player,third,"member",2,4));
        repository.upsertSharedMember("shared",player,target,"member",2,5);
        repository.transferSharedWarehouse("shared",player,target,6);
        assertThrows(SQLException.class,()->repository.upsertSharedMember("shared",player,third,"member",6,7));
        assertThrows(SQLException.class,()->repository.removeSharedMember("shared",player,target));
        assertEquals("owner",repository.loadSharedWarehouse(target,"shared").orElseThrow().viewerRole());
    }
}
