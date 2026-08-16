package xuanmo.arcartxsuite.battlepass.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * 启动时为每个任务模板检测发布模块是否存在：
 * <ul>
 *   <li>若 EventBus topic 对应的发布模块已加载 → 订阅 EventBus</li>
 *   <li>否则，若任务配置了 bukkit-event → 注册原版 Bukkit 监听器</li>
 * </ul>
 * 确保不依赖其他 ArcartX 模块也能正常工作，且不会重复计数。
 */
public final class BattlePassTaskEngine {

    private final Plugin plugin;
    private final BattlePassService service;
    private final BattlePassModuleConfiguration configuration;
    private final Function<String, Boolean> moduleExistsChecker;
    private final List<String> subscriptionIds = new ArrayList<>();
    private EventBusCapability eventBus;
    private BukkitEventSourceListener bukkitListener;
    private volatile boolean active;

    /**
     * EventBus topic → 发布模块 ID 的映射。
     * 用于检测某个 topic 的发布模块是否已加载。
     */
    private static final Map<String, String> TOPIC_TO_MODULE = new HashMap<>();

    static {
        TOPIC_TO_MODULE.put("axs.combateffect.kill_entity", "combateffect");
        TOPIC_TO_MODULE.put("axs.combateffect.damage_dealt", "combateffect");
        TOPIC_TO_MODULE.put("axs.combateffect.player_death", "combateffect");
        TOPIC_TO_MODULE.put("axs.pickup.item_pickup", "pickup");
        TOPIC_TO_MODULE.put("axs.loginview.login_success", "loginview");
        TOPIC_TO_MODULE.put("axs.fishing.success", "fishing");
        TOPIC_TO_MODULE.put("axs.questgps.quest_completed", "questgps");
        // 以下 topic 由宿主核心或已加载模块发布，不需要降级
        TOPIC_TO_MODULE.put("axs.currency.spent", "__core__");
        TOPIC_TO_MODULE.put("market.listing_created", "market");
    }

