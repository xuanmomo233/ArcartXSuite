package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.UiIdParser;

public record PluginConfiguration(
    int refreshIntervalTicks,
    double defaultViewerRange,
    int maxVisibleBars,
    BossSortMode sortMode,
    List<String> uiIds,
    boolean registerUiOnEnable,
    boolean overwriteUiFile,
    Map<String, BossDefinition> bosses
) {

    private static final String DEFAULT_UI_ID = "AXS:boss_tracker";
    private static final String LEGACY_UI_ID = "ArcartXSuite:boss_tracker";
    private static final Set<String> RESERVED_ROOT_KEYS = Set.of("settings", "bosses");

    public static PluginConfiguration from(ConfigurationSection config) {
        return from(config, null);
    }

    public static PluginConfiguration from(ConfigurationSection config, File bossesDirectory) {
        if (config == null) {
            config = new YamlConfiguration();
        }
        ConfigurationSection settings = findSectionIgnoreCase(config, "settings");
        int refreshIntervalTicks = settings != null ? Math.max(1, settings.getInt("refresh-interval-ticks", 5)) : 5;
        double defaultViewerRange = settings != null ? Math.max(1.0D, settings.getDouble("default-viewer-range", 48.0D)) : 48.0D;
        int maxVisibleBars = settings != null ? Math.max(1, settings.getInt("max-visible-bars", 5)) : 5;
        BossSortMode sortMode = BossSortMode.from(settings != null ? settings.getString("sort-mode", "spawn-order") : "spawn-order");
        List<String> uiIds = settings != null
            ? UiIdParser.readUiIds(settings, "ui-id", DEFAULT_UI_ID).stream()
                .map(PluginConfiguration::normalizeUiId).toList()
            : List.of(DEFAULT_UI_ID);
        boolean registerUiOnEnable = settings == null || settings.getBoolean("register-ui-on-enable", true);
        boolean overwriteUiFile = settings != null && settings.getBoolean("overwrite-ui-file", false);

        Map<String, BossDefinition> bosses = new LinkedHashMap<>();
        if (bossesDirectory != null && bossesDirectory.isDirectory()) {
            loadBossesFromDirectory(bosses, bossesDirectory, defaultViewerRange);
        }

        return new PluginConfiguration(
            refreshIntervalTicks,
            defaultViewerRange,
            maxVisibleBars,
            sortMode,
            uiIds,
            registerUiOnEnable,
            overwriteUiFile,
            Collections.unmodifiableMap(bosses)
        );
    }

    /** 向后兼容：返回首个 uiId。 */
    public String uiId() {
        return uiIds.isEmpty() ? "" : uiIds.get(0);
    }

    public BossDefinition findBoss(String mythicMobId) {
        if (mythicMobId == null) {
            return null;
        }
        return bosses.get(mythicMobId.toLowerCase(Locale.ROOT));
    }

    public int getTrackedBossCount() {
        return bosses.size();
    }

    public int getDamageRankingBossCount() {
        int count = 0;
        for (BossDefinition definition : bosses.values()) {
            if (definition.damageRanking().enabled()) {
                count++;
            }
        }
        return count;
    }

    public int getMaxDamageRankingEntries() {
        int maxEntries = 1;
        for (BossDefinition definition : bosses.values()) {
            if (!definition.damageRanking().enabled()) {
                continue;
            }
            maxEntries = Math.max(maxEntries, definition.damageRanking().maxEntries());
        }
        return maxEntries;
    }

    public List<String> getTrackedBossIds() {
        List<String> ids = new ArrayList<>(bosses.size());
        for (BossDefinition definition : bosses.values()) {
            ids.add(definition.mythicMobId());
        }
        return List.copyOf(ids);
    }

    public String summarizeTrackedBossIds(int limit) {
        List<String> ids = getTrackedBossIds();
        if (ids.isEmpty()) {
            return "无";
        }
        int safeLimit = Math.max(1, limit);
        if (ids.size() <= safeLimit) {
            return String.join(", ", ids);
        }
        List<String> preview = ids.subList(0, safeLimit);
        return String.join(", ", preview) + " ... +" + (ids.size() - safeLimit);
    }

    private static void loadBossesFromSection(
        Map<String, BossDefinition> bosses,
        ConfigurationSection section,
        double defaultViewerRange,
        boolean rootCompatibilityMode
    ) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (rootCompatibilityMode && RESERVED_ROOT_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }

            ConfigurationSection bossSection = section.getConfigurationSection(key);
            if (bossSection == null) {
                continue;
            }
            if (rootCompatibilityMode && !isLikelyBossSection(bossSection)) {
                continue;
            }

            String normalizedKey = key.toLowerCase(Locale.ROOT);
            bosses.putIfAbsent(normalizedKey, BossDefinition.from(key, bossSection, defaultViewerRange));
        }
    }

    private static void loadBossesFromDirectory(
        Map<String, BossDefinition> bosses,
        File directory,
        double defaultViewerRange
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            String mythicMobId = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String normalizedKey = mythicMobId.toLowerCase(Locale.ROOT);
                bosses.put(normalizedKey, BossDefinition.from(mythicMobId, yaml, defaultViewerRange));
            } catch (Exception e) {
                // silently skip malformed files
            }
        }
    }

    private static boolean isLikelyBossSection(ConfigurationSection section) {
        return section.contains("enabled")
            || section.contains("priority")
            || section.contains("viewer-range")
            || section.contains("title-format")
            || section.contains("subtitle-format")
            || section.contains("damage-ranking")
            || section.contains("spawn-chat-card")
            || section.contains("death-chat-card")
            || section.contains("despawn-chat-card");
    }

    private static String normalizeUiId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_UI_ID;
        }
        String value = rawValue.trim();
        return LEGACY_UI_ID.equalsIgnoreCase(value) ? DEFAULT_UI_ID : value;
    }

    private static ConfigurationSection findSectionIgnoreCase(ConfigurationSection parent, String expectedKey) {
        if (parent == null || expectedKey == null || expectedKey.isBlank()) {
            return null;
        }

        ConfigurationSection direct = parent.getConfigurationSection(expectedKey);
        if (direct != null) {
            return direct;
        }

        for (String key : parent.getKeys(false)) {
            if (!key.equalsIgnoreCase(expectedKey)) {
                continue;
            }
            return parent.getConfigurationSection(key);
        }
        return null;
    }
}


