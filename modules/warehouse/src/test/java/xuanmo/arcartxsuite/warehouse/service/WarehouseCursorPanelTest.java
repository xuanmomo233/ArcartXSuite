package xuanmo.arcartxsuite.warehouse.service;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

import static org.mockito.Mockito.*;

class WarehouseCursorPanelTest {
    @Test
    void aReleasedCapabilityIgnoresLateClientRequests() {
        WarehouseService service = mock(WarehouseService.class);
        PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        Player player = mock(Player.class);
        WarehouseCursorPanel panel = new WarehouseCursorPanel(service, bridge, Logger.getAnonymousLogger());
        panel.close();
        panel.handle(player, "warehouse", List.of("open"));
        verifyNoInteractions(service, bridge, player);
    }
}
