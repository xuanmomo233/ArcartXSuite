package xuanmo.arcartxsuite.lottery.packet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class LotteryViewReadsTest {
    @Test void stopDiscardsQueuedReadsAndKeepsTheRunningQueryTracked() throws Exception {
        var reads = new LotteryViewReads(mock(JavaPlugin.class));
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var queued = new AtomicBoolean();
        var replied = new AtomicBoolean();
        for (int i = 0; i < 2; i++) reads.submit(UUID.randomUUID(), () -> {
            entered.countDown();
            try { assertTrue(release.await(4, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            return "running";
        }, (value, error) -> replied.set(true));
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            reads.submit(UUID.randomUUID(), () -> { queued.set(true); return "queued"; },
                (value, error) -> replied.set(true));
            reads.stop();
            assertFalse(reads.stopped().toCompletableFuture().isDone());
        } finally {
            release.countDown();
            reads.stop();
        }
        reads.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertFalse(queued.get());
        assertFalse(replied.get());
    }
}
