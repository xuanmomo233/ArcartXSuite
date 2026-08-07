package xuanmo.arcartxsuite.entitytracker.boss.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Boss 伤害排名设置，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 聚合排名开关、最大条目数、最低伤害门槛以及奖励设置，
 * 是 BossDefinition 中 damage-ranking 配置节的根设置对象。
 *
 * @param enabled            是否启用伤害排名
 * @param maxEntries         排名最大条目数（至少为 1）
 * @param minDamageThreshold 参与排名的最低伤害门槛
 * @param rewards            旧版排名奖励设置
 * @param rankingRewards     新版排名奖励设置
 */
public record BossDamageRankingSettings(
    boolean enabled,
    int maxEntries,
    DamageThreshold minDamageThreshold,
    BossDamageRankingRewardsSettings rewards,
    BossRankingRewardsSettings rankingRewards
) {

    private static final int DEFAULT_MAX_ENTRIES = 10;
    private static final BossDamageRankingSettings DEFAULT = new BossDamageRankingSettings(
        true,
        DEFAULT_MAX_ENTRIES,
        DamageThreshold.defaults(),
        BossDamageRankingRewardsSettings.defaults(),
        BossRankingRewardsSettings.defaults()
    );

    public BossDamageRankingSettings(boolean enabled, int maxEntries, DamageThreshold minDamageThreshold) {
        this(enabled, maxEntries, minDamageThreshold, BossDamageRankingRewardsSettings.defaults(), BossRankingRewardsSettings.defaults());
    }

    public BossDamageRankingSettings(boolean enabled, int maxEntries, DamageThreshold minDamageThreshold, BossDamageRankingRewardsSettings rewards) {
        this(enabled, maxEntries, minDamageThreshold, rewards, BossRankingRewardsSettings.defaults());
    }

    /**
     * 从 Bukkit 配置节解析伤害排名设置。
     *
     * @param section 配置节，为 {@code null} 时返回默认值
     * @return 解析得到的设置实例
     */
    public static BossDamageRankingSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }

        boolean enabled = section.getBoolean("enabled", DEFAULT.enabled());
        int maxEntries = Math.max(1, section.getInt("max-entries", DEFAULT.maxEntries()));
        DamageThreshold minDamageThreshold = DamageThreshold.parse(
            section.getString("min-damage", DEFAULT.minDamageThreshold().displayText())
        );
        BossDamageRankingRewardsSettings rewards = BossDamageRankingRewardsSettings.from(section.getConfigurationSection("rewards"));
        BossRankingRewardsSettings rankingRewards = BossRankingRewardsSettings.from(section.getConfigurationSection("ranking-rewards"));
        return new BossDamageRankingSettings(enabled, maxEntries, minDamageThreshold, rewards, rankingRewards);
    }

    /**
     * 返回默认的伤害排名设置。
     *
     * @return 默认设置实例
     */
    public static BossDamageRankingSettings defaults() {
        return DEFAULT;
    }
}

