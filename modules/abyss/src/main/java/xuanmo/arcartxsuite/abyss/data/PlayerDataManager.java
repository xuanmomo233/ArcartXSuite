package xuanmo.arcartxsuite.abyss.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家数据管理器：DB 的内存缓存 + 异步读写。
 * <p>
 * 读路径：缓存优先，未命中则异步加载后回填（调用方拿到默认值的场景下
 * 由监听器在 join 时预加载）。写路径：缓存即时更新 + 异步落盘。
 */
public final class PlayerDataManager {

    private final JdbcAbyssRepository repository;
    private final Logger logger;
    private final DatabaseQueue queue = new DatabaseQueue();

    @FunctionalInterface public interface DatabaseRead<T> { T execute() throws Exception; }

    public <T> java.util.concurrent.CompletableFuture<T> submitDatabase(DatabaseRead<T> task) {
        return queue.write(task::execute);
    }

    public <T> java.util.concurrent.CompletableFuture<T> submitMaintenance(DatabaseRead<T> task) {
        return queue.maintenance(task::execute);
    }

    private void write(Runnable task) {
        var future = queue.write(() -> { task.run(); return null; });
        if (future.isCompletedExceptionally()) future.join();
        future.exceptionally(failure -> {
            logger.warning("[abyss] 数据写入未完成: " + failure.getMessage());
            return null;
        });
    }

    public java.util.concurrent.CompletableFuture<Void> writeAdmission(
            JdbcAbyssRepository.AdmissionReceipt receipt, boolean reserve) {
        return queue.write(() -> {
                if (reserve) repository.insertAdmission(receipt);
                else repository.updateAdmission(receipt);
                return null;
        });
    }
    private final int defaultStamina;
    private final Map<UUID, java.util.concurrent.CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> ready = ConcurrentHashMap.newKeySet();

    private final Map<UUID, int[]> staminaCache = new ConcurrentHashMap<>();      // [stamina]
    private final Map<UUID, Long> staminaRegenCache = new ConcurrentHashMap<>();  // lastRegenMillis
    private final Map<UUID, Map<String, Long>> cooldownCache = new ConcurrentHashMap<>();
    /** 全服共享计数器缓存（跨副本/跨实例；启动时全量预载）。 */
    private final Map<String, Long> globalCache = new ConcurrentHashMap<>();
    /** 通关记录 TTL 缓存（占位符/TAB 等高频同步读防打穿 DB）。key=uuid:dungeonId */
    private final Map<String, RecordEntry> recordCache = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.CompletableFuture<long[]>> recordReads = new ConcurrentHashMap<>();
    private static final long RECORD_CACHE_TTL_MS = 30_000;

    private record RecordEntry(long completions, long bestTime, long expiry) { }
    private record LeaderboardKey(String dungeon, int limit) { }
    private record LeaderboardPage(java.util.List<JdbcAbyssRepository.LeaderboardEntry> rows, long expiry) { }
    private final Map<LeaderboardKey, LeaderboardPage> leaderboards = new ConcurrentHashMap<>();
    private final Map<LeaderboardKey, java.util.concurrent.CompletableFuture<java.util.List<JdbcAbyssRepository.LeaderboardEntry>>>
        leaderboardReads = new ConcurrentHashMap<>();

    public PlayerDataManager(JdbcAbyssRepository repository, Logger logger) {
        this(repository, logger, 100);
    }

    public PlayerDataManager(JdbcAbyssRepository repository, Logger logger, int defaultStamina) {
        this.repository = repository;
        this.logger = logger;
        this.defaultStamina = defaultStamina;
    }

    // ═══ 体力 ═══

    public int getStamina(UUID player, int maxIfAbsent) {
        preload(player);
        int[] c = staminaCache.get(player);
        return ready.contains(player) && c != null ? c[0] : 0;
    }

    public boolean ready(UUID player) { preload(player); return ready.contains(player); }

