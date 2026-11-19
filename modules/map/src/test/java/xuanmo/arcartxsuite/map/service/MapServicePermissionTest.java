package xuanmo.arcartxsuite.map.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;
import xuanmo.arcartxsuite.map.storage.MapRepository;

class MapServicePermissionTest {
    private static final String MENU_ID = "AXS_MAP";

    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private final MapRepository repository = mock(MapRepository.class);
    private final Player player = mock(Player.class);
    private MapService service;

    @BeforeEach
    void setUp() {
        var configuration = mock(MapModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(configuration.client().packetId()).thenReturn(MENU_ID);
        service = new MapService(null, Logger.getAnonymousLogger(), null, configuration, repository,
            bridge, MENU_ID, "AXS_MAP_HUD", null, null, null, null);
        when(player.isOnline()).thenReturn(true);
    }

    @Test
    void rejectsOpenAndNavigateBeforePlayerTargetLookupWithoutMapUse() {
        assertFalse(service.openMenuFor(player, "quest-target").success());
        assertFalse(service.navigateExternalTarget(player, "quest-target"));

        verify(player, never()).getUniqueId();
        verify(player, never()).getWorld();
        verifyNoInteractions(repository, bridge);
    }

    @Test
    void rejectsStaffEntryForUnprivilegedSenderBeforePlayerTargetLookup() {
        CommandSender sender = mock(CommandSender.class);

        assertFalse(service.openMenuForAdmin(sender, player, "quest-target").success());

        verify(player, never()).getUniqueId();
        verify(player, never()).getWorld();
        verifyNoInteractions(repository, bridge);
    }

    @Test
    @SuppressWarnings("unchecked")
    void removingAnExternalTargetRequiresItsSourceAndClearsItsSelection() throws Exception {
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(false);
        var field = MapService.class.getDeclaredField("externalTargets");
        field.setAccessible(true);
        var external = (ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>>) field.get(service);
        var targets = new ConcurrentHashMap<String, MapExternalTarget>();
        targets.put("quest", new MapExternalTarget("quest", "questgps", "world", "quest", "", 1, 64, 1,
            null, true, 0, false));
        targets.put("other", new MapExternalTarget("other", "other-source", "world", "other", "", 2, 64, 2,
            null, false, 0, false));
        external.put(playerId, targets);
        var view = new MapPlayerViewState();
        view.selectExternalTarget("quest");
        service.viewStates.put(playerId, view);
        var navigationField = MapService.class.getDeclaredField("navigationStates");
        navigationField.setAccessible(true);
        var navigation = (ConcurrentMap<UUID, LinkedHashMap<String, MapNavigationState>>) navigationField.get(service);
        var otherNavigation = new MapNavigationState(true, MapNavigationState.TargetType.EXTERNAL,
            "world", "quest", "other-waypoint", "other", "other-source");
        var routes = new LinkedHashMap<String, MapNavigationState>();
        routes.put("external:quest", otherNavigation);
        navigation.put(playerId, routes);

        assertFalse(service.removeExternalTarget(player, "other-source", "quest"));
        assertEquals("quest", view.selectedExternalTargetId());
        assertTrue(service.removeExternalTarget(player, "QuestGPS", "Quest"));
        assertFalse(service.removeExternalTarget(player, "questgps", "quest"));
        assertEquals("", view.selectedExternalTargetId());
        assertTrue(targets.containsKey("other"));
        assertEquals(otherNavigation, routes.get("external:quest"));
        verifyNoInteractions(bridge, repository);
    }

    @Test
    void staffCanOpenTheMenuForAPlayerWithoutMapUse() {
        CommandSender sender = mock(CommandSender.class);
        World world = mock(World.class);
        when(sender.hasPermission("arcartxsuite.admin")).thenReturn(true);
        when(world.getName()).thenReturn("test_world");
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AtomicBoolean online = new AtomicBoolean(true);
        when(player.isOnline()).thenAnswer(invocation -> online.get());
        doAnswer(invocation -> {
            online.set(false);
            return true;
        }).when(bridge).openUi(player, MENU_ID);

        assertTrue(service.openMenuForAdmin(sender, player, "").success());

        verify(bridge).openUi(player, MENU_ID);
        verify(player, never()).hasPermission("arcartxsuite.map.use");
    }
}
