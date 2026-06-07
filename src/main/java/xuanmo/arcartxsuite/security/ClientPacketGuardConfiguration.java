package xuanmo.arcartxsuite.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;
import org.bukkit.configuration.file.FileConfiguration;

public record ClientPacketGuardConfiguration(
    boolean enabled,
    long cleanupIntervalTicks,
    ClientPacketGuardRule defaults,
    Map<String, ClientPacketGuardRule> moduleRules,
    Map<String, Map<String, ClientPacketGuardRule>> actionRules
) {

    private static final String ROOT_PATH = "client-packet-guard";

    private static final ClientPacketGuardRule HARDCODED_DEFAULTS = new ClientPacketGuardRule(
        true,
        1000L,
        20,
        ClientPacketGuardMode.SILENT,
        "&c操作过快，请稍后再试。",
        3000L,
        ""
    );

    public static ClientPacketGuardConfiguration load(FileConfiguration configuration, Logger logger) {
        boolean enabled = configuration.getBoolean(ROOT_PATH + ".enabled", true);
        long cleanupIntervalTicks = Math.max(20L, configuration.getLong(ROOT_PATH + ".cleanup-interval-ticks", 200L));

        ConfigurationSection rootSection = configuration.getConfigurationSection(ROOT_PATH);
        ClientPacketGuardRule defaults = sanitizeRule(
            mergeRule(HARDCODED_DEFAULTS, rootSection == null ? null : rootSection.getConfigurationSection("defaults")),
            logger,
            ROOT_PATH + ".defaults"
        );

        LinkedHashMap<String, ClientPacketGuardRule> moduleRules = buildModuleRules(defaults, rootSection, logger);
        LinkedHashMap<String, Map<String, ClientPacketGuardRule>> actionRules = buildActionRules(defaults, moduleRules, rootSection, logger);

        return new ClientPacketGuardConfiguration(
            enabled,
            cleanupIntervalTicks,
            defaults,
            Map.copyOf(moduleRules),
            Map.copyOf(actionRules)
        );
    }

    public ClientPacketGuardRule resolve(String module, String action) {
        String normalizedModule = normalizeKey(module);
        String normalizedAction = normalizeKey(action);
        Map<String, ClientPacketGuardRule> perActionRules = actionRules.get(normalizedModule);
        if (perActionRules != null) {
            ClientPacketGuardRule actionRule = perActionRules.get(normalizedAction);
            if (actionRule != null) {
                return actionRule;
            }
        }
        ClientPacketGuardRule moduleRule = moduleRules.get(normalizedModule);
        return moduleRule == null ? defaults : moduleRule;
    }

    private static LinkedHashMap<String, ClientPacketGuardRule> buildModuleRules(
        ClientPacketGuardRule defaults,
        ConfigurationSection rootSection,
        Logger logger
    ) {
        LinkedHashMap<String, ClientPacketGuardRule> rules = new LinkedHashMap<>();
        LinkedHashMap<String, ClientPacketGuardRule> hardcodedRules = hardcodedModuleRules();
        ConfigurationSection modulesSection = rootSection == null ? null : rootSection.getConfigurationSection("modules");

        Set<String> moduleIds = new LinkedHashSet<>(hardcodedRules.keySet());
        if (modulesSection != null) {
            moduleIds.addAll(modulesSection.getKeys(false));
        }

        for (String rawModuleId : moduleIds) {
            String moduleId = normalizeKey(rawModuleId);
            if (moduleId.isBlank()) {
                continue;
            }
            ClientPacketGuardRule baseRule = hardcodedRules.getOrDefault(moduleId, defaults);
            ConfigurationSection moduleSection = modulesSection == null ? null : modulesSection.getConfigurationSection(rawModuleId);
            ClientPacketGuardRule rule = sanitizeRule(
                mergeRule(baseRule, moduleSection),
                logger,
                ROOT_PATH + ".modules." + moduleId
            );
            rules.put(moduleId, rule);
        }
        return rules;
    }

    private static LinkedHashMap<String, Map<String, ClientPacketGuardRule>> buildActionRules(
        ClientPacketGuardRule defaults,
        Map<String, ClientPacketGuardRule> moduleRules,
        ConfigurationSection rootSection,
        Logger logger
    ) {
        LinkedHashMap<String, Map<String, ClientPacketGuardRule>> result = new LinkedHashMap<>();
        LinkedHashMap<String, Map<String, ClientPacketGuardRule>> hardcodedRules = hardcodedActionRules();
        ConfigurationSection modulesSection = rootSection == null ? null : rootSection.getConfigurationSection("modules");

        Set<String> moduleIds = new LinkedHashSet<>(hardcodedRules.keySet());
        if (modulesSection != null) {
            moduleIds.addAll(modulesSection.getKeys(false));
        }

        for (String rawModuleId : moduleIds) {
            String moduleId = normalizeKey(rawModuleId);
            if (moduleId.isBlank()) {
                continue;
            }
            LinkedHashMap<String, ClientPacketGuardRule> actionRules = new LinkedHashMap<>();
            Map<String, ClientPacketGuardRule> moduleHardcodedRules = hardcodedRules.getOrDefault(moduleId, Map.of());

            ConfigurationSection moduleSection = modulesSection == null ? null : modulesSection.getConfigurationSection(rawModuleId);
            ConfigurationSection actionsSection = moduleSection == null ? null : moduleSection.getConfigurationSection("actions");

            Set<String> actionIds = new LinkedHashSet<>(moduleHardcodedRules.keySet());
            if (actionsSection != null) {
                actionIds.addAll(actionsSection.getKeys(false));
            }

            for (String rawActionId : actionIds) {
                String actionId = normalizeKey(rawActionId);
                if (actionId.isBlank()) {
                    continue;
                }
                ClientPacketGuardRule baseRule = moduleHardcodedRules.getOrDefault(
                    actionId,
                    moduleRules.getOrDefault(moduleId, defaults)
                );
                ConfigurationSection actionSection = actionsSection == null ? null : actionsSection.getConfigurationSection(rawActionId);
                ClientPacketGuardRule actionRule = sanitizeRule(
                    mergeRule(baseRule, actionSection),
                    logger,
                    ROOT_PATH + ".modules." + moduleId + ".actions." + actionId
                );
                actionRules.put(actionId, actionRule);
            }

            if (!actionRules.isEmpty()) {
                result.put(moduleId, Map.copyOf(actionRules));
            }
        }
        return result;
    }

    private static LinkedHashMap<String, ClientPacketGuardRule> hardcodedModuleRules() {
        LinkedHashMap<String, ClientPacketGuardRule> rules = new LinkedHashMap<>();
        rules.put("title", new ClientPacketGuardRule(true, 1000L, 4, ClientPacketGuardMode.SILENT, HARDCODED_DEFAULTS.notifyMessage(), 3000L, ""));
        rules.put("mail", new ClientPacketGuardRule(true, 1000L, 4, ClientPacketGuardMode.SILENT, HARDCODED_DEFAULTS.notifyMessage(), 3000L, ""));
        rules.put("questgps", new ClientPacketGuardRule(true, 500L, 4, ClientPacketGuardMode.SILENT, HARDCODED_DEFAULTS.notifyMessage(), 3000L, ""));
        rules.put("map", new ClientPacketGuardRule(true, 500L, 4, ClientPacketGuardMode.SILENT, HARDCODED_DEFAULTS.notifyMessage(), 3000L, ""));
        rules.put("pickup", new ClientPacketGuardRule(true, 500L, 8, ClientPacketGuardMode.SILENT, HARDCODED_DEFAULTS.notifyMessage(), 3000L, ""));
        return rules;
    }

    private static LinkedHashMap<String, Map<String, ClientPacketGuardRule>> hardcodedActionRules() {
        LinkedHashMap<String, Map<String, ClientPacketGuardRule>> rules = new LinkedHashMap<>();
        rules.put("title", actionRules(
            actionRule("equip", 1500L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("unequip", 1500L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("unequip_group", 1500L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("unequip_all", 1500L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("hide", 1500L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("unhide", 1500L, 1, ClientPacketGuardMode.NOTIFY)
        ));
        rules.put("mail", actionRules(
            actionRule("compose-send", 5000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("claimall", 5000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("deleteall", 5000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("cdk", 3000L, 1, ClientPacketGuardMode.NOTIFY)
        ));
        rules.put("questgps", actionRules(
            actionRule("accept_quest", 2000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("abandon_quest", 2000L, 1, ClientPacketGuardMode.NOTIFY)
        ));
        rules.put("map", actionRules(
            actionRule("unlock_anchor", 3000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("teleport_anchor", 3000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("create_waypoint", 2000L, 1, ClientPacketGuardMode.NOTIFY),
            actionRule("delete_waypoint", 2000L, 1, ClientPacketGuardMode.NOTIFY)
        ));
        rules.put("announcer", actionRules(actionRule("click", 3000L, 2, ClientPacketGuardMode.NOTIFY)));
        rules.put("eventpacket", actionRules(actionRule("client-packet", 3000L, 2, ClientPacketGuardMode.NOTIFY)));
        rules.put("loginview", actionRules(
            actionRule("login", 3000L, 3, ClientPacketGuardMode.NOTIFY),
            actionRule("register", 5000L, 2, ClientPacketGuardMode.NOTIFY),
            actionRule("change_password", 5000L, 2, ClientPacketGuardMode.NOTIFY),
            actionRule("bypass_enter", 5000L, 2, ClientPacketGuardMode.NOTIFY),
            actionRule("bind_code", 3000L, 3, ClientPacketGuardMode.NOTIFY)
        ));
        rules.put("tab", actionRules(actionRule("refresh", 1500L, 1, ClientPacketGuardMode.SILENT)));
        rules.put("pickup", actionRules(
            actionRule("pick", 1000L, 4, ClientPacketGuardMode.NOTIFY),
            actionRule("scroll_up", 500L, 8, ClientPacketGuardMode.SILENT),
            actionRule("scroll_down", 500L, 8, ClientPacketGuardMode.SILENT),
            actionRule("open_menu", 1000L, 4, ClientPacketGuardMode.SILENT),
            actionRule("close_menu", 1000L, 4, ClientPacketGuardMode.SILENT)
        ));
        return rules;
    }

    private static Map.Entry<String, ClientPacketGuardRule> actionRule(
        String action,
        long windowMs,
        int maxHits,
        ClientPacketGuardMode mode
    ) {
        return Map.entry(
            normalizeKey(action),
            new ClientPacketGuardRule(true, windowMs, maxHits, mode, HARDCODED_DEFAULTS.notifyMessage(), 3000L, "")
        );
    }

    @SafeVarargs
    private static Map<String, ClientPacketGuardRule> actionRules(Map.Entry<String, ClientPacketGuardRule>... entries) {
        LinkedHashMap<String, ClientPacketGuardRule> values = new LinkedHashMap<>();
        for (Map.Entry<String, ClientPacketGuardRule> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    private static ClientPacketGuardRule mergeRule(ClientPacketGuardRule baseRule, ConfigurationSection section) {
        if (section == null) {
            return baseRule;
        }
        boolean enabled = section.contains("enabled") ? section.getBoolean("enabled", baseRule.enabled()) : baseRule.enabled();
        long windowMs = section.contains("window-ms") ? Math.max(1L, section.getLong("window-ms", baseRule.windowMs())) : baseRule.windowMs();
        int maxHits = section.contains("max-hits") ? Math.max(1, section.getInt("max-hits", baseRule.maxHits())) : baseRule.maxHits();
        ClientPacketGuardMode mode = section.contains("mode")
            ? ClientPacketGuardMode.parse(section.getString("mode"), baseRule.mode())
            : baseRule.mode();
        String notifyMessage = section.contains("notify-message")
            ? string(section.getString("notify-message"), "")
            : baseRule.notifyMessage();
        long notifyCooldownMs = section.contains("notify-cooldown-ms")
            ? Math.max(0L, section.getLong("notify-cooldown-ms", baseRule.notifyCooldownMs()))
            : baseRule.notifyCooldownMs();
        String punishCommand = section.contains("punish-command")
            ? string(section.getString("punish-command"), "")
            : baseRule.punishCommand();
        return new ClientPacketGuardRule(enabled, windowMs, maxHits, mode, notifyMessage, notifyCooldownMs, punishCommand);
    }

    private static ClientPacketGuardRule sanitizeRule(
        ClientPacketGuardRule rule,
        Logger logger,
        String path
    ) {
        if (rule.mode() == ClientPacketGuardMode.PUNISH && rule.punishCommand().isBlank()) {
            logger.warning(path + " 配置了 punish 模式但 punish-command 为空，已回退为 notify。");
            return new ClientPacketGuardRule(
                rule.enabled(),
                rule.windowMs(),
                rule.maxHits(),
                ClientPacketGuardMode.NOTIFY,
                rule.notifyMessage(),
                rule.notifyCooldownMs(),
                ""
            );
        }
        return rule;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

