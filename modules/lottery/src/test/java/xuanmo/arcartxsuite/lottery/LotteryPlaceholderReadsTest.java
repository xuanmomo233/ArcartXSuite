package xuanmo.arcartxsuite.lottery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

class LotteryPlaceholderReadsTest {
    @Test void allPointPlaceholdersShareOneBackgroundRead() throws Exception {
        var repository = mock(LotteryRepository.class);
        UUID player = UUID.randomUUID();
        Thread caller = Thread.currentThread();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.getPoints(player)).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return Map.of("one", 7L, "two", 8L);
        });
        try (var reads = new LotteryPlaceholderReads(repository, Logger.getAnonymousLogger())) {
            try {
                assertNull(reads.point(player, "one"));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertNull(reads.point(player, "two"));
                release.countDown();
                long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (reads.point(player, "one") == null && System.nanoTime() < limit) Thread.sleep(1);
                assertEquals(7L, reads.point(player, "one"));
                assertEquals(8L, reads.point(player, "two"));
                verify(repository, times(1)).getPoints(player);
            } finally { release.countDown(); }
        }
    }
}
