package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsOfflineSavingsConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

class OnlineRewardsOfflineSavingsTest {
    private static final OnlineRewardsPeriodContext DAY = period("2026-11-16");
    private static final OnlineRewardsPeriodContext NEXT = period("2026-11-17");

    @Test
    void repeatedLogoutOnlyStoresNewMeasuredMinutesAndCarriesRounding() {
        var engine = engine(120, 0.5);
        var state = new OnlineRewardsPlayerState();
        engine.normalizeForPeriod(state, DAY);
        state.setOnlineMinutes(200);
        assertFalse(engine.storeOfflineSavings(state, DAY));
        engine.recordSavingsEarnings(state, 5);
        assertTrue(engine.storeOfflineSavings(state, DAY));
        assertEquals(2, state.offlineSavingsMinutes());
        assertFalse(engine.storeOfflineSavings(state.copy(), DAY));
        engine.recordSavingsEarnings(state, 1);
        assertTrue(engine.storeOfflineSavings(state, DAY));
        assertEquals(3, state.offlineSavingsMinutes());
    }

    @Test
    void capAndManualResetDoNotMakeAlreadyAccountedMinutesEarnAgain() {
        var engine = engine(2, 0.5);
        var state = new OnlineRewardsPlayerState();
        engine.normalizeForPeriod(state, DAY);
        engine.recordSavingsEarnings(state, 10);
        assertTrue(engine.storeOfflineSavings(state, DAY));
        assertEquals(2, state.offlineSavingsMinutes());
        state.setOfflineSavingsMinutes(0);
        assertFalse(engine.storeOfflineSavings(state, DAY));
        engine.recordSavingsEarnings(state, 2);
        assertTrue(engine.storeOfflineSavings(state, DAY));
        assertEquals(0, state.offlineSavingsMinutes());
        assertEquals(12, state.offlineSavingsCreditedMinutes());
    }

    @Test
    void balanceSurvivesNormalizationAndRedeemsOnlyIntoTheNewDailyTotal() {
        var engine = engine(120, 1);
        var state = new OnlineRewardsPlayerState();
        engine.advanceMinutes(state, DAY, 6);
        engine.recordSavingsEarnings(state, 6);
        assertTrue(engine.storeOfflineSavings(state, DAY));
        assertFalse(engine.redeemOfflineSavings(state, DAY));
        engine.normalizeForPeriod(state, NEXT);
        assertEquals(6, state.offlineSavingsMinutes());
        assertEquals(0, state.onlineMinutes());
        assertTrue(engine.redeemOfflineSavings(state, NEXT));
        assertFalse(engine.redeemOfflineSavings(state, NEXT));
        assertEquals(6, state.onlineMinutes());
        assertEquals(6, state.weekMinutes());
        assertEquals(6, state.monthMinutes());
        assertEquals(6, state.totalMinutes());
        assertFalse(engine.storeOfflineSavings(state, NEXT));
        assertEquals(0, state.offlineSavingsMinutes());
    }

    @Test
    void balanceWithoutABusinessDayIsNeverMintedOrRedeemedAutomatically() {
        var engine = engine(120, 1);
        var state = new OnlineRewardsPlayerState();
        state.setRewardDate(DAY.rewardDate());
        state.setOnlineMinutes(90);
        state.setOfflineSavingsMinutes(7);
        engine.normalizeForPeriod(state, DAY);
        assertFalse(engine.storeOfflineSavings(state, DAY));
        engine.normalizeForPeriod(state, NEXT);
        assertFalse(engine.redeemOfflineSavings(state, NEXT));
        assertEquals(7, state.offlineSavingsMinutes());
        assertEquals(0, state.onlineMinutes());
        assertEquals(0, state.offlineSavingsEarnedMinutes());
    }

    private static OnlineRewardsStateEngine engine(int cap, double rate) {
        var settings = new OnlineRewardsOfflineSavingsConfiguration(true, cap, rate, 1);
        var config = new OnlineRewardsModuleConfiguration(false, 20, "Asia/Shanghai", "完成", "progress", "title",
            null, null, null, null, List.of(), List.of(), List.of(), List.of(), settings, null, List.of());
        return new OnlineRewardsStateEngine(config);
    }

    private static OnlineRewardsPeriodContext period(String date) {
        return new OnlineRewardsPeriodContext(LocalDate.parse(date), date, "2026-W47", "2026-11");
    }
}
