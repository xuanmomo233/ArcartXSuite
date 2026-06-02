package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record BossDamageRankingRewardsSettings(
    boolean enabled,
    BossDamageRewardInventoryFullStrategy inventoryFullStrategy,
    Map<Integer, BossDamageRankRewardDefinition> rankRewards
) {

    private static final BossDamageRankingRewardsSettings DEFAULT = new BossDamageRankingRewardsSettings(
        false,
        BossDamageRewardInventoryFullStrategy.DROP,
        Map.of()
    );

    public static BossDamageRankingRewardsSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }

        boolean enabled = section.getBoolean("enabled", DEFAULT.enabled());
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy = BossDamageRewardInventoryFullStrategy.parse(
            section.getString("inventory-full", DEFAULT.inventoryFullStrategy().configKey())
        );

        Map<Integer, BossDamageRankRewardDefinition> rankRewards = new LinkedHashMap<>();
        ConfigurationSection ranksSection = section.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String rankKey : ranksSection.getKeys(false)) {
                int rank;
                try {
                    rank = Integer.parseInt(rankKey.trim());
                } catch (NumberFormatException exception) {
                    continue;
                }
                if (rank <= 0) {
                    continue;
                }

                List<BossDamageRewardAction> actions = new ArrayList<>();
                for (Map<?, ?> rawAction : ranksSection.getMapList(rankKey + ".actions")) {
                    BossDamageRewardAction action = BossDamageRewardAction.from(rawAction);
                    if (action != null) {
                        actions.add(action);
                    }
                }
                rankRewards.put(
                    rank,
                    new BossDamageRankRewardDefinition(rank, List.copyOf(actions))
                );
            }
        }

        return new BossDamageRankingRewardsSettings(
            enabled,
            inventoryFullStrategy,
            Collections.unmodifiableMap(rankRewards)
        );
    }

    public static BossDamageRankingRewardsSettings defaults() {
        return DEFAULT;
    }

    public BossDamageRankRewardDefinition rewardForRank(int rank) {
        BossDamageRankRewardDefinition definition = rankRewards.get(rank);
        return definition == null ? BossDamageRankRewardDefinition.empty(rank) : definition;
    }
}

