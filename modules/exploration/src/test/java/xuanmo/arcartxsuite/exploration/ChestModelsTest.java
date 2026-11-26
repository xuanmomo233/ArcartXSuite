package xuanmo.arcartxsuite.exploration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;

class ChestModelsTest {
    private final AdyeshachNpcBridgeAPI bridge = mock(AdyeshachNpcBridgeAPI.class);
    private final Plugin plugin = mock(Plugin.class);
    private final Player owner = mock(Player.class);
    private final World world = mock(World.class);

    private void placeOwner() {
        when(owner.isOnline()).thenReturn(true);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenAnswer(call -> new Location(world, 0.5, 64, 0.5));
        when(owner.getEyeLocation()).thenAnswer(call -> new Location(world, 0.5, 65.62, 0.5));
        var air = mock(Block.class);
        when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(air);
        var floor = new RayTraceResult(new Vector(0.5, 64, 2.1), mock(Block.class), BlockFace.UP);
        when(world.rayTraceBlocks(any(), eq(new Vector(0, -1, 0)), eq(2.6), eq(FluidCollisionMode.NEVER), eq(true)))
            .thenReturn(floor);
        when(bridge.initialize()).thenReturn(true);
        when(bridge.registerInteractHandler(any())).thenReturn(true);
    }

    @Test void missingPluginLeavesOtherGameplayAvailable() {
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        var models = new ChestModels(null, plugin);
        assertFalse(models.spawn(owner, "cache", 1.0, ignored -> fail()));
        assertFalse(models.inReach(owner));
        models.shutdown();
    }

    @Test void failedModelSetupRemovesThePrivateEntity() {
        placeOwner();
        var models = new ChestModels(bridge, plugin);
        when(bridge.spawnPrivateMarker(eq(owner), anyString(), any())).thenReturn(new Object());
        assertFalse(models.spawn(owner, "cache", 1.0, ignored -> fail()));
        verify(bridge).removePrivateMarker(eq(owner), anyString());
        assertFalse(models.inReach(owner));
        models.shutdown();
        verify(bridge).shutdown();
    }
}
