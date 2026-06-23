package xuanmo.arcartxsuite.onlinerewards.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;

public record OnlineRewardsModuleConfiguration(
    boolean debug,
    long clientSyncDelayTicks,
    String doneMessage,
    String progressVariableName,
    String titleVariableName,
    OnlineRewardsUiConfiguration ui,
    OnlineRewardsStorageConfiguration storage,
    CrossServerChannelConfig crossServer,
    OnlineRewardsSignInConfiguration signIn,
    List<OnlineRewardsTimeBonusGroup> timeBonusGroups,
    List<OnlineRewardDefinition> rewards,
    List<OnlineRewardsPeriodicReward> weeklyRewards,
    List<OnlineRewardsPeriodicReward> monthlyRewards,
    OnlineRewardsOfflineSavingsConfiguration offlineSavings,
    OnlineRewardsServerSignInGoalConfiguration serverSignInGoal
) {

    public static OnlineRewardsModuleConfiguration load(FileConfiguration configuration) {
        return load(configuration, null, null);
    }

    public static OnlineRewardsModuleConfiguration load(
        FileConfiguration configuration,
        File signInFile,
        File rewardsFile
    ) {
        OnlineRewardsUiConfiguration ui = new OnlineRewardsUiConfiguration(
            readString(configuration, "ui.packet-id", "AXS_ONLINE_REWARDS"),
            xuanmo.arcartxsuite.api.config.UiIdParser.readUiIds(
                configuration.getConfigurationSection("ui"), "menu-ui-id", "AXS:online_rewards_menu"
            ),
            configuration.getBoolean("ui.register-ui-on-enable", true),
            configuration.getBoolean("ui.overwrite-ui-files", false)
        );

        OnlineRewardsStorageConfiguration storage = new OnlineRewardsStorageConfiguration(
            OnlineRewardsPersistenceDialect.parse(configuration.getString("storage.mode", "sqlite")),
            readString(configuration, "storage.sqlite.file", "online-rewards.db"),
            readString(configuration, "storage.mysql.host", "127.0.0.1"),
            Math.max(1, configuration.getInt("storage.mysql.port", 3306)),
            readString(configuration, "storage.mysql.database", "arcartxsuite"),
            readString(configuration, "storage.mysql.username", "root"),
            readString(configuration, "storage.mysql.password", ""),
            Math.max(1, configuration.getInt("storage.mysql.pool-size", 2))
        );

        List<Map<?, ?>> rewardsList = (rewardsFile != null && rewardsFile.isFile())
            ? YamlConfiguration.loadConfiguration(rewardsFile).getMapList("rewards")
            : List.of();
        List<OnlineRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> entry : rewardsList) {
            int minutes = parseInt(entry.get("minutes"), 0);
            String name = nullToDefault(entry.get("name"), "阶段奖励");
            String rewardText = nullToDefault(entry.get("rewardText"), "");
            List<String> commands = toStringList(entry.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(entry);
            rewards.add(new OnlineRewardDefinition(Math.max(0, minutes), name, rewardText, commands, mailPresetIds));
        }

        FileConfiguration signInCfg = (signInFile != null && signInFile.isFile())
            ? YamlConfiguration.loadConfiguration(signInFile) : null;
        if (signInCfg == null) {
            throw new IllegalStateException("sign-in.yml 配置文件缺失，请确保外部文件存在。");
        }
        OnlineRewardsSignInConfiguration signIn = loadSignIn(signInCfg);

        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(
            configuration.getConfigurationSection("cross-server")
        );

        return new OnlineRewardsModuleConfiguration(
            configuration.getBoolean("settings.debug", false),
            Math.max(0L, configuration.getLong("settings.client-sync-delay-ticks", 50L)),
            readString(configuration, "messages.done", "§c所有阶段已领取"),
            readString(configuration, "variables.progress", "arcartx_online_time"),
            readString(configuration, "variables.title", "arcartx_online_time_title"),
            ui,
            storage,
            crossServer,
            signIn,
            loadTimeBonusGroups(configuration.getMapList("time-bonus.permission-groups")),
            List.copyOf(rewards),
            loadPeriodicRewards(configuration.getMapList("weekly-rewards")),
            loadPeriodicRewards(configuration.getMapList("monthly-rewards")),
            loadOfflineSavings(configuration.getConfigurationSection("offline-savings")),
            loadServerSignInGoal(configuration.getConfigurationSection("server-sign-in-goal"))
        );
    }

    private static OnlineRewardsSignInConfiguration loadSignIn(FileConfiguration external) {
        return new OnlineRewardsSignInConfiguration(
            external.getBoolean("reminder-on-join", true),
            nullToDefault(external.getString("messages.sign-in-success"), "§a今日签到成功，连续 {streak} 天，累计 {total} 天。"),
            nullToDefault(external.getString("messages.sign-in-repeat"), "§e今天已经签到过了。"),
            nullToDefault(external.getString("messages.sign-in-reminder"), "§e你今天还没有签到，输入 /signin 即可领取奖励。"),
            toStringList(external.get("base-commands")),
            readMailPresetIdsFromSection(external, "base"),
            nullToDefault(external.getString("base-rewardText"), ""),
            loadMakeupFromSection(external),
            loadMilestoneRewards(external.getMapList("streak-rewards"), "days"),
            loadMilestoneRewards(external.getMapList("total-rewards"), "days"),
            loadDayOfMonthRewards(external.getMapList("day-of-month-rewards")),
            loadHolidayRewards(external.getMapList("holiday-rewards")),
            loadPermissionBonusRewards(external.getMapList("permission-bonus-groups"))
        );
    }

    private static List<OnlineRewardsTimeBonusGroup> loadTimeBonusGroups(List<Map<?, ?>> values) {
        List<OnlineRewardsTimeBonusGroup> groups = new ArrayList<>();
        for (Map<?, ?> value : values) {
            String permission = nullToDefault(value.get("permission"), "");
            if (permission.isBlank()) {
                continue;
            }
            double multiplier = Math.max(1.0D, parseDouble(value.get("multiplier"), 1.0D));
            int priority = parseInt(value.get("priority"), 0);
            groups.add(new OnlineRewardsTimeBonusGroup(permission, multiplier, priority));
        }
        return List.copyOf(groups);
    }

    private static List<OnlineRewardsPermissionBonusReward> loadPermissionBonusRewards(List<Map<?, ?>> values) {
        List<OnlineRewardsPermissionBonusReward> rewards = new ArrayList<>();
        for (Map<?, ?> value : values) {
            String permission = nullToDefault(value.get("permission"), "");
            if (permission.isBlank()) {
                continue;
            }
            int priority = parseInt(value.get("priority"), 0);
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            rewards.add(new OnlineRewardsPermissionBonusReward(permission, priority, rewardText, commands, mailPresetIds));
        }
        return List.copyOf(rewards);
    }

    private static List<OnlineRewardsHolidayReward> loadHolidayRewards(List<Map<?, ?>> values) {
        List<OnlineRewardsHolidayReward> rewards = new ArrayList<>();
        for (Map<?, ?> value : values) {
            int month = Math.max(1, Math.min(12, parseInt(value.get("month"), 0)));
            int day = Math.max(1, Math.min(31, parseInt(value.get("day"), 0)));
            String name = nullToDefault(value.get("name"), month + "月" + day + "日");
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            rewards.add(new OnlineRewardsHolidayReward(month, day, name, rewardText, commands, mailPresetIds));
        }
        return List.copyOf(rewards);
    }

    private static List<OnlineRewardsMilestoneReward> loadMilestoneRewards(List<Map<?, ?>> values, String thresholdKey) {
        List<OnlineRewardsMilestoneReward> rewards = new ArrayList<>();
        for (Map<?, ?> value : values) {
            int threshold = Math.max(1, parseInt(value.get(thresholdKey), 0));
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            rewards.add(new OnlineRewardsMilestoneReward(threshold, rewardText, commands, mailPresetIds));
        }
        return List.copyOf(rewards);
    }

    private static List<OnlineRewardsDayOfMonthReward> loadDayOfMonthRewards(List<Map<?, ?>> values) {
        List<OnlineRewardsDayOfMonthReward> rewards = new ArrayList<>();
        for (Map<?, ?> value : values) {
            int day = Math.max(1, Math.min(31, parseInt(value.get("day"), 0)));
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            rewards.add(new OnlineRewardsDayOfMonthReward(day, rewardText, commands, mailPresetIds));
        }
        return List.copyOf(rewards);
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double parseDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(String.valueOf(entry));
        }
        return List.copyOf(result);
    }

    private static List<String> readMailPresetIds(Map<?, ?> entry) {
        List<String> values = toStringList(entry.get("mail-presets"));
        if (!values.isEmpty()) {
            return values;
        }

        String singlePresetId = nullToDefault(entry.get("mail-preset"), "");
        return singlePresetId.isBlank() ? List.of() : List.of(singlePresetId);
    }

    private static List<String> readMailPresetIds(FileConfiguration configuration, String pathPrefix) {
        List<String> values = toStringList(configuration.get(pathPrefix + "-mail-presets"));
        if (!values.isEmpty()) {
            return values;
        }

        String singlePresetId = readString(configuration, pathPrefix + "-mail-preset", "");
        return singlePresetId.isBlank() ? List.of() : List.of(singlePresetId);
    }

    private static List<String> readMailPresetIdsFromSection(FileConfiguration section, String prefix) {
        List<String> values = toStringList(section.get(prefix + "-mail-presets"));
        if (!values.isEmpty()) {
            return values;
        }
        String single = section.getString(prefix + "-mail-preset", "");
        return (single == null || single.isBlank()) ? List.of() : List.of(single.trim());
    }

    private static OnlineRewardsMakeupConfiguration loadMakeupFromSection(FileConfiguration section) {
        return new OnlineRewardsMakeupConfiguration(
            section.getBoolean("makeup.enabled", true),
            nullToDefault(section.getString("makeup.card-name"), "补签卡"),
            nullToDefault(section.getString("makeup.success"), "§a已消耗 1 张{card}补签 {date}，当前剩余 {cards} 张。"),
            nullToDefault(section.getString("makeup.no-card"), "§c你的{card}不足，无法补签。"),
            nullToDefault(section.getString("makeup.invalid-date"), "§c只能补签本月今天之前未签到的日期。"),
            nullToDefault(section.getString("makeup.already-signed"), "§e该日期已经签到过了。")
        );
    }

    private static List<OnlineRewardsPeriodicReward> loadPeriodicRewards(List<Map<?, ?>> values) {
        List<OnlineRewardsPeriodicReward> rewards = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> value : values) {
            String id = nullToDefault(value.get("id"), "reward-" + index);
            int minutes = Math.max(0, parseInt(value.get("minutes"), 0));
            String name = nullToDefault(value.get("name"), "周期奖励");
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            boolean repeat = parseBoolean(value.get("repeat"), false);
            rewards.add(new OnlineRewardsPeriodicReward(id, minutes, name, rewardText, commands, mailPresetIds, repeat));
            index++;
        }
        return List.copyOf(rewards);
    }

    private static OnlineRewardsOfflineSavingsConfiguration loadOfflineSavings(ConfigurationSection section) {
        if (section == null) {
            return new OnlineRewardsOfflineSavingsConfiguration(false, 0, 1.0D, 1);
        }
        return new OnlineRewardsOfflineSavingsConfiguration(
            section.getBoolean("enabled", false),
            Math.max(0, section.getInt("max-minutes", 0)),
            Math.max(0.0D, Math.min(1.0D, section.getDouble("storage-rate", 1.0D))),
            Math.max(1, section.getInt("expire-days", 1))
        );
    }

    private static OnlineRewardsServerSignInGoalConfiguration loadServerSignInGoal(ConfigurationSection section) {
        if (section == null) {
            return new OnlineRewardsServerSignInGoalConfiguration(false, false, List.of());
        }
        List<OnlineRewardsServerSignInGoalTarget> targets = new ArrayList<>();
        List<Map<?, ?>> targetList = section.getMapList("targets");
        int index = 0;
        for (Map<?, ?> value : targetList) {
            String id = nullToDefault(value.get("id"), "goal-" + index);
            int required = Math.max(0, parseInt(value.get("required"), 0));
            String name = nullToDefault(value.get("name"), "全服签到目标");
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            List<String> chatCardIds = toStringList(value.get("chat-cards"));
            List<String> subtitleGroupIds = toStringList(value.get("subtitle-groups"));
            List<String> titleIds = toStringList(value.get("title-ids"));
            String broadcastMessage = nullToDefault(value.get("broadcast"), "");
            targets.add(new OnlineRewardsServerSignInGoalTarget(id, required, name, rewardText, commands, mailPresetIds, chatCardIds, subtitleGroupIds, titleIds, broadcastMessage));
            index++;
        }
        return new OnlineRewardsServerSignInGoalConfiguration(
            section.getBoolean("enabled", false),
            section.getBoolean("broadcast", true),
            List.copyOf(targets)
        );
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string.trim());
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return defaultValue;
    }

    private static String nullToDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String string = String.valueOf(value).trim();
        return string.isBlank() ? defaultValue : string;
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
