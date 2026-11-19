package xuanmo.arcartxsuite.lottery.packet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

class LotteryPoolDetailsTest {
    @Test
    void gachaOddsFollowStarOrderUpBranchesAndTheFourStarFallback() {
        var up = item("up-five", 1, null);
        var standard = item("standard-five", 99, null);
        var four = item("four", 1, null);
        var otherFour = item("other-four", 3, null);
        var three = item("three", 1, null);
        var config = new GachaPoolConfig(GachaPoolConfig.GachaPoolType.CHARACTER, 90, 10, 73, 0.06,
            0.10, 0.20, 0.75, 0, null, List.of(up), List.of(standard), List.of(),
            List.of(four, otherFour), List.of(three));
        var pool = new PoolDefinition("gacha", PoolType.GACHA, true, "Gacha", "", "", null,
            new CostConfig(List.of(), List.of()), config, null, null, false, Map.of());

        var details = LotteryPoolDetails.compile(pool, PoolItem::name);
        assertEquals(List.of(up, standard, four, otherFour, three), details.icons());
        assertEquals(5, details.fields().get("detailCount"));
        assertEquals(0.18, (double) details.fields().get("detailBase4Rate"), 0.00001);
        assertEquals(List.of("7.50%", "2.50%", "4.50%", "13.50%", "72.00%"), odds(details));
    }

    @Test
    void caseOddsKeepTheConfiguredOrderAndUseLongWeightSums() {
        int large = Integer.MAX_VALUE;
        var common = item("common", large, "COMMON");
        var other = item("other", large, "common");
        var rare = item("rare", large, "RARE");
        var unavailable = item("unavailable", 1, "UNKNOWN");
        var pools = List.of(common, rare, other, unavailable);
        var config = new CasePoolConfig(Map.of("COMMON", new CasePoolConfig.RaritySetting("&f", large),
            "RARE", new CasePoolConfig.RaritySetting("&b", large)), pools);
        var pool = new PoolDefinition("case", PoolType.CASE, true, "Case", "", "", null,
            new CostConfig(List.of(), List.of()), null, config, null, false, Map.of());

        var details = LotteryPoolDetails.compile(pool, PoolItem::name);
        assertEquals(pools, details.icons());
        assertEquals(List.of("25.00%", "50.00%", "25.00%", ""), odds(details));
    }

    private static List<?> odds(LotteryPoolDetails details) {
        return ((Map<?, ?>) details.fields().get("detailRows")).values().stream()
            .map(row -> ((Map<?, ?>) row).get("probability")).toList();
    }

    private static PoolItem item(String id, int weight, String rarity) {
        return new PoolItem(id, id, PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "STONE", null, null, List.of(), 1, weight, rarity);
    }
}
