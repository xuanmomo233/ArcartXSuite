package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;

class RecipeEditorTest {
    private final Player player = mock(Player.class);
    private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
    private final VanillaRecipeRegistry registry = mock(VanillaRecipeRegistry.class);
    private final InventoryView nativeWorkbench = mock(InventoryView.class), playerView = mock(InventoryView.class);
    private final AtomicReference<InventoryView> view = new AtomicReference<>(nativeWorkbench);
    private final AtomicReference<Map<String,Object>> state = new AtomicReference<>();
    private MockedStatic<Bukkit> bukkit;
    private RecipeEditor editor;
    private final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    private final Map<String,ItemStack> previews = new HashMap<>();

    @BeforeEach void setup() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(RecipeEditor.ADMIN)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getItemOnCursor()).thenAnswer(call -> cursor.get());
        doAnswer(call -> { cursor.set(call.getArgument(0)); return null; }).when(player).setItemOnCursor(nullable(ItemStack.class));
        when(player.getOpenInventory()).thenAnswer(call -> view.get());
        doAnswer(call -> { view.set(playerView); return null; }).when(player).closeInventory();
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUi(player, RecipeEditor.UI)).thenReturn(true);
        when(bridge.isUiOpen(player, RecipeEditor.UI)).thenReturn(true);
        doAnswer(call -> { state.set(call.getArgument(3)); return true; })
            .when(bridge).sendPacket(eq(player), eq(RecipeEditor.UI), eq("recipe_editor_state"), any());
        when(registry.recipes()).thenReturn(List.of());
        ItemPresentation presentation = mock(ItemPresentation.class);
        when(presentation.name(any())).thenReturn("样品");
        when(presentation.icon(any())).thenAnswer(call -> call.<ItemStack>getArgument(0).getType().name());
        var factory = mock(ItemFactory.class);
        when(factory.equals(null,null)).thenReturn(true);
        var unsafe = mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3465);
        when(unsafe.getMaterial(anyString(), anyInt())).thenAnswer(call -> Material.valueOf(call.getArgument(0)));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        ItemBridgeAPI display=mock(ItemBridgeAPI.class);
        when(display.isAvailable()).thenReturn(true);
        doAnswer(call -> {
            String id=call.getArgument(1);
            ItemStack item=call.getArgument(2);
            if(item==null||item.getType().isAir())previews.remove(id);
            else previews.put(id,item.clone());
            return true;
        }).when(display).showExtraSlot(any(),anyString(),nullable(ItemStack.class));
        editor = new RecipeEditor(mock(Plugin.class), registry, bridge, presentation, display, RecipeEditor.UI);
    }
    @AfterEach void close() { bukkit.close(); }

    @Test void openingFinishesTheRealWorkbenchBeforeOpeningTheEditor() {
        assertTrue(editor.open(player, "AXS:vanilla_crafting"));
        var order = inOrder(player, bridge);
        order.verify(player).closeInventory();
        order.verify(bridge).closeUi(player, "AXS:vanilla_crafting");
        order.verify(bridge).openUi(player, RecipeEditor.UI);
        assertSame(playerView, player.getOpenInventory());
    }

    @Test void extraSlotsReallyMoveItemsFromAndBackToTheCursor() {
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String prefix=(String)state.get().get("slotPrefix");
        var sample=new ItemStack(Material.DIAMOND,8);
        cursor.set(sample);
        for(int i=0;i<8;i++)editor.clickExtra(player,prefix+i,0);
        assertEquals(8,previews.size());
        assertNull(cursor.get());
        assertEquals(8,sample.getAmount());
        editor.clickExtra(player,prefix+"0",0);
        assertEquals(1,cursor.get().getAmount());
        assertEquals(7,previews.size());
        cursor.set(new ItemStack(Material.BOOK,8));
        editor.clickExtra(player,prefix+"9",0);
        assertNull(cursor.get());
        assertEquals(8,previews.get(prefix+"9").getAmount());
        editor.clickExtra(player,prefix+"9",1);
        assertEquals(4,cursor.get().getAmount());
        assertEquals(4,previews.get(prefix+"9").getAmount());
    }

    @Test void staleCloseAndSlotPacketsCannotCloseOrModifyANewEditor() {
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String token=(String)state.get().get("token"),prefix=(String)state.get().get("slotPrefix");
        editor.handle(player,List.of("close",token));
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String next=(String)state.get().get("token");
        assertNotEquals(token,next);
        cursor.set(new ItemStack(Material.DIAMOND));
        editor.clickExtra(player,prefix+"0",0);
        editor.handle(player,List.of("close",token));
        editor.handle(player,List.of("ready"));
        assertEquals(next,state.get().get("token"));
        assertTrue(previews.isEmpty());
        verify(bridge,times(1)).closeUi(player,RecipeEditor.UI);
    }

    @Test void reopeningARecipeRestoresItsConfigurationInsteadOfTheDiscardedEditingBuffer() throws Exception {
        var ingredients = new ArrayList<xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient>(
            Collections.nCopies(9,xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient.blank()));
        ingredients.set(0,new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient(List.of(),List.of(new ItemStack(Material.DIAMOND))));
        var definition = new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition("saved",
            xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station.SHAPED,ingredients,new ItemStack(Material.BOOK,4),
            "",200,0,xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData.INHERIT_BASE);
        var entry = new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Entry(new NamespacedKey("test","saved"),"config",1,definition);
        when(registry.recipes()).thenReturn(List.of(entry));
        when(registry.configured(entry)).thenReturn(true);
        when(registry.references("saved")).thenReturn(Map.of());
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String token=(String)state.get().get("token"),prefix=(String)state.get().get("slotPrefix");
        editor.handle(player,List.of("select",token,"saved"));
        assertEquals(2,previews.size());
        assertEquals(4,previews.get(prefix+"9").getAmount());
        editor.clickExtra(player,prefix+"0",0);
        assertEquals(Material.DIAMOND,cursor.get().getType());
        assertEquals(1,previews.size());
        editor.handle(player,List.of("close",token));
        cursor.set(null);
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        token=(String)state.get().get("token");
        editor.handle(player,List.of("select",token,"saved"));
        assertEquals(2,previews.size());
        prefix=(String)state.get().get("slotPrefix");
        assertEquals(4,previews.get(prefix+"9").getAmount());
        verify(registry,never()).save(anyString(),any(),anyLong(),anyMap());
    }

    @Test void matchingSwitchAppliesToExistingSlotsAndLegacyFieldsAreNotPublished() {
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String token=(String)state.get().get("token"),prefix=(String)state.get().get("slotPrefix");
        cursor.set(new ItemStack(Material.DIAMOND,2));
        editor.clickExtra(player,prefix+"0",0);
        editor.clickExtra(player,prefix+"1",0);
        assertEquals("精确匹配",state.get().get("matching"));
        editor.handle(player,List.of("match_mode",token));
        assertEquals("材质匹配",state.get().get("matching"));
        assertEquals(2,previews.size());
        editor.handle(player,List.of("match_mode",token));
        assertEquals("精确匹配",state.get().get("matching"));
        for(String field:List.of("target","cells","amount","resultVersion","materialMode")) {
            assertFalse(state.get().containsKey(field),field+" is a removed UI field");
        }
    }

    @Test void savingUsesTheResultSlotsQuantityAndRejectsTheOldQuantityArgument() throws Exception {
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String token=(String)state.get().get("token"),prefix=(String)state.get().get("slotPrefix");
        cursor.set(new ItemStack(Material.DIAMOND));
        editor.clickExtra(player,prefix+"0",0);
        cursor.set(new ItemStack(Material.BOOK,5));
        editor.clickExtra(player,prefix+"9",0);
        editor.handle(player,List.of("save",token,"physical","64","perm.test"));
        verify(registry,never()).save(anyString(),any(),anyLong(),anyMap());
        editor.handle(player,List.of("save",token,"physical","perm.test"));
        var definition=org.mockito.ArgumentCaptor.forClass(
            xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition.class);
        verify(registry).save(eq("physical"),definition.capture(),eq(0L),anyMap());
        assertEquals(5,definition.getValue().result().getAmount());
        assertEquals("perm.test",definition.getValue().permission());
    }

    @Test void eightRowPagesPublishAccurateNavigationState() {
        var definition = new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition("template",
            xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station.SHAPELESS,
            List.of(new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient(List.of(Material.DIAMOND),List.of())),
            new ItemStack(Material.BOOK),"",200,0,xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData.INHERIT_BASE);
        var entries = new ArrayList<xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Entry>();
        for (int i=0;i<12;i++) entries.add(new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Entry(
            new NamespacedKey("test","recipe_"+i),"config",1,
            new xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition("recipe_"+i,
                definition.station(),definition.ingredients(),definition.result(),"",200,0,definition.smithingData())));
        when(registry.recipes()).thenReturn(entries);
        when(registry.configured(any())).thenReturn(true);
        assertTrue(editor.open(player));
        editor.handle(player,List.of("ready"));
        String token=(String)state.get().get("token");
        assertEquals(8,((Map<?,?>)state.get().get("list")).size());
        assertEquals(false,state.get().get("hasPrevious"));
        assertEquals(true,state.get().get("hasNext"));
        editor.handle(player,List.of("page",token,"1"));
        assertEquals(4,((Map<?,?>)state.get().get("list")).size());
        assertEquals("2 / 2",state.get().get("page"));
        assertEquals(true,state.get().get("hasPrevious"));
        assertEquals(false,state.get().get("hasNext"));
    }
}
