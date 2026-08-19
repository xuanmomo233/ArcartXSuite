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
 * 属于 {@code questgps} 模块，负责将当前追踪状态、任务进度与导航坐标
 * 组装为发送给客户端引导 HUD 的数据包。
 */
public final class QuestGpsGuidePacketFactory {

    private static final int DISPLAY_TASK_LIMIT = 3;

    private QuestGpsGuidePacketFactory() {
    }

    /**
     * 构建引导 HUD 数据包。
     *
     * @param configuration       模块配置
     * @param player              当前玩家，为 {@code null} 时仅返回空任务结构
     * @param trackState          当前追踪状态
     * @param presentationService 展示服务，用于构建任务描述符
     * @param navigationService   导航服务，用于获取当前追踪坐标
     * @return 引导 HUD 数据包
     */
    public static Map<String, Object> build(
        QuestGpsModuleConfiguration configuration,
        Player player,
        QuestGpsNavigationService.TrackingState trackState,
        QuestGpsPresentationService presentationService,
        QuestGpsNavigationService navigationService
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.ui().packetId());
        payload.put("active", trackState.active());
        payload.put("questName", trackState.label());

        Map<String, Object> tasks = new LinkedHashMap<>();
        int completedCount = 0;
        int totalCount = 0;
        if (player != null) {
            PlayerProfile profile = ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)
                ? ChemdahAPI.INSTANCE.getChemdahProfile(player)
                : null;
            if (profile != null) {
                QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(trackState.questId());
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
                        int shown = 0;
                        for (QuestGpsSnapshotBuilder.TaskDescriptor task : taskDescriptors) {
                            if (task.completed()) {
                                completedCount++;
                            }
                            if (shown < DISPLAY_TASK_LIMIT) {
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("id", task.taskId());
                                entry.put("text", task.text());
                                entry.put("description", task.descriptionText());
                                entry.put("completed", task.completed());
                                entry.put("status", task.statusText());
                                tasks.put(task.taskId(), entry);
                                shown++;
                            }
                        }
                    }
                }
            }
        }
        payload.put("completedCount", completedCount);
        payload.put("totalCount", totalCount);
        payload.put("progressText", completedCount + "/" + totalCount);
        payload.put("tasks", tasks);
        payload.put("taskCount", tasks.size());

        QuestGpsNavigationService.NavigationPoint point = navigationService.trackingPoint(player).orElse(null);
        payload.put("hasNav", point != null);
        payload.put("navWorld", point != null ? point.world() : "");
        payload.put("navX", point != null ? (int) point.x() : 0);
        payload.put("navY", point != null ? (int) point.y() : 0);
        payload.put("navZ", point != null ? (int) point.z() : 0);
        return payload;
    }
}
