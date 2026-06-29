package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Agent;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.service.QuestGpsRewardPreviewResolver;

/**
 * 从 Chemdah 任务模板解析奖励预览（仅 UI 展示，不执行发放）。
 */
public final class ChemdahRewardReader {

    private final Logger logger;
    private final QuestGpsRewardPreviewResolver overlayResolver;

    public ChemdahRewardReader(Logger logger, QuestGpsRewardPreviewResolver overlayResolver) {
        this.logger = logger;
        this.overlayResolver = overlayResolver;
    }

    public List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> resolve(
        QuestGpsModuleConfiguration configuration,
        QuestGpsModuleConfiguration.QuestDefinition overlay,
        Template template
    ) {
        PresentationSource source = overlay.presentation().source(configuration.presentation().source());
        if (source == PresentationSource.OVERLAY) {
            return overlayResolver.resolve(configuration, overlay.categoryOverride(), overlay.id());
        }
        return readChemdahRewards(template);
    }

    private List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> readChemdahRewards(Template template) {
        if (template == null) {
            return List.of();
        }
        List<QuestGpsRewardPreviewResolver.ResolvedRewardPreview> rewards = new ArrayList<>();
        int index = 0;
        for (Agent agent : template.getAgentList()) {
            if (agent == null || agent.getType() != AgentType.QUEST_COMPLETED) {
                continue;
            }
            for (String action : agent.getAction()) {
                if (action == null || action.isBlank()) {
                    continue;
                }
                String lower = action.toLowerCase(Locale.ROOT);
                if (lower.contains("item") || lower.contains("give")) {
                    rewards.add(textPreview(template.getId(), index++, "物品奖励", action.trim()));
                } else if (lower.contains("level") || lower.contains("exp")) {
                    rewards.add(textPreview(template.getId(), index++, "经验/等级", action.trim()));
                } else if (lower.contains("title")) {
                    rewards.add(textPreview(template.getId(), index++, "称号", action.trim()));
                } else {
                    rewards.add(textPreview(template.getId(), index++, "奖励", action.trim()));
                }
            }
        }

        ConfigurationSection section = ChemdahConfigAccessor.section(template);
        if (section != null) {
            List<?> rewardList = section.getList("reward");
            if (rewardList != null) {
                for (Object entry : rewardList) {
                    rewards.add(textPreview(template.getId(), index++, "奖励", String.valueOf(entry)));
                }
            }
        }
        if (rewards.isEmpty()) {
            logger.fine("QuestGPS 未从 Chemdah 模板解析到奖励预览: " + template.getId());
        }
        return List.copyOf(rewards);
    }

    private QuestGpsRewardPreviewResolver.ResolvedRewardPreview textPreview(
        String questId,
        int index,
        String title,
        String description
    ) {
        return new QuestGpsRewardPreviewResolver.ResolvedRewardPreview(
            questId + "#chemdah#" + index,
            "text",
            title,
            description,
            "",
            "",
            "",
            1
        );
    }
}
