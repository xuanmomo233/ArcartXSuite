package xuanmo.arcartxsuite.tab.config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

public record TabModuleConfiguration(
    int refreshIntervalTicks,
    boolean debug,
    boolean registerUiOnEnable,
    boolean overwriteUiFile,
    String serverId,
    boolean crossServerDefault,
    long staleSnapshotMs,
    int batchWindowTicks,
    long leaveGraceMs,
    boolean dryRun,
    TabStyleConfiguration style,
    CrossServerChannelConfig crossServer,
    List<TabDefinition> definitions
) {

    private static final TabClientRefreshGuardConfiguration DEFAULT_CLIENT_REFRESH_GUARD =
        new TabClientRefreshGuardConfiguration(
            true,
            1500L,
            1,
            ClientPacketGuardMode.SILENT,
            "&cTAB 刷新过快，请稍后再试。",
            3000L
        );

    public static TabModuleConfiguration load(FileConfiguration configuration, File tabsDirectory, Logger logger) {
        int refreshIntervalTicks = Math.max(1, configuration.getInt("settings.refresh-interval-ticks", 20));
        boolean debug = configuration.getBoolean("settings.debug", false);
        boolean registerUiOnEnable = configuration.getBoolean("settings.register-ui-on-enable", true);
        boolean overwriteUiFile = configuration.getBoolean("settings.overwrite-ui-file", false);
        String serverId = nullToEmpty(configuration.getString("settings.server-id", "default")).trim();
        if (serverId.isBlank()) {
            serverId = "default";
        }
        boolean crossServerDefault = configuration.getBoolean("settings.cross-server", false);
        long staleSnapshotMs = Math.max(5000L, configuration.getLong("settings.stale-snapshot-ms", 30000L));
        int batchWindowTicks = Math.max(0, configuration.getInt("settings.batch.window-ticks", 0));
        long leaveGraceMs = Math.max(0L, configuration.getLong("settings.leave-grace-ms", 0L));
        TabStyleConfiguration style = readStyleConfiguration(configuration.getConfigurationSection("settings.style"));
        boolean dryRun = configuration.getBoolean("settings.debug-tools.dry-run", false);

        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(
            configuration.getConfigurationSection("cross-server")
        );

        Map<String, TabDefinition> definitionMap = new LinkedHashMap<>();
        if (tabsDirectory != null && tabsDirectory.isDirectory()) {
            loadDefinitionsFromDirectory(tabsDirectory, definitionMap, logger);
        }
        if (definitionMap.isEmpty()) {
            logger.warning("tabs 目录为空或不存在，未加载任何 Tab 定义。");
        }
        List<TabDefinition> definitions = List.copyOf(definitionMap.values());

        return new TabModuleConfiguration(
            refreshIntervalTicks, debug, registerUiOnEnable, overwriteUiFile,
            serverId, crossServerDefault, staleSnapshotMs, batchWindowTicks, leaveGraceMs,
            dryRun, style, crossServer, definitions
        );
    }

    private static TabDefinition parseDefinition(String id, ConfigurationSection section, Logger logger) {
        List<UiTarget> uiTargets = readUiTargets(section, logger, id);
        if (uiTargets.isEmpty()) {
            logger.warning("ArcartXTab 配置 '" + id + "' 缺少有效的 ui-targets（或 ui-id + packet-handler），已跳过。");
            return null;
        }

        Object packTemplate = readPackNode(section, "pack");
        boolean enabled = section.getBoolean("enabled", true);
        String clientRefreshPacketId = nullToEmpty(section.getString("client-refresh-packet-id", "TAB")).trim();
        String clientRefreshAction = nullToEmpty(section.getString("client-refresh-action", "update")).trim();
        TabClientRefreshGuardConfiguration clientRefreshGuard = readClientRefreshGuard(
            section.getConfigurationSection("client-refresh-guard"),
            logger,
            id
        );
        int maxEntries = section.getInt("max-entries", -1);
        boolean omitBlankValues = section.getBoolean("omit-blank-values", false);
        Boolean crossServer = section.contains("cross-server") ? section.getBoolean("cross-server") : null;

        List<TabSortKey> sortKeys = readSortKeys(section, logger, id);

        ConfigurationSection filterSection = section.getConfigurationSection("filters");
        List<TabFilterRule> includeFilters = readFilterRules(filterSection, "include", logger, id);
        List<TabFilterRule> excludeFilters = readFilterRules(filterSection, "exclude", logger, id);
        boolean hideVanished = filterSection != null && filterSection.getBoolean("hide-vanished", false);

        ConfigurationSection pinnedSection = section.getConfigurationSection("pinned");
        List<TabFilterRule> pinnedTop = readFilterRules(pinnedSection, "top", logger, id);
        List<TabFilterRule> pinnedBottom = readFilterRules(pinnedSection, "bottom", logger, id);

        String view = nullToEmpty(section.getString("view", "default")).trim();
        TabGroupingConfiguration grouping = readGrouping(section.getConfigurationSection("grouping"));
        TabPaginationConfiguration pagination = readPagination(section.getConfigurationSection("pagination"));
        TabAggregateConfiguration aggregate = readAggregate(section.getConfigurationSection("aggregate"));

        return new TabDefinition(
            id,
            enabled,
            uiTargets,
            clientRefreshPacketId,
            clientRefreshAction,
            clientRefreshGuard,
            maxEntries,
            sortKeys,
            includeFilters,
            excludeFilters,
            hideVanished,
            pinnedTop,
            pinnedBottom,
            omitBlankValues,
            packTemplate,
            crossServer,
            view,
            grouping,
            pagination,
            aggregate
        );
    }

    private static void loadDefinitionsFromDirectory(
        File directory, Map<String, TabDefinition> target, Logger logger
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                TabDefinition def = parseDefinition(id, yaml, logger);
                if (def != null) {
                    target.put(id, def);
                }
            } catch (Exception e) {
                logger.warning("ArcartXTab 加载 Tab 定义文件失败: " + file.getName() + " | " + e.getMessage());
            }
        }
    }

    /**
     * 读取 ui-targets 列表；若不存在则回退到旧字段 ui-id + packet-handler 构建单个 target。
     * <p>
     * 支持两种配置格式：
     * <pre>
     * # 旧格式（向后兼容）
     * ui-id: "tab"
     * packet-handler: "tab"
     *
     * # 新格式（多 UI 目标，每个可自定义 handler）
     * ui-targets:
     *   - ui-id: "tab"
     *     packet-handler: "tab"
     *   - ui-id: "tab-arena"
     *     packet-handler: "arena"
     * </pre>
     */
    private static List<UiTarget> readUiTargets(ConfigurationSection section, Logger logger, String tabId) {
        List<?> rawList = section.getList("ui-targets");
        if (rawList != null && !rawList.isEmpty()) {
            List<UiTarget> targets = new ArrayList<>();
            for (int index = 0; index < rawList.size(); index++) {
                Object raw = rawList.get(index);
                if (!(raw instanceof Map<?, ?> mapNode)) {
                    logger.warning("ArcartXTab 配置 '" + tabId + "' ui-targets[" + index + "] 不是字典，已跳过。");
                    continue;
                }
                String uiId = asString(mapNode.get("ui-id"), "").trim();
                String handler = asString(mapNode.get("packet-handler"), "").trim();
                if (uiId.isBlank() || handler.isBlank()) {
                    logger.warning("ArcartXTab 配置 '" + tabId + "' ui-targets[" + index + "] 缺少 ui-id 或 packet-handler，已跳过。");
                    continue;
                }
                targets.add(new UiTarget(uiId, handler));
            }
            if (!targets.isEmpty()) {
                return List.copyOf(targets);
            }
        }

        // 回退：旧字段 ui-id + packet-handler
        String legacyUiId = nullToEmpty(section.getString("ui-id")).trim();
        String legacyHandler = nullToEmpty(section.getString("packet-handler")).trim();
        if (!legacyUiId.isBlank() && !legacyHandler.isBlank()) {
            return List.of(new UiTarget(legacyUiId, legacyHandler));
        }
        return List.of();
    }

    private static TabStyleConfiguration readStyleConfiguration(ConfigurationSection section) {
        if (section == null) {
            return TabStyleConfiguration.defaults();
        }
        ConfigurationSection pvp = section.getConfigurationSection("pvp-highlight");
        boolean pvpEnabled = pvp != null && pvp.getBoolean("enabled", false);
        long pvpWindowMs = pvp == null ? 5_000L : Math.max(0L, pvp.getLong("window-ms", 5_000L));
        return new TabStyleConfiguration(pvpEnabled, pvpWindowMs);
    }

    private static TabClientRefreshGuardConfiguration readClientRefreshGuard(
        ConfigurationSection section,
        Logger logger,
        String tabId
    ) {
        if (section == null) {
            return DEFAULT_CLIENT_REFRESH_GUARD;
        }

        ClientPacketGuardMode mode = section.contains("mode")
            ? ClientPacketGuardMode.parse(section.getString("mode"), DEFAULT_CLIENT_REFRESH_GUARD.mode())
            : DEFAULT_CLIENT_REFRESH_GUARD.mode();
        if (mode == ClientPacketGuardMode.PUNISH) {
            logger.warning("ArcartXTab 配置 '" + tabId + "' 的 client-refresh-guard.mode 不支持 punish，已回退为 notify。");
            mode = ClientPacketGuardMode.NOTIFY;
        }

        return new TabClientRefreshGuardConfiguration(
            section.getBoolean("enabled", DEFAULT_CLIENT_REFRESH_GUARD.enabled()),
            Math.max(1L, section.getLong("window-ms", DEFAULT_CLIENT_REFRESH_GUARD.windowMs())),
            Math.max(1, section.getInt("max-hits", DEFAULT_CLIENT_REFRESH_GUARD.maxHits())),
            mode,
            section.getString("notify-message", DEFAULT_CLIENT_REFRESH_GUARD.notifyMessage()),
            Math.max(0L, section.getLong("notify-cooldown-ms", DEFAULT_CLIENT_REFRESH_GUARD.notifyCooldownMs()))
        );
    }

    /**
     * 读取 sort-keys；若不存在则回退到旧字段 sort-mode / sort-papi-key / sort-papi-numeric /
     * sort-prem-group / sort-descending，构造单元素 sortKeys，保持向后兼容。
     */
    private static List<TabSortKey> readSortKeys(ConfigurationSection section, Logger logger, String tabId) {
        List<?> rawList = section.getList("sort-keys");
        if (rawList != null && !rawList.isEmpty()) {
            List<TabSortKey> keys = new ArrayList<>();
            for (int index = 0; index < rawList.size(); index++) {
                Object raw = rawList.get(index);
                if (!(raw instanceof Map<?, ?> mapNode)) {
                    logger.warning("ArcartXTab 配置 '" + tabId + "' sort-keys[" + index + "] 不是字典，已跳过。");
                    continue;
                }
                TabSortKey key = parseSortKey(mapNode);
                if (key != null) {
                    keys.add(key);
                }
            }
            if (!keys.isEmpty()) {
                return List.copyOf(keys);
            }
        }

        // 回退：旧字段
        TabSortMode legacyMode = TabSortMode.parse(section.getString("sort-mode", "name"));
        boolean legacyDescending = section.getBoolean("sort-descending", false);
        String legacyPapiKey = nullToEmpty(section.getString("sort-papi-key")).trim();
        boolean legacyPapiNumeric = section.getBoolean("sort-papi-numeric", false);
        List<String> legacyPremGroups = normalizeGroups(section.getStringList("sort-prem-group"));
        return List.of(new TabSortKey(legacyMode, legacyPapiKey, legacyPapiNumeric, legacyPremGroups, legacyDescending));
    }

    private static TabSortKey parseSortKey(Map<?, ?> rawMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        TabSortMode mode = TabSortMode.parse(asString(map.get("mode"), "name"));
        boolean descending;
        Object orderObj = map.get("order");
        if (orderObj != null) {
            descending = "desc".equalsIgnoreCase(String.valueOf(orderObj))
                || "descending".equalsIgnoreCase(String.valueOf(orderObj));
        } else {
            descending = asBoolean(map.get("descending"), false);
        }
        String papiKey = asString(map.get("key"), asString(map.get("papi-key"), "")).trim();
        boolean papiNumeric = asBoolean(map.get("numeric"), asBoolean(map.get("papi-numeric"), false));
        List<String> premGroups = normalizeGroups(asStringList(map.get("prem-group")));
        return new TabSortKey(mode, papiKey, papiNumeric, premGroups, descending);
    }

    /**
     * 通用过滤规则列表读取，用于 filters.include/exclude 与 pinned.top/bottom。
     */
    private static List<TabFilterRule> readFilterRules(
        ConfigurationSection parent,
        String key,
        Logger logger,
        String tabId
    ) {
        if (parent == null) {
            return List.of();
        }
        List<?> rawList = parent.getList(key);
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<TabFilterRule> rules = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object raw = rawList.get(index);
            if (!(raw instanceof Map<?, ?> mapNode)) {
                logger.warning(
                    "ArcartXTab 配置 '" + tabId + "' " + parent.getCurrentPath() + "." + key
                        + "[" + index + "] 不是字典，已跳过。"
                );
                continue;
            }
            TabFilterRule rule = parseFilterRule(mapNode);
            if (rule == null || !rule.isValid()) {
                logger.warning(
                    "ArcartXTab 配置 '" + tabId + "' " + parent.getCurrentPath() + "." + key
                        + "[" + index + "] 必须包含 papi 或 permission，已跳过。"
                );
                continue;
            }
            rules.add(rule);
        }
        return List.copyOf(rules);
    }

    private static TabGroupingConfiguration readGrouping(ConfigurationSection section) {
        if (section == null) {
            return TabGroupingConfiguration.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        String groupByPapi = nullToEmpty(section.getString("group-by-papi")).trim();
        List<String> groupOrder = section.getStringList("group-order");
        Object headerPack = readPackNode(section, "header-pack");
        if (headerPack instanceof String stringHeader && stringHeader.isBlank()) {
            headerPack = null;
        }
        boolean includeUnordered = section.getBoolean("include-unordered", true);
        return new TabGroupingConfiguration(enabled, groupByPapi, groupOrder, headerPack, includeUnordered);
    }

    private static TabPaginationConfiguration readPagination(ConfigurationSection section) {
        if (section == null) {
            return TabPaginationConfiguration.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        int pageSize = Math.max(1, section.getInt("page-size", 80));
        String packetId = nullToEmpty(section.getString("packet-id", "TAB_PAGE")).trim();
        String nextAction = nullToEmpty(section.getString("next-action", "next")).trim();
        String prevAction = nullToEmpty(section.getString("prev-action", "prev")).trim();
        String setAction = nullToEmpty(section.getString("set-action", "set")).trim();
        return new TabPaginationConfiguration(enabled, pageSize, packetId, nextAction, prevAction, setAction);
    }

    private static TabAggregateConfiguration readAggregate(ConfigurationSection section) {
        if (section == null) {
            return TabAggregateConfiguration.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        Object linePack = readPackNode(section, "line-pack");
        return new TabAggregateConfiguration(enabled, linePack);
    }

    private static TabFilterRule parseFilterRule(Map<?, ?> rawMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        String papi = asString(map.get("papi"), "").trim();
        String equalsValue = asString(map.get("equals"), "");
        String permission = asString(map.get("permission"), "").trim();
        boolean invert = asBoolean(map.get("invert"), false);
        return new TabFilterRule(papi, equalsValue, permission, invert);
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> listValue) {
            List<String> result = new ArrayList<>(listValue.size());
            for (Object element : listValue) {
                if (element != null) {
                    result.add(String.valueOf(element));
                }
            }
            return result;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return List.of(stringValue);
        }
        return List.of();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> normalizeGroups(List<String> rawGroups) {
        if (rawGroups == null || rawGroups.isEmpty()) {
            return List.of("default");
        }

        List<String> groups = new ArrayList<>(rawGroups.size());
        for (String rawGroup : rawGroups) {
            String group = nullToEmpty(rawGroup).trim();
            if (!group.isBlank()) {
                groups.add(group.toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (groups.isEmpty()) {
            groups.add("default");
        }
        return List.copyOf(groups);
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
}
