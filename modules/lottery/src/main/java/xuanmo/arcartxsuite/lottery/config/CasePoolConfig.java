package xuanmo.arcartxsuite.lottery.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

public record CasePoolConfig(
    @NotNull Map<String, RaritySetting> raritySettings,
    double stattrakChance,
    @NotNull List<PoolItem> items
) {

    public record RaritySetting(
        String color,
        int baseWeight
    ) {}

    public static CasePoolConfig load(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();

        Map<String, RaritySetting> raritySettings = loadRaritySettings(section.getConfigurationSection("rarity-settings"));
        double stattrakChance = section.getDouble("stattrak-chance", 0.1);
        List<PoolItem> items = loadItems(section.getConfigurationSection("items"), logger);

        return new CasePoolConfig(raritySettings, stattrakChance, items);
    }

    private static Map<String, RaritySetting> loadRaritySettings(@Nullable ConfigurationSection section) {
        Map<String, RaritySetting> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection rs = section.getConfigurationSection(key);
            if (rs == null) continue;
            result.put(key.toUpperCase(), new RaritySetting(
                rs.getString("color", "&f"),
                rs.getInt("base-weight", 1)
            ));
        }
        return result;
    }

    private static List<PoolItem> loadItems(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) return List.of();
        List<PoolItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(key);
            if (itemSec == null) continue;
            PoolItem item = PoolItem.load(itemSec, logger);
            if (item != null) items.add(item);
        }
        return Collections.unmodifiableList(items);
    }
}
