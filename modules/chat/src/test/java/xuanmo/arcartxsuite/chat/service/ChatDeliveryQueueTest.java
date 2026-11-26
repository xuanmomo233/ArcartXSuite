package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.GuardedRoutes;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.RouteState;

class ChatDeliveryQueueTest {
    @Test
    void oneGuardCoversTheWholeOrderedRemoteBatchUntilCallbacksFinish() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var received = new ArrayList<String>();
        var locks = new AtomicInteger();
        var held = new AtomicBoolean();
        UUID first = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        try (var queue = new ChatDeliveryQueue(main::add, main::add, () -> 0,
                failure -> { throw failure; }, actors -> {
                    assertEquals(Set.of(first, last), actors);
                    locks.incrementAndGet();
                    return guard(held);
                })) {
            assertTrue(queue.offerGuardedRemote(Set.of(first), 1, route -> "first", received::add, () -> {}));
            assertTrue(queue.offerGuardedRemote(Set.of(last), 1, route -> "last", value -> {
                assertTrue(held.get());
                received.add(value);
            }, () -> {}));
            queue.startAdmittedRemoteBatch();
            next(main).run();
            Runnable callback = next(main);
            assertTrue(held.get());
            callback.run();
            next(main).run();
            assertEquals(1, locks.get());
            assertEquals(List.of("first", "last"), received);
            assertFalse(held.get());
        }
    }

    @Test
    void guardedCallbackCannotSendAfterItsUnstartedLeaseWasCancelled() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var retry = new LinkedBlockingQueue<Runnable>();
        var held = new AtomicBoolean();
        var sent = new AtomicInteger();
        try (var queue = new ChatDeliveryQueue(main::add, retry::add, () -> 0,
                failure -> { throw failure; }, actors -> guard(held))) {
            assertTrue(queue.offerGuardedRemote(Set.of(UUID.randomUUID()), 1, route -> "message",
                message -> sent.incrementAndGet(), () -> {}));
            next(main).run();
            Runnable delayedCallback = next(main);
            next(main).run();
            assertFalse(held.get());
            assertNotNull(retry.poll());
            delayedCallback.run();
            assertEquals(0, sent.get());
        }
    }

    @Test
    void revokedRemoteRouteDoesNotAcquireLocksOrBlockLocalMessages() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var authorized = new AtomicBoolean(true);
        var received = new ArrayList<String>();
        var locks = new AtomicInteger();
        try (var queue = new ChatDeliveryQueue(main::add, main::add, () -> 0,
                failure -> { throw failure; }, actors -> {
                    locks.incrementAndGet();
                    return guard(new AtomicBoolean());
                }, authorized::get)) {
            assertTrue(queue.offerGuardedRemote(Set.of(UUID.randomUUID()), 1, route -> "remote",
                received::add, () -> received.add("abandoned")));
            authorized.set(false);
            assertTrue(queue.offer(() -> "local", (value, failure) -> received.add(value)));
            next(main).run();
            if (received.size() == 1) next(main).run();
            assertEquals(List.of("abandoned", "local"), received);
            assertEquals(0, locks.get());
        }
    }

    @Test
    void localHeadWaitsForAnOutstandingPolicyWriteThenReadsRoutesAgain() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var retry = new LinkedBlockingQueue<Runnable>();
        var pendingPolicy = new AtomicBoolean(true);
        var reads = new AtomicInteger();
        var received = new ArrayList<Boolean>();
        try (var queue = new ChatDeliveryQueue(main::add, retry::add, () -> 0, failure -> { throw failure; })) {
            assertTrue(queue.offer(() -> {
                reads.incrementAndGet();
                return pendingPolicy.get();
            }, value -> true, value -> !pendingPolicy.get(), (value, failure) -> received.add(value)));
            next(main).run();
            assertTrue(received.isEmpty());
            pendingPolicy.set(false);
            next(retry).run();
            next(main).run();
            assertEquals(2, reads.get());
            assertEquals(List.of(false), received);
        }
    }

    private static GuardedRoutes guard(AtomicBoolean held) {
        assertTrue(held.compareAndSet(false, true));
        return new GuardedRoutes() {
            @Override
            public Map<UUID, RouteState> loadRouteStates(Set<UUID> actors, String channelId, String defaultChannelId) {
                return Map.of();
            }

            @Override
            public void close() {
                held.set(false);
            }
        };
    }

    @Test
    void retriesThrowingRemoteCallbackBeforeReleasingAnyReadyLaterMessage() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var retry = new LinkedBlockingQueue<Runnable>();
        var received = new ArrayList<String>();
        var attempts = new AtomicInteger();
        var failures = new AtomicInteger();
        try (var delivery = new ChatDeliveryQueue(main::add, retry::add, () -> 0, failure -> failures.incrementAndGet())) {
            assertTrue(delivery.offerRemote(() -> "first", message -> {
                if (attempts.getAndIncrement() == 0) throw new IllegalStateException("send callback");
                received.add(message);
            }, () -> received.add("abandoned")));
            assertTrue(delivery.offerRemote(() -> "second", received::add, () -> received.add("abandoned")));
            next(main).run();
            next(main).run();
            assertEquals(1, failures.get());
            assertTrue(received.isEmpty());
            next(retry).run();
            next(main).run();
            next(main).run();
            assertEquals(List.of("first", "second"), received);
        }
    }

    @Test
    void retriesTheUnsentRemoteHeadBeforeAReadyTailAndAbandonsOnClose() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var retry = new LinkedBlockingQueue<Runnable>();
        var received = new ArrayList<String>();
        var attempts = new AtomicInteger();
        var aborted = new AtomicInteger();
        var delivery = new ChatDeliveryQueue(main::add, retry::add, () -> 0, failure -> { throw failure; });
        try (delivery) {
            assertTrue(delivery.offerRemote(() -> {
                if (attempts.incrementAndGet() == 1) throw new java.sql.SQLException("storage offline");
                return "first";
            }, received::add, aborted::incrementAndGet));
            assertTrue(delivery.offerRemote(() -> "second", received::add, aborted::incrementAndGet));
            next(main).run();
            next(main).run();
            assertTrue(received.isEmpty());
            next(retry).run();
            next(main).run();
            next(main).run();
            assertEquals(List.of("first", "second"), received);
            assertTrue(delivery.offerRemote(() -> "third", received::add, aborted::incrementAndGet));
            Runnable closing = next(main);
            delivery.close();
            closing.run();
            assertEquals(1, aborted.get());
            assertEquals(List.of("first", "second"), received);
        }
    }

    @Test
    void checksSharedPolicyAgainAtHeadEvenWhenNoNotificationArrives() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var retry = new LinkedBlockingQueue<Runnable>();
        var ignored = new AtomicBoolean();
        var reads = new AtomicInteger();
        var received = new ArrayList<String>();
        try (var delivery = new ChatDeliveryQueue(main::add, retry::add, () -> 0, failure -> { throw failure; })) {
            assertTrue(delivery.offerRemote(() -> {
                if (!ignored.get()) throw new java.sql.SQLException("blocked head");
                return "first";
            }, received::add, () -> {}));
            assertTrue(delivery.offerRemote(() -> {
                reads.incrementAndGet();
                return !ignored.get();
            }, allowed -> {
                if (allowed) received.add("denied recipient");
                received.add("allowed recipient");
            }, () -> {}));
            next(main).run();
            next(main).run();
            assertEquals(1, reads.get());
            ignored.set(true);
            next(retry).run();
            next(main).run();
            next(main).run();
            assertEquals(2, reads.get());
            assertEquals(List.of("first", "allowed recipient"), received);
        }
    }

    @Test
    void limitsPendingWorkAndAllowsRefusedInputAfterEarlierJobsFinish() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var received = new ArrayList<Integer>();
        var release = new CountDownLatch(1);
        try (var delivery = new ChatDeliveryQueue(main::add, () -> 0)) {
            for (int index = 0; index < 256; index++) {
                int message = index;
                assertTrue(delivery.offer(() -> {
                    if (message == 0) {
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) throw new java.sql.SQLException("gate timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new java.sql.SQLException(interrupted);
                        }
                    }
                    return message;
                }, (value, error) -> {
                    assertNull(error);
                    received.add(value);
                }));
            }
            assertFalse(delivery.hasRoom());
            assertFalse(delivery.offer(() -> 256, (value, error) -> received.add(value)));
            release.countDown();
            next(main).run();
            assertTrue(delivery.hasRoom());
            assertTrue(delivery.offer(() -> 256, (value, error) -> received.add(value)));
            while (received.size() < 257) next(main).run();
            assertEquals(java.util.stream.IntStream.rangeClosed(0, 256).boxed().toList(), received);
        }
    }

    @Test
    void rechecksPoliciesWithoutLettingLaterMessagesOvertake() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var policy = new AtomicLong();
        var received = new ArrayList<String>();
        try (var delivery = new ChatDeliveryQueue(main::add, policy::get)) {
            assertTrue(delivery.offer(() -> "first/" + policy.get(), (value, error) -> {
                assertNull(error);
                received.add(value);
            }));
            Runnable first = next(main);
            assertTrue(delivery.offer(() -> "second/" + policy.get(), (value, error) -> {
                assertNull(error);
                received.add(value);
            }));
            Runnable second = next(main);
            policy.incrementAndGet();
            first.run();
            second.run();
            assertTrue(received.isEmpty());
            while (received.size() < 2) next(main).run();
            assertEquals(List.of("first/1", "second/1"), received);
        }
    }

    @Test
    void doesNotDeliverACompletionAfterModuleShutdown() throws Exception {
        var main = new LinkedBlockingQueue<Runnable>();
        var received = new ArrayList<>();
        var delivery = new ChatDeliveryQueue(main::add, () -> 0);
        assertTrue(delivery.offer(() -> "message", (value, error) -> received.add(value)));
        Runnable completion = next(main);
        delivery.close();
        completion.run();
        assertTrue(received.isEmpty());
    }

    private static Runnable next(LinkedBlockingQueue<Runnable> main) throws InterruptedException {
        Runnable task = main.poll(5, TimeUnit.SECONDS);
        assertNotNull(task, "The worker did not finish the query");
        return task;
    }
}
