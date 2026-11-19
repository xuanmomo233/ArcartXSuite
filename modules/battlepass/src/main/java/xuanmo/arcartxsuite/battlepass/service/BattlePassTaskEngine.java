package xuanmo.arcartxsuite.battlepass.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.RegionProtectionQueryable;
import xuanmo.arcartxsuite.battlepass.condition.EventPayloadCondition;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.source.BukkitEventSourceListener;

/**
 * 战令任务引擎，负责监听事件并驱动任务进度更新。
 * <p>
 * EventBus 订阅跨发布模块的加载和重载持续存在。
 * 配置的 Bukkit 降级事件只在 EventBus 尚无该主题的发布者时计数。
 */
public final class BattlePassTaskEngine {

    private final Plugin plugin;
    private final BattlePassService service;
    private final BattlePassModuleConfiguration configuration;
    private final Logger logger;
    private final List<String> subscriptionIds = new ArrayList<>();
    private EventBusCapability eventBus;
    private BukkitEventSourceListener bukkitListener;
    private xuanmo.arcartxsuite.battlepass.source.MythicMobEventListener mythicListener;
    private volatile boolean active;
    private final TaskTriggerLimiter triggerLimiter = new TaskTriggerLimiter();

    private static final Map<String, String> PAYLOAD_EXPRESSION_KEYS = Map.of(
        "victim.type.name", "entityType",
        "victim.name", "entityName",
        "entity.type.name", "entityType",
        "entity.name", "entityName",
        "target.type.name", "targetType",
        "target.name", "targetName",
        "item.type.name", "material",
        "item.amount", "amount"
    );

    /**
     * 构造任务引擎。
     *
     * @param plugin        插件实例
     * @param service       BattlePassService 实例
     * @param configuration 模块配置
     * @param logger        模块专用 Logger（由宿主注入，自动携带统一前缀与模块标签）
     */
    public BattlePassTaskEngine(Plugin plugin, BattlePassService service,
                                 BattlePassModuleConfiguration configuration, Logger logger) {
        this.plugin = plugin;
        this.service = service;
        this.configuration = configuration;
        this.logger = logger;
    }

