package xuanmo.arcartxsuite.conversation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.conversation.service.NpcConversationIndex.Binding;

class NpcConversationIndexTest {
    @Test
    void mergesNpcIdsCaseInsensitivelyAndKeepsWorldNamesExact() {
        var index = new NpcConversationIndex(List.of(
            new Binding("Trader", List.of("town")),
            new Binding("trader", List.of("island")),
            new Binding("Guide", List.of("town")),
            new Binding("guide", List.of()),
            new Binding("Guide", List.of("island"))));
        assertTrue(index.contains("TRADER", "town"));
        assertTrue(index.contains("trader", "island"));
        assertFalse(index.contains("trader", "TOWN"));
        assertFalse(index.contains("trader", "mine"));
        assertTrue(index.contains("GUIDE", "mine"));
        assertFalse(index.contains("absent", "town"));
    }

    @Test
    void comparesTheDisplayedDistanceWithoutFormattingEveryScan() {
        assertEquals(ConversationInteractionStateSupport.distanceTenths(1.01 * 1.01),
            ConversationInteractionStateSupport.distanceTenths(1.04 * 1.04));
        assertEquals("1.1m", ConversationInteractionStateSupport.distanceText(
            ConversationInteractionStateSupport.distanceTenths(1.06 * 1.06)));
        assertEquals("0.0m", ConversationInteractionStateSupport.distanceText(
            ConversationInteractionStateSupport.distanceTenths(0)));
    }
}
