package xuanmo.arcartxsuite.lottery.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

public record CostConfig(
    CostMode mode,
    String currency,
    int single,
    int ten,
    String itemId,
    int itemAmount
) {
    public enum CostMode {
        CURRENCY, ITEM
    }

    public static CostConfig load(@Nullable ConfigurationSection section) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
        String modeStr = section.getString("mode", "CURRENCY");
        CostMode mode = CostMode.valueOf(modeStr.toUpperCase());
        return new CostConfig(
            mode,
            section.getString("currency", ""),
            section.getInt("single", 1),
            section.getInt("ten", 10),
            section.getString("item-id", ""),
            section.getInt("item-amount", 1)
        );
    }
}
