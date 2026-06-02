package xuanmo.arcartxsuite.prop.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PropKeyMappingConfiguration(
    String category,
    Map<String, PropKeyBindingDefinition> bindings
) {

    public PropKeyMappingConfiguration {
        bindings = Map.copyOf(bindings);
    }

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
                bindings.put(
                    bindingId,
                    new PropKeyBindingDefinition(
                        bindingId,
                        readString(child, "defaultKey", "X"),
                        readString(child, "slot", "Slot1")
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
