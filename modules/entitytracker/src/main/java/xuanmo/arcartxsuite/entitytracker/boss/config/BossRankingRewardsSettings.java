package xuanmo.arcartxsuite.entitytracker.boss.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Boss 排行榜定时奖励配置（周 / 月），独立于即时死亡结算奖励。
 * 每个周期各自一套 {@link BossDamageRankingRewardsSettings}。
 */
public record BossRankingRewardsSettings(
    BossDamageRankingRewardsSettings weekly,
    BossDamageRankingRewardsSettings monthly
) {

    private static final BossRankingRewardsSettings DEFAULT = new BossRankingRewardsSettings(
        BossDamageRankingRewardsSettings.defaults(),
        BossDamageRankingRewardsSettings.defaults()
    );

    public static BossRankingRewardsSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }

        BossDamageRankingRewardsSettings weekly = BossDamageRankingRewardsSettings.from(
            section.getConfigurationSection("weekly")
        );
        BossDamageRankingRewardsSettings monthly = BossDamageRankingRewardsSettings.from(
            section.getConfigurationSection("monthly")
        );

        return new BossRankingRewardsSettings(weekly, monthly);
    }

    public static BossRankingRewardsSettings defaults() {
        return DEFAULT;
    }

    /**
     * 根据周期类型获取对应的奖励配置
     */
    public BossDamageRankingRewardsSettings forPeriod(String periodType) {
        return switch (periodType) {
            case "weekly" -> weekly;
            case "monthly" -> monthly;
            default -> BossDamageRankingRewardsSettings.defaults();
        };
    }
}
