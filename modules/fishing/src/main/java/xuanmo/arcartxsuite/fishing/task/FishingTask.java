package xuanmo.arcartxsuite.fishing.task;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 钓鱼任务模板定义。
 *
 * @param taskId          任务唯一标识
 * @param displayName     显示名称
 * @param description     描述
 * @param category        任务分类（DAILY/WEEKLY/SEASON）
 * @param difficulty      难度
 * @param eventTopic      触发事件主题（ArcartX EventBus）
 * @param requiredCount   完成所需次数
 * @param baseXpReward    基础经验奖励
 * @param difficultyMultiplier 难度倍率
 * @param conditions      触发条件列表
 * @param incrementStrategy 增量策略
 * @param weight          分配权重
 */
public record FishingTask(
    @NotNull String taskId,
    @NotNull String displayName,
    @NotNull String description,
    @NotNull TaskCategory category,
    @NotNull TaskDifficulty difficulty,
    @NotNull String eventTopic,
    int requiredCount,
    int baseXpReward,
    float difficultyMultiplier,
    @NotNull List<TaskCondition> conditions,
    @NotNull IncrementStrategy incrementStrategy,
    int weight
) {

    public FishingTask {
        if (conditions == null) conditions = Collections.emptyList();
        if (difficultyMultiplier <= 0) difficultyMultiplier = 1.0f;
        if (weight <= 0) weight = 1;
    }

    public int totalXpReward() {
        return Math.round(baseXpReward * difficultyMultiplier);
    }

    public boolean matchesConditions(@NotNull org.bukkit.entity.Player player,
                                      @NotNull java.util.Map<String, String> payload) {
        for (TaskCondition condition : conditions) {
            if (!condition.test(player, payload)) return false;
        }
        return true;
    }

    public int calculateIncrement(@NotNull org.bukkit.entity.Player player,
                                   @NotNull java.util.Map<String, String> payload) {
        return incrementStrategy.calculateIncrement(player, payload);
    }

    public enum TaskCategory { DAILY, WEEKLY, SEASON }

    public enum TaskDifficulty {
        EASY(1.0f), NORMAL(1.2f), HARD(1.5f);
        private final float multiplier;
        TaskDifficulty(float m) { this.multiplier = m; }
        public float multiplier() { return multiplier; }
    }
}
