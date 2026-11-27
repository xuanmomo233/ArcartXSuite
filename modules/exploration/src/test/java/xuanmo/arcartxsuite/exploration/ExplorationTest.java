package xuanmo.arcartxsuite.exploration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;

class ExplorationTest {
    private static final String AREA = "regions.forest_ruins.";
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private final Discoveries discoveries = mock(Discoveries.class);
    private final AtomicBoolean admitted = new AtomicBoolean(true);
    private final AtomicReference<Long> expiry = new AtomicReference<>();
    private final YamlConfiguration yaml = defaults();
    private ExplorationConfig.Region region;
    private ExplorationService service;
    private Location start;

    private static YamlConfiguration defaults() {
        var stream = Objects.requireNonNull(ExplorationTest.class.getResourceAsStream("/ArcartXExploration.yml"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    @BeforeEach void setup() {
        when(world.getName()).thenReturn("world");
        start = new Location(world, 100.5, 61, 108);
        when(player.getLocation()).thenAnswer(call -> start.clone());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var view = mock(InventoryView.class);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        when(player.getOpenInventory()).thenReturn(view);
        when(bridge.openUi(player, "hud")).thenReturn(true);
        when(bridge.sendPacket(eq(player), eq("hud"), eq("sync"), any())).thenReturn(true);
        yaml.set(AREA + "enabled", true);
        yaml.set(AREA + "duration-ticks", 6);
        var config = ExplorationConfig.load(yaml);
        region = config.worlds().get("world").get(0);
        var data = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.get(region.cooldownKey(), PersistentDataType.LONG)).thenAnswer(call -> expiry.get());
        doAnswer(call -> { expiry.set(call.getArgument(2)); return null; })
            .when(data).set(eq(region.cooldownKey()), eq(PersistentDataType.LONG), anyLong());
        when(discoveries.resolve(eq(player), eq(region), any())).thenReturn(true);
        service = new ExplorationService(config, bridge, "hud", discoveries, mock(MessageProvider.class),
            ignored -> admitted.get(), Logger.getAnonymousLogger());
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) service.tick(List.of(player));
    }

    private void startSearch() {
        ticks(5);
        service.press(player);
        ticks(1);
        verify(bridge).sendPacket(player, "hud", "sync", Map.of("name", region.name(), "percent", 0));
    }

    @Test void repeatedPressesCompleteOnceAfterFullDurationAndPersistCooldown() {
        startSearch();
        for (int i = 0; i < 5; i++) { service.press(player); ticks(1); }
        verify(discoveries, never()).resolve(any(), any(), any());
        ticks(1);
        verify(bridge).sendPacket(player, "hud", "sync", Map.of("name", region.name(), "percent", 100));
        service.press(player);
        ticks(15);
        verify(discoveries, times(1)).resolve(eq(player), eq(region), any());
        assertTrue(expiry.get() > System.currentTimeMillis());
        verify(bridge, times(1)).openUi(player, "hud");
    }

    @Test void movingAwayAndBackBetweenTicksCancelsButLookingAroundDoesNot() {
        startSearch();
        Location look = start.clone();
        look.setYaw(88);
        service.onMove(new PlayerMoveEvent(player, start, look));
        verify(bridge, never()).closeUi(player, "hud");
        service.onMove(new PlayerMoveEvent(player, look, start.clone().add(0.4, 0, 0)));
        verify(bridge).closeUi(player, "hud");
        ticks(20);
        verify(discoveries, never()).resolve(any(), any(), any());
        assertNull(expiry.get());
    }

    @Test void teleportEvenWithinAreaCancelsAndRequiresAnotherPress() {
        startSearch();
        service.onTeleport(new PlayerTeleportEvent(player, start, start.clone()));
        ticks(15);
        verify(discoveries, never()).resolve(any(), any(), any());
    }

    @Test void lossOfAdmissionBeforeFinalTickCancels() {
        startSearch();
        ticks(5);
        admitted.set(false);
        ticks(1);
        verify(discoveries, never()).resolve(any(), any(), any());
        verify(bridge).closeUi(player, "hud");
    }

    @Test void outsidePlayerCannotStartWithStaleHud() {
        ticks(5);
        start.setY(200);
        service.press(player);
        ticks(20);
        verify(discoveries, never()).resolve(any(), any(), any());
        verify(bridge).closeUi(player, "hud");
    }

    @Test void deniedDiscoveryLeavesRegionAvailable() {
        when(discoveries.resolve(eq(player), eq(region), any())).thenReturn(false);
        startSearch();
        ticks(12);
        verify(bridge, times(2)).openUi(player, "hud");
        assertNull(expiry.get());
    }

    @Test void negativeReversedCornersIncludeBothEdgeBlocksAndRejectOtherWorld() {
        yaml.set(AREA + "corner-a", List.of(0, 10, 2));
        yaml.set(AREA + "corner-b", List.of(-4, 8, -8));
        var bounds = ExplorationConfig.load(yaml).worlds().get("world").get(0);
        assertTrue(bounds.contains(new Location(world, -4.0, 8.0, -8.0)));
        assertTrue(bounds.contains(new Location(world, 0.99, 10.99, 2.99)));
        assertFalse(bounds.contains(new Location(world, -4.01, 8, 0)));
        World other = mock(World.class);
        when(other.getName()).thenReturn("nether");
        assertFalse(bounds.contains(new Location(other, 0, 9, 0)));
    }

    @Test void bundledPoolHasExactWeightBoundariesAndDisabledOptIns() {
        assertTrue(ExplorationConfig.load(defaults()).worlds().isEmpty());
        assertEquals(List.of(new ExplorationConfig.Loot("minecraft", "SWEET_BERRIES", 3)),
            region.outcomes().get(0).items());
        assertEquals(List.of(new ExplorationConfig.Loot("minecraft", "IRON_INGOT", 2),
            new ExplorationConfig.Loot("minecraft", "EMERALD", 1)), region.outcomes().get(1).items());
        var random = mock(RandomGenerator.class);
        when(random.nextLong(100)).thenReturn(0L, 69L, 70L, 99L);
        for (var kind : List.of("GATHER", "GATHER", "CHEST", "CHEST")) assertEquals(kind, region.draw(random).kind().name());
        yaml.set(AREA + "outcomes.herbs.weight", 0);
        yaml.set(AREA + "outcomes.cache.weight", 0);
        assertThrows(IllegalArgumentException.class, () -> ExplorationConfig.load(yaml));
    }
}
