package xuanmo.arcartxsuite.vanilla.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;

class InventorySorterTest {
    @Test
    void compactsOnlySimilarItemsWithinTheStackLimit() {
        ItemStack hotbar = new ItemStack(Material.IRON_INGOT, 17);
        ItemStack first = new ItemStack(Material.IRON_INGOT, 40);
        ItemStack second = new ItemStack(Material.IRON_INGOT, 31);
        ItemStack overLimit = new ItemStack(Material.COAL, 90);
        ItemStack named = new ItemStack(Material.IRON_INGOT, 8);
        Damageable meta = mock(Damageable.class);
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.isApplicable(meta, Material.IRON_INGOT)).thenReturn(true);
        when(factory.asMetaFor(meta, Material.IRON_INGOT)).thenReturn(meta);
        when(factory.updateMaterial(meta, Material.IRON_INGOT)).thenReturn(Material.IRON_INGOT);
        when(meta.clone()).thenReturn(meta);
        when(factory.equals(null, null)).thenReturn(true);
        when(factory.equals(meta, meta)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            assertTrue(named.setItemMeta(meta));
            ItemStack[] original = {hotbar, null, first, overLimit, named, second, new ItemStack(Material.COAL, 12)};

            ItemStack[] sorted = InventorySorter.arrange(original, 1, 64, InventorySorter.Mode.AMOUNT);

            assertSame(hotbar, sorted[0]);
            assertEquals(64, sorted[1].getAmount());
            assertEquals(Material.COAL, sorted[2].getType());
            assertEquals(12, sorted[2].getAmount());
            assertSame(overLimit, sorted[3]);
            assertTrue(named.isSimilar(sorted[4]));
            assertEquals(8, sorted[4].getAmount());
            assertEquals(Material.IRON_INGOT, sorted[5].getType());
            assertEquals(7, sorted[5].getAmount());
            assertFalse(named.isSimilar(sorted[5]));
            assertNull(sorted[6]);
            assertEquals(40, first.getAmount());
            assertEquals(31, second.getAmount());
            assertArrayEquals(sorted, InventorySorter.arrange(sorted, 1, 64, InventorySorter.Mode.AMOUNT));
        }
    }

    @Test
    void restoresAlreadyWrittenSlotsIfAContainerWriteFails() {
        ItemStack hotbar = new ItemStack(Material.STICK, 5);
        ItemStack original = new ItemStack(Material.COAL, 13);
        ItemStack[] slots = {hotbar, null, original};
        Inventory inventory = mock(Inventory.class);
        when(inventory.getStorageContents()).thenReturn(slots);
        when(inventory.getMaxStackSize()).thenReturn(64);
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            int index = call.getArgument(0);
            ItemStack item = call.getArgument(1);
            slots[index] = item;
            if (index == 2 && item == null && failOnce.getAndSet(false)) throw new IllegalStateException("write failed");
            return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));

        assertThrows(IllegalStateException.class, () -> InventorySorter.sort(inventory, 1, InventorySorter.Mode.TYPE));
        assertSame(hotbar, slots[0]);
        assertNull(slots[1]);
        assertEquals(Material.COAL, slots[2].getType());
        assertEquals(13, slots[2].getAmount());
        verify(inventory, never()).setItem(eq(0), nullable(ItemStack.class));
    }

    @Test
    void requiresARealContainerAndChecksBothHalvesOfADoubleChest() {
        Player player = mock(Player.class);
        Inventory ownEnderChest = mock(Inventory.class);
        Inventory otherEnderChest = mock(Inventory.class);
        when(player.getEnderChest()).thenReturn(ownEnderChest);
        when(ownEnderChest.getType()).thenReturn(InventoryType.ENDER_CHEST);
        when(otherEnderChest.getType()).thenReturn(InventoryType.ENDER_CHEST);
        assertTrue(UtilityWorkbenchService.isSortableStorage(player, ownEnderChest));
        assertFalse(UtilityWorkbenchService.isSortableStorage(player, otherEnderChest));

        Inventory virtual = mock(Inventory.class);
        when(virtual.getType()).thenReturn(InventoryType.CHEST);
        assertFalse(UtilityWorkbenchService.isSortableStorage(player, virtual));
        for (InventoryType excluded : new InventoryType[]{InventoryType.HOPPER, InventoryType.DISPENSER, InventoryType.DROPPER}) {
            when(virtual.getType()).thenReturn(excluded);
            assertFalse(UtilityWorkbenchService.isSortableStorage(player, virtual));
        }

        DoubleChestInventory doubleChest = mock(DoubleChestInventory.class);
        Inventory leftSide = chestAt(mock(Block.class));
        Block rightBlock = mock(Block.class);
        Inventory rightSide = chestAt(rightBlock);
        when(doubleChest.getType()).thenReturn(InventoryType.CHEST);
        when(doubleChest.getLeftSide()).thenReturn(leftSide);
        when(doubleChest.getRightSide()).thenReturn(rightSide);
        World leftWorld = leftSide.getLocation().getWorld();
        World rightWorld = rightSide.getLocation().getWorld();
        UUID leftWorldId = new UUID(0, 1);
        UUID rightWorldId = new UUID(0, 2);
        when(leftWorld.getUID()).thenReturn(leftWorldId);
        when(rightWorld.getUID()).thenReturn(rightWorldId);
        when(leftWorld.isChunkLoaded(0, 0)).thenReturn(true);
        when(rightWorld.isChunkLoaded(0, 0)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(leftWorldId)).thenReturn(leftWorld);
            bukkit.when(() -> Bukkit.getWorld(rightWorldId)).thenReturn(rightWorld);
            assertTrue(UtilityWorkbenchService.isSortableStorage(player, doubleChest));
            Chest replacement = mock(Chest.class);
            Inventory replacementInventory = mock(Inventory.class);
            when(replacement.getBlockInventory()).thenReturn(replacementInventory);
            when(rightBlock.getState()).thenReturn(replacement);
            assertFalse(UtilityWorkbenchService.isSortableStorage(player, doubleChest));
        }
    }

    private static Inventory chestAt(Block block) {
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        World world = mock(World.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getState()).thenReturn(chest);
        when(inventory.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        when(inventory.getHolder()).thenReturn(chest);
        when(chest.getBlockInventory()).thenReturn(inventory);
        return inventory;
    }
}
