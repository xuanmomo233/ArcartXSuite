package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.DamageThreshold;

class BossDamageRankingReloadTest {
    @Test
    void keepsContributionsAndTieOrderAndAppliesNewThreshold() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        var original = new BossDamageRankingTracker(BossDamageRankingSettings.defaults());
        original.recordDamage(first, "Zulu", 20);
        original.recordTakenDamage(first, "Zulu", 6);
        original.recordDamage(second, "Alpha", 20);
        assertEquals(first, original.snapshot(100).entry(1).playerUuid());

        var restored = new BossDamageRankingTracker(
            new BossDamageRankingSettings(true, 5, DamageThreshold.parse("21")), original);
        var beforeNewHits = restored.snapshot(100);
        assertEquals(40, beforeNewHits.totalDamage());
        assertEquals(6, beforeNewHits.entry(first).takenDamage());
        assertFalse(beforeNewHits.entry(first).qualified());

        restored.recordDamage(first, "Zulu", 2);
        restored.recordDamage(second, "Alpha", 2);
        restored.recordDamage(next, "Beta", 22);
        var afterNewHits = restored.snapshot(100);
        assertEquals(first, afterNewHits.entry(1).playerUuid());
        assertEquals(second, afterNewHits.entry(2).playerUuid());
        assertEquals(next, afterNewHits.entry(3).playerUuid());
        assertEquals(40, original.snapshot(100).totalDamage());
    }
}
