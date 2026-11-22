package xuanmo.arcartxsuite.prop.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

/**
 * 道具定义加载器。
 * <p>
 * 工具类，负责从 {@code props/} 目录递归扫描 YAML 文件并解析为 {@link PropDefinition} 集合。
 * 文件相对路径（去除扩展名）作为道具 ID，小写归一化后存入返回的 Map。
 */
public final class PropDefinitionLoader {

    private PropDefinitionLoader() {
    }

    /**
     * 扫描道具目录并加载全部定义。
     *
     * @param propsDirectory 道具目录，为 {@code null} 或不存在时返回空 Map
     * @param logger         日志记录器，用于输出警告信息
     * @return 以小写归一化 ID 为键的不可变 Map
     * @throws IOException 读取文件时发生 I/O 错误
     */
    public static Map<String, PropDefinition> load(File propsDirectory, Logger logger) throws IOException {
        if (propsDirectory == null || !propsDirectory.exists()) {
            return Map.of();
        }

        LinkedHashMap<String, PropDefinition> definitions = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        try (Stream<Path> stream = Files.walk(propsDirectory.toPath())) {
            for (Path file : stream
                .filter(Files::isRegularFile)
                .filter(PropDefinitionLoader::isYamlFile)
                .sorted()
                .toList()
            ) {
                String id = toDefinitionId(propsDirectory.toPath(), file).trim();
                String key = id.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    definitions.remove(key);
                    if (logger != null) logger.warning("Prop 道具 ID 重复，已停用: " + id);
                    continue;
                }
                try {
                    FileConfiguration configuration = new YamlConfiguration();
                    try (var reader = Files.newBufferedReader(file)) {
                        configuration.load(reader);
                    }
                    definitions.put(key, loadDefinition(id, configuration, logger));
                } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
                    if (logger != null) logger.warning("Prop 无法载入 " + file + ": " + exception.getMessage());
                }
            }
        }
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * 从单个 YAML 配置解析道具定义。
     *
     * @param id           道具 ID（相对路径）
     * @param configuration YAML 配置
     * @param logger       日志记录器
     * @return 解析后的 {@link PropDefinition}
     */
    public static PropDefinition loadDefinition(String id, FileConfiguration configuration, Logger logger) {
        String normalizedId = safe(id);
        String displayName = readString(configuration, "name", "");
        String coolDownGroup = readString(configuration, "coolDownGroup", "默认冷却组");
        int coolDownTimeSeconds = Math.max(0, configuration.getInt("coolDownTime", 10));
        boolean remove = configuration.getBoolean("remove", true);
        boolean hand = configuration.getBoolean("hand", true);
        boolean key = configuration.getBoolean("key", true);
        String permission = readString(configuration, "permission", "");
        PropEffects effects = PropEffects.load(configuration);
        List<ScriptCondition> conditions = readConditions(configuration);
        PropAutoTriggerDefinition auto = readAutoTrigger(configuration, normalizedId, logger);

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
            remove,
            hand,
            key,
            permission,
            effects,
            conditions,
            auto
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

    private static List<ScriptCondition> readConditions(ConfigurationSection configuration) {
        List<ScriptCondition> conditions = ScriptConditionsLoader.loadModuleConditions(configuration);
        Object raw = configuration.get("conditions");
        if (raw == null) return conditions;
        int count;
        if (raw instanceof ConfigurationSection nested) {
            count = 0;
            for (String key : nested.getKeys(false)) {
                if ("list".equals(key)) {
                    count += countEntries(nested.getList(key));
                } else if (nested.isConfigurationSection(key)) {
                    count++;
                } else {
                    throw new IllegalArgumentException("conditions." + key + " 必须为配置节");
                }
            }
        } else {
            count = countEntries(configuration.getList("conditions"));
        }
        if (count != conditions.size()) {
            throw new IllegalArgumentException("conditions 含有无法解析的条件");
        }
        return conditions;
    }

    private static int countEntries(List<?> entries) {
        if (entries == null) throw new IllegalArgumentException("conditions 必须为列表或配置节");
        int count = 0;
        for (Object entry : entries) {
            if (entry != null && !(entry instanceof String text && text.isBlank())) count++;
        }
        return count;
    }

    private static PropAutoTriggerDefinition readAutoTrigger(FileConfiguration configuration, String propId, Logger logger) {
        ConfigurationSection section = configuration.getConfigurationSection("auto");
        if (section == null) {
            return PropAutoTriggerDefinition.DISABLED;
        }
        List<ScriptCondition> conditions;
        try {
            conditions = readConditions(section);
        } catch (IllegalArgumentException exception) {
            if (logger != null) logger.warning("Prop " + propId + " 已停用自动触发: " + exception.getMessage());
            return PropAutoTriggerDefinition.DISABLED;
        }
        return new PropAutoTriggerDefinition(
            section.getBoolean("enabled", false),
            Math.max(0, section.getInt("interval", 0)),
            section.getBoolean("notify", true),
            conditions
        );
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
