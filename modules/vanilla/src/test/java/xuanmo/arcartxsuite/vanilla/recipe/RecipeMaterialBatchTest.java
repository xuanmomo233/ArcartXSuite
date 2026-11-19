package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;

class RecipeMaterialBatchTest {
    @Test
    void recipeBookReadsSlotsOnceAndStillRequiresEachIngredient() {
        var player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        var view = mock(InventoryView.class);
        var grid = mock(CraftingInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(grid);
        when(grid.getSize()).thenReturn(10);
        when(grid.getMatrix()).thenReturn(new ItemStack[9]);
        var stack = new ItemStack(Material.OAK_PLANKS);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stack});

        ItemFactory factory = mock(ItemFactory.class);
        when(factory.equals(null, null)).thenReturn(true);
        try (var server = mockStatic(Bukkit.class)) {
            server.when(Bukkit::getItemFactory).thenReturn(factory);
            var stock = new MaterialStock(null, new WarehouseMaterialAccess.Snapshot("personal", List.of(
                new WarehouseMaterialAccess.Entry(4, "iron", 1, 1, new ItemStack(Material.IRON_INGOT))), ""));
            var available = new RecipeMaterialPlanner().availability(player, WorkshopType.CRAFTING, stock);
            var single = new ShapedRecipe(new NamespacedKey("test", "single"), new ItemStack(Material.STICK)).shape("X");
            single.setIngredient('X', new RecipeChoice.MaterialChoice(Material.OAK_PLANKS));
            var doubleUse = new ShapedRecipe(new NamespacedKey("test", "double"), new ItemStack(Material.STICK)).shape("XX");
            doubleUse.setIngredient('X', new RecipeChoice.MaterialChoice(Material.OAK_PLANKS));
            var missing = new ShapedRecipe(new NamespacedKey("test", "missing"), new ItemStack(Material.TORCH)).shape("X");
            missing.setIngredient('X', new RecipeChoice.MaterialChoice(Material.COAL));
            var warehouse = new ShapedRecipe(new NamespacedKey("test", "warehouse"), new ItemStack(Material.SHEARS)).shape("X");
            warehouse.setIngredient('X', new RecipeChoice.MaterialChoice(Material.IRON_INGOT));
            assertTrue(available.canMake(single));
            assertFalse(available.canMake(doubleUse));
            assertFalse(available.canMake(missing));
            assertTrue(available.canMake(warehouse));
            assertTrue(available.canMake(single));
        }
        assertEquals(1, stack.getAmount());
        verify(inventory).getStorageContents();
        verify(grid).getMatrix();
    }

    @Test
    void batchCombinesSimilarStacksButKeepsEachGridCellHomogeneous() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        InventoryView view = mock(InventoryView.class);
        CraftingInventory grid = mock(CraftingInventory.class);
        World world = mock(World.class);
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.equals(null, null)).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getWorld()).thenReturn(world);
        when(view.getTopInventory()).thenReturn(grid);
        when(grid.getSize()).thenReturn(10);
        when(grid.getMatrix()).thenReturn(new ItemStack[9]);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{
            new ItemStack(Material.OAK_PLANKS, 2), new ItemStack(Material.BIRCH_PLANKS, 4)
        });
        var key = new NamespacedKey("test", "batch");
        var stock = new MaterialStock(null, new WarehouseMaterialAccess.Snapshot("personal", List.of(
            new WarehouseMaterialAccess.Entry(3, "oak", 1, 3, new ItemStack(Material.OAK_PLANKS))), ""));
        try (var server = mockStatic(Bukkit.class)) {
            server.when(Bukkit::getItemFactory).thenReturn(factory);
            var recipe = new ShapedRecipe(key, new ItemStack(Material.STICK)).shape("X");
            recipe.setIngredient('X', new RecipeChoice.MaterialChoice(Material.OAK_PLANKS, Material.BIRCH_PLANKS));
            server.when(() -> Bukkit.getCraftingRecipe(any(ItemStack[].class), any(World.class))).thenReturn(recipe);
            RecipeMaterialPlanner planner = new RecipeMaterialPlanner();
            var preview = planner.previewCrafting(player, key, recipe, stock);
            assertEquals(5, preview.maximum());
            assertEquals(1, preview.rows().get(0).missing(10));
            verify(inventory).getStorageContents();
            verify(grid).getMatrix();
        }
    }
}
