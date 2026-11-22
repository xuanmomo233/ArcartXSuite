package xuanmo.arcartxsuite.entitytracker.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.*;
import xuanmo.arcartxsuite.entitytracker.config.DropAllocationSettings;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService.DropItemSnapshot;
import xuanmo.arcartxsuite.entitytracker.service.KillAllocation;

class KillSettlementStoreTest {
    @TempDir Path directory;
    SQLiteDataSource source;
    final UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();
    final List<DropItemSnapshot> drops=List.of(new DropItemSnapshot("diamond","原始物品",3,"original-serialized-nbt"));

    @BeforeEach void prepare() throws Exception {
        var config=new SQLiteConfig();config.enforceForeignKeys(true);config.setBusyTimeout(10000);
        source=new SQLiteDataSource(config);source.setUrl("jdbc:sqlite:"+directory.resolve("kills.sqlite"));
        new JdbcEntityTrackerRepository(source,StorageDescriptor.sqlite("kills.sqlite"),directory.toFile(),java.util.logging.Logger.getAnonymousLogger()).initialize();
    }

    @Test void twoWritersFreezeOneKillAndEachBalanceHistoryAndRollOnlyOnce() throws Exception {
        var first=new KillSettlementStore(source,new KillAllocation(null));
        var second=new KillSettlementStore(source,new KillAllocation(null));
        var executor=Executors.newFixedThreadPool(2);
        KillSettlementStore.Stored a,b;
        try {
            var start=new CountDownLatch(1);
            var one=executor.submit(()->{start.await();return first.commit("one",record(),settlement(),drops,true,settings("roll"));});
            var two=executor.submit(()->{start.await();return second.commit("one",record(),settlement(),drops,true,settings("roll"));});
            start.countDown();a=one.get(15,TimeUnit.SECONDS);b=two.get(15,TimeUnit.SECONDS);
        }finally{executor.shutdownNow();}
        assertEquals(a.killId(),b.killId());assertEquals(a.deliveries(),b.deliveries());assertNotEquals(a.created(),b.created());
        assertEquals(1,count("boss_kill_records"));assertEquals(1,count("entitytracker_kill_receipts"));
        assertEquals(2,count("dkp_transaction_records"));assertEquals(1,count("drop_allocation_records"));assertEquals(2,count("roll_participation_records"));
        assertEquals(55,scalar("SELECT SUM(total_points) FROM player_dkp"));
        assertEquals(1,scalar("SELECT kill_count FROM boss_drop_statistics"));
        assertEquals(1,scalar("SELECT drop_count FROM boss_drop_statistics"));
        assertEquals("original-serialized-nbt",a.deliveries().get(0).itemData());
    }

    @Test void rollbackAfterBalanceAndAllocationChangesLeavesNoPartialKill() throws Exception {
        var broken=mock(DataSource.class);
        when(broken.getConnection()).thenAnswer(call->{
            var conn=spy(source.getConnection());
            doAnswer(prepare->{
                String sql=prepare.getArgument(0);
                var statement=(PreparedStatement)prepare.callRealMethod();
                if(sql.startsWith("INSERT INTO roll_participation_records")) {
                    var wrapped=spy(statement);doThrow(new SQLException("fault before commit")).when(wrapped).executeBatch();return wrapped;
                }
                return statement;
            }).when(conn).prepareStatement(anyString());
            return conn;
        });
        var store=new KillSettlementStore(broken,new KillAllocation(null));
        assertThrows(SQLException.class,()->store.commit("rollback",record(),settlement(),drops,true,settings("roll")));
        for(var table:List.of("boss_kill_records","entitytracker_kill_receipts","entitytracker_boss_totals","boss_drop_statistics","player_dkp","dkp_transaction_records","drop_allocation_records","roll_participation_records"))assertEquals(0,count(table),table);
        assertTrue(new KillSettlementStore(source,new KillAllocation(null)).commit("rollback",record(),settlement(),drops,true,settings("roll")).created());
    }

    @Test void hostAcceptedButModuleReceiptFailedReplaysTheSameFrozenBatch() throws Exception {
        var host=mock(PendingRewardService.class);
        var accepted=new HashMap<String,List<PendingRewardService.PendingReward>>();
        var calls=new AtomicInteger();
        when(host.savePendingBatchConfirmed(anyString(),anyList())).thenAnswer(call->{
            String key=call.getArgument(0);List<PendingRewardService.PendingReward> rewards=call.getArgument(1);
            var prior=accepted.putIfAbsent(key,rewards);
            if(prior!=null)assertEquals(prior.stream().map(PendingRewardService.PendingReward::payload).toList(),rewards.stream().map(PendingRewardService.PendingReward::payload).toList());
            if(calls.getAndIncrement()==0)throw new IllegalStateException("lost host confirmation");
            return true;
        });
        var store=new KillSettlementStore(source,new KillAllocation(host));
        var frozen=store.commit("replay",record(),settlement(),drops,true,settings("roll"));
        assertThrows(IllegalStateException.class,()->store.handoff(frozen));
        var reopened=new KillSettlementStore(source,new KillAllocation(host));
        assertEquals(frozen.deliveries(),reopened.pending(64).get(0).deliveries());
        assertTrue(reopened.handoff(reopened.pending(64).get(0)));
        assertTrue(reopened.pending(64).isEmpty());assertEquals(1,accepted.size());assertEquals(2,calls.get());
        assertTrue(reopened.commit("replay",record(),settlement(),drops,true,settings("roll")).deliveries().isEmpty());
        assertEquals(1,count("boss_kill_records"));assertEquals(55,scalar("SELECT SUM(total_points) FROM player_dkp"));
    }

