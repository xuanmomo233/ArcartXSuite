package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RecipeEditorSlotsTest {
    @Test void materialSlotsHoldOneIngredientAndReturnTheRemainder() {
        var slots=new RecipeEditorSlots();
        var cursor=slots.click(0,0,new ItemStack(Material.COPPER_INGOT,8));
        assertEquals(7,cursor.getAmount());
        assertEquals(1,slots.get(0).getAmount());
        var item=slots.click(0,0,null);
        assertEquals(1,item.getAmount());
        assertNull(slots.get(0));
    }

    @Test void resultSlotSupportsMergingHalfPickupAndPhysicalSwap() {
        ItemFactory factory=mock(ItemFactory.class);
        when(factory.equals(null,null)).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            var slots=new RecipeEditorSlots();
            assertNull(slots.click(9,0,new ItemStack(Material.BOOK,60)));
            var remainder=slots.click(9,0,new ItemStack(Material.BOOK,8));
            assertEquals(4,remainder.getAmount());
            assertEquals(64,slots.get(9).getAmount());
            var half=slots.click(9,1,null);
            assertEquals(32,half.getAmount());
            assertEquals(32,slots.get(9).getAmount());
            var swapped=slots.click(9,0,new ItemStack(Material.DIAMOND,3));
            assertEquals(Material.BOOK,swapped.getType());
            assertEquals(32,swapped.getAmount());
            assertEquals(Material.DIAMOND,slots.get(9).getType());
            assertEquals(3,slots.get(9).getAmount());
        }
    }
}
