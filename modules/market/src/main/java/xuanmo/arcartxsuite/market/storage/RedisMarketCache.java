package xuanmo.arcartxsuite.market.storage;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RedisConfiguration;

/**
 * Redis 拍卖列表缓存（Pub/Sub 已迁移至统一 CrossServer SDK）。
 */
public class RedisMarketCache {

    private final RedisConfiguration config;
    private final Logger logger;
    private JedisPool pool;

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
            logger.info("[Market] Redis 连接成功: " + config.host() + ":" + config.port());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Market] Redis 连接失败", e);
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
            logger.log(Level.WARNING, "[Market] Redis 缓存写入失败", e);
        }
    }

    public @Nullable String getCached(String key) {
        if (!isAvailable()) return null;
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market] Redis 缓存读取失败", e);
            return null;
        }
    }

    public void invalidateByPrefix(String prefix) {
        if (!isAvailable()) return;
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = jedis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market] Redis 缓存失效失败", e);
        }
    }
}
