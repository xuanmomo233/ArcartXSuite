package xuanmo.arcartxsuite.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectSourcesTest {
    @Test
    void mergesMaximumLevelsAndRestoresTheRemainingSource() {
        var sources = new EffectSources<String>();
        sources.replace("title", Map.of("speed", 1, "strength", 2));
        sources.replace("equipment", Map.of("speed", 4));
        assertEquals(Map.of("speed", 4, "strength", 2), sources.snapshot());
        sources.replace("equipment", Map.of("jump", 3));
        assertEquals(Map.of("speed", 1, "strength", 2, "jump", 3), sources.snapshot());
        assertEquals(Map.of("jump", 3), sources.remove("equipment").previous());
        assertEquals(Map.of("speed", 1, "strength", 2), sources.snapshot());
        sources.remove("title");
        assertTrue(sources.isEmpty());
    }

    @Test
    void unchangedSourcesReuseAnImmutableSnapshotAcrossRefreshes() {
        var sources = new EffectSources<String>();
        sources.replace("title", Map.of("speed", 2));
        var snapshot = sources.snapshot();
        for (int i = 0; i < 10_000; i++) assertSame(snapshot, sources.snapshot());
        assertFalse(sources.replace("title", Map.of("speed", 2)).changed());
        assertFalse(sources.remove("missing").changed());
        assertSame(snapshot, sources.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("speed", 5));
    }

    @Test
    void inputMutationAndLaterUpdatesDoNotChangeOlderViews() {
        var sources = new EffectSources<String>();
        var input = new HashMap<>(Map.of("speed", 1));
        sources.replace("title", input);
        var before = sources.snapshot();
        input.put("speed", 99);
        assertEquals(Map.of("speed", 1), before);
        sources.replace("title", Map.of("speed", 3));
        assertEquals(Map.of("speed", 1), before);
        assertEquals(Map.of("speed", 3), sources.snapshot());
    }

    @Test
    void concurrentSourceChangesDoNotLoseTheMergedResult() throws Exception {
        var sources = new EffectSources<String>();
        var workers = Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 1; i <= 100; i++) {
                int level = i;
                futures.add(workers.submit(() -> sources.replace("source-" + level, Map.of("speed", level))));
            }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(Map.of("speed", 100), sources.snapshot());
            futures.clear();
            for (int i = 1; i <= 100; i++) {
                int id = i;
                futures.add(workers.submit(() -> sources.remove("source-" + id)));
            }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertTrue(sources.isEmpty());
        } finally { workers.shutdownNow(); }
    }
}
