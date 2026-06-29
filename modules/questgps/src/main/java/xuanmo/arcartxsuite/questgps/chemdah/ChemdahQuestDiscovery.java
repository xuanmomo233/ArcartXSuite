package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 任务列表发现：overlay 白名单或扫描全部 Chemdah 模板。
 */
public final class ChemdahQuestDiscovery {

    private final Logger logger;
    private final ChemdahCategoryResolver categoryResolver;

    public ChemdahQuestDiscovery(Logger logger, ChemdahCategoryResolver categoryResolver) {
        this.logger = logger;
        this.categoryResolver = categoryResolver;
    }

    public List<QuestGpsModuleConfiguration.QuestDefinition> resolveDefinitions(
        QuestGpsModuleConfiguration configuration
    ) {
        if (!isAutoMode(configuration.discoveryMode())) {
            return configuration.orderedQuests();
        }
        Map<String, Template> templates = loadAllTemplates();
        if (templates.isEmpty()) {
            logger.warning("QuestGPS discovery.mode=auto 但未从 Chemdah 加载到任何任务模板，已回退 overlay 白名单。");
            return configuration.orderedQuests();
        }
        LinkedHashMap<String, QuestGpsModuleConfiguration.QuestDefinition> merged = new LinkedHashMap<>();
        for (Template template : templates.values()) {
            if (template == null || template.getId() == null || template.getId().isBlank()) {
                continue;
            }
            String questId = template.getId().trim();
            QuestGpsModuleConfiguration.QuestDefinition overlay = configuration.quest(questId);
            if (overlay != null && !overlay.enabled()) {
                continue;
            }
            QuestGpsModuleConfiguration.QuestDefinition definition = overlay != null
                ? overlay
                : QuestGpsModuleConfiguration.syntheticQuest(questId);
            merged.put(questId.toLowerCase(java.util.Locale.ROOT), definition);
        }
        List<QuestGpsModuleConfiguration.QuestDefinition> definitions = new ArrayList<>(merged.values());
        definitions.sort(
            Comparator
                .comparingInt((QuestGpsModuleConfiguration.QuestDefinition d) -> effectiveSortOrder(d, templates))
                .thenComparingInt(QuestGpsModuleConfiguration.QuestDefinition::sortOrder)
                .thenComparing(QuestGpsModuleConfiguration.QuestDefinition::id, String.CASE_INSENSITIVE_ORDER)
        );
        return List.copyOf(definitions);
    }

    public static boolean isAutoMode(String discoveryMode) {
        return discoveryMode != null && discoveryMode.trim().equalsIgnoreCase("auto");
    }

    private int effectiveSortOrder(
        QuestGpsModuleConfiguration.QuestDefinition definition,
        Map<String, Template> templates
    ) {
        Template template = templates.get(definition.id());
        if (template == null) {
            template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        }
        QuestGpsCategory category = categoryResolver.resolveEffectiveCategory(template, definition);
        return category == null ? Integer.MAX_VALUE : category.sortOrder();
    }

    private Map<String, Template> loadAllTemplates() {
        try {
            Map<String, Template> templates = ChemdahAPI.INSTANCE.getQuestTemplate();
            if (templates == null || templates.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(templates);
        } catch (Exception ex) {
            logger.warning("QuestGPS 扫描 Chemdah 任务模板失败: " + ex.getMessage());
        }
        return Map.of();
    }
}
