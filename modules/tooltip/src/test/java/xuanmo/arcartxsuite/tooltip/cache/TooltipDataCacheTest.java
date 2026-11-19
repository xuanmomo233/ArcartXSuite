package xuanmo.arcartxsuite.tooltip.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TooltipDataCacheTest {
    @Test
    void limitsRetainedContentPerPlayerAndAcrossPlayers() {
        var cache = new TooltipDataCache(() -> 0, 1024, 1536);
        var one = player(UUID.randomUUID());
        var two = player(UUID.randomUUID());
        var three = player(UUID.randomUUID());
        String line = "a".repeat(100);
        String raw = "[\"" + line + "\"]";
        cache.put(one, "test:old", raw, List.of(line), "{}");
        cache.put(one, "test:new", raw, List.of(line), "{}");
        assertNull(cache.get(one, "test:old"));
        assertNotNull(cache.get(one, "test:new"));
        cache.put(two, "test:item", raw, List.of(line), "{}");
        cache.put(three, "test:item", raw, List.of(line), "{}");
        assertNull(cache.get(one, "test:new"));
        assertNotNull(cache.get(two, "test:item"));
        assertNotNull(cache.get(three, "test:item"));
        assertEquals(2, cache.playerCount());
    }

    @Test
    void duplicateRenewalExpiresInOrderAndQuitOrCloseClearsTheCorrectSession() {
        var now = new AtomicLong();
        var cache = new TooltipDataCache(now::get, 8192, 8192);
        UUID id = UUID.randomUUID();
        Player initial = player(id);
        Player next = player(id);
        cache.put(initial, "test:a", "[\"a\"]", List.of("a"), "{}");
        cache.put(initial, "test:b", "[\"b\"]", List.of("b"), "{}");
        now.set(TimeUnit.SECONDS.toNanos(40));
        assertTrue(cache.compareUpload(initial, "test:a", "[\"a\"]", "{}").renewed());
        var otherConnection = cache.compareUpload(next, "test:a", "[\"a\"]", "{}");
        assertFalse(otherConnection.renewed());
        assertNull(otherConnection.textLines());
        assertFalse(otherConnection.structuredUnchanged());
        now.set(TimeUnit.SECONDS.toNanos(61));
        cache.cleanup();
        assertNull(cache.get(initial, "test:b"));
        assertNotNull(cache.get(initial, "test:a"));
        assertNull(cache.get(next, "test:a"));

        cache.put(next, "test:a", "[\"next\"]", List.of("next"), "{}");
        cache.removePlayer(initial);
        assertEquals(List.of("next"), cache.get(next, "test:a").textLines());
        cache.close();
        cache.put(next, "test:a", "[\"late\"]", List.of("late"), "{}");
        assertEquals(0, cache.playerCount());
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }
}
