package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.InventoryOperation;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;

class RecipeMaterialFeatureTest {
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final CraftingInventory grid = mock(CraftingInventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final World world = mock(World.class);
    private final RecipeMaterialPlanner planner = new RecipeMaterialPlanner();
    private final ItemStack[] storage = new ItemStack[36];
    private final ItemStack[] matrix = new ItemStack[9];
    private final AtomicReference<String> message = new AtomicReference<>();
    private final NamespacedKey key = new NamespacedKey("test", "planks");
    private MockedStatic<Bukkit> bukkit;
    private ShapedRecipe recipe;

    @BeforeEach void setup() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getWorld()).thenReturn(world);
        when(player.hasDiscoveredRecipe(any())).thenReturn(true);
        when(view.getTopInventory()).thenReturn(grid);
        when(view.getType()).thenReturn(InventoryType.WORKBENCH);
        when(grid.getSize()).thenReturn(10);
        when(grid.getMatrix()).thenAnswer(call -> InventoryItems.copy(matrix));
        when(grid.getContents()).thenAnswer(call -> {
            ItemStack[] contents = new ItemStack[10];
            System.arraycopy(matrix, 0, contents, 1, 9);
            return contents;
        });
        when(grid.getItem(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            return index == 0 ? null : matrix[index - 1];
        });
        doAnswer(call -> {
            int slot = call.getArgument(0);
            assertTrue(slot >= 1 && slot <= 9, "fill must never write to the result slot");
            matrix[slot - 1] = InventoryItems.copy(call.<ItemStack>getArgument(1));
            return null;
        }).when(grid).setItem(anyInt(), nullable(ItemStack.class));
        when(inventory.getStorageContents()).thenAnswer(call -> InventoryItems.copy(storage));
        doAnswer(call -> {
            storage[call.<Integer>getArgument(0)] = InventoryItems.copy(call.<ItemStack>getArgument(1));
            return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.equals(null, null)).thenReturn(true);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3465);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        recipe = new ShapedRecipe(key, new ItemStack(Material.STICK)).shape("XX");
        recipe.setIngredient('X', Material.OAK_PLANKS);
        bukkit.when(() -> Bukkit.getRecipe(key)).thenReturn(recipe);
        bukkit.when(() -> Bukkit.getCraftingRecipe(any(ItemStack[].class), eq(world))).thenReturn(recipe);
    }

    @AfterEach void close() {
        planner.close();
        bukkit.close();
        assertFalse(InventoryOperation.busy(player));
    }

    @Test void detailsCountEachSourceAndCalculateMissingForSelectedQuantity() {
        matrix[0] = new ItemStack(Material.OAK_PLANKS, 2);
        storage[9] = new ItemStack(Material.OAK_PLANKS, 3);
        var stock = stock(4);
        var detail = planner.describe(player, recipe, stock).get(0);
        assertEquals(2, detail.perCraft());
        assertEquals(2, detail.workbench());
        assertEquals(3, detail.backpack());
        assertEquals(4, detail.warehouse());
        assertEquals(1, detail.missing(5));
        assertEquals(4, planner.maximumCraftingBatch(player, key, recipe, stock));
    }

    @Test void maximumNeverExceeds64EvenWithTenThousandItemsInWarehouse() {
        assertEquals(64, planner.maximumCraftingBatch(player, key, recipe, stock(10_000)));
    }

    @Test void specifiedFillAndMaximumWorkWithoutWarehouseAndPreserveTotalItems() {
        storage[9] = new ItemStack(Material.OAK_PLANKS, 10);
        fill(3);
        assertEquals("", message.get());
        assertEquals(3, matrix[0].getAmount());
        assertEquals(3, matrix[1].getAmount());
        assertEquals(4, storage[9].getAmount());
        assertEquals(10, total());
        fill(0);
        assertEquals("", message.get());
        assertEquals(5, matrix[0].getAmount());
        assertEquals(5, matrix[1].getAmount());
        assertEquals(10, total());
        assertNull(storage[9]);
    }

    @Test void invalidQuantitiesAndInsufficientStockLeaveBothInventoriesUnchanged() {
        storage[9] = new ItemStack(Material.OAK_PLANKS, 4);
        for (int quantity : new int[]{-1, 65, 3}) {
            fill(quantity);
            assertFalse(message.get().isBlank());
            assertEquals(4, storage[9].getAmount());
            assertTrue(Arrays.stream(matrix).allMatch(java.util.Objects::isNull));
        }
    }

    @Test void conflictingNativeRecipeAndOccupiedCursorBlockFilling() {
        storage[9] = new ItemStack(Material.OAK_PLANKS, 4);
        var conflict = new ShapedRecipe(new NamespacedKey("test", "other"), new ItemStack(Material.STONE)).shape("X");
        bukkit.when(() -> Bukkit.getCraftingRecipe(any(ItemStack[].class), eq(world))).thenReturn(conflict);
        fill(1);
        assertTrue(message.get().contains("其他配方"));
        assertEquals(4, total());
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.STONE));
        fill(1);
        assertTrue(message.get().contains("光标"));
        assertEquals(4, total());
    }

    @Test void warehouseDeliveryUsesOnlyTheDeficitAndCompletesItsReceipt() {
        storage[9] = new ItemStack(Material.OAK_PLANKS, 2);
        WarehouseMaterialAccess warehouse = warehouse(20);
        fill(3);
        assertEquals("", message.get());
        assertEquals(6, total());
        assertEquals(3, matrix[0].getAmount());
        assertEquals(3, matrix[1].getAmount());
        verify(warehouse).prepareMaterials(eq(player), any(), eq(List.of(new WarehouseMaterialAccess.Request(7, 4))), any(), any());
        verify(warehouse).completeDelivery(eq(player), eq("receipt"), any());
        verify(warehouse, never()).cancelPrepared(any(), anyString());
    }

    @Test void closingTheWindowBeforeAuthorizationReturnsEscrowWithoutApplying() {
        WarehouseMaterialAccess warehouse = warehouse(20);
        doAnswer(call -> {
            when(player.getOpenInventory()).thenReturn(mock(InventoryView.class));
            call.<Consumer<WarehouseMaterialAccess.Prepared>>getArgument(4)
                .accept(new WarehouseMaterialAccess.Prepared(true, "receipt", ""));
            return null;
        }).when(warehouse).prepareMaterials(any(), any(), any(), any(), any());
        fill(3);
        verify(warehouse).cancelPrepared(player, "receipt");
        verify(warehouse, never()).authorizeDelivery(any(), anyString(), any());
        assertEquals(0, total());
    }

    @Test void aChangeAfterAuthorizationReturnsUnappliedMaterials() {
        WarehouseMaterialAccess warehouse = warehouse(20);
        doAnswer(call -> {
            when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.STONE));
            call.<Consumer<Boolean>>getArgument(2).accept(true);
            return null;
        }).when(warehouse).authorizeDelivery(any(), anyString(), any());
        doAnswer(call -> { call.<Consumer<Boolean>>getArgument(2).accept(true); return null; })
            .when(warehouse).cancelUndelivered(eq(player), eq("receipt"), any());
        fill(3);
        assertEquals(0, total());
        assertTrue(message.get().contains("已回仓"));
        verify(warehouse).cancelUndelivered(eq(player), eq("receipt"), any());
        verify(warehouse, never()).recordUncertain(any(), anyString(), anyString());
        verify(warehouse, never()).cancelPrepared(any(), anyString());
        verify(warehouse, never()).completeDelivery(any(), anyString(), any());
    }

    private void fill(int quantity) { planner.fill(player, key.toString(), WorkshopType.CRAFTING, quantity, () -> true, message::set); }
    private int total() { return java.util.stream.Stream.concat(Arrays.stream(storage), Arrays.stream(matrix))
        .filter(java.util.Objects::nonNull).mapToInt(ItemStack::getAmount).sum(); }
    private MaterialStock stock(int count) { return new MaterialStock(null, new WarehouseMaterialAccess.Snapshot("personal",
        List.of(new WarehouseMaterialAccess.Entry(7, "oak", 1, count, new ItemStack(Material.OAK_PLANKS))), "")); }
    private WarehouseMaterialAccess warehouse(int count) {
        WarehouseMaterialAccess warehouse = mock(WarehouseMaterialAccess.class);
        doAnswer(call -> { call.<Consumer<WarehouseMaterialAccess.Snapshot>>getArgument(1).accept(stock(count).warehouse()); return null; })
            .when(warehouse).materialsAsync(any(), any());
        doAnswer(call -> { call.<Consumer<WarehouseMaterialAccess.Prepared>>getArgument(4)
            .accept(new WarehouseMaterialAccess.Prepared(true, "receipt", "")); return null; })
            .when(warehouse).prepareMaterials(any(), any(), any(), any(), any());
        doAnswer(call -> { call.<Consumer<Boolean>>getArgument(2).accept(true); return null; })
            .when(warehouse).authorizeDelivery(any(), anyString(), any());
        doAnswer(call -> { call.<Consumer<Boolean>>getArgument(2).accept(true); return null; })
            .when(warehouse).completeDelivery(any(), anyString(), any());
        planner.setProvider(() -> warehouse);
        return warehouse;
    }
}
