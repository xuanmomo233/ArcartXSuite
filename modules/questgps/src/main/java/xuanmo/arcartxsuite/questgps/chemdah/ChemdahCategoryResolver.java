package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import java.util.Map;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.CategoryDefaults;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

public final class ChemdahCategoryResolver {
    private final CategoryDefaults defaults;
    private final Supplier<Map<String, QuestGpsCategory>> categories;

    public ChemdahCategoryResolver(CategoryDefaults defaults, Supplier<Map<String, QuestGpsCategory>> categories) {
        this.defaults = defaults;
        this.categories = categories;
    }

    public QuestGpsCategory resolveEffectiveCategory(Template template, QuestGpsModuleConfiguration.QuestDefinition view) {
        if (view != null && view.categoryOverride() != null) return view.categoryOverride();
        return resolveFromChemdah(template);
    }

    public QuestGpsCategory resolveFromChemdah(Template template) {
        Object meta = template == null ? null : template.metaAll().get("type");
        if (meta instanceof MetaType types) {
            for (String type : types.getType()) {
                QuestGpsCategory category = QuestGpsCategory.parse(type, categories.get());
                if (category != null) return category;
            }
        }
        return defaults.fallback().enabled() ? QuestGpsCategory.parse(defaults.fallback().id(), categories.get()) : null;
    }

    public void resetWarnings() {
        // Kept as the category refresh hook; no source-selection warning state exists.
    }
}
