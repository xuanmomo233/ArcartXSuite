package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;
import java.util.UUID;

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

    public PlayerTaskInstance withProgress(int progress) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            progress, completed, assignedDate, weekNumber);
    }

    public PlayerTaskInstance withCompleted(boolean completed) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            currentProgress, completed, assignedDate, weekNumber);
    }

    public PlayerTaskInstance withWeekNumber(int weekNumber) {
        return new PlayerTaskInstance(playerUuid, instanceId, templateId, category, targetCount,
            currentProgress, completed, assignedDate, weekNumber);
    }

    public double progressPercent() {
        return Math.min(100.0, (currentProgress * 100.0) / targetCount);
    }
}
