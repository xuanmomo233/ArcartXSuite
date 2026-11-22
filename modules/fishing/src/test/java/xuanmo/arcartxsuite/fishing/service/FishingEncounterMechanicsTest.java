package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.util.PointInPolygon.Vertex;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight.HookPhase;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight.HookResult;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight.MovePhase;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorMode;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorPlan;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.FleeParams;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.HookSet;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.Personality;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.SizeDifficulty;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.SizeModifiers;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.Stamina;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea.ObstacleZone;
import xuanmo.arcartxsuite.fishing.model.WaterArea.CurrentZone;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;

class FishingEncounterMechanicsTest {
    @Test
    void theBundledBarReversesInOneStepAwayFromTheEdges() throws Exception {
        var yaml = new YamlConfiguration();
        try (var resource = getClass().getResourceAsStream("/ArcartXFishing.yml")) {
            assertNotNull(resource);
            yaml.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        var settings = FishingSettings.load(yaml.getConfigurationSection("fishing"));
        var fish = fish(false, 20, 10, 1).build();
        var session = session(fish, 10, new FishingSession.Gear(1, 1, Map.of()), null, null);
        var fight = new FishingFight(settings, session, TalentBonuses.empty(), 1, null, new Random(17));

        for (int i = 0; i < 10; i++) fight.step(false);
        var rising = fight.frame();
        assertTrue(rising.barVelocity() > 0.03);
        assertTrue(fight.step(true).barY() < rising.barY());

        for (int i = 0; i < 10; i++) fight.step(true);
        var falling = fight.frame();
        assertTrue(falling.barVelocity() < -0.03);
        assertTrue(fight.step(false).barY() > falling.barY());
    }

    @Test
    void onlyNewerInputsForThisFightCanChangeTheBarState() {
        var session = openingSession(fish(false, 10, 5, 2).build(), new FishingSession.Opening(Personality.NORMAL, null));
        session.updateBarInput(session.id(), 2, true);
        session.updateBarInput(session.id(), 1, false);
        session.updateBarInput(session.id(), 2, false);
        session.updateBarInput("previous-fight", 3, false);
        assertEquals(new FishingSession.BarInput(2, true), session.barInput());
        session.updateBarInput(session.id(), 3, false);
        assertEquals(new FishingSession.BarInput(3, false), session.barInput());
    }

    @Test
    void telegraphMatchesTheRushAndBaitExtendsOnlyTheFirstRecovery() {
        var fight = fight(false, 10, 5, 2, new FishingSession.Gear(1, 1, Map.of(FishBehaviorType.SINKER, 3)), null);
        var tell = fight.step(false);
        assertEquals(MovePhase.TELL, tell.phase());
        assertEquals(-1, tell.direction());
        assertEquals(0.5, fight.step(false).fishY());
        var rush = fight.step(false);
        assertEquals(MovePhase.RUSH, rush.phase());
        assertEquals(tell.moveId(), rush.moveId());
        assertTrue(rush.fishY() < tell.fishY());

        for (int i = 4; i <= 13; i++) fight.step(false);
        assertEquals(MovePhase.RECOVERY, fight.frame().phase());
        assertEquals(8, fight.frame().phaseSteps());
        for (int i = 14; i <= 33; i++) fight.step(false);
        assertEquals(MovePhase.RECOVERY, fight.frame().phase());
        assertNotEquals(tell.moveId(), fight.frame().moveId());
        assertEquals(5, fight.frame().phaseSteps());
    }

    @Test
    void slackenRelievesTensionAndExpiryDoesNotResumeASeveredFight() {
        var gear = new FishingSession.Gear(0.25, 1, Map.of());
        var tense = fight(true, 40, 10, 1, gear, null);
        var controlled = fight(true, 40, 10, 1, gear, null);
        for (int i = 0; i < 40 && controlled.frame().tension() < 80; i++) {
            controlled.step(false);
            tense.step(false);
        }
        assertTrue(controlled.frame().tension() >= 80);
        double before = controlled.frame().tension();
        long remaining = controlled.frame().remainingTicks();
        var slackened = controlled.step(false, true);
        assertTrue(slackened.slackened());
        assertTrue(slackened.tension() < before);
        assertEquals(remaining - 1, slackened.remainingTicks());
        long cooldown = slackened.slackCooldown();
        assertEquals(cooldown - 1, controlled.step(false, true).slackCooldown());

        while (!tense.frame().finished()) tense.step(false);
        assertTrue(tense.frame().lineSnapped());
        assertEquals(tense.frame(), tense.step(false, true));
    }

    @Test
    void treasureAppearsDuringRecoveryAndRequiresContinuousCoverage() {
        var treasure = new TreasureDefinition("test", "宝藏", IconDefinition.load("minecraft:gold_ingot"), 1);
        var fight = fight(false, 9, 50, 2, new FishingSession.Gear(1, 1, Map.of()), treasure);
        // Keep the bar low during the opening, then move to the upper treasure.
        for (int step = 0; step < 11; step++) fight.step(true);
        var appeared = fight.step(false);
        assertEquals(MovePhase.RECOVERY, appeared.phase());
        assertTrue(appeared.treasureVisible());
        assertFalse(appeared.treasureClaimed());
        for (int i = 0; i < 29 && !fight.frame().treasureClaimed(); i++) fight.step(false);
        assertTrue(fight.frame().treasureClaimed());
        assertFalse(fight.frame().treasureVisible());
        assertFalse(fight.frame().landed());
    }

    @Test
    void obstacleEnablesSlackenAndRestartsTheWarningAfterTheLineTightens() {
        var gear = new FishingSession.Gear(2, 1, Map.of());
        var session = session(fish(false, 10, 5, 20).build(), 10, gear, null, obstacle(0, 1));
        assertTrue(session.tensionEnabled());
        var fight = fight(session);
        for (int step = 1; step <= 4; step++) {
            var warning = fight.step(false);
            assertTrue(warning.obstacleWarning());
            assertFalse(warning.obstacleScraping());
            assertEquals(0, warning.tension());
        }
        session.requestSlacken("wrong-fight");
        var scraping = fight.step(false, session.consumeSlackenRequest());
        assertTrue(scraping.obstacleScraping());
        assertEquals(0.75, scraping.tension());

        session.requestSlacken(session.id());
        var slack = fight.step(false, session.consumeSlackenRequest());
        assertTrue(slack.slackened());
        assertFalse(slack.obstacleWarning());
        assertFalse(slack.obstacleScraping());
        assertEquals(0, slack.tension());
        for (int step = 0; step < 7; step++) assertTrue(fight.step(false).slackened());

        for (int step = 1; step <= 4; step++) {
            var warning = fight.step(false, true);
            assertFalse(warning.slackened());
            assertTrue(warning.obstacleWarning());
        }
        assertTrue(fight.step(false).obstacleScraping());
    }

    @Test
    void fishLeavingTheBandStopsScrapingOnTheSameStep() {
        var session = session(fish(false, 10, 5, 6).build(), 10,
            new FishingSession.Gear(1, 1, Map.of()), null, obstacle(0.49, 0.51));
        var fight = fight(session);
        for (int step = 1; step <= 6; step++) fight.step(false);
        assertTrue(fight.frame().obstacleScraping());
        var left = fight.step(false);
        assertTrue(left.fishY() < 0.49);
        assertFalse(left.obstacleWarning());
        assertFalse(left.obstacleScraping());
        assertEquals(left.fishInside() ? 6 : 3, left.tension() - 5.5);
    }

    @Test
    void sizeScalesRushSpeedDurationAndPullWhileGearAbsorbsPullAndScraping() {
        var size = new SizeDifficulty(new SizeModifiers(1.15, 0.90, 0.85), new SizeModifiers(0.85, 1.20, 1.25));
        var fish = fish(true, 10, 5, 6).minSize(80).maxSize(250).sizeDifficulty(size).build();
        var gear = new FishingSession.Gear(2, 1, Map.of());
        var small = fight(session(fish, 80, gear, null, obstacle(0, 1)));
        var large = fight(session(fish, 250, gear, null, obstacle(0, 1)));
        for (int step = 1; step <= 6; step++) assertEquals(small.step(false), large.step(false));

        var smallRush = small.step(false);
        var largeRush = large.step(false);
        assertEquals(MovePhase.RUSH, smallRush.phase());
        assertEquals(MovePhase.RUSH, largeRush.phase());
        assertEquals(9, smallRush.phaseSteps());
        assertEquals(12, largeRush.phaseSteps());
        assertTrue(smallRush.fishY() < largeRush.fishY());
        assertEquals(1.5 + ((smallRush.fishInside() ? 6 : 3) * 0.85 + 4) / 2, smallRush.tension(), 1e-9);
        assertEquals(1.5 + ((largeRush.fishInside() ? 6 : 3) * 1.25 + 4) / 2, largeRush.tension(), 1e-9);
        for (int step = 0; step < 9; step++) small.step(false);
        for (int step = 0; step < 12; step++) large.step(false);
        assertEquals(MovePhase.RECOVERY, small.frame().phase());
        assertEquals(MovePhase.RECOVERY, large.frame().phase());
        assertEquals(5, small.frame().phaseSteps());
        assertEquals(5, large.frame().phaseSteps());
        assertEquals(size.resolve(165, 80, 250), size.resolve(80, 80, 80));
    }

    private ObstacleZone obstacle(double trackMin, double trackMax) {
        return new ObstacleZone("reeds", "芦苇", List.of(new Vertex(0, 0), new Vertex(2, 0), new Vertex(0, 2)),
            trackMin, trackMax, 4, 4);
    }

    @Test
    void currentCarriesFishIntoAnObstacleBeforeTheContactCheckAndThenStops() {
        var fish = fish(false, 15, 6, 2).build();
        var gear = new FishingSession.Gear(1, 1, Map.of());
        var obstacle = obstacle(0.14, 0.155);
        var current = new CurrentZone("rapids", "急流", obstacle.vertices(), 0.12, 0.4, -0.01, 4);
        var flowing = fight(new FishingSession(new UUID(0, 0), fish, 10, 30, 120, null, 40, null,
            0, 1, 0, gear, null, null, obstacle, current, false));
        var still = fight(session(fish, 10, gear, null, obstacle));
        for (int step = 1; step <= 17; step++) assertEquals(still.step(false), flowing.step(false));

        var drift = flowing.step(false);
        var settled = still.step(false);
        assertEquals(MovePhase.RECOVERY, drift.phase());
        assertEquals(-0.01, drift.currentDrift(), 1e-9);
        assertEquals(settled.fishY() - 0.01, drift.fishY(), 1e-9);
        assertTrue(drift.obstacleWarning());
        assertFalse(settled.obstacleWarning());
        assertEquals(-0.0075, flowing.step(false).currentDrift(), 1e-9);
        flowing.step(false);
        var lastDrift = flowing.step(false);
        assertEquals(0, flowing.step(false).currentDrift());
        assertEquals(lastDrift.fishY(), flowing.frame().fishY());
    }

    @Test
    void exhaustionAccumulatesAcrossRushesAndBoostsOnlyTightLineProgress() {
        var gear = new FishingSession.Gear(1, 1, Map.of());
        var fish = fish(true, 2, 2, 1).progressGainRate(0.002);
        var baseline = fight(new FishingSession(new UUID(0, 0), fish.build(), 10, 30, 120, null, 40, null,
            600, 1, 0, gear, null, null));
        var withStamina = fish.stamina(new Stamina(25, 6, 1.5)).build();
        var test = fight(new FishingSession(new UUID(0, 0), withStamina, 10, 30, 120, null, 40, null,
            600, 1, 0, gear, null, null));
        for (int step = 1; step <= 8; step++) {
            var frame = test.step(false);
            assertEquals(0, frame.exhaustionSteps());
            assertEquals(baseline.step(false).progress(), frame.progress());
            if (step == 4) assertEquals(50, frame.stamina());
        }
        var previous = test.frame();
        assertEquals(0, previous.stamina());
        var exhausted = test.step(false);
        var recovered = baseline.step(false);
        assertEquals(MovePhase.RECOVERY, exhausted.phase());
        assertEquals(6, exhausted.exhaustionSteps());
        assertEquals(6, exhausted.phaseSteps());
        assertTrue(exhausted.fishInside());
        assertEquals((recovered.progress() - previous.progress()) * 1.5,
            exhausted.progress() - previous.progress(), 1e-9);
        var slackened = test.step(false, true);
        assertTrue(slackened.slackened());
        assertEquals(5, slackened.exhaustionSteps());
        assertEquals(exhausted.progress(), slackened.progress());
        for (int step = 11; step <= 14; step++) test.step(false);
        var next = test.step(false);
        assertEquals(0, next.exhaustionSteps());
        assertEquals(100, next.stamina());
        assertEquals(MovePhase.TELL, next.phase());
    }

    @Test
    void hookTimingLocksOneResultAndAdjustsTheFirstRecoveryWithoutSpendingFightTime() {
        var fish = fish(false, 2, 5, 1).build();
        var opening = new FishingSession.Opening(Personality.NORMAL, new HookSet(3, 2, 2, 4, 2));
        var goodSession = openingSession(fish, opening);
        var good = fight(goodSession);
        assertEquals(HookPhase.TAP, good.frame().hookPhase());
        goodSession.requestHookSet("old-fight", "1");
        assertFalse(goodSession.consumeHookSetRequest());
        for (int i = 0; i < 4; i++) good.step(false);
        assertEquals(HookPhase.READY, good.frame().hookPhase());
        goodSession.requestHookSet(goodSession.id(), "1");
        var graded = good.step(false, false, goodSession.consumeHookSetRequest());
        assertEquals(HookResult.GOOD, graded.hookResult());
        assertEquals(0, graded.hookErrorSteps());
        assertEquals(HookPhase.DONE, graded.hookPhase());
        assertEquals(0, graded.step());
        assertEquals(120, graded.remainingTicks());

        var early = fight(openingSession(fish, opening));
        assertEquals(HookResult.EARLY, early.step(false, false, true).hookResult());
        var late = fight(openingSession(fish, opening));
        for (int i = 0; i < 5; i++) late.step(false);
        assertEquals(HookPhase.LATE, late.frame().hookPhase());
        assertEquals(HookResult.LATE, late.step(false, false, true).hookResult());
        var missed = fight(openingSession(fish, opening));
        for (int i = 0; i < 7; i++) missed.step(false);
        assertEquals(HookResult.MISSED, missed.frame().hookResult());
        for (int step = 0; step < 4; step++) {
            good.step(false, false, true);
            early.step(false);
            late.step(false);
            missed.step(false);
        }
        assertEquals(HookResult.GOOD, good.frame().hookResult());
        assertEquals(MovePhase.RECOVERY, good.frame().phase());
        assertEquals(9, good.frame().phaseSteps());
        assertEquals(3, early.frame().phaseSteps());
        assertEquals(3, late.frame().phaseSteps());
        assertEquals(3, missed.frame().phaseSteps());
    }

    @Test
    void continuousPracticeRandomizesTheWaitAndCountsEachCompletedRoundOnce() {
        var fish = fish(false, 2, 5, 1).build();
        var alert = new Personality("alert", "警觉", 1, true, 1);
        var opening = new FishingSession.Opening(alert, new HookSet(3, 2, 2, 4, 2), true);
        var session = openingSession(fish, opening);
        var fight = fight(session);
        assertEquals(1, fight.frame().hookRound());
        assertEquals(0, fight.frame().hookRoundsCompleted());
        fight.step(false);
        session.requestHookSet(session.id(), "1");
        var early = fight.step(false, false, session.consumeHookSetRequest());
        assertEquals(HookPhase.FEEDBACK, early.hookPhase());
        assertEquals(HookResult.EARLY, early.hookResult());
        int firstWait = 1 - early.hookErrorSteps();
        assertTrue(firstWait >= 3 && firstWait <= 23);
        assertEquals(1, early.hookRoundsCompleted());
        assertEquals(0, early.hookAccuracyPercent());
        for (int i = 0; i < 19; i++) assertEquals(early, fight.step(true, true, true));
        session.requestHookSet(session.id(), "1");
        var restarted = fight.step(false, false, session.consumeHookSetRequest());
        assertEquals(2, restarted.hookRound());
        assertEquals(HookPhase.TAP, restarted.hookPhase());
        assertEquals(HookResult.PENDING, restarted.hookResult());
        assertEquals(0, restarted.hookErrorSteps());
        assertEquals(1, restarted.hookRoundsCompleted());
        session.requestHookSet(session.id(), "1");
        session.requestHookSet(session.id(), "3");
        assertFalse(session.consumeHookSetRequest());

        int secondWait = waitForPracticeReady(fight).hookStep();
        fight.step(false);
        assertEquals(HookPhase.READY, fight.frame().hookPhase());
        fight.step(false);
        assertEquals(HookPhase.LATE, fight.frame().hookPhase());
        session.requestHookSet(session.id(), "2");
        var late = fight.step(false, false, session.consumeHookSetRequest());
        assertEquals(HookResult.LATE, late.hookResult());
        assertEquals(1, late.hookErrorSteps());
        assertEquals(2, late.hookRoundsCompleted());
        assertEquals(0, late.hookAccuracyPercent());
        for (int i = 0; i < 20; i++) fight.step(false);
        assertEquals(3, fight.frame().hookRound());
        int thirdWait = waitForPracticeReady(fight).hookStep();
        for (int i = 0; i < 4; i++) fight.step(false);
        assertEquals(HookResult.MISSED, fight.frame().hookResult());
        assertEquals(3, fight.frame().hookRoundsCompleted());
        assertEquals(0, fight.frame().hookAccuracyPercent());
        for (int i = 0; i < 20; i++) fight.step(false);
        assertEquals(4, fight.frame().hookRound());
        assertEquals(3, fight.frame().hookRoundsCompleted());
        int fourthWait = waitForPracticeReady(fight).hookStep();
        assertTrue(firstWait != secondWait || firstWait != thirdWait || firstWait != fourthWait);
        session.requestHookSet(session.id(), "4");
        var good = fight.step(false, false, session.consumeHookSetRequest());
        assertEquals(HookResult.GOOD, good.hookResult());
        assertEquals(0, good.hookErrorSteps());
        assertEquals(4, good.hookRoundsCompleted());
        assertEquals(25, good.hookAccuracyPercent());
        assertEquals(0, good.step());
        assertEquals(120, good.remainingTicks());
        assertFalse(good.finished());
        assertEquals(10, session.caughtSize());
        assertSame(alert, session.opening().personality());
    }

    private FishingFight.Frame waitForPracticeReady(FishingFight fight) {
        var frame = fight.frame();
        for (int step = 0; step < 23 && frame.hookPhase() == HookPhase.TAP; step++) {
            frame = fight.step(false);
        }
        assertEquals(HookPhase.READY, frame.hookPhase());
        assertTrue(frame.hookStep() >= 3 && frame.hookStep() <= 23);
        return frame;
    }

    @Test
    void alertFishQueuesOneTelegraphedCounterAfterTheFirstMiss() {
        var fish = fish(false, 9, 12, 4).build();
        var opening = new FishingSession.Opening(new Personality("alert", "警觉", 1, true, 1), null);
        var alert = fight(openingSession(fish, opening));
        var normal = fight(openingSession(fish, new FishingSession.Opening(Personality.NORMAL, null)));
        boolean wasInside = false;
        boolean lost = false;
        for (int step = 1; step <= 25; step++) {
            var frame = alert.step(false);
            lost |= wasInside && !frame.fishInside();
            wasInside = frame.fishInside();
            assertEquals(normal.step(false), frame);
        }
        assertTrue(lost);
        var counter = alert.step(false);
        assertEquals(MovePhase.TELL, counter.phase());
        assertTrue(counter.personalityRush());
        assertEquals(9, counter.phaseSteps());
        assertFalse(normal.step(false).personalityRush());
        while (!alert.frame().finished()) {
            var frame = alert.step(false);
            if (frame.personalityRush()) assertEquals(counter.moveId(), frame.moveId());
        }
    }

    @Test
    void resilientFishSpendsLessStaminaPerRushStep() {
        var fish = fish(false, 5, 4, 1).stamina(new Stamina(10, 6, 1.5)).build();
        var resilient = new Personality("resilient", "顽强", 1, false, 0.65);
        var fight = fight(openingSession(fish, new FishingSession.Opening(resilient, null)));
        assertEquals(100, fight.step(false).stamina());
        assertEquals(93.5, fight.step(false).stamina());
    }

    private FishingSession openingSession(FishDefinition fish, FishingSession.Opening opening) {
        return new FishingSession(new UUID(0, 0), fish, 10, 30, 120, null, 40, null,
            0, 1, 0, new FishingSession.Gear(1, 1, Map.of()), null, null, null, null, opening.repeatHook(), opening);
    }

    private FishingFight fight(boolean tension, int rush, int recover, int tell,
                               FishingSession.Gear gear, TreasureDefinition offer) {
        return fight(session(fish(tension, rush, recover, tell).build(), 10, gear, offer, null));
    }

    private FishDefinition.Builder fish(boolean tension, int rush, int recover, int tell) {
        return FishDefinition.builder().id("encounter").difficulty(40).lineTension(tension)
            .progressGainRate(0.0).progressDrainRate(0.0)
            .fleeParams(new FleeParams(0.0, 0.0, null, null, null, null, null, null, null, null))
            .behaviorPlan(new BehaviorPlan(BehaviorMode.SEQUENCE,
                List.of(new BehaviorEntry(FishBehaviorType.SINKER, 1, rush, tell, recover))));
    }

    private FishingSession session(FishDefinition fish, int size, FishingSession.Gear gear,
                                   TreasureDefinition offer, ObstacleZone obstacle) {
        return new FishingSession(new UUID(0, 0), fish, size, 30, 120, null, 40, null,
            offer == null ? 0 : 145, 1, 0, gear, offer, null, obstacle);
    }

    private FishingFight fight(FishingSession session) {
        return new FishingFight(FishingSettings.load(null), session, TalentBonuses.empty(), 1, null, new Random(17));
    }
}
