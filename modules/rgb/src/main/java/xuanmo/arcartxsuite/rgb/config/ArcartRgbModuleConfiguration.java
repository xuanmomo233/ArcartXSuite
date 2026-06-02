package xuanmo.arcartxsuite.rgb.config;

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

public record ArcartRgbModuleConfiguration(
    boolean debug,
    Map<String, ArcartRgbEntry> entries
) {

    private static final ArcartRgbColor DEFAULT_COLOR = new ArcartRgbColor(255, 255, 255);

    public ArcartRgbModuleConfiguration {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static ArcartRgbModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static ArcartRgbModuleConfiguration load(FileConfiguration configuration, Logger logger, File entriesDirectory) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        Map<String, ArcartRgbEntry> entries = new LinkedHashMap<>();
        if (entriesDirectory != null && entriesDirectory.isDirectory()) {
            loadEntriesFromDirectory(entriesDirectory, entries, logger);
        }
        if (entries.isEmpty()) {
            logger.warning("entries 目录为空或不存在，未加载任何 RGB 条目。");
        }
        return new ArcartRgbModuleConfiguration(debug, entries);
    }

    private static void loadEntriesFromDirectory(File directory, Map<String, ArcartRgbEntry> target, Logger logger) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection child = yaml.getConfigurationSection(id);
                if (child == null) continue;
                target.put(id.toLowerCase(Locale.ROOT), parseEntry(id, child, logger));
            }
        }
    }

    private static ArcartRgbEntry parseEntry(String id, ConfigurationSection child, Logger logger) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        List<ArcartRgbColor> gradientColors = parseGradientColors(child, logger, id);
        return new ArcartRgbEntry(
            normalizedId,
            child.getBoolean("enabled", true),
            readString(child, "text", ""),
            gradientColors,
            child.getBoolean("shine", false),
            Math.max(0L, child.getLong("switch-interval-ticks", 2L)),
            Math.max(1, child.getInt("shine-width", 2)),
            parseColor(
                readString(child, "shine-color", "#FFFFFF"),
                DEFAULT_COLOR,
                logger,
                id,
                "shine-color"
            ),
            child.getDouble("shine-strength", 0.55D)
        );
    }

    public ArcartRgbEntry entry(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return entries.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public int enabledEntryCount() {
        int count = 0;
        for (ArcartRgbEntry entry : entries.values()) {
            if (entry.active()) {
                count++;
            }
        }
        return count;
    }

    private static List<ArcartRgbColor> parseGradientColors(
        ConfigurationSection child,
        Logger logger,
        String entryId
    ) {
        List<String> rawValues = new ArrayList<>(child.getStringList("gradient-colors"));
        if (rawValues.isEmpty()) {
            String singleValue = child.getString("gradient-colors");
            if (singleValue != null && !singleValue.isBlank()) {
                rawValues.add(singleValue.trim());
            }
        }

        List<ArcartRgbColor> result = new ArrayList<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            try {
                result.add(ArcartRgbColor.parse(rawValue));
            } catch (IllegalArgumentException exception) {
                logger.warning("ArcartXRGB entry[" + entryId + "] 的 gradient-colors 含有无效颜色: " + rawValue);
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }

        // 诊断：区分 "键不存在" 和 "颜色值被 YAML 当作注释"
        List<?> rawList = child.getList("gradient-colors");
        if (rawList != null && !rawList.isEmpty()) {
            // 列表存在但 getStringList 返回空 → 项被 YAML 解析为 null（#RRGGBB 被当作注释）
            logger.warning("ArcartXRGB entry[" + entryId + "] 的 gradient-colors 列表包含 "
                + rawList.size() + " 项但全部无效。"
                + " 请检查颜色值是否加了引号，例如: - \"#FF7A18\" （不加引号时 # 会被 YAML 当作注释）");
        } else if (rawList == null) {
            logger.warning("ArcartXRGB entry[" + entryId + "] 未配置 gradient-colors 字段。");
        } else {
            logger.warning("ArcartXRGB entry[" + entryId + "] 的 gradient-colors 列表为空。");
        }
        return List.of(DEFAULT_COLOR);
    }

    private static ArcartRgbColor parseColor(
        String rawValue,
        ArcartRgbColor fallback,
        Logger logger,
        String entryId,
        String field
    ) {
        try {
            return ArcartRgbColor.parse(rawValue);
        } catch (IllegalArgumentException exception) {
            logger.warning("ArcartXRGB entry[" + entryId + "] 的 " + field + " 无效，已回退默认值: " + rawValue);
            return fallback;
        }
    }

    private static String readString(ConfigurationSection configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
