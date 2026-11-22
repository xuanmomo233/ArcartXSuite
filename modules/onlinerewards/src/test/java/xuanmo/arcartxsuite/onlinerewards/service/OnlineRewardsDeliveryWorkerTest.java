package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;

class OnlineRewardsDeliveryWorkerTest {
    private final UUID player = UUID.randomUUID();
    private final Delivery delivery = new Delivery(player, "duration:test", 0, new Reward("COMMAND", "say test", ""));

    @Test
    void claimsAndWritesReceiptOnWorkerAndSettlesOnlyOnce() throws Exception {
        Thread main = Thread.currentThread();
        CountDownLatch receipt = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<UUID> attempt = new AtomicReference<>();
        try (var worker = new OnlineRewardsDeliveryWorker<String>(repository((name, args) -> {
            assertFalse(Thread.currentThread() == main);
            if (name.equals("readyDeliveries")) {
                assertEquals(16, args[1]);
                return List.of(delivery);
            }
            assertEquals("beginDelivery", name);
            attempt.set((UUID) args[1]);
            return true;
        }), Logger.getAnonymousLogger())) {
            assertTrue(worker.fetch(Map.of(player, "session"), 1));
            OnlineRewardsDeliveryWorker.Claim<String> claim = waitForClaim(worker);
            assertEquals(attempt.get(), claim.attempt());
            assertEquals("session", claim.session());
            worker.settle(claim, () -> {
                if (Thread.currentThread() != main) writes.incrementAndGet();
                receipt.countDown();
            });
            worker.settle(claim, writes::incrementAndGet);
            assertTrue(receipt.await(5, TimeUnit.SECONDS));
        }
        assertEquals(1, writes.get());
    }

    @Test
    void closingReleasesOnlyTheClaimNotTakenByPrimaryThread() throws Exception {
        CountDownLatch claimed = new CountDownLatch(1);
        AtomicReference<UUID> attempt = new AtomicReference<>();
        AtomicReference<UUID> released = new AtomicReference<>();
        var worker = new OnlineRewardsDeliveryWorker<String>(repository((name, args) -> switch (name) {
            case "readyDeliveries" -> List.of(delivery);
            case "beginDelivery" -> {
                attempt.set((UUID) args[1]);
                claimed.countDown();
                yield true;
            }
            case "releaseUnstartedDelivery" -> {
                released.set((UUID) args[1]);
                yield true;
            }
            default -> throw new AssertionError(name);
        }), Logger.getAnonymousLogger());
        try (worker) {
            assertTrue(worker.fetch(Map.of(player, "session"), 1));
            assertTrue(claimed.await(5, TimeUnit.SECONDS));
        }
        worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(attempt.get());
        assertEquals(attempt.get(), released.get());
        assertNull(worker.next());
    }

    @Test
    void ambiguousClaimAcknowledgementNeverReachesPrimaryThread() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        AtomicReference<UUID> attempt = new AtomicReference<>();
        AtomicReference<UUID> releasedAttempt = new AtomicReference<>();
        try (var worker = new OnlineRewardsDeliveryWorker<String>(repository((name, args) -> switch (name) {
            case "readyDeliveries" -> List.of(delivery);
            case "beginDelivery" -> {
                attempt.set((UUID) args[1]);
                throw new SQLException("lost acknowledgement");
            }
            case "releaseUnstartedDelivery" -> {
                releasedAttempt.set((UUID) args[1]);
                released.countDown();
                yield true;
            }
            default -> throw new AssertionError(name);
        }), Logger.getAnonymousLogger())) {
            assertTrue(worker.fetch(Map.of(player, "session"), 1));
            assertTrue(released.await(5, TimeUnit.SECONDS));
            assertEquals(attempt.get(), releasedAttempt.get());
            assertNull(worker.next());
        }
    }

    @Test
    void reusesReadOnlyPageWithoutClaimingTheWholePage() throws Exception {
        var rows = IntStream.range(0, 16).mapToObj(step -> new Delivery(player, delivery.batchId(), step, delivery.reward())).toList();
        var reads = new AtomicInteger();
        var claims = new AtomicInteger();
        var receipts = new AtomicInteger();
        var worker = new OnlineRewardsDeliveryWorker<String>(repository((name, args) -> switch (name) {
            case "readyDeliveries" -> {
                reads.incrementAndGet();
                yield rows;
            }
            case "beginDelivery" -> {
                assertEquals(claims.getAndIncrement(), ((Delivery) args[0]).step());
                yield true;
            }
            default -> throw new AssertionError(name);
        }), Logger.getAnonymousLogger());

        try (worker) {
            var audience = Map.of(player, "session");
            for (int step = 0; step < rows.size(); step++) {
                assertTrue(worker.fetch(audience, 1));
                var next = waitForClaim(worker);
                assertEquals(step, next.delivery().step());
                assertEquals(step + 1, claims.get());
                assertTrue(worker.finishPage());
                worker.settle(next, receipts::incrementAndGet);
            }
        }
        worker.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, reads.get());
        assertEquals(rows.size(), receipts.get());
    }

    private static OnlineRewardsDeliveryWorker.Claim<String> waitForClaim(OnlineRewardsDeliveryWorker<String> worker)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        OnlineRewardsDeliveryWorker.Claim<String> claim;
        while ((claim = worker.next()) == null && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotNull(claim);
        return claim;
    }

    private static OnlineRewardsRepository repository(Call call) {
        return (OnlineRewardsRepository) Proxy.newProxyInstance(OnlineRewardsRepository.class.getClassLoader(),
            new Class<?>[] { OnlineRewardsRepository.class }, (proxy, method, args) -> call.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface Call {
        Object invoke(String name, Object[] args) throws SQLException;
    }
}
