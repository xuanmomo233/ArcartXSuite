package xuanmo.arcartxsuite.conversation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NpcScanCycleTest {
    @Test
    void distributesNinetySixPlayersAcrossOneTenTickInterval() {
        var cycle = new NpcScanCycle<Integer>(10);
        var source = IntStream.range(0, 96).boxed().toList();
        var batches = new ArrayList<Integer>();
        var visited = new ArrayList<Integer>();
        var snapshots = new AtomicInteger();
        for (int tick = 0; tick < 10; tick++) {
            var batch = cycle.nextBatch(() -> {
                snapshots.incrementAndGet();
                return source;
            });
            batches.add(batch.size());
            visited.addAll(batch);
        }
        assertEquals(List.of(10, 10, 10, 10, 10, 10, 9, 9, 9, 9), batches);
        assertEquals(source, visited);
        assertEquals(1, snapshots.get());
        assertEquals(List.of(200), cycle.nextBatch(() -> List.of(200)));
    }

    @Test
    void resetDropsTheOldSnapshotAndSingleTickKeepsTheConfiguredCadence() {
        var cycle = new NpcScanCycle<String>(3);
        assertEquals(List.of("first"), cycle.nextBatch(() -> List.of("first", "leaving")));
        cycle.reset();
        assertEquals(List.of("joining"), cycle.nextBatch(() -> List.of("joining")));
        assertTrue(cycle.nextBatch(() -> fail("mid-cycle snapshot")).isEmpty());
        assertTrue(cycle.nextBatch(() -> fail("mid-cycle snapshot")).isEmpty());
        assertTrue(cycle.nextBatch(List::of).isEmpty());

        var everyTick = new NpcScanCycle<Integer>(1);
        assertEquals(List.of(1, 2), everyTick.nextBatch(() -> List.of(1, 2)));
        assertEquals(List.of(3), everyTick.nextBatch(() -> List.of(3)));
    }
}
