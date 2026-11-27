package xuanmo.arcartxsuite.announcer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration.SubtitleSettings;
import xuanmo.arcartxsuite.announcer.config.AnnouncerEntry;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration;
import xuanmo.arcartxsuite.announcer.transport.AnnouncerEnvelope;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

class AnnouncerReliabilityTest {
    @Test
    void clickRequiresThePlayersCurrentHandleAndConsumesItOnce() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alice");
        when(player.isOnline()).thenReturn(true);
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        AtomicReference<String> handle = new AtomicReference<>();
        var packetSent = new AtomicBoolean(true);
        when(bridge.sendPacketToAll(eq(player), anyList(), eq("display"), any())).thenAnswer(call -> {
            Map<?, ?> payload = call.getArgument(3);
            handle.set((String) payload.get("id"));
            return packetSent.get();
        });
        var config = mock(AnnouncerModuleConfiguration.class);
        when(config.autoPlay()).thenReturn(true);
        when(config.activeEntries()).thenReturn(List.of(new AnnouncerEntry("sale", true, "Sale", "say {player}")));
        var service = new AnnouncerService(plugin, Logger.getAnonymousLogger(), config, bridge,
            List.of("announcer"), null, mock(PlaceholderResolverAPI.class));
        var console = mock(ConsoleCommandSender.class);
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var reconnect = mock(Player.class);
        var admissionGeneration = new AtomicLong();
        var connection = UUID.randomUUID();
        when(reconnect.getUniqueId()).thenReturn(id);
        when(reconnect.isOnline()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            auth.when(() -> AuthAccess.snapshot(id)).thenAnswer(call -> new AuthSnapshot(id, connection,
                admissionGeneration.get(), id, AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED,
                "test", java.util.Set.of(), ""));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            service.syncAfterReload();
            assertNull(handle.get());
            tasks.removeFirst().run();
            String first = handle.get();
            assertNotNull(first);
            assertNotEquals("sale", first);
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of("sale"));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(reconnect);
            service.handleClientPacket(reconnect, AnnouncerService.CLICK_PACKET_ID, List.of(first));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            admissionGeneration.incrementAndGet();
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(first));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alice"), never());
            service.markClientInitialized(player);
            tasks.removeFirst().run();
            assertNotEquals(first, handle.get());
            first = handle.get();
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(first));
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(first));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alice"), times(1));
            service.syncAfterReload();
            tasks.removeFirst().run();
            assertNotEquals(first, handle.get());
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(first));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alice"), times(1));
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(handle.get()));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alice"), times(2));
            packetSent.set(false);
            service.syncAfterReload();
            tasks.removeFirst().run();
            service.handleClientPacket(player, AnnouncerService.CLICK_PACKET_ID, List.of(handle.get()));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alice"), times(2));
        }
    }

    @Test
    void largeAnnouncesUseTickBudgetAndKeepWaitingPlayersFirstWhenReplaced() {
        var plugin = mock(JavaPlugin.class);
        var bridge = mock(PacketBridgeAPI.class);
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        var delivered = new java.util.ArrayList<Player>();
        var payloads = new java.util.ArrayList<Map<?, ?>>();
        var players = java.util.stream.IntStream.range(0, 96).mapToObj(ignored -> onlinePlayer()).toList();
        var onlineById = new java.util.HashMap<UUID, Player>();
        players.forEach(player -> onlineById.put(player.getUniqueId(), player));
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUiAll(any(), anyList())).thenReturn(true);
        when(bridge.sendPacketToAll(any(), anyList(), eq("display"), any())).thenAnswer(call -> {
            delivered.add(call.getArgument(0));
            payloads.add(call.getArgument(3));
            return true;
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var resolver = mock(PlaceholderResolverAPI.class);
        when(resolver.available()).thenThrow(new IllegalStateException("placeholder bridge unavailable"));
        var service = new AnnouncerService(plugin, Logger.getAnonymousLogger(), mock(AnnouncerModuleConfiguration.class),
            bridge, List.of("announcer"), null, resolver);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> onlineById.get(call.getArgument(0)));
            auth.when(() -> AuthAccess.admitted(any(UUID.class))).thenReturn(true);
            service.broadcastNow("旧公告");
            verify(bridge, never()).openUiAll(any(), anyList());
            assertEquals(1, tasks.size());
            tasks.removeFirst().run();
            assertEquals(players.subList(0, 32), delivered);
            verify(bridge, times(32)).openUiAll(any(), anyList());

            service.broadcastNow("最新公告 %player_name%");
            for (int step = 0; step < 3; step++) {
                assertEquals(1, tasks.size());
                int before = delivered.size();
                tasks.removeFirst().run();
                assertEquals(32, delivered.size() - before);
            }
            assertTrue(tasks.isEmpty());
            assertEquals(players.subList(32, 96), delivered.subList(32, 96));
            assertEquals(players.subList(0, 32), delivered.subList(96, 128));
            assertEquals("最新公告 %player_name%", payloads.get(32).get("text"));
            payloads.subList(32, 128).forEach(payload -> assertSame(payloads.get(32), payload));
        }
    }

    @Test
    void pendingQueueRejectsOverflowAndReportsDrops() {
        var service = new AnnouncerService(null, Logger.getAnonymousLogger(), mock(AnnouncerModuleConfiguration.class),
            null, List.of(), null, null);
        for (int i = 0; i < 128; i++) {
            assertTrue(service.enqueueManualBroadcast("announcement " + i, false));
        }
        assertFalse(service.enqueueManualBroadcast("overflow", false));
        assertEquals(128, service.pendingManualBroadcasts());
        assertEquals(1, service.droppedManualBroadcasts());
    }

    @Test
    void displayBatchesPacketsAndOnlyExpandsTheCurrentAnnouncement() {
        var plugin = mock(JavaPlugin.class);
        var alice = onlinePlayer();
        var bob = onlinePlayer();
        var bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        var resolver = mock(PlaceholderResolverAPI.class);
        when(resolver.available()).thenReturn(true);
        when(resolver.applyPlaceholders(alice, "新公告 %player_name%")).thenReturn("新公告 Alice");
        when(resolver.applyPlaceholders(bob, "新公告 %player_name%")).thenReturn("新公告 Bob");
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var config = mock(AnnouncerModuleConfiguration.class);
        when(config.autoPlay()).thenReturn(true);
        when(config.activeEntries()).thenReturn(List.of(new AnnouncerEntry("notice", true, "公告", "")));
        var service = new AnnouncerService(plugin, Logger.getAnonymousLogger(), config, bridge,
            List.of("announcer"), null, resolver);
        UUID aliceId = alice.getUniqueId();
        UUID bobId = bob.getUniqueId();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(alice, bob));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(aliceId)).thenReturn(alice);
            bukkit.when(() -> Bukkit.getPlayer(bobId)).thenReturn(bob);
            auth.when(() -> AuthAccess.admitted(aliceId)).thenReturn(true);
            auth.when(() -> AuthAccess.admitted(bobId)).thenReturn(true);
            service.syncAfterReload();
            assertEquals(1, tasks.size());
            verify(bridge, never()).sendPacketToAll(any(), anyList(), anyString(), any());
            verify(resolver, never()).applyPlaceholders(any(Player.class), anyString());
            tasks.removeFirst().run();
            var payloads = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(bridge, times(2)).sendPacketToAll(any(), eq(List.of("announcer")), eq("display"), payloads.capture());
            assertSame(payloads.getAllValues().get(0), payloads.getAllValues().get(1));

            clearInvocations(bridge);
            service.broadcastNow("旧公告 %player_name%");
            service.broadcastNow("新公告 %player_name%");
            assertEquals(1, tasks.size());
            verify(resolver, never()).applyPlaceholders(any(Player.class), anyString());
            tasks.removeFirst().run();
            verify(resolver).applyPlaceholders(alice, "新公告 %player_name%");
            verify(resolver).applyPlaceholders(bob, "新公告 %player_name%");
            verify(resolver, never()).applyPlaceholders(any(Player.class), eq("旧公告 %player_name%"));
            verify(bridge).sendPacketToAll(eq(alice), eq(List.of("announcer")), eq("display"),
                argThat(packet -> packet instanceof Map<?, ?> map && "新公告 Alice".equals(map.get("text"))));
            verify(bridge).sendPacketToAll(eq(bob), eq(List.of("announcer")), eq("display"),
                argThat(packet -> packet instanceof Map<?, ?> map && "新公告 Bob".equals(map.get("text"))));

            clearInvocations(bridge);
            service.markClientInitialized(alice);
            tasks.removeFirst().run();
            verify(bridge).sendPacketToAll(eq(alice), eq(List.of("announcer")), eq("display"), any());
            verify(bridge, never()).sendPacketToAll(eq(bob), anyList(), anyString(), any());

            service.markClientInitialized(alice);
            assertEquals(1, tasks.size());
            clearInvocations(bridge);
            service.shutdown();
            tasks.removeFirst().run();
            verifyNoInteractions(bridge);
        }
    }

    @Test
    void remoteImmediateBroadcastsUseABoundedFifoAndStopWithTheService() {
        org.junit.jupiter.api.Assumptions.assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        var plugin = mock(JavaPlugin.class);
        var config = mock(AnnouncerModuleConfiguration.class);
        var crossServer = mock(CrossServerAPI.class);
        when(crossServer.nodeId()).thenReturn("local");
        var service = new AnnouncerService(plugin, Logger.getAnonymousLogger(), config, null,
            List.of(), crossServer, null);
        var qqBot = mock(QQBotBroadcastable.class);
        service.setQQBotProvider(() -> qqBot);
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            for (int i = 0; i < 129; i++) {
                service.handleRemoteEnvelope(new AnnouncerEnvelope("id-" + i, "remote", "公告 " + i, true));
            }
            service.handleRemoteEnvelope(new AnnouncerEnvelope("id-0", "remote", "重复", true));
            assertEquals(128, service.pendingManualBroadcasts());
            assertEquals(1, service.droppedManualBroadcasts());
            assertEquals(1, tasks.size());
            tasks.removeFirst().run();
            tasks.removeFirst().run();
            var order = inOrder(qqBot);
            order.verify(qqBot).sendToAllGroups("[公告] 公告 0");
            order.verify(qqBot).sendToAllGroups("[公告] 公告 1");
            assertEquals(126, service.pendingManualBroadcasts());
            service.shutdown();
            tasks.removeFirst().run();
            verifyNoMoreInteractions(qqBot);
            assertEquals(0, service.pendingManualBroadcasts());
        }
    }

    private static Player onlinePlayer() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test
    void subtitleRetriesAnUiThatFailedToOpen(@TempDir Path directory) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUi(player, "subtitle")).thenReturn(false, true);
        PlaceholderResolverAPI resolver = mock(PlaceholderResolverAPI.class);
        when(resolver.applyPlaceholders(eq(player), anyString())).thenAnswer(call -> call.getArgument(1));
        var settings = new SubtitleSettings(false, List.of("subtitle"), "groups", false);
        Path groups = Files.createDirectories(directory.resolve("groups"));
        Files.writeString(groups.resolve("test.yml"), """
            1:
              text: retry
            """);
        var service = new SubtitleService(plugin, Logger.getAnonymousLogger(), bridge, settings,
            List.of("subtitle"), directory.toFile(), resolver);
        PluginManager pluginManager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.loadGroups();
            assertFalse(service.playGroup(player, "test"));
            verify(bridge, never()).sendPacketToAll(eq(player), anyList(), eq("play"), any());
            assertTrue(service.playGroup(player, "test"));
            verify(bridge, times(2)).openUi(player, "subtitle");
            verify(bridge).sendPacketToAll(eq(player), eq(List.of("subtitle")), eq("play"), any());
        }
    }
}
