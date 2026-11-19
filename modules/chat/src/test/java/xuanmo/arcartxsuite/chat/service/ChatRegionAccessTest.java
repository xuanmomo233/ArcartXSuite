package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.RegionChatAccess;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.chat.config.ChatCardConfiguration;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.config.ChatFunctionConfiguration;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.RouteState;

class ChatRegionAccessTest {
    private static final String CHANNEL = "global";
    private static final ChatChannelDefinition GLOBAL = new ChatChannelDefinition(CHANNEL, true, CHANNEL,
        ChatChannelMode.GLOBAL, "", "", true, 0, "", "", "", "", "");
    private final ChatRepository repository = mock(ChatRepository.class);
    private final RegionChatAccess access = mock(RegionChatAccess.class);
    private final PacketBridgeAPI packets = mock(PacketBridgeAPI.class);
    private final CrossServerAPI crossServer = mock(CrossServerAPI.class);
    private final ChatService service = service();

    @Test
    void deniesPublicSenderBeforeLocalDeliveryEventsOrRelay() throws Exception {
        Player sender = onlinePlayer();
        var events = mock(EventBusCapability.class);
        service.setEventBusProvider(() -> events);
        when(access.canSendPublic(sender)).thenReturn(false);

        try (var auth = mockStatic(AuthAccess.class)) {
            UUID senderId = sender.getUniqueId();
            auth.when(() -> AuthAccess.admitted(senderId)).thenReturn(true);
            var dispatch = ChatService.class.getDeclaredMethod("dispatchChannelMessage", Player.class, String.class,
                ChatChannelDefinition.class, ChatMuteRecord.class, List.class, Map.class);
            dispatch.setAccessible(true);
            var result = (ChatOperationResult) dispatch.invoke(service, sender, "blocked", GLOBAL, null, List.of(), Map.of());
            assertFalse(result.success());
        }
        verify(access).canSendPublic(sender);
        verifyNoInteractions(packets, events, crossServer);
    }

