package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery;
import xuanmo.arcartxsuite.chat.model.RedPacketReward;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;
import xuanmo.arcartxsuite.chat.storage.ChatRelayJournal;

class ChatShutdownTest {
    @Test
    void alreadyRunningExpiryScanIsIncludedInDatabaseShutdown() throws Exception {
        var repository = mock(ChatRepository.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadActiveRedPackets()).thenAnswer(call -> {
            entered.countDown();
            await(release);
            return java.util.List.of();
        });
        var service = new RedPacketService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), null,
            repository, null, null, null, null, null, null, null, null, null, false);
        var field = RedPacketService.class.getDeclaredField("expiration");
        field.setAccessible(true);
        Runnable callback = (Runnable) field.get(service);
        var scan = java.util.concurrent.CompletableFuture.runAsync(callback);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        scan.get(2, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
        callback.run();
        verify(repository, times(1)).loadActiveRedPackets();
    }
    @Test
    void activeDeliveryQueryRemainsTrackedAfterCancellation() throws Exception {
        var delivery = new ChatDeliveryQueue(Runnable::run, () -> 0);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        assertTrue(delivery.offer(() -> {
            entered.countDown();
            boolean interrupted = false;
            while (release.getCount() > 0) {
                try { release.await(); }
                catch (InterruptedException stopped) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return 1;
        }, (value, failure) -> {}));
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            delivery.close();
            assertFalse(delivery.stopped().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        delivery.stopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }
    @Test
    void queuedRewardReceiptFinishesAfterShutdownWithoutWaitingOnTheCaller() throws Exception {
        var service = new RedPacketService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), null,
            mock(ChatRepository.class), null, null, null, null, null, null, null, null, null, false);
        var executor = field(service, "ioExecutor");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var receipts = new AtomicInteger();
        executor.execute(() -> { entered.countDown(); await(release); });
        executor.execute(receipts::incrementAndGet);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            service.shutdown();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> executor.execute(() -> {}));
        } finally { release.countDown(); }
        service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
        service.shutdown();
        assertEquals(1, receipts.get());
    }

    @Test
    void relayReportsTheActualExitOfAnInterruptedDriver() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        var relay = new ChatRelay(mock(ChatRelayJournal.class), "lab", Runnable::run,
            (payload, source, timestamp) -> java.util.concurrent.CompletableFuture.completedFuture(false),
            () -> {}, Logger.getAnonymousLogger(), () -> true);
        var executor = field(relay, "writer");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            boolean interrupted = false;
            while (release.getCount() > 0) {
                try { release.await(); }
                catch (InterruptedException stopped) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            relay.close();
            assertFalse(relay.stopped().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        relay.stopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }

    @Test
    void queuedRelayWriteIsAbandonedWhenTheSeatExpires() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        var journal = mock(ChatRelayJournal.class);
        var allowed = new AtomicBoolean(true);
        var relay = new ChatRelay(journal, "lab", Runnable::run,
            (payload, source, timestamp) -> CompletableFuture.completedFuture(false),
            () -> {}, Logger.getAnonymousLogger(), allowed::get);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        field(relay, "writer").execute(() -> { entered.countDown(); await(release); });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var receipt = relay.publish(UUID.randomUUID(), "pending");
            allowed.set(false);
            release.countDown();
            assertFalse(receipt.toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertFalse(relay.publish(UUID.randomUUID(), "later").toCompletableFuture().get(2, TimeUnit.SECONDS));
            verifyNoInteractions(journal);
        } finally {
            release.countDown();
            relay.close();
            relay.stopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void itemRedPacketConfirmsHostAcceptanceOnTheStorageWorkerBeforeFinishing() throws Exception {
        var repository = mock(ChatRepository.class);
        var pending = mock(PendingRewardService.class);
        var service = new RedPacketService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), null,
            repository, null, null, null, pending, null, null, null, null, null, false);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CompletableFuture<Void>();
        var recipient = UUID.randomUUID();
        var admission = new AuthSnapshot(recipient, UUID.randomUUID(), 3, recipient, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var delivery = new RedPacketDelivery("item-test", "packet-test", recipient, RedPacketReward.item("AQID", "diamond"),
            false, RedPacketDelivery.Status.READY, Instant.now());
        when(repository.transitionDelivery(any(), any(), any())).thenReturn(true);
        Thread main = Thread.currentThread();
        when(pending.savePendingBatchConfirmed(eq("redpacket:item:item-test"), anyList())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            List<PendingRewardService.PendingReward> items = call.getArgument(1);
            assertEquals(1, items.size());
            assertEquals(recipient, items.get(0).playerUuid());
            assertEquals(PendingRewardService.PendingReward.Type.ITEM_SERIALIZED, items.get(0).type());
            assertTrue(items.get(0).payload().contains("AQID"));
            entered.countDown();
            await(release);
            return true;
        });
        var reserve = RedPacketService.class.getDeclaredMethod("reserveDelivery", RedPacketDelivery.class, AuthSnapshot.class);
        reserve.setAccessible(true);
        field(service, "ioExecutor").execute(() -> {
            try (var auth = mockStatic(AuthAccess.class)) {
                auth.when(() -> AuthAccess.snapshot(recipient)).thenReturn(admission);
                reserve.invoke(service, delivery, admission);
                completed.complete(null);
            } catch (Throwable failure) { completed.completeExceptionally(failure); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            verify(repository, never()).transitionDelivery(any(), eq(RedPacketDelivery.Status.HANDED_OFF), any());
            service.shutdown();
        } finally { release.countDown(); }
        completed.get(5, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verify(repository).transitionDelivery(any(), eq(RedPacketDelivery.Status.HANDED_OFF), any());
    }

    private ModuleExecutor field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (ModuleExecutor) field.get(target);
    }

    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException stopped) { throw new AssertionError(stopped); }
    }
}
