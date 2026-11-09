package xuanmo.arcartxsuite.proxy.bungee;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;
import xuanmo.arcartxsuite.proxy.common.auth.ProxyProofForwarding;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BungeeProofForwarderTest {
    @Test void expiredQueuedLoginNeverConsumesAReceiptAndReleasesItsIntent() throws Exception {
        when(plugin.getProxy()).thenReturn(proxy);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(proxy.getPluginManager()).thenReturn(mock(PluginManager.class)); when(forwarding.enabled()).thenReturn(true);
        var clock=new java.util.concurrent.atomic.AtomicLong(); var entered=new CountDownLatch(2); var release=new CountDownLatch(1); var finished=new CountDownLatch(3);
        when(forwarding.claim(any(),anyString())).thenAnswer(call->{ entered.countDown(); release.await(3,TimeUnit.SECONDS);
            return new ProxyProofs.Identity(call.getArgument(0),call.getArgument(1),"microsoft",System.currentTimeMillis()); });
        try(var adapter=new BungeeProofForwarder(plugin,forwarding,clock::get)) {
            LoginEvent queued=null; UUID last=null;
            for(int index=0;index<3;index++) {
                var connection=mock(PendingConnection.class); UUID id=UUID.randomUUID();
                when(connection.getUniqueId()).thenReturn(id); when(connection.getName()).thenReturn("Alice");
                when(connection.isOnlineMode()).thenReturn(true); when(connection.isConnected()).thenReturn(true);
                var event=mock(LoginEvent.class); when(event.getConnection()).thenReturn(connection);
                doAnswer(call->{ finished.countDown(); return null; }).when(event).completeIntent(plugin);
                adapter.login(event); queued=event; last=id;
                if(index==1) assertTrue(entered.await(2,TimeUnit.SECONDS));
            }
            clock.set(4_000_000_000L); release.countDown(); assertTrue(finished.await(3,TimeUnit.SECONDS));
            verify(queued).setCancelled(true); verify(forwarding,never()).claim(eq(last),anyString());
        } finally { release.countDown(); }
    }
    private final Plugin plugin = mock(Plugin.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final ProxyProofForwarding forwarding = mock(ProxyProofForwarding.class);
    private final UUID profile = UUID.randomUUID();

    private BungeeProofForwarder adapter() {
        when(plugin.getProxy()).thenReturn(proxy);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("BungeeProofForwarderTest"));
        when(proxy.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(forwarding.enabled()).thenReturn(true);
        return new BungeeProofForwarder(plugin, forwarding);
    }

    private PendingConnection login(BungeeProofForwarder adapter) throws Exception {
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.getUniqueId()).thenReturn(profile);
        when(connection.getName()).thenReturn("Alice");
        when(connection.isOnlineMode()).thenReturn(true);
        when(connection.isConnected()).thenReturn(true);
        when(forwarding.claim(profile, "Alice")).thenReturn(new ProxyProofs.Identity(profile, "Alice", "microsoft", System.currentTimeMillis()));
        LoginEvent event = mock(LoginEvent.class);
        when(event.getConnection()).thenReturn(connection);
        CountDownLatch done = new CountDownLatch(1);
        doAnswer(invocation -> { done.countDown(); return null; }).when(event).completeIntent(plugin);
        adapter.login(event);
        assertTrue(done.await(3, TimeUnit.SECONDS));
        verify(event).registerIntent(plugin);
        verify(event, times(1)).completeIntent(plugin);
        return connection;
    }

    @Test void clientMessagesCannotReachTheProofSigner() throws Exception {
        try (var adapter = adapter()) {
            PluginMessageEvent event = mock(PluginMessageEvent.class);
            when(event.getTag()).thenReturn(ProxyProofForwarding.CHANNEL);
            when(event.getData()).thenReturn(new byte[0]);
            when(event.getSender()).thenReturn(mock(ProxiedPlayer.class));
            adapter.message(event);
            verify(event).setCancelled(true);
            verify(forwarding, never()).response(anyString(), any(), anyString(), any(), any());
        }
    }

    @Test void onlyTheCurrentBackendOfTheAuthenticatedConnectionReceivesAProof() throws Exception {
        try (var adapter = adapter()) {
            PendingConnection connection = login(adapter);
            ProxiedPlayer player = mock(ProxiedPlayer.class);
            Server backend = mock(Server.class), obsolete = mock(Server.class);
            ServerInfo info = mock(ServerInfo.class);
            when(backend.getInfo()).thenReturn(info);
            when(info.getName()).thenReturn("lobby");
            when(player.getPendingConnection()).thenReturn(connection);
            when(player.getUniqueId()).thenReturn(profile);
            when(player.getName()).thenReturn("Alice");
            when(player.getServer()).thenReturn(backend);
            byte[] request = {1}, response = {2};
            when(forwarding.response(eq("lobby"), eq(profile), eq("Alice"), any(), same(request))).thenReturn(response);
            PluginMessageEvent event = mock(PluginMessageEvent.class);
            when(event.getTag()).thenReturn(ProxyProofForwarding.CHANNEL);
            when(event.getData()).thenReturn(request);
            when(event.getReceiver()).thenReturn(player);
            when(event.getSender()).thenReturn(obsolete);
            adapter.message(event);
            verify(forwarding, never()).response(anyString(), any(), anyString(), any(), any());
            when(event.getSender()).thenReturn(backend);
            adapter.message(event);
            verify(backend).sendData(ProxyProofForwarding.CHANNEL, response);
        }
    }

    @Test void offlineModeAloneCannotProduceAnIdentityReceipt() throws Exception {
        try (var adapter = adapter()) {
            LoginEvent event = mock(LoginEvent.class);
            when(event.getConnection()).thenReturn(mock(PendingConnection.class));
            adapter.login(event);
            verify(event, never()).registerIntent(plugin);
            verify(forwarding, never()).claim(any(), anyString());
        }
    }

    @Test void missingReceiptRejectsLoginAndCompletesItsIntent() throws Exception {
        try (var adapter = adapter()) {
            PendingConnection connection = mock(PendingConnection.class);
            when(connection.getUniqueId()).thenReturn(profile);
            when(connection.getName()).thenReturn("Alice");
            when(connection.isOnlineMode()).thenReturn(true);
            when(forwarding.claim(profile, "Alice")).thenThrow(new IllegalStateException("No receipt"));
            LoginEvent event = mock(LoginEvent.class);
            when(event.getConnection()).thenReturn(connection);
            CountDownLatch done = new CountDownLatch(1);
            doAnswer(invocation -> { done.countDown(); return null; }).when(event).completeIntent(plugin);
            adapter.login(event);
            assertTrue(done.await(3, TimeUnit.SECONDS));
            verify(event).setCancelled(true);
            verify(event, times(1)).completeIntent(plugin);
        }
    }

    @Test void shutdownReleasesARegisteredIntentExactlyOnce() throws Exception {
        var adapter = adapter();
        CountDownLatch started = new CountDownLatch(1), done = new CountDownLatch(1), release = new CountDownLatch(1);
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.getUniqueId()).thenReturn(profile);
        when(connection.getName()).thenReturn("Alice");
        when(connection.isOnlineMode()).thenReturn(true);
        when(forwarding.claim(profile, "Alice")).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return new ProxyProofs.Identity(profile, "Alice", "microsoft", System.currentTimeMillis());
        });
        LoginEvent event = mock(LoginEvent.class);
        when(event.getConnection()).thenReturn(connection);
        doAnswer(invocation -> { done.countDown(); return null; }).when(event).completeIntent(plugin);
        try {
            adapter.login(event);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            adapter.close();
            assertTrue(done.await(3, TimeUnit.SECONDS));
            verify(event).setCancelled(true);
            verify(event, times(1)).completeIntent(plugin);
        } finally {
            release.countDown();
            adapter.close();
        }
    }

    @Test void disconnectForOldConnectionDoesNotEraseTheReconnectedIdentity() throws Exception {
        try (var adapter = adapter()) {
            PendingConnection previous = login(adapter), current = login(adapter);
            ProxiedPlayer oldPlayer = mock(ProxiedPlayer.class);
            when(oldPlayer.getPendingConnection()).thenReturn(previous);
            PlayerDisconnectEvent disconnect = mock(PlayerDisconnectEvent.class);
            when(disconnect.getPlayer()).thenReturn(oldPlayer);
            adapter.disconnect(disconnect);
            ProxiedPlayer player = mock(ProxiedPlayer.class);
            when(player.getPendingConnection()).thenReturn(current);
            when(player.getUniqueId()).thenReturn(profile);
            when(player.getName()).thenReturn("Alice");
            Server backend = mock(Server.class);
            ServerInfo info = mock(ServerInfo.class);
            when(player.getServer()).thenReturn(backend);
            when(backend.getInfo()).thenReturn(info);
            when(info.getName()).thenReturn("lobby");
            byte[] frame = {1};
            PluginMessageEvent event = mock(PluginMessageEvent.class);
            when(event.getTag()).thenReturn(ProxyProofForwarding.CHANNEL);
            when(event.getSender()).thenReturn(backend);
            when(event.getReceiver()).thenReturn(player);
            when(event.getData()).thenReturn(frame);
            adapter.message(event);
            verify(forwarding).response(eq("lobby"), eq(profile), eq("Alice"), any(), same(frame));
        }
    }
}
