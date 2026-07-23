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

public final class BattlePassTaskEngine {

    private final BattlePassService service;
    private final BattlePassModuleConfiguration configuration;
    private final List<String> subscriptionIds = new ArrayList<>();
    private EventBusCapability eventBus;
    private volatile boolean active;

    public BattlePassTaskEngine(BattlePassService service, BattlePassModuleConfiguration configuration) {
        this.service = service;
        this.configuration = configuration;
    }

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
