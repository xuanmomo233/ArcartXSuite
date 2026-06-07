package xuanmo.arcartxsuite.announcer.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.UiIdParser;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;

public record AnnouncerModuleConfiguration(
    boolean debug,
    List<String> uiIds,
    boolean registerUiOnEnable,
    boolean overwriteUiFile,
    boolean autoPlay,
    long checkIntervalTicks,
    long cooldownMs,
    long betweenEntryIntervalMs,
    int textWidthFontSize,
    boolean forwardToQQ,
    List<AnnouncerEntry> entries,
    CrossServerChannelConfig crossServer,
    SubtitleSettings subtitle
) {

    public record SubtitleSettings(
        boolean debug,
        List<String> uiIds,
        boolean registerUiOnEnable,
        boolean overwriteUiFile,
        String groupsDirectory,
        boolean showBackground
    ) {
        public static final SubtitleSettings DEFAULTS = new SubtitleSettings(
            false, List.of("AXS:subtitle_hud"), true, false, "subtitle/groups", true
        );
    }

    public static AnnouncerModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static AnnouncerModuleConfiguration load(FileConfiguration configuration, Logger logger, File entriesDirectory) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        List<String> uiIds = UiIdParser.readUiIds(
            configuration.getConfigurationSection("settings"), "ui-id", "AXS:announcer_hud"
        );
        boolean registerUiOnEnable = configuration.getBoolean("settings.register-ui-on-enable", true);
        boolean overwriteUiFile = configuration.getBoolean("settings.overwrite-ui-file", false);
        boolean autoPlay = configuration.getBoolean("settings.auto-play", true);
        long checkIntervalTicks = Math.max(1L, configuration.getLong("settings.check-interval-ticks", 20L));
        long cooldownMs = Math.max(0L, configuration.getLong("settings.cooldown-ms", 30000L));
        long betweenEntryIntervalMs = Math.max(0L, configuration.getLong("settings.between-entry-interval-ms", 30000L));
        int textWidthFontSize = Math.max(1, configuration.getInt("settings.text-width-font-size", 60));
        boolean forwardToQQ = configuration.getBoolean("settings.forward-to-qq", false);

        Map<String, AnnouncerEntry> entryMap = new LinkedHashMap<>();
        if (entriesDirectory != null && entriesDirectory.isDirectory()) {
            loadEntriesFromDirectory(entriesDirectory, entryMap);
        }
        if (entryMap.isEmpty()) {
            logger.warning("entries 目录为空或不存在，未加载任何公告条目。");
        }

        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(
            configuration.getConfigurationSection("cross-server")
        );
        SubtitleSettings subtitleSettings = loadSubtitle(configuration);

        return new AnnouncerModuleConfiguration(
            debug,
            uiIds,
            registerUiOnEnable,
            overwriteUiFile,
            autoPlay,
            checkIntervalTicks,
            cooldownMs,
            betweenEntryIntervalMs,
            textWidthFontSize,
            forwardToQQ,
            List.copyOf(new ArrayList<>(entryMap.values())),
            crossServer,
            subtitleSettings
        );
    }

    private static void loadEntriesFromDirectory(File directory, Map<String, AnnouncerEntry> target) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection s = yaml.getConfigurationSection(id);
                if (s == null) continue;
                target.put(id, new AnnouncerEntry(
                    id, s.getBoolean("enabled", true), s.getString("text", ""), s.getString("click-command", "")
                ));
            }
        }
    }

    public List<AnnouncerEntry> activeEntries() {
        List<AnnouncerEntry> activeEntries = new ArrayList<>();
        for (AnnouncerEntry entry : entries) {
            if (entry.enabled() && !entry.text().isBlank()) {
                activeEntries.add(entry);
            }
        }
        return List.copyOf(activeEntries);
    }

    private static SubtitleSettings loadSubtitle(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("subtitle.settings");
        if (section == null) {
            return SubtitleSettings.DEFAULTS;
        }
        return new SubtitleSettings(
            section.getBoolean("debug", false),
            UiIdParser.readUiIds(section, "ui-id", "AXS:subtitle_hud"),
            section.getBoolean("register-ui-on-enable", true),
            section.getBoolean("overwrite-ui-file", false),
            readString(section, "groups-directory", "subtitle/groups"),
            section.getBoolean("show-background", true)
        );
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String readString(ConfigurationSection section, String path, String defaultValue) {
        String value = section.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
