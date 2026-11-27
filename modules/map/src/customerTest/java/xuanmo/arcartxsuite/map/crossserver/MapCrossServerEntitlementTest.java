package xuanmo.arcartxsuite.map.crossserver;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CrossServerArrivalsConfiguration;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CrossServerConfiguration;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapTeleportSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapCrossServerEntitlementTest {
    @Test void cachedRemoteWorldAndAnchorAreUnavailableAfterDowngrade() {
        var fixture = new Fixture();
        try (var bukkit = mockStatic(Bukkit.class)) {
            fixture.start(bukkit);
            fixture.loadRemoteCatalog();
            assertEquals(1, fixture.map.remoteWorlds().size());
            assertNotNull(fixture.map.remoteWorld("peer/world"));
            assertNotNull(fixture.map.remoteAnchor("peer/gate"));

            fixture.active.set(false);
            assertTrue(fixture.map.remoteWorlds().isEmpty());
            assertNull(fixture.map.remoteWorld("peer/world"));
            assertNull(fixture.map.remoteAnchor("peer/gate"));
        }
    }

    @Test void rawConnectRequiresAnActiveChannelAtTheTimeTheReplyIsProcessed() throws Exception {
        var fixture = new Fixture();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            fixture.start(bukkit);
            auth.when(() -> AuthAccess.snapshot(fixture.playerId)).thenReturn(fixture.admission);
            fixture.loadRemoteCatalog();
            var request = fixture.requestTransfer();
            fixture.active.set(false);
            fixture.deliver(approval(request));
            verify(fixture.player, never()).sendPluginMessage(eq(fixture.plugin), eq("BungeeCord"), any(byte[].class));
            assertFalse(fixture.map.requestTeleport(fixture.session(), "peer", "world", "gate", () -> true).success());

            fixture.active.set(true);
            fixture.deliver(approval(fixture.requestTransfer()));
            var bytes = ArgumentCaptor.forClass(byte[].class);
            verify(fixture.player).sendPluginMessage(eq(fixture.plugin), eq("BungeeCord"), bytes.capture());
            try (var packet = new DataInputStream(new ByteArrayInputStream(bytes.getValue()))) {
                assertEquals("Connect", packet.readUTF());
                assertEquals("peer", packet.readUTF());
            }
        }
    }

    @Test void scheduledArrivalDoesNotTeleportAfterChannelRevocation() {
        var fixture = new Fixture();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            fixture.start(bukkit);
            auth.when(() -> AuthAccess.snapshot(fixture.playerId)).thenReturn(fixture.admission);
            when(fixture.handler.validateArrival(fixture.playerId, "world", "gate"))
                .thenReturn(CompletableFuture.completedFuture(MapOperationResult.success("ready")));
            fixture.deliver("""
                {"v":1,"t":"tpreq","dst":"local","req":"incoming","player":"%s","world":"world","anchor":"gate"}
                """.formatted(fixture.playerId));
            fixture.map.onPlayerAdmitted(fixture.session());
            var delayed = ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.scheduler).runTaskLater(eq(fixture.plugin), delayed.capture(), eq(20L));

            fixture.active.set(false);
            delayed.getValue().run();
            verify(fixture.handler, never()).executeArrival(any(), anyString(), anyString());
        }
    }

    private static String approval(String request) {
        return """
            {"v":1,"t":"tprsp","dst":"local","req":"%s","ok":true}
            """.formatted(request);
    }

    private static final class Fixture {
        private final Plugin plugin = mock(Plugin.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final CrossServerAPI api = mock(CrossServerAPI.class);
        private final CrossServerChannel channel = mock(CrossServerChannel.class);
        private final MapCrossServerService.Handler handler = mock(MapCrossServerService.Handler.class);
        private final Player player = mock(Player.class);
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final UUID playerId = UUID.randomUUID();
        private final AuthSnapshot admission = new AuthSnapshot(playerId, UUID.randomUUID(), 1, UUID.randomUUID(),
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "microsoft", Set.of(), "ADMITTED");
        private final CrossServerConfiguration config = new CrossServerConfiguration(true, "", "", 120, 400,
            true, 10, 20, 256, 4096, true, new CrossServerArrivalsConfiguration(true, false, true),
            CrossServerChannelConfig.enabledDefault());
        private final MapCrossServerService map = new MapCrossServerService(plugin, Logger.getAnonymousLogger(),
            config, api, null, handler);
        private Consumer<CrossServerDelivery> listener;

        private void start(MockedStatic<Bukkit> bukkit) {
            var server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getMessenger()).thenReturn(mock(Messenger.class));
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("Alex");
            when(player.getUniqueId()).thenReturn(playerId);
            when(api.nodeId()).thenReturn("local");
            when(channel.isActive()).thenAnswer(call -> active.get());
            when(api.openChannel(eq("map"), eq(config.channel()), any())).thenAnswer(call -> {
                listener = call.getArgument(2);
                return channel;
            });
            when(handler.sharedWorldCatalog()).thenReturn(List.of());
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            map.start();
        }

        private void loadRemoteCatalog() {
            deliver("""
                {"v":1,"t":"world","id":"world","name":"Peer World","dim":"minecraft:overworld","arrive":true}
                """);
            deliver("""
                {"v":1,"t":"anchors","world":"world","list":[{"id":"gate","name":"Gate","x":1,"y":64,"z":2}]}
                """);
        }

        private void deliver(String payload) {
            listener.accept(new CrossServerDelivery("map", "peer", UUID.randomUUID().toString(), payload,
                true, System.currentTimeMillis()));
        }

        private String requestTransfer() {
            clearInvocations(channel);
            assertTrue(map.requestTeleport(session(), "peer", "world", "gate", () -> true).success());
            var message = ArgumentCaptor.forClass(String.class);
            verify(channel).publish(message.capture());
            return JsonParser.parseString(message.getValue()).getAsJsonObject().get("req").getAsString();
        }

        private MapTeleportSession session() {
            return new MapTeleportSession(player, admission);
        }
    }
}
