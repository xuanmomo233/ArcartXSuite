package xuanmo.arcartxsuite.entitytracker.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
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
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService.DropItemSnapshot;
import xuanmo.arcartxsuite.entitytracker.service.KillAllocation;

class KillRetentionTest {
    @TempDir Path directory;
    DataSource source;
    boolean mysql;
    KillSettlementStore store;
    final UUID player=UUID.randomUUID();
    final LocalDateTime cutoff=LocalDateTime.now().minusDays(30);
    final List<DropItemSnapshot> drops=List.of(new DropItemSnapshot("diamond","Item",1,"serialized"));

    @BeforeEach void setup()throws Exception {
        String url=System.getenv("AXS_ENTITY_RETENTION_MYSQL_URL");
        StorageDescriptor descriptor;
        if(url!=null&&!url.isBlank()) {
            if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/suite_retention_[a-z0-9_]+(\\?.*)?"))throw new IllegalArgumentException("Only a loopback retention test database is allowed");
            mysql=true;source=mock(DataSource.class);when(source.getConnection()).thenAnswer(call->DriverManager.getConnection(url));
            descriptor=StorageDescriptor.mysql("127.0.0.1",1,"suite_retention_test","root","",1,"");
        } else {
            var config=new SQLiteConfig();config.enforceForeignKeys(true);
            var sqlite=new SQLiteDataSource(config);sqlite.setUrl("jdbc:sqlite:"+directory.resolve("history.sqlite"));source=sqlite;
            descriptor=StorageDescriptor.sqlite("history.sqlite");
        }
        new JdbcEntityTrackerRepository(source,descriptor,directory.toFile(),Logger.getAnonymousLogger()).initialize();
        var host=mock(PendingRewardService.class);when(host.savePendingBatchConfirmed(anyString(),anyList())).thenReturn(true);
        store=new KillSettlementStore(source,new KillAllocation(host));
    }

    @AfterEach void resetPrivateMysqlFixture()throws Exception {
        if(!mysql)return;
        try(var conn=source.getConnection();var statement=conn.createStatement()) {
            for(var table:List.of("roll_participation_records","drop_allocation_records","dkp_transaction_records",
                    "boss_kill_records","boss_drop_statistics","player_dkp","entitytracker_kill_receipts","entitytracker_boss_totals"))
                statement.executeUpdate("DELETE FROM "+table);
        }
    }

    @Test void expiredTransferredHistoryIsDeletedWithoutBalanceOrAuditLossAndPendingIsKept()throws Exception {
        var old=store.commit("old",record(true),settlement(),drops,true,settings());assertTrue(store.handoff(old));
        store.commit("pending",record(true),settlement(),drops,true,settings());
        store.commit("recent",record(false),settlement(),drops,true,settings());
        var dao=new BossKillRecordDao(source,Logger.getAnonymousLogger());
        assertEquals(1,dao.deleteExpiredBatch(cutoff,256));assertEquals(0,dao.deleteExpiredBatch(cutoff,256));
        assertEquals(2,count("boss_kill_records"));assertEquals(2,count("drop_allocation_records"));
        assertEquals(2,count("roll_participation_records"));assertEquals(3,count("dkp_transaction_records"));
        assertEquals(90,number("SELECT total_points FROM player_dkp"));
        assertEquals(1,number("SELECT COUNT(*) FROM dkp_transaction_records WHERE boss_kill_id IS NULL"));
        assertEquals(3,count("entitytracker_kill_receipts"));
        assertFalse(store.commit("old",record(true),settlement(),drops,true,settings()).created());
        assertEquals(90,number("SELECT total_points FROM player_dkp"));
    }

