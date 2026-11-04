package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.service.QuestGpsRewardPreviewResolver;

/**
 * 奖励预览解析（仅 UI 展示，不执行发放）。
 *
 * <p>预览读取 Chemdah 任务的 {@code addon.axs-view.rewards}。
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
        Player player
    ) {
        if (overlay == null) {
            return List.of();
        }
        return rewardResolver.resolve(configuration, player, overlay.id());
    }
}
