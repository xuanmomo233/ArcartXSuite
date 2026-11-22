package xuanmo.arcartxsuite.prop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;

class PropKeyPressQueueTest {
    @Test
    void maintainsDistinctPressOrderWithinTheQueueAndPerTickLimits() {
        var scheduled = new ArrayDeque<Runnable>();
        var handled = new ArrayList<String>();
        var player = mock(Player.class);
        var admission = admitted();
        var queue = new PropKeyPressQueue(scheduled::add, press -> handled.add(press.binding().bindingId()));
        for (int key = 0; key < PropKeyPressQueue.CAPACITY + 20; key++) {
            var binding = new PropKeyBindingDefinition("key-" + key, "X", "Slot1");
            queue.offer(player, binding, admission);
            queue.offer(player, binding, admission);
        }
        while (!scheduled.isEmpty()) {
            assertEquals(1, scheduled.size());
            int before = handled.size();
            scheduled.remove().run();
            assertEquals(PropKeyPressQueue.PER_TICK, handled.size() - before);
        }
        assertEquals(IntStream.range(0, PropKeyPressQueue.CAPACITY).mapToObj(i -> "key-" + i).toList(), handled);
    }

    @Test
    void leavingCancelsOnlyThatPlayersPressAndClosingRejectsNewWork() {
        var scheduled = new ArrayDeque<Runnable>();
        var handled = new ArrayList<Player>();
        var first = mock(Player.class);
        var second = mock(Player.class);
        var binding = new PropKeyBindingDefinition("use", "X", "Slot1");
        var queue = new PropKeyPressQueue(scheduled::add, press -> handled.add(press.player()));
        queue.offer(first, binding, admitted());
        queue.offer(second, binding, admitted());
        queue.cancel(first);
        scheduled.remove().run();
        assertEquals(List.of(second), handled);

        queue.offer(second, binding, admitted());
        queue.close();
        scheduled.remove().run();
        queue.offer(first, binding, admitted());
        assertTrue(scheduled.isEmpty());
        assertEquals(List.of(second), handled);
    }

    private AuthSnapshot admitted() {
        UUID id = UUID.randomUUID();
        return new AuthSnapshot(id, UUID.randomUUID(), 0, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }
}
