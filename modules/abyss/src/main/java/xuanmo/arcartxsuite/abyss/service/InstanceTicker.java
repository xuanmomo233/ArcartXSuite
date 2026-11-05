package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;

/** Spreads each one-second polling round across server ticks with one shared budget. */
final class InstanceTicker {
    private final ArrayDeque<UUID> pending = new ArrayDeque<>();
    private final HashSet<UUID> queued = new HashSet<>();
    private final Supplier<Collection<AbyssInstance>> instances;
    private final Function<UUID, AbyssInstance> lookup;
    private final Consumer<AbyssInstance> action;
    private int ticks;

    InstanceTicker(Supplier<Collection<AbyssInstance>> instances, Function<UUID, AbyssInstance> lookup,
                   Consumer<AbyssInstance> action) {
        this.instances = instances;
        this.lookup = lookup;
        this.action = action;
    }

    void tick() {
        if (ticks++ % 20 == 0) {
            for (var instance : instances.get()) if (active(instance) && queued.add(instance.getInstanceId()))
                pending.addLast(instance.getInstanceId());
        }
        long deadline = System.nanoTime() + 4_000_000L;
        int count = 0;
        while (!pending.isEmpty() && count++ < 4 && System.nanoTime() < deadline) {
            UUID id = pending.removeFirst();
            queued.remove(id);
            var instance = lookup.apply(id);
            if (active(instance)) action.accept(instance);
        }
    }

    private static boolean active(AbyssInstance instance) {
        return instance != null && !instance.designMode() && instance.getStatus() == AbyssStatus.ACTIVE;
    }
}
