package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

class ClientPacketQueueTest {
    @Test
    void sharedAndPlayerLimitsPreserveFifoWithOneScheduledDrain() {
        var fixture = new Fixture();
        var executed = new ArrayList<Integer>();
        for (int player = 0; player < 8; player++) {
            UUID id = new UUID(0, player);
            for (int item = 0; item < 16; item++) {
                int sequence = player * 16 + item;
                assertTrue(fixture.queue.offer(id, () -> () -> executed.add(sequence)));
            }
            assertFalse(fixture.queue.offer(id, () -> fail("A rejected packet must not be copied")));
        }
        var other = new UUID(0, 9);
        assertFalse(fixture.queue.offer(other, () -> fail("The global queue is full")));
        assertEquals(1, fixture.tasks.size());
        var drain = fixture.tasks.get(0);
        drain.callback.run();
        assertEquals(32, executed.size());
        assertTrue(fixture.queue.offer(other, () -> () -> executed.add(128)));
        for (int tick = 0; tick < 4; tick++) drain.callback.run();
        assertEquals(IntStream.rangeClosed(0, 128).boxed().toList(), executed);
        assertTrue(drain.isCancelled());
    }

    @Test
    void budgetDefersTheNextPacketAndRetirementDiscardsPendingWork() {
        var fixture = new Fixture();
        var executed = new ArrayList<Integer>();
        var player = UUID.randomUUID();
        assertTrue(fixture.queue.offer(player, () -> () -> {
            executed.add(1);
            fixture.now.addAndGet(3_000_000);
        }));
        assertTrue(fixture.queue.offer(player, () -> () -> executed.add(2)));
        var first = fixture.tasks.get(0);
        first.callback.run();
        assertEquals(List.of(1), executed);
        assertFalse(first.isCancelled());
        first.callback.run();
        assertEquals(List.of(1, 2), executed);
        assertTrue(first.isCancelled());

        assertTrue(fixture.queue.offer(player, () -> () -> fail("Old packet survived reset")));
        var retired = fixture.tasks.get(1);
        fixture.queue.clear(false);
        assertTrue(retired.isCancelled());
        retired.callback.run();
        assertTrue(fixture.queue.offer(player, () -> () -> fail("Packet ran after shutdown")));
        var last = fixture.tasks.get(2);
        fixture.queue.clear(true);
        assertTrue(last.isCancelled());
        last.callback.run();
        assertFalse(fixture.queue.offer(player, () -> fail("Retired queue accepted a packet")));
    }

    private static final class Fixture {
        private final List<Task> tasks = new ArrayList<>();
        private final AtomicLong now = new AtomicLong();
        private final ClientPacketQueue queue = new ClientPacketQueue(action -> {
            var task = new Task(action);
            tasks.add(task);
            return task;
        }, now::get, Logger.getAnonymousLogger());
    }

    private static final class Task implements ScheduledTask {
        private final Runnable callback;
        private boolean cancelled;

        private Task(Runnable callback) { this.callback = callback; }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCancelled() { return cancelled; }
    }
}
