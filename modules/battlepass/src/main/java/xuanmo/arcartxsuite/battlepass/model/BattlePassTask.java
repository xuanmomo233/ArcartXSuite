package xuanmo.arcartxsuite.battlepass.model;

import java.util.Collections;
import java.util.List;
import xuanmo.arcartxsuite.battlepass.condition.TaskCondition;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategy;

public record BattlePassTask(
    String taskId,
    String displayName,
    String description,
    TaskCategory taskCategory,
    TaskDifficulty difficulty,
    String eventTopic,
    int requiredCount,
    int baseXpReward,
    float difficultyMultiplier,
    List<TaskCondition> conditions,
    IncrementStrategy incrementStrategy,
    int weight
) {

    public BattlePassTask {
        if (conditions == null) conditions = Collections.emptyList();
        if (incrementStrategy == null) incrementStrategy = new xuanmo.arcartxsuite.battlepass.increment.FixedIncrementStrategy(1);
        if (difficultyMultiplier <= 0) difficultyMultiplier = 1.0f;
        if (weight <= 0) weight = 1;
    }

    public int totalXpReward() {
        return Math.round(baseXpReward * difficultyMultiplier);
    }

    public boolean matchesConditions(org.bukkit.entity.Player player, java.util.Map<String, String> payload) {
        for (TaskCondition condition : conditions) {
            if (!condition.test(player, payload)) return false;
        }
        return true;
    }

    public int calculateIncrement(org.bukkit.entity.Player player, java.util.Map<String, String> payload) {
        return incrementStrategy.calculateIncrement(player, payload);
    }

    public enum TaskCategory {
        DAILY, WEEKLY, SEASON
    }

    public enum TaskDifficulty {
        EASY(1.0f),
        NORMAL(1.2f),
        HARD(1.5f);

        private final float multiplier;

        TaskDifficulty(float multiplier) {
            this.multiplier = multiplier;
        }

        public float multiplier() {
            return multiplier;
        }
    }
}
