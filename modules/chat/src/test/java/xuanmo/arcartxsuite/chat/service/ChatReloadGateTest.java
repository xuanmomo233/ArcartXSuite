package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class ChatReloadGateTest {
    @Test
    void reloadRejectsNativeMessagesOnceAndReleasesTheGateAfterHandoff() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var hint = new AtomicReference<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            hint.set(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var gate = new ChatReloadGate(plugin, "retry");
            gate.start();
            var message = new AsyncPlayerChatEvent(true, player, "private", new HashSet<>());
            gate.early(message);
            gate.late(message);
            assertTrue(message.isCancelled());
            verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
            hint.get().run();
            verify(player).sendMessage("retry");
            gate.close();
            var after = new AsyncPlayerChatEvent(true, player, "normal", new HashSet<>());
            assertFalse(gate.reject(after));
            assertFalse(after.isCancelled());
        }
    }
}
