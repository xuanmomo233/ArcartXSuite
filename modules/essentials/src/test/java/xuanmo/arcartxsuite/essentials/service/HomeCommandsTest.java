package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

class HomeCommandsTest {
    @Test void aPendingTpaLookupCannotRecreateARequestAfterCancelOrQuit() throws Exception {
        for (boolean quit : new boolean[]{false, true}) try (var harness = new Harness(false, true)) {
            var target = mock(Player.class); UUID targetId = UUID.randomUUID();
            when(target.getUniqueId()).thenReturn(targetId); when(target.isOnline()).thenReturn(true);
            when(target.getName()).thenReturn("Target"); when(target.hasPermission("axs.essentials.tpa")).thenReturn(true);
            harness.bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
            harness.admit(targetId, 1);
            when(harness.repository.receivesTpa(targetId)).thenReturn(true);
            harness.service.sendTpa(harness.player, target, false);
            if (quit) harness.service.onQuit(new PlayerQuitEvent(target, ""));
            else harness.service.cancelTpa(harness.player, "");
            harness.finishJob(); harness.replies.remove().run();
            assertTrue(harness.service.tpaRequests(target).isEmpty());
            verify(target, never()).sendMessage(anyString());
        }
    }

    @Test void disabledHomeActionsDoNotPrefetchCompletionDataOnJoin() {
        try (var harness = new Harness(true)) {
            harness.service.onFirstJoin(new org.bukkit.event.player.PlayerJoinEvent(harness.player, ""));
            assertTrue(harness.service.getHomeNames(harness.player).isEmpty());
            assertTrue(harness.jobs.isEmpty());
            verifyNoInteractions(harness.repository);
        }
    }

    @Test void homeWritesCaptureBukkitStateBeforeQueuingAndOnlyConfirmAfterTheWrite() throws Exception {
        try (var harness = new Harness()) {
            Thread main = Thread.currentThread();
            when(harness.repository.setHome(eq(harness.id), eq("Home"), any(), eq(3))).thenAnswer(call -> {
                assertNotSame(main, Thread.currentThread());
                assertEquals(new EssentialsRepository.Position("world", 1, 70, 3, 0, 0), call.getArgument(2));
                return new EssentialsRepository.HomeSave(true, List.of("home"));
            });
            harness.service.setHome(harness.player, "Home");
            verifyNoInteractions(harness.repository);
            verify(harness.player, never()).sendMessage(anyString());
            harness.finishJob();
            verify(harness.player, never()).sendMessage(anyString());
            harness.replies.remove().run();
            verify(harness.player).sendMessage(contains("Home"));
            assertEquals(List.of("home"), harness.service.getHomeNames(harness.player));
            assertTrue(harness.jobs.isEmpty());
        }
    }

    @Test void aLateHomeReadCannotTeleportAfterPermissionIsRevoked() throws Exception {
        try (var harness = new Harness()) {
            when(harness.repository.getHome(harness.id, "home")).thenReturn(new EssentialsRepository.Position("world", 1, 70, 3, 0, 0));
            harness.service.teleportHome(harness.player, "home");
            harness.finishJob();
            when(harness.player.hasPermission("axs.essentials.home")).thenReturn(false);
            harness.replies.remove().run();
            verify(harness.player, never()).teleport(any(Location.class));
            verify(harness.player, never()).sendMessage(anyString());
        }
    }

    @Test void aHomeReadCannotTeleportAfterAdmissionChangesOnTheSameConnection() throws Exception {
        try (var harness = new Harness()) {
            when(harness.repository.getHome(harness.id, "home"))
                .thenReturn(new EssentialsRepository.Position("world", 1, 70, 3, 0, 0));
            harness.service.teleportHome(harness.player, "home");
            harness.finishJob();
            harness.admit(harness.id, 2);
            harness.replies.remove().run();
            verify(harness.player, never()).teleport(any(Location.class));
            verify(harness.player, never()).sendMessage(anyString());
        }
    }

    @Test void completionReadsOncePerSessionAndDoesNotRepopulateAfterQuit() throws Exception {
        try (var harness = new Harness()) {
            when(harness.repository.listHomeNames(harness.id)).thenReturn(List.of("first"));
            assertTrue(harness.service.getHomeNames(harness.player).isEmpty());
            assertTrue(harness.service.getHomeNames(harness.player).isEmpty());
            assertEquals(1, harness.jobs.size());
            harness.finishJob();
            harness.service.onQuit(new PlayerQuitEvent(harness.player, ""));
            harness.replies.remove().run();
            assertTrue(harness.service.getHomeNames(harness.player).isEmpty());
            assertEquals(1, harness.jobs.size());
            harness.finishJob(); harness.replies.remove().run();
            assertEquals(List.of("first"), harness.service.getHomeNames(harness.player));
            assertTrue(harness.jobs.isEmpty());
            verify(harness.repository, times(2)).listHomeNames(harness.id);
        }
    }

    private static final class Harness implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final EssentialsRepository repository = mock(EssentialsRepository.class);
        final EssentialsWork work = mock(EssentialsWork.class);
        final Queue<Runnable> jobs = new ConcurrentLinkedQueue<>(), replies = new ConcurrentLinkedQueue<>();
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final org.mockito.MockedStatic<AuthAccess> auth = mockStatic(AuthAccess.class);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final TeleportService service;

        Harness() { this(false); }

        Harness(boolean disabled) { this(disabled, false); }

        Harness(boolean disabled, boolean tpa) {
            admit(id, 1);
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission(anyString())).thenReturn(false);
            for (String action : List.of("home", "sethome", "delhome", "tpa")) when(player.hasPermission("axs.essentials." + action)).thenReturn(true);
            when(world.getName()).thenReturn("world");
            when(player.getLocation()).thenReturn(new Location(world, 1, 70, 3));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(work.submit(any())).thenAnswer(call -> {
                Callable<?> operation = call.getArgument(0);
                var future = new CompletableFuture<>();
                jobs.add(() -> { try { future.complete(operation.call()); } catch (Exception failure) { future.completeExceptionally(failure); } });
                return future;
            });
            when(work.read(any())).thenAnswer(call -> work.submit(call.getArgument(0)));
            doAnswer(call -> { replies.add(call.getArgument(0)); return null; }).when(work).onMain(any());
            var yaml = new YamlConfiguration(); yaml.set("tpa.enabled", tpa); yaml.set("spawn.respawn-at-spawn", false);
            yaml.set("spawn.first-join-at-spawn", false);
            yaml.set("unstuck.enabled", false);
            if (disabled) yaml.set("disabled-actions", List.of("home", "delhome"));
            var config = EssentialsConfiguration.load(yaml, Logger.getAnonymousLogger());
            var players = mock(PlayerManagementService.class); when(players.canSee(any(), any())).thenReturn(true);
            service = new TeleportService(mock(JavaPlugin.class), config, repository, players,
                Logger.getAnonymousLogger(), mock(WarpDirectory.class), () -> null, () -> null, work);
            service.start();
        }

        void admit(UUID id, long generation) {
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(new AuthSnapshot(id, id, generation, id,
                AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "server", Set.of(), ""));
        }

        void finishJob() throws Exception { worker.submit(jobs.remove()).get(5, TimeUnit.SECONDS); }
        @Override public void close() { service.shutdown(); worker.shutdownNow(); auth.close(); bukkit.close(); }
    }
}
