package xuanmo.arcartxsuite.lottery.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

public record GachaPoolConfig(
    GachaPoolType poolType,
    int pity5star,
    int pity4star,
    int softPityStart,
    double softPityIncrement,
    double base5starRate,
    double base4starRate,
    double upRate,
    int fatePointCap,
    @Nullable String sharedPityGroup,
    @NotNull List<PoolItem> up5starItems,
    @NotNull List<PoolItem> standard5starItems,
    @NotNull List<PoolItem> up4starItems,
    @NotNull List<PoolItem> standard4starItems,
    @NotNull List<PoolItem> star3Items
) {
    public enum GachaPoolType {
        CHARACTER, WEAPON, STANDARD
    }

    public static GachaPoolConfig load(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();

        String poolTypeStr = section.getString("pool-type", "CHARACTER");
        GachaPoolType poolType = GachaPoolType.valueOf(poolTypeStr.toUpperCase());

        int pity5 = section.getInt("pity-5star", 90);
        int pity4 = section.getInt("pity-4star", 10);
        int softStart = section.getInt("soft-pity-start", 73);
        double softInc = section.getDouble("soft-pity-increment", 0.06);
        double base5 = section.getDouble("base-5star-rate", 0.006);
        double base4 = section.getDouble("base-4star-rate", 0.051);
        double upRate = section.getDouble("up-rate", 0.5);
        int fateCap = section.getInt("fate-point-cap", 0);
        String sharedGroup = section.getString("shared-pity-group");
        if (sharedGroup != null && sharedGroup.isBlank()) sharedGroup = null;

        List<PoolItem> up5 = loadItems(section.getConfigurationSection("up-5star-items"), logger);
        List<PoolItem> std5 = loadItems(section.getConfigurationSection("standard-5star-items"), logger);
        List<PoolItem> up4 = loadItems(section.getConfigurationSection("up-4star-items"), logger);
        List<PoolItem> std4 = loadItems(section.getConfigurationSection("standard-4star-items"), logger);
        List<PoolItem> star3 = loadItems(section.getConfigurationSection("star3-items"), logger);

        return new GachaPoolConfig(
            poolType, pity5, pity4, softStart, softInc, base5, base4, upRate, fateCap, sharedGroup,
            up5, std5, up4, std4, star3
        );
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
