package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Boss 伤害排名奖励总设置，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 负责从配置节解析排名奖励开关以及各名次奖励定义，
 * 供结算服务在 Boss 死亡后按名次发放奖励。
 * <p>
 * 物品发放的背包满处理统一由 {@link xuanmo.arcartxsuite.api.item.ItemRewardDispatcher} 负责
 * （放入背包→背包满转邮件→无邮件则掉落），不再需要配置策略。
 *
 * @param enabled               是否启用排名奖励
 * @param rankRewards           名次到奖励定义的映射，键为名次（从 1 开始）
 */
public record BossDamageRankingRewardsSettings(
    boolean enabled,
    Map<Integer, BossDamageRankRewardDefinition> rankRewards
) {

    private static final BossDamageRankingRewardsSettings DEFAULT = new BossDamageRankingRewardsSettings(
        false,
        Map.of()
    );

    /**
     * 从 Bukkit 配置节解析排名奖励设置。
     *
     * @param section 配置节，为 {@code null} 时返回默认值
     * @return 解析得到的设置实例
     */
    public static BossDamageRankingRewardsSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }

        boolean enabled = section.getBoolean("enabled", DEFAULT.enabled());

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
            Collections.unmodifiableMap(rankRewards)
        );
    }

    /**
     * 返回未启用奖励的默认设置。
     *
     * @return 默认设置实例
     */
    public static BossDamageRankingRewardsSettings defaults() {
        return DEFAULT;
    }

    /**
     * 获取指定名次的奖励定义，名次无配置时返回空定义。
     *
     * @param rank 名次（从 1 开始）
     * @return 该名次的奖励定义
     */
    public BossDamageRankRewardDefinition rewardForRank(int rank) {
        BossDamageRankRewardDefinition definition = rankRewards.get(rank);
        return definition == null ? BossDamageRankRewardDefinition.empty(rank) : definition;
    }
}
