package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import static org.junit.jupiter.api.Assertions.*;

class InstanceTickerTest {
    @Test void pollingRoundsAreSpreadAcrossTicksWithoutVisitingDeletedRuns() {
        var runs = new LinkedHashMap<UUID, AbyssInstance>();
        for (int i = 0; i < 24; i++) {
            var run = new AbyssInstance(UUID.randomUUID(), null, null);
            run.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE);
            runs.put(run.getInstanceId(), run);
        }
        var visited = new ArrayList<UUID>();
        var ticker = new InstanceTicker(runs::values, runs::get, run -> visited.add(run.getInstanceId()));
        ticker.tick();
        assertTrue(visited.size() <= 4);
        UUID deleted = runs.keySet().stream().filter(id -> !visited.contains(id)).findFirst().orElseThrow();
        runs.remove(deleted);
        for (int i = 1; i < 20; i++) {
            int before = visited.size();
            ticker.tick();
            assertTrue(visited.size() - before <= 4);
        }
        assertFalse(visited.contains(deleted));
        assertEquals(23, visited.size());
        assertEquals(23, visited.stream().distinct().count());
    }
}
