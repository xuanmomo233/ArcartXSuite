package xuanmo.arcartxsuite.onlinerewards.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

/**
 * 在线奖励模块顶层配置。
 * <p>
 * 属于 {@code onlinerewards} 模块，聚合 UI、存储、跨服、签到、阶段奖励、
 * 周期奖励、离线储蓄、全服签到目标等全部子配置，由 {@link #load(FileConfiguration, File, File)}
 * 从主配置文件及附属 sign-in.yml / rewards.yml 解析构建。
 *
 * @param debug                 是否开启调试日志
 * @param clientSyncDelayTicks  客户端菜单同步延迟（tick）
 * @param timezone              跨天判定时区（如 Asia/Shanghai），空串表示使用服务器默认时区
 * @param doneMessage           全部阶段奖励领取完毕时的提示文本
 * @param progressVariableName  进度占位符变量名
 * @param titleVariableName     阶段标题占位符变量名
 * @param ui                    UI 配置
 * @param storage               存储配置
 * @param crossServer           跨服通道配置
 * @param signIn                签到子系统配置
 * @param timeBonusGroups       在线时长倍率权限组列表
 * @param rewards               在线时长阶段奖励列表
 * @param weeklyRewards         周期性周奖励列表
 * @param monthlyRewards        周期性月奖励列表
 * @param offlineSavings        离线储蓄配置
 * @param serverSignInGoal      全服签到目标配置
 * @param timeBasedRewards      时刻奖励列表（指定时刻给在线玩家发奖励）
 */
