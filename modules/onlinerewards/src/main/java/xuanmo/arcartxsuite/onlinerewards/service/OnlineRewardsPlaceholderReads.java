package xuanmo.arcartxsuite.onlinerewards.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;

/** Bounded snapshots for synchronous placeholders about players without a live state. */
final class OnlineRewardsPlaceholderReads {
    private static final long FRESH_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final int MAX_PENDING = 8;

    private final Cache<DailyKey, Personal> players;
    private final Cache<MonthKey, Integer> months;

    OnlineRewardsPlaceholderReads(OnlineRewardsRepository repository, OnlineRewardsMenuReads reads) {
        players = new Cache<>(128, reads, key -> {
            var state = repository.loadState(key.player());
            boolean signed = key.day().equals(state.lastSignInDate()) || repository.hasSignInRecord(key.player(), key.day());
            return new Personal(state, signed);
        });
        months = new Cache<>(256, reads, key -> repository.loadSignInDates(
            key.player(), key.month().atDay(1).toString(), key.month().atEndOfMonth().toString()).size());
    }

    Personal player(UUID id, String day) {
        return players.display(new DailyKey(id, day));
    }

    int days(UUID id, YearMonth month) {
        Integer known = months.display(new MonthKey(id, month));
        return known == null ? 0 : known;
    }

    void invalidate(UUID id, LocalDate today) {
        players.invalidate(new DailyKey(id, today.toString()));
        months.invalidate(new MonthKey(id, YearMonth.from(today)));
    }

    void clear() {
        players.clear();
        months.clear();
    }

    record Personal(OnlineRewardsPlayerState state, boolean signedToday) {}
    private record DailyKey(UUID player, String day) {}
    private record MonthKey(UUID player, YearMonth month) {}

    @FunctionalInterface
    private interface Loader<K, V> { V load(K key) throws SQLException; }

    private static final class Cache<K, V> {
        private final int capacity;
        private final OnlineRewardsMenuReads reads;
        private final Loader<K, V> loader;
        private final Map<K, Entry<V>> entries = new LinkedHashMap<>(32, 0.75F, true);
        private final Map<K, Object> pending = new HashMap<>();
        private long retryAfter;

        Cache(int capacity, OnlineRewardsMenuReads reads, Loader<K, V> loader) {
            this.capacity = capacity;
            this.reads = reads;
            this.loader = loader;
        }

        synchronized V display(K key) {
            long now = System.nanoTime();
            var entry = entries.get(key);
            if ((entry == null || now - entry.loadedAt() >= FRESH_NANOS) && pending.size() < MAX_PENDING
                    && !pending.containsKey(key) && (retryAfter == 0 || now - retryAfter >= 0)) {
                var ticket = new Object();
                pending.put(key, ticket);
                reads.query(() -> loader.load(key)).whenComplete((value, failure) -> finish(key, ticket, value, failure));
            }
            return entry != null && now - entry.loadedAt() < MAX_AGE_NANOS ? entry.value() : null;
        }

        private synchronized void finish(K key, Object ticket, V value, Throwable failure) {
            if (!pending.remove(key, ticket)) return;
            if (failure != null) {
                retryAfter = System.nanoTime() + RETRY_NANOS;
                return;
            }
            entries.put(key, new Entry<>(value, System.nanoTime()));
            if (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        }

        synchronized void invalidate(K key) {
            entries.remove(key);
            pending.remove(key);
        }

        synchronized void clear() {
            entries.clear();
            pending.clear();
        }

        private record Entry<V>(V value, long loadedAt) {}
    }
}
