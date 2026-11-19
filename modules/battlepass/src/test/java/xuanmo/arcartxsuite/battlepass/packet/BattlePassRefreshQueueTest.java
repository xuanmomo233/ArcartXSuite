package xuanmo.arcartxsuite.battlepass.packet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

class BattlePassRefreshQueueTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private final BattlePassService service = mock(BattlePassService.class);
    private final ArrayDeque<Runnable> async = new ArrayDeque<>();
    private final ArrayDeque<Runnable> retries = new ArrayDeque<>();

    @Test
    void repeatedRequestsAndTheRetryShareOneLoadAndSendOnlyConfirmedData() {
        var handler = fixture();
        when(service.isLoadFailed(id)).thenReturn(true, false);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session());
            for (int i = 0; i < 20; i++) refresh(handler);
            assertEquals(1, async.size());
            async.removeFirst().run();
            verify(bridge, never()).sendPacket(any(), anyString(), anyString(), any());
            assertEquals(1, retries.size());

            for (int i = 0; i < 20; i++) refresh(handler);
            assertTrue(async.isEmpty());
            assertEquals(1, retries.size());
            retries.removeFirst().run();
            verify(service, times(2)).getProgress(player);
            verify(bridge).sendPacket(eq(player), eq("rewards"), eq("rewards"), any());
        }
    }

    @Test
    void oldClaimCompletionCannotReleaseTheNewConnectionsToken() throws Exception {
        var handler = fixture();
        var gates = new ArrayDeque<BooleanSupplier>();
        var first = new CompletableFuture<BattlePassService.ClaimResult>();
        var second = new CompletableFuture<BattlePassService.ClaimResult>();
        when(service.claimLevelAsync(eq(player), eq(1), any(BooleanSupplier.class))).thenAnswer(call -> {
            gates.add(call.getArgument(2));
            return gates.size() == 1 ? first : second;
        });
        var field = BattlePassPacketHandler.class.getDeclaredField("actionTokens");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var tokens = (Map<UUID, String>) field.get(handler);
        try (var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session());
            tokens.put(id, "old");
            handler.handleClientPacket(player, "AXS_BATTLEPASS", List.of("claim", "1", "old"));
            assertTrue(gates.getFirst().getAsBoolean());

            var rejoined = session();
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(rejoined);
            handler.clearPendingRefresh(id);
            tokens.put(id, "new");
            handler.handleClientPacket(player, "AXS_BATTLEPASS", List.of("claim", "1", "new"));
            assertFalse(gates.getFirst().getAsBoolean());
            assertTrue(gates.getLast().getAsBoolean());

            first.complete(BattlePassService.ClaimResult.SUCCESS);
            assertTrue(gates.getLast().getAsBoolean());
            handler.handleClientPacket(player, "AXS_BATTLEPASS", List.of("claim", "1", "new"));
            verify(service, times(2)).claimLevelAsync(eq(player), eq(1), any(BooleanSupplier.class));
            second.complete(BattlePassService.ClaimResult.SUCCESS);
            assertNotEquals("new", tokens.get(id));
        }
    }

    @Test
    void aQueuedRefreshForAnOldConnectionCannotReadOrSpoilTheNewRequest() {
        var handler = fixture();
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session());
            refresh(handler);
            var replacement = session();
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(replacement);
            refresh(handler);
            assertEquals(2, async.size());

            async.removeFirst().run();
            verify(service, never()).getProgress(any());
            verify(bridge, never()).sendPacket(any(), anyString(), anyString(), any());
            async.removeFirst().run();
            verify(service).getProgress(player);
            verify(bridge).sendPacket(eq(player), eq("rewards"), eq("rewards"), any());
        }
    }

    private BattlePassPacketHandler fixture() {
        var config = mock(BattlePassModuleConfiguration.class);
        when(service.configuration()).thenReturn(config);
        when(service.isActive()).thenReturn(true);
        when(service.getProgress(player)).thenReturn(new BattlePassPlayerProgress(id, "season"));
        doAnswer(call -> { async.add(call.getArgument(0)); return null; }).when(service).async(any(Runnable.class));
        doAnswer(call -> { retries.add(call.getArgument(0)); return null; }).when(service).laterAsync(any(Runnable.class), eq(20L));
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("arcartxsuite.battlepass.use")).thenReturn(true);
        when(bridge.isUiOpen(player, "rewards")).thenReturn(true);
        return new BattlePassPacketHandler(null, bridge, null, service, "main", "tasks", "rewards",
            "settlements", null, null, null);
    }

    private void refresh(BattlePassPacketHandler handler) {
        assertTrue(handler.handleClientPacket(player, "AXS_BATTLEPASS", List.of("refresh_rewards")));
    }

    private AuthSnapshot session() {
        return new AuthSnapshot(id, UUID.randomUUID(), 1L, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }
}
