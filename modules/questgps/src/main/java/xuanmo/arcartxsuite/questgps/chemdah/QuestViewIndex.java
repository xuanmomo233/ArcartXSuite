package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration.QuestDefinition;

/** A complete immutable view is published only after a successful rebuild. */
public final class QuestViewIndex {
    public record Snapshot(Map<String, QuestDefinition> quests, Map<String, QuestGpsCategory> categories) {
        public Snapshot {
            quests = Map.copyOf(quests);
            categories = Map.copyOf(categories);
        }
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(Map.of(), Map.of()));

    public Snapshot snapshot() {
        return current.get();
    }

    public void publish(Map<String, QuestDefinition> quests, Map<String, QuestGpsCategory> categories) {
        current.set(new Snapshot(quests, categories));
    }

    public void clear() {
        current.set(new Snapshot(Map.of(), Map.of()));
    }
}
