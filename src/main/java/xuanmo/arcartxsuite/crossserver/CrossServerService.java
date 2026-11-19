package xuanmo.arcartxsuite.crossserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.Bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.resps.StreamEntry;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

/**
 * {@link CrossServerAPI} 的默认实现，支持 Redis 与 Proxy 插件消息双后端跨服传输。
 * <p>
 * 通过 HMAC 签名信封，按频道发布/订阅消息，并路由到对应模块的处理器。
 * Redis 不可用时自动降级到 PluginMessage 通道。
 */
public final class CrossServerService implements CrossServerAPI, PluginMessageListener {
    private static final CompletableFuture<Boolean> DELIVERED = CompletableFuture.completedFuture(true);

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final int PROXY_MAX_BYTES = Short.MAX_VALUE;
    private static final long INBOX_RETRY_MS = 250;
    private static final int INBOUND_QUEUE_MESSAGES = 2_048;
    private static final int INBOUND_BATCH_SIZE = 128;
    private static final int INBOUND_CALLBACKS_PER_TICK = 32;
    private static final long INBOUND_CALLBACK_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(4);
    private static final long INBOUND_BATCH_WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(3);
    private static final int REDIS_QUEUE_CHARS = 8 * 1024 * 1024;

    private final JavaPlugin plugin;
    private volatile CrossServerGlobalConfiguration configuration;
    private volatile InboundWorker inboundWorker;
    private final Map<String, ModuleRegistration> registrations = new ConcurrentHashMap<>();
    private final AtomicLong nextInboundWarning = new AtomicLong();
    private final AtomicLong redisRegistrationRevision = new AtomicLong();

    private volatile JedisPool jedisPool;
    private volatile CrossServerRedisOutbox redisPublisher;
    private xuanmo.arcartxsuite.api.scheduler.ModuleExecutor redisSubscriberExecutor;
    private volatile boolean redisSubscriberActive;
    private volatile boolean proxyActive;

