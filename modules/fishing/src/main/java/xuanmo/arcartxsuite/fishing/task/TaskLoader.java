package xuanmo.arcartxsuite.fishing.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * 钓鱼任务配置加载器，从 tasks/ 目录的 YAML 文件解析为任务模板列表。
 * <p>
 * 关联模块：{@code fishing}。
 */
public final class TaskLoader {

    private TaskLoader() {}

    /**
     * 从 YAML 配置加载任务模板列表。
     *
     * @param yaml     任务配置（daily.yml / weekly.yml / season.yml）
     * @param category 任务分类
     * @param logger   日志记录器
     * @return 任务模板列表
     */
    public static @NotNull List<FishingTask> load(@NotNull YamlConfiguration yaml,
                                                   @NotNull FishingTask.TaskCategory category,
                                                   @NotNull Logger logger) {
        List<FishingTask> tasks = new ArrayList<>();
        for (String taskId : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(taskId);
            if (section == null) continue;
            try {
                tasks.add(parseTask(taskId, section, category));
            } catch (Exception e) {
                logger.warning("解析任务失败 [" + taskId + "]: " + e.getMessage());
            }
        }
        return tasks;
    }

    private static FishingTask parseTask(String taskId, ConfigurationSection section,
                                          FishingTask.TaskCategory category) {
        String displayName = section.getString("display-name", taskId);
        String description = section.getString("description", "");
        String difficultyStr = section.getString("difficulty", "easy").toUpperCase();
        FishingTask.TaskDifficulty difficulty = parseDifficulty(difficultyStr);
        String eventTopic = section.getString("event-topic", "");
        int requiredCount = section.getInt("required-count", 1);
        int baseXpReward = section.getInt("base-xp-reward", 100);
        int weight = section.getInt("weight", 1);

        List<TaskCondition> conditions = new ArrayList<>();
        List<?> rawConditions = section.getList("conditions");
        if (rawConditions != null) {
            for (Object item : rawConditions) {
                if (item instanceof Map<?, ?> map) {
                    String type = String.valueOf(map.get("type"));
                    if ("event_payload".equals(type)) {
                        String key = String.valueOf(map.get("key"));
                        String operator = String.valueOf(map.get("operator"));
                        String value = String.valueOf(map.get("value"));
                        conditions.add(new EventPayloadCondition(key, operator, value));
                    }
                }
            }
        }

        IncrementStrategy incrementStrategy = new FixedIncrementStrategy(1);
        ConfigurationSection incrSection = section.getConfigurationSection("increment-strategy");
        if (incrSection != null) {
            String type = incrSection.getString("type", "fixed");
            if ("fixed".equals(type)) {
                incrementStrategy = new FixedIncrementStrategy(incrSection.getInt("value", 1));
            } else if ("payload_value".equals(type)) {
                incrementStrategy = new PayloadValueIncrementStrategy(
                    incrSection.getString("payload-key", ""),
                    incrSection.getInt("max-per-event", 0),
                    incrSection.getDouble("scale", 1.0)
                );
            }
        }

        return new FishingTask(taskId, displayName, description, category, difficulty,
            eventTopic, requiredCount, baseXpReward, difficulty.multiplier(),
            conditions, incrementStrategy, weight);
    }

    private static FishingTask.TaskDifficulty parseDifficulty(String str) {
        return switch (str.toUpperCase()) {
            case "NORMAL" -> FishingTask.TaskDifficulty.NORMAL;
            case "HARD" -> FishingTask.TaskDifficulty.HARD;
            default -> FishingTask.TaskDifficulty.EASY;
        };
    }
}
