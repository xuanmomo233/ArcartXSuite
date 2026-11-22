package xuanmo.arcartxsuite.warehouse.service;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseCursorPanelTest {
    @Test
    void backpackUsesTheUnifiedPasswordSessionOutsideMainWarehouseActions() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartXSuite");
        var access = mock(SecondaryPasswordAccess.class);
        var player = mock(Player.class);
        var service = new WarehouseService(plugin, Logger.getAnonymousLogger(), null, null, null, null,
            mock(WarehouseModuleConfiguration.class), null, null, null, null, null,
            null, null, () -> access, null);
        var panel = new WarehouseCursorPanel(service, null, Logger.getAnonymousLogger());
        var canWrite = WarehouseCursorPanel.class.getDeclaredMethod("canWrite", Player.class);
        canWrite.setAccessible(true);
        assertFalse(service.isSecondaryUnlocked(player));

        when(access.isUnlocked(player)).thenReturn(true);
        assertEquals(true, canWrite.invoke(panel, player));
        verify(player, never()).sendMessage(anyString());

        when(access.isUnlocked(player)).thenReturn(false);
        assertEquals(false, canWrite.invoke(panel, player));
        verify(player).sendMessage("仓库已锁定，请先解锁二级密码。");
        verify(access, times(2)).isUnlocked(player);
    }

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
