package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

class OnlineRewardsStateEngineTest {
    @Test
    void weeklyRolloverReopensOnlyRepeatableRewards() {
        var repeated = reward("weekly-repeat", true);
        var once = reward("weekly-once", false);
        var engine = engine(List.of(repeated, once), List.of());
        var state = new OnlineRewardsPlayerState();
        var first = period("2026-11-16", "2026-W47", "2026-11");
        var next = period("2026-11-23", "2026-W48", "2026-11");

        engine.advanceMinutes(state, first, 30);
        assertEquals(List.of(repeated, once), engine.checkWeeklyRewards(state));
        assertTrue(engine.checkWeeklyRewards(state).isEmpty());

        engine.advanceMinutes(state, next, 29);
        assertEquals(Set.of(once.id()), state.claimedWeeklyRewardIds());
        assertTrue(engine.checkWeeklyRewards(state).isEmpty());
        engine.advanceMinutes(state, next, 1);
        assertEquals(List.of(repeated), engine.checkWeeklyRewards(state));
        assertTrue(engine.checkWeeklyRewards(state).isEmpty());
    }

    @Test
    void monthlyRolloverReopensOnlyRepeatableRewards() {
        var repeated = reward("monthly-repeat", true);
        var once = reward("monthly-once", false);
        var engine = engine(List.of(), List.of(repeated, once));
        var state = new OnlineRewardsPlayerState();
        var first = period("2026-11-30", "2026-W49", "2026-11");
        var next = period("2026-12-01", "2026-W49", "2026-12");

        engine.advanceMinutes(state, first, 30);
        assertEquals(List.of(repeated, once), engine.checkMonthlyRewards(state));
        assertTrue(engine.checkMonthlyRewards(state).isEmpty());

        engine.advanceMinutes(state, next, 29);
        assertEquals(Set.of(once.id()), state.claimedMonthlyRewardIds());
        assertTrue(engine.checkMonthlyRewards(state).isEmpty());
        engine.advanceMinutes(state, next, 1);
        assertEquals(List.of(repeated), engine.checkMonthlyRewards(state));
        assertTrue(engine.checkMonthlyRewards(state).isEmpty());
    }

    private static OnlineRewardsStateEngine engine(List<OnlineRewardsPeriodicReward> weekly, List<OnlineRewardsPeriodicReward> monthly) {
        var configuration = new OnlineRewardsModuleConfiguration(false, 20, "Asia/Shanghai", "完成", "progress", "title",
            null, null, null, null, List.of(), List.of(), weekly, monthly, null, null, List.of());
        return new OnlineRewardsStateEngine(configuration);
    }

    private static OnlineRewardsPeriodicReward reward(String id, boolean repeat) {
        return new OnlineRewardsPeriodicReward(id, 30, id, "", List.of(), List.of(), repeat, List.of(), null);
    }

    private static OnlineRewardsPeriodContext period(String date, String week, String month) {
        return new OnlineRewardsPeriodContext(LocalDate.parse(date), date, week, month);
    }
}
