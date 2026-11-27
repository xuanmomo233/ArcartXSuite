package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.eventpacket.RealtimeRules.Snapshot;
import xuanmo.arcartxsuite.eventpacket.RuleConfiguration.UiAudience;

class UiPacketBroadcastsTest {
    @Test
    void readsValidatedAudiencesAndDefaultsToSelf() throws Exception {
        assertEquals(UiAudience.SELF, audience(""));
        assertEquals(UiAudience.ALL_ONLINE, audience(", audience: all-online"));
        assertEquals(UiAudience.OTHERS, audience(", audience: others"));
        assertThrows(IllegalArgumentException.class, () -> audience(", audience: everyone"));
    }

    @Test
    void collectsOnceAndSkipsTheOriginAndRecipientsWithAChangedSession() {
        var next = new AtomicReference<Runnable>();
        var task = mock(ScheduledTask.class);
        var received = new ArrayList<Player>();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class);
             var broadcasts = new UiPacketBroadcasts(runnable -> { next.set(runnable); return task; })) {
            var origin = online(bukkit, auth, true);
            var blocked = online(bukkit, auth, false);
            var added = new ArrayList<Online>();
            for (int i = 0; i < 20; i++) added.add(online(bukkit, auth, true));
            var reconnecting = online(bukkit, auth, true);
            var players = new ArrayList<>(List.of(origin.player(), blocked.player()));
            players.addAll(added.stream().map(Online::player).toList());
            players.add(reconnecting.player());
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);

            assertTrue(broadcasts.enqueue(origin.snapshot(), UiAudience.OTHERS, received::add));
            assertTrue(received.isEmpty());
            next.get().run();
            assertTrue(received.size() <= 16);
            reconnecting.session().set(admitted(reconnecting.player().getUniqueId()));
            for (int i = 0; i < 60; i++) next.get().run();

            assertEquals(added.stream().map(Online::player).toList(), received);
            bukkit.verify(Bukkit::getOnlinePlayers);
            verify(task).cancel();
        }
    }

    @Test
    void includesOriginForAllOnlineAndStopsWhenTheOriginSessionOrRuntimeRetires() {
        var next = new AtomicReference<Runnable>();
        var task = mock(ScheduledTask.class);
        var received = new ArrayList<Player>();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class);
             var broadcasts = new UiPacketBroadcasts(runnable -> { next.set(runnable); return task; })) {
            var origin = online(bukkit, auth, true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(origin.player()));
            assertTrue(broadcasts.enqueue(origin.snapshot(), UiAudience.ALL_ONLINE, received::add));
            for (int i = 0; i < 10; i++) next.get().run();
            assertEquals(List.of(origin.player()), received);

            assertTrue(broadcasts.enqueue(origin.snapshot(), UiAudience.ALL_ONLINE, received::add));
            origin.session().set(admitted(origin.player().getUniqueId()));
            next.get().run();
            assertTrue(broadcasts.enqueue(origin.snapshot(), UiAudience.ALL_ONLINE, received::add));
            broadcasts.close();
            next.get().run();
            assertEquals(List.of(origin.player()), received);
            bukkit.verify(Bukkit::getOnlinePlayers);
            verify(task, times(3)).cancel();
        }
    }

    private static UiAudience audience(String audience) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            rules:
              demo:
                trigger: signal
                signal: demo
                actions: [{type: ui.packet, ui: 'AXS:sample', handler: refresh%s}]
            """.formatted(audience));
        return RuleConfiguration.parse(yaml).topics().get("eventpacket.signal.demo").getFirst().actions().getFirst().audience();
    }

    private static Online online(MockedStatic<Bukkit> bukkit, MockedStatic<AuthAccess> auth, boolean allowed) {
        var id = UUID.randomUUID();
        var player = mock(Player.class);
        var session = new AtomicReference<>(allowed ? admitted(id) : AuthSnapshot.absent(id));
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        auth.when(() -> AuthAccess.snapshot(id)).thenAnswer(ignored -> session.get());
        return new Online(player, session);
    }

    private static AuthSnapshot admitted(UUID id) {
        return new AuthSnapshot(id, UUID.randomUUID(), 1, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
    }

    private record Online(Player player, AtomicReference<AuthSnapshot> session) {
        Snapshot snapshot() { return Snapshot.from(session.get(), Map.of()); }
    }
}
