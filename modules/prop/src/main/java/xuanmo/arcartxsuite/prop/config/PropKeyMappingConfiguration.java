package xuanmo.arcartxsuite.prop.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 按键映射配置模型。
 * <p>
 * 从 {@code key.yml} 解析，包含按键分类名称与全部按键绑定定义。
 *
 * @param category 按键分类显示名称
 * @param bindings 以绑定 ID 为键的绑定定义 Map
 */
public record PropKeyMappingConfiguration(
    String category,
    Map<String, PropKeyBindingDefinition> bindings
) {

    public PropKeyMappingConfiguration {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    /**
     * 从 YAML 配置加载按键映射。
     *
     * @param configuration YAML 配置
     * @return 解析后的 {@link PropKeyMappingConfiguration}
     */
    public static PropKeyMappingConfiguration load(FileConfiguration configuration) {
        String category = readString(configuration, "category", "ArcartX 快捷道具按键");
        LinkedHashMap<String, PropKeyBindingDefinition> bindings = new LinkedHashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("keys");
        if (section != null) {
            for (String bindingId : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(bindingId);
                if (child == null) {
                    continue;
                }
                PropKeyBindingDefinition.Mode mode;
                try {
                    mode = PropKeyBindingDefinition.Mode.parse(readString(child, "mode", "prop"));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("key.yml 的 keys." + bindingId + ".mode: " + exception.getMessage(), exception);
                }
                bindings.put(
                    bindingId,
                    new PropKeyBindingDefinition(
                        bindingId,
                        readString(child, "defaultKey", "X"),
                        readString(child, "slot", "Slot1"),
                        mode
                    )
                );
            }
        }
        return new PropKeyMappingConfiguration(category, bindings);
    }

    private static String readString(ConfigurationSection configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
