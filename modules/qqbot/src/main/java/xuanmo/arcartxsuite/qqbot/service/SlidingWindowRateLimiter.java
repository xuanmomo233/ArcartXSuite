package xuanmo.arcartxsuite.qqbot.service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Bounded rolling-window budget. Full key capacity rejects new keys rather than evicting active limits. */
public final class SlidingWindowRateLimiter<K> {
    private final int limit;
    private final long windowMillis;
    private final int maxKeys;
    private final Map<K, ArrayDeque<Long>> timestamps = new HashMap<>();
    public SlidingWindowRateLimiter(int limit, long windowMillis, int maxKeys) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1, windowMillis);
        this.maxKeys = Math.max(1, maxKeys);
    }
    public synchronized boolean allow(K key, long now) {
        ArrayDeque<Long> history = timestamps.get(key);
        if (history == null) {
            if (timestamps.size() >= maxKeys) {
                timestamps.values().removeIf(queue -> queue.isEmpty() || now - queue.peekLast() >= windowMillis);
                if (timestamps.size() >= maxKeys) return false;
            }
            history = new ArrayDeque<>(); timestamps.put(key, history);
        }
        while (!history.isEmpty() && now - history.peekFirst() >= windowMillis) history.removeFirst();
        if (history.size() >= limit) return false;
        history.addLast(now);
        return true;
    }
    public synchronized void remove(K key) { timestamps.remove(key); }
    public synchronized void clear() { timestamps.clear(); }
}
