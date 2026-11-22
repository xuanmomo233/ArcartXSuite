package xuanmo.arcartxsuite.conversation.service;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/** Spreads one snapshot's NPC lookups over the configured scan period. */
final class NpcScanCycle<T> {
    private final long period;
    private List<T> players = List.of();
    private long tick;
    private int offset;

    NpcScanCycle(long period) {
        this.period = period;
    }

    List<T> nextBatch(Supplier<? extends Collection<? extends T>> onlinePlayers) {
        if (tick == 0) players = List.copyOf(onlinePlayers.get());
        int count = (int) (players.size() / period) + (tick < players.size() % period ? 1 : 0);
        var batch = count == 0 ? List.<T>of() : players.subList(offset, offset + count);
        offset += count;
        if (offset == players.size()) players = List.of();
        if (++tick == period) reset();
        return batch;
    }

    void reset() {
        players = List.of();
        tick = 0;
        offset = 0;
    }
}
