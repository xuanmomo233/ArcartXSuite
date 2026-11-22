package xuanmo.arcartxsuite.entitytracker.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;

class BossRewardItemsTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sufficientSpaceAppliesOnlyChangedSlotsAndRestoresOnWriteFailure(boolean fail) {
        var player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        var existing = diamond(63);
        var incoming = diamond(5);
        var merged = mock(ItemStack.class);
        var split = mock(ItemStack.class);
        var full = diamond(64);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{existing, null, full});
        when(existing.isSimilar(incoming)).thenReturn(true);
        when(existing.clone()).thenReturn(merged);
        when(incoming.clone()).thenReturn(split);
        var items = mock(ItemSourceRegistry.class);
        when(items.generateItem("minecraft", "DIAMOND", 5, null, player)).thenReturn(incoming);
        var pending = mock(PendingRewardService.class);
        var delivery = new BossRewardItems(items, pending);
        var action = BossDamageRewardAction.from(Map.of("type", "item", "id", "DIAMOND", "amount", 5));

        if (fail) {
            doThrow(new IllegalStateException("slot closed")).when(inventory).setItem(1, split);
            assertThrows(IllegalStateException.class, () -> delivery.prepareOnline(player, action, "fixture"));
            verify(inventory).setItem(0, existing);
            verify(inventory).setItem(1, null);
        } else {
            var prepared = delivery.prepareOnline(player, action, "fixture");
            assertTrue(prepared.result().success());
            assertFalse(prepared.requiresStorage());
        }
        verify(merged).setAmount(64);
        verify(split).setAmount(4);
        verify(inventory).setItem(0, merged);
        verify(inventory).setItem(1, split);
        verify(inventory, never()).setItem(eq(2), any());
        verify(existing, never()).setAmount(anyInt());
        verifyNoInteractions(pending);
        verify(player, never()).saveData();
    }

    private static ItemStack diamond(int amount) {
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(64);
        return item;
    }
}
