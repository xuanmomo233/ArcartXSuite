package xuanmo.arcartxsuite.menu.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.Action;

public record MenuItemBinding(
    String menuId,
    String material,
    String nameContains,
    String nameRegex,
    String loreContains,
    int customModelData,
    Action clickAction,
    boolean mainHand,
    boolean offHand,
    String permission
) {

    public static List<MenuItemBinding> loadList(List<?> rawList, String defaultMenuId) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<MenuItemBinding> bindings = new ArrayList<>();
        for (Object raw : rawList) {
            if (raw instanceof ConfigurationSection section) {
                bindings.add(load(section, defaultMenuId));
            } else if (raw instanceof java.util.Map<?, ?> map) {
                org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        memory.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                bindings.add(load(memory, defaultMenuId));
            }
        }
        return List.copyOf(bindings);
    }

    public static MenuItemBinding load(ConfigurationSection section, String defaultMenuId) {
        String menuId = section.getString("menu", section.getString("open", defaultMenuId));
        return new MenuItemBinding(
            menuId == null ? "" : menuId,
            section.getString("material", ""),
            section.getString("name-contains", section.getString("nameContains", "")),
            section.getString("name-regex", section.getString("nameRegex", "")),
            section.getString("lore-contains", section.getString("loreContains", "")),
            section.getInt("custom-model-data", section.getInt("customModelData", -1)),
            parseAction(section.getString("action", section.getString("click", "RIGHT_CLICK"))),
            section.getBoolean("main-hand", section.getBoolean("mainHand", true)),
            section.getBoolean("off-hand", section.getBoolean("offHand", true)),
            section.getString("permission", "")
        );
    }

    private static Action parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            return Action.RIGHT_CLICK_AIR;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "LEFT", "LEFT_CLICK", "LEFT_CLICK_AIR", "LEFT_CLICK_BLOCK" -> Action.LEFT_CLICK_AIR;
            case "PHYSICAL" -> Action.PHYSICAL;
            default -> Action.RIGHT_CLICK_AIR;
        };
    }
}
