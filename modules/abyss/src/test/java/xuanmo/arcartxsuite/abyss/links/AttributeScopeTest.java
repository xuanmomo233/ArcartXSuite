package xuanmo.arcartxsuite.abyss.links;

import java.util.*;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.attribute.*;
import xuanmo.arcartxsuite.abyss.stats.CombatStatisticsService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttributeScopeTest {
    AbyssLinks links(AttributeBridgeRegistry registry) {
        return new AbyssLinks(new AbyssLinks.CapabilityResolver(){public <T>T get(Class<T> type){return null;}},registry,Logger.getAnonymousLogger());
    }
    @Test void distinctEffectsCoexistAndAreAllRemovedThroughHostApi() {
        var registry=mock(AttributeBridgeRegistry.class);var symphony=mock(SymphonyBridge.class);
        when(registry.symphony()).thenReturn(symphony);when(symphony.available()).thenReturn(true);
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var links=links(registry);
        links.applyAttribute(player,"attack",false,5,"abyss");links.applyAttribute(player,"defense",false,7,"abyss");
        links.applyAttribute(player,"attack",false,5,"abyss");
        ArgumentCaptor<String> sources=ArgumentCaptor.forClass(String.class);
        verify(symphony,times(3)).setAttribute(eq(player),anyString(),anyBoolean(),anyDouble(),sources.capture());
        assertEquals(3,new HashSet<>(sources.getAllValues()).size());
        links.removeAttribute(player,"abyss");
        for(String source:sources.getAllValues())verify(symphony).removeAttribute(player,source);
        links.close();
    }
    @Test void listenersAreUnregisteredExactlyOnceOnClose() {
        var registry=mock(AttributeBridgeRegistry.class);when(registry.hasDamageSource()).thenReturn(true);when(registry.hasHealSource()).thenReturn(true);
        var stats=mock(CombatStatisticsService.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));when(stats.enabled()).thenReturn(true);
        var links=links(registry);links.wireAttributeStats(stats,player->null);
        var damage=ArgumentCaptor.forClass(AttributeDamageListener.class);var heal=ArgumentCaptor.forClass(AttributeHealListener.class);
        verify(registry).registerDamageListener(damage.capture());verify(registry).registerHealListener(heal.capture());
        links.close();links.close();
        verify(registry).unregisterDamageListener(damage.getValue());verify(registry).unregisterHealListener(heal.getValue());
    }
}
