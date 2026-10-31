package xuanmo.arcartxsuite.bridge;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingRewardBatchTest {
    @TempDir Path dir;
    DefaultPendingRewardService service;
    final UUID player=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        StorageManager storage=mock(StorageManager.class);
        when(storage.getHostConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("pending.db")));
        service=new DefaultPendingRewardService(null,Logger.getAnonymousLogger(),storage,null);
        service.initialize();
    }
    PendingReward coins(int amount) { return PendingReward.currency(player,"abyss","money",amount,"test"); }
    @Test void identicalOperationIsOnlyPersistedOnceEvenAfterDelivery() {
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        var pending=service.loadPending(player);assertEquals(1,pending.size());
        service.markDelivered(pending.get(0).id());
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        assertTrue(service.loadPending(player).isEmpty());
    }
    @Test void conflictingReplayIsRejected() {
        assertTrue(service.savePendingBatchConfirmed("key",List.of(coins(5))));
        assertFalse(service.savePendingBatchConfirmed("key",List.of(coins(6))));
        assertEquals(1,service.loadPending(player).size());
    }
    @Test void failedSecondInsertRollsBackRewardsAndReceipt() {
        var invalid=new PendingReward(0,player,"abyss",PendingReward.Type.CURRENCY,null,"bad",0,0);
        assertFalse(service.savePendingBatchConfirmed("retryable",List.of(coins(5),invalid)));
        assertTrue(service.loadPending(player).isEmpty());
        assertTrue(service.savePendingBatchConfirmed("retryable",List.of(coins(5))));
        assertEquals(1,service.loadPending(player).size());
    }
    @Test void batchesCannotMixRecipientsOrSources() {
        assertThrows(IllegalArgumentException.class,()->service.savePendingBatchConfirmed("key",List.of(coins(5),
            PendingReward.currency(UUID.randomUUID(),"abyss","money",5,"wrong"))));
        assertTrue(service.loadPending(player).isEmpty());
    }
    @Test void unavailableDatabaseUsesHostOutboxAndImportsExactlyOnceAfterRecovery()throws Exception {
        StorageManager unavailable=mock(StorageManager.class);when(unavailable.getHostConnection()).thenThrow(new java.sql.SQLException("offline"));
        var plugin=mock(org.bukkit.plugin.java.JavaPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());
        var host=new DefaultPendingRewardService(plugin,Logger.getAnonymousLogger(),unavailable,null);
        assertTrue(host.savePendingBatchConfirmed("outage",List.of(coins(9))));
        assertTrue(host.savePendingBatchConfirmed("outage",List.of(coins(9))));
        assertFalse(host.savePendingBatchConfirmed("outage",List.of(coins(10))));
        doAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("pending.db"))).when(unavailable).getHostConnection();
        var replay=DefaultPendingRewardService.class.getDeclaredMethod("importOutbox",UUID.class);replay.setAccessible(true);
        replay.invoke(host,player);replay.invoke(host,player);
        assertEquals(1,host.loadPending(player).size());
    }
}
