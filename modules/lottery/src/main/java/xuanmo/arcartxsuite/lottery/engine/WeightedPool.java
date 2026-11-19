package xuanmo.arcartxsuite.lottery.engine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/** Immutable cumulative weights. Configured weights can exceed the int range in aggregate. */
final class WeightedPool<T> {
    private final List<T> entries;
    private final long[] cumulative;
    private final long total;

    WeightedPool(List<T> entries, ToIntFunction<T> weight) {
        this.entries = List.copyOf(entries);
        this.cumulative = new long[entries.size()];
        long sum = 0;
        for (int i = 0; i < entries.size(); i++) {
            sum += Math.max(0, weight.applyAsInt(entries.get(i)));
            cumulative[i] = sum;
        }
        this.total = sum;
    }

    T roll(boolean uniformWhenZero) {
        if (entries.isEmpty()) return null;
        if (entries.size() == 1) return entries.get(0);
        var random = ThreadLocalRandom.current();
        if (total == 0L) return entries.get(uniformWhenZero ? random.nextInt(entries.size()) : 0);
        return select(random.nextLong(total));
    }

    T select(long draw) {
        if (draw < 0 || draw >= total) throw new IllegalArgumentException("Draw falls outside the weight range");
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (cumulative[middle] <= draw) low = middle + 1;
            else high = middle;
        }
        return entries.get(low);
    }
}
