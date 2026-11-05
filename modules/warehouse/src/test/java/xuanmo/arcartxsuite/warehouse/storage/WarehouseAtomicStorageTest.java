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
        return new SlotItemRecord("personal", player.toString(), "personal", index, "hash", "all", "石头", "STONE", "石头", "shitou", "st",
            "serialized", "{}", amount, 1, 1, "");
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

    @Test void failedInsertRollsBackEarlierMerge() throws Exception {
        repository.upsertSlot(slot(0,64));
        assertThrows(SQLException.class, () -> repository.applySlotChanges(List.of(new SlotChange(slot(0,64), slot(0,80)), new SlotChange(null, slot(0,1)))));
        assertEquals(64, repository.loadSlot("personal", player.toString(), "personal", 0).orElseThrow().amount());
    }

    @Test void sqliteFixedDepositUsesOneConnectionAndPaysExactlyOnce() throws Exception {
        repository.createFixedDeposit(new FixedDepositRecord("fixed", player, "demo", "money", new BigDecimal("100.01"), new BigDecimal("0.05"), 1, 2, false, 0));
        assertEquals(new BigDecimal("105.0105"), repository.claimFixedDepositAtomic("fixed", player, 3).orElseThrow());
        assertTrue(repository.claimFixedDepositAtomic("fixed", player, 3).isEmpty());
        assertEquals(0, new BigDecimal("105.0105").compareTo(repository.loadBankBalances(player).get("money")));
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
