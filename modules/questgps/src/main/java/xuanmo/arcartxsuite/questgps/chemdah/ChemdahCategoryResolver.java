package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.CategoryDefaults;
import xuanmo.arcartxsuite.questgps.config.CategorySource;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 任务分类解析：全局仅一种数据源（Chemdah meta.type 或 overlay category），
 * 未匹配时可按配置归入兜底分类。
 */
public final class ChemdahCategoryResolver {

    private final Logger logger;
    private final CategoryDefaults categoryDefaults;
    private final Map<String, QuestGpsCategory> categoryRegistry;
    private final QuestGpsCategory fallbackCategory;
    private final Set<String> warnedMetaTypeQuestIds = new HashSet<>();
    private final Set<String> warnedOverlayQuestIds = new HashSet<>();

    public ChemdahCategoryResolver(
        Logger logger,
        CategoryDefaults categoryDefaults,
        Map<String, QuestGpsCategory> categoryRegistry
    ) {
        this.logger = logger;
        this.categoryDefaults = categoryDefaults == null ? CategoryDefaults.defaults() : categoryDefaults;
        this.categoryRegistry = categoryRegistry == null ? Map.of() : categoryRegistry;
        CategoryDefaults.FallbackCategory fallback = this.categoryDefaults.fallback();
        this.fallbackCategory = fallback.enabled()
            ? QuestGpsCategory.parse(fallback.id(), this.categoryRegistry)
            : null;
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

    /**
     * 重置警告记录，允许下次 resolve 周期再次输出（每次 reload 调用一次）。
     */
    public void resetWarnings() {
        warnedMetaTypeQuestIds.clear();
        warnedOverlayQuestIds.clear();
    }

    private QuestGpsCategory resolveFromOverlay(QuestGpsModuleConfiguration.QuestDefinition overlay) {
        if (overlay != null && overlay.categoryOverride() != null) {
            return overlay.categoryOverride();
        }
        if (fallbackCategory != null) {
            return fallbackCategory;
        }
        String questId = overlay == null ? "unknown" : overlay.id();
        if (warnedOverlayQuestIds.add(questId)) {
            logger.warning("QuestGPS: category.source=overlay 但任务未配置 category: " + questId);
        }
        return null;
    }

    private QuestGpsCategory resolveFromChemdahMetaType(Template template, String questIdForLog) {
        QuestGpsCategory fromMeta = resolveMetaType(template);
        if (fromMeta != null) {
            return fromMeta;
        }
        if (fallbackCategory != null) {
            return fallbackCategory;
        }
        String questId = questIdForLog != null && !questIdForLog.isBlank()
            ? questIdForLog
            : (template == null ? "unknown" : template.getId());
        if (warnedMetaTypeQuestIds.add(questId)) {
            logger.warning(
                "QuestGPS: Chemdah meta.type 未在 categories 注册，任务不会进入菜单: " + questId
            );
        }
        return null;
    }

    private QuestGpsCategory resolveMetaType(Template template) {
        if (template == null) {
            return null;
        }
        Meta<?> typeMeta = template.getMetaMap().get("type");
        if (!(typeMeta instanceof MetaType metaType)) {
            return null;
        }
        List<String> types = metaType.getType();
        if (types == null || types.isEmpty()) {
            return null;
        }
        for (String raw : types) {
            QuestGpsCategory category = QuestGpsCategory.parse(raw, categoryRegistry);
            if (category != null) {
                return category;
            }
        }
        return null;
    }
}
