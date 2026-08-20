package xuanmo.arcartxsuite.battlepass.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.source.BukkitEventSourceListener;

/**
 * 战令任务引擎，负责监听事件并驱动任务进度更新。
 * <p>
 * 支持双事件源：ArcartX EventBus（优先）和原版 Bukkit 事件（降级）。
 * 启动时为每个任务模板检测发布者是否已注册：
 * <ul>
 *   <li>若 EventBus 已有该 topic 的发布者 → 订阅 EventBus</li>
 *   <li>否则，若任务配置了 bukkit-event → 注册原版 Bukkit 监听器</li>
 * </ul>
 * 确保不依赖其他 ArcartX 模块也能正常工作，且不会重复计数。
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

        int eventBusCount = 0;
        int bukkitCount = 0;
        int skippedCount = 0;
        // MythicMobs 事件回调（独立收集，因 MythicMobs 为可选依赖，需通过独立监听器注册）
        List<BiConsumer<Player, Map<String, String>>> mythicCallbacks = new ArrayList<>();

        for (BattlePassTask task : configuration.tasks().all()) {
            String topic = task.eventTopic();
            boolean hasTopic = topic != null && !topic.isEmpty();

            // 判断事件源优先级：
            // 1. 有 event-topic 且 EventBus 已有发布者 → 使用 EventBus
            // 2. 有 bukkit-event → 使用 Bukkit 事件（无论 event-topic 是否存在）
            // 3. 有 event-topic 但无发布者且无 bukkit-event → 跳过
            // 4. 两者都没有 → 跳过
            boolean useEventBus = hasTopic && shouldUseEventBus(topic);

            if (useEventBus) {
                // 使用 EventBus
                String subId = eventBus.subscribe(topic, event -> {
                    if (event.player() == null || !event.player().isOnline()) return;
                    handleEvent(event.player(), task, event.payload());
                });
                if (subId != null) {
                    subscriptionIds.add(subId);
                    eventBusCount++;
                    logger.fine("TaskEngine 订阅 EventBus: topic=" + topic + " task=" + task.taskId());
                }
            } else if (task.bukkitEvent() != null) {
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
                    bukkitCount++;
                    logger.info("TaskEngine 使用 Bukkit 事件: event="
                        + bukkitClass + " task=" + task.taskId()
                        + (hasTopic ? " (topic=" + topic + " 无发布者，降级)" : ""));
                }
            } else if (hasTopic) {
                logger.warning("TaskEngine 跳过任务（EventBus 无发布者且无 bukkit-event 降级）: task="
                    + task.taskId() + " topic=" + topic);
                skippedCount++;
            } else {
                logger.warning("TaskEngine 跳过任务（无可用事件源）: task=" + task.taskId());
                skippedCount++;
            }
        }

        logger.info("TaskEngine.start 完成: eventBus订阅=" + eventBusCount
            + " bukkit事件=" + bukkitCount + " 跳过=" + skippedCount
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

    /**
     * 判断某个 EventBus topic 是否已有发布者注册。
     * <p>
     * 通过 {@link EventBusCapability#hasPublisher(String)} API 查询，
     * 无需维护硬编码的 topic → moduleId 映射。
     *
     * @param topic EventBus 主题
     * @return true 表示已有发布者注册，应使用 EventBus；false 表示需要降级
     */
    private boolean shouldUseEventBus(String topic) {
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
        Map<String, String> mapped = new java.util.LinkedHashMap<>(payload);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = payload.get(entry.getValue());
            if (value == null) value = resolvePayloadExpression(payload, entry.getValue());
            if (value != null) mapped.put(entry.getKey(), value);
        }
        return mapped;
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
        if (!task.matchesConditions(player, payload)) return;
        int increment = task.calculateIncrement(player, payload);
        if (increment > 0
            && triggerLimiter.tryAcquire(
                player.getUniqueId(), task.taskId(), task.limits())) {
            service.updateTaskProgress(player, task.taskId(), increment);
        }
    }
}
