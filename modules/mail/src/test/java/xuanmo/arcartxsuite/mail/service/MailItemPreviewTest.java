package xuanmo.arcartxsuite.mail.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.mail.util.MailItemPreview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailItemPreviewTest {
    @Test
    void usesFullCloneWithCountOneAndKeepsRewardAt128() {
        ItemStack reward = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(reward.clone()).thenReturn(copy);
        // Rebuilding from Material loses tags; the preview must retain the original's full clone.
        assertSame(copy, MailItemPreview.copyForIcon(reward));
        verify(copy).setAmount(1);
        verify(reward, never()).setAmount(anyInt());
        verify(reward, never()).getItemMeta();
    }

    @Test
    void everyIconHasCountOneWhileOriginalAmountsStayUnchanged() {
        ItemStack normal = new ItemStack(Material.DIAMOND, 48);
        assertEquals(1, MailItemPreview.copyForIcon(normal).getAmount());
        assertEquals(48, normal.getAmount());
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 128);
        assertEquals(1, MailItemPreview.copyForIcon(sword).getAmount());
        assertEquals(128, sword.getAmount());
    }

    @Test
    void independentAmountsPreserve128AndSlotKeysAcrossEmptyEntries() {
        var items = java.util.Arrays.asList(new ItemStack(Material.DIAMOND, 128),
            null, new ItemStack(Material.AIR), new ItemStack(Material.PAPER, 48));
        assertEquals(java.util.Map.of("0", 128, "3", 48), MailItemPreview.amounts(items));
        assertTrue(MailItemPreview.amounts(null).isEmpty());
    }
}
