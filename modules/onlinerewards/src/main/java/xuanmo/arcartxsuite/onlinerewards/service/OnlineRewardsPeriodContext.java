package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.LocalDate;

record OnlineRewardsPeriodContext(
    LocalDate date,
    String rewardDate,
    String weekKey,
    String monthKey
) {
}
