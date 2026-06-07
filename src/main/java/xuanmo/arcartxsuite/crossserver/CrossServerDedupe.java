package xuanmo.arcartxsuite.crossserver;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CrossServerDedupe {

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    CrossServerDedupe(long ttlMs) {
        this.ttlMs = Math.max(1000L, ttlMs);
    }

    /** @return {@code true} 表示首次见到该 messageId，应处理 */
    boolean registerIfNew(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        purgeExpired(now);
        Long previous = recent.putIfAbsent(messageId, now);
        return previous == null;
    }

    private void purgeExpired(long now) {
        if (recent.size() < 256) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > ttlMs) {
                iterator.remove();
            }
        }
    }
}
