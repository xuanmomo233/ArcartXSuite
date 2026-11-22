package xuanmo.arcartxsuite.questgps.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Keeps one Chemdah quest change in flight until the provider reports its result. */
final class QuestGpsQuestChanges {
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    <T> boolean submit(UUID player, Supplier<? extends CompletionStage<T>> operation,
                       BiConsumer<T, Throwable> complete) {
        if (!inFlight.add(player)) return false;
        CompletionStage<T> pending;
        try {
            pending = java.util.Objects.requireNonNull(operation.get(), "Chemdah operation returned no completion stage");
        } catch (RuntimeException | LinkageError failure) {
            inFlight.remove(player);
            complete.accept(null, failure);
            return true;
        }
        pending.whenComplete((result, failure) -> {
            inFlight.remove(player);
            complete.accept(result, failure);
        });
        return true;
    }

    boolean pending(UUID player) { return inFlight.contains(player); }

    void clear() { inFlight.clear(); }
}
