package xuanmo.arcartxsuite.afkreward.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import xuanmo.arcartxsuite.afkreward.model.AfkItemReward;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.QuitCheckpoint;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.SessionRecord;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.util.ItemSerializer;

class AfkSessionLifecycleTest {
    @TempDir Path directory;

    @Test
    void cancelledTeleportLeavesPlayerUnrestrictedAndShutdownRestoresOriginalState() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var repository = mock(AfkRewardRepository.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(repository.loadStats(id)).thenReturn(new PlayerStats("Steve", "", 0, 0, 0));
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        when(player.getWalkSpeed()).thenReturn(0.35f);
        when(player.isCollidable()).thenReturn(true);
        when(player.hasPermission("axs.afkreward.area.lounge")).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(null, 0, 60, 0));

        var configFile = new YamlConfiguration();
        configFile.set("manual.restrict-actions", true);
        configFile.set("manual.protections.collidable", true);
        var destination = new Location(null, 50, 60, 50);
        var area = new AfkArea("lounge", "lounge", true, "world", "lounge", List.of(),
            destination, true, 1, new AfkArea.RewardConfig(1,
            new AfkArea.RewardConfig.MaxConfig(false, 0), new AfkArea.RewardConfig.PlayerLimitConfig(false, 0),
            new AfkRewardType("lounge", "", Map.of(), Map.of(), List.of())), null);
        var logger = Logger.getAnonymousLogger();
        var config = AfkRewardConfiguration.load(configFile, logger).withAreas(Map.of("lounge", area));
        var service = new AfkRewardService(plugin, config, repository, logger, null, null, null, null, null);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(mock(BukkitTask.class));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            when(player.teleport(destination)).thenAnswer(call -> {
                assertFalse(service.isInManualAfk(id), "own teleport protection must not cancel the start");
                return false;
            });
            service.start();
            finishRecovery(service, plugin, scheduler, player);
            finishManualCommand(plugin, scheduler, player, () -> service.startManualAfk(player, "lounge"));
            assertFalse(service.isInManualAfk(id));
            verify(player, never()).setWalkSpeed(anyFloat());
            verify(player, never()).setCollidable(anyBoolean());

            verify(repository, timeout(3000)).removeUnstartedManualSession(any(SessionRecord.class));
            finishRecovery(service, plugin, scheduler, player);
            doReturn(true).when(player).teleport(destination);
            finishManualCommand(plugin, scheduler, player, () -> service.startManualAfk(player, "lounge"));
            assertTrue(service.isInManualAfk(id));
            verify(player).setWalkSpeed(0.0001f);
            verify(player).setCollidable(false);

