package xuanmo.arcartxsuite.tab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabRemotePlayersTest {
    private static final ProxyTabVisibility.PlayerDetails DETAILS = new ProxyTabVisibility.PlayerDetails(
        "aa".repeat(32), List.of(5L, 0L, 0L, 1L), "积分十五", "§7[冒险者]§r Remote");

    @Test void boundsAndValidatesBothDirections() {
        byte[] details = ProxyTabVisibility.playerDetailsMessage(DETAILS, 40);
        assertEquals(40, ProxyTabVisibility.sequence(details));
        assertEquals(DETAILS, ProxyTabVisibility.readPlayerDetails(details));
        assertNull(ProxyTabVisibility.readPlayerDetails(ProxyTabVisibility.playerDetailsMessage(null, 41)));
        assertThrows(IllegalArgumentException.class, () ->
            ProxyTabVisibility.readPlayerDetails(Arrays.copyOf(details, details.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ProxyTabVisibility.playerDetailsMessage(
            new ProxyTabVisibility.PlayerDetails("ff".repeat(32), List.of(), "超".repeat(43)), 40));
        assertThrows(IllegalArgumentException.class, () -> ProxyTabVisibility.playerDetailsMessage(
            new ProxyTabVisibility.PlayerDetails("ff".repeat(32), List.of(), null, "超".repeat(38)), 40));
        var noFormat = new ProxyTabVisibility.PlayerDetails("ff".repeat(32), List.of(), null);
        assertEquals(noFormat, ProxyTabVisibility.readPlayerDetails(ProxyTabVisibility.playerDetailsMessage(noFormat, 40)));

        var maximumDetails = new ProxyTabVisibility.PlayerDetails(
            "ff".repeat(32), List.of(1L, 1L, 1L, 1L), "z".repeat(ProxyTabVisibility.MAX_SORT_BYTES),
            "n".repeat(ProxyTabVisibility.MAX_DISPLAY_NAME_BYTES));
        var limit = IntStream.rangeClosed(1, ProxyTabVisibility.MAX_REMOTE_PLAYERS)
            .mapToObj(number -> new ProxyTabVisibility.RemotePlayer(new UUID(0, number), "x".repeat(64), 100, maximumDetails))
            .toList();
        byte[] snapshot = ProxyTabVisibility.crossServerListMessage(limit, 42);
        assertTrue(snapshot.length < 32767);
        assertEquals(42, ProxyTabVisibility.sequence(snapshot));
        assertEquals(limit, ProxyTabVisibility.readCrossServerList(snapshot));
        assertEquals(List.of(), ProxyTabVisibility.readCrossServerList(ProxyTabVisibility.crossServerListMessage(List.of(), 43)));
        assertThrows(IllegalArgumentException.class, () -> ProxyTabVisibility.readCrossServerList(new byte[0]));
        assertThrows(IllegalArgumentException.class, () ->
            ProxyTabVisibility.readCrossServerList(ProxyTabVisibility.stateMessage(ProxyTabVisibility.VISIBLE, 42)));
        assertThrows(IllegalArgumentException.class, () ->
            ProxyTabVisibility.readCrossServerList(Arrays.copyOf(snapshot, snapshot.length - 1)));
        assertThrows(IllegalArgumentException.class, () ->
            ProxyTabVisibility.readCrossServerList(Arrays.copyOf(snapshot, snapshot.length + 1)));
        var excessive = new ArrayList<>(limit);
        excessive.add(remote(80));
        assertThrows(IllegalArgumentException.class, () -> ProxyTabVisibility.crossServerListMessage(excessive, 42));
        byte[] duplicate = ProxyTabVisibility.crossServerListMessage(List.of(remote(1), remote(1)), 42);
        assertThrows(IllegalArgumentException.class, () -> ProxyTabVisibility.readCrossServerList(duplicate));
    }

    @Test void publishesFormattedNamesWithinTheUtf8Budget() {
        var player = mock(Player.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var playerList = mock(TabPlayerList.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getMessenger()).thenReturn(mock(Messenger.class));
        String glyphTitle = "§k!%000001<icon>§r Player";
        when(playerList.detailsFor(eq(player), anyCollection(), anyLong())).thenReturn(
            new TabPlayerList.Details(DETAILS.inputsKey(), DETAILS.permissionMask(), DETAILS.sortValue(), glyphTitle),
            new TabPlayerList.Details(DETAILS.inputsKey(), DETAILS.permissionMask(), DETAILS.sortValue(), "超".repeat(38)));

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            auth.when(() -> AuthAccess.admitted(playerId)).thenReturn(true);
            try (var publisher = new TabVisibilityPublisher(plugin, () -> true, playerList)) {
                byte[] query = {ProxyTabVisibility.QUERY};
                publisher.onPluginMessageReceived(ProxyTabVisibility.CHANNEL, player, query);
                publisher.onPluginMessageReceived(ProxyTabVisibility.CHANNEL, player, query);

                var frame = ArgumentCaptor.forClass(byte[].class);
                verify(player, times(4)).sendPluginMessage(eq(plugin), eq(ProxyTabVisibility.CHANNEL), frame.capture());
                var published = frame.getAllValues().stream()
                    .filter(message -> message[0] == ProxyTabVisibility.PLAYER_LIST_DETAILS)
                    .map(ProxyTabVisibility::readPlayerDetails).toList();
                assertEquals(2, published.size());
                assertEquals(glyphTitle, published.get(0).displayName());
                assertNull(published.get(1).displayName());
                assertEquals(DETAILS.sortValue(), published.get(1).sortValue());
                assertEquals(DETAILS.permissionMask(), published.get(1).permissionMask());
            }
        }
    }

    @Test void onlyExposesCurrentSnapshotsForTheAdmittedPaidViewerConnection() {
        var entitled = new AtomicBoolean(true);
        var viewer = mock(Player.class);
        var replacement = mock(Player.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.isOnline()).thenReturn(true);
        when(replacement.getUniqueId()).thenReturn(viewerId);
        when(replacement.isOnline()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getMessenger()).thenReturn(mock(Messenger.class));

        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);
            auth.when(() -> AuthAccess.admitted(viewerId)).thenReturn(false);
            try (var publisher = new TabVisibilityPublisher(plugin, entitled::get, mock(TabPlayerList.class))) {
                deliver(publisher, viewer, List.of(remote(1)), 20);
                auth.when(() -> AuthAccess.admitted(viewerId)).thenReturn(true);
                assertEquals(List.of(), publisher.remotePlayers(viewer));

                deliver(publisher, viewer, List.of(remote(1)), 20);
                deliver(publisher, viewer, List.of(remote(2)), 19);
                var current = publisher.remotePlayers(viewer).get(0);
                assertEquals(remote(1).uuid(), current.uuid());
                assertEquals(DETAILS.permissionMask(), current.details().permissionMask());
                assertEquals(DETAILS.sortValue(), current.details().sortValue());
                assertEquals(DETAILS.displayName(), current.details().displayName());
                assertEquals(List.of(), publisher.remotePlayers(replacement));

                entitled.set(false);
                deliver(publisher, viewer, List.of(remote(2)), 21);
                assertEquals(List.of(), publisher.remotePlayers(viewer));

                entitled.set(true);
                bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(replacement);
                deliver(publisher, replacement, List.of(remote(2)), 1);
                deliver(publisher, viewer, List.of(remote(1)), 100);
                assertEquals(remote(2).uuid(), publisher.remotePlayers(replacement).get(0).uuid());
                assertEquals(List.of(), publisher.remotePlayers(viewer));
                deliver(publisher, replacement, List.of(), 2);
                assertEquals(List.of(), publisher.remotePlayers(replacement));
            }
        }
    }

    private static ProxyTabVisibility.RemotePlayer remote(int number) {
        return new ProxyTabVisibility.RemotePlayer(new UUID(0, number), "Remote" + number, 12, DETAILS);
    }

    private static void deliver(TabVisibilityPublisher publisher, Player player,
                                List<ProxyTabVisibility.RemotePlayer> players, long sequence) {
        publisher.onPluginMessageReceived(ProxyTabVisibility.CHANNEL, player,
            ProxyTabVisibility.crossServerListMessage(players, sequence));
    }
}
