package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;

class OnlineRewardsScheduledAwardsTest {
    @Test
    void closingDrainsAcceptedBatchesAndRetriesWithoutBlockingTheCaller() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        OnlineRewardsRepository repo = (OnlineRewardsRepository) Proxy.newProxyInstance(
            OnlineRewardsRepository.class.getClassLoader(), new Class<?>[] { OnlineRewardsRepository.class },
            (proxy, method, args) -> {
                assertEquals("enqueueRewardBatch", method.getName());
                if (writes.incrementAndGet() == 1) {
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    throw new SQLException("lost acknowledgement during retirement");
                }
                return 1;
            });
        var awards = new OnlineRewardsScheduledAwards(repo, Logger.getAnonymousLogger());
        var committed = new AtomicInteger();
        try {
            assertTrue(awards.submit("time:retirement:first", Map.of(), committed::addAndGet));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(awards.submit("time:retirement:second", Map.of(), committed::addAndGet));
            long start = System.nanoTime();
            awards.close();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(awards.stopped().toCompletableFuture().isDone());
            assertFalse(awards.submit("time:retirement:rejected", Map.of(), committed::addAndGet));
        } finally { release.countDown(); awards.close(); }
        awards.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(3, writes.get());
        assertEquals(2, committed.get());
    }

    @Test
    void retriesUnknownCommitWithTheSameBatchAndFrozenRecipients() throws Exception {
        String batch = "time:test:at:2026-11-15";
        var recipients = Map.of(UUID.randomUUID(), List.of(new Reward("COMMAND", "say frozen", "")));
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger(-1);
        CountDownLatch completed = new CountDownLatch(1);
        Thread main = Thread.currentThread();
        OnlineRewardsRepository repo = (OnlineRewardsRepository) Proxy.newProxyInstance(
            OnlineRewardsRepository.class.getClassLoader(), new Class<?>[] { OnlineRewardsRepository.class },
            (proxy, method, args) -> {
                assertNotEquals(main, Thread.currentThread());
                assertEquals("enqueueRewardBatch", method.getName());
                assertEquals(batch, args[0]);
                assertEquals(recipients, args[1]);
                if (attempts.incrementAndGet() == 1) throw new SQLException("commit acknowledgement lost");
                return 0;
            });
        try (var awards = new OnlineRewardsScheduledAwards(repo, Logger.getAnonymousLogger())) {
            assertTrue(awards.submit(batch, recipients, count -> {
                accepted.set(count);
                completed.countDown();
            }));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }
        assertEquals(2, attempts.get());
        assertEquals(0, accepted.get());
    }
}
