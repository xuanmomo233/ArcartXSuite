package xuanmo.arcartxsuite.regions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.packet.RegionsAdminPacketHandler;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;

class RegionsAdminPacketHandlerTest {
    private static final String ADMIN_UI = "AXS:regions_admin";
    private static final String ADMIN_PACKET = RegionsAdminPacketHandler.PACKET_ID;
    private static final String ADMIN_PERMISSION = "axs.regions.admin";

    @TempDir Path folder;
    private RegionManager manager;
    private Player player;
    private PacketBridgeAPI bridge;
    private RegionsAdminPacketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        Logger logger = Logger.getAnonymousLogger();
        var repository = new RegionsRepository(folder.resolve("regions.yml"));
        repository.applyChanges("Seed", "seed", List.of(), IntStream.range(0, 19)
            .mapToObj(i -> new Region("zone-" + i, "world", 0, 0, 0, 9, 10, 10)).toList());
        manager = new RegionManager(repository,
            RegionsConfiguration.load(new YamlConfiguration(), new YamlConfiguration(), logger),
            logger, Runnable::run, Runnable::run);
        manager.loadAll();

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(new UUID(0, 1));
        when(player.getName()).thenReturn("AdminTest");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(ADMIN_PERMISSION)).thenReturn(true);
        bridge = mock(PacketBridgeAPI.class);
        when(bridge.openUi(player, ADMIN_UI)).thenReturn(true);
        when(bridge.isUiOpen(player, ADMIN_UI)).thenReturn(true);
        handler = new RegionsAdminPacketHandler(null, bridge, manager, ADMIN_UI);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        Server server = mock(Server.class);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        serverField.set(null, server);
        try {
            handler.openMenu(player);
        } finally {
            serverField.set(null, previous);
        }
    }

    @Test
    void packetsRequireTheRegisteredOpenScreenAndCurrentAdminPermission() {
        when(bridge.isUiOpen(player, ADMIN_UI)).thenReturn(false);
        clearInvocations(bridge);
        send("ready");
        send("select", manager.getRegion("zone-0", "world").uid().toString());
        verify(bridge, times(2)).isUiOpen(player, ADMIN_UI);
        verifyNoMoreInteractions(bridge);

        when(bridge.isUiOpen(player, ADMIN_UI)).thenReturn(true);
        when(player.hasPermission(ADMIN_PERMISSION)).thenReturn(false);
        clearInvocations(bridge);
        send("ready");
        send("select", manager.getRegion("zone-0", "world").uid().toString());
        handler.openMenu(player);
        verifyNoInteractions(bridge);
        assertFalse(handler.handleClientPacket(player, "different-packet", List.of("ready")));
    }

    @Test
    void sendsOnlyVisiblePagesAndDoesNotResendRegionListForRuleChanges() {
        send("ready");
        assertEquals(8, ((List<?>) payloads("list", 1).get(0).get("rows")).size());
        String token = (String) payloads("credentials", 1).get(0).get("token");
        send("list", "regions", "2");
        assertEquals(3, ((List<?>) payloads("list", 2).get(1).get("rows")).size());
        send("select", manager.getRegion("zone-0", "world").uid().toString());
        send("rules", "misc", "0");
        send("flag", token, manager.getRegion("zone-0", "world").uid().toString(), "receive-chat", "deny");
        assertEquals(RegionFlag.State.DENY, manager.getRegion("zone-0", "world").getFlag(RegionFlag.RECEIVE_CHAT));
        send("flag", token, manager.getRegion("zone-0", "world").uid().toString(), "receive-chat", "allow");
        assertEquals(RegionFlag.State.DENY, manager.getRegion("zone-0", "world").getFlag(RegionFlag.RECEIVE_CHAT));
        payloads("list", 2);
        payloads("detail", 2);
        payloads("rules", 3);
        verify(bridge, never()).sendPacket(eq(player), eq(ADMIN_UI), eq("members"), any());
    }

    private void send(String... action) {
        assertTrue(handler.handleClientPacket(player, ADMIN_PACKET, List.of(action)));
    }

    private List<Map<?, ?>> payloads(String channel, int count) {
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(bridge, times(count)).sendPacket(eq(player), eq(ADMIN_UI), eq(channel), payload.capture());
        return payload.getAllValues().stream().<Map<?, ?>>map(value -> (Map<?, ?>) value).toList();
    }
}
