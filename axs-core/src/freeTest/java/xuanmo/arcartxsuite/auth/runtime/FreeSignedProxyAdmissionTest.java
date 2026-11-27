package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.storage.StorageManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreeSignedProxyAdmissionTest {
    @Test void signedProxyConfigurationRemainsRestrictedWithoutConsultingSeatsOrOpeningChannels() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
            schema-version: 1
            backend: standalone
            realm-id: test
            requirements:
              terms: false
            ingress:
              type: signed-proxy
              trusted-proxies: [unused]
            """);
        var settings = AuthenticationSettings.read(config, new LoginTransport(false, true, List.of()));
        assertEquals(AuthenticationSettings.Ingress.SIGNED_PROXY, settings.ingress());
        var player = mock(Player.class);
        var playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alex");
        try (var bukkit = mockStatic(Bukkit.class);
             var runtime = new AuthenticationRuntime(mock(JavaPlugin.class), mock(StorageManager.class),
                 () -> null, settings, () -> { throw new AssertionError("Free ingress consulted a customer seat"); })) {
            runtime.onJoin(new PlayerJoinEvent(player, ""));
            runtime.refreshProxyIngress();
            assertTrue(runtime.requiresAdmission());
            assertFalse(runtime.passwordAuthenticationAllowed());
            assertEquals(AuthSnapshot.Stage.RESTRICTED, runtime.snapshot(playerId).stage());
            assertFalse(runtime.snapshot(playerId).admitted());
            assertNull(runtime.proxyChallenge(player));
            runtime.acceptProxyProof(player, runtime.snapshot(playerId).connectionId(), "test proof");
            assertFalse(runtime.snapshot(playerId).identityVerified());
            bukkit.verifyNoInteractions();
        }
    }
}
