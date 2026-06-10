package xuanmo.arcartxsuite.battlepass.config;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.battlepass.condition.TaskCondition;
import xuanmo.arcartxsuite.battlepass.condition.TaskConditionParser;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategy;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategyParser;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;

public record BattlePassModuleConfiguration(
    BattlePassStorageConfiguration storage,
    BattlePassSeasonConfiguration season,
    BattlePassTasksConfiguration tasks,
    List<BattlePassReward> rewards,
    BattlePassUiConfiguration ui,
    BattlePassCrossServerConfiguration crossServer
) {

    public static BattlePassModuleConfiguration load(FileConfiguration yaml, @Nullable File dataFolder, Logger logger) {
        return new BattlePassModuleConfiguration(
            BattlePassStorageConfiguration.load(yaml.getConfigurationSection("storage")),
            BattlePassSeasonConfiguration.load(yaml.getConfigurationSection("season")),
            BattlePassTasksConfiguration.load(yaml.getConfigurationSection("tasks"), dataFolder, logger),
            loadRewards(yaml.getConfigurationSection("rewards")),
            BattlePassUiConfiguration.load(yaml.getConfigurationSection("ui")),
            BattlePassCrossServerConfiguration.load(yaml.getConfigurationSection("cross-server"))
        );
    }

    private static List<BattlePassReward> loadRewards(ConfigurationSection section) {
        List<BattlePassReward> result = new ArrayList<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(key);
            if (rewardSection == null) continue;
            int level = rewardSection.getInt("level", 0);
            ConfigurationSection freeSection = rewardSection.getConfigurationSection("free");
            if (freeSection != null && level > 0) {
                result.add(parseReward(level + "_free", level, BattlePassReward.RewardTier.FREE, freeSection));
            }
            ConfigurationSection premiumSection = rewardSection.getConfigurationSection("premium");
            if (premiumSection != null && level > 0) {
                result.add(parseReward(level + "_premium", level, BattlePassReward.RewardTier.PREMIUM, premiumSection));
            }
            ConfigurationSection deluxeSection = rewardSection.getConfigurationSection("deluxe");
            if (deluxeSection != null && level > 0) {
                result.add(parseReward(level + "_deluxe", level, BattlePassReward.RewardTier.DELUXE, deluxeSection));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static BattlePassReward parseReward(String rewardId, int level, BattlePassReward.RewardTier tier, ConfigurationSection section) {
        String type = section.getString("type", "command");
        Map<String, String> data = new LinkedHashMap<>();
        data.put("data", section.getString("data", ""));
        return new BattlePassReward(rewardId, level, tier, type, data);
    }

    public record BattlePassStorageConfiguration(
        String mode,
        String sqliteFileName,
        int poolSize,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword
    ) {
        public static BattlePassStorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new BattlePassStorageConfiguration(
                section.getString("mode", "sqlite"),
                section.getString("sqlite-file-name", "battlepass.db"),
                section.getInt("pool-size", 1),
                section.getString("mysql-host", "localhost"),
                section.getInt("mysql-port", 3306),
                section.getString("mysql-database", "axs_battlepass"),
                section.getString("mysql-username", "root"),
                section.getString("mysql-password", "")
            );
        }

        public StorageDescriptor toDescriptor() {
            boolean isMysql = "mysql".equalsIgnoreCase(mode);
            if (isMysql) {
                return StorageDescriptor.mysql(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, "");
            }
            return StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    public record BattlePassSeasonConfiguration(
        String seasonId,
        String displayName,
        int maxLevel,
        int xpPerLevel,
        LocalDate startDate,
        LocalDate endDate
    ) {
        public static BattlePassSeasonConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new BattlePassSeasonConfiguration(
                section.getString("season-id", "season-1"),
                section.getString("display-name", "第一赛季"),
                section.getInt("max-level", 100),
                section.getInt("xp-per-level", 1000),
                parseDate(section.getString("start-date", "2026-06-01")),
                parseDate(section.getString("end-date", "2026-08-01"))
            );
        }

        private static LocalDate parseDate(String value) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                return LocalDate.now();
            }
        }
    }

    public record BattlePassTasksConfiguration(
        List<BattlePassTask> daily,
        List<BattlePassTask> weekly,
        List<BattlePassTask> season,
        int dailyCount,
        int weeklyCount
    ) {
        public static BattlePassTasksConfiguration load(ConfigurationSection section, @Nullable File dataFolder, Logger logger) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            String tasksDirectory = section.getString("tasks-directory", "tasks");
            return new BattlePassTasksConfiguration(
                loadTasksWithFallback(section, "daily", BattlePassTask.TaskCategory.DAILY, tasksDirectory, dataFolder, logger),
                loadTasksWithFallback(section, "weekly", BattlePassTask.TaskCategory.WEEKLY, tasksDirectory, dataFolder, logger),
                loadTasksWithFallback(section, "season", BattlePassTask.TaskCategory.SEASON, tasksDirectory, dataFolder, logger),
                section.getInt("daily-count", 3),
                section.getInt("weekly-count", 2)
            );
        }

        private static List<BattlePassTask> loadTasksWithFallback(
            ConfigurationSection section, String key,
            BattlePassTask.TaskCategory category, String tasksDirectory,
            @Nullable File dataFolder, Logger logger
        ) {
            // 优先读取主配置中的内联任务（兼容旧版）
            ConfigurationSection inline = section.getConfigurationSection(key);
            if (inline != null && !inline.getKeys(false).isEmpty()) {
                return loadTaskList(inline, category);
            }
            // 无内联定义时，从外部文件读取
            if (dataFolder != null) {
                File file = new File(dataFolder, tasksDirectory + "/" + key + ".yml");
                if (file.exists()) {
                    try {
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                        ConfigurationSection root = yaml.getConfigurationSection("tasks");
                        if (root == null) root = yaml; // 允许顶层直接写任务
                        List<BattlePassTask> tasks = loadTaskList(root, category);
                        if (logger != null) {
                            logger.fine("已从外部文件加载 " + category.name().toLowerCase() + " 任务: " + file.getName() + " (" + tasks.size() + " 条)");
                        }
                        return tasks;
                    } catch (Exception e) {
                        if (logger != null) {
                            logger.log(Level.WARNING, "加载外部任务文件失败: " + file.getPath(), e);
                        }
                    }
                } else {
                    if (logger != null) {
                        logger.warning("未找到外部任务文件: " + file.getPath() + "，" + category.name().toLowerCase() + " 任务列表为空");
                    }
                }
            }
            return Collections.emptyList();
        }

        private static List<BattlePassTask> loadTaskList(ConfigurationSection section, BattlePassTask.TaskCategory category) {
            List<BattlePassTask> result = new ArrayList<>();
            if (section == null) return result;
            for (String key : section.getKeys(false)) {
                ConfigurationSection taskSection = section.getConfigurationSection(key);
                if (taskSection == null) continue;
                result.add(parseTask(taskSection, key, category));
            }
            return Collections.unmodifiableList(result);
        }

        private static BattlePassTask parseTask(ConfigurationSection section, String key, BattlePassTask.TaskCategory category) {
            String taskId = section.getString("task-id", key);
            String displayName = section.getString("display-name", key);
            String description = section.getString("description", "");
            String eventTopic = section.getString("event-topic", "");
            int requiredCount = section.getInt("required-count", 1);
            int baseXpReward = section.getInt("base-xp-reward", section.getInt("xp-reward", 0));

            String diffStr = section.getString("difficulty", "easy").toUpperCase();
            BattlePassTask.TaskDifficulty difficulty;
            try {
                difficulty = BattlePassTask.TaskDifficulty.valueOf(diffStr);
            } catch (IllegalArgumentException e) {
                difficulty = BattlePassTask.TaskDifficulty.EASY;
            }

            float difficultyMultiplier = (float) section.getDouble("difficulty-multiplier", difficulty.multiplier());
            List<TaskCondition> conditions = TaskConditionParser.parseList(section.getConfigurationSection("conditions"));
            IncrementStrategy incrementStrategy = IncrementStrategyParser.parse(section.getConfigurationSection("increment-strategy"));
            int weight = section.getInt("weight", 1);

            return new BattlePassTask(
                taskId, displayName, description, category, difficulty,
                eventTopic, requiredCount, baseXpReward, difficultyMultiplier,
                conditions, incrementStrategy, weight
            );
        }

        public List<BattlePassTask> all() {
            List<BattlePassTask> all = new ArrayList<>();
            all.addAll(daily);
            all.addAll(weekly);
            all.addAll(season);
            return Collections.unmodifiableList(all);
        }
    }

    public record BattlePassUiConfiguration(
        boolean registerOnEnable,
        String mainId,
        String tasksId
    ) {
        public static BattlePassUiConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new BattlePassUiConfiguration(
                section.getBoolean("register-on-enable", true),
                section.getString("main-id", "battlepass_main"),
                section.getString("tasks-id", "battlepass_tasks")
            );
        }
    }

    public record BattlePassCrossServerConfiguration(
        boolean enabled,
        List<String> syncFields
    ) {
        public static BattlePassCrossServerConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            List<String> fields = section.getStringList("sync-fields");
            if (fields.isEmpty()) {
                fields = List.of("progress", "task-progress", "claimed-rewards");
            }
            return new BattlePassCrossServerConfiguration(
                section.getBoolean("enabled", false),
                Collections.unmodifiableList(fields)
            );
        }
    }
}
