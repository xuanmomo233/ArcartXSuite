package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.Template;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.CategorySource;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * 启动/重载时校验 overlay 任务 ID 与分类配置是否完整。
 */
public final class QuestGpsOverlayValidator {

    private final Logger logger;
    private final QuestGpsModuleConfiguration configuration;
    private final ChemdahCategoryResolver categoryResolver;

    public QuestGpsOverlayValidator(
        Logger logger,
        QuestGpsModuleConfiguration configuration,
        ChemdahCategoryResolver categoryResolver
    ) {
        this.logger = logger;
        this.configuration = configuration;
        this.categoryResolver = categoryResolver;
    }

    public void validate() {
        if (configuration.quests().isEmpty()) {
            return;
        }
        CategorySource source = configuration.categoryDefaults().source();
        for (QuestGpsModuleConfiguration.QuestDefinition quest : configuration.quests().values()) {
            if (!quest.enabled()) {
                continue;
            }
            Template template = ChemdahAPI.INSTANCE.getQuestTemplate(quest.id());
            if (template == null) {
                logger.warning("QuestGPS: overlay 任务未找到 Chemdah 模板: " + quest.id());
                continue;
            }
            if (source == CategorySource.OVERLAY) {
                if (quest.categoryOverride() == null) {
                    logger.warning("QuestGPS: category.source=overlay 但任务未配置 category: " + quest.id());
                }
                continue;
            }
            QuestGpsCategory fromMeta = categoryResolver.resolveFromChemdah(template);
            if (fromMeta == null) {
                logger.warning(
                    "QuestGPS: Chemdah 任务 meta.type 未在 categories 注册，任务不会进入菜单: " + quest.id()
                );
            }
            if (quest.categoryOverride() != null) {
                logger.warning(
                    "QuestGPS: category.source=chemdah 时 overlay category 不会生效，请删除或改 source: overlay — "
                        + quest.id()
                );
            }
        }
    }
}
