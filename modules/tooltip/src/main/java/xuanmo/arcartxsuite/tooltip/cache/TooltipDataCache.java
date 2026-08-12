package xuanmo.arcartxsuite.tooltip.cache;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tooltip 数据缓存，按玩家 + 物品指纹去重。
 * <p>
 * 客户端每次 tooltip 事件都发包，服务端根据物品指纹去重，
 * 避免重复处理同一物品的数据。
 */
public final class TooltipDataCache {

    /** 单条缓存数据的 TTL（毫秒） */
    private static final long TTL_MILLIS = 60_000L;

    private final ConcurrentMap<UUID, ConcurrentMap<String, CachedEntry>> cache = new ConcurrentHashMap<>();

    /**
     * 存入 tooltip 数据。
     *
     * @param player        玩家
     * @param fingerprint   物品指纹（类型 ID + NBT hash）
     * @param textLines     tooltip 文本行
     * @param structuredData 结构化数据 JSON
     */
    public void put(@NotNull Player player, @NotNull String fingerprint,
                    @NotNull List<String> textLines, @NotNull String structuredData) {
        ConcurrentMap<String, CachedEntry> playerCache = cache.computeIfAbsent(
            player.getUniqueId(), k -> new ConcurrentHashMap<>());
        playerCache.put(fingerprint, new CachedEntry(textLines, structuredData, System.currentTimeMillis()));
    }

    /**
     * 获取玩家的 tooltip 数据（按指纹）。
     */
    @Nullable
    public CachedEntry get(@NotNull UUID playerUuid, @NotNull String fingerprint) {
        ConcurrentMap<String, CachedEntry> playerCache = cache.get(playerUuid);
        if (playerCache == null) return null;
        CachedEntry entry = playerCache.get(fingerprint);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp > TTL_MILLIS) {
            playerCache.remove(fingerprint);
            return null;
        }
        return entry;
    }

    /**
     * 获取玩家当前手持物品的 tooltip 数据。
     */
    @Nullable
    public CachedEntry get(@NotNull Player player, @NotNull ItemStack itemStack) {
        String fingerprint = fingerprint(itemStack);
        return get(player.getUniqueId(), fingerprint);
    }

    /**
     * 清理过期数据（可定期调用）。
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        for (ConcurrentMap<String, CachedEntry> playerCache : cache.values()) {
            playerCache.entrySet().removeIf(e -> now - e.getValue().timestamp > TTL_MILLIS);
        }
        // 清理空玩家缓存
        cache.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 清理玩家数据（玩家退出时调用）。
     */
    public void removePlayer(@NotNull UUID playerUuid) {
        cache.remove(playerUuid);
    }

    /**
     * 生成物品指纹：类型 ID + NBT hash。
     */
    public static String fingerprint(@NotNull ItemStack itemStack) {
        String typeId = itemStack.getType().getKey().toString();
        int nbtHash = itemStack.hasItemMeta() ? itemStack.getItemMeta().hashCode() : 0;
        return typeId + "@" + nbtHash;
    }

    /** 缓存条目 */
    public static final class CachedEntry {
        private final List<String> textLines;
        private final String structuredData;
        private final long timestamp;

        public CachedEntry(List<String> textLines, String structuredData, long timestamp) {
            this.textLines = textLines;
            this.structuredData = structuredData;
            this.timestamp = timestamp;
        }

        public List<String> textLines() { return textLines; }
        public String structuredData() { return structuredData; }
    }
}
