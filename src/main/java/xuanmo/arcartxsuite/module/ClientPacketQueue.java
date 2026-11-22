package xuanmo.arcartxsuite.module;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.IllegalPluginAccessException;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/** Bounded FIFO for network-thread arrivals. Only the scheduled server-thread task runs handlers. */
final class ClientPacketQueue {
    private static final int MAX_PENDING = 128;
    private static final int MAX_PER_PLAYER = 16;
    private static final int MAX_PER_TICK = 32;
    private static final long TICK_BUDGET_NANOS = 2_000_000L;

    private record Entry(UUID player, Runnable handler) {}

    private final ArrayDeque<Entry> queue = new ArrayDeque<>(MAX_PENDING);
    private final Map<UUID, Integer> pendingByPlayer = new HashMap<>();
    private final Function<Runnable, ScheduledTask> schedule;
    private final LongSupplier nanoTime;
    private final Logger logger;
    private Drain drain;
    private int inFlight;
    private boolean closed;

    ClientPacketQueue(Function<Runnable, ScheduledTask> schedule, LongSupplier nanoTime, Logger logger) {
        this.schedule = schedule;
        this.nanoTime = nanoTime;
        this.logger = logger;
    }

    synchronized boolean offer(UUID player, Supplier<Runnable> snapshot) {
        int pending = pendingByPlayer.getOrDefault(player, 0);
        if (closed || queue.size() + inFlight >= MAX_PENDING || pending >= MAX_PER_PLAYER) return false;
        Runnable handler = snapshot.get();
        if (drain == null) {
            var next = new Drain();
            try {
                next.task = schedule.apply(next);
            } catch (RejectedExecutionException | IllegalPluginAccessException stopped) {
                return false;
            }
            drain = next;
        }
        queue.addLast(new Entry(player, handler));
        pendingByPlayer.put(player, pending + 1);
        return true;
    }

    void clear(boolean close) {
        Drain retired;
        synchronized (this) {
            closed |= close;
            retired = drain;
            drain = null;
            for (Entry pending : queue) release(pending.player());
            queue.clear();
        }
        if (retired != null) retired.task.cancel();
    }

    private synchronized Entry poll(Drain current) {
        if (drain != current) return null;
        Entry next = queue.pollFirst();
        if (next != null) inFlight++;
        return next;
    }

    private synchronized void finish(Entry packet) {
        inFlight--;
        release(packet.player());
    }

    private void release(UUID player) {
        pendingByPlayer.computeIfPresent(player, (ignored, count) -> count == 1 ? null : count - 1);
    }

    private final class Drain implements Runnable {
        private ScheduledTask task;

        @Override
        public void run() {
            long started = nanoTime.getAsLong();
            try {
                for (int handled = 0; handled < MAX_PER_TICK; handled++) {
                    if (handled > 0 && nanoTime.getAsLong() - started >= TICK_BUDGET_NANOS) break;
                    Entry packet = poll(this);
                    if (packet == null) break;
                    try {
                        packet.handler().run();
                    } catch (RuntimeException | LinkageError failure) {
                        logger.log(Level.WARNING, "客户端包处理失败", failure);
                    } finally {
                        finish(packet);
                    }
                }
            } finally {
                stopIfIdle();
            }
        }

        private void stopIfIdle() {
            synchronized (ClientPacketQueue.this) {
                if (drain != this || !queue.isEmpty()) return;
                drain = null;
            }
            task.cancel();
        }
    }
}
