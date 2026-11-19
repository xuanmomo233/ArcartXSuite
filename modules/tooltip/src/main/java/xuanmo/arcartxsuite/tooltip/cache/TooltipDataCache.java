package xuanmo.arcartxsuite.tooltip.cache;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Per-player display data, bounded across both the player and the server. */
public final class TooltipDataCache {
    private static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_ENTRIES_PER_PLAYER = 256;
    private static final int MAX_WEIGHT_PER_PLAYER = 512 * 1024;
    private static final int MAX_TOTAL_WEIGHT = 64 * 1024 * 1024;
    private static final int CLEANUP_LIMIT = 256;

    private final Map<UUID, PlayerCache> players = new HashMap<>();
    // Accessed here only on upload or renewal. The eldest entry therefore expires first.
    private final LinkedHashMap<CachedEntry, Boolean> byUpload = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier clock;
    private final int playerLimit;
    private final int totalLimit;
    private int weight;
    private boolean closed;

    public TooltipDataCache() {
        this(System::nanoTime, MAX_WEIGHT_PER_PLAYER, MAX_TOTAL_WEIGHT);
    }

    TooltipDataCache(LongSupplier clock, int playerLimit, int totalLimit) {
        this.clock = clock;
        this.playerLimit = playerLimit;
        this.totalLimit = totalLimit;
    }

    /** Only exact matches can reuse the validation from a previous upload. */
    public synchronized UploadMatch compareUpload(Player player, String id, String linesJson, String structuredData) {
        if (closed) return UploadMatch.MISS;
        var bucket = players.get(player.getUniqueId());
        if (bucket == null || bucket.owner != player) return UploadMatch.MISS;
        var entry = bucket.entries.get(id);
        if (entry == null) return UploadMatch.MISS;
        long now = clock.getAsLong();
        if (expired(entry, now)) {
            remove(entry);
            return UploadMatch.MISS;
        }
        boolean sameLines = entry.linesJson.equals(linesJson);
        boolean sameStructure = entry.structuredData.equals(structuredData);
        if (sameLines && sameStructure) {
            entry.writtenAt = now;
            byUpload.get(entry);
            return UploadMatch.RENEWED;
        }
        if (!sameLines && !sameStructure) return UploadMatch.MISS;
        return new UploadMatch(false, sameLines ? entry.textLines : null, sameStructure);
    }

    public synchronized void put(Player player, String id, String linesJson, List<String> lines, String structuredData) {
        if (closed) return;
        // Conservatively account for retained UTF-16 content and per-entry/list overhead.
        int entryWeight = 256 + 2 * (id.length() + linesJson.length() + structuredData.length());
        for (String line : lines) entryWeight += 48 + 2 * line.length();
        if (entryWeight > playerLimit || entryWeight > totalLimit) return;

        UUID uuid = player.getUniqueId();
        var bucket = players.get(uuid);
        if (bucket != null && bucket.owner != player) {
            removeBucket(bucket);
            bucket = null;
        }
        if (bucket == null) bucket = new PlayerCache(uuid, player);

        var previous = bucket.entries.get(id);
        if (previous != null) remove(previous);
        while (bucket.entries.size() >= MAX_ENTRIES_PER_PLAYER || bucket.weight + entryWeight > playerLimit) {
            remove(bucket.entries.values().iterator().next());
        }
        while (weight + entryWeight > totalLimit) remove(byUpload.keySet().iterator().next());

        var entry = new CachedEntry(bucket, id, linesJson, lines, structuredData, entryWeight, clock.getAsLong());
        players.put(uuid, bucket);
        bucket.entries.put(id, entry);
        byUpload.put(entry, Boolean.TRUE);
        bucket.weight += entryWeight;
        weight += entryWeight;
    }

    public synchronized @Nullable CachedEntry get(Player player, String id) {
        var bucket = players.get(player.getUniqueId());
        if (bucket == null || bucket.owner != player) return null;
        var entry = bucket.entries.get(id);
        if (entry != null && expired(entry, clock.getAsLong())) {
            remove(entry);
            return null;
        }
        return entry;
    }

    public @Nullable CachedEntry get(@NotNull Player player, @NotNull ItemStack itemStack) {
        return get(player, fingerprint(itemStack));
    }

    /** Removes a bounded page of the oldest expired entries. */
    public synchronized void cleanup() {
        long now = clock.getAsLong();
        for (int i = 0; i < CLEANUP_LIMIT && !byUpload.isEmpty(); i++) {
            var entry = byUpload.keySet().iterator().next();
            if (!expired(entry, now)) break;
            remove(entry);
        }
    }

    public synchronized void removePlayer(Player player) {
        var bucket = players.get(player.getUniqueId());
        if (bucket != null && bucket.owner == player) removeBucket(bucket);
    }

    public synchronized void close() {
        closed = true;
        byUpload.clear();
        players.clear();
        weight = 0;
    }

    public synchronized int playerCount() {
        return players.size();
    }

    private static boolean expired(CachedEntry entry, long now) {
        return now - entry.writtenAt >= TTL_NANOS;
    }

    private void removeBucket(PlayerCache bucket) {
        for (var entry : bucket.entries.values()) byUpload.remove(entry);
        weight -= bucket.weight;
        players.remove(bucket.uuid);
    }

    private void remove(CachedEntry entry) {
        var bucket = entry.bucket;
        byUpload.remove(entry);
        bucket.entries.remove(entry.id);
        weight -= entry.weight;
        bucket.weight -= entry.weight;
        if (bucket.entries.isEmpty()) players.remove(bucket.uuid);
    }

    /** Registry name shared with the client. Different stacks of the same type share display data. */
    public static String fingerprint(@NotNull ItemStack itemStack) {
        return itemStack.getType().getKey().toString();
    }

    private static final class PlayerCache {
        private final UUID uuid;
        private final Player owner;
        private final LinkedHashMap<String, CachedEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
        private int weight;

        private PlayerCache(UUID uuid, Player owner) {
            this.uuid = uuid;
            this.owner = owner;
        }
    }

    public record UploadMatch(boolean renewed, @Nullable List<String> textLines, boolean structuredUnchanged) {
        private static final UploadMatch MISS = new UploadMatch(false, null, false);
        private static final UploadMatch RENEWED = new UploadMatch(true, null, true);
    }

    public static final class CachedEntry {
        private final PlayerCache bucket;
        private final String id;
        private final String linesJson;
        private final List<String> textLines;
        private final String structuredData;
        private final int weight;
        private long writtenAt;

        private CachedEntry(PlayerCache bucket, String id, String linesJson, List<String> textLines,
                            String structuredData, int weight, long writtenAt) {
            this.bucket = bucket;
            this.id = id;
            this.linesJson = linesJson;
            this.textLines = List.copyOf(textLines);
            this.structuredData = structuredData;
            this.weight = weight;
            this.writtenAt = writtenAt;
        }

        public List<String> textLines() { return textLines; }
        public String structuredData() { return structuredData; }
    }
}