            service.shutdown();
            assertFalse(service.isInManualAfk(id));
            verify(player).setWalkSpeed(0.35f);
            verify(player).setCollidable(true);
        }
    }

    @Test
    void dbOutageDuringEndPreservesLatestSecondsUntilReconnect() throws Exception {
        var fixture = new Fixture();
        AtomicBoolean databaseOnline = new AtomicBoolean(true);
        AtomicReference<SessionRecord> stored = new AtomicReference<>();
        doAnswer(call -> {
            if (!databaseOnline.get()) throw new SQLException("offline");
            for (QuitCheckpoint checkpoint : call.<List<QuitCheckpoint>>getArgument(0)) {
                if (checkpoint.session() != null) stored.set(checkpoint.session());
            }
            return null;
        }).when(fixture.repository).saveQuitCheckpoints(anyList());
        doAnswer(call -> {
            if (!databaseOnline.get()) throw new SQLException("offline");
            stored.set(call.getArgument(0));
            return null;
        }).when(fixture.repository).startManualSession(any(SessionRecord.class));
        when(fixture.repository.refreshManualCheckpoint(any(SessionRecord.class))).thenAnswer(call -> {
            if (!databaseOnline.get()) throw new SQLException("offline");
            stored.set(call.getArgument(0));
            return true;
        });
        when(fixture.repository.loadSession(fixture.id)).thenAnswer(call -> stored.get());
        when(fixture.repository.beginManualSettlement(any(SessionRecord.class), any(UUID.class))).thenAnswer(call ->
            stored.compareAndSet(call.getArgument(0), null));

        try (var bukkit = mockStatic(Bukkit.class)) {
            fixture.stubBukkit(bukkit);
            fixture.service.start();
            fixture.finishRecovery();
            fixture.startManual();
            fixture.service.getState(fixture.id).seconds = 125;
            databaseOnline.set(false);
            fixture.endManual();
            assertFalse(fixture.service.isInManualAfk(fixture.id));
            assertEquals(0, stored.get().startSeconds());
            fixture.service.onPlayerQuit(fixture.player);

            databaseOnline.set(true);
            var schedule = AfkRewardService.class.getDeclaredMethod("scheduleQuitCheckpoints");
            schedule.setAccessible(true);
            var write = AfkRewardService.class.getDeclaredField("quitWrite");
            write.setAccessible(true);
            schedule.invoke(fixture.service);
            assertEquals(true, ((CompletableFuture<?>) write.get(fixture.service)).get(3, TimeUnit.SECONDS));
            schedule.invoke(fixture.service);
            fixture.finishRecovery();
            verify(fixture.repository).beginManualSettlement(argThat(record -> record.startSeconds() == 125), any(UUID.class));
            assertNull(stored.get());
            fixture.service.shutdown();
            fixture.service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void recoveryRestoresOnlyMovementFieldsRecordedBeforeTheCrash() throws Exception {
        var fixture = new Fixture();
        var saved = new SessionRecord(fixture.id, "Steve", "lounge", "lounge", "MANUAL",
            0, 10, 0, 0, "", 0, 0.35f, true);
        var legacy = new SessionRecord(fixture.id, "Steve", "lounge", "lounge", "MANUAL",
            0, 20, 0, 0, "", 0, null, null);
        when(fixture.repository.loadSession(fixture.id)).thenReturn(saved, legacy);
        when(fixture.repository.beginManualSettlement(any(SessionRecord.class), any(UUID.class))).thenReturn(true);
        when(fixture.player.getWalkSpeed()).thenReturn(0.0001f);
        when(fixture.player.isCollidable()).thenReturn(false);

        try (var bukkit = mockStatic(Bukkit.class)) {
            fixture.stubBukkit(bukkit);
            fixture.service.start();
            fixture.finishRecovery();
            verify(fixture.player).setWalkSpeed(0.35f);
            verify(fixture.player).setCollidable(true);
            fixture.service.onPlayerQuit(fixture.player);
            clearInvocations(fixture.player);
            fixture.finishRecovery();
            verify(fixture.player, never()).setWalkSpeed(anyFloat());
            verify(fixture.player, never()).setCollidable(anyBoolean());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void manualSettlementWaitsForTheCommandReceipt(boolean commandSucceeded) throws Exception {
        var fixture = new Fixture(Map.of("default", List.of("testreward {player}")));
        var callback = new AtomicReference<Consumer<ThrottledCommandExecutor.Result>>();
        when(fixture.repository.refreshManualCheckpoint(any(SessionRecord.class))).thenReturn(true);
        when(fixture.repository.beginManualSettlement(any(SessionRecord.class), any(UUID.class))).thenReturn(true);

        try (var bukkit = mockStatic(Bukkit.class); var commands = mockStatic(ThrottledCommandExecutor.class)) {
            fixture.stubBukkit(bukkit);
            commands.when(() -> ThrottledCommandExecutor.dispatchWithResult(eq("testreward Steve"), any(), any()))
                .thenAnswer(call -> {
                    callback.set(call.getArgument(2));
                    return null;
                });
            fixture.service.start();
            fixture.finishRecovery();
            fixture.startManual();
            fixture.service.getState(fixture.id).seconds = 60;
            fixture.endManual();
            assertNotNull(callback.get());
            verify(fixture.repository, never()).completeManualSettlement(any(), anyInt());
            verify(fixture.repository, never()).flagManualSettlementForReview(any());
            var order = inOrder(fixture.player, fixture.repository);
            order.verify(fixture.player).saveData();
            order.verify(fixture.repository).beginManualSettlement(any(), any(UUID.class));

            callback.get().accept(commandSucceeded ? ThrottledCommandExecutor.Result.SUCCESS
                : ThrottledCommandExecutor.Result.UNCONFIRMED);
            order.verify(fixture.player).saveData();
            fixture.service.shutdown();
            fixture.service.databaseStopped().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            order.verify(fixture.repository).saveStatsBatch(argThat(stats ->
                stats.containsKey(fixture.id) && stats.get(fixture.id).totalCount() == 1));
            if (commandSucceeded) {
                order.verify(fixture.repository).completeManualSettlement(any(SessionRecord.class), eq(1));
                verify(fixture.repository, never()).flagManualSettlementForReview(any());
            } else {
                order.verify(fixture.repository).flagManualSettlementForReview(any(SessionRecord.class));
                verify(fixture.repository, never()).completeManualSettlement(any(), anyInt());
            }
        }
    }

    @Test
    void endingManualAfkHandsItemsOffOnTheWriterWithoutAnExtraPlayerSave() throws Exception {
        var fixture = new Fixture(Map.of(), Map.of("default", List.of(new AfkItemReward("minecraft", "COD", 2, null))));
        var pending = mock(PendingRewardService.class);
        var sources = mock(ItemSourceRegistry.class);
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.COD);
        when(stack.getAmount()).thenReturn(2);
        when(sources.generateItem("minecraft", "COD", 2, null, fixture.player)).thenReturn(stack);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Thread primary = Thread.currentThread();
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            assertNotSame(primary, Thread.currentThread());
            assertTrue(call.<String>getArgument(0).endsWith(":items"));
            List<PendingRewardService.PendingReward> rewards = call.getArgument(1);
            assertEquals(1, rewards.size());
            assertEquals(fixture.id, rewards.get(0).playerUuid());
            assertTrue(rewards.get(0).payload().contains("AQID"));
            entered.countDown();
            return release.await(5, TimeUnit.SECONDS);
        });
        fixture.service.setPendingRewardService(pending);
        fixture.service.setItemSourceRegistry(sources);
        when(fixture.repository.refreshManualCheckpoint(any(SessionRecord.class))).thenReturn(true);
        when(fixture.repository.beginManualSettlement(any(SessionRecord.class), any(UUID.class))).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var serialize = mockStatic(ItemSerializer.class)) {
            fixture.stubBukkit(bukkit);
            serialize.when(() -> ItemSerializer.serialize(stack)).thenReturn(new byte[]{1, 2, 3});
            fixture.service.start();
            fixture.finishRecovery();
            fixture.startManual();
            fixture.service.getState(fixture.id).seconds = 60;
            fixture.endManual();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            verify(fixture.repository, never()).completeManualSettlement(any(), anyInt());
            verify(fixture.player, times(1)).saveData();
            fixture.service.shutdown();
        } finally { release.countDown(); }
        fixture.service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void recoveryDoesNotDispatchWhenAnotherAttemptClaimsCheckpoint() throws Exception {
        var fixture = new Fixture(Map.of("default", List.of("testreward {player}")));
        var saved = new SessionRecord(fixture.id, "Steve", "lounge", "lounge", "MANUAL",
            60, 10, 0, 0, "", 60, 0.35f, true);
        when(fixture.repository.loadSession(fixture.id)).thenReturn(saved);
        when(fixture.repository.beginManualSettlement(eq(saved), any(UUID.class))).thenReturn(false);

        try (var bukkit = mockStatic(Bukkit.class); var commands = mockStatic(ThrottledCommandExecutor.class)) {
            fixture.stubBukkit(bukkit);
            fixture.service.start();
            fixture.finishRecovery();
            var order = inOrder(fixture.player, fixture.repository);
            order.verify(fixture.player).saveData();
            order.verify(fixture.repository).beginManualSettlement(eq(saved), any(UUID.class));
            commands.verifyNoInteractions();
            verify(fixture.repository, never()).completeManualSettlement(any(), anyInt());
            fixture.service.shutdown();
        }
    }

    @Test
    void manualCommandWaitsForTheExistingBackgroundRecovery() throws Exception {
        var fixture = new Fixture();
        var saved = new SessionRecord(fixture.id, "Steve", "lounge", "lounge", "MANUAL",
            0, 10, 0, 0, "", 15, 0.35f, true);
        when(fixture.repository.loadSession(fixture.id)).thenReturn(saved);
        when(fixture.repository.beginManualSettlement(eq(saved), any(UUID.class))).thenReturn(true);
        when(fixture.player.getWalkSpeed()).thenReturn(0.0001f);
        when(fixture.player.isCollidable()).thenReturn(false);
        var world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(fixture.player.getWorld()).thenReturn(world);
        var timers = new ArrayDeque<Runnable>();
        var workers = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        when(fixture.scheduler.runTaskTimer(eq(fixture.plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(call -> enqueue(timers, call.getArgument(1)));
        when(fixture.scheduler.runTaskAsynchronously(eq(fixture.plugin), any(Runnable.class)))
            .thenAnswer(call -> enqueue(workers, call.getArgument(1)));
        when(fixture.scheduler.runTask(eq(fixture.plugin), any(Runnable.class)))
            .thenAnswer(call -> enqueue(callbacks, call.getArgument(1)));

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            fixture.stubBukkit(bukkit);
            auth.when(() -> AuthAccess.admitted(fixture.id)).thenReturn(true);
            fixture.service.start();
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.player));
            timers.element().run();
            verify(fixture.repository, never()).loadSession(fixture.id);
            while (!workers.isEmpty()) workers.remove().run();
            verify(fixture.repository).loadSession(fixture.id);
            verify(fixture.repository, never()).beginManualSettlement(eq(saved), any(UUID.class));

            assertFalse(fixture.service.startManualAfk(fixture.player, "missing"));
            verify(fixture.repository, never()).beginManualSettlement(eq(saved), any(UUID.class));
            callbacks.remove().run();
            runPendingStatsReads(fixture.service);
            while (!workers.isEmpty()) workers.remove().run();
            callbacks.remove().run();
            assertTrue(workers.isEmpty());
            assertEquals(15, fixture.service.getStatsSnapshot(fixture.id).totalSeconds());
            verify(fixture.repository).beginManualSettlement(eq(saved), any(UUID.class));
            verify(fixture.player).setWalkSpeed(0.35f);
            verify(fixture.player).setCollidable(true);
            fixture.service.shutdown();
        }
    }

    @Test
    void recoveredCheckpointRetainsNewRegionSecondsAccruedDuringRead() throws Exception {
        var fixture = new Fixture();
        var world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(fixture.player.getWorld()).thenReturn(world);
        when(fixture.player.getLocation()).thenReturn(new Location(world, 5, 64, 5));
        when(fixture.repository.loadStats(fixture.id)).thenReturn(new PlayerStats("Steve", "", 0, 0, 100));
        when(fixture.repository.loadAreaStats(fixture.id, "lounge"))
            .thenReturn(new AfkRewardRepository.AreaStats("lounge", 0, 0, ""));
        var saved = new SessionRecord(fixture.id, "Steve", "lounge", "lounge", "MANUAL",
            0, 10, 0, 0, "", 140, null, null);
        when(fixture.repository.loadSession(fixture.id)).thenReturn(saved);
        when(fixture.repository.beginManualSettlement(eq(saved), any(UUID.class))).thenReturn(true);
        var area = new AfkArea("lounge", "lounge", true, "world", "lounge",
            List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)),
            null, false, 1, new AfkArea.RewardConfig(1,
            new AfkArea.RewardConfig.MaxConfig(false, 0), new AfkArea.RewardConfig.PlayerLimitConfig(false, 0),
            new AfkRewardType("lounge", "", Map.of(), Map.of(), List.of())), null);
        var logger = Logger.getAnonymousLogger();
        var config = AfkRewardConfiguration.load(new YamlConfiguration(), logger).withAreas(Map.of("lounge", area));
        var service = new AfkRewardService(fixture.plugin, config, fixture.repository, logger, null, null, null, null, null);
        var timers = new ArrayDeque<Runnable>();
        var workers = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        when(fixture.scheduler.runTaskTimer(eq(fixture.plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(call -> enqueue(timers, call.getArgument(1)));
        when(fixture.scheduler.runTaskAsynchronously(eq(fixture.plugin), any(Runnable.class)))
            .thenAnswer(call -> enqueue(workers, call.getArgument(1)));
        when(fixture.scheduler.runTask(eq(fixture.plugin), any(Runnable.class)))
            .thenAnswer(call -> enqueue(callbacks, call.getArgument(1)));

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            fixture.stubBukkit(bukkit);
            auth.when(() -> AuthAccess.admitted(fixture.id)).thenReturn(true);
            service.start();
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.player));
            timers.element().run();
            assertEquals(1, service.getState(fixture.id).seconds);
            while (!workers.isEmpty()) workers.remove().run();
            callbacks.remove().run();
            runPendingStatsReads(service);
            while (!workers.isEmpty()) workers.remove().run();
            callbacks.remove().run();
            assertEquals(141, service.getStatsSnapshot(fixture.id).totalSeconds());
            assertEquals(1, service.getState(fixture.id).seconds);
            timers.element().run();
            assertEquals(142, service.getStatsSnapshot(fixture.id).totalSeconds());
            verify(fixture.repository).beginManualSettlement(eq(saved), any(UUID.class));
            service.shutdown();
        }
    }

    private static BukkitTask enqueue(ArrayDeque<Runnable> tasks, Runnable task) {
        tasks.add(task);
        return mock(BukkitTask.class);
    }

    private static void runPendingStatsReads(AfkRewardService service) throws Exception {
        var batch = AfkRewardService.class.getDeclaredMethod("scheduleStatsReads");
        batch.setAccessible(true);
        batch.invoke(service);
    }

    private static void finishRecovery(AfkRewardService service, JavaPlugin plugin, BukkitScheduler scheduler,
                                       Player player) throws Exception {
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        try (var auth = mockStatic(AuthAccess.class)) {
            UUID id = player.getUniqueId();
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            assertFalse(service.startManualAfk(player, "missing"));
            runPendingStatsReads(service);
            runPendingStatsReads(service);
        }
    }

    private static void finishManualCommand(JavaPlugin plugin, BukkitScheduler scheduler, Player player,
                                            java.util.function.BooleanSupplier command) throws Exception {
        var completed = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        Thread main = Thread.currentThread();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            Runnable callback = call.getArgument(1);
            if (Thread.currentThread() == main) callback.run();
            else completed.add(callback);
            return mock(BukkitTask.class);
        });
        try (var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.admitted(player.getUniqueId())).thenReturn(true);
            assertTrue(command.getAsBoolean());
            Runnable callback = completed.poll(3, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(callback);
            callback.run();
        }
    }

    private final class Fixture {
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final AfkRewardRepository repository = mock(AfkRewardRepository.class);
        final Player player = mock(Player.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final UUID id = UUID.randomUUID();
        final AfkRewardService service;

        Fixture() throws Exception {
            this(Map.of());
        }

        Fixture(Map<String, List<String>> tierCommands) throws Exception {
            this(tierCommands, Map.of());
        }

        Fixture(Map<String, List<String>> tierCommands, Map<String, List<AfkItemReward>> tierItems) throws Exception {
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn("Steve");
            when(player.isOnline()).thenReturn(true);
            when(player.getWalkSpeed()).thenReturn(0.35f);
            when(player.isCollidable()).thenReturn(true);
            when(player.hasPermission("axs.afkreward.area.lounge")).thenReturn(true);
            when(player.getLocation()).thenReturn(new Location(null, 0, 60, 0));
            var destination = new Location(null, 50, 60, 50);
            when(player.teleport(destination)).thenReturn(true);
            when(repository.loadStats(id)).thenReturn(new PlayerStats("Steve", "", 0, 0, 0));
            var configFile = new YamlConfiguration();
            configFile.set("manual.restrict-actions", true);
            configFile.set("manual.protections.collidable", true);
            var area = new AfkArea("lounge", "lounge", true, "world", "lounge", List.of(),
                destination, true, 1, new AfkArea.RewardConfig(1,
                    new AfkArea.RewardConfig.MaxConfig(false, 0), new AfkArea.RewardConfig.PlayerLimitConfig(false, 0),
                    new AfkRewardType("lounge", "", new java.util.LinkedHashMap<>(tierCommands),
                        new java.util.LinkedHashMap<>(tierItems), List.of())),
                new AfkArea.MultiplierConfig(false, 1.0, 1.0, "max", List.of()));
            var logger = Logger.getAnonymousLogger();
            var config = AfkRewardConfiguration.load(configFile, logger).withAreas(Map.of("lounge", area));
            service = new AfkRewardService(plugin, config, repository, logger, null, null, null, null, null);
        }

        void stubBukkit(org.mockito.MockedStatic<Bukkit> bukkit) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        }

        void finishRecovery() throws Exception {
            AfkSessionLifecycleTest.finishRecovery(service, plugin, scheduler, player);
        }

        void startManual() throws Exception {
            finishManualCommand(plugin, scheduler, player, () -> service.startManualAfk(player, "lounge"));
        }

        void endManual() throws Exception {
            finishManualCommand(plugin, scheduler, player, () -> service.endManualAfk(player, false));
        }
    }
}