    @Test void boundedBatchesLeaveUntransferredPlansAlone()throws Exception {
        for(int i=0;i<5;i++)store.commit("plain-"+i,record(true),settlement(),List.of(),false,DropAllocationSettings.disabled());
        store.commit("held",record(true),settlement(),drops,false,settings());
        var dao=new BossKillRecordDao(source,Logger.getAnonymousLogger());
        assertEquals(2,dao.deleteExpiredBatch(cutoff,2));assertEquals(2,dao.deleteExpiredBatch(cutoff,2));
        assertEquals(1,dao.deleteExpiredBatch(cutoff,2));assertEquals(0,dao.deleteExpiredBatch(cutoff,2));
        assertEquals(1,count("boss_kill_records"));assertEquals(1,store.pending(64).size());
        assertThrows(IllegalArgumentException.class,()->dao.deleteExpiredBatch(cutoff,513));
    }

    @Test void cleanupFailureRollsBackAllChildDeletesAndAuditDetachment()throws Exception {
        var old=store.commit("old",record(true),settlement(),drops,false,settings());store.handoff(old);
        var broken=mock(DataSource.class);
        when(broken.getConnection()).thenAnswer(call->{
            var conn=spy(source.getConnection());
            doAnswer(prepare->{
                var statement=(PreparedStatement)prepare.callRealMethod();
                if(((String)prepare.getArgument(0)).startsWith("DELETE FROM boss_kill_records")) {
                    var wrapped=spy(statement);doThrow(new SQLException("fault before parent delete")).when(wrapped).executeUpdate();return wrapped;
                }
                return statement;
            }).when(conn).prepareStatement(anyString());return conn;
        });
        assertThrows(SQLException.class,()->new BossKillRecordDao(broken,Logger.getAnonymousLogger()).deleteExpiredBatch(cutoff,256));
        assertEquals(1,count("boss_kill_records"));assertEquals(1,count("drop_allocation_records"));assertEquals(1,count("roll_participation_records"));
        assertEquals(0,number("SELECT COUNT(*) FROM dkp_transaction_records WHERE boss_kill_id IS NULL"));
        assertEquals(30,number("SELECT total_points FROM player_dkp"));
    }

    @Test void legacyStatisticsKeepTheirLifetimeDenominatorAfterHistoryWasPruned()throws Exception {
        try(var conn=source.getConnection();var statement=conn.createStatement()) {
            statement.execute("INSERT INTO boss_drop_statistics (boss_id,item_id,item_name,drop_count,kill_count,drop_rate,server_name) VALUES ('boss','diamond','Item',100,500,0.2,'node')");
        }
        store.commit("after-upgrade",record(false),settlement(),drops,true,DropAllocationSettings.disabled());
        assertEquals(501,number("SELECT kill_count FROM entitytracker_boss_totals"));
        assertEquals(501,number("SELECT kill_count FROM boss_drop_statistics"));assertEquals(101,number("SELECT drop_count FROM boss_drop_statistics"));
    }

    BossKillRecord record(boolean old) {
        var value=new BossKillRecord();value.setBossId("boss");value.setBossDisplayName("Boss");value.setServerName("node");
        value.setKillTime(old?cutoff.minusDays(1):cutoff.plusDays(1));value.setParticipantsJson("[]");value.setDropsJson("[]");return value;
    }
    BossDamageSettlementRecord settlement() {
        var entry=new BossDamageSettlementEntry(player,"Player",1,true,100,100,0,false,"",List.of());
        return new BossDamageSettlementRecord("S000001",System.currentTimeMillis(),"boss","Boss",UUID.randomUUID().toString(),1,1,100,
            BossDamageRankingRewardsSettings.defaults(),List.of(entry),Map.of(player,entry));
    }
    DropAllocationSettings settings() {
        return new DropAllocationSettings(true,"roll",60,new DropAllocationSettings.DkpSettings(true,10,Map.of(1,20)),
            DropAllocationSettings.RollSettings.defaults(),DropAllocationSettings.PrioritySettings.defaults());
    }
    int count(String table)throws Exception{return number("SELECT COUNT(*) FROM "+table);}
    int number(String sql)throws Exception{try(var conn=source.getConnection();var query=conn.createStatement();var rows=query.executeQuery(sql)){assertTrue(rows.next());return rows.getInt(1);}}
}
