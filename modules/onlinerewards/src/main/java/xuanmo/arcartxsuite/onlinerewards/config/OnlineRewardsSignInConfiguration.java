package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsSignInConfiguration(
    boolean reminderOnJoin,
    String signInSuccessMessage,
    String signInRepeatMessage,
    String signInReminderMessage,
    List<String> baseCommands,
    List<String> baseMailPresetIds,
    String baseRewardText,
    OnlineRewardsMakeupConfiguration makeup,
    List<OnlineRewardsMilestoneReward> streakRewards,
    List<OnlineRewardsMilestoneReward> totalRewards,
    List<OnlineRewardsDayOfMonthReward> dayOfMonthRewards,
    List<OnlineRewardsHolidayReward> holidayRewards,
    List<OnlineRewardsPermissionBonusReward> permissionBonusGroups
) {
}
