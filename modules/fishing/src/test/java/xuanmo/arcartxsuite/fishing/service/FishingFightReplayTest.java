package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorMode;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorPlan;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorStage;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;

class FishingFightReplayTest {
    @Test
    void sameSeedAndInputReplayEveryFrame() {
        var first = fight(1357, 64);
        var replay = fight(1357, 64);
        var other = fight(2468, 64);
        boolean differentFishPath = false;
        for (int step = 0; step < 64; step++) {
            boolean pressing = step % 7 < 3;
            var frame = first.step(pressing);
            assertEquals(frame, replay.step(pressing), "step " + step);
            if (frame.fishY() != other.step(pressing).fishY()) differentFishPath = true;
        }
        assertTrue(differentFishPath);
    }

    @Test
    void pressAndReleaseMoveInOppositeDirectionsAndStopAtTheDeadline() {
        var pressed = fight(123, 1);
        var released = fight(123, 1);
        var down = pressed.step(true);
        var up = released.step(false);
        assertTrue(down.barY() < 0.5);
        assertTrue(up.barY() > 0.5);
        assertTrue(up.timedOut());
        assertEquals(up, released.step(true));
    }

    @Test
    void sequenceUsesExactDurationsAndLoopsAcrossSeeds() {
        var plan = new BehaviorPlan(BehaviorMode.SEQUENCE, List.of(
            new BehaviorEntry(FishBehaviorType.SINKER, 1, 2),
            new BehaviorEntry(FishBehaviorType.FLOATER, 1, 3),
            new BehaviorEntry(FishBehaviorType.ZIGZAG, 1, 1)));
        var fish = FishDefinition.builder().id("sequence").behaviorPlan(plan).build();
        var first = fight(fish, 41, 20, TalentBonuses.empty());
        var second = fight(fish, 86, 20, TalentBonuses.empty());
        for (var behavior : List.of(FishBehaviorType.SINKER, FishBehaviorType.SINKER, FishBehaviorType.FLOATER,
                FishBehaviorType.FLOATER, FishBehaviorType.FLOATER, FishBehaviorType.ZIGZAG, FishBehaviorType.SINKER)) {
            assertEquals(behavior, first.step(false).behavior());
            assertEquals(behavior, second.step(false).behavior());
        }
    }

    @Test
    void enteringASequenceStageStartsAtItsFirstAction() {
        var latePlan = new BehaviorPlan(BehaviorMode.SEQUENCE, List.of(
            new BehaviorEntry(FishBehaviorType.SINKER, 1, 1),
            new BehaviorEntry(FishBehaviorType.FLOATER, 1, 1)));
        var fish = FishDefinition.builder().id("stage").progressGainRate(0.05).progressDrainRate(0.0)
            .behaviors(List.of(new BehaviorEntry(FishBehaviorType.SMOOTH, 1)))
            .behaviorStages(List.of(new BehaviorStage(0.1, latePlan)))
            .build();
        var fight = fight(fish, 29, 20, new TalentBonuses(Map.of("start_progress", 0.06)));
        var initial = fight.step(false);
        assertEquals(-1, initial.behaviorStage());
        assertEquals(FishBehaviorType.SMOOTH, initial.behavior());
        var entered = fight.step(false);
        assertEquals(0, entered.behaviorStage());
        assertEquals(FishBehaviorType.SINKER, entered.behavior());
        assertEquals(FishBehaviorType.FLOATER, fight.step(false).behavior());
    }

    @Test
    void timedRandomKeepsEachDrawForTheConfiguredDuration() {
        var plan = new BehaviorPlan(BehaviorMode.RANDOM, List.of(
            new BehaviorEntry(FishBehaviorType.SINKER, 1, 2),
            new BehaviorEntry(FishBehaviorType.FLOATER, 1, 2)));
        var fish = FishDefinition.builder().id("timed-random").behaviorPlan(plan).build();
        var fight = fight(fish, 41, 32, TalentBonuses.empty());
        var observed = new java.util.HashSet<FishBehaviorType>();
        for (int pair = 0; pair < 16; pair++) {
            var first = fight.step(false).behavior();
            assertEquals(first, fight.step(false).behavior());
            observed.add(first);
        }
        assertEquals(java.util.Set.of(FishBehaviorType.SINKER, FishBehaviorType.FLOATER), observed);
    }

    private static FishingFight fight(long seed, long durationTicks) {
        var fish = FishDefinition.builder().id("replay").displayName("Replay").difficulty(55)
            .behaviors(List.of(new FishDefinition.BehaviorEntry(FishBehaviorType.SMOOTH, 1.0)))
            .build();
        return fight(fish, seed, durationTicks, TalentBonuses.empty());
    }

    private static FishingFight fight(FishDefinition fish, long seed, long durationTicks, TalentBonuses bonuses) {
        var session = new FishingSession(new UUID(0, 0), fish, 10, 20, durationTicks,
            null, 55, null, 0, 1.0, 0);
        return new FishingFight(FishingSettings.load(null), session, bonuses, 1.0, null, new Random(seed));
    }
}
