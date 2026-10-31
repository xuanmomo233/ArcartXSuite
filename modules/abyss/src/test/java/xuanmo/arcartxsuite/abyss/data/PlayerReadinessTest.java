package xuanmo.arcartxsuite.abyss.data;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerReadinessTest {
    @Test void coldDataNeverPretendsToHaveMaximumStaminaAndDoesNotOverwriteWrites()throws Exception {
        var repository=mock(JdbcAbyssRepository.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
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
        var repository=mock(JdbcAbyssRepository.class);UUID player=UUID.randomUUID();
        when(repository.loadStamina(player)).thenThrow(new java.sql.SQLException("unavailable")).thenReturn(new long[]{3,1});
        when(repository.loadAllCooldowns(player)).thenReturn(Map.of());
        var data=new PlayerDataManager(repository,Logger.getAnonymousLogger());
        try {
            assertThrows(ExecutionException.class,()->data.preload(player).get(5,TimeUnit.SECONDS));
            data.preload(player).get(5,TimeUnit.SECONDS);assertEquals(3,data.getStamina(player,100));
        } finally {data.shutdown();}
    }
}
