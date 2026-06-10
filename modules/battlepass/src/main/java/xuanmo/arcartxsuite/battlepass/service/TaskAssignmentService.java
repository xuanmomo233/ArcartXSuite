package xuanmo.arcartxsuite.battlepass.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

public final class TaskAssignmentService {

    private final Random random = new Random();

    public List<PlayerTaskInstance> assignDailyTasks(UUID playerUuid, List<BattlePassTask> pool, int count, LocalDate date) {
        List<BattlePassTask> selected = weightedRandomPick(pool, count);
        List<PlayerTaskInstance> instances = new ArrayList<>(selected.size());
        for (BattlePassTask task : selected) {
            instances.add(new PlayerTaskInstance(
                playerUuid, task.taskId(), BattlePassTask.TaskCategory.DAILY,
                task.requiredCount(), date
            ));
        }
        return Collections.unmodifiableList(instances);
    }

    public List<PlayerTaskInstance> assignWeeklyTasks(UUID playerUuid, List<BattlePassTask> pool, int count, int weekNumber, LocalDate date) {
        List<BattlePassTask> selected = weightedRandomPick(pool, count);
        List<PlayerTaskInstance> instances = new ArrayList<>(selected.size());
        for (BattlePassTask task : selected) {
            instances.add(new PlayerTaskInstance(
                playerUuid, task.taskId(), BattlePassTask.TaskCategory.WEEKLY,
                task.requiredCount(), date
            ).withWeekNumber(weekNumber));
        }
        return Collections.unmodifiableList(instances);
    }

    private List<BattlePassTask> weightedRandomPick(List<BattlePassTask> pool, int count) {
        if (pool.isEmpty() || count <= 0) return Collections.emptyList();
        int effectiveCount = Math.min(count, pool.size());
        List<BattlePassTask> source = new ArrayList<>(pool);
        List<BattlePassTask> result = new ArrayList<>(effectiveCount);
        for (int i = 0; i < effectiveCount; i++) {
            int totalWeight = source.stream().mapToInt(BattlePassTask::weight).sum();
            if (totalWeight <= 0) break;
            int pick = random.nextInt(totalWeight);
            int cumulative = 0;
            BattlePassTask chosen = null;
            for (BattlePassTask task : source) {
                cumulative += task.weight();
                if (pick < cumulative) {
                    chosen = task;
                    break;
                }
            }
            if (chosen == null) chosen = source.get(source.size() - 1);
            result.add(chosen);
            source.remove(chosen);
        }
        return Collections.unmodifiableList(result);
    }
}
