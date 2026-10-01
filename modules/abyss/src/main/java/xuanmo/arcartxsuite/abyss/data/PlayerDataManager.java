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

    private final Map<UUID, int[]> staminaCache = new ConcurrentHashMap<>();      // [stamina]
    private final Map<UUID, Long> staminaRegenCache = new ConcurrentHashMap<>();  // lastRegenMillis
    private final Map<UUID, Map<String, Long>> cooldownCache = new ConcurrentHashMap<>();
    /** 全服共享计数器缓存（跨副本/跨实例；启动时全量预载）。 */
    private final Map<String, Long> globalCache = new ConcurrentHashMap<>();

    public PlayerDataManager(JdbcAbyssRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Abyss-DB");
            t.setDaemon(true);
            return t;
        });
    }

    // ═══ 体力 ═══

    public int getStamina(UUID player, int maxIfAbsent) {
        int[] c = staminaCache.get(player);
        if (c != null) return c[0];
        // 异步加载并暂用最大值（避免阻塞主线程）
        staminaCache.put(player, new int[]{maxIfAbsent});
        dbExecutor.submit(() -> {
            try {
                long[] r = repository.loadStamina(player);
                if (r != null) {
                    staminaCache.put(player, new int[]{(int) r[0]});
                    staminaRegenCache.put(player, r[1]);
                }
            } catch (Exception e) {
                logger.warning("[abyss] 体力加载失败 " + player + ": " + e.getMessage());
            }
        });
        return maxIfAbsent;
    }

    public void setStamina(UUID player, int stamina, long lastRegen) {
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
        // 未缓存：同步读一次（调用方一般在异步/预处理路径），失败按 0 处理
        try {
            return repository.loadCooldown(player, dungeonId);
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
        dbExecutor.submit(() -> {
            try {
                Map<String, Long> m = repository.loadAllCooldowns(player);
                cooldownCache.put(player, new ConcurrentHashMap<>(m));
            } catch (Exception e) {
                logger.warning("[abyss] 冷却预载失败 " + player + ": " + e.getMessage());
            }
        });
    }

    // ═══ 通关记录 ═══

    public void saveRecord(UUID player, String dungeonId, long timeSeconds) {
        dbExecutor.submit(() -> {
            try {
                repository.saveRecord(player, dungeonId, timeSeconds);
            } catch (Exception e) {
                logger.warning("[abyss] 通关记录保存失败 " + player + ": " + e.getMessage());
            }
        });
    }

    /** 同步读记录（排行榜/统计 UI 调用，应在异步路径）。 */
    public long[] loadRecord(UUID player, String dungeonId) {
        try {
            return repository.loadRecord(player, dungeonId);
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

    /** 取待恢复位置（同步读+删，join 时异步调用）。 */
    public @Nullable Location takePendingLocation(UUID player) {
        try {
            JdbcAbyssRepository.PendingLocation p = repository.takePendingLocation(player);
            if (p == null) return null;
            org.bukkit.World w = Bukkit.getWorld(p.world());
            if (w == null) w = Bukkit.getWorlds().get(0);
            return new Location(w, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        } catch (Exception e) {
            logger.warning("[abyss] 待恢复位置读取失败 " + player + ": " + e.getMessage());
            return null;
        }
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
    public void unload(UUID player) {
        staminaCache.remove(player);
        staminaRegenCache.remove(player);
        cooldownCache.remove(player);
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
