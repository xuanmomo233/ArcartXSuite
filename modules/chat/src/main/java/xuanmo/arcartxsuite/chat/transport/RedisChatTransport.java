package xuanmo.arcartxsuite.chat.transport;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import xuanmo.arcartxsuite.chat.config.ChatRedisConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.service.ChatEnvelopeCodec;

public final class RedisChatTransport implements ChatTransport {

    private final JavaPlugin plugin;
    private final ChatRedisConfiguration configuration;
    private final Consumer<ChatEnvelope> consumer;
    private JedisPool pool;
    private ExecutorService subscriberExecutor;
    private JedisPubSub pubSub;
    private boolean active;

    public RedisChatTransport(JavaPlugin plugin, ChatRedisConfiguration configuration, Consumer<ChatEnvelope> consumer) {
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
            String password = configuration.password() != null && !configuration.password().isBlank()
                ? configuration.password() : null;
            pool = new JedisPool(
                new JedisPoolConfig(),
                configuration.host(),
                configuration.port(),
                configuration.connectTimeoutMs(),
                password,
                configuration.database()
            );
            subscriberExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AXS-Chat-Redis");
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
                        consumer.accept(ChatEnvelopeCodec.decode(message.substring(separator + 1)));
                    } catch (Exception exception) {
                        plugin.getLogger().warning("解析 Redis 聊天消息失败: " + exception.getMessage());
                    }
                }
            };
            subscriberExecutor.execute(this::subscribeLoop);
            active = true;
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("初始化 Chat Redis 广播失败: " + exception.getMessage());
            shutdown();
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void send(ChatEnvelope envelope) {
        if (!active || pool == null || envelope == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            prepare(jedis);
            jedis.publish(configuration.channel(), configuration.nodeId() + "\t" + ChatEnvelopeCodec.encode(envelope));
        } catch (Exception exception) {
            plugin.getLogger().warning("Chat Redis 发布失败: " + exception.getMessage());
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
                    plugin.getLogger().warning("Chat Redis 订阅中断，3 秒后重试: " + exception.getMessage());
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
        // 密码和 database 已在 JedisPool 构造时设置，无需手动处理
    }
}
