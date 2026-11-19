package xuanmo.arcartxsuite.afkreward.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkArea.Point;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.AreaStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.QuitCheckpoint;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class AfkHudUpdatesTest {
    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "mysql", "mysql-with-blocked-shutdown"})
    void retriesNewPlayerStatsAndSavesTrustedAreaOnQuit(String backend, @TempDir Path directory) throws Exception {
        boolean mysql = !backend.equals("sqlite");
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var repository = mock(AfkRewardRepository.class);
        if (mysql) {
            when(repository.getDescriptor()).thenReturn(StorageDescriptor.mysql("localhost", 3306, "test", "", "", 2, ""));
        }
        var world = mock(World.class);
        when(world.getName()).thenReturn("world");
        var player = player(world, "new-player");
        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().toString();
        var location = new AtomicReference<>(new Location(world, 5, 64, 5));
        when(player.getLocation()).thenAnswer(call -> location.get());
        when(player.hasPermission("axs.afkreward.area.lounge")).thenReturn(true);
        when(repository.loadStats(uuid))
            .thenThrow(new SQLException("temporary player read failure"))
            .thenReturn(new PlayerStats("new-player", today, 5, 12, 91));
        when(repository.loadAllAreaStats(uuid))
            .thenThrow(new SQLException("temporary area read failure"))
            .thenReturn(List.of(new AreaStats("lounge", 20, 9, today)));

        var area = new AfkArea("lounge", "lounge", true, "world", "lounge",
            List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)),
            null, false, 1, new AfkArea.RewardConfig(10,
            new AfkArea.RewardConfig.MaxConfig(false, 0), new AfkArea.RewardConfig.PlayerLimitConfig(false, 0),
            new AfkRewardType("lounge", "", Map.of(), Map.of(), List.of())), null);
        Logger logger = Logger.getAnonymousLogger();
        var config = AfkRewardConfiguration.load(new YamlConfiguration(), logger).withAreas(Map.of("lounge", area));
        var service = new AfkRewardService(plugin, config, repository, logger, null, null, null, null, null);
        var scheduler = mock(BukkitScheduler.class);
        var timers = new ArrayList<Runnable>();
        var workers = new ArrayList<Runnable>();
        var callbacks = new ArrayList<Runnable>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            timers.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            workers.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            auth.when(() -> AuthAccess.admitted(uuid)).thenReturn(true);
            service.start();
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            Runnable tick = timers.get(0);
            Runnable checkpoint = timers.get(1);

            tick.run();
            assertEquals(1, service.getState(uuid).seconds);
            verify(repository, never()).loadStats(uuid);
            assertEquals(0, service.getAreaStats(uuid, "lounge").totalSeconds());
            verify(repository, never()).loadAllAreaStats(uuid);
            drain(workers);
            drain(callbacks);
            assertTrue(service.isLoadFailed(uuid));
            checkpoint.run();
            verify(repository, never()).saveStatsBatch(anyMap(), anyList());

            tick.run();
            assertEquals(2, service.getState(uuid).seconds);
            drain(workers);
            drain(callbacks);
            assertFalse(service.isLoadFailed(uuid));
            var expected = new PlayerStats("new-player", today, 5, 12, 93);
            assertEquals(expected, service.getStatsSnapshot(uuid));
            assertEquals(new AreaStats("lounge", 22, 11, today), service.getAreaStats(uuid, "lounge"));
            checkpoint.run();
            verify(repository, timeout(2000)).saveStatsBatch(Map.of(uuid, expected), List.of());
            checkpoint.run();
            tick.run();
            var newer = new PlayerStats("new-player", today, 5, 12, 94);
            drain(workers);
            checkpoint.run();
            verify(repository, timeout(2000)).saveStatsBatch(Map.of(uuid, newer), List.of());
            drain(workers);
            checkpoint.run();
            verify(repository).saveStatsBatch(Map.of(uuid, newer), List.of());
            verify(repository, times(2)).saveStatsBatch(anyMap(), anyList());

            location.set(new Location(world, 50, 64, 50));
            tick.run();
            clearInvocations(repository);
            service.onPlayerQuit(player);
            verifyNoInteractions(repository);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var expectedQuit = List.of(new QuitCheckpoint(uuid, null,
                List.of(new AreaStats("lounge", 23, 12, today)), null));
            if (backend.equals("mysql-with-blocked-shutdown")) {
                var started = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                Thread server = Thread.currentThread();
                doAnswer(call -> {
                    assertNotSame(server, Thread.currentThread());
                    started.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return null;
                }).when(repository).saveQuitCheckpoints(expectedQuit);
                tick.run();
                assertTrue(started.await(1, TimeUnit.SECONDS));
                long start = System.nanoTime();
                try {
                    service.shutdown();
                    assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
                    assertFalse(service.databaseStopped().toCompletableFuture().isDone());
                } finally {
                    release.countDown();
                }
                service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
                verify(repository, atLeastOnce()).saveQuitCheckpoints(expectedQuit);
                return;
            }
            tick.run();
            verify(repository, timeout(2000)).saveQuitCheckpoints(expectedQuit);
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void sendsFinalOccupancyAndRetriesOnlyAnUnsentHide(@TempDir Path directory) throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var repository = mock(AfkRewardRepository.class);
        when(repository.loadStats(any())).thenReturn(new PlayerStats("", "", 0, 0, 0));
        var world = mock(World.class);
        when(world.getName()).thenReturn("world");
        var inside = new Location(world, 5, 64, 5);
        var outside = new Location(world, 50, 64, 50);
        var location = new AtomicReference<>(inside);
        var first = player(world, "first");
        var second = player(world, "second");
        when(first.getLocation()).thenAnswer(call -> location.get());
        when(second.getLocation()).thenReturn(inside);
        when(first.hasPermission("axs.afkreward.area.lounge")).thenReturn(true);
        when(second.hasPermission("axs.afkreward.area.lounge")).thenReturn(true);

        var area = new AfkArea("lounge", "lounge", true, "world", "lounge",
            List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)),
            null, false, 1, new AfkArea.RewardConfig(10,
            new AfkArea.RewardConfig.MaxConfig(false, 0), new AfkArea.RewardConfig.PlayerLimitConfig(false, 0),
            new AfkRewardType("lounge", "", Map.of(), Map.of(), List.of())), null);
        Logger logger = Logger.getAnonymousLogger();
        var config = AfkRewardConfiguration.load(new YamlConfiguration(), logger).withAreas(Map.of("lounge", area));
        var service = new AfkRewardService(plugin, config, repository, logger, null, null, null, null, null);
        var bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        var firstPackets = new ArrayList<Map<String, String>>();
        var hideSucceeds = new AtomicBoolean(false);
        when(bridge.sendPacket(eq(first), eq("AXS:test_hud"), eq("update"), any())).thenAnswer(call -> {
            Map<String, String> payload = call.getArgument(3);
            firstPackets.add(payload);
            return "true".equals(payload.get("visible")) || hideSucceeds.get();
        });
        service.setHudBridge(bridge, "AXS:test_hud");
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayList<Runnable>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, second));
            auth.when(() -> AuthAccess.admitted(any(UUID.class))).thenReturn(true);
            service.start();
            Runnable tick = tasks.get(0);
            tick.run();
            assertEquals("2", firstPackets.get(0).get("players"));

            location.set(outside);
            tick.run();
            assertEquals("false", firstPackets.get(firstPackets.size() - 1).get("visible"));
            hideSucceeds.set(true);
            tick.run();
            assertEquals("false", firstPackets.get(firstPackets.size() - 1).get("visible"));
            tick.run();
            assertEquals(3, firstPackets.size());

            location.set(inside);
            tick.run();
            assertEquals("true", firstPackets.get(firstPackets.size() - 1).get("visible"));
            assertFalse(service.toggleHud(first.getUniqueId()));
            tick.run();
            tick.run();
            assertEquals(5, firstPackets.size());
            assertEquals("false", firstPackets.get(firstPackets.size() - 1).get("visible"));

            assertTrue(service.toggleHud(first.getUniqueId()));
            tick.run();
            assertEquals(6, firstPackets.size());
            assertEquals("2", firstPackets.get(firstPackets.size() - 1).get("players"));
            service.getState(first.getUniqueId()).seconds--;
            tick.run();
            assertEquals(6, firstPackets.size(), "unchanged visible values should not be resent");
            service.onClientInitialized(first);
            service.getState(first.getUniqueId()).seconds--;
            tick.run();
            assertEquals(7, firstPackets.size(), "client initialization should refresh unchanged values");
            service.shutdown();
            assertEquals(8, firstPackets.size());
            assertEquals("false", firstPackets.get(firstPackets.size() - 1).get("visible"));
        }
    }

    private static void drain(List<Runnable> queued) {
        var tasks = List.copyOf(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    private static Player player(World world, String name) {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        return player;
    }
}
