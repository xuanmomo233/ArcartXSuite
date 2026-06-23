package xuanmo.arcartxsuite.onlinerewards.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public final class OnlineRewardsStateEngine {

    private final OnlineRewardsModuleConfiguration configuration;

    public OnlineRewardsStateEngine(OnlineRewardsModuleConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean normalizeForPeriod(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        if (state == null) {
            return false;
        }

        boolean changed = false;
        if (!context.rewardDate().equals(state.rewardDate())) {
            state.setRewardDate(context.rewardDate());
            state.setOnlineMinutes(0);
            state.setRewardStage(0);
            changed = true;
        }
        if (!context.weekKey().equals(state.weekKey())) {
            state.setWeekKey(context.weekKey());
            state.setWeekMinutes(0);
            state.setClaimedWeeklyRewardIds(retainNonRepeatableIds(state.claimedWeeklyRewardIds(), configuration.weeklyRewards()));
            changed = true;
        }
        if (!context.monthKey().equals(state.monthKey())) {
            state.setMonthKey(context.monthKey());
            state.setMonthMinutes(0);
            state.setClaimedMonthlyRewardIds(retainNonRepeatableIds(state.claimedMonthlyRewardIds(), configuration.monthlyRewards()));
            changed = true;
        }
        return changed;
    }

    private Set<String> retainNonRepeatableIds(Set<String> claimedIds, List<OnlineRewardsPeriodicReward> rewards) {
        Set<String> repeatableIds = new HashSet<>();
        for (OnlineRewardsPeriodicReward reward : rewards) {
            if (reward.repeat() && claimedIds.contains(reward.id())) {
                repeatableIds.add(reward.id());
            }
        }
        return Set.copyOf(repeatableIds);
    }

    public List<OnlineRewardsPeriodicReward> checkWeeklyRewards(OnlineRewardsPlayerState state) {
        return checkPeriodicRewards(state, configuration.weeklyRewards(), state.weekMinutes(), state.claimedWeeklyRewardIds());
    }

    public List<OnlineRewardsPeriodicReward> checkMonthlyRewards(OnlineRewardsPlayerState state) {
        return checkPeriodicRewards(state, configuration.monthlyRewards(), state.monthMinutes(), state.claimedMonthlyRewardIds());
    }

    private List<OnlineRewardsPeriodicReward> checkPeriodicRewards(
        OnlineRewardsPlayerState state,
        List<OnlineRewardsPeriodicReward> rewards,
        int minutes,
        Set<String> claimedIds
    ) {
        List<OnlineRewardsPeriodicReward> triggered = new ArrayList<>();
        Set<String> newlyClaimed = new HashSet<>(claimedIds);
        for (OnlineRewardsPeriodicReward reward : rewards) {
            if (minutes >= reward.minutes() && !claimedIds.contains(reward.id())) {
                triggered.add(reward);
                newlyClaimed.add(reward.id());
            }
        }
        if (!triggered.isEmpty()) {
            if (rewards == configuration.weeklyRewards()) {
                state.setClaimedWeeklyRewardIds(newlyClaimed);
            } else {
                state.setClaimedMonthlyRewardIds(newlyClaimed);
            }
        }
        return List.copyOf(triggered);
    }

    public OnlineRewardsProgressSnapshot snapshot(OnlineRewardsPlayerState state) {
        List<OnlineRewardDefinition> rewards = configuration.rewards();
        if (rewards.isEmpty() || state.rewardStage() >= rewards.size()) {
            return new OnlineRewardsProgressSnapshot(1.0F, configuration.doneMessage(), true);
        }

        OnlineRewardDefinition currentReward = rewards.get(state.rewardStage());
        int thresholdMinutes = Math.max(1, currentReward.minutes());
        float progress = Math.min(1.0F, state.onlineMinutes() / (float) thresholdMinutes);
        return new OnlineRewardsProgressSnapshot(progress, currentReward.name(), false);
    }

    public OnlineRewardsTickResult advanceOneMinute(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        return advanceMinutes(state, context, 1);
    }

    public OnlineRewardsTickResult advanceMinutes(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context, int minutes) {
        boolean changed = normalizeForPeriod(state, context);
        int safeMinutes = Math.max(0, minutes);
        if (safeMinutes > 0) {
            state.setOnlineMinutes(state.onlineMinutes() + safeMinutes);
            state.setWeekMinutes(state.weekMinutes() + safeMinutes);
            state.setMonthMinutes(state.monthMinutes() + safeMinutes);
            state.setTotalMinutes(state.totalMinutes() + safeMinutes);
            changed = true;
        }

        List<OnlineRewardDefinition> triggeredRewards = new ArrayList<>();
        while (state.rewardStage() < configuration.rewards().size()) {
            OnlineRewardDefinition currentReward = configuration.rewards().get(state.rewardStage());
            if (state.onlineMinutes() >= currentReward.minutes()) {
                triggeredRewards.add(currentReward);
                state.setRewardStage(state.rewardStage() + 1);
            } else {
                break;
            }
        }
        return new OnlineRewardsTickResult(changed, List.copyOf(triggeredRewards), snapshot(state));
    }

    public boolean hasSignedToday(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        return state != null && context.rewardDate().equals(state.lastSignInDate());
    }

}
