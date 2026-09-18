package xuanmo.arcartxsuite.questgps.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.UiIdParser;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.chemdah.PresentationSource;
import xuanmo.arcartxsuite.questgps.chemdah.database.QuestGpsDatabaseSettings;

public record QuestGpsModuleConfiguration(
    boolean debug,
    UiConfiguration ui,
    NavigationDefaults navigation,
    PresentationDefaults presentation,
    CategoryDefaults categoryDefaults,
    QuestGpsDatabaseSettings database,
    String discoveryMode,
    GateConfiguration gate,
    Map<String, QuestGpsCategory> categoryRegistry,
    Map<String, QuestDefinition> quests
) {

    private static final String DEFAULT_PACKET_ID = "AXS_QUESTGPS";
    private static final String DEFAULT_MENU_UI_ID = "AXS:questgps_menu";
    private static final String DEFAULT_GUIDE_UI_ID = "AXS:questgps_guide";
    private static final boolean DEFAULT_REGISTER_UI_ON_ENABLE = true;
    private static final boolean DEFAULT_OVERWRITE_UI_FILES = false;
    private static final boolean DEFAULT_NAVIGATION_ENABLED = true;
    private static final String DEFAULT_WAYPOINT_STYLE_ID = "default";
    private static final String DEFAULT_QUEST_ID_PREFIX = "AXS-questgps-";
    private static final boolean DEFAULT_REMOVE_ON_FINISH = true;

    public static QuestGpsModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static QuestGpsModuleConfiguration load(FileConfiguration configuration, Logger logger, File questsDirectory) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(logger, "logger");

        boolean debug = configuration.getBoolean("debug.enabled", false);
        UiConfiguration ui = loadUi(configuration.getConfigurationSection("ui"));
        NavigationDefaults navigation = loadNavigationDefaults(configuration.getConfigurationSection("navigation"));
        PresentationDefaults presentation = loadPresentationDefaults(configuration.getConfigurationSection("presentation"), logger);
        CategoryDefaults categoryDefaults = loadCategoryDefaults(configuration.getConfigurationSection("category"), logger);
        QuestGpsDatabaseSettings database = loadDatabaseSettings(configuration.getConfigurationSection("database"));
        String discoveryMode = string(configuration.getString("discovery.mode"), "overlay");
        List<QuestGpsCategory> customCategories = loadCustomCategories(configuration.getConfigurationSection("categories"), logger);
        if (categoryDefaults.fallback().enabled()) {
            CategoryDefaults.FallbackCategory fallback = categoryDefaults.fallback();
            customCategories.add(new QuestGpsCategory(fallback.id(), fallback.displayName(), fallback.sortOrder()));
        }
        Map<String, QuestGpsCategory> categoryRegistry = QuestGpsCategory.buildRegistry(customCategories);
        GateConfiguration gate = loadGate(configuration.getConfigurationSection("gate"), categoryRegistry);
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        if (questsDirectory != null && questsDirectory.isDirectory()) {
            loadQuestsFromDirectory(questsDirectory, quests, categoryRegistry, logger);
        }
        return new QuestGpsModuleConfiguration(
            debug,
            ui,
            navigation,
            presentation,
            categoryDefaults,
            database,
            discoveryMode,
            gate,
            categoryRegistry,
            Map.copyOf(quests)
        );
    }

    public QuestDefinition quest(String questId) {
        return quests.get(normalizeKey(questId));
    }

    public TaskDefinition task(String questId, String taskId) {
        QuestDefinition quest = quest(questId);
        return quest == null ? null : quest.task(taskId);
    }

    public List<QuestDefinition> orderedQuests() {
        List<QuestDefinition> definitions = new ArrayList<>(quests.values());
        definitions.sort(
            Comparator
                .comparingInt((QuestDefinition d) -> categorySortPlaceholder(d.categoryOverride()))
                .thenComparingInt(QuestDefinition::sortOrder)
                .thenComparing(QuestDefinition::id, String.CASE_INSENSITIVE_ORDER)
        );
        return List.copyOf(definitions);
    }

    public int configuredQuestCount() {
        return quests.size();
    }

    /**
     * auto 发现模式下，为未在 overlay 登记的任务生成最小定义。
     */
    public static QuestDefinition syntheticQuest(String questId) {
        String id = questId == null ? "" : questId.trim();
        return new QuestDefinition(
            id,
            true,
            null,
            "",
            List.of(),
            0,
            false,
            List.of(),
            List.of(),
            new QuestNavigation(false, null),
            HookSignals.empty(),
            QuestPresentationOverride.empty(),
            Map.of()
        );
    }

    private static UiConfiguration loadUi(ConfigurationSection section) {
        return new UiConfiguration(
            string(section == null ? null : section.getString("packet-id"), DEFAULT_PACKET_ID),
            UiIdParser.readUiIds(section, "menu-ui-id", DEFAULT_MENU_UI_ID),
            UiIdParser.readUiIds(section, "guide-ui-id", DEFAULT_GUIDE_UI_ID),
            section == null || section.getBoolean("register-ui-on-enable", DEFAULT_REGISTER_UI_ON_ENABLE),
            section != null && section.getBoolean("overwrite-ui-files", DEFAULT_OVERWRITE_UI_FILES)
        );
    }

    private static NavigationDefaults loadNavigationDefaults(ConfigurationSection section) {
        ConfigurationSection waypointSection = section == null ? null : section.getConfigurationSection("waypoint");
        WaypointDefaults waypoint = new WaypointDefaults(
            waypointSection == null || waypointSection.getBoolean("enabled", true),
            string(waypointSection == null ? null : waypointSection.getString("style-id"), DEFAULT_WAYPOINT_STYLE_ID),
            normalizeStringList(rawStringList(waypointSection == null ? null : waypointSection.get("title-template")))
        );
        return new NavigationDefaults(
            section == null || section.getBoolean("enabled", DEFAULT_NAVIGATION_ENABLED),
            string(section == null ? null : section.getString("quest-id-prefix"), DEFAULT_QUEST_ID_PREFIX),
            section == null || section.getBoolean("remove-on-finish", DEFAULT_REMOVE_ON_FINISH),
            string(section == null ? null : section.getString("mode"), "hybrid"),
            loadModelMarkerDefaults(section == null ? null : section.getConfigurationSection("model-marker")),
            loadTextureMarkerDefaults(section == null ? null : section.getConfigurationSection("texture-marker")),
            waypoint,
            section == null ? 10 : section.getInt("refresh-ticks", 10),
            section == null ? 30 : section.getInt("world-hint-interval", 30),
            section == null ? 0.0 : section.getDouble("finish-range", 0.0),
            normalizeStringList(section == null ? List.of() : section.getStringList("finish-commands")),
            section == null ? 3.0 : section.getDouble("finish-rearm-buffer", 3.0)
        );
    }

    private static CategoryDefaults loadCategoryDefaults(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return CategoryDefaults.defaults();
        }
        CategorySource source = CategorySource.parse(section.getString("source"), CategorySource.CHEMDAH);
        if (section.contains("id-prefix-rules")) {
            logger.warning(
                "QuestGPS: category.id-prefix-rules 已废弃，分类仅由 category.source 指定的一种数据源决定"
                    + "（chemdah=meta.type，overlay=quests/*.yml 的 category）"
            );
        }
        CategoryDefaults.FallbackCategory fallbackDefaults = CategoryDefaults.FallbackCategory.disabled();
        ConfigurationSection fallbackSection = section.getConfigurationSection("fallback");
        if (fallbackSection != null) {
            fallbackDefaults = new CategoryDefaults.FallbackCategory(
                fallbackSection.getBoolean("enabled", fallbackDefaults.enabled()),
                string(fallbackSection.getString("id"), fallbackDefaults.id()),
                string(fallbackSection.getString("display-name"), fallbackDefaults.displayName()),
                fallbackSection.getInt("sort-order", fallbackDefaults.sortOrder())
            );
        }
        return new CategoryDefaults(source, fallbackDefaults);
    }

    private static int categorySortPlaceholder(QuestGpsCategory categoryOverride) {
        return categoryOverride == null ? Integer.MAX_VALUE : categoryOverride.sortOrder();
    }

    private static QuestGpsCategory readOptionalCategory(
        ConfigurationSection questSection,
        Map<String, QuestGpsCategory> categoryRegistry,
        Logger logger,
        String questId
    ) {
        if (!questSection.contains("category")) {
            return null;
        }
        QuestGpsCategory parsed = QuestGpsCategory.parse(questSection.getString("category"), categoryRegistry);
        if (parsed == null) {
            logger.warning("QuestGPS: overlay category 无效，已忽略: " + questId);
            return null;
        }
        return parsed;
    }

    private static PresentationDefaults loadPresentationDefaults(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return PresentationDefaults.defaults();
        }
        warnDeprecatedPresentationFields(section, logger);
        PresentationSource source = PresentationSource.parseGlobal(section.getString("source"), PresentationSource.CHEMDAH);
        return new PresentationDefaults(source);
    }

    private static void warnDeprecatedPresentationFields(ConfigurationSection section, Logger logger) {
        for (String key : List.of(
            "name-source",
            "description-source",
            "task-text-source",
            "task-description-source",
            "rewards-source"
        )) {
            if (!section.contains(key)) {
                continue;
            }
            String value = section.getString(key);
            logger.warning(
                "QuestGPS: presentation." + key + " 已废弃，请改用 presentation.source（chemdah | overlay）"
            );
        }
    }

    private static QuestGpsDatabaseSettings loadDatabaseSettings(ConfigurationSection section) {
        if (section == null) {
            return QuestGpsDatabaseSettings.defaults();
        }
        return new QuestGpsDatabaseSettings(
            section.getBoolean("enabled", false),
            section.getBoolean("load-in-join-event", true),
            section.getBoolean("release-in-quit-event", true),
            section.getBoolean("disable-auto-save", false),
            section.getBoolean("disable-auto-create-table", false)
        );
    }

    private static QuestPresentationOverride loadQuestPresentation(ConfigurationSection section) {
        if (section == null) {
            return QuestPresentationOverride.empty();
        }
        String raw = section.getString("source");
        if (raw == null || raw.isBlank()) {
            return QuestPresentationOverride.empty();
        }
        return new QuestPresentationOverride(PresentationSource.parseGlobal(raw, PresentationSource.CHEMDAH));
    }

    private static ModelMarkerDefaults loadModelMarkerDefaults(ConfigurationSection section) {
        if (section == null) {
            return ModelMarkerDefaults.defaults();
        }
        return new ModelMarkerDefaults(
            section.getBoolean("enabled", true),
            string(section.getString("model-id"), "nav_beacon"),
            section.getDouble("scale", 1.0),
            string(section.getString("default-state"), "idle"),
            string(section.getString("animation"), "rotate"),
            section.getDouble("y-offset", 2.0),
            section.getDouble("path-interval", 3.0),
            section.getInt("path-max-markers", 20),
            section.getInt("path-update-ticks", 10),
            section.getDouble("path-max-distance", 64.0),
            section.getInt("path-max-iterations", 2000),
            section.getDouble("path-reshow-buffer", 8.0)
        );
    }

    private static TextureMarkerDefaults loadTextureMarkerDefaults(ConfigurationSection section) {
        if (section == null) {
            return TextureMarkerDefaults.defaults();
        }
        ConfigurationSection pulseSection = section.getConfigurationSection("pulse");
        return new TextureMarkerDefaults(
            section.getBoolean("enabled", false),
            string(section.getString("texture"), "nav_arrow"),
            section.getDouble("width", 100.0),
            section.getDouble("height", 100.0),
            section.getBoolean("facing", false),
            section.getBoolean("glowing", true),
            section.getDouble("y-offset", 0.1),
            (float) section.getDouble("pitch", 90.0),
            section.getBoolean("orient-to-path", true),
            section.getInt("life-time", -1),
            pulseSection == null || pulseSection.getBoolean("enabled", true),
            pulseSection == null ? 0.8 : pulseSection.getDouble("from", 0.8),
            pulseSection == null ? 1.2 : pulseSection.getDouble("to", 1.2),
            pulseSection == null ? 20 : pulseSection.getInt("duration", 20),
            pulseSection == null ? 2 : pulseSection.getInt("stagger", 2),
            section.getDouble("path-interval", 3.0),
            section.getInt("path-max-markers", 20),
            section.getInt("path-update-ticks", 10),
            section.getDouble("path-max-distance", 64.0),
            section.getInt("path-max-iterations", 2000),
            section.getDouble("path-reshow-buffer", 8.0)
        );
    }

    private static List<QuestGpsCategory> loadCustomCategories(ConfigurationSection section, Logger logger) {
        List<QuestGpsCategory> categories = new ArrayList<>();
        if (section == null || section.getKeys(false).isEmpty()) {
            logger.warning(
                "QuestGPS: categories 段为空：无分类 Tab，且 Chemdah meta.type / overlay category 须在 categories 注册后任务才会显示"
            );
            return categories;
        }
        for (String categoryId : section.getKeys(false)) {
            ConfigurationSection catSection = section.getConfigurationSection(categoryId);
            if (catSection == null) {
                continue;
            }
            String displayName = string(catSection.getString("display-name"), categoryId);
            int sortOrder = catSection.getInt("sort-order", 300);
            categories.add(new QuestGpsCategory(categoryId, displayName, sortOrder));
            logger.fine("QuestGPS: 加载分类: " + categoryId + " (" + displayName + ", sort=" + sortOrder + ")");
        }
        return categories;
    }

    private static GateConfiguration loadGate(ConfigurationSection section, Map<String, QuestGpsCategory> registry) {
        return new GateConfiguration(
            normalizeStringList(section == null ? List.of() : section.getStringList("required-mainline-quest-ids")),
            parseCategoryIds(section == null ? List.of() : section.getStringList("blocked-categories"), registry),
            normalizeStringList(section == null ? List.of() : section.getStringList("blocked-command-prefixes")),
            normalizeStringList(section == null ? List.of() : section.getStringList("blocked-module-entries")),
            normalizeStringList(section == null ? List.of() : section.getStringList("blocked-event-rule-ids")),
            string(section == null ? null : section.getString("deny-message"), "&c你需要先完成必要主线任务。"),
            string(section == null ? null : section.getString("deny-chat-card"), ""),
            string(section == null ? null : section.getString("deny-subtitle"), "")
        );
    }

    private static Map<String, QuestDefinition> loadQuests(ConfigurationSection section, Logger logger) {
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        if (section == null) {
            return quests;
        }
        for (String questId : section.getKeys(false)) {
            ConfigurationSection questSection = section.getConfigurationSection(questId);
            if (questSection == null) {
                logger.warning("QuestGPS: 任务配置格式无效，已跳过: " + questId);
                continue;
            }
            boolean enabled = questSection.getBoolean("enabled", true);
            if (!enabled) {
                continue;
            }
            QuestGpsCategory categoryOverride = readOptionalCategory(questSection, null, logger, questId);
            List<RewardPreviewDefinition> rewards = readRewardList(questSection.getMapList("rewards"), logger, questId);
            ConfigurationSection taskSection = questSection.getConfigurationSection("tasks");
            Map<String, TaskDefinition> tasks = loadTasks(taskSection, logger, questId);
            QuestNavigation navigation = loadQuestNavigation(questSection.getConfigurationSection("navigation"));
            HookSignals hooks = loadHooks(questSection.getConfigurationSection("hooks"));
            QuestPresentationOverride presentation = loadQuestPresentation(questSection.getConfigurationSection("presentation"));
            QuestDefinition definition = new QuestDefinition(
                questId.trim(),
                true,
                categoryOverride,
                string(questSection.getString("display-name-override"), ""),
                normalizeMultiline(questSection.get("description")),
                questSection.getInt("sort-order", 0),
                questSection.getBoolean("allow-abandon", false),
                normalizeStringList(questSection.getStringList("required-mainline")),
                rewards,
                navigation,
                hooks,
                presentation,
                tasks
            );
            quests.put(normalizeKey(questId), definition);
        }
        return quests;
    }

    private static void loadQuestsFromDirectory(File directory, Map<String, QuestDefinition> target, Map<String, QuestGpsCategory> categoryRegistry, Logger logger) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String questId : yaml.getKeys(false)) {
                ConfigurationSection questSection = yaml.getConfigurationSection(questId);
                if (questSection == null) continue;
                boolean enabled = questSection.getBoolean("enabled", true);
                if (!enabled) continue;
                QuestGpsCategory categoryOverride = readOptionalCategory(questSection, categoryRegistry, logger, questId);
                List<RewardPreviewDefinition> rewards = readRewardList(questSection.getMapList("rewards"), logger, questId);
                Map<String, TaskDefinition> tasks = loadTasks(questSection.getConfigurationSection("tasks"), logger, questId);
                QuestNavigation navigation = loadQuestNavigation(questSection.getConfigurationSection("navigation"));
                HookSignals hooks = loadHooks(questSection.getConfigurationSection("hooks"));
                QuestPresentationOverride presentation = loadQuestPresentation(questSection.getConfigurationSection("presentation"));
                target.put(normalizeKey(questId), new QuestDefinition(
                    questId.trim(), true, categoryOverride,
                    string(questSection.getString("display-name-override"), ""),
                    normalizeMultiline(questSection.get("description")),
                    questSection.getInt("sort-order", 0),
                    questSection.getBoolean("allow-abandon", false),
                    normalizeStringList(questSection.getStringList("required-mainline")),
                    rewards, navigation, hooks, presentation, tasks
                ));
            }
        }
    }

    private static Map<String, TaskDefinition> loadTasks(
        ConfigurationSection section,
        Logger logger,
        String questId
    ) {
        Map<String, TaskDefinition> tasks = new LinkedHashMap<>();
        if (section == null) {
            return Map.of();
        }
        for (String taskId : section.getKeys(false)) {
            ConfigurationSection taskSection = section.getConfigurationSection(taskId);
            if (taskSection == null) {
                logger.warning("QuestGPS: 任务目标配置格式无效，已跳过: " + questId + "/" + taskId);
                continue;
            }
            TaskDefinition definition = new TaskDefinition(
                taskId.trim(),
                string(taskSection.getString("display-text"), ""),
                normalizeMultiline(taskSection.get("description")),
                taskSection.getInt("sort-order", 0),
                readNavigationPoint(taskSection.getConfigurationSection("navigation"))
            );
            tasks.put(normalizeKey(taskId), definition);
        }
        return Map.copyOf(tasks);
    }

    private static QuestNavigation loadQuestNavigation(ConfigurationSection section) {
        if (section == null) {
            return new QuestNavigation(true, readNavigationPoint(null));
        }
        return new QuestNavigation(section.getBoolean("enabled", true), readNavigationPoint(section.getConfigurationSection("point")));
    }

    private static HookSignals loadHooks(ConfigurationSection section) {
        if (section == null) {
            return HookSignals.empty();
        }
        return new HookSignals(
            normalizeStringList(section.getStringList("triggered")),
            normalizeStringList(section.getStringList("accepted")),
            normalizeStringList(section.getStringList("abandoned")),
            normalizeStringList(section.getStringList("completed")),
            normalizeStringList(section.getStringList("track-changed"))
        );
    }

    private static List<RewardPreviewDefinition> readRewardList(
        List<Map<?, ?>> rawRewards,
        Logger logger,
        String questId
    ) {
        if (rawRewards == null || rawRewards.isEmpty()) {
            return List.of();
        }
        List<RewardPreviewDefinition> rewards = new ArrayList<>(rawRewards.size());
        for (int index = 0; index < rawRewards.size(); index++) {
            Map<?, ?> raw = rawRewards.get(index);
            String type = string(raw.get("type"), "text").toLowerCase(Locale.ROOT);
            if (!Set.of("neigeitems", "mythicmobs", "mythicitems", "mmoitems", "overture", "material", "itemstack", "title", "text").contains(type)) {
                logger.warning("QuestGPS: 奖励类型未识别，将按 text 处理: " + questId + "[" + index + "] -> " + type);
                type = "text";
            }
            rewards.add(
                new RewardPreviewDefinition(
                    type,
                    string(raw.get("neige-item-id"), ""),
                    string(raw.get("mythic-item-id"), string(raw.get("mythicmobs-item-id"), string(raw.get("item-id"), ""))),
                    string(raw.get("overture-item-id"), string(raw.get("overture-id"), "")),
                    string(raw.get("type-id"), string(raw.get("mmo-type"), string(raw.get("mmoitems-type"), ""))),
                    string(raw.get("id"), string(raw.get("mmo-id"), string(raw.get("mmoitems-id"), ""))),
                    string(raw.get("material"), ""),
                    positiveInt(raw.get("amount"), 1),
                    string(raw.get("display-name"), ""),
                    normalizeStringList(rawList(raw.get("lore"))),
                    string(raw.get("text"), ""),
                    string(raw.get("fallback-material"), ""),
                    string(raw.get("title-id"), ""),
                    string(raw.get("duration"), "permanent"),
                    positiveInt(raw.get("level"), 0),
                    string(raw.get("tier"), "")
                )
            );
        }
        return List.copyOf(rewards);
    }

    private static NavigationPointDefinition readNavigationPoint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = string(section.getString("world"), "");
        String npc = string(section.getString("npc"), "");
        if (world.isBlank() && npc.isBlank()) {
            return null;
        }
        return new NavigationPointDefinition(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            string(section.getString("title"), ""),
            string(section.getString("style-id"), ""),
            string(section.getString("map-label"), ""),
            npc,
            section.contains("finish-range") ? section.getDouble("finish-range") : -1,
            normalizeStringList(section.getStringList("finish-commands")),
            readTeleportPoint(section.getConfigurationSection("teleport"))
        );
    }

    private static TeleportPointDefinition readTeleportPoint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = string(section.getString("world"), "");
        if (world.isBlank()) {
            return null;
        }
        return new TeleportPointDefinition(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z")
        );
    }

    private static List<String> normalizeMultiline(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Iterable<?> iterable) {
            List<String> lines = new ArrayList<>();
            for (Object entry : iterable) {
                lines.addAll(normalizeMultiline(entry));
            }
            return List.copyOf(lines);
        }
        String value = String.valueOf(raw);
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return List.copyOf(lines);
    }

    private static List<String> rawList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    /** 字符串或字符串列表统一转列表（title-template 既支持单行字符串也支持多行列表）。 */
    private static List<String> rawStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
            return result;
        }
        if (value == null) {
            return List.of();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? List.of() : List.of(text);
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private static Set<String> parseCategoryIds(List<String> rawValues, Map<String, QuestGpsCategory> registry) {
        Set<String> ids = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null) continue;
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && registry.containsKey(normalized)) {
                ids.add(normalized);
            }
        }
        return Set.copyOf(ids);
    }

    private static String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            int result = number.intValue();
            return result > 0 ? result : fallback;
        }
        if (value instanceof String string) {
            try {
                int result = Integer.parseInt(string.trim());
                return result > 0 ? result : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record UiConfiguration(
        String packetId,
        List<String> menuUiIds,
        List<String> guideUiIds,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
        /** 向后兼容 */
        public String menuUiId() { return menuUiIds.isEmpty() ? "" : menuUiIds.get(0); }
        public String guideUiId() { return guideUiIds.isEmpty() ? "" : guideUiIds.get(0); }
    }

    public record NavigationDefaults(
        boolean enabled,
        String questIdPrefix,
        boolean removeOnFinish,
        String mode,
        ModelMarkerDefaults modelMarker,
        TextureMarkerDefaults textureMarker,
        WaypointDefaults waypoint,
        int refreshTicks,
        int worldHintIntervalSeconds,
        double finishRange,
        List<String> finishCommands,
        double rearmBuffer
    ) {
    }

    /**
     * ArcartX 路标（waypoint）渲染配置：目标点光柱与标题，样式在 ArcartX waypoint 目录定义。
     *
     * @param enabled       是否向客户端推送路标（false 时导航仅保留追踪态/到达判定/路径标记，不发路标包）
     * @param styleId       路标样式 ID
     * @param titleTemplate 路标标题模板行（拼接为单行；%label%/%quest%/%objective%/%distance%/%world%/%x%/%y%/%z% 占位）
     */
    public record WaypointDefaults(
        boolean enabled,
        String styleId,
        List<String> titleTemplate
    ) {
        /** 标题模板拼接为单行（路标标题为单行文本）。 */
        public String titleTemplateText() {
            return titleTemplate == null || titleTemplate.isEmpty() ? "" : String.join(" ", titleTemplate);
        }
    }

    public record PresentationDefaults(PresentationSource source) {
        public static PresentationDefaults defaults() {
            return new PresentationDefaults(PresentationSource.CHEMDAH);
        }
    }

    public record QuestPresentationOverride(PresentationSource source) {
        public static QuestPresentationOverride empty() {
            return new QuestPresentationOverride(null);
        }

        public PresentationSource source(PresentationSource fallback) {
            return source == null ? fallback : source;
        }
    }

    public record ModelMarkerDefaults(
        boolean enabled,
        String modelId,
        double scale,
        String defaultState,
        String animation,
        double yOffset,
        double pathInterval,
        int pathMaxMarkers,
        int pathUpdateTicks,
        double pathMaxDistance,
        int pathMaxIterations,
        double pathReshowBuffer
    ) implements xuanmo.arcartxsuite.api.util.NavigationMarkerConfig {
        public static ModelMarkerDefaults defaults() {
            return new ModelMarkerDefaults(true, "nav_beacon", 1.0, "idle", "rotate", 2.0,
                3.0, 20, 10, 64.0, 2000, 8.0);
        }
    }

    /**
     * 导航贴图提示配置（ArcartX WorldTexture 路径贴图，与 modelMarker 模型导航互补、独立开关）。
     *
     * @param enabled           是否启用贴图路径提示（需 ArcartX 客户端资源包中存在 texture 对应贴图）
     * @param texture           贴图资源 ID
     * @param width             渲染宽度
     * @param height            渲染高度
     * @param facing            是否始终面向玩家（billboard）；false = 贴地按 yaw/pitch 定向
     * @param glowing           是否泛光
     * @param yOffset           贴图相对路径点的 Y 轴偏移（方块数）
     * @param pitch             俯仰角（度；贴地渲染 90）
     * @param orientToPath      yaw 是否朝向下一个路径点（贴地箭头指示方向）
     * @param lifeTime          贴图存活时长（-1 = 永续直到移除）
     * @param pulseEnabled      是否启用循环缩放脉冲动画
     * @param pulseFrom         脉冲起始缩放倍率
     * @param pulseTo           脉冲目标缩放倍率
     * @param pulseDuration     脉冲单段时长
     * @param pulseStagger      脉冲逐级延迟（波浪效果；0 = 同步）
     * @param pathInterval      路径贴图间距（方块数）
     * @param pathMaxMarkers    路径贴图最大数量
     * @param pathUpdateTicks   路径更新间隔（tick）
     * @param pathMaxDistance   路径最大渲染距离（方块数）
     * @param pathMaxIterations 寻路最大迭代次数
     * @param pathReshowBuffer  距离滞回缓冲（格）
     */
    public record TextureMarkerDefaults(
        boolean enabled,
        String texture,
        double width,
        double height,
        boolean facing,
        boolean glowing,
        double yOffset,
        float pitch,
        boolean orientToPath,
        int lifeTime,
        boolean pulseEnabled,
        double pulseFrom,
        double pulseTo,
        int pulseDuration,
        int pulseStagger,
        double pathInterval,
        int pathMaxMarkers,
        int pathUpdateTicks,
        double pathMaxDistance,
        int pathMaxIterations,
        double pathReshowBuffer
    ) implements xuanmo.arcartxsuite.api.util.NavigationTextureMarkerConfig {
        public static TextureMarkerDefaults defaults() {
            return new TextureMarkerDefaults(false, "nav_arrow", 100.0, 100.0, false, true,
                0.1, 90f, true, -1, true, 0.8, 1.2, 20, 2,
                3.0, 20, 10, 64.0, 2000, 8.0);
        }
    }

    public record GateConfiguration(
        List<String> requiredMainlineQuestIds,
        Set<String> blockedCategories,
        List<String> blockedCommandPrefixes,
        List<String> blockedModuleEntries,
        List<String> blockedEventRuleIds,
        String denyMessage,
        String denyChatCard,
        String denySubtitle
    ) {
    }

    public record QuestDefinition(
        String id,
        boolean enabled,
        QuestGpsCategory categoryOverride,
        String displayNameOverride,
        List<String> description,
        int sortOrder,
        boolean allowAbandon,
        List<String> requiredMainline,
        List<RewardPreviewDefinition> rewards,
        QuestNavigation navigation,
        HookSignals hooks,
        QuestPresentationOverride presentation,
        Map<String, TaskDefinition> tasks
    ) {
        public TaskDefinition task(String taskId) {
            return tasks.get(normalizeKey(taskId));
        }
    }

    public record TaskDefinition(
        String taskId,
        String displayText,
        List<String> description,
        int sortOrder,
        NavigationPointDefinition navigation
    ) {
    }

    public record QuestNavigation(
        boolean enabled,
        NavigationPointDefinition point
    ) {
    }

    public record HookSignals(
        List<String> triggered,
        List<String> accepted,
        List<String> abandoned,
        List<String> completed,
        List<String> trackChanged
    ) {
        public static HookSignals empty() {
            return new HookSignals(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record RewardPreviewDefinition(
        String type,
        String neigeItemId,
        String mythicItemId,
        String overtureItemId,
        String mmoItemType,
        String mmoItemId,
        String material,
        int amount,
        String displayName,
        List<String> lore,
        String text,
        String fallbackMaterial,
        String titleId,
        String duration,
        int level,
        String tier
    ) {
    }

    /**
     * @param npc            Adyeshach NPC 名/ID（非空时导航点跟随该 NPC 实时位置；world 可留空）
     * @param finishRange    到达触发半径（格）；&lt; 0 表示继承全局 navigation.finish-range
     * @param finishCommands 到达命令（控制台执行，%player% 占位）；空列表表示继承全局 navigation.finish-commands
     * @param teleport       跨世界传送点（目标在其他世界时面板显示「传送并追踪」，可空）
     */
    public record NavigationPointDefinition(
        String world,
        double x,
        double y,
        double z,
        String title,
        String styleId,
        String mapLabel,
        String npc,
        double finishRange,
        List<String> finishCommands,
        TeleportPointDefinition teleport
    ) {
    }

    /**
     * 跨世界传送点定义（quest/task 级 navigation.teleport 段）。
     *
     * @param world 目标世界
     * @param x     X 坐标
     * @param y     Y 坐标
     * @param z     Z 坐标
     */
    public record TeleportPointDefinition(
        String world,
        double x,
        double y,
        double z
    ) {
    }
}
