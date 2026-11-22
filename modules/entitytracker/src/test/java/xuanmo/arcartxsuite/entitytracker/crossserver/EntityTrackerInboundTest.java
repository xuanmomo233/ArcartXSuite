package xuanmo.arcartxsuite.entitytracker.crossserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.crossserver.*;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.entity.*;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class EntityTrackerInboundTest {
    @TempDir Path directory;

    @Test void fullInboundQueueReturnsNoReceiptAndAcceptedWritesDrainBeforeStop() throws Exception {
        var real=new SQLiteDataSource();real.setUrl("jdbc:sqlite:"+directory.resolve("incoming.sqlite"));
        new JdbcEntityTrackerRepository(real,StorageDescriptor.sqlite("incoming.sqlite"),directory.toFile(),java.util.logging.Logger.getAnonymousLogger()).initialize();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        var source=mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call->{
            if(once.compareAndSet(true,false)){entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}
            return real.getConnection();
        });
        var service=create(source);service.start();
        var futures=new ArrayList<CompletionStage<Boolean>>();
        try {
            futures.add(admit(service,payload()));assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<256;i++)futures.add(admit(service,payload()));
            assertNull(admit(service,payload()));
            service.shutdown();assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            assertNull(admit(service,payload()));release.countDown();
            for(var future:futures)assertTrue(future.toCompletableFuture().get(10,TimeUnit.SECONDS));
            service.databaseStopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
            try(var conn=real.getConnection();var stmt=conn.createStatement();var rows=stmt.executeQuery("SELECT COUNT(*) FROM player_boss_best_damage")) {
                assertTrue(rows.next());assertEquals(1,rows.getInt(1));
            }
        }finally{release.countDown();service.shutdown();}
    }

    @Test void sqlFailureDoesNotConfirmTheTransportReceipt() throws Exception {
        var source=mock(DataSource.class);when(source.getConnection()).thenThrow(new SQLException("offline"));
        var service=create(source);service.start();
        try{assertFalse(admit(service,payload()).toCompletableFuture().get(5,TimeUnit.SECONDS));}
        finally{service.shutdown();service.databaseStopped().toCompletableFuture().get(5,TimeUnit.SECONDS);}
    }

    @Test void killProtocolCarriesIdentityInsteadOfDeduplicatingByTimestamp() {
        var first=new BossKillRecord();first.setBossId("boss");first.setKillTime(LocalDateTime.now());first.setServerName("node");
        first.setSettlementKey(UUID.randomUUID().toString());first.setParticipantsJson("[]");first.setDropsJson("[]");
        var encoded=EntityTrackerCrossServerPayloadCodec.encodeKillRecord(first);
        assertEquals(first.getSettlementKey(),EntityTrackerCrossServerPayloadCodec.decodeKillRecord(encoded).getSettlementKey());
        first.setSettlementKey(UUID.randomUUID().toString());
        assertNotEquals(encoded,EntityTrackerCrossServerPayloadCodec.encodeKillRecord(first));
        assertThrows(IllegalArgumentException.class,()->EntityTrackerCrossServerPayloadCodec.decode(encoded.replaceFirst("KILL_RECORD_V2","KILL_RECORD")));
    }

    private EntityTrackerCrossServerService create(DataSource source) {
        var api=mock(CrossServerAPI.class);var channel=mock(CrossServerChannel.class);
        when(api.openChannelCompleting(anyString(),any(),any())).thenReturn(channel);
        return new EntityTrackerCrossServerService(mock(JavaPlugin.class),java.util.logging.Logger.getAnonymousLogger(),api,CrossServerChannelConfig.disabled(),source);
    }
    private CompletionStage<Boolean> admit(EntityTrackerCrossServerService service,String payload)throws Exception {
        var method=EntityTrackerCrossServerService.class.getDeclaredMethod("admit",String.class);method.setAccessible(true);
        return (CompletionStage<Boolean>)method.invoke(service,payload);
    }
    private String payload() {
        var record=new PlayerBossBestDamage("00000000-0000-0000-0000-000000000001","Player","boss","Boss",100,LocalDateTime.now(),"node");
        return EntityTrackerCrossServerPayloadCodec.encodeBestDamage(record);
    }
}
