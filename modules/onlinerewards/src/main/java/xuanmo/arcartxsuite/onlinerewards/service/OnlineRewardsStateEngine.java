package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

/**
 * 在线奖励状态引擎。
 * <p>
 * 属于 {@code onlinerewards} 模块，负责玩家状态的周期归零、在线时长累计、
 * 阶段奖励触发判定、周期（周/月）奖励触发判定与签到状态查询等纯状态计算逻辑。
 */
public final class OnlineRewardsStateEngine {

    private final OnlineRewardsModuleConfiguration configuration;

    /**
     * 构造状态引擎。
     *
     * @param configuration 模块配置
     */
    public OnlineRewardsStateEngine(OnlineRewardsModuleConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * 按周期上下文归零过期统计字段，并清理不可重复领取的周期奖励记录。
     *
     * @param state   玩家状态
     * @param context 周期上下文
     * @return 状态是否发生变更
     */
    public boolean normalizeForPeriod(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        if (state == null) {
            return false;
        }

        boolean changed = false;
        if (!context.rewardDate().equals(state.rewardDate())) {
            state.setRewardDate(context.rewardDate());
            state.setOnlineMinutes(0);
            state.setRewardStage(0);
            state.setOfflineSavingsEarnedMinutes(0);
            state.setOfflineSavingsCreditedMinutes(0);
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

    void recordSavingsEarnings(OnlineRewardsPlayerState state, int gained) {
        if (configuration.offlineSavings().enabled() && gained > 0) {
            state.setOfflineSavingsEarnedMinutes((int) Math.min(Integer.MAX_VALUE,
                (long) state.offlineSavingsEarnedMinutes() + gained));
        }
    }

    boolean storeOfflineSavings(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        var savings = configuration.offlineSavings();
        if (!savings.enabled() || savings.maxMinutes() <= 0 || savings.storageRate() <= 0
                || !context.rewardDate().equals(state.rewardDate())) return false;
        if (state.offlineSavingsMinutes() > 0 && state.offlineSavingsDay().isBlank()) return false;
        int earned = state.offlineSavingsEarnedMinutes();
        int credited = state.offlineSavingsCreditedMinutes();
        if (earned <= credited) return false;

        int eligible = Math.min(savings.maxMinutes(), (int) Math.floor(earned * savings.storageRate()));
        int accounted = Math.min(savings.maxMinutes(), (int) Math.floor(credited * savings.storageRate()));
        int room = Math.max(0, savings.maxMinutes() - state.offlineSavingsMinutes());
        int deposit = Math.min(room, Math.max(0, eligible - accounted));
        // Mark the measured minutes even when the balance is full or rounded down.
        state.setOfflineSavingsCreditedMinutes(earned);
        if (deposit > 0) {
            state.setOfflineSavingsMinutes(state.offlineSavingsMinutes() + deposit);
            state.setOfflineSavingsDay(context.rewardDate());
        }
        return true;
    }

    boolean redeemOfflineSavings(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        var savings = configuration.offlineSavings();
        if (!savings.enabled() || savings.maxMinutes() <= 0 || state.offlineSavingsMinutes() <= 0) return false;
        LocalDate deposited = dateOrNull(state.offlineSavingsDay());
        if (deposited == null || !context.date().isAfter(deposited)) return false;
        int bonus = Math.min(state.offlineSavingsMinutes(), savings.maxMinutes());
        state.setOnlineMinutes((int) Math.min(Integer.MAX_VALUE, (long) state.onlineMinutes() + bonus));
        state.setOfflineSavingsMinutes(0);
        state.setOfflineSavingsDay("");
        return true;
    }

    private static LocalDate dateOrNull(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private Set<String> retainNonRepeatableIds(Set<String> claimedIds, List<OnlineRewardsPeriodicReward> rewards) {
        Set<String> retained = new HashSet<>();
        for (OnlineRewardsPeriodicReward reward : rewards) {
            if (!reward.repeat() && claimedIds.contains(reward.id())) {
                retained.add(reward.id());
            }
        }
        return Set.copyOf(retained);
    }

    /**
     * 检查并触发本周已达阈值且未领取的周期奖励。
     *
     * @param state 玩家状态
     * @return 本次触发的周期奖励列表
     */
    public List<OnlineRewardsPeriodicReward> checkWeeklyRewards(OnlineRewardsPlayerState state) {
        return checkPeriodicRewards(state, configuration.weeklyRewards(), state.weekMinutes(), state.claimedWeeklyRewardIds(), true);
    }

    /**
     * 检查并触发本月已达阈值且未领取的周期奖励。
     *
     * @param state 玩家状态
     * @return 本次触发的周期奖励列表
     */
    public List<OnlineRewardsPeriodicReward> checkMonthlyRewards(OnlineRewardsPlayerState state) {
        return checkPeriodicRewards(state, configuration.monthlyRewards(), state.monthMinutes(), state.claimedMonthlyRewardIds(), false);
    }

    /**
     * @param weekly true=周奖励，false=月奖励。显式周期标记，
     *               替代原来的 {@code rewards == configuration.weeklyRewards()} 引用相等判断——
     *               若配置每次返回新 list，引用相等恒 false 会把周奖励记录错写到月字段。
     */
    private List<OnlineRewardsPeriodicReward> checkPeriodicRewards(
        OnlineRewardsPlayerState state,
        List<OnlineRewardsPeriodicReward> rewards,
        int minutes,
        Set<String> claimedIds,
        boolean weekly
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
            if (weekly) {
                state.setClaimedWeeklyRewardIds(newlyClaimed);
            } else {
                state.setClaimedMonthlyRewardIds(newlyClaimed);
            }
        }
        return List.copyOf(triggered);
    }

    /**
     * 计算当前阶段进度快照。
     *
     * @param state 玩家状态
     * @return 进度快照
     */
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

    /**
     * 推进 1 分钟在线时长并返回触发结果。
     *
     * @param state   玩家状态
     * @param context 周期上下文
     * @return tick 推进结果
     */
    public OnlineRewardsTickResult advanceOneMinute(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        return advanceMinutes(state, context, 1);
    }

    /**
     * 推进指定分钟数在线时长，累计各周期统计并触发已达阈值的阶段奖励。
     *
     * @param state   玩家状态
     * @param context 周期上下文
     * @param minutes 推进的分钟数
     * @return tick 推进结果
     */
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

    /**
     * 判断玩家在当前周期上下文下今日是否已签到。
     *
     * @param state   玩家状态
     * @param context 周期上下文
     * @return 今日是否已签到
     */
    public boolean hasSignedToday(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        return state != null && context.rewardDate().equals(state.lastSignInDate());
    }

}
