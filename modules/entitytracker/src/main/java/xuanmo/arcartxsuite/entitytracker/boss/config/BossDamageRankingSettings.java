package xuanmo.arcartxsuite.entitytracker.boss.config;

import org.bukkit.configuration.ConfigurationSection;

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

    public static BossDamageRankingSettings defaults() {
        return DEFAULT;
    }
}

