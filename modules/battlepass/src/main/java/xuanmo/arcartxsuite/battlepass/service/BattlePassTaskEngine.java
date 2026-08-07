package xuanmo.arcartxsuite.battlepass.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;

/**
 * 战令任务引擎，负责监听事件总线并驱动任务进度更新。
 * <p>
 * 启动时为每个任务模板订阅对应的事件主题，事件到达时先进行条件匹配，
 * 匹配通过后计算增量并委托 {@link BattlePassService} 更新玩家任务进度。
 */
public final class BattlePassTaskEngine {

    private final BattlePassService service;
    private final BattlePassModuleConfiguration configuration;
    private final List<String> subscriptionIds = new ArrayList<>();
    private EventBusCapability eventBus;
    private volatile boolean active;

    /**
     * 构造任务引擎。
     *
     * @param service       BattlePassService 实例
     * @param configuration 模块配置
     */
    public BattlePassTaskEngine(BattlePassService service, BattlePassModuleConfiguration configuration) {
        this.service = service;
        this.configuration = configuration;
    }

    /**
     * 启动任务引擎，订阅所有任务模板对应的事件主题。
     *
     * @param eventBusProvider 事件总线能力提供者
     */
    public void start(Supplier<EventBusCapability> eventBusProvider) {
        if (active) return;
        eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus == null) return;
        active = true;

        for (BattlePassTask task : configuration.tasks().all()) {
            String topic = task.eventTopic();
            if (topic == null || topic.isEmpty()) continue;
            String subId = eventBus.subscribe(topic, event -> {
                if (event.player() == null || !event.player().isOnline()) return;
                handleEvent(event.player(), task, event.payload());
            });
            if (subId != null) {
                subscriptionIds.add(subId);
            }
        }
    }

    /**
     * 关闭任务引擎，注销所有事件订阅。
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
    }

    private void handleEvent(Player player, BattlePassTask task, java.util.Map<String, String> payload) {
        if (!active) return;
        if (!task.matchesConditions(player, payload)) return;
        int increment = task.calculateIncrement(player, payload);
        if (increment > 0) {
            service.updateTaskProgress(player, task.taskId(), increment);
        }
    }
}
