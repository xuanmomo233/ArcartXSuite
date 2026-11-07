package xuanmo.arcartxsuite.auth.runtime;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import static org.mockito.Mockito.*;

class AdmissionGuardTest {
    @Test void disabledSuiteAuthenticationDoesNotBlockAnotherPluginsLoginCommand() {
        var guard=new AdmissionGuard(()->false);
        var event=mock(PlayerCommandPreprocessEvent.class);
        when(event.getPlayer()).thenReturn(mock(Player.class));
        guard.command(event);
        verify(event,never()).setCancelled(anyBoolean());
    }

    @Test void enabledSuiteAuthenticationStillBlocksUnknownConnections() {
        AuthAccess.install(()->null);
        var guard=new AdmissionGuard(()->true);
        var event=mock(PlayerCommandPreprocessEvent.class);
        var player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(event.getPlayer()).thenReturn(player);
        guard.command(event);
        verify(event).setCancelled(true);
    }
}
