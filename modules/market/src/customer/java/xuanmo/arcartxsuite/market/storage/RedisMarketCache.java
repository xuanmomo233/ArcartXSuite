package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RedisConfiguration;

/**
 * Redis 拍卖和玩家店铺列表缓存。
 */
public class RedisMarketCache {
    private static final String AUCTION_PAGES = "market:listings:";
    private static final String PLAYER_SHOP_PAGES = "market:pshop:";

    private final RedisConfiguration config;
    private final Logger logger;
    private volatile JedisPool pool;

    public RedisMarketCache(RedisConfiguration config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void initialize() {
        if (!config.enabled()) return;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);

        if (config.password() == null || config.password().isEmpty()) {
            pool = new JedisPool(poolConfig, config.host(), config.port(), 3000, null, config.database());
        } else {
            pool = new JedisPool(poolConfig, config.host(), config.port(), 3000, config.password(), config.database());
        }

        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
            logger.info("Redis 连接成功: " + config.host() + ":" + config.port());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Redis 连接失败", e);
            pool.close();
            pool = null;
        }
    }

    public void shutdown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
        pool = null;
    }

    public boolean isAvailable() {
        return pool != null && !pool.isClosed();
    }

    public void cacheListings(String key, String json) {
        if (!isAvailable()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, config.cacheTtlSeconds(), json);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redis 缓存写入失败", e);
        }
    }

    public @Nullable String getCached(String key) {
        if (!isAvailable()) return null;
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redis 缓存读取失败", e);
            return null;
        }
    }

    /** An invalidated epoch is never reused, even if an old page is written back after its read. */
    public @Nullable String versionedListPageKey(UUID viewer, String baseKey) {
        String namespace = listNamespace(baseKey);
        if (namespace == null || !isAvailable()) return null;
        try (Jedis jedis = pool.getResource()) {
            String global = pageEpoch(jedis, namespace + "epoch");
            String personal = pageEpoch(jedis, namespace + "viewer-epoch:" + viewer);
            return baseKey + ":" + global + ":" + personal;
        } catch (Exception failure) {
            logger.log(Level.FINE, "Redis 市场列表缓存版本读取失败", failure);
            return null;
        }
    }

    private String pageEpoch(Jedis jedis, String key) {
        String found = jedis.get(key);
        return found != null ? found : rotatePageEpoch(jedis, key);
    }

    private String rotatePageEpoch(Jedis jedis, String key) {
        String next = UUID.randomUUID().toString();
        jedis.setex(key, Math.max(600, config.cacheTtlSeconds()), next);
        return next;
    }

    public void invalidateByPrefix(String prefix) {
        String namespace = listNamespace(prefix);
        if (namespace == null || !isAvailable()) return;
        try (Jedis jedis = pool.getResource()) {
            if (namespace.equals(prefix)) {
                rotatePageEpoch(jedis, namespace + "epoch");
            } else if (prefix.length() == namespace.length() + 37 && prefix.endsWith(":")) {
                String viewer = prefix.substring(namespace.length(), prefix.length() - 1);
                rotatePageEpoch(jedis, namespace + "viewer-epoch:" + viewer);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redis 缓存失效失败", e);
        }
    }

    private @Nullable String listNamespace(String key) {
        if (key.startsWith(AUCTION_PAGES)) return AUCTION_PAGES;
        if (key.startsWith(PLAYER_SHOP_PAGES)) return PLAYER_SHOP_PAGES;
        return null;
    }
}