    /**
     * 启动任务引擎，为每个任务模板选择事件源并订阅。
     *
     * @param eventBusProvider 事件总线能力提供者
     */
    public void start(Supplier<EventBusCapability> eventBusProvider) {
        if (active) return;
        eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        active = true;

        logger.fine("TaskEngine.start: eventBus=" + (eventBus != null ? "available" : "null"));

        Map<String, List<BiConsumer<Player, Map<String, String>>>> callbacksByEvent =
            new HashMap<>();
        Map<String, List<BattlePassTask>> gameplayTopics = new LinkedHashMap<>();
        List<BattlePassTask> auctionTasks = new ArrayList<>();

        int eventBusCount = 0;
        int bukkitCount = 0;
        int unavailableCount = 0;
        // MythicMobs 事件回调（独立收集，因 MythicMobs 为可选依赖，需通过独立监听器注册）
        List<BiConsumer<Player, Map<String, String>>> mythicCallbacks = new ArrayList<>();

        for (BattlePassTask task : configuration.tasks().all()) {
            String topic = task.eventTopic();
            boolean hasTopic = topic != null && !topic.isBlank();
            boolean subscribed = hasTopic && eventBus != null;
            if (subscribed) {
                if (EventBusCapability.MARKET_AUCTION_PURCHASED.equals(topic)) {
                    auctionTasks.add(task);
                } else if (hasIndependentFact(task)) {
                    subscriptionIds.add(subscribe(task));
                    eventBusCount++;
                } else {
                    gameplayTopics.computeIfAbsent(topic, ignored -> new ArrayList<>()).add(task);
                }
            }
            int taskBukkitCount = 0;
            if (task.bukkitEvent() != null && (!hasTopic || !topic.startsWith("axs.auth."))) {
                // 使用 Bukkit 事件（支持多个事件类）
                for (String bukkitClass : task.bukkitEvent().eventClasses()) {
                    if (!BukkitEventSourceListener.isSupportedBukkitEvent(bukkitClass)) {
                        logger.warning("不支持的 Bukkit 事件，跳过: task="
                            + task.taskId() + " event=" + bukkitClass);
                        continue;
                    }
                    String normalizedClass = BukkitEventSourceListener.normalizeBukkitEventClass(bukkitClass);
                    BiConsumer<Player, Map<String, String>> callback = (player, payload) -> {
                        try {
                            if (subscribed && hasPublisher(topic)) return;
                            handleEvent(player, task, mapPayload(payload, task.bukkitEvent().payloadFields()));
                        } catch (Exception e) {
                            logger.log(Level.WARNING,
                                "任务 " + task.taskId()
                                    + " 处理 Bukkit 事件 " + normalizedClass + " 失败", e);
                        }
                    };
                    // MythicMobs 事件单独收集，由独立监听器处理
                    if (normalizedClass.equals(xuanmo.arcartxsuite.battlepass.source.MythicMobEventListener.MYTHIC_MOB_DEATH)) {
                        mythicCallbacks.add(callback);
                    } else {
                        callbacksByEvent.computeIfAbsent(normalizedClass, ignored -> new ArrayList<>())
                            .add(callback);
                    }
                    taskBukkitCount++;
                    logger.fine("TaskEngine 注册 Bukkit 事件: event="
                        + bukkitClass + " task=" + task.taskId()
                        + (hasTopic ? " (EventBus 降级)" : ""));
                }
            }
            bukkitCount += taskBukkitCount;
            if (!subscribed && taskBukkitCount == 0) {
                logger.warning("TaskEngine 无可用事件源: task=" + task.taskId() + " topic=" + topic);
                unavailableCount++;
            }
        }

        for (var route : gameplayTopics.entrySet()) {
            List<GameplayTask> tasks = route.getValue().stream()
                .map(task -> new GameplayTask(task,
                    task.conditions().stream().anyMatch(condition -> !(condition instanceof EventPayloadCondition))))
                .toList();
            subscriptionIds.add(eventBus.subscribe(route.getKey(), event -> handleGameplayEvent(tasks, event)));
        }
        if (eventBus != null) {
            List<BattlePassTask> tasks = List.copyOf(auctionTasks);
            subscriptionIds.add(eventBus.subscribeConfirmed(EventBusCapability.BATTLEPASS_AUCTION_CONSUMER,
                EventBusCapability.MARKET_AUCTION_PURCHASED, event -> {
                    if (!active) return CompletableFuture.failedFuture(new IllegalStateException("BattlePass is not active"));
                    var writes = tasks.stream().map(task -> handleBusinessEvent(task, event)).toArray(CompletableFuture<?>[]::new);
                    return CompletableFuture.allOf(writes);
                }));
            eventBusCount++;
        }
        eventBusCount += gameplayTopics.size();

        logger.info("TaskEngine.start 完成: eventBus订阅=" + eventBusCount
            + " bukkit事件=" + bukkitCount + " 无可用事件源=" + unavailableCount
            + " | 总任务数=" + configuration.tasks().all().size());

        // 注册原版 Bukkit 事件监听器
        if (!callbacksByEvent.isEmpty()) {
            registerBukkitListener(callbacksByEvent);
        }
        // 注册 MythicMobs 事件监听器（仅在 MythicMobs 可用时）
        if (!mythicCallbacks.isEmpty()) {
            registerMythicListener(mythicCallbacks);
        }
    }

    private static boolean hasIndependentFact(BattlePassTask task) {
        return switch (task.eventTopic()) {
            case BattlePassTask.PRESENCE_TOPIC, "axs.auth.profile_first_admitted",
                "axs.market.npc_exchange_completed",
                "axs.onlinerewards.signin_completed" -> true;
            case RegionProtectionQueryable.REGION_CHANGE_TOPIC -> task.taskId().equals("weekly-region-explore");
            default -> false;
        };
    }

