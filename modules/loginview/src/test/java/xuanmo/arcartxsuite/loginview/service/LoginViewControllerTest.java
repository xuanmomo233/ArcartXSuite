package xuanmo.arcartxsuite.loginview.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;

class LoginViewControllerTest {
    private static final String UI = "AXS:auth";

    @Test
    void thePulseUsesJoinAndQuitMembershipWithoutRescanningOnlinePlayers() {
        try (var lab = new Lab()) {
            Player joined = player();
            var admitted = snapshot(lab.player, UUID.randomUUID(), AuthSnapshot.Stage.ADMITTED);
            var joinedState = snapshot(joined, UUID.randomUUID(), AuthSnapshot.Stage.ADMITTED);
            when(lab.runtime.snapshot(lab.player.getUniqueId())).thenReturn(admitted);
            when(lab.runtime.snapshot(joined.getUniqueId())).thenReturn(joinedState);
            lab.start();
            lab.pulse();
            lab.controller.onQuit(new PlayerQuitEvent(lab.player, ""));
            lab.controller.onJoin(new PlayerJoinEvent(joined, ""));
            lab.pulse();
            lab.pulse();
            lab.bukkit.verify(Bukkit::getOnlinePlayers);
            verify(lab.runtime).snapshot(lab.player.getUniqueId());
            verify(lab.runtime, times(2)).snapshot(joined.getUniqueId());
            verify(lab.packets, never()).openUiUnsafe(any(), anyString());
            verify(lab.packets, never()).sendPacket(any(), anyString(), anyString(), any());
        }
    }

    @Test
    void failedOpenStateAndCloseAreRetriedWithoutResendingSuccessfulState() {
        try (var lab = new Lab()) {
            UUID connection = UUID.randomUUID();
            var state = new AtomicReference<>(snapshot(lab.player, connection, AuthSnapshot.Stage.AWAITING_IDENTITY));
            when(lab.runtime.snapshot(lab.player.getUniqueId())).thenAnswer(call -> state.get());
            var visible = new AtomicBoolean();
            var opens = new AtomicInteger();
            var closes = new AtomicInteger();
            when(lab.packets.isUiOpen(lab.player, UI)).thenAnswer(call -> visible.get());
            when(lab.packets.openUiUnsafe(lab.player, UI)).thenAnswer(call -> {
                if (opens.incrementAndGet() == 1) return false;
                visible.set(true);
                return true;
            });
            when(lab.packets.sendPacket(eq(lab.player), eq(UI), eq("state"), any())).thenReturn(false, true);
            when(lab.packets.closeUiUnsafe(lab.player, UI)).thenAnswer(call -> {
                if (closes.incrementAndGet() == 1) return false;
                visible.set(false);
                return true;
            });
            lab.start();
            lab.pulse();
            verify(lab.packets, never()).sendPacket(any(), anyString(), anyString(), any());
            lab.pulse();
            lab.pulse();
            lab.pulse();
            assertEquals(2, opens.get());
            verify(lab.packets, times(2)).sendPacket(eq(lab.player), eq(UI), eq("state"), any());
            verify(lab.runtime, times(2)).terms();

            state.set(snapshot(lab.player, connection, AuthSnapshot.Stage.ADMITTED));
            lab.pulse();
            assertTrue(visible.get());
            lab.pulse();
            lab.pulse();
            assertFalse(visible.get());
            assertEquals(2, closes.get());
        }
    }

    @Test
    void repeatedRefreshesShareOneRequestAndCanRetryAfterFailure() {
        try (var lab = new Lab()) {
            UUID id = lab.player.getUniqueId();
            UUID connection = UUID.randomUUID();
            var state = snapshot(lab.player, connection, AuthSnapshot.Stage.AWAITING_IDENTITY);
            var first = new CompletableFuture<AuthRuntimeCapability.Result>();
            var retry = new CompletableFuture<AuthRuntimeCapability.Result>();
            when(lab.runtime.snapshot(id)).thenReturn(state);
            when(lab.runtime.refresh(id, connection)).thenReturn(first, retry);
            var callback = ArgumentCaptor.forClass(Runnable.class);
            when(lab.scheduler.runTask(eq(lab.plugin), callback.capture())).thenReturn(mock(BukkitTask.class));
            var refresh = List.of("refresh", connection.toString());
            lab.start();

            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", List.of("refresh", connection.toString(), "extra")));
            verify(lab.runtime, never()).refresh(any(), any());
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            first.completeExceptionally(new IllegalStateException("backend unavailable"));
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            verify(lab.runtime).refresh(id, connection);
            verify(lab.scheduler).runTask(eq(lab.plugin), any(Runnable.class));

            callback.getValue().run();
            verify(lab.packets).sendPacket(eq(lab.player), eq(UI), eq("result"), any());
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            verify(lab.runtime, times(2)).refresh(id, connection);
        }
    }

    @Test
    void aReplyForThePreviousConnectionCannotRedisplayTheLoginViewOrClearTheNewRequest() {
        try (var lab = new Lab()) {
            UUID original = UUID.randomUUID();
            UUID id = lab.player.getUniqueId();
            var state = snapshot(lab.player, original, AuthSnapshot.Stage.AWAITING_IDENTITY);
            when(lab.runtime.snapshot(id)).thenReturn(state);
            when(lab.runtime.refresh(id, original)).thenReturn(CompletableFuture.completedFuture(
                new AuthRuntimeCapability.Result(true, "READY", state)));
            var callback = ArgumentCaptor.forClass(Runnable.class);
            when(lab.scheduler.runTask(eq(lab.plugin), callback.capture())).thenReturn(mock(BukkitTask.class));
            lab.start();
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", List.of("refresh", original.toString())));
            var next = snapshot(lab.player, UUID.randomUUID(), AuthSnapshot.Stage.AWAITING_IDENTITY);
            when(lab.runtime.snapshot(id)).thenReturn(next);
            when(lab.runtime.refresh(id, next.connectionId())).thenReturn(new CompletableFuture<>());
            var refresh = List.of("refresh", next.connectionId().toString());
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            callback.getValue().run();
            assertTrue(lab.controller.handle(lab.player, "AXS_auth_v1", refresh));
            verify(lab.runtime).refresh(id, next.connectionId());
            verify(lab.packets, never()).openUiUnsafe(any(), anyString());
            verify(lab.packets, never()).sendPacket(any(), anyString(), anyString(), any());
        }
    }

    private static Player player() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static AuthSnapshot snapshot(Player player, UUID connection, AuthSnapshot.Stage stage) {
        return new AuthSnapshot(player.getUniqueId(), connection, 1, null, AuthSnapshot.Registration.REGISTERED,
            stage, "local-password", Set.of(), "");
    }

    private static final class Lab implements AutoCloseable {
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Player player = player();
        final PacketBridgeAPI packets = mock(PacketBridgeAPI.class);
        final AuthRuntimeCapability runtime = mock(AuthRuntimeCapability.class);
        final ArgumentCaptor<Runnable> pulse = ArgumentCaptor.forClass(Runnable.class);
        final LoginViewController controller = new LoginViewController(plugin, packets, runtime, UI, 0);

        Lab() {
            bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(ignored -> List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            when(runtime.terms()).thenReturn(new AuthRuntimeCapability.Terms(false, "", List.of()));
            when(scheduler.runTaskTimer(eq(plugin), pulse.capture(), eq(1L), eq(10L))).thenReturn(mock(BukkitTask.class));
        }

        void start() { controller.start(); }
        void pulse() { pulse.getValue().run(); }

        @Override public void close() {
            try { controller.close(); }
            finally { bukkit.close(); }
        }
    }
}