public record OnlineRewardsModuleConfiguration(
    boolean debug,
    long clientSyncDelayTicks,
    String timezone,
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
    OnlineRewardsServerSignInGoalConfiguration serverSignInGoal,
    List<OnlineRewardsTimeBasedReward> timeBasedRewards
) {

    /**
     * 从主配置文件加载模块配置，签到与奖励附属文件使用默认查找。
     *
     * @param configuration 主配置
     * @return 解析后的模块配置
     */
    public static OnlineRewardsModuleConfiguration load(FileConfiguration configuration) {
        return load(configuration, null, null);
    }

    /**
     * 从主配置文件及附属签到、奖励文件加载模块配置。
     *
     * @param configuration 主配置
     * @param signInFile    签到附属配置文件（sign-in.yml）
     * @param rewardsFile   奖励附属配置文件（rewards.yml）
     * @return 解析后的模块配置
     * @throws IllegalStateException 当签到附属配置缺失时抛出
     */
    public static OnlineRewardsModuleConfiguration load(
        FileConfiguration configuration,
        File signInFile,
        File rewardsFile
    ) {
        OnlineRewardsUiConfiguration ui = new OnlineRewardsUiConfiguration(
            readString(configuration, "ui.packet-id", "AXS_ONLINE_REWARDS"),
            xuanmo.arcartxsuite.api.config.UiIdParser.readUiIds(
                configuration.getConfigurationSection("ui"), "ui-id", "AXS:online_rewards_menu"
            ),
            configuration.getBoolean("ui.register-ui-on-enable", true),
            configuration.getBoolean("ui.overwrite-ui-files", false)
        );

        OnlineRewardsStorageConfiguration storage = OnlineRewardsStorageConfiguration.load(
            configuration.getConfigurationSection("storage")
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(entry);
            IconDefinition icon = loadIconFromMap(entry);
            rewards.add(new OnlineRewardDefinition(Math.max(0, minutes), name, rewardText, commands, mailPresetIds, itemRewards, icon));
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
            readString(configuration, "settings.timezone", ""),
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
            loadServerSignInGoal(configuration.getConfigurationSection("server-sign-in-goal")),
            loadTimeBasedRewards(configuration.getMapList("time-based-rewards"))
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
            readItemRewardsFromSection(external, "base"),
            IconDefinition.load(external, "base.icon"),
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            IconDefinition icon = loadIconFromMap(value);
            rewards.add(new OnlineRewardsPermissionBonusReward(permission, priority, rewardText, commands, mailPresetIds, itemRewards, icon));
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            IconDefinition icon = loadIconFromMap(value);
            rewards.add(new OnlineRewardsHolidayReward(month, day, name, rewardText, commands, mailPresetIds, itemRewards, icon));
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            IconDefinition icon = loadIconFromMap(value);
            rewards.add(new OnlineRewardsMilestoneReward(threshold, rewardText, commands, mailPresetIds, itemRewards, icon));
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            IconDefinition icon = loadIconFromMap(value);
            rewards.add(new OnlineRewardsDayOfMonthReward(day, rewardText, commands, mailPresetIds, itemRewards, icon));
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
        // 新格式: mail.presets（mail 子节下的 presets 列表）
        Object mailObj = entry.get("mail");
        if (mailObj instanceof Map<?, ?> mailMap) {
            List<String> values = toStringList(mailMap.get("presets"));
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    /**
     * 从配置 Map 中解析 icon 图标定义。
     * <p>
     * 配置格式与 {@link IconDefinition#load(ConfigurationSection)} 一致，支持 material / source+id /
     * json / texture / skull-texture / color 等字段。icon 节不存在或无有效图标时返回 null。
     *
     * @param entry 包含 "icon" 键的配置 Map
     * @return 解析后的图标定义，无有效图标时返回 null
     */
    @Nullable
    private static IconDefinition loadIconFromMap(Map<?, ?> entry) {
        Object iconObj = entry.get("icon");
        if (iconObj == null) {
            return null;
        }
        if (iconObj instanceof String str) {
            return IconDefinition.load(str);
        }
        if (!(iconObj instanceof Map<?, ?> iconMap)) {
            return null;
        }
        String material = nullToDefault(iconMap.get("material"), "");
        int amount = Math.max(1, parseInt(iconMap.get("amount"), 1));
        String name = nullToDefault(iconMap.get("name"), "");
        List<String> lore = toStringList(iconMap.get("lore"));
        int customModelData = parseInt(iconMap.get("custom-model-data"), parseInt(iconMap.get("customModelData"), 0));
        String source = nullToDefault(iconMap.get("source"), "");
        String sourceId = nullToDefault(iconMap.get("id"), nullToDefault(iconMap.get("item-id"), ""));
        String mmoType = nullToDefault(iconMap.get("mmo-type"), "");
        String mmoId = nullToDefault(iconMap.get("mmo-id"), "");
        String json = nullToDefault(iconMap.get("json"), "");
        String texture = nullToDefault(iconMap.get("texture"), "");
        String textureUrl = nullToDefault(iconMap.get("texture-url"), nullToDefault(iconMap.get("url"), ""));
        boolean glow = parseBoolean(iconMap.get("glow"), false);
        String skullTexture = nullToDefault(iconMap.get("skull-texture"), nullToDefault(iconMap.get("skullTexture"), ""));
        String color = nullToDefault(iconMap.get("color"), "");
        // nbt 可以是 Map（键值对）或 String（整段 SNBT）
        java.util.Map<String, String> nbt = java.util.Map.of();
        String nbtString = "";
        Object nbtRaw = iconMap.get("nbt");
        if (nbtRaw instanceof Map<?, ?> nbtMap) {
            java.util.Map<String, String> nbtResult = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : nbtMap.entrySet()) {
                if (e.getValue() != null) {
                    nbtResult.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            nbt = nbtResult;
        } else if (nbtRaw != null) {
            nbtString = String.valueOf(nbtRaw);
        }
        IconDefinition icon = new IconDefinition(
            material, amount, name, lore, customModelData,
            source, sourceId, mmoType, mmoId, json,
            texture, textureUrl, nbt, nbtString,
            glow, skullTexture, color
        );
        return icon.hasIcon() ? icon : null;
    }

    /**
     * 从配置 Map 中解析 item-rewards 列表。
     * <p>
     * 配置格式：
     * <pre>
     * item-rewards:
     *   - source: "minecraft"
     *     id: "DIAMOND"
     *     amount: 1
     *   - source: "mythic"
     *     id: "legendary_gem"
     *     amount: 2
     * </pre>
     */
    private static List<OnlineRewardsItemReward> readItemRewards(Map<?, ?> entry) {
        Object raw = entry.get("item-rewards");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<OnlineRewardsItemReward> rewards = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String source = nullToDefault(map.get("source"), "minecraft");
            String id = nullToDefault(map.get("id"), "");
            if (id.isBlank()) continue;
            int amount = Math.max(1, parseInt(map.get("amount"), 1));
            Object nbtRaw = map.get("nbt");
            String nbt = nbtRaw == null ? null : String.valueOf(nbtRaw);
            rewards.add(new OnlineRewardsItemReward(source, id, amount, nbt));
        }
        return List.copyOf(rewards);
    }

    /**
     * 从 ConfigurationSection 中解析 item-rewards 列表（用于签到基础奖励等直接从 section 读取的场景）。
     */
    private static List<OnlineRewardsItemReward> readItemRewardsFromSection(
        org.bukkit.configuration.file.FileConfiguration section, String prefix
    ) {
        List<?> raw = section.getList(prefix + ".item-rewards");
        if (raw == null || raw.isEmpty()) return List.of();
        List<OnlineRewardsItemReward> rewards = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String source = nullToDefault(map.get("source"), "minecraft");
            String id = nullToDefault(map.get("id"), "");
            if (id.isBlank()) continue;
            int amount = Math.max(1, parseInt(map.get("amount"), 1));
            Object nbtRaw = map.get("nbt");
            String nbt = nbtRaw == null ? null : String.valueOf(nbtRaw);
            rewards.add(new OnlineRewardsItemReward(source, id, amount, nbt));
        }
        return List.copyOf(rewards);
    }

    private static List<String> readMailPresetIds(FileConfiguration configuration, String pathPrefix) {
        List<String> values = toStringList(configuration.get(pathPrefix + ".mail.presets"));
        return values.isEmpty() ? List.of() : values;
    }

    private static List<String> readMailPresetIdsFromSection(FileConfiguration section, String prefix) {
        List<String> values = toStringList(section.get(prefix + ".mail.presets"));
        return values.isEmpty() ? List.of() : values;
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            IconDefinition icon = loadIconFromMap(value);
            rewards.add(new OnlineRewardsPeriodicReward(id, minutes, name, rewardText, commands, mailPresetIds, repeat, itemRewards, icon));
            index++;
        }
        return List.copyOf(rewards);
    }

    private static List<OnlineRewardsTimeBasedReward> loadTimeBasedRewards(List<Map<?, ?>> values) {
        List<OnlineRewardsTimeBasedReward> rewards = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> value : values) {
            String id = nullToDefault(value.get("id"), "time-based-" + index);
            boolean enabled = parseBoolean(value.get("enabled"), true);
            String name = nullToDefault(value.get("name"), "时刻奖励");
            String rewardText = nullToDefault(value.get("rewardText"), "");
            List<String> commands = toStringList(value.get("commands"));
            List<String> mailPresetIds = readMailPresetIds(value);
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            String broadcastMessage = nullToDefault(value.get("broadcast"), "");
            // 解析 schedule 节
            ScheduleSpec schedule = null;
            Object scheduleObj = value.get("schedule");
            if (scheduleObj instanceof Map<?, ?> scheduleMap) {
                try {
                    // 借助临时 ConfigurationSection 包装解析
                    org.bukkit.configuration.MemorySection ms = new org.bukkit.configuration.MemoryConfiguration();
                    for (Map.Entry<?, ?> entry : scheduleMap.entrySet()) {
                        ms.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    schedule = ScheduleSpec.load(ms);
                } catch (Exception ignored) {
                    // schedule 配置非法时跳过该奖励
                }
            }
            if (schedule != null) {
                rewards.add(new OnlineRewardsTimeBasedReward(
                    id, enabled, schedule, name, rewardText,
                    commands, mailPresetIds, itemRewards, broadcastMessage
                ));
            }
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
            List<OnlineRewardsItemReward> itemRewards = readItemRewards(value);
            targets.add(new OnlineRewardsServerSignInGoalTarget(id, required, name, rewardText, commands, mailPresetIds, chatCardIds, subtitleGroupIds, titleIds, broadcastMessage, itemRewards));
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