    private String subscribe(BattlePassTask task) {
        String topic = task.eventTopic();
        boolean presence = BattlePassTask.PRESENCE_TOPIC.equals(topic);
        if (presence || "axs.auth.profile_first_admitted".equals(topic)) {
            String consumerKey = configuration.season().seasonId() + "/" + task.taskId();
            String consumer = "battlepass/" + UUID.nameUUIDFromBytes(consumerKey.getBytes(StandardCharsets.UTF_8));
            return eventBus.subscribeConfirmed(consumer, topic, event -> {
                Player player = event.player();
                if (!active || player == null || !player.isOnline() || !AuthAccess.admitted(player.getUniqueId())) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Player unavailable"));
                }
                Map<String, String> payload = event.payload();
                if (!task.matchesConditions(player, payload)) return CompletableFuture.completedFuture(null);
                String eventId = payload.get("event_id");
                String date = payload.get("business_date");
                if (eventId == null || eventId.isBlank() || (presence && date == null)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Missing authentication fact fields"));
                }
                LocalDate businessDate = date == null ? LocalDate.now() : LocalDate.parse(date);
                return service.acceptAuthFact(player, task.taskId(), task.calculateIncrement(player, payload), eventId, businessDate);
            });
        }
        if (topic.equals("axs.market.npc_exchange_completed") || topic.equals("axs.onlinerewards.signin_completed")) {
            return eventBus.subscribe(topic, event -> handleBusinessEvent(task, event));
        }
        return eventBus.subscribe(topic, event -> handleRegionVisit(task, event));
    }

    private record GameplayTask(BattlePassTask task, boolean readsPlayerState) {}

    private void handleGameplayEvent(List<GameplayTask> tasks, EventBusCapability.BusEvent event) {
        Player player = event.player();
        if (!active || player == null || !player.isOnline()) return;
        if (tasks.size() == 1) {
            handleEvent(player, tasks.get(0).task(), event.payload());
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!AuthAccess.admitted(uuid)) return;
        Map<String, String> payload = event.payload();
        Map<String, Integer> increments = null;
        for (GameplayTask route : tasks) {
            if (!active) return;
            BattlePassTask task = route.task();
            try {
                if (route.readsPlayerState()) {
                    // Stateful conditions may read progress already advanced by an earlier task on this event.
                    var earlier = increments;
                    increments = null;
                    applyGameplayProgress(player, uuid, earlier);
                    handleEvent(player, task, payload);
                    if (!AuthAccess.admitted(uuid)) return;
                    continue;
                }
                if (!task.matchesConditions(player, payload)) continue;
                int amount = task.calculateIncrement(player, payload);
                if (amount <= 0 || !triggerLimiter.tryAcquire(uuid, task.taskId(), task.limits())) continue;
                if (increments == null) increments = new LinkedHashMap<>();
                increments.merge(task.taskId(), amount, (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
            } catch (RuntimeException | LinkageError error) {
                logger.log(Level.WARNING, "任务 " + task.taskId() + " 处理事件失败", error);
            }
        }
        applyGameplayProgress(player, uuid, increments);
    }

    private void applyGameplayProgress(Player player, UUID uuid, Map<String, Integer> increments) {
        if (active && increments != null && AuthAccess.admitted(uuid)) service.updateTaskProgress(player, increments);
    }

    private void handleRegionVisit(BattlePassTask task, EventBusCapability.BusEvent event) {
        Player player = event.player();
        if (!active || player == null || !player.isOnline() || !AuthAccess.admitted(player.getUniqueId())) return;
        var payload = event.payload();
        if (!"enter".equals(payload.get("type")) || !task.matchesConditions(player, payload)) return;
        String region = payload.get("region_uuid");
        UUID regionId;
        try {
            if (region == null) return;
            regionId = UUID.fromString(region);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        int increment = task.calculateIncrement(player, payload);
        if (increment <= 0 || !triggerLimiter.tryAcquire(player.getUniqueId(), task.taskId(), task.limits())) return;
        service.acceptTaskFactForAssignment(player.getUniqueId(), task.taskId(), increment, regionId.toString(), LocalDate.now())
            .whenComplete((ignored, error) -> {
                if (error != null && active) logger.log(Level.WARNING, "区域探索任务计数失败", error);
            });
    }

    private CompletableFuture<Void> handleBusinessEvent(BattlePassTask task, EventBusCapability.BusEvent event) {
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("BattlePass is not active"));
        var payload = event.payload();
        Player player = event.player();
        String topic = event.topic();
        boolean auction = topic.equals("axs.market.auction_purchased");
        String key = payload.get(auction ? "listing_id" : "event_id");
        if (key == null || key.isBlank()) return CompletableFuture.completedFuture(null);
        String playerId = payload.get(auction ? "buyer_uuid" : "player_uuid");
        UUID uuid = playerId == null ? (player == null ? null : player.getUniqueId()) : UUID.fromString(playerId);
        if (uuid == null || (player != null && !uuid.equals(player.getUniqueId()))) return CompletableFuture.completedFuture(null);
        if (topic.equals("axs.onlinerewards.signin_completed")
                && (player == null || !AuthAccess.admitted(uuid))) return CompletableFuture.completedFuture(null);
        if (!task.matchesConditions(player, payload)) return CompletableFuture.completedFuture(null);
        String date = payload.get("business_date");
        LocalDate businessDate = date == null ? LocalDate.now() : LocalDate.parse(date);
        return service.acceptTaskFact(uuid, task.taskId(), task.calculateIncrement(player, payload), key, businessDate)
            .whenComplete((ignored, error) -> {
                if (error != null && active) logger.log(Level.WARNING, "联动任务计数失败: " + task.taskId(), error);
            }).toCompletableFuture();
    }

    private boolean hasPublisher(String topic) {
        if (eventBus == null) return false;
        try {
            return eventBus.hasPublisher(topic);
        } catch (Exception e) {
            logger.warning("EventBus.hasPublisher 查询异常 topic=" + topic + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 注册原版 Bukkit 事件监听器，将多个回调合并为一个分发器。
     */
    private void registerBukkitListener(
        Map<String, List<BiConsumer<Player, Map<String, String>>>> callbacksByEvent
    ) {
        Map<String, BiConsumer<Player, Map<String, String>>> dispatchers = new HashMap<>();
        for (Map.Entry<String, List<BiConsumer<Player, Map<String, String>>>> entry
                : callbacksByEvent.entrySet()) {
            BiConsumer<Player, Map<String, String>> dispatcher = combine(entry.getValue());
            if (dispatcher != null) dispatchers.put(entry.getKey(), dispatcher);
        }
        bukkitListener = BukkitEventSourceListener.register(
            plugin,
            logger,
            dispatchers
        );
    }

    /**
     * 注册 MythicMobs 事件监听器（仅在 MythicMobs 可用时）。
     * <p>
     * MythicMobs 为可选依赖，运行时可能不存在。{@link xuanmo.arcartxsuite.battlepass.source.MythicMobEventListener#registerIfAvailable}
     * 会在 MythicMobs 不可用时返回 null，此时 MythicMobs 相关任务不会被触发。
     */
    private void registerMythicListener(List<BiConsumer<Player, Map<String, String>>> callbacks) {
        BiConsumer<Player, Map<String, String>> dispatcher = combine(callbacks);
        if (dispatcher == null) return;
        mythicListener = xuanmo.arcartxsuite.battlepass.source.MythicMobEventListener
            .registerIfAvailable(plugin, dispatcher);
        if (mythicListener != null) {
            logger.info("MythicMobs 事件监听器已注册");
        } else {
            logger.warning("有任务配置了 MythicMobs 事件，但 MythicMobs 插件未安装，相关任务不会触发");
        }
    }

    /**
     * 将多个回调合并为一个分发器，任一回调异常不影响其他回调。
     *
     * @param callbacks 回调列表
     * @return 合并后的回调，为空时返回 null
     */
    private BiConsumer<Player, Map<String, String>> combine(
        List<BiConsumer<Player, Map<String, String>>> callbacks
    ) {
        if (callbacks == null || callbacks.isEmpty()) return null;
        return (player, payload) -> {
            for (BiConsumer<Player, Map<String, String>> callback : callbacks) {
                callback.accept(player, payload);
            }
        };
    }

    /**
     * 关闭任务引擎，注销所有事件订阅和 Bukkit 监听器。
     */
    public void shutdown() {
        active = false;
        if (eventBus != null) {
            for (String subId : subscriptionIds) {
                try {
                    eventBus.unsubscribe(subId);
                } catch (RuntimeException ignored) {}
            }
            eventBus = null;
        }
        subscriptionIds.clear();
        if (bukkitListener != null) {
            bukkitListener.unregister();
            bukkitListener = null;
        }
        if (mythicListener != null) {
            mythicListener.unregister();
            mythicListener = null;
        }
        triggerLimiter.clear();
    }

    /**
     * 清理玩家的任务触发限流状态。
     *
     * @param uuid 玩家 UUID
     */
    public void clearPlayerLimits(UUID uuid) {
        triggerLimiter.clear(uuid);
    }

    private Map<String, String> mapPayload(Map<String, String> payload, Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) return payload;
        Map<String, String> mapped = null;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = payload.get(entry.getValue());
            if (value == null) value = resolvePayloadExpression(payload, entry.getValue());
            if (value != null && !value.equals(payload.get(entry.getKey()))) {
                if (mapped == null) mapped = new LinkedHashMap<>(payload);
                mapped.put(entry.getKey(), value);
            }
        }
        return mapped == null ? payload : mapped;
    }

    private String resolvePayloadExpression(Map<String, String> payload, String expression) {
        if (expression == null) return null;
        String payloadKey = PAYLOAD_EXPRESSION_KEYS.get(expression);
        String value = payload.get(payloadKey != null ? payloadKey : expression);
        if (value == null) {
            logger.fine("无法解析 payload 表达式: " + expression);
        }
        return value;
    }

    private void handleEvent(Player player, BattlePassTask task, Map<String, String> payload) {
        if (!active) return;
        if (!AuthAccess.admitted(player.getUniqueId())) return;
        if (!task.matchesConditions(player, payload)) return;
        int increment = task.calculateIncrement(player, payload);
        if (increment > 0
            && triggerLimiter.tryAcquire(
                player.getUniqueId(), task.taskId(), task.limits())) {
            service.updateTaskProgress(player, task.taskId(), increment);
        }
    }
}
