package xuanmo.arcartxsuite.fishing.task;

import java.time.LocalDate;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家任务实例，记录运行时状态。
 *
 * @param playerUuid      玩家 UUID
 * @param instanceId      实例唯一标识
 * @param templateId      任务模板 ID
 * @param category        任务分类
 * @param targetCount     完成所需次数
 * @param currentProgress 当前进度
 * @param completed       是否已完成
 * @param rewardClaimed   是否已领取奖励
 * @param assignedDate    分配日期
 */
public record PlayerTaskInstance(
    @NotNull UUID playerUuid,
    @NotNull String instanceId,
    @NotNull String templateId,
    @NotNull FishingTask.TaskCategory category,
    int targetCount,
    int currentProgress,
    boolean completed,
    boolean rewardClaimed,
    @NotNull LocalDate assignedDate
) {

    public PlayerTaskInstance {
        if (currentProgress < 0) currentProgress = 0;
        if (targetCount < 1) targetCount = 1;
    }

    public PlayerTaskInstance(@NotNull UUID playerUuid, @NotNull String templateId,
                               @NotNull FishingTask.TaskCategory category,
                               int targetCount, @NotNull LocalDate assignedDate) {
        this(playerUuid, playerUuid + "|" + category + "|" + templateId + "|" + assignedDate,
            templateId, category, targetCount, 0, false, false, assignedDate);
    }

    public PlayerTaskInstance withProgress(int progress) {
        boolean done = progress >= targetCount;
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category,
            targetCount, Math.min(progress, targetCount), done, rewardClaimed, assignedDate);
    }

    public PlayerTaskInstance withRewardClaimed() {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category,
            targetCount, currentProgress, completed, true, assignedDate);
    }

    public double progressPercent() {
        return Math.min(100.0, (currentProgress * 100.0) / targetCount);
    }
}