    public java.util.concurrent.CompletableFuture<Void> preload(UUID player) {
        var existing = loading.get(player);
        if (existing != null) return existing;
        var future = new java.util.concurrent.CompletableFuture<Void>();
        existing = loading.putIfAbsent(player, future);
        if (existing != null) return existing;
        queue.write(() -> {
            try {
                long[] row = repository.loadStamina(player);
                Map<String, Long> cooldowns = repository.loadAllCooldowns(player);
                synchronized (this) {
                    if (loading.get(player) == future) {
                        staminaCache.put(player, new int[]{row != null ? (int) row[0] : defaultStamina});
                        staminaRegenCache.put(player, row != null ? row[1] : System.currentTimeMillis());
                        cooldownCache.put(player, new ConcurrentHashMap<>(cooldowns));
                        ready.add(player);
                    }
                }
                future.complete(null);
            } catch (Exception error) {
                loading.remove(player, future);
                future.completeExceptionally(error);
                logger.warning("[abyss] 玩家数据加载失败，进本保持关闭: " + player + ": " + error.getMessage());
            }
            return null;
        }).exceptionally(failure -> {
            loading.remove(player, future);
            future.completeExceptionally(failure);
            return null;
        });
        return future;
    }

    public void setStamina(UUID player, int stamina, long lastRegen) {
        if (!ready.contains(player)) throw new IllegalStateException("玩家数据尚未加载完成");
        write(() -> {
            try {
                repository.saveStamina(player, stamina, lastRegen);
            } catch (Exception e) {
                logger.warning("[abyss] 体力保存失败 " + player + ": " + e.getMessage());
            }
        });
        staminaCache.put(player, new int[]{stamina});
        staminaRegenCache.put(player, lastRegen);
    }

    public long getStaminaLastRegen(UUID player) {
        return staminaRegenCache.getOrDefault(player, 0L);
    }

    // ═══ 冷却 ═══

    public long getCooldown(UUID player, String dungeonId) {
        preload(player);
        Map<String, Long> m = cooldownCache.get(player);
        if (m != null && m.containsKey(dungeonId)) {
            return m.get(dungeonId);
        }
        return 0;
    }

    public void setCooldown(UUID player, String dungeonId, long expiryMillis) {
        write(() -> {
            try {
                repository.saveCooldown(player, dungeonId, expiryMillis);
            } catch (Exception e) {
                logger.warning("[abyss] 冷却保存失败 " + player + ": " + e.getMessage());
            }
        });
        cooldownCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(dungeonId, expiryMillis);
    }

    public void preloadCooldowns(UUID player) {
        preload(player);
    }

    // ═══ 通关记录 ═══

