package xuanmo.arcartxsuite.auth.runtime;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerAdmissionRuntimeTest {
    @Test void normalServerJoinAdmitsWithoutClaimingSuiteIdentityVerification() {
        var plugin=mock(JavaPlugin.class);
        var scheduler=mock(BukkitScheduler.class);
        var manager=mock(PluginManager.class);
        var player=mock(Player.class);
        UUID profile=UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(profile);
        var join=mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        var quit=mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var runtime=new ServerAdmissionRuntime(plugin,()->null,ZoneId.of("Asia/Shanghai"));
            runtime.start();
            runtime.join(join);
            var state=runtime.snapshot(profile);
            assertTrue(state.admitted());
            assertFalse(state.identityVerified());
            assertNull(state.accountId());
            assertFalse(runtime.requiresAdmission());
            assertTrue(runtime.refresh(profile,state.connectionId()).toCompletableFuture().join().success());
            runtime.quit(quit);
            assertFalse(runtime.snapshot(profile).admitted());
            runtime.close();
        }
    }
}
