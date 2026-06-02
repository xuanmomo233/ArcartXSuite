package xuanmo.arcartxsuite.prop.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PropDefinitionLoader {

    private PropDefinitionLoader() {
    }

    public static Map<String, PropDefinition> load(File propsDirectory, Logger logger) throws IOException {
        if (propsDirectory == null || !propsDirectory.exists()) {
            return Map.of();
        }

        LinkedHashMap<String, PropDefinition> definitions = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(propsDirectory.toPath())) {
            for (Path file : stream
                .filter(Files::isRegularFile)
                .filter(PropDefinitionLoader::isYamlFile)
                .sorted()
                .toList()
            ) {
                String id = toDefinitionId(propsDirectory.toPath(), file);
                FileConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
                PropDefinition definition = loadDefinition(id, configuration, logger);
                definitions.put(id.toLowerCase(Locale.ROOT), definition);
            }
        }
        return Map.copyOf(definitions);
    }

    public static PropDefinition loadDefinition(String id, FileConfiguration configuration, Logger logger) {
        String normalizedId = safe(id);
        String displayName = readString(configuration, "name", normalizedId);
        String coolDownGroup = readString(configuration, "coolDownGroup", "默认冷却组");
        int coolDownTimeSeconds = Math.max(0, configuration.getInt("coolDownTime", 10));
        int durationSeconds = Math.max(0, configuration.getInt("Duration", 10));
        boolean remove = configuration.getBoolean("remove", true);
        boolean hand = configuration.getBoolean("hand", true);
        boolean key = configuration.getBoolean("key", true);
        String permission = readString(configuration, "permission", "");
        List<String> effects = readEffects(configuration);
        List<PropCondition> conditions = readConditions(configuration, normalizedId, logger);

        if (displayName.isBlank()) {
            displayName = normalizedId;
        }
        if (coolDownGroup.isBlank()) {
            coolDownGroup = "默认冷却组";
            if (logger != null) {
                logger.warning("Prop " + normalizedId + " 未填写 coolDownGroup，已回退为 默认冷却组。");
            }
        }

        return new PropDefinition(
            normalizedId,
            displayName,
            coolDownGroup,
            coolDownTimeSeconds,
            durationSeconds,
            remove,
            hand,
            key,
            permission,
            effects,
            conditions
        );
    }

    private static boolean isYamlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yml") || fileName.endsWith(".yaml");
    }

    private static String toDefinitionId(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        int extensionIndex = relative.lastIndexOf('.');
        return extensionIndex > 0 ? relative.substring(0, extensionIndex) : relative;
    }

    private static List<String> readEffects(FileConfiguration configuration) {
        List<String> effects = new ArrayList<>();
        List<?> rawList = configuration.getList("effects");
        if (rawList == null) {
            return List.of();
        }
        for (Object raw : rawList) {
            String effect = safe(raw == null ? "" : String.valueOf(raw));
            if (!effect.isBlank()) {
                effects.add(effect);
            }
        }
        return List.copyOf(effects);
    }

    private static List<PropCondition> readConditions(FileConfiguration configuration, String propId, Logger logger) {
        List<?> rawList = configuration.getList("conditions");
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<PropCondition> conditions = new ArrayList<>();
        for (Object raw : rawList) {
            String line = safe(raw == null ? "" : String.valueOf(raw));
            if (line.isBlank()) {
                continue;
            }
            PropCondition condition = PropCondition.parse(line);
            if (condition != null) {
                conditions.add(condition);
            } else if (logger != null) {
                logger.warning("Prop " + propId + " 条件格式无效，已跳过: " + line);
            }
        }
        return List.copyOf(conditions);
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
