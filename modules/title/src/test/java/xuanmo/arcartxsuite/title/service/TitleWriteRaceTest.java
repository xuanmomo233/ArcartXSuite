package xuanmo.arcartxsuite.title.service;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.placeholder.TitlePlaceholderExpansion;
import xuanmo.arcartxsuite.title.storage.TitleRepository;
import xuanmo.arcartxsuite.title.storage.TitleRepository.GrantedTitle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitleWriteRaceTest {
    @TempDir java.nio.file.Path folder;

    @BeforeEach void admitFixturePlayers() {
        var runtime = mock(AuthRuntimeCapability.class);
        when(runtime.snapshot(any(UUID.class))).thenAnswer(call -> {
            UUID player = call.getArgument(0);
            return new AuthSnapshot(player, player, 1, player, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "fixture", Set.of(), "");
        });
        AuthAccess.install(() -> runtime);
    }

    @AfterEach void clearFixtureAdmission() {
        AuthAccess.install(() -> null);
    }

    @Test void placeholdersReuseTheViewAndReactToPermissionAndTimeBoundaries() throws Exception {
        var taskQueue = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            taskQueue.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        UUID id = UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("title.main")).thenReturn(true);
        var now = Instant.parse("2026-11-18T12:00:00Z");
        var activates = now.plusSeconds(10);
        var expires = now.plusSeconds(20);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        var repository = mock(TitleRepository.class);
        var ownedHero = new PlayerOwnedTitle("hero", false, now, null, expires, now, "test");
        var pending = new PlayerOwnedTitle("later", true, now, activates, null, now, "test");
        var stored = new PlayerTitleState(id, Map.of("main", "hero"), Map.of("hero", ownedHero), "hero", now)
            .withOwnedTitle(pending, now);
        when(repository.loadState(id)).thenReturn(stored);

        var config = placeholderConfig();
        var plugin = plugin(scheduler);
        var service = spy(new TitleService(plugin, Logger.getAnonymousLogger(), config, repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, clock,
            mock(AttributeBridgeRegistry.class, RETURNS_DEEP_STUBS), null,
            null, null, null));
        var placeholders = new TitlePlaceholderExpansion(plugin, () -> service, () -> config);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            service.preloadPlayer(player);
            waitUntil(() -> !taskQueue.isEmpty());
            drain(taskQueue);
            assertEquals("true", placeholders.onRequest(player, "owned_hero"));
            assertEquals("false", placeholders.onRequest(player, "owned_later"));
            assertEquals("2", placeholders.onRequest(player, "set_duo_total"));
            assertEquals("0", placeholders.onRequest(player, "set_total"));
            verify(service, never()).resolveState(any());

            var initial = service.resolveState(id);
            assertSame(initial, service.resolveState(id));
            assertEquals("[Hero]", placeholders.onRequest(player, "chat_main_prefix"));
            assertEquals("1", placeholders.onRequest(player, "set_duo_completion"));
            when(player.hasPermission("title.main")).thenReturn(false);
            assertEquals("", placeholders.onRequest(player, "chat_main_prefix"));
            assertEquals("0", placeholders.onRequest(player, "owned_count"));
            assertNotSame(initial, service.resolveState(id));

            when(player.hasPermission("title.main")).thenReturn(true);
            when(clock.instant()).thenReturn(activates);
            assertEquals("true", placeholders.onRequest(player, "owned_later"));
            assertEquals("2", placeholders.onRequest(player, "set_duo_completion"));
            assertEquals("true", placeholders.onRequest(player, "set_duo_active"));
            assertEquals("1", placeholders.onRequest(player, "hidden_count"));
            when(clock.instant()).thenReturn(expires);
            assertEquals("", placeholders.onRequest(player, "chat_main_prefix"));
            assertEquals("1", placeholders.onRequest(player, "owned_count"));
            assertEquals("false", placeholders.onRequest(player, "set_duo_active"));
            assertEquals("0", placeholders.onRequest(player, "set_completion"));
            assertEquals("false", placeholders.onRequest(player, "set_active"));
            verify(repository).loadState(id);
            verify(repository, never()).deleteExpiredTitles(any());

            service.clearPlayer(player);
            assertEquals("0", placeholders.onRequest(player, "owned_count"));
            assertEquals("false", placeholders.onRequest(player, "owned_later"));
        } finally {
            stopExecutor(service);
        }
    }

    @Test void revokeMustPublishBeforeQueuedEquipCanReadOwnership() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var config = config();
        when(player.hasPermission("arcartxsuite.title.use")).thenReturn(true);
        var repository = mock(TitleRepository.class);
        var state = PlayerTitleState.empty(id).grant("hero", Instant.now(), null, null, Instant.now(), "test");
        when(repository.loadState(id)).thenReturn(state);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(repository).deleteOwnedTitle(id, "hero");
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config, repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            service.preloadPlayer(player);
            waitUntil(() -> !tasks.isEmpty());
            drain(tasks);
            var revoked = service.revokeTitle(id, "hero");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            service.equipTitle(player, "hero");
            drain(tasks);
            assertTrue(service.getCachedState(id).equippedTitleIdsByGroup().isEmpty());
            verify(repository, never()).setEquippedTitle(any(), anyString(), anyString(), any());
            release.countDown();
            waitUntil(() -> !tasks.isEmpty());
            assertFalse(revoked.isDone());
            assertTrue(service.getCachedState(id).hasOwnedTitle("hero"));
            drain(tasks);
            assertTrue(revoked.get(2, TimeUnit.SECONDS).success());
            assertFalse(service.getCachedState(id).hasOwnedTitle("hero"));
            assertTrue(service.getCachedState(id).equippedTitleIdsByGroup().isEmpty());
            verify(repository, never()).setEquippedTitle(any(), anyString(), anyString(), any());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void committedGrantIsPublishedOnTheServerThread() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var id = UUID.randomUUID();
        var repository = mock(TitleRepository.class);
        Thread caller = Thread.currentThread();
        var initializationThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        doAnswer(call -> {
            initializationThread.set(Thread.currentThread());
            return null;
        }).when(repository).initialize();
        when(repository.grantOwnedTitle(eq(id), any(), eq(true)))
            .thenAnswer(call -> new GrantedTitle(call.getArgument(1), true));
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var receipt = service.grantPermanent(id, "hero", "test");
            waitUntil(() -> !tasks.isEmpty());
            assertFalse(receipt.isDone());
            drain(tasks);
            assertTrue(receipt.get(2, TimeUnit.SECONDS));
            assertNotSame(caller, initializationThread.get());
            verify(repository, never()).loadState(id);
        } finally { stopExecutor(service); }
    }

    @Test void expiryDoesNotBacklogAndCanRetryAfterDatabaseFailure() throws Exception {
        var repository = mock(TitleRepository.class);
        var service = new TitleService(plugin(mock(BukkitScheduler.class)), Logger.getAnonymousLogger(), config(),
            repository, mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        Thread main = Thread.currentThread();
        when(repository.deleteExpiredTitles(any())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                throw new SQLException("temporarily unavailable");
            }
            return 0;
        });
        var tick = TitleService.class.getDeclaredMethod("tickExpirationCleanup");
        tick.setAccessible(true);
        var executor = TitleService.class.getDeclaredField("databaseExecutor");
        executor.setAccessible(true);
        var worker = (ExecutorService) executor.get(service);
        try {
            tick.invoke(service);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) tick.invoke(service);
            release.countDown();
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            tick.invoke(service);
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void equipmentWaitsForCommitAndConsumesTheClientTokenOnce() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var id = UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var repository = mock(TitleRepository.class);
        var bridge = mock(PacketBridgeAPI.class);
        when(player.hasPermission("arcartxsuite.title.use")).thenReturn(true);
        when(bridge.isUiOpen(player, "test-title")).thenReturn(true);
        var state = PlayerTitleState.empty(id).grant("hero", Instant.now(), null, null, Instant.now(), "test");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.setEquippedTitle(eq(id), eq("main"), eq("hero"), any())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return state.equip("main", "hero", Instant.now());
        });
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            bridge, null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        cachedStates(service).put(id, state);
        setField(service, "runtimeUiId", "test-title");
        Field guardField = TitleService.class.getDeclaredField("tokenGuard");
        guardField.setAccessible(true);
        var guard = (xuanmo.arcartxsuite.api.security.ActionTokenGuard) guardField.get(service);
        String token = guard.issue(id, "test-title");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            service.handleClientPacket(player, TitleService.CLIENT_PACKET_ID, List.of("equip", token, "hero"));
            service.handleClientPacket(player, TitleService.CLIENT_PACKET_ID, List.of("equip", token, "hero"));
            drain(tasks);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(service.getCachedState(id).equippedTitleIdsByGroup().isEmpty());
            verify(player, never()).sendMessage(anyString());
            verify(repository, times(1)).setEquippedTitle(eq(id), eq("main"), eq("hero"), any());
            service.handleClientPacket(player, TitleService.CLIENT_PACKET_ID, List.of("refresh"));
            verify(bridge, never()).sendPacket(any(), anyString(), anyString(), any());
            when(bridge.isUiOpen(player, "test-title")).thenReturn(false);
            release.countDown();
            waitUntil(() -> !tasks.isEmpty());
            assertTrue(service.getCachedState(id).equippedTitleIdsByGroup().isEmpty());
            drain(tasks);
            assertEquals("hero", service.getCachedState(id).equippedTitleIdsByGroup().get("main"));
            verify(player, atLeastOnce()).sendMessage(anyString());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void aBlockedDatabaseCannotGrowTheWriteReceiptChainWithoutBound() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var repository = mock(TitleRepository.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        }).when(repository).deleteOwnedTitle(any(), anyString());
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        List<java.util.concurrent.CompletableFuture<TitleOperationResult>> accepted = new ArrayList<>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            UUID id = UUID.randomUUID();
            accepted.add(service.revokeTitle(id, "hero"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 1; i < 256; i++) accepted.add(service.revokeTitle(id, "hero"));
            var chained = accepted.get(0).thenCompose(result -> service.revokeTitle(id, "hero"));
            assertTrue(accepted.get(1).cancel(false));
            for (int i = 0; i < 500; i++) {
                var rejected = service.revokeTitle(id, "hero");
                assertTrue(rejected.isDone());
                assertFalse(rejected.join().success());
            }
            Field receiptsField = TitleService.class.getDeclaredField("writeReceipts");
            receiptsField.setAccessible(true);
            assertEquals(256, ((java.util.Set<?>) receiptsField.get(service)).size());
            release.countDown();
            waitUntil(() -> {
                drain(tasks);
                return accepted.stream().allMatch(java.util.concurrent.CompletableFuture::isDone) && chained.isDone();
            });
            assertTrue(accepted.stream().filter(future -> !future.isCancelled()).allMatch(future -> future.join().success()));
            assertTrue(chained.join().success());
            assertTrue(((java.util.Set<?>) receiptsField.get(service)).isEmpty());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void aFailedEquipmentWriteKeepsThePreviousCachedState() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var id = UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var repository = mock(TitleRepository.class);
        when(player.hasPermission("arcartxsuite.title.use")).thenReturn(true);
        when(repository.setEquippedTitle(eq(id), anyString(), anyString(), any()))
            .thenThrow(new java.sql.SQLException("test database outage"));
        var state = PlayerTitleState.empty(id).grant("hero", Instant.now(), null, null, Instant.now(), "test");
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        cachedStates(service).put(id, state);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            service.equipTitle(player, "hero");
            drain(tasks);
            waitUntil(() -> !tasks.isEmpty());
            drain(tasks);
            assertSame(state, service.getCachedState(id));
            assertTrue(state.equippedTitleIdsByGroup().isEmpty());
            verify(player, atLeastOnce()).sendMessage(anyString());
        } finally {
            stopExecutor(service);
        }
    }

    @Test void completedFailuresReuseTheExistingPlayerWriteBarrier() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var repository = mock(TitleRepository.class);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (calls.getAndIncrement() > 0) throw new java.sql.SQLException("test failure");
            return null;
        }).when(repository).deleteOwnedTitle(any(), anyString());
        var logger = Logger.getAnonymousLogger();
        logger.setLevel(java.util.logging.Level.OFF);
        var service = new TitleService(plugin(scheduler), logger, config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            UUID id = UUID.randomUUID();
            var first = service.revokeTitle(id, "hero");
            waitUntil(() -> !tasks.isEmpty());
            Field writesField = TitleService.class.getDeclaredField("playerWrites");
            writesField.setAccessible(true);
            Map<?, ?> writes = (Map<?, ?>) writesField.get(service);
            Object barrier = writes.get(id);
            assertNotNull(barrier);
            for (int i = 0; i < 200; i++) {
                assertFalse(service.revokeTitle(id, "hero").get(2, TimeUnit.SECONDS).success());
                assertSame(barrier, writes.get(id));
            }
            drain(tasks);
            assertTrue(first.get(2, TimeUnit.SECONDS).success());
            assertTrue(writes.isEmpty());
        } finally {
            stopExecutor(service);
        }
    }

    @Test void stoppingFinishesAcceptedWritesAndSkipsQueuedPreloads() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var repository = mock(TitleRepository.class);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(repository).deleteOwnedTitle(any(), anyString());
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            stubShutdown(bukkit);
            UUID firstId = UUID.randomUUID();
            UUID queuedId = UUID.randomUUID();
            UUID rejectedId = UUID.randomUUID();
            var first = service.revokeTitle(firstId, "hero");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var queued = service.revokeTitle(queuedId, "hero");
            var loader = TitleService.class.getDeclaredMethod("ensureLoadedAsync", UUID.class);
            loader.setAccessible(true);
            var loading = (java.util.concurrent.CompletableFuture<?>) loader.invoke(service, UUID.randomUUID());
            service.shutdown();
            assertFalse(first.isDone());
            assertFalse(queued.isDone());
            assertFalse(service.revokeTitle(rejectedId, "hero").join().success());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> loading.get(2, TimeUnit.SECONDS));
            release.countDown();
            service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(first.join().success());
            assertTrue(queued.join().success());
            var writes = inOrder(repository);
            writes.verify(repository).deleteOwnedTitle(firstId, "hero");
            writes.verify(repository).deleteOwnedTitle(queuedId, "hero");
            verify(repository, never()).deleteOwnedTitle(eq(rejectedId), anyString());
            verify(repository, never()).loadState(any());
            assertTrue(tasks.isEmpty());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void shutdownReturnsWhileTheDriverIsStillBlocked() throws Exception {
        var scheduler = mock(BukkitScheduler.class);
        var repository = mock(TitleRepository.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        doAnswer(call -> {
            entered.countDown();
            boolean waiting = true;
            while (waiting) {
                try {
                    waiting = !release.await(5, TimeUnit.SECONDS);
                    if (waiting) throw new AssertionError("test driver timeout");
                } catch (InterruptedException ignored) {
                    interrupted.set(true);
                }
            }
            return null;
        }).when(repository).deleteOwnedTitle(any(), anyString());
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> null, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            stubShutdown(bukkit);
            var receipt = service.revokeTitle(UUID.randomUUID(), "hero");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            service.shutdown();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            assertFalse(receipt.isDone());
            release.countDown();
            service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(interrupted.get());
            assertTrue(receipt.join().success());
        } finally {
            release.countDown();
            stopExecutor(service);
        }
    }

    @Test void reportsACommitEvenIfShutdownRetiresItsPendingUiCallback() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var repository = mock(TitleRepository.class);
        var tab = mock(xuanmo.arcartxsuite.api.capability.TabRefreshable.class);
        var service = new TitleService(plugin(scheduler), Logger.getAnonymousLogger(), config(), repository,
            mock(PacketBridgeAPI.class), null, () -> tab, null, mock(AttributeBridgeRegistry.class), null);
        try (var bukkit = mockStatic(Bukkit.class)) {
            stubShutdown(bukkit);
            var id = UUID.randomUUID();
            var receipt = service.revokeTitle(id, "hero");
            waitUntil(() -> !tasks.isEmpty());
            verify(repository).deleteOwnedTitle(id, "hero");
            assertFalse(receipt.isDone());

            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(receipt.join().success());
            drain(tasks);
            verifyNoInteractions(tab);
        } finally {
            stopExecutor(service);
        }
    }

    private static void stubShutdown(org.mockito.MockedStatic<Bukkit> bukkit) {
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        var scoreboards = mock(org.bukkit.scoreboard.ScoreboardManager.class);
        when(scoreboards.getMainScoreboard()).thenReturn(mock(org.bukkit.scoreboard.Scoreboard.class));
        bukkit.when(Bukkit::getScoreboardManager).thenReturn(scoreboards);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, PlayerTitleState> cachedStates(TitleService service) throws Exception {
        Field field = TitleService.class.getDeclaredField("states");
        field.setAccessible(true);
        return (Map<UUID, PlayerTitleState>) field.get(service);
    }

    private static void setField(TitleService service, String name, Object value) throws Exception {
        Field field = TitleService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private TitleModuleConfiguration config() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("groups.main.display-name", "Main");
        var title = new YamlConfiguration();
        title.set("hero.group", "main");
        title.set("hero.display-name", "Hero");
        title.save(folder.resolve("hero.yml").toFile());
        var config = TitleModuleConfiguration.load(yaml, Logger.getAnonymousLogger(), folder.toFile());
        assertNotNull(config.title("hero"));
        return config;
    }

    private TitleModuleConfiguration placeholderConfig() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("groups.main.display-name", "Main");
        yaml.set("groups.main.permission", "title.main");
        yaml.set("sets.duo.required-titles", List.of("hero", "later"));
        var titles = new YamlConfiguration();
        titles.set("hero.group", "main");
        titles.set("hero.chat-prefix", "[Hero]");
        titles.set("later.group", "main");
        titles.save(folder.resolve("test-titles.yml").toFile());
        return TitleModuleConfiguration.load(yaml, Logger.getAnonymousLogger(), folder.toFile());
    }

    private static void drain(Queue<Runnable> tasks) {
        for (Runnable task; (task = tasks.poll()) != null;) task.run();
    }

    private static JavaPlugin plugin(BukkitScheduler scheduler) {
        var plugin = mock(JavaPlugin.class);
        var server = mock(org.bukkit.Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        return plugin;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean());
    }

    private static void stopExecutor(TitleService service) throws Exception {
        Field field = TitleService.class.getDeclaredField("databaseExecutor");
        field.setAccessible(true);
        ((ExecutorService) field.get(service)).shutdownNow();
    }
}
