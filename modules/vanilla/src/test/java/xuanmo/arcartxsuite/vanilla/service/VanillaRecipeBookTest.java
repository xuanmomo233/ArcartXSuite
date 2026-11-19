package xuanmo.arcartxsuite.vanilla.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;

class VanillaRecipeBookTest {
    @Test
    void coalescesReadsAndDropsResponseFromABookThatWasClosedAndReopened() {
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        var view = mock(InventoryView.class);
        var inventory = mock(Inventory.class);
        var config = mock(VanillaConfiguration.class);
        var bridge = mock(PacketBridgeAPI.class);
        var warehouse = mock(WarehouseMaterialAccess.class);
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        var logger = Logger.getAnonymousLogger();
        when(plugin.getLogger()).thenReturn(logger);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getType()).thenReturn(InventoryType.WORKBENCH);
        when(inventory.getSize()).thenReturn(10);
        when(config.enabled(WorkshopType.CRAFTING)).thenReturn(true);
        when(config.uiId(any())).thenAnswer(call -> call.<WorkshopType>getArgument(0).uiId());
        String ui = WorkshopType.CRAFTING.uiId();
        when(bridge.isUiOpen(player, ui)).thenReturn(true);
        List<Consumer<WarehouseMaterialAccess.Snapshot>> reads = new ArrayList<>();
        doAnswer(call -> { reads.add(call.getArgument(1)); return null; }).when(warehouse).materialsAsync(eq(player), any());

        var server = mock(Server.class);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            var service = new VanillaService(plugin, bridge, null, null, config, logger);
            service.materialPlanner().setProvider(() -> warehouse);
            try {
                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                assertEquals(1, reads.size());
                service.handleClientPacket(player, ui, List.of("toggle_recipe_book"));
                verify(bridge).sendPacket(player, ui, "recipe_book_toggle", Map.of("open", false));
                service.handleClientPacket(player, ui, List.of("toggle_recipe_book"));
                assertEquals(2, reads.size());

                var snapshot = new WarehouseMaterialAccess.Snapshot("personal", List.of(), "");
                reads.get(0).accept(snapshot);
                verify(bridge, times(1)).sendPacket(eq(player), eq(ui), eq("recipe_book_toggle"), any());
                reads.get(1).accept(snapshot);
                verify(bridge).sendPacket(player, ui, "recipe_book_toggle", Map.of("open", true, "recipes", Map.of(), "recipeCount", 0));

                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                assertEquals(3, reads.size());
                service.materialPlanner().setProvider(() -> null);
                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                reads.get(2).accept(snapshot);
                verify(bridge, times(2)).sendPacket(player, ui, "recipe_book_toggle",
                    Map.of("open", true, "recipes", Map.of(), "recipeCount", 0));
            } finally {
                service.shutdown();
            }
        }
    }

    @Test
    void bookAndDetailsShareOneWarehouseReadAndUseTheLatestRequestedQuantity() {
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        var view = mock(InventoryView.class);
        var grid = mock(CraftingInventory.class);
        var storage = mock(PlayerInventory.class);
        var config = mock(VanillaConfiguration.class);
        var bridge = mock(PacketBridgeAPI.class);
        var warehouse = mock(WarehouseMaterialAccess.class);
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getInventory()).thenReturn(storage);
        when(storage.getStorageContents()).thenReturn(new ItemStack[36]);
        when(view.getTopInventory()).thenReturn(grid);
        when(view.getType()).thenReturn(InventoryType.WORKBENCH);
        when(grid.getSize()).thenReturn(10);
        when(grid.getMatrix()).thenReturn(new ItemStack[9]);
        when(config.enabled(WorkshopType.CRAFTING)).thenReturn(true);
        when(config.uiId(any())).thenAnswer(call -> call.<WorkshopType>getArgument(0).uiId());
        String ui = WorkshopType.CRAFTING.uiId();
        when(bridge.isUiOpen(player, ui)).thenReturn(true);
        List<Consumer<WarehouseMaterialAccess.Snapshot>> reads = new ArrayList<>();
        doAnswer(call -> { reads.add(call.getArgument(1)); return null; }).when(warehouse).materialsAsync(eq(player), any());

        var key = new NamespacedKey("test", "sample");
        when(player.hasDiscoveredRecipe(key)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            var itemFactory = mock(ItemFactory.class);
            when(itemFactory.equals(null, null)).thenReturn(true);
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            var recipe = new ShapedRecipe(key, new ItemStack(Material.STICK)).shape("X");
            recipe.setIngredient('X', Material.OAK_PLANKS);
            bukkit.when(() -> Bukkit.getRecipe(key)).thenReturn(recipe);
            var service = new VanillaService(plugin, bridge, null, null, config, Logger.getAnonymousLogger());
            service.materialPlanner().setProvider(() -> warehouse);
            try {
                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                service.handleClientPacket(player, ui, List.of("inspect_recipe", key.toString(), "1"));
                service.handleClientPacket(player, ui, List.of("inspect_recipe", key.toString(), "4"));
                assertEquals(1, reads.size());
                var snapshot = new WarehouseMaterialAccess.Snapshot("personal", List.of(), "");
                reads.get(0).accept(snapshot);
                verify(bridge).sendPacket(eq(player), eq(ui), eq("recipe_book_toggle"), any());
                verify(bridge).sendPacket(eq(player), eq(ui), eq("recipe_details"),
                    argThat(data -> data instanceof Map<?, ?> packet && Integer.valueOf(4).equals(packet.get("quantity"))));

                service.handleClientPacket(player, ui, List.of("inspect_recipe", key.toString(), "2"));
                assertEquals(2, reads.size());
                auth.when(() -> AuthAccess.admitted(id)).thenReturn(false);
                reads.get(1).accept(snapshot);
                verify(bridge, times(1)).sendPacket(eq(player), eq(ui), eq("recipe_details"), any());

                auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
                when(bridge.sendPacket(eq(player), eq(ui), eq("recipe_book_toggle"), any()))
                    .thenThrow(new IllegalStateException("test book send failure"));
                service.handleClientPacket(player, ui, List.of("show_recipe_book"));
                service.handleClientPacket(player, ui, List.of("inspect_recipe", key.toString(), "3"));
                assertEquals(3, reads.size());
                assertThrows(IllegalStateException.class, () -> reads.get(2).accept(snapshot));
                verify(bridge).sendPacket(eq(player), eq(ui), eq("recipe_details"),
                    argThat(data -> data instanceof Map<?, ?> packet && Integer.valueOf(3).equals(packet.get("quantity"))));
            } finally {
                service.shutdown();
            }
        }
    }
}
