package xuanmo.arcartxsuite.menu.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record MenuPageDefinition(
    String id,
    String title,
    Map<String, MenuButtonDefinition> buttons
) {

    public static MenuPageDefinition load(ConfigurationSection section) {
        if (section == null) {
            return new MenuPageDefinition("main", "", Map.of());
        }
        String id = section.getString("id", "main");
        String title = section.getString("title", id);
        Map<String, MenuButtonDefinition> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonsSection = section.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            for (String buttonId : buttonsSection.getKeys(false)) {
                buttons.put(buttonId, MenuButtonDefinition.load(buttonId, buttonsSection.getConfigurationSection(buttonId)));
            }
        }
        return new MenuPageDefinition(id, title, Map.copyOf(buttons));
    }

    public static List<MenuPageDefinition> loadList(List<Map<?, ?>> rawPages) {
        if (rawPages == null || rawPages.isEmpty()) {
            return List.of(new MenuPageDefinition("main", "Menu", Map.of()));
        }
        List<MenuPageDefinition> pages = new ArrayList<>();
        org.yaml.snakeyaml.Yaml yamlDumper = new org.yaml.snakeyaml.Yaml();
        for (Map<?, ?> rawPage : rawPages) {
            if (rawPage == null) {
                continue;
            }
            String dump = yamlDumper.dump(rawPage);
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            try {
                config.loadFromString(dump);
                pages.add(load(config));
            } catch (Exception ignored) {
                // 跳过格式错误的 page
            }
        }
        return List.copyOf(pages);
    }
}
