package xuanmo.arcartxsuite.tab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabProxyEntitlementTest {
    @Test void localHudRegistersEvenWhenProxyRosterIsConfiguredOnAFreeHost(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("config.yml"), "settings:\n  layout: simple\n  proxy-roster: true\n");
        var context = mock(ModuleContext.class);
        var bridge = mock(PacketBridgeAPI.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var messenger = mock(Messenger.class);
        var hud = folder.resolve("ui/tab/tab-simple.yml").toFile();
        when(plugin.getServer()).thenReturn(server);
        when(server.getMessenger()).thenReturn(messenger);
        when(context.plugin()).thenReturn(plugin);
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.packetBridge()).thenReturn(bridge);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.logger()).thenReturn(Logger.getAnonymousLogger());
        when(context.advancedCrossServerOrchestrationAllowed()).thenReturn(false);
        when(bridge.registerOrReloadUi("AXS:tab-simple", hud)).thenReturn(
            new PacketBridgeAPI.UiRegistrationResult(true, "AXS:tab-simple", "AXS:tab-simple", "register", ""));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                verify(bridge).registerOrReloadUi("AXS:tab-simple", hud);
                verify(context, never()).registerCapability(eq(TabRefreshable.class), any());
                verifyNoInteractions(messenger);
            } finally {
                module.onDisable();
            }
        }
    }

    @Test void pausedProxyRosterRejectsRequestsAndDropsPreviouslyQueuedRefresh() {
        var permitted = new AtomicBoolean(true);
        var plugin = mock(JavaPlugin.class);
        var player = mock(Player.class);
        var server = mock(Server.class);
        var messenger = mock(Messenger.class);
        var scheduler = mock(BukkitScheduler.class);
        UUID playerId = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(server.getMessenger()).thenReturn(messenger);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            auth.when(() -> AuthAccess.admitted(playerId)).thenReturn(true);
            try (var roster = new TabVisibilityPublisher(plugin, permitted::get)) {
                byte[] query = {ProxyTabVisibility.QUERY};
                roster.onPluginMessageReceived(ProxyTabVisibility.CHANNEL, player, query);
                verify(player).sendPluginMessage(eq(plugin), eq(ProxyTabVisibility.CHANNEL),
                    argThat(frame -> frame.length == ProxyTabVisibility.STATE_MESSAGE_LENGTH
                        && frame[0] == ProxyTabVisibility.VISIBLE));
                clearInvocations(player);

                roster.requestGlobalRefresh("queued while licensed");
                var refresh = ArgumentCaptor.forClass(Runnable.class);
                verify(scheduler).runTask(eq(plugin), refresh.capture());
                permitted.set(false);
                refresh.getValue().run();
                roster.onPluginMessageReceived(ProxyTabVisibility.CHANNEL, player, query);
                roster.requestViewerRefresh(player, "state change after revocation");
                assertFalse(roster.requestVanish(player).toCompletableFuture().join());
                verify(player, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
            }
        }
    }
}
