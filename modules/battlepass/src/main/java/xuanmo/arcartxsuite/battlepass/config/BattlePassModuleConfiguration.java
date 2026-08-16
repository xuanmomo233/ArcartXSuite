package xuanmo.arcartxsuite.battlepass.config;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.battlepass.condition.TaskCondition;
import xuanmo.arcartxsuite.battlepass.condition.TaskConditionParser;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategy;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategyParser;
import xuanmo.arcartxsuite.battlepass.model.BattlePassItemDefinition;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;

/**
 * BattlePass 模块配置根 record，聚合存储、赛季、任务、奖励、UI、跨服等全部子配置。
 * <p>
 * 通过 {@link #load} 从 {@link FileConfiguration} 一次性解析所有配置节，
 * 内部嵌套多个子 record 分别对应不同配置区段。
 *
 * @param storage     存储配置（SQLite/MySQL 连接参数）
 * @param season      赛季配置（赛季 ID、等级上限、XP 规则、起止日期）
 * @param tasks       任务配置（每日/每周/赛季任务列表及分配数量）
 * @param rewardsFile 奖励配置文件名（相对模块数据目录）
 * @param rewards     奖励列表（按等级与档位组织的不可变列表）
 * @param ui          UI 配置（注册开关与 UI ID）
 * @param crossServer 跨服配置（启用开关与同步字段列表）
 */
