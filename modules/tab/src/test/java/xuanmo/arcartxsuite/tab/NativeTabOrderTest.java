package xuanmo.arcartxsuite.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class NativeTabOrderTest {
    @Test
    void sortsHighToLowAndKeepsExistingPrioritiesOnATailJoin() {
        var first = player(UUID.randomUUID());
        var second = player(UUID.randomUUID());
        var newcomer = player(UUID.randomUUID());
        var access = new FakeAccess();
        access.orders.put(first, 7);
        access.orders.put(second, 0);
        access.orders.put(newcomer, 21);
        var nativeOrder = new NativeTabOrder(access);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(first.getUniqueId())).thenReturn(first);
            bukkit.when(() -> Bukkit.getPlayer(second.getUniqueId())).thenReturn(second);
            bukkit.when(() -> Bukkit.getPlayer(newcomer.getUniqueId())).thenReturn(newcomer);
            assertTrue(nativeOrder.apply(List.of(first, second)));
            assertTrue(access.order(first) > access.order(second));
            nativeOrder.apply(List.of(first, second));
            assertEquals(2, access.writes);

            assertTrue(nativeOrder.apply(List.of(first, second, newcomer)));
            assertEquals(3, access.writes);
            assertTrue(access.order(second) > access.order(newcomer));
            assertTrue(nativeOrder.apply(List.of(first, second)));
            assertEquals(21, access.order(newcomer));
            nativeOrder.close();
        }

        assertEquals(7, access.order(first));
        assertEquals(0, access.order(second));
        assertEquals(21, access.order(newcomer));
    }

    @Test
    void restoresUnchangedOwnedValuesAndYieldsToExternalChanges() {
        UUID retainedId = UUID.randomUUID();
        UUID removedId = UUID.randomUUID();
        Player retained = player(retainedId);
        Player removed = player(removedId);
        var access = new FakeAccess();
        access.orders.put(retained, 12);
        access.orders.put(removed, 0);
        var nativeOrder = new NativeTabOrder(access);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(retainedId)).thenReturn(retained);
            bukkit.when(() -> Bukkit.getPlayer(removedId)).thenReturn(removed);
            assertTrue(nativeOrder.apply(List.of(retained, removed)));
            access.orders.put(retained, 30);
            assertFalse(nativeOrder.apply(List.of(retained)));
            assertFalse(nativeOrder.apply(List.of(retained)));
            assertEquals(30, access.order(retained));
            nativeOrder.close();
        }
        assertEquals(30, access.order(retained));
        assertEquals(0, access.order(removed));
    }

    @Test
    void doesNotRestoreIntoAReplacementConnection() {
        UUID id = UUID.randomUUID();
        Player previous = player(id);
        Player reconnect = player(id);
        var access = new FakeAccess();
        access.orders.put(previous, 0);
        access.orders.put(reconnect, 13);
        var nativeOrder = new NativeTabOrder(access);
        nativeOrder.apply(List.of(previous));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(reconnect);
            assertTrue(nativeOrder.apply(List.of()));
            assertEquals(1, access.writes);
            nativeOrder.apply(List.of(reconnect));
            nativeOrder.close();
        }

        assertEquals(13, access.order(reconnect));
    }

    @Test
    void requiresTheNativeOrderingProtocolAndRemainsSafeOnLookupFailure() {
        Player viewer = player(UUID.randomUUID());
        var access = new FakeAccess();
        var nativeOrder = new NativeTabOrder(access);
        access.protocol = -1;
        assertFalse(nativeOrder.supportsViewer(viewer));
        access.protocol = 767;
        assertFalse(nativeOrder.supportsViewer(viewer));
        access.protocol = 768;
        assertTrue(nativeOrder.supportsViewer(viewer));
        access.protocol = 769;
        assertTrue(nativeOrder.supportsViewer(viewer));
        access.failProtocolLookup = true;
        assertFalse(nativeOrder.supportsViewer(viewer));
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private static final class FakeAccess implements NativeTabOrder.Access {
        private final Map<Player, Integer> orders = new IdentityHashMap<>();
        private int protocol;
        private int writes;
        private boolean failProtocolLookup;

        @Override public int order(Player player) {
            return orders.get(player);
        }

        @Override public void order(Player player, int order) {
            writes++;
            orders.put(player, order);
        }

        @Override public int protocol(Player player) {
            if (failProtocolLookup) throw new IllegalStateException("Protocol lookup unavailable");
            return protocol;
        }
    }
}
