package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsSignInConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressRequest;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressResult;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInCommit;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInStatus;

class OnlineRewardsShutdownTest {
    @Test
    void acceptedTimeAdjustmentIsSavedOffThreadBeforePresenceAndStorageClose() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var cross = mock(CrossServerAPI.class);
        when(cross.nodeId()).thenReturn("isolated");
        var service = service(repository, cross, null);
        var active = OnlineRewardsService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.updateProgress(anyList())).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Write", Thread.currentThread().getName());
            entered.countDown();
            await(release);
            List<ProgressRequest> rows = call.getArgument(0);
            assertEquals(1, rows.size());
            assertEquals(id, rows.get(0).playerUuid());
            var state = new OnlineRewardsPlayerState();
            var plan = rows.get(0).mutation().apply(state, 0);
            assertTrue(plan.changed());
            assertEquals(17, state.onlineMinutes());
            return List.of(new ProgressResult(id, state, true, false, 0));
        });
        var update = service.adjustOnlineTime(player, "add", 17).toCompletableFuture();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(update.isDone());
            long start = System.nanoTime();
            service.shutdown();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).shutdown();
        } finally { release.countDown(); service.shutdown(); }
        assertTrue(update.get(3, TimeUnit.SECONDS).success());
        service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        var ordered = inOrder(repository);
        ordered.verify(repository).updateProgress(anyList());
        ordered.verify(repository).removePresence(eq("isolated"), any(), isNull());
        ordered.verify(repository).shutdown();
        verify(repository, times(1)).shutdown();
    }

    @Test
    void acceptedTitleResultAndItsQueuedReceiptFinishBeforeStorageCloses() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var title = mock(TitleGrantable.class);
        var granted = new CompletableFuture<Boolean>();
        when(title.grantPermanent(any(), anyString(), anyString())).thenReturn(granted);
        var service = service(repository, null, title);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var delivery = new Delivery(id, "time:retirement", 0, new Reward("TITLE", "test", ""));
        when(repository.readyDeliveries(anySet(), anyInt())).thenReturn(List.of(delivery));
        when(repository.beginDelivery(eq(delivery), any())).thenReturn(true);
        var worker = (OnlineRewardsDeliveryWorker<String>) field(service, "deliveryWorker");
        assertTrue(worker.fetch(Map.of(id, "current"), 1));
        OnlineRewardsDeliveryWorker.Claim<String> claim = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (claim == null && System.nanoTime() < deadline) { claim = worker.next(); Thread.sleep(2); }
        assertNotNull(claim);
        var ownedClaim = claim;
        var receiptEntered = new CountDownLatch(1);
        var receiptRelease = new CountDownLatch(1);
        var writes = new AtomicInteger();
        BiConsumer<Object, Throwable> finished = (outcome, failure) -> worker.settle(ownedClaim, () -> {
            assertEquals("AXS-OnlineRewards-Delivery", Thread.currentThread().getName());
            writes.incrementAndGet();
            receiptEntered.countDown();
            await(receiptRelease);
        });
        var dispatch = OnlineRewardsService.class.getDeclaredMethod("dispatchDelivery", Player.class,
            Delivery.class, UUID.class, BiConsumer.class);
        dispatch.setAccessible(true);
        dispatch.invoke(service, player, delivery, claim.attempt(), finished);
        try {
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).shutdown();
            granted.complete(true);
            assertFalse(granted.complete(true));
            assertTrue(receiptEntered.await(2, TimeUnit.SECONDS));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).shutdown();
        } finally { granted.complete(true); receiptRelease.countDown(); service.shutdown(); }
        service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(1, writes.get());
        verify(repository).shutdown();
    }

    @Test
    void admissionWakesOnlyTheAdmittedPlayerAndReleasesAClaimForTheOldSession() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var service = service(repository, null, null);
        var active = OnlineRewardsService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        var snapshot = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var reward = new Delivery(id, "daily:test", 0, new Reward("MESSAGE", "queued", ""));
        var claimed = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        var attempt = new AtomicReference<UUID>();
        when(repository.readyDeliveries(eq(Set.of(id)), anyInt())).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Delivery", Thread.currentThread().getName());
            return List.of(reward);
        });
        when(repository.beginDelivery(eq(reward), any())).thenAnswer(call -> {
            attempt.set(call.getArgument(1));
            claimed.countDown();
            return true;
        });
        when(repository.releaseUnstartedDelivery(eq(reward), any())).thenAnswer(call -> {
            assertEquals(attempt.get(), call.getArgument(1));
            released.countDown();
            return true;
        });

        var poll = new AtomicReference<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
            poll.set(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(snapshot);
            service.onAdmission(new AuthAdmissionEvent(snapshot));
            assertNotNull(poll.get());
            verify(repository, never()).readyDeliveries(anySet(), anyInt());
            poll.get().run();
            assertTrue(claimed.await(2, TimeUnit.SECONDS));

            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(AuthSnapshot.absent(id));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (released.getCount() != 0 && System.nanoTime() < deadline) {
                poll.get().run();
                Thread.sleep(2);
            }
            assertEquals(0, released.getCount());
            verify(player, never()).sendMessage(anyString());
            verify(repository, never()).finishDelivery(any(), any(), anyBoolean());
            bukkit.verify(Bukkit::getOnlinePlayers, never());
        } finally {
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void signInResultWaitsForTheBackgroundCommitAndShutdownDrainsIt() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var service = service(repository, null, null);
        var settings = (OnlineRewardsModuleConfiguration) field(service, "configuration");
        var signIn = new OnlineRewardsSignInConfiguration(false, "已签到", "已签到过", "",
            List.of("give {player} apple 1"), List.of(), "", List.of(), null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());
        when(settings.signIn()).thenReturn(signIn);
        var active = OnlineRewardsService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);

        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        var snapshot = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(id)).thenReturn(snapshot);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.commitSignInWithRewards(eq(id), eq("Tester"), anyString(), eq(false), any())).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Write", Thread.currentThread().getName());
            entered.countDown();
            await(release);
            Set<String> dates = Set.of(call.<String>getArgument(2));
            var rewards = call.<Function<Set<String>, List<Reward>>>getArgument(4).apply(dates);
            assertTrue(rewards.contains(new Reward("COMMAND", "give Tester apple 1", "")));
            return new SignInCommit(SignInStatus.APPLIED, dates, 0);
        });

        AuthAccess.install(() -> auth);
        try {
            var result = service.signIn(player).toCompletableFuture();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).shutdown();
            release.countDown();
            assertTrue(result.get(2, TimeUnit.SECONDS).success());
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            verify(repository).shutdown();
        } finally {
            release.countDown();
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            AuthAccess.install(() -> null);
        }
    }

    @Test
    void leaderboardWorkIsDrainedAndFinalWriteFailureIsReported() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var cross = mock(CrossServerAPI.class);
        when(cross.nodeId()).thenReturn("isolated");
        var service = service(repository, cross, null);
        doThrow(new java.sql.SQLException("isolated disconnect")).when(repository).removePresence(eq("isolated"), any(), isNull());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ((ModuleExecutor) field(service, "leaderboardWorker")).execute(() -> { entered.countDown(); await(release); });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).removePresence(anyString(), any(), isNull());
        } finally { release.countDown(); service.shutdown(); }
        var failure = assertThrows(ExecutionException.class,
            () -> service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS));
        assertEquals("isolated disconnect", failure.getCause().getMessage());
        verify(repository).shutdown();
    }

    private OnlineRewardsService service(OnlineRewardsRepository repository, CrossServerAPI cross, TitleGrantable title) {
        var configuration = mock(OnlineRewardsModuleConfiguration.class);
        when(configuration.crossServer()).thenReturn(cross == null
            ? CrossServerChannelConfig.disabled() : CrossServerChannelConfig.enabledDefault());
        return new OnlineRewardsService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), configuration,
            repository, null, null, null, null, null, () -> title, null, null, null, null, null,
            Clock.systemUTC(), cross);
    }

    private Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
}