    public CrossServerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configuration = CrossServerGlobalConfiguration.disabled();
    }

    public void start() {
        start(CompletableFuture.completedFuture(null));
    }

    public void start(CompletionStage<Void> modulesReady) {
        shutdownBackends();
        configuration = CrossServerGlobalConfiguration.from(plugin.getConfig());
        boolean enabled = configuration.redisEnabled() || configuration.proxyEnabled();
        if (enabled && !CrossServerGlobalConfiguration.validEnvelopeField(configuration.nodeId())) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CrossServer] node-id 无效，跨服传输未启动");
            return;
        }
        if (enabled && configuration.missingSignatureSecret()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 跨服消息已禁用：signature.enabled 或 signature.verify 已开启，必须配置有效的 secret"
            );
        } else if (enabled && !configuration.shouldVerify()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 未要求入站签名。请仅在可信传输网络使用此配置"
            );
        }
        if (enabled && !configuration.missingSignatureSecret()) {
            inboundWorker = new InboundWorker(configuration, modulesReady.toCompletableFuture());
        }
        if (configuration.redisEnabled()) {
            startRedis(modulesReady);
        }
        if (configuration.proxyEnabled() && !configuration.missingSignatureSecret()) {
            startProxy();
        }
        if (configuration.redisEnabled() || configuration.proxyEnabled()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info(
                "[CrossServer] 已启动 | node=" + configuration.nodeId()
                    + " | redis=" + (redisPublisher != null)
                    + " | proxy=" + proxyActive
                    + " | sign=" + configuration.shouldSign()
                    + " | verify=" + configuration.shouldVerify()
            );
        }
    }

    public CompletionStage<Void> shutdown() { return shutdownAsync(); }

    CompletionStage<Void> shutdownAsync() {
        for (ModuleRegistration registration : registrations.values()) {
            registration.closed = true;
        }
        registrations.clear();
        return shutdownBackends();
    }

    @Override
    public String nodeId() {
        return configuration.nodeId();
    }

    /** Redis 后端是否已激活（供 axs-core 内部选主服务使用）。 */
    public boolean isRedisAvailable() {
        JedisPool pool = jedisPool;
        return pool != null && !pool.isClosed();
    }

    /** 获取 Redis 连接池（供 axs-core 内部选主服务使用，Redis 不可用时返回 null）。 */
    @org.jetbrains.annotations.Nullable
    public JedisPool getJedisPoolInternal() {
        return jedisPool;
    }

    @Override
    public CrossServerChannel openChannel(
        String moduleId,
        CrossServerChannelConfig config,
        Consumer<CrossServerDelivery> consumer
    ) {
        Objects.requireNonNull(consumer, "consumer");
        return registerChannel(moduleId, config, delivery -> {
            consumer.accept(delivery);
            return DELIVERED;
        }, Admission.DIRECT);
    }

    @Override
    public CrossServerChannel openChannelAdmitting(String moduleId, CrossServerChannelConfig config,
                                                    Predicate<CrossServerDelivery> admit) {
        Objects.requireNonNull(admit, "admit");
        return registerChannel(moduleId, config, delivery -> admit.test(delivery) ? DELIVERED : null, Admission.BUFFERED);
    }

    @Override
    public CrossServerChannel openChannelCompleting(String moduleId, CrossServerChannelConfig config,
            Function<CrossServerDelivery, CompletionStage<Boolean>> admit) {
        return registerChannel(moduleId, config, Objects.requireNonNull(admit, "admit"), Admission.CONFIRMED);
    }

    private CrossServerChannel registerChannel(String moduleId, CrossServerChannelConfig config,
            Function<CrossServerDelivery, CompletionStage<Boolean>> admit, Admission admission) {
        if (!CrossServerGlobalConfiguration.validEnvelopeField(moduleId)) {
            throw new IllegalArgumentException("Invalid cross-server module id");
        }
        CrossServerChannelConfig channelConfig = config == null ? CrossServerChannelConfig.disabled() : config;
        ModuleRegistration registration = new ModuleRegistration(moduleId, channelConfig, admit, admission);
        registrations.compute(moduleId, (id, existing) -> {
            if (existing != null) {
                existing.closed = true;
            }
            return registration;
        });
        redisRegistrationRevision.incrementAndGet();
        return new CrossServerChannelHandle(registration);
    }

    private void startRedis(CompletionStage<Void> modulesReady) {
        CrossServerGlobalConfiguration activeConfig = configuration;
        try {
            String password = activeConfig.redisPassword().isBlank() ? null : activeConfig.redisPassword();
            String username = activeConfig.redisUsername().isBlank() ? null : activeConfig.redisUsername();
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxWait(Duration.ofMillis(activeConfig.redisConnectTimeoutMs()));
            JedisPool pool = new JedisPool(
                poolConfig,
                new HostAndPort(activeConfig.redisHost(), activeConfig.redisPort()),
                DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(activeConfig.redisConnectTimeoutMs())
                    .socketTimeoutMillis(activeConfig.redisConnectTimeoutMs())
                    .blockingSocketTimeoutMillis(activeConfig.redisConnectTimeoutMs())
                    .user(username)
                    .password(password)
                    .database(activeConfig.redisDatabase())
                    .build()
            );
            jedisPool = pool;
            if (activeConfig.missingSignatureSecret()) {
                // 保留 Redis 连接池供独立的跨服选主功能使用。
                return;
            }
            int frameBytes = (int) Math.min(REDIS_QUEUE_CHARS, 3L * activeConfig.maxPayloadChars());
            var stream = new CrossServerRedisStream(activeConfig, frameBytes);
            redisPublisher = new CrossServerRedisOutbox(pool, activeConfig, stream,
                plugin.getDataFolder().toPath().resolve("data/cross-server-outbox.db"), frameBytes,
                () -> registrations.values().stream()
                    .filter(registration -> registration.accepts(Transport.REDIS, activeConfig))
                    .map(registration -> registration.moduleId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
            redisSubscriberActive = true;
            redisSubscriberExecutor = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(1, 1, 0,
                java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "AXS-CrossServer-Redis");
                thread.setDaemon(true);
                return thread;
            });
            var receiver = new RedisStreamReceiver(pool, stream, inboundWorker, frameBytes, modulesReady.toCompletableFuture());
            ExecutorService receiverExecutor = redisSubscriberExecutor;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (redisSubscriberExecutor == receiverExecutor && redisSubscriberActive) {
                    receiverExecutor.execute(receiver::run);
                }
            });
        } catch (Exception exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CrossServer] Redis 初始化失败: " + exception.getMessage());
            shutdownRedis();
        }
    }

    private void startProxy() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        proxyActive = true;
    }

    private CompletionStage<Void> shutdownBackends() {
        InboundWorker worker = inboundWorker;
        var publisher = redisPublisher;
        var receiver = redisSubscriberExecutor;
        inboundWorker = null;
        if (worker != null) worker.thread.interrupt();
        shutdownRedis();
        shutdownProxy();
        return CompletableFuture.allOf(
            worker == null ? CompletableFuture.completedFuture(null) : worker.stopped,
            publisher == null ? CompletableFuture.completedFuture(null) : publisher.stopped().toCompletableFuture(),
            receiver == null ? CompletableFuture.completedFuture(null) : receiver.stopped().toCompletableFuture());
    }

    private void shutdownRedis() {
        redisSubscriberActive = false;
        CrossServerRedisOutbox publisher = redisPublisher;
        redisPublisher = null;
        if (publisher != null) {
            publisher.shutdown();
        }
        if (redisSubscriberExecutor != null) {
            redisSubscriberExecutor.shutdownNow();
            redisSubscriberExecutor = null;
        }
        if (jedisPool != null) {
            try {
                jedisPool.close();
            } catch (Exception ignored) {
            }
            jedisPool = null;
        }
    }

    private void shutdownProxy() {
        if (!proxyActive) {
            return;
        }
        proxyActive = false;
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        CrossServerGlobalConfiguration activeConfig = configuration;
        if (!proxyActive || !BUNGEE_CHANNEL.equals(channel) || activeConfig.missingSignatureSecret()) {
            return;
        }
        String wire = decodeProxyPayload(message, activeConfig.proxyMessengerChannel());
        if (wire != null) {
            handleWireMessage(wire, Transport.PROXY, activeConfig);
        }
    }

    static String decodeProxyPayload(byte[] message, String expectedChannel) {
        if (message == null || message.length > Messenger.MAX_MESSAGE_SIZE) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            // Forward 请求中的目标服和命令名已被代理消费，转发后的帧从子频道开始。
            if (!expectedChannel.equalsIgnoreCase(input.readUTF())) {
                return null;
            }
            int length = input.readUnsignedShort();
            if (length == 0 || length > PROXY_MAX_BYTES || input.available() != length) {
                return null;
            }
            return new String(input.readNBytes(length), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private void handleWireMessage(String wire, Transport transport, CrossServerGlobalConfiguration activeConfig) {
        CrossServerEnvelope envelope = verifyWire(wire, activeConfig, false);
        InboundWorker worker = inboundWorker;
        if (envelope == null || worker == null || worker.config != activeConfig) return;
        InboundMessage message = inboundMessage(envelope, wire, transport, activeConfig);
        if (message != null) worker.submit(message);
    }

    private CrossServerEnvelope verifyWire(String wire, CrossServerGlobalConfiguration activeConfig, boolean fromInbox) {
        if (configuration != activeConfig || activeConfig.missingSignatureSecret()
            || wire == null || wire.length() > activeConfig.maxPayloadChars() || wire.isBlank()) {
            return null;
        }
        try {
            CrossServerEnvelope envelope = CrossServerEnvelopeCodec.decode(wire);
            if (envelope.protocol() != CrossServerEnvelope.PROTOCOL_VERSION
                || !CrossServerGlobalConfiguration.validEnvelopeField(envelope.nodeId())
                || !CrossServerGlobalConfiguration.validEnvelopeField(envelope.messageId())
                || activeConfig.nodeId().equalsIgnoreCase(envelope.nodeId())
                || envelope.timestamp() <= 0
                || (!fromInbox && !activeConfig.acceptsTimestamp(envelope.timestamp(), System.currentTimeMillis()))) {
                return null;
            }
            boolean verify = activeConfig.shouldVerify();
            if (verify && !CrossServerSigner.verify(envelope, envelope.signature(), secretBytes(activeConfig))) {
                warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] 拒绝未签名或签名无效的跨服消息");
                return null;
            }
            return envelope;
        } catch (RuntimeException exception) {
            warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] 无效的跨服信封或调度已关闭");
            return null;
        }
    }

    private InboundMessage inboundMessage(CrossServerEnvelope envelope, String wire, Transport transport,
                                          CrossServerGlobalConfiguration activeConfig) {
        ModuleRegistration registration = registrations.get(envelope.module());
        if (registration == null || !registration.accepts(transport, activeConfig)) return null;
        var delivery = new CrossServerDelivery(envelope.module(), envelope.nodeId(), envelope.messageId(),
            envelope.payload(), activeConfig.shouldVerify(), envelope.timestamp());
        String proxyWire = transport == Transport.PROXY && registration.admission == Admission.CONFIRMED ? wire : null;
        int size = wire.length() + (proxyWire != null && envelope.payload() != null ? envelope.payload().length() : 0);
        return new InboundMessage(delivery, envelope.timestamp(), size, transport, registration, proxyWire);
    }

    private CompletionStage<Boolean> publishEnvelope(CrossServerEnvelope unsignedEnvelope, ModuleRegistration registration,
                                                     CrossServerGlobalConfiguration activeConfig, boolean durableOnly) {
        if (unsignedEnvelope.payload() != null
            && unsignedEnvelope.payload().length() > activeConfig.maxPayloadChars()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 模块 " + unsignedEnvelope.module() + " payload 超过上限，已跳过发送"
            );
            return CompletableFuture.completedFuture(false);
        }
        CrossServerRedisOutbox publisher = redisPublisher;
        boolean sendRedis = publisher != null && publisher.configuration == activeConfig
            && registration.accepts(Transport.REDIS, activeConfig);
        boolean sendProxy = !durableOnly && proxyActive && registration.accepts(Transport.PROXY, activeConfig);
        if (!sendRedis && !sendProxy) {
            return CompletableFuture.completedFuture(false);
        }
        if (sendRedis && !sendProxy) {
            int reserved = publisher.serializationReservation(unsignedEnvelope);
            if (reserved > 0) return publisher.publish(unsignedEnvelope, reserved);
        }
        CrossServerEnvelope envelope = activeConfig.shouldSign()
            ? CrossServerSigner.withSignature(unsignedEnvelope, secretBytes(activeConfig))
            : unsignedEnvelope;
        String wire = CrossServerEnvelopeCodec.encode(envelope);
        if (wire.length() > activeConfig.maxPayloadChars()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 模块 " + registration.moduleId + " 跨服信封超过上限，已跳过发送"
            );
            return CompletableFuture.completedFuture(false);
        }
        CompletionStage<Boolean> result = sendRedis
            ? publisher.publish(wire, unsignedEnvelope.timestamp(), registration.moduleId)
            : CompletableFuture.completedFuture(false);
        if (sendProxy) {
            var proxyReceipt = new CompletableFuture<Boolean>();
            Runnable send = () -> {
                if (!proxyReceipt.isCancelled() && proxyActive && configuration == activeConfig && !registration.closed) {
                    proxyReceipt.complete(publishProxy(wire, registration.moduleId, activeConfig));
                } else {
                    proxyReceipt.complete(false);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                send.run();
            } else {
                try {
                    Bukkit.getScheduler().runTask(plugin, send);
                } catch (IllegalPluginAccessException ignored) {
                    proxyReceipt.complete(false);
                }
            }
            if (!sendRedis) return proxyReceipt;
        }
        return result;
    }

    private boolean publishProxy(String wire, String moduleId, CrossServerGlobalConfiguration activeConfig) {
        byte[] payloadBytes = wire.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > PROXY_MAX_BYTES) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 模块 " + moduleId + " Proxy 负载 " + payloadBytes.length
                    + " 字节超过 " + PROXY_MAX_BYTES + "，已跳过 Proxy"
            );
            return false;
        }
        byte[] message = buildProxyMessage(payloadBytes, activeConfig);
        if (message == null) {
            return false;
        }
        if (message.length > Messenger.MAX_MESSAGE_SIZE) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] 模块 " + moduleId + " Proxy 完整帧超过传输上限，已跳过 Proxy"
            );
            return false;
        }
        // 尝试最多 3 个不同的在线玩家作为消息载体
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length == 0) return false;
        int maxAttempts = Math.min(3, players.length);
        for (int i = 0; i < maxAttempts; i++) {
            Player carrier = players[i];
            if (!carrier.isOnline()) {
                continue;
            }
            try {
                carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, message);
                return true;
            } catch (Exception exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                    "[CrossServer] Proxy 发布失败(尝试 " + (i + 1) + "/" + maxAttempts
                        + " 玩家=" + carrier.getName() + "): " + exception.getMessage()
                );
            }
        }
        xuanmo.arcartxsuite.module.AxsLog.logger().warning(
            "[CrossServer] 模块 " + moduleId + " Proxy 发布失败：所有候选玩家均发送失败。"
        );
        return false;
    }

    private byte[] buildProxyMessage(byte[] payloadBytes, CrossServerGlobalConfiguration activeConfig) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeUTF("Forward");
                output.writeUTF(activeConfig.proxyForwardTarget());
                output.writeUTF(activeConfig.proxyMessengerChannel());
                output.writeShort(payloadBytes.length);
                output.write(payloadBytes);
            }
            return buffer.toByteArray();
        } catch (Exception exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[CrossServer] Proxy 消息构建失败: " + exception.getMessage()
            );
            return null;
        }
    }

    private static byte[] secretBytes(CrossServerGlobalConfiguration configuration) {
        return configuration.signatureSecret().getBytes(StandardCharsets.UTF_8);
    }

    private static void warnAtMostOncePerInterval(AtomicLong nextWarning, String message) {
        long now = System.currentTimeMillis();
        long next = nextWarning.get();
        if (now >= next && nextWarning.compareAndSet(next, now + 10_000L)) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(message);
        }
    }

    private enum Transport {
        REDIS, PROXY
    }

    private enum Admission {
        DIRECT, BUFFERED, CONFIRMED
    }

    private record InboundMessage(CrossServerDelivery delivery, long timestamp, int size,
                                  Transport transport, ModuleRegistration registration, String proxyWire) {}

    private final class InboundWorker {
        private final CrossServerGlobalConfiguration config;
        private final CrossServerReplayStore store;
        private final CompletableFuture<Void> modulesReady;
        private final String proxyInboxScope;
        private final ArrayBlockingQueue<InboundMessage> pending = new ArrayBlockingQueue<>(INBOUND_QUEUE_MESSAGES);
        private final Semaphore queuedMessages = new Semaphore(INBOUND_QUEUE_MESSAGES);
        private final Semaphore queuedChars = new Semaphore(REDIS_QUEUE_CHARS);
        private final Thread thread;
        private final CompletableFuture<Void> stopped = new CompletableFuture<>();
        private long proxyInboxRevision = Long.MIN_VALUE;
        private long proxyInboxNextCheck;
        private long proxyInboxDeferredUntil = Long.MAX_VALUE;
        private long proxyInboxAfterRow;
        private long proxyInboxRetryAt;
        private InboxDispatch proxyDispatch;

        private InboundWorker(CrossServerGlobalConfiguration config, CompletableFuture<Void> modulesReady) {
            this.config = config;
            this.modulesReady = modulesReady;
            this.proxyInboxScope = "proxy\n" + config.nodeId().toLowerCase(java.util.Locale.ROOT);
            this.store = new CrossServerReplayStore(plugin.getDataFolder().toPath().resolve("data/cross-server-replay.db"));
            this.thread = new Thread(this::run, "AXS-CrossServer-Inbound");
            thread.setDaemon(true);
            thread.start();
        }

        private void submit(InboundMessage message) {
            boolean hasSlot = false;
            boolean hasChars = false;
            boolean enqueued = false;
            try {
                if (message.size() > REDIS_QUEUE_CHARS) {
                    warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] 信封超过跨服入站内存上限");
                    return;
                }
                hasSlot = queuedMessages.tryAcquire();
                hasChars = hasSlot && queuedChars.tryAcquire(message.size());
                if (!hasChars) {
                    warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] 跨服入站队列已满");
                    return;
                }
                if (inboundWorker == this && !stopped.isDone()
                    && config.acceptsTimestamp(message.timestamp(), System.currentTimeMillis())) {
                    enqueued = pending.offer(message);
                }
            } finally {
                if (!enqueued) {
                    if (hasChars) queuedChars.release(message.size());
                    if (hasSlot) queuedMessages.release();
                }
            }
        }

        private void run() {
            try (store) {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        advanceProxyInbox();
                        InboundMessage first = pending.poll(
                            proxyDispatch == null ? INBOX_RETRY_MS : 25, TimeUnit.MILLISECONDS);
                        if (first == null) continue;
                        var batch = new ArrayList<InboundMessage>(INBOUND_BATCH_SIZE);
                        batch.add(first);
                        long deadline = System.nanoTime() + INBOUND_BATCH_WAIT_NANOS;
                        while (batch.size() < INBOUND_BATCH_SIZE) {
                            pending.drainTo(batch, INBOUND_BATCH_SIZE - batch.size());
                            long remaining = deadline - System.nanoTime();
                            if (batch.size() == INBOUND_BATCH_SIZE || remaining <= 0) break;
                            InboundMessage next = pending.poll(remaining, TimeUnit.NANOSECONDS);
                            if (next == null) break;
                            batch.add(next);
                        }
                        persistAndDispatch(batch);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (SQLException | RuntimeException failure) {
                stopped.completeExceptionally(failure);
            } finally {
                if (proxyDispatch != null) proxyDispatch.close();
                pending.clear();
                stopped.complete(null);
            }
        }

        private boolean active() {
            return inboundWorker == this && !Thread.currentThread().isInterrupted();
        }

        private void advanceProxyInbox() {
            if (!modulesReady.isDone() || modulesReady.isCompletedExceptionally() || !active()) return;
            long now = System.currentTimeMillis();
            if (now < proxyInboxRetryAt) return;
            try {
                if (proxyDispatch != null) {
                    proxyDispatch.saveCompleted(store);
                    if (!proxyDispatch.settled()) return;
                    boolean unconfirmed = !proxyDispatch.confirmed();
                    proxyDispatch.close();
                    proxyDispatch = null;
                    if (unconfirmed) {
                        long retry = now + INBOX_RETRY_MS;
                        proxyInboxDeferredUntil = Math.min(proxyInboxDeferredUntil, retry);
                        if (proxyInboxAfterRow == 0) {
                            proxyInboxNextCheck = Math.min(proxyInboxNextCheck, retry);
                            proxyInboxRetryAt = retry;
                            return;
                        }
                    }
                }
                long revision = redisRegistrationRevision.get();
                if (revision != proxyInboxRevision) {
                    proxyInboxRevision = revision;
                    proxyInboxAfterRow = 0;
                    proxyInboxNextCheck = 0;
                }
                if (now < proxyInboxNextCheck) return;
                if (proxyInboxAfterRow == 0) proxyInboxDeferredUntil = Long.MAX_VALUE;
                var batch = readInboxBatch(store, proxyInboxScope, proxyInboxAfterRow, Transport.PROXY, config,
                    PROXY_MAX_BYTES * INBOUND_BATCH_SIZE, this::active);
                if (!active()) return;
                if (!batch.deliveries().isEmpty()) {
                    proxyDispatch = new InboxDispatch(batch.deliveries(), config, this::active, nextInboundWarning, false);
                }
                proxyInboxDeferredUntil = Math.min(proxyInboxDeferredUntil, batch.deferredUntil());
                proxyInboxAfterRow = batch.hasMore() ? batch.lastRow() : 0;
                proxyInboxNextCheck = batch.hasMore() ? 0 : proxyInboxDeferredUntil;
            } catch (IOException | SQLException failure) {
                try { store.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] Proxy 收件箱暂时无法读取，将重试");
                proxyInboxRetryAt = now + INBOX_RETRY_MS;
            }
        }

        private void persistAndDispatch(List<InboundMessage> batch) throws InterruptedException {
            boolean handedOff = false;
            try {
                if (inboundWorker != this) return;
                long now = System.currentTimeMillis();
                var accepted = batch.stream()
                    .filter(item -> item.registration().accepts(item.transport(), config)
                        && config.acceptsTimestamp(item.timestamp(), now))
                    .toList();
                var inbox = new ArrayList<CrossServerReplayStore.InboxItem>();
                var candidates = new ArrayList<InboundMessage>(accepted.size());
                for (var item : accepted) {
                    if (item.proxyWire() == null) candidates.add(item);
                    else inbox.add(new CrossServerReplayStore.InboxItem(item.delivery().messageId(),
                        CrossServerReplayStore.expiry(item.timestamp(), config.dedupeTtlMs()), item.proxyWire()));
                }
                if (!inbox.isEmpty()) {
                    var staged = store.stageProxy(proxyInboxScope, inbox, now, INBOUND_QUEUE_MESSAGES);
                    for (boolean inserted : staged.inserted()) {
                        if (inserted) {
                            proxyInboxNextCheck = 0;
                            break;
                        }
                    }
                    if (staged.atCapacity()) {
                        warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] Proxy 收件箱达到本机待处理上限");
                    }
                }
                var claims = candidates.stream()
                    .map(item -> new CrossServerReplayStore.Claim(item.delivery().messageId(), item.timestamp()))
                    .toList();
                boolean[] committed = store.claim(claims, now, config.dedupeTtlMs());
                var deliveries = new ArrayList<InboundMessage>(candidates.size());
                for (int index = 0; index < committed.length; index++) {
                    if (committed[index]) deliveries.add(candidates.get(index));
                }
                if (deliveries.isEmpty() || inboundWorker != this) return;
                if (deliveries.stream().anyMatch(item -> item.registration().admission == Admission.BUFFERED)) {
                    dispatchInOrder(deliveries);
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        deliver(deliveries);
                    } finally {
                        release(batch);
                    }
                });
                handedOff = true;
            } catch (IOException | SQLException failure) {
                proxyInboxRevision = Long.MIN_VALUE;
                try { store.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                warnAtMostOncePerInterval(nextInboundWarning, "[CrossServer] 无法写入重放记录，消息已拒绝");
            } catch (IllegalPluginAccessException ignored) {
                // Bukkit is already shutting down.
            } finally {
                if (!handedOff) release(batch);
            }
        }

        private void dispatchInOrder(List<InboundMessage> deliveries) throws InterruptedException {
            var finished = new CompletableFuture<Void>();
            new BukkitRunnable() {
                private int offset;

                @Override public void run() {
                    try {
                        long deadline = System.nanoTime() + INBOUND_CALLBACK_BUDGET_NS;
                        int callbacks = 0;
                        while (inboundWorker == InboundWorker.this && offset < deliveries.size()
                            && callbacks < INBOUND_CALLBACKS_PER_TICK && System.nanoTime() < deadline) {
                            if (!tryDeliver(deliveries.get(offset))) break;
                            offset++;
                            callbacks++;
                        }
                    } finally {
                        if (offset == deliveries.size() || inboundWorker != InboundWorker.this) {
                            cancel();
                            finished.complete(null);
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            // The bounded host queue owns this batch until the module admits its last message.
            try {
                finished.get();
            } catch (ExecutionException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        private void deliver(List<InboundMessage> deliveries) {
            for (InboundMessage item : deliveries) {
                if (inboundWorker != this) return;
                tryDeliver(item);
            }
        }

        private boolean tryDeliver(InboundMessage item) {
            if (!item.registration().accepts(item.transport(), config)) return true;
            try {
                return item.registration().admit.apply(item.delivery()) != null;
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "[CrossServer] 模块处理跨服消息失败", failure);
                return true;
            }
        }

        private void release(List<InboundMessage> batch) {
            for (InboundMessage item : batch) {
                queuedChars.release(item.size());
            }
            queuedMessages.release(batch.size());
        }
    }

    private record InboxDelivery(CrossServerReplayStore.InboxItem item, InboundMessage message) {}
    private record InboxReceipt(CrossServerReplayStore.InboxItem item, CompletableFuture<Boolean> finished) {}
    private record InboxRecovery(boolean completed, long deferredUntil) {}
    private record InboxBatch(List<InboxDelivery> deliveries, long lastRow, boolean hasMore, long deferredUntil) {}

    private InboxBatch readInboxBatch(CrossServerReplayStore store, String scope, long afterRow, Transport transport,
            CrossServerGlobalConfiguration config, int maxBytes, BooleanSupplier active)
            throws IOException, SQLException {
        var page = store.pending(scope, afterRow, INBOUND_BATCH_SIZE, maxBytes);
        var deliveries = new ArrayList<InboxDelivery>(page.items().size());
        var discarded = new ArrayList<CrossServerReplayStore.InboxItem>();
        long now = System.currentTimeMillis();
        long deferredUntil = Long.MAX_VALUE;
        for (var item : page.items()) {
            var envelope = verifyWire(item.wire(), config, true);
            if (envelope == null) {
                // An obsolete or invalid signature must not become trusted through recovery.
                discarded.add(item);
                continue;
            }
            var message = inboundMessage(envelope, item.wire(), transport, config);
            if (message == null && transport == Transport.PROXY) {
                // The local inbox already owns this frame. Continue only through an enabled
                // confirmed receiver after an operator switches the module away from Proxy.
                var viaRedis = inboundMessage(envelope, item.wire(), Transport.REDIS, config);
                if (viaRedis != null && viaRedis.registration().admission == Admission.CONFIRMED) {
                    message = viaRedis;
                }
            }
            if (message != null) deliveries.add(new InboxDelivery(item, message));
            else if (item.expiresAt() < now) discarded.add(item);
            else deferredUntil = Math.min(deferredUntil, item.expiresAt());
        }
        if (active.getAsBoolean()) store.finish(discarded);
        return new InboxBatch(deliveries, page.lastRow(), page.hasMore(), deferredUntil);
    }

    private InboxRecovery recoverInbox(CrossServerReplayStore store, String scope, Transport transport,
            CrossServerGlobalConfiguration config, int maxBytes, BooleanSupplier active, AtomicLong nextWarning)
            throws IOException, SQLException, InterruptedException {
        long deferredUntil = Long.MAX_VALUE;
        long afterRow = 0;
        while (active.getAsBoolean()) {
            var batch = readInboxBatch(store, scope, afterRow, transport, config, maxBytes, active);
            if (!active.getAsBoolean()) break;
            deferredUntil = Math.min(deferredUntil, batch.deferredUntil());
            if (!dispatchInbox(batch.deliveries(), store, config, active, nextWarning, null)) break;
            if (!batch.hasMore()) return new InboxRecovery(true, deferredUntil);
            afterRow = batch.lastRow();
        }
        return new InboxRecovery(false, deferredUntil);
    }

    private boolean dispatchInbox(List<InboxDelivery> deliveries, CrossServerReplayStore store,
            CrossServerGlobalConfiguration config, BooleanSupplier active, AtomicLong nextWarning, Runnable heartbeat)
            throws IOException, SQLException, InterruptedException {
        if (deliveries.isEmpty()) return true;
        if (!active.getAsBoolean()) return false;
        try (var dispatch = new InboxDispatch(deliveries, config, active, nextWarning, true)) {
            long renewAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (active.getAsBoolean()) {
                dispatch.saveCompleted(store);
                if (dispatch.settled()) return dispatch.confirmed();
                if (heartbeat != null && System.nanoTime() >= renewAt) {
                    try {
                        heartbeat.run();
                    } catch (RuntimeException failure) {
                        warnAtMostOncePerInterval(nextWarning, "[CrossServer] Redis Stream 游标续租暂时失败");
                    }
                    renewAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                }
                Thread.sleep(25);
            }
            return false;
        }
    }

    /** A bounded dispatch batch. Bukkit admits callbacks; the owning worker persists their receipts. */
    private final class InboxDispatch implements AutoCloseable {
        private final int expected;
        private final org.bukkit.scheduler.BukkitTask task;
        private final boolean stopOnFailure;
        private final AtomicBoolean stopSubmitting = new AtomicBoolean();
        private final ConcurrentLinkedQueue<InboxReceipt> receipts = new ConcurrentLinkedQueue<>();
        private final List<CrossServerReplayStore.InboxItem> completed = new ArrayList<>();
        private volatile boolean submitted;
        private int committed;

        private InboxDispatch(List<InboxDelivery> deliveries, CrossServerGlobalConfiguration config,
                BooleanSupplier active, AtomicLong nextWarning, boolean stopOnFailure) {
            expected = deliveries.size();
            this.stopOnFailure = stopOnFailure;
            task = new BukkitRunnable() {
                private int offset;

                @Override
                public void run() {
                    long deadline = System.nanoTime() + INBOUND_CALLBACK_BUDGET_NS;
                    int callbacks = 0;
                    try {
                        while (active.getAsBoolean() && !stopSubmitting.get() && offset < deliveries.size()
                                && callbacks < INBOUND_CALLBACKS_PER_TICK && System.nanoTime() < deadline) {
                            if (!tryDeliver(deliveries.get(offset))) break;
                            offset++;
                            callbacks++;
                        }
                    } catch (Error failure) {
                        finish();
                        throw failure;
                    }
                    if (!active.getAsBoolean() || stopSubmitting.get() || offset == deliveries.size()) finish();
                }

                private boolean tryDeliver(InboxDelivery delivery) {
                    InboundMessage message = delivery.message();
                    if (message.registration().accepts(message.transport(), config)) {
                        try {
                            var finished = message.registration().admit.apply(message.delivery());
                            if (finished == null) return false;
                            receipts.add(new InboxReceipt(delivery.item(), finished.toCompletableFuture()));
                        } catch (RuntimeException failure) {
                            warnAtMostOncePerInterval(nextWarning, "[CrossServer] 模块回调失败，收件箱保留待重试");
                            if (stopOnFailure) {
                                stopSubmitting.set(true);
                                return false;
                            }
                        }
                    }
                    return true;
                }

                private void finish() {
                    cancel();
                    submitted = true;
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void saveCompleted(CrossServerReplayStore store) throws IOException, SQLException {
            // A failed row must not discard a later callback's successful receipt.
            var waiting = receipts.iterator();
            while (waiting.hasNext()) {
                InboxReceipt receipt = waiting.next();
                if (!receipt.finished().isDone()) continue;
                waiting.remove();
                if (!receipt.finished().isCompletedExceptionally()
                        && Boolean.TRUE.equals(receipt.finished().getNow(false))) {
                    completed.add(receipt.item());
                } else if (stopOnFailure) {
                    stopSubmitting.set(true);
                }
            }
            if (!completed.isEmpty()) {
                store.finish(completed);
                committed += completed.size();
                completed.clear();
            }
        }

        private boolean settled() {
            return submitted && receipts.isEmpty() && completed.isEmpty();
        }

        private boolean confirmed() {
            return committed == expected;
        }

        @Override
        public void close() {
            task.cancel();
        }
    }

    private final class RedisStreamReceiver {
        private final JedisPool pool;
        private final CrossServerRedisStream stream;
        private final InboundWorker worker;
        private final CrossServerGlobalConfiguration config;
        private final CrossServerReplayStore store;
        private final CompletableFuture<Void> modulesReady;
        private final int frameBytes;
        private final int batchBytes;
        private final String scope;
        private final AtomicLong nextWarning = new AtomicLong();
        private final AtomicLong nextGapWarning = new AtomicLong();

        private RedisStreamReceiver(JedisPool pool, CrossServerRedisStream stream, InboundWorker worker, int frameBytes,
                                    CompletableFuture<Void> modulesReady) {
            this.pool = pool;
            this.stream = stream;
            this.worker = worker;
            this.config = worker.config;
            this.modulesReady = modulesReady;
            this.frameBytes = frameBytes;
            this.batchBytes = config.proxyEnabled() ? Math.max(REDIS_QUEUE_CHARS / 2, frameBytes) : REDIS_QUEUE_CHARS;
            this.scope = config.redisHost() + ":" + config.redisPort() + "/" + config.redisDatabase()
                + "\n" + config.redisChannel() + "\n" + config.nodeId();
            this.store = new CrossServerReplayStore(plugin.getDataFolder().toPath().resolve("data/cross-server-replay.db"));
        }

        private boolean active() {
            return redisSubscriberActive && jedisPool == pool && inboundWorker == worker
                && !Thread.currentThread().isInterrupted();
        }

        private void run() {
            try (store) {
                while (active()) {
                    try {
                        if (modulesReady.isCompletedExceptionally()) return;
                        if (!modulesReady.isDone()) {
                            // Keep the cursor leased while enabled modules are still registering.
                            try (Jedis jedis = pool.getResource()) { stream.checkpoint(jedis, store.cursor(scope)); }
                            try { modulesReady.get(5, TimeUnit.SECONDS); }
                            catch (TimeoutException waiting) { continue; }
                            if (!active()) return;
                        }
                        if (registrations.isEmpty()) {
                            Thread.sleep(250);
                            continue;
                        }
                        long revision = redisRegistrationRevision.get();
                        InboxRecovery recovery = recoverInbox();
                        if (!recovery.completed()) {
                            Thread.sleep(250);
                            continue;
                        }
                        try (Jedis jedis = pool.getResource()) {
                            readStream(jedis, revision, recovery.deferredUntil());
                        }
                    } catch (IOException | SQLException | ExecutionException | RuntimeException failure) {
                        try { store.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                        if (active()) {
                            warnAtMostOncePerInterval(nextWarning, "[CrossServer] Redis Stream 暂停，保留游标和收件箱重试: "
                                + failure.getClass().getSimpleName());
                            Thread.sleep(500);
                        }
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (SQLException failure) {
                warnAtMostOncePerInterval(nextWarning, "[CrossServer] 关闭 Redis Stream 收件箱失败");
            }
        }

        private InboxRecovery recoverInbox() throws IOException, SQLException, InterruptedException {
            reserve(REDIS_QUEUE_CHARS);
            try {
                return CrossServerService.this.recoverInbox(store, scope, Transport.REDIS, config,
                    REDIS_QUEUE_CHARS, this::active, nextWarning);
            } finally {
                release(REDIS_QUEUE_CHARS);
            }
        }

        private void readStream(Jedis jedis, long revision, long deferredUntil)
                throws IOException, SQLException, InterruptedException {
            String cursor = store.cursor(scope);
            stream.checkpoint(jedis, cursor);
            long nextRenew = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (canReadStream(revision, deferredUntil)) {
                reserve(batchBytes);
                try {
                    var entries = readBatch(jedis, cursor);
                    if (entries.isEmpty()) {
                        if (System.nanoTime() >= nextRenew) {
                            stream.checkpoint(jedis, cursor);
                            nextRenew = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        }
                        // In dual-transport mode, do not hold the Proxy budget during a blocking read.
                        if (config.proxyEnabled()) Thread.sleep(10);
                        continue;
                    }
                    if (!canReadStream(revision, deferredUntil)) return;
                    var first = entries.get(0);
                    String preceding = first.getFields().get("prev");
                    if (!CrossServerRedisStream.START.equals(cursor) && preceding != null && !preceding.equals(cursor)) {
                        warnAtMostOncePerInterval(nextGapWarning, "[CrossServer] Redis Stream 留存已越过本机游标，部分历史无法补发");
                    }
                    cursor = entries.get(entries.size() - 1).getID().toString();
                    if (!stageAndDispatch(jedis, entries, cursor)) return;
                    nextRenew = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                } finally {
                    release(batchBytes);
                }
            }
        }

        private boolean canReadStream(long revision, long deferredUntil) {
            return active() && !registrations.isEmpty() && redisRegistrationRevision.get() == revision
                && (deferredUntil == Long.MAX_VALUE || System.currentTimeMillis() <= deferredUntil);
        }

        private List<StreamEntry> readBatch(Jedis jedis, String cursor) {
            var entries = new ArrayList<StreamEntry>(INBOUND_BATCH_SIZE);
            int remaining = batchBytes;
            while (active() && remaining >= frameBytes && entries.size() < INBOUND_BATCH_SIZE) {
                int count = Math.min(remaining / frameBytes, INBOUND_BATCH_SIZE - entries.size());
                var next = stream.read(jedis, cursor, count, entries.isEmpty() && !config.proxyEnabled());
                if (next.isEmpty()) break;
                for (var entry : next) {
                    String wire = entry.getFields().get("wire");
                    if (wire != null) remaining -= CrossServerEnvelopeCodec.utf8Length(wire);
                }
                entries.addAll(next);
                cursor = next.get(next.size() - 1).getID().toString();
                if (next.size() < count) break;
            }
            return entries;
        }

        private boolean stageAndDispatch(Jedis jedis, List<StreamEntry> entries, String cursor)
                throws IOException, SQLException, InterruptedException {
            var candidates = new ArrayList<InboxDelivery>(entries.size());
            for (var entry : entries) {
                String wire = entry.getFields().get("wire");
                var envelope = verifyWire(wire, config, false);
                if (envelope == null) continue;
                var message = inboundMessage(envelope, wire, Transport.REDIS, config);
                if (message == null) continue;
                var item = new CrossServerReplayStore.InboxItem(envelope.messageId(),
                    CrossServerReplayStore.expiry(envelope.timestamp(), config.dedupeTtlMs()), wire);
                candidates.add(new InboxDelivery(item, message));
            }
            boolean[] inserted = store.stage(scope, cursor, candidates.stream().map(InboxDelivery::item).toList(),
                System.currentTimeMillis());
            stream.checkpoint(jedis, cursor);
            var admitted = new ArrayList<InboxDelivery>(candidates.size());
            for (int index = 0; index < inserted.length; index++) {
                if (inserted[index]) admitted.add(candidates.get(index));
            }
            return dispatchInbox(admitted, store, config, this::active, nextWarning,
                () -> stream.checkpoint(jedis, cursor));
        }

        private void reserve(int bytes) throws InterruptedException {
            worker.queuedMessages.acquire(INBOUND_BATCH_SIZE);
            try {
                worker.queuedChars.acquire(bytes);
            } catch (InterruptedException failure) {
                worker.queuedMessages.release(INBOUND_BATCH_SIZE);
                throw failure;
            }
        }

        private void release(int bytes) {
            worker.queuedChars.release(bytes);
            worker.queuedMessages.release(INBOUND_BATCH_SIZE);
        }
    }

    private final class CrossServerChannelHandle implements CrossServerChannel {

        private final ModuleRegistration registration;

        private CrossServerChannelHandle(ModuleRegistration registration) {
            this.registration = registration;
        }

        @Override
        public String moduleId() {
            return registration.moduleId;
        }

        @Override
        public boolean isActive() {
            CrossServerGlobalConfiguration activeConfig = configuration;
            if (activeConfig.missingSignatureSecret()) {
                return false;
            }
            return (redisPublisher != null && registration.accepts(Transport.REDIS, activeConfig))
                || (proxyActive && registration.accepts(Transport.PROXY, activeConfig));
        }

        @Override
        public void publish(String payload) {
            submit(payload, false);
        }

        @Override
        public CompletionStage<Boolean> publishDurably(String payload) {
            return submit(payload, true);
        }

        @Override
        public CompletionStage<Boolean> publishWithReceipt(String payload) {
            return submit(payload, false);
        }

        @Override
        public boolean requiresSharedStateCatchUp() {
            var current = configuration;
            return proxyActive && registration.accepts(Transport.PROXY, current)
                && (redisPublisher == null || !registration.accepts(Transport.REDIS, current));
        }

        private CompletionStage<Boolean> submit(String payload, boolean durableOnly) {
            CrossServerGlobalConfiguration activeConfig = configuration;
            if (registration.closed || !registration.config.enabled() || payload == null
                || activeConfig.missingSignatureSecret()) {
                return CompletableFuture.completedFuture(false);
            }
            CrossServerEnvelope envelope = new CrossServerEnvelope(
                CrossServerEnvelope.PROTOCOL_VERSION,
                registration.moduleId,
                activeConfig.nodeId(),
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                payload,
                ""
            );
            return publishEnvelope(envelope, registration, activeConfig, durableOnly);
        }

        @Override
        public void close() {
            registration.closed = true;
            if (registrations.remove(registration.moduleId, registration)) redisRegistrationRevision.incrementAndGet();
        }
    }

    private static final class ModuleRegistration {
        private final String moduleId;
        private final CrossServerChannelConfig config;
        private final Function<CrossServerDelivery, CompletionStage<Boolean>> admit;
        private final Admission admission;
        private volatile boolean closed;

        private ModuleRegistration(String moduleId, CrossServerChannelConfig config,
                                   Function<CrossServerDelivery, CompletionStage<Boolean>> admit, Admission admission) {
            this.moduleId = moduleId;
            this.config = config;
            this.admit = admit;
            this.admission = admission;
        }

        private boolean accepts(Transport transport, CrossServerGlobalConfiguration configuration) {
            if (closed) {
                return false;
            }
            return switch (transport) {
                case REDIS -> configuration.redisEnabled() && config.redisEnabled(true);
                case PROXY -> configuration.proxyEnabled() && config.proxyEnabled(true);
            };
        }
    }
}
