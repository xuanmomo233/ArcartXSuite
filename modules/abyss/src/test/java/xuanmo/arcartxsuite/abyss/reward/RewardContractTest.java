package xuanmo.arcartxsuite.abyss.reward;

import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.item.*;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardContractTest {
    @Test void bothMmoFormsPreserveTypeIdAndQuantity() {
        assertEquals(new ItemDefinition("mmoitems","SWORD;BLADE",2),ItemDefinition.parse("mmo:SWORD:BLADE:2"));
        assertEquals(new ItemDefinition("mmoitems","SWORD;BLADE",2),ItemDefinition.parse("mmoitems:SWORD;BLADE:2"));
        assertThrows(IllegalArgumentException.class,()->ItemDefinition.parse("STONE:-1"));
    }
    @Test void generationDelegatesToPublicItemSourceAndDeliveryRequiresConfirmedPersistence() {
        var source=mock(ItemSourceRegistry.class);var pending=mock(PendingRewardService.class);var dispatcher=mock(ItemRewardDispatcher.class);
        var rewards=new RewardResolver(source,dispatcher,pending,mock(CurrencyBridgeAPI.class),Logger.getAnonymousLogger());
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        rewards.buildItem("mmoitems:SWORD;BLADE:2",player);
        verify(source).generateItem("mmoitems","SWORD;BLADE",2,null,player);
        try(var serializer=mockStatic(ItemSerializer.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            serializer.when(()->ItemSerializer.serialize(any())).thenReturn(new byte[]{1,2,3});
            when(pending.savePendingBatchConfirmed(anyString(),anyList())).thenReturn(false,true);
            assertFalse(rewards.queueItems(player,List.of(new ItemStack(Material.STONE,2)),"receipt"));
            verify(pending,never()).flushPlayer(any());
            assertTrue(rewards.queueItems(player,List.of(new ItemStack(Material.STONE,2)),"receipt"));
            verify(pending).flushPlayer(player);verifyNoInteractions(dispatcher);
        }
    }
}
