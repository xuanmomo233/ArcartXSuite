package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.List;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

public final class ChemdahQuestDiscovery {
    public ChemdahQuestDiscovery(Logger logger, ChemdahCategoryResolver categoryResolver) {}

    public List<QuestGpsModuleConfiguration.QuestDefinition> resolveDefinitions(QuestGpsModuleConfiguration configuration) {
        return configuration.orderedQuests().stream().filter(QuestGpsModuleConfiguration.QuestDefinition::enabled).toList();
    }
}