    @Test
    void rechecksExternalPublicRecipientsBeforeReadingTheirRoutesAndSending() throws Exception {
        Player allowed = onlinePlayer();
        Player denied = onlinePlayer();
        UUID allowedId = allowed.getUniqueId();
        UUID deniedId = denied.getUniqueId();
        when(access.canReceivePublic(allowed)).thenReturn(true);
        when(access.canReceivePublic(denied)).thenReturn(false);
        Thread main = Thread.currentThread();
        when(repository.loadRouteStates(Set.of(allowedId), CHANNEL, CHANNEL)).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return route(allowedId);
        });
        var both = new java.util.HashMap<>(route(allowedId));
        both.putAll(route(deniedId));
        when(repository.loadRouteStates(Set.of(allowedId, deniedId), CHANNEL, CHANNEL)).thenReturn(both);

        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(allowedId)).thenReturn(allowed);
        when(server.getPlayer(deniedId)).thenReturn(denied);
        doReturn(List.of(allowed, denied)).when(server).getOnlinePlayers();
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        try (var auth = mockStatic(AuthAccess.class)) {
            for (UUID player : List.of(allowedId, deniedId)) {
                var snapshot = new AuthSnapshot(player, UUID.randomUUID(), 1, player,
                    AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
                auth.when(() -> AuthAccess.snapshot(player)).thenReturn(snapshot);
            }
            var first = service.receiveExternal(CHANNEL, null, "{message}", "first", Set.of()).toCompletableFuture();
            assertFalse(first.isDone());
            runCallback(callbacks);
            assertTrue(first.get(3, TimeUnit.SECONDS));
            verify(allowed).sendMessage("first");
            verify(denied, never()).sendMessage("first");
            verify(repository, never()).loadRouteStates(Set.of(deniedId), CHANNEL, CHANNEL);

            when(access.canReceivePublic(denied)).thenReturn(true);
            var second = service.receiveExternal(CHANNEL, null, "{message}", "second", Set.of()).toCompletableFuture();
            when(access.canReceivePublic(allowed)).thenReturn(false);
            runCallback(callbacks);
            assertTrue(second.get(3, TimeUnit.SECONDS));
            verify(denied).sendMessage("second");
            verify(allowed, never()).sendMessage("second");
            verify(repository).loadRouteStates(Set.of(allowedId, deniedId), CHANNEL, CHANNEL);

            var queued = service.receiveExternal(CHANNEL, null, "{message}", "queued", Set.of()).toCompletableFuture();
            service.shutdown();
            assertFalse(queued.get(3, TimeUnit.SECONDS));
            verify(denied, never()).sendMessage("queued");
        } finally {
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            serverField.set(null, previous);
        }
    }

    private static void runCallback(LinkedBlockingQueue<Runnable> callbacks) throws InterruptedException {
        Runnable task = callbacks.poll(3, TimeUnit.SECONDS);
        assertNotNull(task);
        task.run();
    }

    @Test
    void rejectsLinkedExternalSenderInTheirLiveRegionBeforeDelivery() {
        Player sender = onlinePlayer();
        UUID senderId = sender.getUniqueId();
        when(access.canSendPublic(sender)).thenReturn(false);
        clearInvocations(repository);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(senderId)).thenReturn(sender);

            assertFalse(service.receiveExternal(CHANNEL, senderId, "{message}", "blocked", Set.of())
                .toCompletableFuture().getNow(true));

            bukkit.verify(Bukkit::getOnlinePlayers, never());
        }
        verify(access).canSendPublic(sender);
        verifyNoInteractions(repository);
    }

    @Test
    void rechecksRegionalAccessAtTheFinalRelayCallback() throws Exception {
        Player recipient = onlinePlayer();
        UUID recipientId = recipient.getUniqueId();
        UUID senderId = UUID.randomUUID();
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(recipient.spigot()).thenReturn(spigot);
        when(access.canReceivePublic(recipient)).thenReturn(false);
        var admitted = new AuthSnapshot(recipientId, UUID.randomUUID(), 1, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var envelope = new ChatEnvelope(UUID.randomUUID().toString(), "origin", "origin", CHANNEL, senderId.toString(),
            "Sender", "Sender", "", "", "relayed", "relayed", "", "relayed", false, false, false,
            List.of(), null, "relayed", GLOBAL.audienceKey());
        var connectionType = Class.forName(ChatService.class.getName() + "$ChatConnection");
        var connection = connectionType.getDeclaredConstructor(Player.class, AuthSnapshot.class);
        connection.setAccessible(true);
        var audience = List.of(connection.newInstance(recipient, admitted));
        var routes = Map.of(senderId, route(senderId).get(senderId), recipientId, route(recipientId).get(recipientId));
        var deliver = ChatService.class.getDeclaredMethod("deliverPreparedPublic", ChatEnvelope.class,
            ChatChannelDefinition.class, Player.class, List.class, Map.class, boolean.class);
        deliver.setAccessible(true);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(() -> Bukkit.getPlayer(recipientId)).thenReturn(recipient);
            auth.when(() -> AuthAccess.snapshot(recipientId)).thenReturn(admitted);

            deliver.invoke(service, envelope, GLOBAL, null, audience, routes, false);
            verifyNoInteractions(spigot);

            when(access.canReceivePublic(recipient)).thenReturn(true);
            deliver.invoke(service, envelope, GLOBAL, null, audience, routes, false);
            verify(spigot).sendMessage(any(BaseComponent[].class));
        }
    }

    private ChatService service() {
        ChatModuleConfiguration configuration = mock(ChatModuleConfiguration.class);
        when(configuration.channel(CHANNEL)).thenReturn(GLOBAL);
        when(configuration.defaultChannelId()).thenReturn(CHANNEL);
        when(configuration.maxLength()).thenReturn(80);
        when(configuration.functions()).thenReturn(new ChatFunctionConfiguration(false, false, false, "", "", "", List.of()));
        when(configuration.cards()).thenReturn(new ChatCardConfiguration("", "", "", "", 16, 8, 160));
        ChatService service = new ChatService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), () -> null,
            configuration, repository, packets, mock(ItemBridgeAPI.class), "", crossServer, null);
        service.setRegionChatProvider(() -> access);
        return service;
    }

    private static Player onlinePlayer() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static Map<UUID, RouteState> route(UUID playerId) {
        return Map.of(playerId, new RouteState(ChatPlayerState.createDefault(playerId, CHANNEL), true, false));
    }
}
