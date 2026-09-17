package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
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
            int index = 0;
            for (QuestGpsNavigationService.TrackingState state : trackStates.values()) {
                if (index >= DISPLAY_QUEST_LIMIT) {
                    break;
                }
                quests.put(String.valueOf(index), buildQuestEntry(configuration, player, profile, state, presentationService, navigationService));
                index++;
            }
        }
        payload.put("quests", quests);
        payload.put("questCount", quests.size());

        // 兼容字段：主追踪（最近激活）单卡数据，旧版 UI/调试用
        QuestGpsNavigationService.TrackingState primary = primaryState(trackStates);
        payload.put("questName", primary == null ? "" : primary.label());
        QuestGpsNavigationService.NavigationPoint point = primary == null || player == null
            ? null
            : navigationService.trackingPoint(player, primary.trackKey()).orElse(null);
        payload.put("hasNav", point != null);
        payload.put("navWorld", point != null ? point.world() : "");
        payload.put("navX", point != null ? (int) point.x() : 0);
        payload.put("navY", point != null ? (int) point.y() : 0);
        payload.put("navZ", point != null ? (int) point.z() : 0);
        return payload;
    }

    private static QuestGpsNavigationService.TrackingState primaryState(Map<String, QuestGpsNavigationService.TrackingState> trackStates) {
        if (trackStates == null || trackStates.isEmpty()) {
            return null;
        }
        QuestGpsNavigationService.TrackingState last = null;
        for (QuestGpsNavigationService.TrackingState state : trackStates.values()) {
            last = state;
        }
        return last;
    }

    private static Map<String, Object> buildQuestEntry(
        QuestGpsModuleConfiguration configuration,
        Player player,
        PlayerProfile profile,
        QuestGpsNavigationService.TrackingState state,
        QuestGpsPresentationService presentationService,
        QuestGpsNavigationService navigationService
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("questId", state.questId());
        entry.put("taskId", state.taskId());
        entry.put("mode", state.mode().name());
        entry.put("name", state.questName().isBlank() ? state.label() : state.questName());
        entry.put("label", state.label());

        int completedCount = 0;
        int totalCount = 0;
        String taskText = "";
        String taskDesc = "";
        if (profile != null) {
            QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(state.questId());
            if (definition != null) {
                Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
                if (template != null) {
                    List<QuestGpsSnapshotBuilder.TaskDescriptor> taskDescriptors = presentationService.buildTaskDescriptors(
                        profile,
                        template,
                        definition,
                        QuestGpsPage.ACTIVE
                    );
                    totalCount = taskDescriptors.size();
                    QuestGpsSnapshotBuilder.TaskDescriptor firstPending = null;
                    for (QuestGpsSnapshotBuilder.TaskDescriptor task : taskDescriptors) {
                        if (task.completed()) {
                            completedCount++;
                        } else if (firstPending == null) {
                            firstPending = task;
                        }
                    }
                    if (firstPending != null) {
                        taskText = firstPending.text();
                        taskDesc = firstPending.descriptionText();
                    } else if (totalCount > 0) {
                        taskText = "全部完成";
                    }
                }
            }
        }
        entry.put("progressText", completedCount + "/" + totalCount);
        entry.put("taskText", taskText);
        entry.put("taskDesc", taskDesc);

        QuestGpsNavigationService.NavigationPoint point = player == null
            ? null
            : navigationService.trackingPoint(player, state.trackKey()).orElse(null);
        entry.put("hasNav", point != null);
        entry.put("navWorld", point != null ? point.world() : "");
        entry.put("navText", point == null ? "" : point.world() + " " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z());
        return entry;
    }
}