    @Test void statisticsUseAllKillsIncludingEarlierKillsWithoutThisDrop() throws Exception {
        var store=new KillSettlementStore(source,new KillAllocation(null));
        store.commit("empty",record(),settlement(),List.of(),true,DropAllocationSettings.disabled());
        store.commit("drop",record(),settlement(),drops,true,DropAllocationSettings.disabled());
        assertEquals(2,scalar("SELECT kill_count FROM boss_drop_statistics"));
        try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery("SELECT drop_rate FROM boss_drop_statistics")) {
            assertTrue(rows.next());assertEquals(0.5,rows.getDouble(1),0.00001);
        }
    }

    @Test void manualModeDoesNotRequireItemSerializationOrEnqueueExtraItems() throws Exception {
        var host=mock(PendingRewardService.class);var store=new KillSettlementStore(source,new KillAllocation(host));
        var result=store.commit("manual",record(),settlement(),List.of(new DropItemSnapshot("diamond","原掉落",3,null)),true,settings("manual"));
        assertTrue(result.deliveries().isEmpty());assertTrue(store.handoff(result));verifyNoInteractions(host);
        assertEquals(1,count("drop_allocation_records"));assertEquals(0,count("roll_participation_records"));
    }

    @Test void dkpModeChoosesFromOneBatchReadAfterEarningPoints() throws Exception {
        var store=new KillSettlementStore(source,new KillAllocation(null));
        var result=store.commit("dkp",record(),settlement(),drops,false,settings("dkp"));
        assertEquals(alice,result.deliveries().get(0).player());assertEquals(30,result.deliveries().get(0).score());
        assertEquals(0,count("boss_drop_statistics"));assertEquals(0,count("entitytracker_boss_totals"));
    }

    @Test void severalStacksOfOneMaterialAreOneDropOccurrenceButSeparateAllocations() throws Exception {
        var store=new KillSettlementStore(source,new KillAllocation(null));
        var many=java.util.stream.IntStream.range(0,32).mapToObj(i->new DropItemSnapshot("diamond","NBT-"+i,1,"data-"+i)).toList();
        var result=store.commit("nbt",record(),settlement(),many,true,settings("roll"));
        assertEquals(32,result.deliveries().size());assertEquals(32,count("drop_allocation_records"));
        assertEquals(1,scalar("SELECT drop_count FROM boss_drop_statistics"));
        assertEquals(1,scalar("SELECT drop_rate FROM boss_drop_statistics"));
    }

    @Test void disabledDkpAndPriorityModesLeaveItemsForManualHandling() throws Exception {
        for(var mode:List.of("dkp","priority")) {
            var off=new DropAllocationSettings(true,mode,60,DropAllocationSettings.DkpSettings.disabled(),
                DropAllocationSettings.RollSettings.defaults(),new DropAllocationSettings.PrioritySettings(false,Map.of(),Map.of()));
            assertFalse(KillAllocation.allocatesItems(off,settlement()));
            var result=new KillSettlementStore(source,new KillAllocation(null)).commit(mode,record(),settlement(),
                List.of(new DropItemSnapshot("diamond","original",3,null)),false,off);
            assertTrue(result.deliveries().isEmpty());
        }
        assertEquals(0,count("dkp_transaction_records"));assertEquals(0,count("roll_participation_records"));
    }

    DropAllocationSettings settings(String mode) {
        return new DropAllocationSettings(true,mode,60,new DropAllocationSettings.DkpSettings(true,10,Map.of(1,20,2,15)),
            DropAllocationSettings.RollSettings.defaults(),DropAllocationSettings.PrioritySettings.defaults());
    }
    BossDamageSettlementRecord settlement() {
        var first=new BossDamageSettlementEntry(alice,"Alice",1,true,100,50,0,false,"",List.of());
        var second=new BossDamageSettlementEntry(bob,"Bob",2,true,100,50,0,false,"",List.of());
        var players=new LinkedHashMap<UUID,BossDamageSettlementEntry>();players.put(alice,first);players.put(bob,second);
        return new BossDamageSettlementRecord("one",System.currentTimeMillis(),"boss","Boss","entity",2,2,200,BossDamageRankingRewardsSettings.defaults(),List.of(first,second),players);
    }
    BossKillRecord record() {
        var record=new BossKillRecord();record.setBossId("boss");record.setBossDisplayName("Boss");record.setKillTime(LocalDateTime.now());
        record.setServerName("node");record.setParticipantsJson("[]");record.setDropsJson("[]");return record;
    }
    int count(String table) throws Exception {return scalar("SELECT COUNT(*) FROM "+table);}
    int scalar(String sql) throws Exception {
        try(var conn=source.getConnection();var statement=conn.createStatement();var rows=statement.executeQuery(sql)){assertTrue(rows.next());return rows.getInt(1);}
    }
}
