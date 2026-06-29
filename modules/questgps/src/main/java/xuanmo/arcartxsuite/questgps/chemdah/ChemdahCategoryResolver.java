package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Template;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.CategoryDefaults;
import xuanmo.arcartxsuite.questgps.config.CategorySource;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 任务分类解析：全局仅一种数据源（Chemdah meta.type 或 overlay category），无多层回退。
 */
public final class ChemdahCategoryResolver {

    private final Logger logger;
    private final CategoryDefaults categoryDefaults;
    private final Map<String, QuestGpsCategory> categoryRegistry;

    public ChemdahCategoryResolver(
        Logger logger,
        CategoryDefaults categoryDefaults,
        Map<String, QuestGpsCategory> categoryRegistry
    ) {
        this.logger = logger;
        this.categoryDefaults = categoryDefaults == null ? CategoryDefaults.defaults() : categoryDefaults;
        this.categoryRegistry = categoryRegistry == null ? Map.of() : categoryRegistry;
    }

    public QuestGpsCategory resolveEffectiveCategory(
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition overlay
    ) {
        if (categoryDefaults.source() == CategorySource.OVERLAY) {
            return resolveFromOverlay(overlay);
        }
        return resolveFromChemdahMetaType(template, overlay == null ? null : overlay.id());
    }

    /**
     * 当前策略下从 Chemdah 读取的分类（仅 meta.type）。
     */
    public QuestGpsCategory resolveFromChemdah(Template template) {
        return resolveMetaType(template);
    }

    private QuestGpsCategory resolveFromOverlay(QuestGpsModuleConfiguration.QuestDefinition overlay) {
        if (overlay != null && overlay.categoryOverride() != null) {
            return overlay.categoryOverride();
        }
        String questId = overlay == null ? "unknown" : overlay.id();
        logger.warning("QuestGPS category.source=overlay 但任务未配置 category: " + questId);
        return null;
    }

    private QuestGpsCategory resolveFromChemdahMetaType(Template template, String questIdForLog) {
        QuestGpsCategory fromMeta = resolveMetaType(template);
        if (fromMeta != null) {
            return fromMeta;
        }
        String questId = questIdForLog != null && !questIdForLog.isBlank()
            ? questIdForLog
            : (template == null ? "unknown" : template.getId());
        logger.warning(
            "QuestGPS Chemdah meta.type 未在 categories 注册，任务不会进入菜单: " + questId
        );
        return null;
    }

    private QuestGpsCategory resolveMetaType(Template template) {
        if (template == null) {
            return null;
        }
        ConfigurationSection section = ChemdahConfigAccessor.section(template);
        if (section == null) {
            return null;
        }
        ConfigurationSection meta = section.getConfigurationSection("meta");
        if (meta == null) {
            return null;
        }
        return QuestGpsCategory.parse(meta.getString("type"), categoryRegistry);
    }
}
