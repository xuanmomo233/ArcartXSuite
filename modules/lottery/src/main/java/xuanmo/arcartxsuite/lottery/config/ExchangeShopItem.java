package xuanmo.arcartxsuite.lottery.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PoolItem;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 兑换商店商品。
 */
public record ExchangeShopItem(
    @NotNull String id,
    @NotNull String displayName,
    @Nullable IconDefinition icon,
    @Nullable String poolId,
    @NotNull List<CostConfig.CostEntry> cost,
    @NotNull PoolItem reward,
    int limit
) {
    public static ExchangeShopItem load(ConfigurationSection section,
                                        java.util.logging.Logger logger) {
        String id = section.getString("id", section.getName());
        String displayName = section.getString("display-name", id);
        IconDefinition icon = IconDefinition.load(section, "icon");
        String poolId = section.getString("pool-id");
        List<CostConfig.CostEntry> cost;
        ConfigurationSection costSection = section.getConfigurationSection("cost");
        if (costSection != null) {
            cost = CostConfig.load(costSection, "exchange item " + id).single();
        } else {
            cost = CostConfig.loadDirectList(section.getMapList("cost"), "exchange item " + id);
        }
        ConfigurationSection rewardSection = section.getConfigurationSection("reward");
        if (rewardSection == null) {
            throw new IllegalArgumentException("Exchange item missing reward: " + id);
        }
        PoolItem reward = PoolItem.load(rewardSection, logger);
        if (reward == null) throw new IllegalArgumentException("Invalid exchange reward: " + id);
        return new ExchangeShopItem(id, displayName, icon, poolId, cost,
            reward, section.getInt("limit", 0));
    }
}