    /**
     * 构造任务引擎。
     *
     * @param plugin               插件实例
     * @param service              BattlePassService 实例
     * @param configuration        模块配置
     * @param moduleExistsChecker  模块存在性检测函数（输入模块 ID，返回是否已加载），可为 null
     */
    public BattlePassTaskEngine(Plugin plugin, BattlePassService service,
                                 BattlePassModuleConfiguration configuration,
                                 Function<String, Boolean> moduleExistsChecker) {
        this.plugin = plugin;
        this.service = service;
        this.configuration = configuration;
        this.moduleExistsChecker = moduleExistsChecker;
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

        java.util.logging.Logger bpLogger = java.util.logging.Logger.getLogger("ArcartXSuite");
        bpLogger.fine("[battlepass] TaskEngine.start: eventBus=" + (eventBus != null ? "available" : "null")
            + " | moduleExistsChecker=" + (moduleExistsChecker != null ? "set" : "null"));

        // 按事件类型分组的降级回调
        Set<String> neededBukkitEvents = new HashSet<>();
        // 各事件类型的回调列表
        List<BiConsumer<Player, Map<String, String>>> killCallbacks = new ArrayList<>();
        List<BiConsumer<Player, Map<String, String>>> damageCallbacks = new ArrayList<>();
        List<BiConsumer<Player, Map<String, String>>> joinCallbacks = new ArrayList<>();
        List<BiConsumer<Player, Map<String, String>>> pickupCallbacks = new ArrayList<>();
        List<BiConsumer<Player, Map<String, String>>> fishCallbacks = new ArrayList<>();

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
                    bpLogger.fine("[battlepass] TaskEngine 订阅 EventBus: topic=" + topic + " task=" + task.taskId());
                }
            } else if (task.bukkitEvent() != null) {
                // 降级到原版 Bukkit 事件
                String bukkitClass = task.bukkitEvent().eventClass();
                neededBukkitEvents.add(bukkitClass);
                BiConsumer<Player, Map<String, String>> callback = (player, payload) ->
                    handleEvent(player, task, payload);
                bukkitCount++;
                bpLogger.info("[battlepass] TaskEngine 降级 Bukkit: event=" + bukkitClass + " task=" + task.taskId() + " (topic=" + topic + " module未加载)");
                // 按事件类型分组
                if (bukkitClass.equals("org.bukkit.event.entity.EntityDeathEvent")) {
                    killCallbacks.add(callback);
                } else if (bukkitClass.equals("org.bukkit.event.entity.EntityDamageByEntityEvent")) {
                    damageCallbacks.add(callback);
                } else if (bukkitClass.equals("org.bukkit.event.player.PlayerJoinEvent")) {
                    joinCallbacks.add(callback);
                } else if (bukkitClass.equals("org.bukkit.event.player.PlayerPickupItemEvent")) {
                    pickupCallbacks.add(callback);
                } else if (bukkitClass.equals("org.bukkit.event.player.PlayerFishEvent")) {
                    fishCallbacks.add(callback);
                }
            } else {
                bpLogger.warning("[battlepass] TaskEngine 跳过任务（无可用事件源）: task=" + task.taskId() + " topic=" + topic);
            }
        }

        bpLogger.info("[battlepass] TaskEngine.start 完成: eventBus订阅=" + eventBusCount
            + " bukkit降级=" + bukkitCount + " 跳过=" + skippedCount
            + " | 总任务数=" + configuration.tasks().all().size());

        // 如果有需要降级的 Bukkit 事件，注册监听器
        if (!neededBukkitEvents.isEmpty()) {
            registerBukkitListener(neededBukkitEvents, killCallbacks, damageCallbacks,
                joinCallbacks, pickupCallbacks, fishCallbacks);
        }
    }

    /**
     * 判断某个 EventBus topic 的发布模块是否已加载。
     *
     * @param topic EventBus 主题
     * @return true 表示发布模块已加载，应使用 EventBus；false 表示需要降级
     */
    private boolean shouldUseEventBus(String topic) {
        if (eventBus == null) return false;
        String moduleId = TOPIC_TO_MODULE.get(topic);
        if (moduleId == null) {
            // 未知 topic，默认尝试 EventBus
            return true;
        }
        if ("__core__".equals(moduleId)) {
            // 宿主核心发布的事件，始终可用
            return true;
        }
        // 检测模块是否已加载
        if (moduleExistsChecker == null) return true;
        try {
            return moduleExistsChecker.apply(moduleId);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 注册原版 Bukkit 事件监听器，将多个回调合并为一个分发器。
     */
    private void registerBukkitListener(Set<String> eventClasses,
                                         List<BiConsumer<Player, Map<String, String>>> killCallbacks,
                                         List<BiConsumer<Player, Map<String, String>>> damageCallbacks,
                                         List<BiConsumer<Player, Map<String, String>>> joinCallbacks,
                                         List<BiConsumer<Player, Map<String, String>>> pickupCallbacks,
                                         List<BiConsumer<Player, Map<String, String>>> fishCallbacks) {
        BiConsumer<Player, Map<String, String>> killDispatcher = combine(killCallbacks);
        BiConsumer<Player, Map<String, String>> damageDispatcher = combine(damageCallbacks);
        BiConsumer<Player, Map<String, String>> joinDispatcher = combine(joinCallbacks);
        BiConsumer<Player, Map<String, String>> pickupDispatcher = combine(pickupCallbacks);
        BiConsumer<Player, Map<String, String>> fishDispatcher = combine(fishCallbacks);

        bukkitListener = BukkitEventSourceListener.register(
            plugin,
            java.util.logging.Logger.getLogger("ArcartXSuite"),
            eventClasses,
            killDispatcher != null ? killDispatcher : null,
            damageDispatcher != null ? damageDispatcher : null,
            joinDispatcher != null ? joinDispatcher : null,
            pickupDispatcher != null ? pickupDispatcher : null,
            fishDispatcher != null ? fishDispatcher : null
        );
    }

    /**
     * 将多个回调合并为一个分发器，任一回调异常不影响其他回调。
     */
    private BiConsumer<Player, Map<String, String>> combine(List<BiConsumer<Player, Map<String, String>>> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) return null;
        return (player, payload) -> {
            for (BiConsumer<Player, Map<String, String>> cb : callbacks) {
                try {
                    cb.accept(player, payload);
                } catch (Exception ignored) {}
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
    }

    private void handleEvent(Player player, BattlePassTask task, Map<String, String> payload) {
        if (!active) return;
        if (!task.matchesConditions(player, payload)) return;
        int increment = task.calculateIncrement(player, payload);
        if (increment > 0) {
            service.updateTaskProgress(player, task.taskId(), increment);
        }
    }
}
