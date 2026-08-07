package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.LocalDate;

/**
 * 周期上下文。
 * <p>
 * 属于 {@code onlinerewards} 模块，封装当前日期、奖励日期键、周键与月键，
 * 供 {@code OnlineRewardsStateEngine} 在周期切换时归零对应统计字段。
 *
 * @param date       当前日期
 * @param rewardDate 奖励日期键（用于当日在线时长归零）
 * @param weekKey    周键（用于本周在线时长归零）
 * @param monthKey   月键（用于本月在线时长归零）
 */
record OnlineRewardsPeriodContext(
    LocalDate date,
    String rewardDate,
    String weekKey,
    String monthKey
) {
}
