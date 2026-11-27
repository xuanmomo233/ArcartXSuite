package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

class ModuleRulesTest {
    @TempDir Path directory;

    @Test
    void fishingAndQuestCapabilitiesAreInvokedThroughActiveModuleRules() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "runtime: {budget-micros: 5000}\n");
        Path config = Files.createDirectory(directory.resolve("rules")).resolve("events.yml");
        Files.writeString(config, config("legendary"));
        var module = new EventPacketModule();
        var context = mock(ModuleContext.class, RETURNS_DEEP_STUBS);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(SchedulerAPI.class);
        var storage = mock(StorageManager.class);
        var source = mock(DataSource.class);
        var scope = mock(ModuleStorage.class);
        var retired = new java.util.concurrent.CompletableFuture<Void>();
        when(scope.dataSource()).thenReturn(source);
        when(scope.retired()).thenReturn(retired);
        doAnswer(call -> {
            ((java.util.concurrent.CompletionStage<?>)call.getArgument(0)).whenComplete((ignored, error) -> {
                if(error==null)retired.complete(null);else retired.completeExceptionally(error);
            });
            return null;
        }).when(scope).retireAfter(any());
        var bus = mock(EventBusCapability.class);
        var subtitles = mock(SubtitlePlayable.class);
        var quests = mock(QuestGpsNavigable.class);
        var bridge = mock(PacketBridgeAPI.class);
        var mail = mock(AxsMailService.class);
        var player = mock(Player.class);
        var playerId = UUID.randomUUID();
        var connection = UUID.randomUUID();
        var subscriptions = new HashMap<String, EventBusCapability.EventHandler>();
        var realtimeLoops = new java.util.ArrayList<Loop>();

        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        when(context.plugin()).thenReturn(plugin);
        when(context.dataFolder()).thenReturn(directory.toFile());
        when(context.pluginDataFolder()).thenReturn(directory.toFile());
        when(context.scheduler()).thenReturn(scheduler);
        when(context.cancelScheduledTasks()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(context.storageManager()).thenReturn(storage);
        when(context.getCapability(EventBusCapability.class)).thenReturn(bus);
        when(context.getCapability(SubtitlePlayable.class)).thenReturn(subtitles);
        when(context.getCapability(QuestGpsNavigable.class)).thenReturn(quests);
        when(context.getCapability(AbyssRunResultAccess.class)).thenReturn(null);
        when(context.getCapability(AxsMailService.class)).thenReturn(null);
        when(context.packetBridge()).thenReturn(bridge);
        when(storage.getDescriptor()).thenReturn(mock(StorageDescriptor.class));
        when(storage.openModuleStorage(eq("eventpacket"), anyString(), any(), isNull())).thenReturn(scope);
        when(source.getConnection()).thenAnswer(ignored -> DriverManager.getConnection("jdbc:sqlite::memory:"));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Ada");
        when(player.isOnline()).thenReturn(true);
        when(subtitles.playGroup(eq(player), anyString())).thenReturn(true);
        when(scheduler.scheduleAtFixedRate(eq(plugin), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            ScheduledTask handle = mock(ScheduledTask.class);
            if (invocation.<Long>getArgument(3) == 1L) {
                realtimeLoops.add(new Loop(invocation.getArgument(1), handle));
            }
            return handle;
        });
        when(bus.subscribe(anyString(), any())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            assertNull(subscriptions.put(topic, invocation.getArgument(1)), "duplicate subscription");
            return topic;
        });
        doAnswer(invocation -> subscriptions.remove(invocation.getArgument(0))).when(bus).unsubscribe(anyString());

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(playerId)).thenReturn(session(playerId, connection));
            assertTrue(module.onEnable(context).toCompletableFuture().join());
            try {
                assertTrue(realtimeLoops.isEmpty());
                var fishing = new EventBusCapability.BusEvent("axs.fishing.success", player,
                    Map.of("fish_rarity", "LEGENDARY"), 0L);
                var quest = new EventBusCapability.BusEvent("axs.questgps.quest_completed", player,
                    Map.of("quest_id", "chapter_one"), 0L);
                publish(subscriptions, fishing);
                publish(subscriptions, quest);
                verifyNoInteractions(subtitles);
                assertEquals(1, realtimeLoops.size());
                drain(realtimeLoops.get(0));
                verify(subtitles).playGroup(player, "legendary");
                verify(quests).offerQuest(player, "chapter_two", true);

                publish(subscriptions, quest);
                auth.when(() -> AuthAccess.snapshot(playerId)).thenReturn(session(playerId, UUID.randomUUID()));
                drain(realtimeLoops.get(1));
                verify(quests, times(1)).offerQuest(player, "chapter_two", true);

                Files.writeString(config, "rules: {}\nrules: {}\n");
                assertThrows(java.util.concurrent.CompletionException.class, () -> module.onReload().toCompletableFuture().join());
                publish(subscriptions, fishing);
                drain(realtimeLoops.get(2));
                verify(subtitles, times(2)).playGroup(player, "legendary");

                publish(subscriptions, fishing);
                Files.writeString(config, config("after-reload"));
                module.onReload().toCompletableFuture().join();
                assertEquals(4, realtimeLoops.size());
                verify(realtimeLoops.get(3).task()).cancel();
                realtimeLoops.get(3).callback().run();
                publish(subscriptions, fishing);
                drain(realtimeLoops.get(4));
                verify(subtitles, times(2)).playGroup(player, "legendary");
                verify(subtitles).playGroup(player, "after-reload");
                Files.writeString(config, "rules: {}\n");
                module.onReload().toCompletableFuture().join();
                assertFalse(subscriptions.containsKey("axs.fishing.success"));
                assertFalse(subscriptions.containsKey("axs.questgps.quest_completed"));
                Files.writeString(config, config("enabled-again"));
                module.onReload().toCompletableFuture().join();
                assertEquals(5, realtimeLoops.size());
                publish(subscriptions, fishing);
                drain(realtimeLoops.get(5));
                verify(subtitles).playGroup(player, "enabled-again");

                Files.writeString(directory.resolve("rules/client.yml"), """
                    rules:
                      panel:
                        trigger: client-packet
                        permission: demo.panel
                        actions:
                          - {type: ui.packet, ui: "AXS:test", handler: update, data: {label: "{player_name}"}}
                          - {type: mail.send, title: "确认 {player_name}", content: "收到操作"}
                    """);
                when(context.getCapability(AxsMailService.class)).thenReturn(mail);
                when(mail.send(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(AxsDeliveryReceipt.ok(3, 1)));
                module.onReload().toCompletableFuture().join();
                var handler = ArgumentCaptor.forClass(PacketHandlerSpec.class);
                verify(context).registerClientPacketHandler(handler.capture());
                var route = handler.getValue().handler();
                when(player.hasPermission("demo.panel")).thenReturn(true);
                route.handleClientPacket(player, LocalTriggerSettings.PACKET_ID, java.util.List.of("client-packet", "panel"));
                assertEquals(6, realtimeLoops.size());
                when(player.isPermissionSet("demo.panel")).thenReturn(true);
                route.handleClientPacket(player, LocalTriggerSettings.PACKET_ID, java.util.List.of("client-packet", "panel", "untrusted"));
                assertEquals(6, realtimeLoops.size());
                route.handleClientPacket(player, LocalTriggerSettings.PACKET_ID, java.util.List.of("client-packet", "panel"));
                drain(realtimeLoops.get(6));
                verify(bridge).sendPacket(player, "AXS:test", "update", Map.of("label", "Ada"));
                var request = ArgumentCaptor.forClass(AxsMailRequest.class);
                verify(mail).send(request.capture());
                assertEquals(java.util.List.of(playerId), request.getValue().recipients());
                assertEquals("确认 Ada", request.getValue().title());
            } finally {
                module.onDisable();
                module.shutdownCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertTrue(subscriptions.isEmpty());
    }

    private static void drain(Loop loop) {
        for (int i = 0; i < 4; i++) loop.callback().run();
        verify(loop.task()).cancel();
    }

    private record Loop(Runnable callback, ScheduledTask task) {}

    private static AuthSnapshot session(UUID player, UUID connection) {
        return new AuthSnapshot(player, connection, 1, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
    }

    private static void publish(Map<String, EventBusCapability.EventHandler> subscriptions, EventBusCapability.BusEvent event) {
        subscriptions.get(event.topic()).handle(event);
    }

    private static String config(String subtitle) {
        return """
            rules:
              fishing:
                trigger: event-bus
                topic: axs.fishing.success
                when: { fish_rarity: LEGENDARY }
                actions:
                  - { type: subtitle, group: %s }
              quest:
                trigger: event-bus
                topic: axs.questgps.quest_completed
                when: { quest_id: chapter_one }
                actions:
                  - { type: quest.offer, quest: chapter_two }
            """.formatted(subtitle);
    }
}