    public synchronized void saveRecord(UUID player, String dungeonId, long timeSeconds) {
        // 失效记录缓存：新通关成绩立即可见
        recordCache.remove(player + ":" + dungeonId);
        recordReads.remove(player + ":" + dungeonId);
        leaderboards.keySet().removeIf(key -> key.dungeon().equals(dungeonId));
        leaderboardReads.keySet().removeIf(key -> key.dungeon().equals(dungeonId));
        write(() -> {
            try {
                repository.saveRecord(player, dungeonId, timeSeconds);
            } catch (Exception e) {
                logger.warning("[abyss] 通关记录保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    /**
     * 读记录（30s TTL 缓存）。
     * <p>占位符 %axsabyss_completions_%/%axsabyss_best_time_% 等高频同步路径
     * 走此缓存，防止计分板/TAB 每 tick 刷新打穿 DB。
     * 缓存未命中或到期时异步刷新，主线程调用返回最近快照。</p>
     */
    public long[] loadRecord(UUID player, String dungeonId) {
        String key = player + ":" + dungeonId;
        RecordEntry c = recordCache.get(key);
        if (c != null && System.currentTimeMillis() < c.expiry()) {
            return new long[]{c.completions(), c.bestTime()};
        }
        if (recordCache.size() > 2048) {
            long now = System.currentTimeMillis();
            recordCache.entrySet().removeIf(e -> e.getValue().expiry() <= now);
        }
        if (recordReads.size() < 256 && recordCache.size() <= 2048) {
            var request = new java.util.concurrent.CompletableFuture<long[]>();
            if (recordReads.putIfAbsent(key, request) == null) {
                queue.write(() -> repository.loadRecord(player, dungeonId)).whenComplete((row, error) -> {
                    synchronized (this) {
                    if (recordReads.get(key) == request) {
                        if (error == null) recordCache.put(key, new RecordEntry(row == null ? 0 : row[0],
                            row == null ? 0 : row[1], System.currentTimeMillis() + RECORD_CACHE_TTL_MS));
                        else if (c != null) recordCache.replace(key, c,
                            new RecordEntry(c.completions(), c.bestTime(), System.currentTimeMillis() + 5000));
                        recordReads.remove(key, request);
                    }
                    }
                    if (error == null) request.complete(row);
                    else request.completeExceptionally(error);
                });
            }
        }
        return c == null ? null : new long[]{c.completions(), c.bestTime()};
    }

    public java.util.List<JdbcAbyssRepository.LeaderboardEntry> loadLeaderboard(
            String dungeonId, int limit) {
        var key = new LeaderboardKey(dungeonId, Math.max(1, Math.min(200, limit)));
        var page = leaderboards.get(key);
        if (page == null || page.expiry() <= System.currentTimeMillis()) loadLeaderboardAsync(dungeonId, limit);
        return page == null ? java.util.List.of() : page.rows();
    }
    public java.util.concurrent.CompletableFuture<java.util.List<JdbcAbyssRepository.LeaderboardEntry>> loadLeaderboardAsync(String dungeonId,int limit) {
        var key = new LeaderboardKey(dungeonId, Math.max(1, Math.min(200, limit)));
        var request = new java.util.concurrent.CompletableFuture<java.util.List<JdbcAbyssRepository.LeaderboardEntry>>();
        var existing = leaderboardReads.putIfAbsent(key, request);
        if (existing != null) return existing.thenApply(java.util.List::copyOf);
        queue.read(() -> java.util.List.copyOf(repository.loadLeaderboard(dungeonId, key.limit())))
            .whenComplete((rows, error) -> {
                synchronized (this) {
                    if (leaderboardReads.get(key) == request) {
                        if (error == null) {
                            if (leaderboards.size() >= 128) leaderboards.entrySet().removeIf(entry ->
                                entry.getValue().expiry() <= System.currentTimeMillis());
                            if (leaderboards.size() < 128 || leaderboards.containsKey(key))
                                leaderboards.put(key, new LeaderboardPage(rows, System.currentTimeMillis() + 30000));
                        }
                        leaderboardReads.remove(key, request);
                    }
                }
                if (error == null) request.complete(rows);
                else request.completeExceptionally(error);
            });
        return request.thenApply(java.util.List::copyOf);
    }

    // ═══ 断线待恢复位置 ═══

    public void savePendingLocation(UUID player, Location loc) {
        savePendingLocation(player, loc, null, null);
    }

    public void savePendingLocation(UUID player, Location loc, String owner, UUID request) {
        if (loc == null || loc.getWorld() == null) return;
        String world = loc.getWorld().getName();
        write(() -> {
            try {
                repository.savePendingLocation(player, world,
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), owner, request);
            } catch (Exception e) {
                logger.warning("[abyss] 待恢复位置保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    public java.util.concurrent.CompletableFuture<JdbcAbyssRepository.PendingLocation> loadPendingLocation(UUID player) {
        return queue.write(() -> repository.loadPendingLocation(player));
    }
    public java.util.concurrent.CompletableFuture<Boolean> confirmPendingLocation(UUID player,JdbcAbyssRepository.PendingLocation expected) {
        return queue.write(() -> repository.confirmPendingLocation(player,expected));
    }

    // ═══ 全服共享计数器（跨副本的全局进度，如全服收集/归档） ═══

    /** 启动预载全部计数（表通常很小）。 */
    public void preloadGlobals() {
        write(() -> {
            try {
                globalCache.putAll(repository.loadAllGlobals());
            } catch (Exception e) {
                logger.warning("[abyss] 全局计数预载失败: " + e.getMessage());
            }
        });
    }

    /** 累加并返回新值（缓存即时生效 + 异步落盘）。 */
    public long addGlobal(String key, long delta) {
        long v = globalCache.merge(key, delta, Long::sum);
        persistGlobal(key, v);
        return v;
    }

    public void setGlobal(String key, long value) {
        globalCache.put(key, value);
        persistGlobal(key, value);
    }

    public long getGlobal(String key) {
        return globalCache.getOrDefault(key, 0L);
    }

    private void persistGlobal(String key, long value) {
        write(() -> {
            try {
                repository.saveGlobal(key, value);
            } catch (Exception e) {
                logger.warning("[abyss] 全局计数保存失败 " + key + ": " + e.getMessage());
            }
        });
    }

    /** 玩家下线清理缓存（数据已异步落盘）。 */
    public synchronized void unload(UUID player) {
        ready.remove(player); loading.remove(player);
        staminaCache.remove(player);
        staminaRegenCache.remove(player);
        cooldownCache.remove(player);
        // 记录缓存前缀清扫（key=uuid:dungeonId）
        String prefix = player.toString() + ":";
        recordCache.keySet().removeIf(k -> k.startsWith(prefix));
        recordReads.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void shutdown() {
        queue.close();
    }
}
