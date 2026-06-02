package xuanmo.arcartxsuite.title.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public record TitleModuleConfiguration(
    boolean debug,
    long expirationCleanupIntervalTicks,
    TitleStorageConfiguration storage,
    TitleUiConfiguration ui,
    TitleAttributePlusConfiguration attributePlus,
    TitleMythicLibConfiguration mythicLib,
    TitleCraneAttributeConfiguration craneAttribute,
    TitleDisplayConfiguration displayTitle,
    Map<String, TitleGroupDefinition> groups,
    Map<String, TitleQualityDefinition> qualities,
    Map<String, TitleDefinition> titles,
    Map<String, TitleSetDefinition> sets
) {

    public List<String> displayTitleGroupOrder() {
        if (displayTitle.groups().isEmpty()) {
            return List.copyOf(groups.keySet());
        }
        return displayTitle.groups();
    }

    public static TitleModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static TitleModuleConfiguration load(FileConfiguration configuration, Logger logger, File titlesDirectory) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        long expirationCleanupIntervalTicks = Math.max(20L, configuration.getLong("settings.expiration-cleanup-interval-ticks", 1200L));

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        TitleStorageConfiguration storage = new TitleStorageConfiguration(
            TitlePersistenceDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "titles.db" : nullToEmpty(storageSection.getString("sqlite.file", "titles.db")).trim(),
            storageSection == null ? "127.0.0.1" : nullToEmpty(storageSection.getString("mysql.host", "127.0.0.1")).trim(),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : nullToEmpty(storageSection.getString("mysql.database", "arcartxsuite")).trim(),
            storageSection == null ? "root" : nullToEmpty(storageSection.getString("mysql.username", "root")).trim(),
            storageSection == null ? "" : nullToEmpty(storageSection.getString("mysql.password", "")).trim(),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );

        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        TitleUiConfiguration ui = new TitleUiConfiguration(
            uiSection == null ? "AXS:title_menu" : nullToEmpty(uiSection.getString("ui-id", "AXS:title_menu")).trim(),
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection == null ? "&0" : nullToEmpty(uiSection.getString("attribute-line-color", "&0")),
            uiSection == null ? "-" : nullToEmpty(uiSection.getString("empty-attribute-placeholder", "-"))
        );
        ConfigurationSection attributePlusSection = configuration.getConfigurationSection("attributeplus");
        TitleAttributePlusConfiguration attributePlus = new TitleAttributePlusConfiguration(
            attributePlusSection == null || attributePlusSection.getBoolean("enabled", true),
            attributePlusSection == null ? "AXS_TITLE" : nullToEmpty(attributePlusSection.getString("source-prefix", "AXS_TITLE"))
        );
        ConfigurationSection mythicLibSection = configuration.getConfigurationSection("mythiclib");
        TitleMythicLibConfiguration mythicLib = new TitleMythicLibConfiguration(
            mythicLibSection != null && mythicLibSection.getBoolean("enabled", false),
            mythicLibSection == null ? "AXS_TITLE" : nullToEmpty(mythicLibSection.getString("source-prefix", "AXS_TITLE"))
        );
        ConfigurationSection craneAttributeSection = configuration.getConfigurationSection("craneattribute");
        TitleCraneAttributeConfiguration craneAttribute = new TitleCraneAttributeConfiguration(
            craneAttributeSection != null && craneAttributeSection.getBoolean("enabled", false),
            craneAttributeSection == null ? "AXS_TITLE" : nullToEmpty(craneAttributeSection.getString("source-prefix", "AXS_TITLE"))
        );
        ConfigurationSection displayTitleSection = configuration.getConfigurationSection("display-title");
        List<String> displayGroups = new ArrayList<>();
        if (displayTitleSection != null) {
            for (String rawGroup : displayTitleSection.getStringList("groups")) {
                String normalized = normalizeId(rawGroup);
                if (!normalized.isBlank()) {
                    displayGroups.add(normalized);
                }
            }
        }
        TitleDisplayConfiguration displayTitle = new TitleDisplayConfiguration(
            displayGroups,
            displayTitleSection == null ? " " : nullToEmpty(displayTitleSection.getString("separator", " ")),
            displayTitleSection == null ? "" : nullToEmpty(displayTitleSection.getString("empty-text", ""))
        );

        Map<String, TitleGroupDefinition> groups = loadGroups(configuration.getConfigurationSection("groups"));
        Map<String, TitleQualityDefinition> qualities = loadQualities(configuration.getConfigurationSection("qualities"));
        Map<String, TitleDefinition> titles = new LinkedHashMap<>();
        if (titlesDirectory != null && titlesDirectory.isDirectory()) {
            loadTitlesFromDirectory(titlesDirectory, groups, qualities, logger, titles);
        }
        Map<String, TitleSetDefinition> sets = loadSets(configuration.getConfigurationSection("sets"), titles, logger);

        return new TitleModuleConfiguration(
            debug,
            expirationCleanupIntervalTicks,
            storage,
            ui,
            attributePlus,
            mythicLib,
            craneAttribute,
            displayTitle,
            immutableCopy(groups),
            immutableCopy(qualities),
            immutableCopy(titles),
            immutableCopy(sets)
        );
    }

    public TitleDefinition title(String titleId) {
        return titles.get(titleId);
    }

    public TitleGroupDefinition group(String groupId) {
        return groups.get(groupId);
    }

    public TitleQualityDefinition quality(String qualityId) {
        return qualities.get(qualityId);
    }

    public int enabledTitleCount() {
        return titles.size();
    }

    public List<TitleDefinition> orderedTitles() {
        List<TitleDefinition> ordered = new ArrayList<>(titles.values());
        ordered.sort(
            java.util.Comparator
                .comparingInt((TitleDefinition definition) -> groupSortOrder(definition.groupId()))
                .thenComparingInt(definition -> qualitySortOrder(definition.qualityId()))
                .thenComparingInt(TitleDefinition::sortOrder)
                .thenComparing(TitleDefinition::id, String.CASE_INSENSITIVE_ORDER)
        );
        return List.copyOf(ordered);
    }

    private int groupSortOrder(String groupId) {
        TitleGroupDefinition definition = groups.get(groupId);
        return definition == null ? Integer.MAX_VALUE : definition.sortOrder();
    }

    private int qualitySortOrder(String qualityId) {
        TitleQualityDefinition definition = qualities.get(qualityId);
        return definition == null ? Integer.MAX_VALUE : definition.sortOrder();
    }

    public TitleSetDefinition set(String setId) {
        return sets.get(setId);
    }

    private static Map<String, TitleSetDefinition> loadSets(
        ConfigurationSection section,
        Map<String, TitleDefinition> titles,
        Logger logger
    ) {
        LinkedHashMap<String, TitleSetDefinition> sets = new LinkedHashMap<>();
        if (section == null) {
            return sets;
        }

        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null) {
                continue;
            }

            String id = normalizeId(rawId);
            if (id.isBlank()) {
                logger.warning("ArcartXTitle.yml sets 中存在空套装 ID，已跳过。");
                continue;
            }

            String displayName = nullToEmpty(child.getString("display-name", rawId)).trim();
            List<String> requiredTitleIds = new ArrayList<>();
            for (String rawTitleId : child.getStringList("required-titles")) {
                String normalizedTitleId = normalizeId(rawTitleId);
                if (!normalizedTitleId.isBlank()) {
                    if (!titles.containsKey(normalizedTitleId)) {
                        logger.warning("套装 '" + id + "' 引用了不存在的称号 '" + normalizedTitleId + "'，已忽略该条目。");
                    } else {
                        requiredTitleIds.add(normalizedTitleId);
                    }
                }
            }
            if (requiredTitleIds.isEmpty()) {
                logger.warning("套装 '" + id + "' 没有有效的 required-titles，已跳过。");
                continue;
            }

            int completionThreshold = child.getInt("completion-threshold", -1);
            Map<String, Double> bonusAttributes = readAttributeMap(child.getConfigurationSection("bonus-attributes"), logger, id, "bonus-attributes");
            List<String> bonusAttributeLines = readStringList(child, logger, id, "bonus-attribute-lines");

            sets.put(id, new TitleSetDefinition(
                id,
                displayName.isBlank() ? rawId : displayName,
                requiredTitleIds,
                completionThreshold,
                bonusAttributes,
                bonusAttributeLines
            ));
        }
        return sets;
    }

    private static Map<String, TitleGroupDefinition> loadGroups(ConfigurationSection section) {
        LinkedHashMap<String, TitleGroupDefinition> groups = new LinkedHashMap<>();
        if (section == null) {
            groups.put("default", new TitleGroupDefinition("default", "默认", 0));
            return groups;
        }

        int index = 0;
        for (String rawId : section.getKeys(false)) {
            String id = normalizeId(rawId);
            if (id.isBlank()) {
                continue;
            }
            ConfigurationSection child = section.getConfigurationSection(rawId);
            String name = child == null ? rawId : nullToEmpty(child.getString("name", rawId)).trim();
            int sortOrder = child == null ? index : child.getInt("sort-order", index);
            groups.put(id, new TitleGroupDefinition(id, name.isBlank() ? rawId : name, sortOrder));
            index++;
        }
        if (groups.isEmpty()) {
            groups.put("default", new TitleGroupDefinition("default", "默认", 0));
        }
        return groups;
    }

    private static Map<String, TitleQualityDefinition> loadQualities(ConfigurationSection section) {
        LinkedHashMap<String, TitleQualityDefinition> qualities = new LinkedHashMap<>();
        if (section == null) {
            qualities.put("default", new TitleQualityDefinition("default", "普通", 0));
            return qualities;
        }

        int index = 0;
        for (String rawId : section.getKeys(false)) {
            String id = normalizeId(rawId);
            if (id.isBlank()) {
                continue;
            }
            ConfigurationSection child = section.getConfigurationSection(rawId);
            String name = child == null ? rawId : nullToEmpty(child.getString("name", rawId)).trim();
            int sortOrder = child == null ? index : child.getInt("sort-order", index);
            qualities.put(id, new TitleQualityDefinition(id, name.isBlank() ? rawId : name, sortOrder));
            index++;
        }
        if (qualities.isEmpty()) {
            qualities.put("default", new TitleQualityDefinition("default", "普通", 0));
        }
        return qualities;
    }

    private static Map<String, TitleDefinition> loadTitles(
        ConfigurationSection section,
        Map<String, TitleGroupDefinition> groups,
        Map<String, TitleQualityDefinition> qualities,
        Logger logger
    ) {
        LinkedHashMap<String, TitleDefinition> titles = new LinkedHashMap<>();
        if (section == null) {
            return titles;
        }

        int index = 0;
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null) {
                continue;
            }

            String id = normalizeId(rawId);
            if (id.isBlank()) {
                logger.warning("ArcartXTitle.yml 中存在空称号 ID，已跳过。");
                continue;
            }

            boolean enabled = child.getBoolean("enabled", true);
            if (!enabled) {
                index++;
                continue;
            }

            String groupId = normalizeId(child.getString("group", "default"));
            String qualityId = normalizeId(child.getString("quality", "default"));
            if (!groups.containsKey(groupId)) {
                logger.warning("称号 '" + id + "' 引用了不存在的分组 '" + groupId + "'，已跳过。");
                index++;
                continue;
            }
            if (!qualities.containsKey(qualityId)) {
                logger.warning("称号 '" + id + "' 引用了不存在的品质 '" + qualityId + "'，已跳过。");
                index++;
                continue;
            }

            String displayName = nullToEmpty(child.getString("display-name", id)).trim();
            titles.put(
                id,
                new TitleDefinition(
                    id,
                    true,
                    groupId,
                    qualityId,
                    TitleKind.parse(child.getString("kind", "text")),
                    displayName.isBlank() ? id : displayName,
                    nullToEmpty(child.getString("chat-prefix")).trim(),
                    nullToEmpty(child.getString("chat-suffix")).trim(),
                    nullToEmpty(child.getString("tab-prefix")).trim(),
                    nullToEmpty(child.getString("tab-suffix")).trim(),
                    nullToEmpty(child.getString("description")).trim(),
                    nullToEmpty(child.getString("source")).trim(),
                    readAttributeMap(child.getConfigurationSection("display-attributes"), logger, id, "display-attributes"),
                    readAttributeMap(child.getConfigurationSection("collection-attributes"), logger, id, "collection-attributes"),
                    readStringList(child, logger, id, "display-attribute-lines"),
                    readStringList(child, logger, id, "collection-attribute-lines"),
                    child.getInt("sort-order", index),
                    TitleDefinition.OverheadMode.parse(child.getString("overhead-mode", "none")),
                    nullToEmpty(child.getString("overhead-texture")).trim(),
                    child.getInt("overhead-width", 64),
                    child.getInt("overhead-height", 64),
                    child.getDouble("overhead-offset-y", 2.3),
                    nullToEmpty(child.getString("overhead-prefix")).trim(),
                    nullToEmpty(child.getString("overhead-suffix")).trim()
                )
            );
            index++;
        }
        return titles;
    }

    private static void loadTitlesFromDirectory(
        File directory,
        Map<String, TitleGroupDefinition> groups,
        Map<String, TitleQualityDefinition> qualities,
        Logger logger,
        Map<String, TitleDefinition> target
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int index = 0;
            for (String rawId : yaml.getKeys(false)) {
                ConfigurationSection child = yaml.getConfigurationSection(rawId);
                if (child == null) { index++; continue; }

                String id = normalizeId(rawId);
                if (id.isBlank()) { index++; continue; }

                boolean enabled = child.getBoolean("enabled", true);
                if (!enabled) { index++; continue; }

                String groupId = normalizeId(child.getString("group", "default"));
                String qualityId = normalizeId(child.getString("quality", "default"));
                if (!groups.containsKey(groupId)) {
                    logger.warning("称号 '" + id + "' (文件 " + file.getName() + ") 引用了不存在的分组 '" + groupId + "'，已跳过。");
                    index++; continue;
                }
                if (!qualities.containsKey(qualityId)) {
                    logger.warning("称号 '" + id + "' (文件 " + file.getName() + ") 引用了不存在的品质 '" + qualityId + "'，已跳过。");
                    index++; continue;
                }

                String displayName = nullToEmpty(child.getString("display-name", id)).trim();
                target.put(id, new TitleDefinition(
                    id, true, groupId, qualityId,
                    TitleKind.parse(child.getString("kind", "text")),
                    displayName.isBlank() ? id : displayName,
                    nullToEmpty(child.getString("chat-prefix")).trim(),
                    nullToEmpty(child.getString("chat-suffix")).trim(),
                    nullToEmpty(child.getString("tab-prefix")).trim(),
                    nullToEmpty(child.getString("tab-suffix")).trim(),
                    nullToEmpty(child.getString("description")).trim(),
                    nullToEmpty(child.getString("source")).trim(),
                    readAttributeMap(child.getConfigurationSection("display-attributes"), logger, id, "display-attributes"),
                    readAttributeMap(child.getConfigurationSection("collection-attributes"), logger, id, "collection-attributes"),
                    readStringList(child, logger, id, "display-attribute-lines"),
                    readStringList(child, logger, id, "collection-attribute-lines"),
                    child.getInt("sort-order", index),
                    TitleDefinition.OverheadMode.parse(child.getString("overhead-mode", "none")),
                    nullToEmpty(child.getString("overhead-texture")).trim(),
                    child.getInt("overhead-width", 64),
                    child.getInt("overhead-height", 64),
                    child.getDouble("overhead-offset-y", 2.3),
                    nullToEmpty(child.getString("overhead-prefix")).trim(),
                    nullToEmpty(child.getString("overhead-suffix")).trim()
                ));
                index++;
            }
        }
    }

    private static Map<String, Double> readAttributeMap(
        ConfigurationSection section,
        Logger logger,
        String titleId,
        String path
    ) {
        if (section == null) {
            return Map.of();
        }

        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String normalizedKey = normalizeId(key);
            if (normalizedKey.isBlank()) {
                continue;
            }

            Object rawValue = section.get(key);
            double numericValue;
            if (rawValue instanceof Number number) {
                numericValue = number.doubleValue();
            } else {
                try {
                    numericValue = Double.parseDouble(String.valueOf(rawValue));
                } catch (NumberFormatException exception) {
                    logger.warning("称号 '" + titleId + "' 的 " + path + "." + key + " 不是数值，已跳过。");
                    continue;
                }
            }
            values.put(normalizedKey, numericValue);
        }
        return values;
    }

    private static List<String> readStringList(
        ConfigurationSection section,
        Logger logger,
        String titleId,
        String path
    ) {
        if (section == null) {
            return List.of();
        }

        Object rawValue = section.get(path);
        if (rawValue == null) {
            return List.of();
        }

        List<?> rawList;
        if (rawValue instanceof List<?> list) {
            rawList = list;
        } else if (rawValue instanceof String stringValue) {
            rawList = List.of(stringValue);
        } else {
            logger.warning("称号 '" + titleId + "' 的 " + path + " 不是字符串列表，已跳过。");
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (Object rawEntry : rawList) {
            if (!(rawEntry instanceof String stringEntry)) {
                logger.warning("称号 '" + titleId + "' 的 " + path + " 存在非字符串条目，已跳过。");
                continue;
            }
            String trimmed = stringEntry.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private static String normalizeId(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
