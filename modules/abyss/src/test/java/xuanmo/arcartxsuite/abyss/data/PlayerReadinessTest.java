package xuanmo.arcartxsuite.abyss.data;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerReadinessTest {
    @Test void recordCacheMissNeverWaitsForDatabaseAndMissingRowsAreCached() throws Exception {
        var repository = mock(JdbcAbyssRepository.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        UUID player = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadRecord(player, "test")).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        var data = new PlayerDataManager(repository, Logger.getAnonymousLogger());
        try {
            assertNull(data.loadRecord(player, "test"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) assertNull(data.loadRecord(player, "test"));
            verify(repository, times(1)).loadRecord(player, "test");
            release.countDown();
            data.submitDatabase(() -> null).get(5, TimeUnit.SECONDS);
            assertArrayEquals(new long[]{0, 0}, data.loadRecord(player, "test"));
            verify(repository, times(1)).loadRecord(player, "test");
        } finally { release.countDown(); data.shutdown(); }
    }

    @Test void cooldownLookupsUseTheCompletePreloadSnapshotWithoutSynchronousMissQueries() throws Exception {
        var repository = mock(JdbcAbyssRepository.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        UUID player = UUID.randomUUID();
        when(repository.loadStamina(player)).thenReturn(new long[]{10, 0});
        when(repository.loadAllCooldowns(player)).thenReturn(Map.of("known", 123L));
        var data = new PlayerDataManager(repository, Logger.getAnonymousLogger());
        try {
            data.preload(player).get(5, TimeUnit.SECONDS);
            assertEquals(123, data.getCooldown(player, "known"));
            assertEquals(0, data.getCooldown(player, "unknown"));
            verify(repository, never()).loadCooldown(any(), anyString());
        } finally { data.shutdown(); }
    }

    @Test void aLateRecordReadCannotOverwriteACompletionInvalidation() throws Exception {
        var repository = mock(JdbcAbyssRepository.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        UUID player = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadRecord(player, "test")).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new long[]{1, 30};
        }).thenReturn(new long[]{2, 20});
        var data = new PlayerDataManager(repository, Logger.getAnonymousLogger());
        try {
            data.loadRecord(player, "test");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            data.saveRecord(player, "test", 20);
            release.countDown();
            data.submitDatabase(() -> null).get(5, TimeUnit.SECONDS);
            assertNull(data.loadRecord(player, "test"));
            data.submitDatabase(() -> null).get(5, TimeUnit.SECONDS);
            assertArrayEquals(new long[]{2, 20}, data.loadRecord(player, "test"));
        } finally { release.countDown(); data.shutdown(); }
    }

    @Test void recoveryReadsAreAsyncAndDoNotConsumeUntilExplicitConfirmation()throws Exception {
        var repository=mock(JdbcAbyssRepository.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        UUID player=UUID.randomUUID();var location=new JdbcAbyssRepository.PendingLocation("home",1,64,1,0,0,123);
        when(repository.loadPendingLocation(player)).thenAnswer(call->{assertEquals("Abyss-DB",Thread.currentThread().getName());return location;});
        when(repository.confirmPendingLocation(player,location)).thenReturn(true);
        var data=new PlayerDataManager(repository,Logger.getAnonymousLogger());
        try {
            assertEquals(location,data.loadPendingLocation(player).get(5,TimeUnit.SECONDS));
            verify(repository,never()).confirmPendingLocation(any(),any());
            assertTrue(data.confirmPendingLocation(player,location).get(5,TimeUnit.SECONDS));
        } finally {data.shutdown();}
    }
    @Test void coldDataNeverPretendsToHaveMaximumStaminaAndDoesNotOverwriteWrites()throws Exception {
        var repository=mock(JdbcAbyssRepository.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        UUID player=UUID.randomUUID();when(repository.loadStamina(player)).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return new long[]{7,123};});
        when(repository.loadAllCooldowns(player)).thenReturn(Map.of("test",9000L));
        var data=new PlayerDataManager(repository,Logger.getAnonymousLogger());
        try {
            assertEquals(0,data.getStamina(player,100));assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(data.ready(player));
            assertThrows(IllegalStateException.class,()->data.setStamina(player,90,0));
            release.countDown();data.preload(player).get(5,TimeUnit.SECONDS);
            assertTrue(data.ready(player));assertEquals(7,data.getStamina(player,100));
            data.setStamina(player,5,1000);data.preload(player).get(5,TimeUnit.SECONDS);
            assertEquals(5,data.getStamina(player,100));verify(repository,times(1)).loadStamina(player);
        } finally {release.countDown();data.shutdown();}
    }
    @Test void failedLoadLeavesAdmissionClosedAndCanBeRetried()throws Exception {
        var repository=mock(JdbcAbyssRepository.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));UUID player=UUID.randomUUID();
        when(repository.loadStamina(player)).thenThrow(new java.sql.SQLException("unavailable")).thenReturn(new long[]{3,1});
        when(repository.loadAllCooldowns(player)).thenReturn(Map.of());
        var data=new PlayerDataManager(repository,Logger.getAnonymousLogger());
        try {
            assertThrows(ExecutionException.class,()->data.preload(player).get(5,TimeUnit.SECONDS));
            data.preload(player).get(5,TimeUnit.SECONDS);assertEquals(3,data.getStamina(player,100));
        } finally {data.shutdown();}
    }
    @Test void leaderboardDatabaseWorkRunsOnTheOwnedDbExecutor()throws Exception {
        var repository=mock(JdbcAbyssRepository.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var thread=new java.util.concurrent.atomic.AtomicReference<String>();
        when(repository.loadLeaderboard(anyString(),anyInt())).thenAnswer(call->{thread.set(Thread.currentThread().getName());return List.of();});
        var data=new PlayerDataManager(repository,Logger.getAnonymousLogger());
        try {data.loadLeaderboardAsync("test",1000).get(5,TimeUnit.SECONDS);assertEquals("Abyss-DB-Read",thread.get());verify(repository).loadLeaderboard("test",200);}
        finally {data.shutdown();}
    }
}
