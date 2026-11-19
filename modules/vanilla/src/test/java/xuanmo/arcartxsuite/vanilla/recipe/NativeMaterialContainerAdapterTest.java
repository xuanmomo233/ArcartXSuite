package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class NativeMaterialContainerAdapterTest {
    @Test
    void doesNotAccessABlockWhenItsWorldIsUnloaded() {
        Inventory inventory = mock(Inventory.class);
        Location location = mock(Location.class);
        when(inventory.getType()).thenReturn(InventoryType.FURNACE);
        when(inventory.getLocation()).thenReturn(location);
        when(location.isWorldLoaded()).thenReturn(false);

        assertFalse(NativeMaterialContainerAdapter.persistentInventoryUnchanged(null, inventory));
        verify(location, never()).getWorld();
        verify(location, never()).getBlock();
    }

    @Test
    void probesTheBackingInventoryOnlyWhenItsChunkIsLoaded() {
        FurnaceInventory inventory = mock(FurnaceInventory.class);
        Location location = mock(Location.class);
        World world = mock(World.class);
        when(inventory.getType()).thenReturn(InventoryType.FURNACE);
        when(inventory.getLocation()).thenReturn(location);
        when(location.isWorldLoaded()).thenReturn(true);
        when(location.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(-1);
        when(location.getBlockZ()).thenReturn(-17);
        when(world.isChunkLoaded(-1, -2)).thenReturn(false, true);

        assertFalse(NativeMaterialContainerAdapter.persistentInventoryUnchanged(null, inventory));
        verify(world, never()).getBlockAt(location);

        Block block = mock(Block.class);
        Furnace furnace = mock(Furnace.class);
        when(world.getBlockAt(location)).thenReturn(block);
        when(block.getState()).thenReturn(furnace);
        when(furnace.getInventory()).thenReturn(inventory);

        assertTrue(NativeMaterialContainerAdapter.persistentInventoryUnchanged(null, inventory));
        verify(world).getBlockAt(location);
    }

    @Test
    void acceptsThePlayersEnderChestWithoutABlockLookup() {
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getType()).thenReturn(InventoryType.ENDER_CHEST);
        when(player.getEnderChest()).thenReturn(inventory);

        assertTrue(NativeMaterialContainerAdapter.persistentInventoryUnchanged(player, inventory));
        verify(inventory, never()).getLocation();
    }
}
