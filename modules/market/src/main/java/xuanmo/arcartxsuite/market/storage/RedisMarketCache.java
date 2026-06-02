package xuanmo.arcartxsuite.market.storage;

import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RedisConfiguration;

/**
 * Redis 缓存 + 跨服 Pub/Sub 广播。
 */
public class RedisMarketCache {

    private final RedisConfiguration config;
    private final Logger logger;
    private JedisPool pool;
    private Thread subscriberThread;
    private volatile boolean running;
    private @Nullable Consumer<String> messageHandler;

    public RedisMarketCache(RedisConfiguration config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void initialize(@Nullable Consumer<String> onMessage) {
        if (!config.enabled()) return;
        this.messageHandler = onMessage;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);

        if (config.password() == null || config.password().isEmpty()) {
            pool = new JedisPool(poolConfig, config.host(), config.port(), 3000, null, config.database());
        } else {
            pool = new JedisPool(poolConfig, config.host(), config.port(), 3000, config.password(), config.database());
        }

        // 验证连接
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
            logger.info("[Market] Redis 连接成功: " + config.host() + ":" + config.port());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Market] Redis 连接失败", e);
            pool.close();
            pool = null;
            return;
        }

        // 启动订阅线程
        running = true;
        subscriberThread = new Thread(this::subscribeLoop, "AXS-Market-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void shutdown() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    public boolean isAvailable() {
        return pool != null && !pool.isClosed();
    }

    /**
     * 发布跨服消息。
     */
    public void publish(String message) {
        if (!isAvailable()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(config.channel(), message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market] Redis 发布消息失败", e);
        }
    }

    /**
     * 缓存拍卖列表数据（JSON）。
     */
    public void cacheListings(String key, String json) {
        if (!isAvailable()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, config.cacheTtlSeconds(), json);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market] Redis 缓存写入失败", e);
        }
    }

    /**
     * 读取缓存。
     */
    public @Nullable String getCached(String key) {
        if (!isAvailable()) return null;
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market] Redis 缓存读取失败", e);
            return null;
        }
    }

    /**
     * 使指定前缀的缓存失效。
     */
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

    private void subscribeLoop() {
        while (running) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (messageHandler != null) {
                            messageHandler.accept(message);
                        }
                    }

                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        logger.fine("[Market] Redis 已订阅频道: " + channel);
                    }
                }, config.channel());
            } catch (Exception e) {
                if (running) {
                    logger.log(Level.WARNING, "[Market] Redis 订阅断开，5秒后重连...", e);
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
                }
            }
        }
    }
}
