package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Template;
import java.util.List;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.service.QuestGpsRewardPreviewResolver;

/**
 * 奖励预览解析（仅 UI 展示，不执行发放）。
 *
 * <p>奖励只在 QuestGPS 配置（{@code data/questgps} 的 {@code quests[].rewards}）中声明，
 * 不再从 Chemdah 任务模板的 agent（kether）脚本里按关键词猜测——Chemdah 没有结构化奖励字段，
 * 那套猜测会把 {@code title}/{@code profile data} 等非奖励动作误报成奖励。
 * {@code template} 参数保留仅为调用方签名兼容。
 */
public final class ChemdahRewardReader {

    private final Logger logger;
    private final QuestGpsRewardPreviewResolver rewardResolver;

    public ChemdahRewardReader(Logger logger, QuestGpsRewardPreviewResolver rewardResolver) {
        this.logger = logger;
        this.rewardResolver = rewardResolver;
    }

    public List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> resolve(
        QuestGpsModuleConfiguration configuration,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        Template template
    ) {
        if (overlay == null) {
            return List.of();
        }
        return rewardResolver.resolve(configuration, overlay.categoryOverride(), overlay.id());
    }
}
