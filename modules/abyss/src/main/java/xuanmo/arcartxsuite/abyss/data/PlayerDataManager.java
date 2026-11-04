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
    private final ExecutorService dbExecutor;

    public java.util.concurrent.CompletableFuture<Void> writeAdmission(
            JdbcAbyssRepository.AdmissionReceipt receipt, boolean reserve) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (reserve) repository.insertAdmission(receipt);
                else repository.updateAdmission(receipt);
            } catch (java.sql.SQLException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, dbExecutor);
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
    private static final long RECORD_CACHE_TTL_MS = 30_000;

    private record RecordEntry(long completions, long bestTime, long expiry) { }

    public PlayerDataManager(JdbcAbyssRepository repository, Logger logger) {
        this(repository, logger, 100);
    }

    public PlayerDataManager(JdbcAbyssRepository repository, Logger logger, int defaultStamina) {
        this.repository = repository;
        this.logger = logger;
        this.defaultStamina = defaultStamina;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Abyss-DB");
            t.setDaemon(true);
            return t;
        });
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
        dbExecutor.submit(() -> {
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
        });
        return future;
    }

    public void setStamina(UUID player, int stamina, long lastRegen) {
        if (!ready.contains(player)) throw new IllegalStateException("玩家数据尚未加载完成");
        staminaCache.put(player, new int[]{stamina});
        staminaRegenCache.put(player, lastRegen);
        dbExecutor.submit(() -> {
            try {
                repository.saveStamina(player, stamina, lastRegen);
            } catch (Exception e) {
                logger.warning("[abyss] 体力保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    public long getStaminaLastRegen(UUID player) {
        return staminaRegenCache.getOrDefault(player, 0L);
    }

    // ═══ 冷却 ═══

    public long getCooldown(UUID player, String dungeonId) {
        Map<String, Long> m = cooldownCache.get(player);
        if (m != null && m.containsKey(dungeonId)) {
            return m.get(dungeonId);
        }
        // 未缓存：同步读一次并回写缓存（含未命中结果——防占位符等高频路径
        // 对从未玩过的地牢每次刷新都打一次 DB）；失败按 0 处理
        try {
            long v = repository.loadCooldown(player, dungeonId);
            cooldownCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(dungeonId, v);
            return v;
        } catch (Exception e) {
            return 0;
        }
    }

    public void setCooldown(UUID player, String dungeonId, long expiryMillis) {
        cooldownCache.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .put(dungeonId, expiryMillis);
        dbExecutor.submit(() -> {
            try {
                repository.saveCooldown(player, dungeonId, expiryMillis);
            } catch (Exception e) {
                logger.warning("[abyss] 冷却保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    public void preloadCooldowns(UUID player) {
        preload(player);
    }

    // ═══ 通关记录 ═══

    public void saveRecord(UUID player, String dungeonId, long timeSeconds) {
        // 失效记录缓存：新通关成绩立即可见
        recordCache.remove(player + ":" + dungeonId);
        dbExecutor.submit(() -> {
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
     * 缓存未命中时仍是同步 DB 读——首查路径调用方应在异步或低频上下文。</p>
     */
    public long[] loadRecord(UUID player, String dungeonId) {
        String key = player + ":" + dungeonId;
        RecordEntry c = recordCache.get(key);
        if (c != null && System.currentTimeMillis() < c.expiry()) {
            return new long[]{c.completions(), c.bestTime()};
        }
        // 惰性清扫过期项（防长期运行积累离线玩家条目）
        if (recordCache.size() > 512) {
            long now = System.currentTimeMillis();
            recordCache.entrySet().removeIf(e -> e.getValue().expiry() <= now);
        }
        try {
            long[] r = repository.loadRecord(player, dungeonId);
            if (r != null) {
                recordCache.put(key, new RecordEntry(r[0], r[1],
                    System.currentTimeMillis() + RECORD_CACHE_TTL_MS));
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public java.util.List<JdbcAbyssRepository.LeaderboardEntry> loadLeaderboard(
            String dungeonId, int limit) {
        try {
            return repository.loadLeaderboard(dungeonId, limit);
        } catch (Exception e) {
            logger.warning("[abyss] 排行榜加载失败 " + dungeonId + ": " + e.getMessage());
            return java.util.List.of();
        }
    }
    public java.util.concurrent.CompletableFuture<java.util.List<JdbcAbyssRepository.LeaderboardEntry>> loadLeaderboardAsync(String dungeonId,int limit) {
        return java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return repository.loadLeaderboard(dungeonId,Math.max(1,Math.min(200,limit)));}
            catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
        },dbExecutor);
    }

    // ═══ 断线待恢复位置 ═══

    public void savePendingLocation(UUID player, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        dbExecutor.submit(() -> {
            try {
                repository.savePendingLocation(player, loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            } catch (Exception e) {
                logger.warning("[abyss] 待恢复位置保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    public java.util.concurrent.CompletableFuture<JdbcAbyssRepository.PendingLocation> loadPendingLocation(UUID player) {
        return java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return repository.loadPendingLocation(player);}
            catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
        },dbExecutor);
    }
    public java.util.concurrent.CompletableFuture<Boolean> confirmPendingLocation(UUID player,JdbcAbyssRepository.PendingLocation expected) {
        return java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return repository.confirmPendingLocation(player,expected);}
            catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
        },dbExecutor);
    }

    // ═══ 全服共享计数器（跨副本的全局进度，如全服收集/归档） ═══

    /** 启动预载全部计数（表通常很小）。 */
    public void preloadGlobals() {
        dbExecutor.submit(() -> {
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
        dbExecutor.submit(() -> {
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
    }

    public void shutdown() {
        dbExecutor.shutdown();
        try {
            dbExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
