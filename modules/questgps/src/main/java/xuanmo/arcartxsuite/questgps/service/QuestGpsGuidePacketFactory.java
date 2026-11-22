package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * QuestGPS 引导界面数据包工厂。
 * <p>
 * 属于 {@code questgps} 模块，负责将当前追踪状态集合（多目标并行）、任务进度与导航坐标
 * 组装为发送给客户端引导 HUD 的数据包。
 * <p>
 * 包结构：{@code quests} 为按激活顺序排列的追踪卡片字典（key = "0","1","2"…），
 * 每张卡片含任务名、进度、当前目标与导航坐标摘要。
 */
public final class QuestGpsGuidePacketFactory {

    /** HUD 最多展示的追踪任务卡片数。 */
    private static final int DISPLAY_QUEST_LIMIT = 3;
    private static final QuestGpsPresentationService.GuideProgress NO_PROGRESS =
        new QuestGpsPresentationService.GuideProgress(0, 0, "", "");

    private QuestGpsGuidePacketFactory() {
    }

    /**
     * 构建引导 HUD 数据包（多追踪版本）。
     *
     * @param configuration       模块配置
     * @param player              当前玩家，为 {@code null} 时仅返回空结构
     * @param trackStates         当前全部追踪状态（trackKey → 状态，激活顺序）
     * @param presentationService 展示服务，用于构建任务描述符
     * @param navigationService   导航服务，用于获取追踪坐标
     * @return 引导 HUD 数据包
     */
    public static Map<String, Object> build(
        QuestGpsModuleConfiguration configuration,
        Player player,
        Map<String, QuestGpsNavigationService.TrackingState> trackStates,
        QuestGpsPresentationService presentationService,
        QuestGpsNavigationService navigationService
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.ui().packetId());
        boolean active = trackStates != null && !trackStates.isEmpty();
        payload.put("active", active);
        payload.put("trackedCount", trackStates == null ? 0 : trackStates.size());

        PlayerProfile profile = null;
        if (player != null) {
            profile = ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)
                ? ChemdahAPI.INSTANCE.getChemdahProfile(player)
                : null;
        }

        Map<String, Object> quests = new LinkedHashMap<>();
        if (trackStates != null) {
            var progressByQuest = new HashMap<String, QuestGpsPresentationService.GuideProgress>();
            int index = 0;
            for (QuestGpsNavigationService.TrackingState state : trackStates.values()) {
                if (index >= DISPLAY_QUEST_LIMIT) {
                    break;
                }
                quests.put(String.valueOf(index), buildQuestEntry(configuration, player, profile, state, presentationService,
                    navigationService, progressByQuest));
                index++;
            }
        }
        payload.put("quests", quests);
        payload.put("questCount", quests.size());

        return payload;
    }

    private static Map<String, Object> buildQuestEntry(
        QuestGpsModuleConfiguration configuration,
        Player player,
        PlayerProfile profile,
        QuestGpsNavigationService.TrackingState state,
        QuestGpsPresentationService presentationService,
        QuestGpsNavigationService navigationService,
        Map<String, QuestGpsPresentationService.GuideProgress> progressByQuest
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("questId", state.questId());
        entry.put("taskId", state.taskId());
        entry.put("mode", state.mode().name());
        entry.put("name", state.questName().isBlank() ? state.label() : state.questName());
        entry.put("label", state.label());

        var progress = progressByQuest.computeIfAbsent(state.questId(), quest ->
            loadProgress(configuration, profile, presentationService, quest));
        entry.put("progressText", progress.completed() + "/" + progress.total());
        entry.put("taskText", progress.taskText());
        entry.put("taskDesc", progress.taskDescription());

        QuestGpsNavigationService.NavigationPoint point = player == null
            ? null
            : navigationService.trackingPoint(player, state.trackKey()).orElse(null);
        entry.put("hasNav", point != null);
        entry.put("navWorld", point != null ? point.world() : "");
        entry.put("navText", point == null ? "" : point.world() + " " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z());
        return entry;
    }

    private static QuestGpsPresentationService.GuideProgress loadProgress(QuestGpsModuleConfiguration configuration,
            PlayerProfile profile, QuestGpsPresentationService presentationService, String questId) {
        if (profile == null) return NO_PROGRESS;
        var definition = configuration.quest(questId);
        if (definition == null) return NO_PROGRESS;
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        return template == null ? NO_PROGRESS : presentationService.guideProgress(profile, template, definition);
    }
}
