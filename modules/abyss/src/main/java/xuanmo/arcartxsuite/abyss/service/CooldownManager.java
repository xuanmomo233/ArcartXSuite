package xuanmo.arcartxsuite.abyss.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;

/**
 * 冷却管理器：内存缓存 + 持久化到 PlayerDataManager。
 */
public final class CooldownManager {

    private final PlayerDataManager data;
    private final Map<String, Long> cache = new ConcurrentHashMap<>(); // "uuid:dungeonId" -> expiryMillis

    public CooldownManager(PlayerDataManager data) {
        this.data = data;
    }

    private static String key(UUID player, String dungeonId) {
        return player + ":" + dungeonId;
    }

    /** 剩余冷却秒数（0=无冷却）。 */
    public long remaining(UUID player, String dungeonId) {
        long expiry = cache.getOrDefault(key(player, dungeonId),
            data.getCooldown(player, dungeonId));
        long left = (expiry - System.currentTimeMillis()) / 1000;
        if (left <= 0) {
            cache.remove(key(player, dungeonId));
            return 0;
        }
        cache.put(key(player, dungeonId), expiry);
        return left;
    }

    /** 设置冷却。 */
    public void setCooldown(UUID player, String dungeonId, long seconds) {
        if (seconds <= 0) return;
        long expiry = System.currentTimeMillis() + seconds * 1000;
        cache.put(key(player, dungeonId), expiry);
        data.setCooldown(player, dungeonId, expiry);
    }

    public void clear(UUID player, String dungeonId) {
        cache.remove(key(player, dungeonId));
        data.setCooldown(player, dungeonId, 0);
    }
}
