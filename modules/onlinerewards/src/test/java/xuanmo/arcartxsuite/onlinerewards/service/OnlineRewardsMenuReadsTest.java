package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInGoalStatus;

class OnlineRewardsMenuReadsTest {
    @TempDir Path temp;

    @Test
    void menuCoalescesPageChangesAndDropsLateResponsesAfterPermissionOrSessionChanges() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var bridge = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        var admitted = new AtomicReference<>(session(playerId, 1));
        var auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(playerId)).thenAnswer(call -> admitted.get());
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("arcartxsuite.onlinerewards.use")).thenReturn(true);
        var signIn = Files.createFile(temp.resolve("sign-in.yml"));
        var config = OnlineRewardsModuleConfiguration.load(new YamlConfiguration(), signIn.toFile(), null);
        String uiId = config.ui().menuUiIds().get(0);
        String packetId = config.ui().packetId();
        when(bridge.isUiOpen(player, uiId)).thenReturn(true);
        when(bridge.openUiAll(player, List.of(uiId))).thenReturn(true);
        var packets = new ArrayList<Map<?, ?>>();
        when(bridge.sendPacketToAll(eq(player), eq(List.of(uiId)), anyString(), any())).thenAnswer(call -> {
            packets.add(call.getArgument(3));
            return true;
        });

        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadState(playerId)).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Menu", Thread.currentThread().getName());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new OnlineRewardsPlayerState();
        });
        when(repository.loadAllSignInDates(playerId)).thenReturn(Set.of("2026-11-18", "2026-11-16"));
        when(repository.signInGoalStatus("2026-11-18")).thenReturn(new SignInGoalStatus(12, Set.of()));

        var completedOnMain = new LinkedBlockingQueue<Runnable>();
        var service = new OnlineRewardsService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), config,
            repository, null, bridge, null, null, null, null, null, null, null, null, null,
             Clock.fixed(Instant.parse("2026-11-18T06:00:00Z"), ZoneOffset.ofHours(8)), null, () -> false) {
            @Override public void runOnMain(Runnable action) { completedOnMain.add(action); }
        };
        var active = OnlineRewardsService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        var runtimeIds = OnlineRewardsService.class.getDeclaredField("runtimeMenuUiIds");
        runtimeIds.setAccessible(true);
        runtimeIds.set(service, List.of(uiId));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            AuthAccess.install(() -> auth);
            assertTrue(service.openMenu(player).success());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(service.handleClientPacket(player, packetId, List.of("calendar_next")));
            assertTrue(service.handleClientPacket(player, packetId, List.of("preview_day", "2026-11-16")));
            assertTrue(packets.isEmpty());
            release.countDown();
            runCallback(completedOnMain);
            assertEquals(1, packets.size());
            assertEquals("2026-11-16", packets.get(0).get("selectedDateText"));
            assertEquals(true, packets.get(0).get("signedToday"));
            verify(bridge).sendPacketToAll(eq(player), eq(List.of(uiId)), eq("init"), any());
            verify(repository).loadState(playerId);
            verify(repository).loadAllSignInDates(playerId);
            verify(repository).signInGoalStatus("2026-11-18");
            verify(repository, never()).hasSignInRecord(any(), anyString());

            assertTrue(service.handleClientPacket(player, packetId, List.of("refresh")));
            Runnable afterPermissionChange = completedOnMain.poll(3, TimeUnit.SECONDS);
            assertNotNull(afterPermissionChange);
            when(player.hasPermission("arcartxsuite.onlinerewards.use")).thenReturn(false);
            afterPermissionChange.run();
            assertEquals(1, packets.size());

            when(player.hasPermission("arcartxsuite.onlinerewards.use")).thenReturn(true);
            assertTrue(service.handleClientPacket(player, packetId, List.of("refresh")));
            Runnable afterSwitch = completedOnMain.poll(3, TimeUnit.SECONDS);
            assertNotNull(afterSwitch);
            admitted.set(session(playerId, 2));
            afterSwitch.run();
            assertEquals(1, packets.size());
        } finally {
            release.countDown();
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AuthAccess.install(() -> null);
        }
    }

    @Test
    void differentPlayersShareGoalCountsUntilTheDateOrRevisionChanges() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        when(repository.signInGoalStatus(anyString())).thenReturn(new SignInGoalStatus(5, Set.of("goal")));
        var reads = new OnlineRewardsMenuReads(repository);
        String day = "2026-11-18";
        try (reads) {
            var first = reads.load(UUID.randomUUID(), false, false, day, () -> true).toCompletableFuture().get(3, TimeUnit.SECONDS);
            var second = reads.load(UUID.randomUUID(), false, false, day, () -> true).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertSame(first.goals(), second.goals());
            verify(repository).signInGoalStatus(day);
            reads.invalidateGoals();
            var changed = reads.load(UUID.randomUUID(), false, false, day, () -> true).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertNotEquals(first.goals().revision(), changed.goals().revision());
            verify(repository, times(2)).signInGoalStatus(day);
            var nextDay = "2026-11-19";
            reads.load(UUID.randomUUID(), false, false, nextDay, () -> true).toCompletableFuture().get(3, TimeUnit.SECONDS);
            verify(repository).signInGoalStatus(nextDay);
        }
        reads.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
    }

    @Test
    void repeatedColdPlaceholderRequestsNeverWaitForStorageAndWarmOnce() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var reads = new OnlineRewardsMenuReads(repository);
        var display = new OnlineRewardsPlaceholderReads(repository, reads);
        UUID id = UUID.randomUUID();
        String day = "2026-11-18";
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadState(id)).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Menu", Thread.currentThread().getName());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            var row = new OnlineRewardsPlayerState();
            row.setOnlineMinutes(27);
            return row;
        });
        when(repository.hasSignInRecord(id, day)).thenReturn(true);
        try (reads) {
            assertNull(display.player(id, day));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) assertNull(display.player(id, day));
            release.countDown();
            reads.query(() -> null).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(27, display.player(id, day).state().onlineMinutes());
            assertTrue(display.player(id, day).signedToday());
            verify(repository).loadState(id);
            verify(repository).hasSignInRecord(id, day);
        } finally {
            release.countDown();
        }
        reads.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
    }

    @Test
    void publicQueriesShareAnInFlightReadAndRejectNewRequestsAfterShutdown() throws Exception {
        var repository = mock(OnlineRewardsRepository.class);
        var config = OnlineRewardsModuleConfiguration.load(new YamlConfiguration(), Files.createFile(temp.resolve("sign-in.yml")).toFile(), null);
        var service = new OnlineRewardsService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), config,
            repository, null, null, null, null, null, null, null, null, null, null, null,
             Clock.fixed(Instant.parse("2026-11-18T06:00:00Z"), ZoneOffset.ofHours(8)), null, () -> false);
        var active = OnlineRewardsService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        UUID id = UUID.randomUUID();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.loadState(id)).thenAnswer(call -> {
            assertEquals("AXS-OnlineRewards-Menu", Thread.currentThread().getName());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            var row = new OnlineRewardsPlayerState();
            row.setRewardDate("2026-11-18");
            row.setOnlineMinutes(12);
            row.setWeekKey("2026-W47");
            row.setWeekMinutes(38);
            row.setMonthKey("2026-10");
            row.setMonthMinutes(50);
            return row;
        });
        when(repository.hasSignInRecord(id, "2026-11-18"))
            .thenReturn(true).thenThrow(new SQLException("unavailable"), new NoClassDefFoundError("JDBC dependency"));
        try {
            assertTrue(service.hasSignedTodayPublic(id).toCompletableFuture().get(3, TimeUnit.SECONDS));
            var unknown = service.hasSignedTodayPublic(id).toCompletableFuture();
            assertInstanceOf(SQLException.class, assertThrows(ExecutionException.class, () -> unknown.get(3, TimeUnit.SECONDS)).getCause());
            var driverFailure = service.hasSignedTodayPublic(id).toCompletableFuture();
            assertInstanceOf(NoClassDefFoundError.class,
                assertThrows(ExecutionException.class, () -> driverFailure.get(3, TimeUnit.SECONDS)).getCause());

            var daily = service.todayOnlineMinutesPublic(id).toCompletableFuture();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var weekly = service.weeklyOnlineMinutesPublic(id).toCompletableFuture();
            var monthly = service.monthlyOnlineMinutesPublic(id).toCompletableFuture();
            assertFalse(daily.isDone());
            service.shutdown();
            var rejected = service.todayOnlineMinutesPublic(id).toCompletableFuture();
            assertTrue(rejected.isCompletedExceptionally());
            assertInstanceOf(RejectedExecutionException.class,
                assertThrows(ExecutionException.class, rejected::get).getCause());
            release.countDown();
            assertEquals(12, daily.get(3, TimeUnit.SECONDS));
            assertEquals(38, weekly.get(3, TimeUnit.SECONDS));
            assertEquals(0, monthly.get(3, TimeUnit.SECONDS));
            verify(repository).loadState(id);
        } finally {
            release.countDown();
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static AuthSnapshot session(UUID id, long generation) {
        return new AuthSnapshot(id, UUID.randomUUID(), generation, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }

    private static void runCallback(LinkedBlockingQueue<Runnable> tasks) throws InterruptedException {
        Runnable next = tasks.poll(3, TimeUnit.SECONDS);
        assertNotNull(next);
        next.run();
    }
}
