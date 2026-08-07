package xuanmo.arcartxsuite.battlepass.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

/**
 * 任务分配服务，负责从任务池中按权重随机选取任务并生成玩家任务实例。
 * <p>
 * 支持每日任务和每周任务的分配，使用加权随机算法确保高权重任务有更高被选概率。
 */
public final class TaskAssignmentService {

    private final Random random = new Random();

    /**
     * 为玩家分配每日任务实例。
     *
     * @param playerUuid 玩家 UUID
     * @param pool       可选任务模板池
     * @param count      分配数量
     * @param date       分配日期
     * @return 分配的任务实例列表（不可变）
     */
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

    /**
     * 为玩家分配每周任务实例。
     *
     * @param playerUuid  玩家 UUID
     * @param pool        可选任务模板池
     * @param count       分配数量
     * @param weekNumber  所属周序号
     * @param date        分配日期
     * @return 分配的任务实例列表（不可变）
     */
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
