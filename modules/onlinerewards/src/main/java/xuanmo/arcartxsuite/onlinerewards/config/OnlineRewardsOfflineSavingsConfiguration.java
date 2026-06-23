package xuanmo.arcartxsuite.onlinerewards.config;

/**
 * 离线时长储蓄配置。
 */
public record OnlineRewardsOfflineSavingsConfiguration(
    boolean enabled,
    int maxMinutes,
    double storageRate,
    int expireDays
) {
}