public record BattlePassModuleConfiguration(
    BattlePassStorageConfiguration storage,
    BattlePassSeasonConfiguration season,
    BattlePassTasksConfiguration tasks,
    String rewardsFile,
    List<BattlePassReward> rewards,
    BattlePassUiConfiguration ui,
    BattlePassCrossServerConfiguration crossServer
) {

    /**
     * 从 YAML 配置加载并组装完整的模块配置。
     *
     * @param yaml       主配置文件
     * @param dataFolder 模块数据目录（用于加载外部任务和奖励文件），可为 null
     * @param logger     日志记录器
     * @return 解析后的配置实例
     */
    public static BattlePassModuleConfiguration load(FileConfiguration yaml, @Nullable File dataFolder, Logger logger) {
        String rewardsFile = yaml.getString("rewards-file", "rewards.yml");
        return new BattlePassModuleConfiguration(
            BattlePassStorageConfiguration.load(yaml.getConfigurationSection("storage")),
            BattlePassSeasonConfiguration.load(yaml.getConfigurationSection("season")),
            BattlePassTasksConfiguration.load(yaml.getConfigurationSection("tasks"), dataFolder, logger),
            rewardsFile,
            loadRewards(rewardsFile, dataFolder, logger),
            BattlePassUiConfiguration.load(yaml.getConfigurationSection("ui")),
            BattlePassCrossServerConfiguration.load(yaml.getConfigurationSection("cross-server"))
        );
    }

    private static List<BattlePassReward> loadRewards(String rewardsFile, @Nullable File dataFolder, Logger logger) {
        List<BattlePassReward> result = new ArrayList<>();
        if (dataFolder == null) return result;
        File file = new File(dataFolder, rewardsFile);
        if (!file.exists()) {
            if (logger != null) {
                logger.warning("未找到奖励配置文件: " + file.getPath() + "，奖励列表为空");
            }
            return result;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("rewards");
            if (section == null) section = yaml; // 允许顶层直接写奖励
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
            if (logger != null) {
                logger.fine("已从外部文件加载战令奖励: " + file.getName() + " (" + result.size() + " 条)");
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.WARNING, "加载奖励配置文件失败: " + file.getPath(), e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static BattlePassReward parseReward(String rewardId, int level, BattlePassReward.RewardTier tier, ConfigurationSection section) {
        String type = section.getString("type", "command");
        IconDefinition icon = IconDefinition.load(section, "icon");
        BattlePassItemDefinition item = parseItemDefinition(section.getConfigurationSection("item"));
        List<String> commands = Collections.unmodifiableList(section.getStringList("commands"));
        String currency = section.getString("currency", "");
        double currencyAmount = section.getDouble("amount", 0);
        return new BattlePassReward(rewardId, level, tier, type, icon, item,
            commands, currency, currencyAmount);
    }

    private static @Nullable BattlePassItemDefinition parseItemDefinition(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        String source = section.getString("source", "minecraft");
        String id = section.getString("id", "");
        int amount = section.getInt("amount", 1);
        String nbt = section.getString("nbt", "");
        if (id.isBlank()) return null;
        return new BattlePassItemDefinition(source, id, amount, nbt.isBlank() ? null : nbt);
    }

    /**
     * 存储配置 record，描述 SQLite/MySQL 连接参数。
     *
     * @param mode           存储模式（sqlite / mysql）
     * @param sqliteFileName SQLite 文件名
     * @param poolSize       连接池大小
     * @param mysqlHost      MySQL 主机地址
     * @param mysqlPort      MySQL 端口
     * @param mysqlDatabase  MySQL 数据库名
     * @param mysqlUsername  MySQL 用户名
     * @param mysqlPassword  MySQL 密码
     */
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
        /**
         * 从配置节加载存储配置。
         *
         * @param section 存储配置节，为 null 时使用默认值
         * @return 存储配置实例
         */
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

        /**
         * 将存储配置转换为 {@link StorageDescriptor}。
         *
         * @return 对应的存储描述符
         */
        public StorageDescriptor toDescriptor() {
            boolean isMysql = "mysql".equalsIgnoreCase(mode);
            if (isMysql) {
                return StorageDescriptor.mysql(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, "");
            }
            return StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         *
         * @return {@code true} 表示模块应自建连接池（mode=mysql）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return "mysql".equalsIgnoreCase(mode);
        }
    }

    /**
     * 赛季配置 record，定义当前赛季的基本参数与时间范围。
     *
     * @param seasonId    赛季唯一标识
     * @param displayName 赛季显示名称
     * @param maxLevel    赛季最大等级
     * @param xpPerLevel  每级所需经验值
     * @param startDate   赛季开始日期
     * @param endDate     赛季结束日期
     */
    public record BattlePassSeasonConfiguration(
        String seasonId,
        String displayName,
        int maxLevel,
        int xpPerLevel,
        LocalDate startDate,
        LocalDate endDate
    ) {
        /**
         * 从配置节加载赛季配置。
         *
         * @param section 赛季配置节，为 null 时使用默认值
         * @return 赛季配置实例
         */
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

    /**
     * 任务配置 record，包含每日/每周/赛季三类任务模板及分配数量。
     *
     * @param daily       每日任务模板列表
     * @param weekly      每周任务模板列表
     * @param season      赛季任务模板列表
     * @param dailyCount  每日分配任务数量
     * @param weeklyCount 每周分配任务数量
     */
    public record BattlePassTasksConfiguration(
        List<BattlePassTask> daily,
        List<BattlePassTask> weekly,
        List<BattlePassTask> season,
        int dailyCount,
        int weeklyCount
    ) {
        /**
         * 从配置节加载任务配置，优先从外部任务文件读取。
         *
         * @param section    任务配置节，为 null 时使用默认值
         * @param dataFolder 模块数据目录，用于定位外部任务文件
         * @param logger     日志记录器
         * @return 任务配置实例
         */
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
            // 从外部任务文件读取
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

            // 解析 bukkit-event 降级配置
            BattlePassTask.BukkitEventSource bukkitEvent = parseBukkitEvent(section.getConfigurationSection("bukkit-event"));

            return new BattlePassTask(
                taskId, displayName, description, category, difficulty,
                eventTopic, bukkitEvent, requiredCount, baseXpReward, difficultyMultiplier,
                conditions, incrementStrategy, weight
            );
        }

        /**
         * 解析 bukkit-event 降级配置段。
         * <p>
         * 配置格式：
         * <pre>
         * bukkit-event:
         *   event-class: "org.bukkit.event.entity.EntityDeathEvent"
         *   payload-fields:
         *     entityType: "victim.type.name"
         *     entityName: "victim.name"
         * </pre>
         */
        private static BattlePassTask.BukkitEventSource parseBukkitEvent(@Nullable ConfigurationSection section) {
            if (section == null) return null;
            String eventClass = section.getString("event-class", "");
            if (eventClass.isEmpty()) return null;
            ConfigurationSection fieldsSection = section.getConfigurationSection("payload-fields");
            java.util.Map<String, String> payloadFields = new java.util.LinkedHashMap<>();
            if (fieldsSection != null) {
                for (String key : fieldsSection.getKeys(false)) {
                    String expr = fieldsSection.getString(key, "");
                    if (!expr.isEmpty()) {
                        payloadFields.put(key, expr);
                    }
                }
            }
            return new BattlePassTask.BukkitEventSource(eventClass, payloadFields);
        }

        /**
         * 返回所有任务模板的合并列表（每日 + 每周 + 赛季）。
         *
         * @return 不可变的全部任务列表
         */
        public List<BattlePassTask> all() {
            List<BattlePassTask> all = new ArrayList<>();
            all.addAll(daily);
            all.addAll(weekly);
            all.addAll(season);
            return Collections.unmodifiableList(all);
        }
    }

    /**
     * UI 配置 record，控制模块 UI 的注册行为与 UI ID。
     *
     * @param registerOnEnable 启用时是否自动注册 UI
     * @param overwriteUiFiles 是否强制覆盖用户已修改的 UI 文件
     * @param mainId           主界面 UI ID
     * @param tasksId          任务界面 UI ID
     * @param rewardsId        奖励界面 UI ID
     */
    public record BattlePassUiConfiguration(
        boolean registerOnEnable,
        boolean overwriteUiFiles,
        String mainId,
        String tasksId,
        String rewardsId
    ) {
        public static BattlePassUiConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new BattlePassUiConfiguration(
                section.getBoolean("register-on-enable", true),
                section.getBoolean("overwrite-ui-files", false),
                section.getString("main-id", "battlepass_main"),
                section.getString("tasks-id", "battlepass_tasks"),
                section.getString("rewards-id", "battlepass_rewards")
            );
        }
    }

    /**
     * 跨服配置 record，控制跨服数据同步行为。
     *
     * @param enabled    是否启用跨服同步
     * @param syncFields 需同步的字段列表（progress / task-progress / claimed-rewards）
     */
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
