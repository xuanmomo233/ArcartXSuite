package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

public record MenuIconDefinition(
    String material,
    int amount,
    String name,
    java.util.List<String> lore,
    int customModelData,
    String source,
    String sourceId,
    String mmoType,
    String mmoId,
    String json
) {

    public boolean hasIcon() {
        if (json != null && !json.isBlank()) {
            return true;
        }
        if (source != null && !source.isBlank() && sourceId != null && !sourceId.isBlank()) {
            return true;
        }
        if (mmoType != null && !mmoType.isBlank() && mmoId != null && !mmoId.isBlank()) {
            return true;
        }
        return material != null && !material.isBlank();
    }

    @Nullable
    public static MenuIconDefinition load(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        MenuIconDefinition icon = new MenuIconDefinition(
            section.getString("material", ""),
            Math.max(1, section.getInt("amount", 1)),
            section.getString("name", ""),
            section.getStringList("lore"),
            section.getInt("custom-model-data", section.getInt("customModelData", 0)),
            section.getString("source", ""),
            section.getString("id", section.getString("item-id", "")),
            section.getString("mmo-type", ""),
            section.getString("mmo-id", ""),
            section.getString("json", "")
        );
        return icon.hasIcon() ? icon : null;
    }
}
