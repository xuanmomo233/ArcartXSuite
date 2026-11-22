package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorMode;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.SizeModifiers;

class FishingBehaviorConfigTest {
    @TempDir Path dataDir;

    @Test
    void bundledListBehaviorsLoadForTheFishAndItsStages() throws Exception {
        Path fishes = Files.createDirectory(dataDir.resolve("fishes"));
        try (var resource = getClass().getResourceAsStream("/fishes/legendary_carp.yml")) {
            assertNotNull(resource);
            Files.copy(resource, fishes.resolve("legendary_carp.yml"));
        }
        var fish = load().findFish("legendary_carp");
        assertNotNull(fish);
        assertEquals(BehaviorMode.RANDOM, fish.behaviorPlan().mode());
        assertEquals(List.of(new BehaviorEntry(FishBehaviorType.DART, 0.5),
            new BehaviorEntry(FishBehaviorType.SINKER, 0.3), new BehaviorEntry(FishBehaviorType.FLOATER, 0.2)),
            fish.behaviorPlan().entries());
        assertEquals(3, fish.behaviorStages().size());
        assertEquals(BehaviorMode.SEQUENCE, fish.behaviorStages().get(0).behaviorPlan().mode());
        assertEquals(FishBehaviorType.SINKER, fish.behaviorStages().get(0).behaviorPlan().entries().get(0).type());
        assertEquals(12, fish.behaviorStages().get(0).behaviorPlan().entries().get(0).tellTicks());
        assertEquals(new SizeModifiers(1.15, 0.90, 0.85), fish.sizeDifficulty().resolve(80, 80, 250));
        assertEquals(new SizeModifiers(0.85, 1.20, 1.25), fish.sizeDifficulty().resolve(250, 80, 250));
        assertEquals(3.0, fish.stamina().rushDrainPerTick());
        assertEquals(20, fish.stamina().exhaustedTicks());
        assertEquals(1.4, fish.stamina().progressGainMultiplier());
        assertEquals(7, fish.personalities().normalWeight());
        assertEquals(2, fish.personalities().variants().size());
        assertEquals(true, fish.personalities().find("alert").firstMissRush());
        assertEquals(0.65, fish.personalities().find("resilient").staminaDrainMultiplier());
        assertEquals(8, fish.hookSet().windowTicks());
    }

    @Test
    void dragonLakeResolvesTheConfiguredReedsFromTheHookPosition() throws Exception {
        Path waters = Files.createDirectory(dataDir.resolve("waters"));
        try (var resource = getClass().getResourceAsStream("/waters/water-dragon_lake.yml")) {
            assertNotNull(resource);
            Files.copy(resource, waters.resolve("water-dragon_lake.yml"));
        }
        var water = load().specifiedWaters().get(0);
        assertEquals("dragon_lake", water.id());
        var reeds = water.obstacleAt(new Location(null, 150.5, 63, 200.5));
        assertNotNull(reeds);
        assertEquals("reeds", reeds.id());
        assertEquals(0.06, reeds.trackMin());
        assertEquals(0.24, reeds.trackMax());
        assertEquals(4, reeds.warningTicks());
        assertEquals(4, reeds.tensionPerTick());
        assertNull(water.obstacleAt(new Location(null, 160.5, 63, 200.5)));
        var rapids = water.currentAt(new Location(null, 160.5, 63, 200.5));
        assertNotNull(rapids);
        assertEquals("rapids", rapids.id());
        assertEquals(-0.016, rapids.driftPerTick());
        assertNull(water.currentAt(new Location(null, 145.5, 63, 200.5)));
    }

    @Test
    void legacyMapAndStageSequenceCanCoexist() throws Exception {
        Path fishes = Files.createDirectory(dataDir.resolve("fishes"));
        Files.writeString(fishes.resolve("test.yml"), """
            behaviors:
              old-style:
                type: smooth
                weight: 2
                duration-ticks: 6
            behavior-stages:
              later:
                threshold: 0.50
                behavior-mode: sequence
                behaviors:
                  - type: sinker
                    duration-ticks: 3
                  - type: floater
                    duration-ticks: 4
            """);
        Files.writeString(fishes.resolve("invalid.yml"), """
            behavior-mode: sequence
            behaviors:
              - type: sinker
            """);
        var config = load();
        var fish = config.findFish("test");
        assertNotNull(fish);
        assertEquals(SizeModifiers.NEUTRAL, fish.sizeDifficulty().resolve(fish.minSize(), fish.minSize(), fish.maxSize()));
        assertEquals(List.of(new BehaviorEntry(FishBehaviorType.SMOOTH, 2, 6)), fish.behaviorPlan().entries());
        var stage = fish.selectBehaviorStage(0.50);
        assertNotNull(stage);
        assertEquals(BehaviorMode.SEQUENCE, stage.behaviorPlan().mode());
        assertEquals(List.of(new BehaviorEntry(FishBehaviorType.SINKER, 1, 3),
            new BehaviorEntry(FishBehaviorType.FLOATER, 1, 4)), stage.behaviorPlan().entries());
        assertNull(config.findFish("invalid"));
    }

    private FishingModuleConfiguration load() {
        return FishingModuleConfiguration.load(new YamlConfiguration(), dataDir.toFile(),
            Logger.getLogger(FishingBehaviorConfigTest.class.getName()));
    }
}
