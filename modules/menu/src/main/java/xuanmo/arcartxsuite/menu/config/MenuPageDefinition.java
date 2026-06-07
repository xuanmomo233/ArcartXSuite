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
        for (Map<?, ?> rawPage : rawPages) {
            if (rawPage == null) {
                continue;
            }
            org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
            for (Map.Entry<?, ?> entry : rawPage.entrySet()) {
                if (entry.getKey() != null) {
                    memory.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            pages.add(load(memory));
        }
        return List.copyOf(pages);
    }
}
