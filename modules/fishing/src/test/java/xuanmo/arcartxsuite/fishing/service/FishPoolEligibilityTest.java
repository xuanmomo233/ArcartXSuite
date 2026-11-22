package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;

class FishPoolEligibilityTest {
    @Test
    void bothFallbacksKeepFishPermissionsAndLevelBounds() {
        FishDefinition eligible = FishDefinition.builder().id("eligible").displayName("Eligible")
            .seasons(List.of("spring")).weathers(List.of("rain")).build();
        FishDefinition restricted = FishDefinition.builder().id("restricted").displayName("Restricted")
            .seasons(List.of("spring", "winter")).requirePermission("fishing.restricted").build();
        FishDefinition highLevel = FishDefinition.builder().id("high").displayName("High")
            .seasons(List.of("spring", "winter")).minPlayerLevel(10).build();
        FishDefinition lowLevel = FishDefinition.builder().id("low").displayName("Low")
            .seasons(List.of("spring", "winter")).maxPlayerLevel(1).build();
        var pool = List.of(eligible, restricted, highLevel, lowLevel);

        assertEquals(List.of(eligible),
            FishingService.selectCandidates(pool, 2, permission -> false, "spring", "clear", "12:00", "river"));
        assertEquals(List.of(eligible),
            FishingService.selectCandidates(pool, 2, permission -> false, "winter", "clear", "12:00", "river"));
        assertEquals(List.of(),
            FishingService.selectCandidates(pool.subList(1, 4), 2, permission -> false, "winter", "clear", "12:00", "river"));
    }
}
