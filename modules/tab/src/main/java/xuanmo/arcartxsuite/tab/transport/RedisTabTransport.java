package xuanmo.arcartxsuite.tab.transport;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import xuanmo.arcartxsuite.tab.config.TabRedisConfiguration;

public final class RedisTabTransport implements TabTransport {

    private final JavaPlugin plugin;
    private final TabRedisConfiguration configuration;
    private final Consumer<TabServerSnapshot> consumer;
    private JedisPool pool;
    private ExecutorService subscriberExecutor;
    private JedisPubSub pubSub;
    private boolean active;

    public RedisTabTransport(JavaPlugin plugin, TabRedisConfiguration configuration, Consumer<TabServerSnapshot> consumer) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public boolean start() {
        if (!configuration.enabled()) {
            return false;
        }
        try {
            pool = new JedisPool(configuration.host(), configuration.port());
            subscriberExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AXS-Tab-Redis");
                thread.setDaemon(true);
                return thread;
            });
            pubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if (message == null || message.isBlank()) {
                        return;
                    }
                    int separator = message.indexOf('\t');
                    if (separator <= 0) {
                        return;
                    }
                    String nodeId = message.substring(0, separator);
                    if (configuration.nodeId().equalsIgnoreCase(nodeId)) {
                        return;
                    }
                    try {
                        consumer.accept(TabSnapshotCodec.decode(message.substring(separator + 1)));
                    } catch (Exception exception) {
                        plugin.getLogger().warning("解析 Redis Tab 消息失败: " + exception.getMessage());
                    }
                }
            };
            subscriberExecutor.execute(this::subscribeLoop);
            active = true;
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("初始化 Tab Redis 广播失败: " + exception.getMessage());
            shutdown();
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void send(TabServerSnapshot snapshot) {
        if (!active || pool == null || snapshot == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            prepare(jedis);
            jedis.publish(configuration.channel(), configuration.nodeId() + "\t" + TabSnapshotCodec.encode(snapshot));
        } catch (Exception exception) {
            plugin.getLogger().warning("Tab Redis 发布失败: " + exception.getMessage());
        }
    }

    @Override
    public void shutdown() {
        active = false;
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
            pubSub = null;
        }
        if (subscriberExecutor != null) {
            subscriberExecutor.shutdownNow();
            subscriberExecutor = null;
        }
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ignored) {
            }
            pool = null;
        }
    }

    @Override
    public String name() {
        return "redis";
    }

    private void subscribeLoop() {
        while (active && pool != null && pubSub != null) {
            try (Jedis jedis = pool.getResource()) {
                prepare(jedis);
                jedis.subscribe(pubSub, configuration.channel());
                return;
            } catch (Exception exception) {
                if (active) {
                    plugin.getLogger().warning("Tab Redis 订阅中断，3 秒后重试: " + exception.getMessage());
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void prepare(Jedis jedis) {
        if (configuration.password() != null && !configuration.password().isBlank()) {
            jedis.auth(configuration.password());
        }
        if (configuration.database() > 0) {
            jedis.select(configuration.database());
        }
    }
}
