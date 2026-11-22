package xuanmo.arcartxsuite.module;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.security.ClientPacketGuard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ClientPacketRouterTest {
    @Test
    void indexedAliasesRespectPriorityAndRemovingOneModuleKeepsOtherRoutes() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        ClientPacketHandler first = mock(ClientPacketHandler.class);
        ClientPacketHandler last = mock(ClientPacketHandler.class);
        var router = new ClientPacketRouter(null);
        router.registerPacketHandler("last", PacketHandlerSpec.of(last, 50, "example", null));
        router.registerPacketHandler("first", PacketHandlerSpec.of(first, 0, "example", null, "EXAMPLE", "alias"));
        try (var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            assertNotNull(router.routeToken("aLiAs"));
            assertTrue(router.routeClientPacket(player, "eXaMpLe", List.of()));
            var order = inOrder(first, last);
            order.verify(first).handleClientPacket(player, "eXaMpLe", List.of());
            order.verify(last).handleClientPacket(player, "eXaMpLe", List.of());
            verifyNoMoreInteractions(first, last);

            router.removePacketHandlers("first");
            assertNull(router.routeToken("alias"));
            assertNotNull(router.routeToken("EXAMPLE"));
            router.clearAll();
            assertNull(router.routeToken("example"));
        }
    }

    @Test
    void queuedPacketsCannotCrossPlayerOrAuthenticationSessions() {
        Player old = mock(Player.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(old.isOnline()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(old.getUniqueId()).thenReturn(id);
        when(player.getUniqueId()).thenReturn(id);
        ClientPacketHandler handler = mock(ClientPacketHandler.class);
        var router = new ClientPacketRouter(null);
        var specification = PacketHandlerSpec.of(handler, 0, "example", null);
        router.registerPacketHandler("example", specification);
        var route = router.routeToken("example");
        var current = session(id, 5);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(current);
            router.routeQueuedPacket(old, "example", List.of(), current, route);
            router.routeQueuedPacket(player, "example", List.of(), session(id, 4), route);
            verifyNoInteractions(handler);
            router.registerPacketHandler("unrelated", PacketHandlerSpec.of(mock(ClientPacketHandler.class), 0, "other", null));
            assertSame(route, router.routeToken("example"));
            router.routeQueuedPacket(player, "example", List.of(), current, route);
            verify(handler).handleClientPacket(player, "example", List.of());

            router.removePacketHandlers("example");
            router.registerPacketHandler("example", specification);
            router.routeQueuedPacket(player, "example", List.of(), current, route);
            verifyNoMoreInteractions(handler);
        }
    }

    private static AuthSnapshot session(UUID playerId, long generation) {
        return new AuthSnapshot(playerId, UUID.randomUUID(), generation, playerId,
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }

    @Test
    void ownedRoutesUseTheirOwnGuardAndUnownedRoutesAreIgnored() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        ClientPacketGuard guard = mock(ClientPacketGuard.class);
        ClientPacketHandler other = mock(ClientPacketHandler.class);
        ClientPacketHandler tooltip = mock(ClientPacketHandler.class);
        var router = new ClientPacketRouter(guard);
        router.registerPacketHandler("other", PacketHandlerSpec.of(other, 0, "other-id", null));
        router.registerPacketHandler("tooltip", PacketHandlerSpec.of(tooltip, 50, "AXS_TOOLTIP_DATA", null));
        List<String> packet = List.of("different-fingerprint");
        when(guard.allow(player, "tooltip", "AXS_TOOLTIP_DATA", "inspect", false)).thenReturn(true);
        when(tooltip.handleClientPacket(player, "AXS_TOOLTIP_DATA", packet)).thenReturn(true);
        when(guard.allow(player, "other", "other-id", "refresh", false)).thenReturn(true);
        when(other.handleClientPacket(player, "other-id", List.of())).thenReturn(true);

        try (var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            assertTrue(router.routeClientPacket(player, "AXS_TOOLTIP_DATA", packet));
            verifyNoInteractions(other);
            assertFalse(router.routeClientPacket(player, "unregistered", List.of()));
            assertTrue(router.routeClientPacket(player, "other-id", List.of()));
        }
        verify(tooltip).handleClientPacket(player, "AXS_TOOLTIP_DATA", packet);
        verify(other).handleClientPacket(player, "other-id", List.of());
    }

    @Test
    void loginPacketNeverDispatchesToOtherModules() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ClientPacketHandler other = mock(ClientPacketHandler.class);
        ClientPacketHandler unrelated = mock(ClientPacketHandler.class);
        ClientPacketHandler login = mock(ClientPacketHandler.class);
        var router = new ClientPacketRouter(null);
        router.registerPacketHandler("other", PacketHandlerSpec.of(other, 0, "other-id", null));
        router.registerPacketHandler("unrelated", PacketHandlerSpec.of(unrelated, 0, "AXS_auth_v1", null));
        router.registerPacketHandler("loginview", PacketHandlerSpec.of(login, 0, "AXS_auth_v1", null));

        try (var auth = mockStatic(AuthAccess.class)) {
            assertTrue(router.routeClientPacket(player, "ordinary", List.of()));
            assertTrue(router.routeClientPacket(player, "AXS_auth_v1", List.of("login")));
        }
        verify(login).handleClientPacket(player, "AXS_auth_v1", List.of("login"));
        verifyNoInteractions(other, unrelated);
    }
}
