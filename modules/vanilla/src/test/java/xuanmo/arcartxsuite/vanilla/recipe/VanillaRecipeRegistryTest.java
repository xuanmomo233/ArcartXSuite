package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station;
import xuanmo.arcartxsuite.api.inventory.InventoryOperation;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

class VanillaRecipeRegistryTest {
    @TempDir Path directory;

    @Test
    void staleLeaseCannotReleaseAnotherOwnersInventory() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var fill = InventoryOperation.tryAcquire(player);
        assertNotNull(fill);
        assertNull(InventoryOperation.tryAcquire(player));
        fill.close();
        try (var exchange = InventoryOperation.tryAcquire(player)) {
            assertNotNull(exchange);
            fill.close();
            assertTrue(InventoryOperation.busy(player));
        }
        assertFalse(InventoryOperation.busy(player));
    }

    @Test
    void nativeStationsWithoutPlayerContextRejectRestrictedRecipes() {
        var ingredient = new Ingredient(List.of(Material.IRON_ORE), List.of());
        var furnace = new Definition("restricted_furnace", Station.FURNACE, List.of(ingredient),
            new ItemStack(Material.IRON_INGOT), "axs.recipe.restricted", 200, 0, SmithingData.INHERIT_BASE);
        var brewing = new Definition("restricted_brewing", Station.BREWING, List.of(ingredient, ingredient),
            new ItemStack(Material.POTION), "axs.recipe.restricted", 200, 0, SmithingData.INHERIT_BASE);
        for (Definition recipe : List.of(furnace, brewing)) {
            var failure = assertThrows(IllegalArgumentException.class,
                () -> NativeRecipeFactory.create(NamespacedKey.fromString("axs:test"), recipe));
            assertTrue(failure.getMessage().contains("不支持玩家权限限制"));
        }
    }

    @Test
    void editRevisionAndFailedNativeRegistrationKeepPreviousRecipe() throws Exception {
        var nativeRecipes = new HashMap<NamespacedKey, Recipe>();
        ItemFactory itemFactory = mock(ItemFactory.class);
        when(itemFactory.equals(null, null)).thenReturn(true);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3465);
        when(unsafe.getMaterial(anyString(), anyInt())).thenAnswer(call -> Material.valueOf(call.getArgument(0)));
        try (var server = mockStatic(Bukkit.class)) {
            server.when(Bukkit::getServer).thenReturn(mock(Server.class));
            server.when(Bukkit::isPrimaryThread).thenReturn(true);
            server.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            server.when(Bukkit::getUnsafe).thenReturn(unsafe);
            server.when(Bukkit::getWorlds).thenReturn(List.of());
            server.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            server.when(() -> Bukkit.getRecipe(any())).thenAnswer(call -> nativeRecipes.get(call.getArgument(0)));
            server.when(() -> Bukkit.addRecipe(any())).thenAnswer(call -> {
                Recipe recipe = call.getArgument(0);
                return recipe.getResult().getType() != Material.GOLD_INGOT
                    && nativeRecipes.putIfAbsent(((Keyed) recipe).getKey(), recipe) == null;
            });
            server.when(() -> Bukkit.removeRecipe(any())).thenAnswer(call -> nativeRecipes.remove(call.getArgument(0)) != null);
            try (var registry = new VanillaRecipeRegistry(directory.toFile(), mock(ItemSourceRegistry.class), new RecipeMaterialPlanner())) {
                registry.reload();
                long first = registry.editRevision();
                registry.save("fixture", recipe("fixture", Material.IRON_INGOT), first);
                var saved = registry.recipes().get(0);
                assertInstanceOf(ShapelessRecipe.class, nativeRecipes.get(saved.key()));
                assertThrows(IllegalStateException.class, () -> registry.save("fixture", null, first));
                assertThrows(IllegalStateException.class, () ->
                    registry.save("fixture", recipe("fixture", Material.GOLD_INGOT), registry.editRevision()));
                assertEquals(saved, registry.recipe(saved.key()).orElseThrow());
                assertEquals(Material.IRON_INGOT, nativeRecipes.get(saved.key()).getResult().getType());
                registry.reload();
                assertEquals(saved.revision(), registry.recipe(saved.key()).orElseThrow().revision());

                Plugin plugin = mock(Plugin.class);
                when(plugin.getName()).thenReturn("OtherCrafts");
                when(plugin.isEnabled()).thenReturn(true);
                var handle = registry.register(plugin, recipe("another", Material.IRON_INGOT));
                assertTrue(nativeRecipes.containsKey(handle.key()));
                handle.close();
                assertFalse(nativeRecipes.containsKey(handle.key()));
            }
            assertTrue(nativeRecipes.isEmpty());
        }
    }

    private static Definition recipe(String id, Material result) {
        return new Definition(id, Station.SHAPELESS, List.of(new Ingredient(List.of(Material.DIRT), List.of())),
            new ItemStack(result), "", 200, 0, SmithingData.INHERIT_BASE);
    }
}
