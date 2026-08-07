package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 玩家任务实例 record，表示某玩家被分配的一个具体任务实例。
 * <p>
 * 与 {@link BattlePassTask}（模板）不同，此 record 记录运行时状态（当前进度、完成状态、分配日期等）。
 *
 * @param playerUuid      玩家 UUID
 * @param instanceId      实例唯一标识（由玩家 UUID + 分类 + 模板 ID + 日期组合生成）
 * @param templateId      对应的任务模板 ID
 * @param category        任务分类（DAILY/WEEKLY/SEASON）
 * @param targetCount     完成所需次数
 * @param currentProgress 当前进度
 * @param completed       是否已完成
 * @param assignedDate    分配日期
 * @param weekNumber      所属周序号
 */
public record PlayerTaskInstance(
    UUID playerUuid,
    String instanceId,
    String templateId,
    BattlePassTask.TaskCategory category,
    int targetCount,
    int currentProgress,
    boolean completed,
    LocalDate assignedDate,
    int weekNumber
) {

    public PlayerTaskInstance {
        if (currentProgress < 0) currentProgress = 0;
        if (targetCount < 1) targetCount = 1;
    }

    public PlayerTaskInstance(UUID playerUuid, String templateId, BattlePassTask.TaskCategory category,
                               int targetCount, LocalDate assignedDate) {
        this(playerUuid, playerUuid + "|" + category + "|" + templateId + "|" + assignedDate, templateId, category,
             targetCount, 0, false, assignedDate, 0);
    }

    /**
     * 返回更新进度后的新任务实例。
     *
     * @param progress 新进度值
     * @return 更新后的任务实例
     */
    public PlayerTaskInstance withProgress(int progress) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            progress, completed, assignedDate, weekNumber);
    }

    /**
     * 返回更新完成状态后的新任务实例。
     *
     * @param completed 新完成状态
     * @return 更新后的任务实例
     */
    public PlayerTaskInstance withCompleted(boolean completed) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            currentProgress, completed, assignedDate, weekNumber);
    }

    /**
     * 返回更新周序号后的新任务实例。
     *
     * @param weekNumber 新周序号
     * @return 更新后的任务实例
     */
    public PlayerTaskInstance withWeekNumber(int weekNumber) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            currentProgress, completed, assignedDate, weekNumber);
    }

    /**
     * 返回任务进度百分比（0~100）。
     *
     * @return 进度百分比
     */
    public double progressPercent() {
        return Math.min(100.0, (currentProgress * 100.0) / targetCount);
    }
}
