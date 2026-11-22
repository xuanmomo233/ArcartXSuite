package xuanmo.arcartxsuite.lottery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

/** Latest display snapshots. Their values are never used for settlement. */
final class LotteryPlaceholderReads implements AutoCloseable {
    private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toNanos(5);
    private static final int CACHE_LIMIT = 1024;
    private record PoolKey(UUID player, String pool) {}

    private static final class Entry<T> {
        final T value;
        final boolean loading;
        final long refreshAt;

        Entry(T value, boolean loading, long refreshAt) {
            this.value = value;
            this.loading = loading;
            this.refreshAt = refreshAt;
        }
    }

    private final Map<UUID, Entry<Map<String, Long>>> points = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<PoolKey, Entry<PlayerGachaState>> pity = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<PoolKey, Entry<PlayerCaseState>> opened = new LinkedHashMap<>(16, 0.75f, true);
    private final ModuleExecutor worker = new ModuleExecutor(0, 1, 5, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, "AXS-Lottery-Placeholders");
            thread.setDaemon(true);
            return thread;
        });
    private final LotteryRepository repository;
    private final Logger logger;
    private volatile boolean closed;

    LotteryPlaceholderReads(LotteryRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    Long point(UUID player, String point) {
        var balances = read(points, player, () -> Map.copyOf(repository.getPoints(player)));
        return balances == null ? null : balances.getOrDefault(point, 0L);
    }

    PlayerGachaState pity(UUID player, String pool) {
        return read(pity, new PoolKey(player, pool), () -> repository.loadGachaState(player, pool));
    }

    PlayerCaseState opened(UUID player, String pool) {
        return read(opened, new PoolKey(player, pool), () -> repository.loadCaseState(player, pool));
    }

    synchronized void invalidatePoints(UUID player) { points.remove(player); }
    synchronized void invalidatePity(UUID player, String pool) { pity.remove(new PoolKey(player, pool)); }
    synchronized void invalidateOpened(UUID player, String pool) { opened.remove(new PoolKey(player, pool)); }

    private synchronized <K, V> V read(Map<K, Entry<V>> cache, K key, Callable<V> load) {
        if (closed) return null;
        Entry<V> existing = cache.get(key);
        long now = System.nanoTime();
        if (existing != null && (existing.loading || now - existing.refreshAt < 0)) return existing.value;
        V previous = existing == null ? null : existing.value;
        if (existing == null && cache.size() >= CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        var request = new Entry<>(previous, true, 0);
        cache.put(key, request);
        try {
            worker.execute(() -> {
                if (closed) return;
                V value = previous;
                try { value = load.call(); }
                catch (Exception failure) { logger.fine("Lottery 展示数据刷新失败: " + failure.getMessage()); }
                synchronized (this) {
                    if (!closed && cache.get(key) == request)
                        cache.put(key, new Entry<>(value, false, System.nanoTime() + REFRESH_INTERVAL));
                }
            });
        } catch (RejectedExecutionException busy) {
            cache.put(key, new Entry<>(previous, false, now + REFRESH_INTERVAL));
        }
        return previous;
    }

    @Override public synchronized void close() {
        closed = true;
        points.clear();
        pity.clear();
        opened.clear();
        worker.shutdown();
    }

    CompletionStage<Void> stopped() { return worker.stopped(); }
}
