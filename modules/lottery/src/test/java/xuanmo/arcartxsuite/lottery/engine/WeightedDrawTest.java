package xuanmo.arcartxsuite.lottery.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

class WeightedDrawTest {
    @Test
    void cumulativeIntervalsStayExactPastTheIntLimitAndSkipZeroWeight() {
        var pool = new WeightedPool<>(List.of("first", "zero", "last"),
            value -> value.equals("zero") ? 0 : Integer.MAX_VALUE);
        assertEquals("first", pool.select((long) Integer.MAX_VALUE - 1L));
        assertEquals("last", pool.select(Integer.MAX_VALUE));
        assertEquals("last", pool.select(2L * Integer.MAX_VALUE - 1L));
        assertThrows(IllegalArgumentException.class, () -> pool.select(2L * Integer.MAX_VALUE));
    }

    @Test
    void compiledCaseRarityKeepsTheOriginalAnimationSlot() {
        var rare = item("rare", 1, "rare");
        var config = new CasePoolConfig(Map.of("RARE", new CasePoolConfig.RaritySetting("&b", 1)),
            List.of(item("first", 1, "COMMON"), rare, item("last", 1, "COMMON")));
        var engine = new CaseOpeningEngine(List.of(config));
        for (var result : engine.openCaseBatch(config, 10)) {
            assertSame(rare, result.item());
            assertEquals(2, result.poolIndex());
            assertEquals("RARE", result.rarity());
        }
    }

    @Test
    void compiledGachaKeepsGuaranteedFateSelectionAndResetsTheCounters() {
        var high = item("higher-weight", Integer.MAX_VALUE, null);
        var target = item("target", 1, null);
        var config = new GachaPoolConfig(GachaPoolConfig.GachaPoolType.WEAPON,
            5, 10, 4, 0.2, 0.01, 0.05, 0.5, 2, null,
            List.of(high, target), List.of(high), List.of(), List.of(high), List.of(high));
        var player = UUID.randomUUID();
        var state = new PlayerGachaState(player, "weapon", 4, 3, true, 2, "target");
        var result = new GachaEngine(List.of(config)).pull(config, state, 1);
        assertEquals(List.of(target), result.items());
        assertTrue(result.guaranteed());
        assertTrue(result.fatePointTriggered());
        assertEquals(new PlayerGachaState(player, "weapon", 0, 0, false, 0, null), result.finalState());
    }

    private static PoolItem item(String id, int weight, String rarity) {
        return new PoolItem(id, id, PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "STONE", null, null, List.of(), 1, weight, rarity);
    }

    @Test
    void standardOnlyPoolCompletesTenPullsWithoutCreatingAGuarantee() {
        var standard = item("standard", 1, null);
        var config = new GachaPoolConfig(GachaPoolConfig.GachaPoolType.STANDARD,
            1, 10, 73, 0.06, 0.006, 0.05, 0.0, 0, "shared",
            List.of(), List.of(standard), List.of(), List.of(standard), List.of(standard));
        var player = UUID.randomUUID();
        var before = new PlayerGachaState(player, "shared", 0, 0, false, 0, null);
        var result = new GachaEngine(List.of(config)).pull(config, before, 10);
        assertEquals(java.util.Collections.nCopies(10, standard), result.items());
        assertFalse(result.guaranteed());
        assertEquals(before, result.finalState());
    }

    @Test
    void aStandardPoolPreservesTheSharedGuaranteeAndOtherPoolsDontAdvanceAnUnavailableTarget() {
        var standard = item("standard", 1, null);
        var different = item("different", 1, null);
        var target = item("target", 1, null);
        var standardPool = new GachaPoolConfig(GachaPoolConfig.GachaPoolType.STANDARD,
            1, 10, 73, 0.06, 0.006, 0.05, 0.0, 0, "shared",
            List.of(), List.of(standard), List.of(), List.of(standard), List.of(standard));
        var differentPool = weaponPool(different, standard);
        var targetPool = weaponPool(target, standard);
        var engine = new GachaEngine(List.of(standardPool, differentPool, targetPool));
        var before = new PlayerGachaState(UUID.randomUUID(), "shared", 0, 0, true, 2, "target");

        var standardResult = engine.pull(standardPool, before, 1);
        assertEquals(List.of(standard), standardResult.items());
        assertFalse(standardResult.guaranteed());
        assertEquals(before, standardResult.finalState());

        var otherResult = engine.pull(differentPool, standardResult.finalState(), 1);
        assertEquals(List.of(different), otherResult.items());
        assertTrue(otherResult.guaranteed());
        assertEquals(2, otherResult.finalState().fatePoints());
        assertEquals("target", otherResult.finalState().fateTarget());

        var targetResult = engine.pull(targetPool, standardResult.finalState(), 1);
        assertEquals(List.of(target), targetResult.items());
        assertTrue(targetResult.guaranteed());
        assertTrue(targetResult.fatePointTriggered());
        assertFalse(targetResult.finalState().guaranteedUp());
        assertEquals(0, targetResult.finalState().fatePoints());
        assertNull(targetResult.finalState().fateTarget());
    }

    private static GachaPoolConfig weaponPool(PoolItem featured, PoolItem standard) {
        return new GachaPoolConfig(GachaPoolConfig.GachaPoolType.WEAPON,
            1, 10, 73, 0.06, 0.006, 0.05, 0.5, 2, "shared",
            List.of(featured), List.of(standard), List.of(), List.of(standard), List.of(standard));
    }
}
