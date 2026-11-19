package xuanmo.arcartxsuite.prop.service;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;

/** Coalesces presses waiting for the same connection and binding before the server tick. */
final class PropKeyPressQueue {
    static final int CAPACITY = 256;
    static final int PER_TICK = 32;

    private final LinkedHashMap<Key, Press> pending = new LinkedHashMap<>();
    private final Consumer<Runnable> schedule;
    private final Consumer<Press> dispatch;
    private boolean scheduled;
    private boolean closed;

    PropKeyPressQueue(Consumer<Runnable> schedule, Consumer<Press> dispatch) {
        this.schedule = schedule;
        this.dispatch = dispatch;
    }

    void offer(Player player, PropKeyBindingDefinition binding, AuthSnapshot admission) {
        synchronized (this) {
            if (closed || pending.size() >= CAPACITY) return;
            var key = new Key(admission.playerId(), admission.connectionId(), admission.generation(), binding.bindingId());
            if (pending.containsKey(key)) return;
            pending.put(key, new Press(player, binding, admission));
            if (scheduled) return;
            scheduled = true;
        }
        scheduleDrain();
    }

    synchronized void cancel(Player player) {
        pending.values().removeIf(press -> press.player() == player);
    }

    synchronized void close() {
        closed = true;
        pending.clear();
    }

    private void scheduleDrain() {
        try {
            schedule.accept(this::drain);
        } catch (RuntimeException schedulerStopped) {
            synchronized (this) {
                pending.clear();
                scheduled = false;
            }
        }
    }

    private void drain() {
        try {
            for (int processed = 0; processed < PER_TICK; processed++) {
                var press = take();
                if (press == null) break;
                dispatch.accept(press);
            }
        } finally {
            boolean more;
            synchronized (this) {
                more = !closed && !pending.isEmpty();
                scheduled = more;
            }
            if (more) scheduleDrain();
        }
    }

    private synchronized Press take() {
        if (closed || pending.isEmpty()) return null;
        var iterator = pending.values().iterator();
        var press = iterator.next();
        iterator.remove();
        return press;
    }

    private record Key(UUID playerId, UUID connection, long generation, String bindingId) {}
    record Press(Player player, PropKeyBindingDefinition binding, AuthSnapshot admission) {}
}
