package xuanmo.arcartxsuite.eventpacket.config;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;
import xuanmo.arcartxsuite.eventpacket.service.EntityCleanupService;
import xuanmo.arcartxsuite.eventpacket.service.ScheduledCommandService;

public record PluginConfiguration(
    boolean debug,
    long refreshIntervalTicks,
    EventPacketStorageConfiguration storage,
    List<EventPacketRule> rules,
    String clientPacketId,
    int clientPacketPresetCount,
    EntityCleanupService.CleanupConfiguration entityCleanup,
    List<ScheduledCommandService.ScheduledTask> scheduledCommands
) {

    public static PluginConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static PluginConfiguration load(
        FileConfiguration configuration,
        Logger logger,
        File clientPacketPresetsDirectory
    ) {
        return load(configuration, logger, clientPacketPresetsDirectory, null);
    }

    public static PluginConfiguration load(
        FileConfiguration configuration,
        Logger logger,
        File clientPacketPresetsDirectory,
        File rulesDirectory
    ) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        long refreshIntervalTicks = Math.max(1L, configuration.getLong("settings.refresh-interval-ticks", 20L));
        Map<String, EventPacketRule> ruleMap = new LinkedHashMap<>();
        if (rulesDirectory != null && rulesDirectory.isDirectory()) {
            loadRulesFromDirectory(rulesDirectory, ruleMap, logger);
        }
        List<EventPacketRule> rules = new ArrayList<>(ruleMap.values());

        String clientPacketId = "";
        int clientPacketPresetCount = 0;
        ConfigurationSection clientPacketSection = configuration.getConfigurationSection("packet-command");
        if (clientPacketSection != null && clientPacketSection.getBoolean("enabled", true)) {
            clientPacketId = nullToEmpty(clientPacketSection.getString("packet-id", "ArcartXEventPacket")).trim();
            if (clientPacketId.isBlank()) {
                clientPacketId = "ArcartXEventPacket";
            }
            List<EventPacketRule> presetRules = loadClientPacketPresets(
                clientPacketPresetsDirectory, clientPacketId, logger
            );
            clientPacketPresetCount = presetRules.size();
            rules.addAll(presetRules);
        }

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        EventPacketStorageConfiguration storage = new EventPacketStorageConfiguration(
            EventPacketPersistenceDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "eventpacket.db" : nullToEmpty(storageSection.getString("sqlite.file", "eventpacket.db")).trim(),
            storageSection == null ? "127.0.0.1" : nullToEmpty(storageSection.getString("mysql.host", "127.0.0.1")).trim(),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : nullToEmpty(storageSection.getString("mysql.database", "arcartxsuite")).trim(),
            storageSection == null ? "root" : nullToEmpty(storageSection.getString("mysql.username", "root")).trim(),
            storageSection == null ? "" : nullToEmpty(storageSection.getString("mysql.password", "")).trim(),
            storageSection == null ? 2 : Math.max(1, storageSection.getInt("pool-size", 2))
        );

        if (rules.isEmpty()) {
            logger.warning("ArcartXEventPacket 未找到任何规则定义（rules 段和目录均为空）。");
        }

        EntityCleanupService.CleanupConfiguration entityCleanup =
            EntityCleanupService.CleanupConfiguration.fromSection(
                configuration.getConfigurationSection("entity-cleanup"));

        List<ScheduledCommandService.ScheduledTask> scheduledTasks = new ArrayList<>();
        ConfigurationSection schedSection = configuration.getConfigurationSection("scheduled-commands");
        if (schedSection != null) {
            for (String key : schedSection.getKeys(false)) {
                scheduledTasks.add(ScheduledCommandService.ScheduledTask.fromSection(
                    key, schedSection.getConfigurationSection(key)));
            }
        }

        return new PluginConfiguration(
            debug, refreshIntervalTicks, storage, List.copyOf(rules),
            clientPacketId, clientPacketPresetCount, entityCleanup, List.copyOf(scheduledTasks)
        );
    }

    public int enabledRuleCount() {
        int enabled = 0;
        for (EventPacketRule rule : rules) {
            if (rule.enabled()) {
                enabled++;
            }
        }
        return enabled;
    }

    public int papiPacketCount() {
        int count = 0;
        for (EventPacketRule rule : rules) {
            if (rule.enabled() && rule.papiTrigger() && !rule.placeholder().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static EventPacketRule loadRule(String id, ConfigurationSection section, Logger logger) {
        if (section == null) {
            return null;
        }
        EventPacketTrigger trigger = EventPacketTrigger.parse(section.getString("trigger", "join"));
        if (trigger == null) {
            logger.warning("EventPacket 规则触发器无效，已跳过: " + id);
            return null;
        }
        List<EventPacketAction> actions = loadActions(section, logger, id);
        List<ScriptCondition> conditions = loadConditions(section, logger, id);
        if (actions.isEmpty()) {
            logger.warning("EventPacket 规则没有动作，已跳过: " + id);
            return null;
        }
        String script = nullToEmpty(section.getString("script")).trim();
        if (trigger.scriptTrigger() && script.isBlank()) {
            logger.warning("EventPacket 脚本触发器规则缺少 script 字段，已跳过: " + id);
            return null;
        }
        return new EventPacketRule(
            id,
            section.getBoolean("enabled", true),
            trigger,
            nullToEmpty(section.getString("signal")).trim(),
            nullToEmpty(section.getString("placeholder")).trim(),
            decimal(section.get("threshold")),
            section.getBoolean("require-non-empty", false),
            Math.max(1, section.getInt("count", 1)),
            normalizeSet(section.getStringList("worlds")),
            normalizeSet(section.getStringList("entity-types")),
            normalizeSet(section.getStringList("mythic-mob-ids")),
            section.getBoolean("repeatable", false),
            parseCooldownMillis(section.get("cooldown")),
            conditions,
            actions,
            "",
            script,
            "",
            false,
            ""
        );
    }

    private static void loadRulesFromDirectory(
        File directory, Map<String, EventPacketRule> target, Logger logger
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String id : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(id);
                    if (section == null) continue;
                    EventPacketRule rule = loadRule(id, section, logger);
                    if (rule != null) {
                        target.put(id, rule);
                    }
                }
            } catch (Exception e) {
                logger.warning("EventPacket 加载规则文件失败: " + file.getName() + " | " + e.getMessage());
            }
        }
    }

    private static List<EventPacketAction> loadActions(ConfigurationSection section, Logger logger, String ruleId) {
        List<Map<?, ?>> rawActions = section.getMapList("actions");
        if (rawActions.isEmpty() && section.isConfigurationSection("action")) {
            ConfigurationSection actionSection = section.getConfigurationSection("action");
            rawActions = actionSection == null ? List.of() : List.of(convertSection(actionSection));
        }
        List<EventPacketAction> actions = new ArrayList<>();
        for (int index = 0; index < rawActions.size(); index++) {
            Map<?, ?> rawAction = rawActions.get(index);
            String type = nullToEmpty(String.valueOf(rawAction.get("type"))).trim().toLowerCase(Locale.ROOT);
            if (type.isBlank()) {
                logger.warning("EventPacket 规则动作缺少 type，已跳过: " + ruleId + "[" + index + "]");
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawAction.entrySet()) {
                values.put(String.valueOf(entry.getKey()), normalizeNode(entry.getValue()));
            }
            actions.add(new EventPacketAction(type, values));
        }
        return List.copyOf(actions);
    }

    private static List<ScriptCondition> loadConditions(ConfigurationSection section, Logger logger, String ruleId) {
        return ScriptConditionsLoader.loadModuleConditions(
            section,
            logger,
            "EventPacket 规则条件格式无效，已跳过: " + ruleId + " -> "
        );
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return new BigDecimal(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long parseCooldownMillis(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue() * 1000L);
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return 0L;
        }
        String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Math.max(0L, Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Math.max(0L, Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1000L);
            }
            if (normalized.endsWith("m")) {
                return Math.max(0L, Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60_000L);
            }
            if (normalized.endsWith("h")) {
                return Math.max(0L, Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 3_600_000L);
            }
            if (normalized.endsWith("d")) {
                return Math.max(0L, Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 86_400_000L);
            }
            return Math.max(0L, Long.parseLong(normalized) * 1000L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Object readPackNode(ConfigurationSection section, String path) {
        if (section.isConfigurationSection(path)) {
            ConfigurationSection childSection = section.getConfigurationSection(path);
            return childSection == null ? "" : convertSection(childSection);
        }
        return normalizeNode(section.get(path));
    }

    private static Object normalizeNode(Object node) {
        if (node == null) {
            return "";
        }

        if (node instanceof ConfigurationSection configurationSection) {
            return convertSection(configurationSection);
        }

        if (node instanceof List<?> listNode) {
            List<Object> normalized = new ArrayList<>(listNode.size());
            for (Object entry : listNode) {
                normalized.add(normalizeNode(entry));
            }
            return normalized;
        }

        if (node instanceof Map<?, ?> mapNode) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapNode.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeNode(entry.getValue()));
            }
            return normalized;
        }

        return node;
    }

    private static Map<String, Object> convertSection(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key, normalizeNode(section.get(key)));
        }
        return values;
    }

    private static List<EventPacketRule> loadClientPacketPresets(
        File presetsDirectory,
        String packetId,
        Logger logger
    ) {
        List<EventPacketRule> rules = new ArrayList<>();
        if (presetsDirectory == null || !presetsDirectory.exists() || !presetsDirectory.isDirectory()) {
            return rules;
        }
        File[] files = presetsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            return rules;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            for (String key : configuration.getKeys(false)) {
                ConfigurationSection section = configuration.getConfigurationSection(key);
                if (section == null) {
                    logger.warning("EventPacket 客户端回包预设文件 " + file.getName() + " 的节点 " + key + " 不是配置节，已跳过。");
                    continue;
                }
                try {
                    String executorType = nullToEmpty(section.getString("type", "op")).trim().toLowerCase(Locale.ROOT);
                    List<String> commands = List.copyOf(section.getStringList("commands"));
                    if (commands.isEmpty()) {
                        continue;
                    }
                    String permission = nullToEmpty(section.getString("permission")).trim();
                    boolean allowArgs = section.getBoolean("allow-args", false);
                    String argsPattern = nullToEmpty(section.getString("args-pattern", "[\\w.:-]{1,64}")).trim();
                    long cooldownMillis = parseCooldownMillis(section.get("cooldown"));
                    List<EventPacketAction> actions = new ArrayList<>();
                    for (String command : commands) {
                        Map<String, Object> actionValues = new LinkedHashMap<>();
                        actionValues.put(
                            "command",
                            command
                                .replace("<player>", "{player_name}")
                                .replace("<uuid>", "{player_uuid}")
                                .replace("<world>", "{player_world}")
                        );
                        actionValues.put("executor", executorType);
                        actions.add(new EventPacketAction("command.dispatch", actionValues));
                    }
                    rules.add(new EventPacketRule(
                        "pcmd@" + key,
                        true,
                        EventPacketTrigger.CLIENT_PACKET,
                        key,
                        "",
                        null,
                        false,
                        1,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        true,
                        cooldownMillis,
                        List.of(),
                        List.copyOf(actions),
                        packetId,
                        "",
                        permission,
                        allowArgs,
                        argsPattern
                    ));
                } catch (IllegalArgumentException exception) {
                    logger.warning(
                        "EventPacket 客户端回包预设 " + key + " 解析失败("
                            + file.getName()
                            + "): "
                            + exception.getMessage()
                    );
                }
            }
        }
        return rules;
    }

    public List<EventPacketRule> clientPacketRules() {
        List<EventPacketRule> result = new ArrayList<>();
        for (EventPacketRule rule : rules) {
            if (rule.isClientPacketTrigger() && rule.enabled()) {
                result.add(rule);
            }
        }
        return result;
    }

    public EventPacketRule findClientPacketRule(String packetId, String presetId) {
        if (packetId == null || presetId == null) {
            return null;
        }
        for (EventPacketRule rule : rules) {
            if (!rule.isClientPacketTrigger() || !rule.enabled()) {
                continue;
            }
            if (packetId.equalsIgnoreCase(rule.clientPacketId()) && presetId.equals(rule.signal())) {
                return rule;
            }
        }
        return null;
    }
}
