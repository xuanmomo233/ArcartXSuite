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
    private final List<String> subscriptionIds = new ArrayList<>();
    private EventBusCapability eventBus;
    private BukkitEventSourceListener bukkitListener;
    private volatile boolean active;
    private static final Logger LOGGER = Logger.getLogger("ArcartXSuite");
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
     */
    public BattlePassTaskEngine(Plugin plugin, BattlePassService service,
                                 BattlePassModuleConfiguration configuration) {
        this.plugin = plugin;
        this.service = service;
        this.configuration = configuration;
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

        LOGGER.fine("[battlepass] TaskEngine.start: eventBus=" + (eventBus != null ? "available" : "null"));

        Map<String, List<BiConsumer<Player, Map<String, String>>>> callbacksByEvent =
            new HashMap<>();

        int eventBusCount = 0;
        int bukkitCount = 0;
        int skippedCount = 0;

        for (BattlePassTask task : configuration.tasks().all()) {
            String topic = task.eventTopic();
            if (topic == null || topic.isEmpty()) { skippedCount++; continue; }

            boolean useEventBus = shouldUseEventBus(topic);

            if (useEventBus) {
                // 使用 EventBus
                String subId = eventBus.subscribe(topic, event -> {
                    if (event.player() == null || !event.player().isOnline()) return;
                    handleEvent(event.player(), task, event.payload());
                });
                if (subId != null) {
                    subscriptionIds.add(subId);
                    eventBusCount++;
                    LOGGER.fine("[battlepass] TaskEngine 订阅 EventBus: topic=" + topic + " task=" + task.taskId());
                }
            } else if (task.bukkitEvent() != null) {
                // 降级到原版 Bukkit 事件（支持多个事件类）
                for (String bukkitClass : task.bukkitEvent().eventClasses()) {
                    if (!BukkitEventSourceListener.isSupportedBukkitEvent(bukkitClass)) {
                        LOGGER.warning("[battlepass] 不支持的 Bukkit 事件，跳过: task="
                            + task.taskId() + " event=" + bukkitClass);
                        continue;
                    }
                    String normalizedClass = BukkitEventSourceListener.normalizeBukkitEventClass(bukkitClass);
                    BiConsumer<Player, Map<String, String>> callback = (player, payload) -> {
                        try {
                            handleEvent(player, task, mapPayload(payload, task.bukkitEvent().payloadFields()));
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING,
                                "[battlepass] 任务 " + task.taskId()
                                    + " 处理 Bukkit 事件 " + normalizedClass + " 失败", e);
                        }
                    };
                    callbacksByEvent.computeIfAbsent(normalizedClass, ignored -> new ArrayList<>())
                        .add(callback);
                    bukkitCount++;
                    LOGGER.info("[battlepass] TaskEngine 使用 Bukkit 降级: event="
                        + bukkitClass + " task=" + task.taskId() + " (topic=" + topic + ")");
                }
            } else {
                LOGGER.warning("[battlepass] TaskEngine 跳过任务（无可用事件源）: task=" + task.taskId() + " topic=" + topic);
            }
        }

        LOGGER.info("[battlepass] TaskEngine.start 完成: eventBus订阅=" + eventBusCount
            + " bukkit降级=" + bukkitCount + " 跳过=" + skippedCount
            + " | 总任务数=" + configuration.tasks().all().size());

        // 如果有需要降级的 Bukkit 事件，注册监听器
        if (!callbacksByEvent.isEmpty()) {
            registerBukkitListener(callbacksByEvent);
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
            LOGGER.warning("[battlepass] EventBus.hasPublisher 查询异常 topic=" + topic + ": " + e.getMessage());
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
            LOGGER,
            dispatchers
        );
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
            LOGGER.fine("[battlepass] 无法解析 payload 表达式: " + expression);
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
