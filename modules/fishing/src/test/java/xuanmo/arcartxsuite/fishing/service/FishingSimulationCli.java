package xuanmo.arcartxsuite.fishing.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.fishing.config.FatigueConfig;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight.Frame;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.talent.TalentLoader;

/** Command-line driver for the server fight engine. Each trial starts from a fresh session. */
public final class FishingSimulationCli {
    private static final UUID PLAYER = new UUID(0, 0);
    private static final Logger LOGGER = Logger.getLogger(FishingSimulationCli.class.getName());

    private FishingSimulationCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Usage: FishingSimulationCli <data-dir> <scenario.yml> <aria-jar-name> [trace.tsv]");
        }
        Path dataDir = Path.of(args[0]);
        Path scenarioPath = Path.of(args[1]);
        SimulationAriaBridge aria = new SimulationAriaBridge(args[2]);
        FishingModuleConfiguration configuration = FishingModuleConfiguration.load(
            load(dataDir.resolve("ArcartXFishing.yml")), dataDir.toFile(), LOGGER, aria);
        FatigueConfig fatigue = FatigueConfig.load(load(dataDir.resolve("fatigue.yml")));
        TalentEngine talents = TalentLoader.load(load(dataDir.resolve("talents.yml")), LOGGER);
        Scenario scenario = Scenario.load(scenarioPath, configuration, fatigue, talents);

        Path tracePath = args.length == 4 ? Path.of(args[3]) : null;
        if (tracePath != null && Files.exists(tracePath)) {
            throw new IllegalArgumentException("逐步记录文件已存在，请指定一个新路径: " + tracePath);
        }
        Map<String, Result> results = new LinkedHashMap<>();
        try (BufferedWriter trace = tracePath == null ? null
                : Files.newBufferedWriter(tracePath, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            if (trace != null) {
                trace.write("case\tseed\tstep\tpressing\tfish_y\tbar_y\tbar_height\tprogress\tremaining_steps"
                    + "\tinside\tbehavior_stage\tbehavior\tfleeing\tlanded\ttimed_out"
                    + "\tphase\ttension\tslackened\tline_snapped\ttreasure_visible\ttreasure_claimed"
                    + "\tstamina\texhaustion_steps\tcurrent_drift");
                trace.newLine();
            }
            for (var entry : scenario.profiles().entrySet()) {
                results.put(entry.getKey(), simulate(entry.getKey(), entry.getValue(), scenario, configuration, fatigue, aria, trace));
            }
            aria.verify();
        }

        printResults(scenario, configuration, aria, results);
        if (tracePath != null) System.out.println("首个种子的逐步记录 " + tracePath.toAbsolutePath());
    }

    private static Result simulate(String id, Profile profile, Scenario scenario,
                                   FishingModuleConfiguration configuration, FatigueConfig fatigue,
                                   SimulationAriaBridge aria, BufferedWriter trace) throws IOException {
        FishDefinition fish = scenario.fish();
        WaterArea water = scenario.water();
        var settings = configuration.fishing();
        double fatigueMultiplier = fatigue.enabled() ? fatigue.efficiencyMultiplier(profile.fatigue()) : 1.0;
        var setup = FishingFight.setup(settings, fish, water.difficultyFormula(), profile.level(), profile.rod(),
            profile.bonuses(), fatigueMultiplier, aria, LOGGER);
        aria.verify();
        if (setup.durationTicks() <= 0) {
            throw new IllegalArgumentException(profile.name() + " 的对抗时限必须为正数");
        }

        boolean[] wins = new boolean[scenario.trials()];
        int landed = 0;
        long steps = 0;
        long hits = 0;
        double finalProgress = 0;
        for (int trial = 0; trial < wins.length; trial++) {
            long seed = scenario.seed() + trial;
            var rod = profile.rod();
            String baitId = profile.bait() == null ? null : profile.bait().id();
            var offer = fish.activeTreasure()
                ? FishingService.rollTreasure(configuration, water, profile.bait(), rod.treasureChanceBonus(),
                    new Random(seed ^ 0x7f4a7c15L))
                : null;
            int caughtSize = fish.randomSize(new Random(seed ^ 0x53495a45L));
            var session = new FishingSession(PLAYER, fish, caughtSize, profile.level(), setup.durationTicks(),
                water, setup.difficulty(), baitId, rod.greenBarHeightBonus(), rod.expMultiplier(), rod.treasureChanceBonus(),
                FishingSession.Gear.resolve(rod, profile.bait(), fish), offer, null, null, scenario.current(), false);
            var fight = new FishingFight(settings, session, profile.bonuses(), setup.fatigueMultiplier(), aria, new Random(seed));
            var controller = new TrackingInput(scenario.reactionSteps(), scenario.minimumHoldSteps(),
                settings.barClickForce() * (1 + profile.bonuses().barClickForceBonus())
                    > settings.barGravity() * (1 - profile.bonuses().barGravityReduction()));
            Frame frame = fight.frame();
            if (trace != null && trial == 0) writeFrame(trace, id, seed, false, frame);
            do {
                boolean pressing = controller.pressing(frame);
                frame = fight.step(pressing, controller.shouldSlacken());
                if (frame.fishInside()) hits++;
                if (trace != null && trial == 0) writeFrame(trace, id, seed, pressing, frame);
            } while (!frame.finished());
            aria.verify();
            wins[trial] = frame.landed();
            if (frame.landed()) landed++;
            steps += frame.step();
            finalProgress += frame.progress();
        }
        double encounter = encounterProbability(scenario, profile, configuration, fatigue);
        return new Result(setup, encounter, wins, landed, finalProgress / wins.length, (double) hits / steps,
            (double) steps / wins.length * Math.max(1, settings.minigameTickInterval()) / 20);
    }

    private static double encounterProbability(Scenario scenario, Profile profile,
                                               FishingModuleConfiguration configuration, FatigueConfig fatigue) {
        List<FishDefinition> candidates = FishingService.selectCandidates(scenario.pool(), profile.level(),
            scenario.permissions()::contains, scenario.season(), scenario.weather(), scenario.time(), scenario.biome());
        if (candidates.isEmpty()) return 0;
        double targetWeight = 0;
        double totalWeight = 0;
        for (FishDefinition fish : candidates) {
            double weight = FishingService.fishWeight(fish, scenario.water(), profile.bait());
            totalWeight += validWeight(weight);
            if (fish.id().equals(scenario.fish().id())) targetWeight = weight;
        }
        double normalChance = totalWeight > 0 ? targetWeight / totalWeight
            : (candidates.contains(scenario.fish()) ? 1.0 / candidates.size() : 0);

        if (!fatigue.enabled() || !fatigue.isInSpecialFishRange(profile.fatigue())) return normalChance;
        double specialTotal = 0;
        double eligibleSpecial = 0;
        double targetSpecial = 0;
        for (FatigueConfig.SpecialFishEntry entry : fatigue.specialFishes()) {
            specialTotal += validWeight(entry.weight());
            FishDefinition fish = configuration.findFish(entry.fishId());
            if (fish != null && FishingService.canCatch(fish, profile.level(), scenario.permissions()::contains)) {
                eligibleSpecial += entry.weight();
                if (fish.id().equals(scenario.fish().id())) targetSpecial += entry.weight();
            }
        }
        if (specialTotal <= 0) return normalChance;
        double rollChance = Math.max(0, Math.min(1, fatigue.specialFishChance()));
        return normalChance * (1 - rollChance * eligibleSpecial / specialTotal)
            + rollChance * targetSpecial / specialTotal;
    }

    private static double validWeight(double weight) {
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("鱼池权重必须是非负的有限值，实际为 " + weight);
        }
        return weight;
    }

    private static void printResults(Scenario scenario, FishingModuleConfiguration configuration,
                                     SimulationAriaBridge aria, Map<String, Result> results) {
        long stepMs = 50L * Math.max(1, configuration.fishing().minigameTickInterval());
        System.out.printf(Locale.ROOT, "%s [%s] / %s / %s %s %s %s%n", scenario.fish().displayName(),
            scenario.fish().id(), scenario.water().id(), scenario.season(), scenario.weather(), scenario.time(), scenario.biome());
        if (scenario.current() != null) System.out.println("水流区 " + scenario.current().id());
        System.out.printf(Locale.ROOT, "每档 %d 局，起始种子 %d，步长 %d ms（20 TPS），反应 %d 步，最短按键间隔 %d 步，Aria %s%n%n",
            scenario.trials(), scenario.seed(), stepMs, scenario.reactionSteps(), scenario.minimumHoldSteps(), aria.version());
        System.out.println("| 场景 | 等级 / 竿 / 饵 / 疲惫 | 遇到后收竿 | 遇鱼概率 | 每次有效咬钩获鱼估计 | 平均终局进度 | 平均用时 | 对照档同种子翻转 |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |");
        Result baseline = results.get(scenario.comparisonBase());
        for (var entry : results.entrySet()) {
            Profile profile = scenario.profiles().get(entry.getKey());
            Result result = entry.getValue();
            String paired = "—";
            if (entry.getKey().contains("/")) {
                int gained = 0;
                int lost = 0;
                for (int i = 0; i < result.wins().length; i++) {
                    if (result.wins()[i] && !baseline.wins()[i]) gained++;
                    if (!result.wins()[i] && baseline.wins()[i]) lost++;
                }
                paired = "+" + gained + " / -" + lost;
            }
            String bait = profile.bait() == null ? "无" : profile.bait().id();
            System.out.printf(Locale.ROOT,
                "| %s | %d / %s / %s / %d | %d/%d (%.2f%%) | %.2f%% | %.2f%% | %.2f%% | %.2fs | %s |%n",
                profile.name(), profile.level(), profile.rod().id(), bait, profile.fatigue(),
                result.landed(), result.wins().length, result.successRate() * 100,
                result.encounter() * 100, result.encounter() * result.successRate() * 100,
                result.finalProgress() * 100, result.meanSeconds(), paired);
        }
        System.out.println();
        for (var entry : results.entrySet()) {
            Result result = entry.getValue();
            var setup = result.setup();
            System.out.printf(Locale.ROOT, "%s  难度=%d  时限=%d步  疲惫倍率=%.3f  判定命中率=%.2f%%%n",
                entry.getKey(), setup.difficulty(), setup.durationTicks(), setup.fatigueMultiplier(), result.hitRate() * 100);
        }
    }

    private static void writeFrame(BufferedWriter trace, String id, long seed, boolean pressing, Frame frame) throws IOException {
        trace.write(String.format(Locale.ROOT,
            "%s\t%d\t%d\t%b\t%.8f\t%.8f\t%.8f\t%.8f\t%d\t%b\t%d\t%s\t%b\t%b\t%b\t%s\t%.2f\t%b\t%b\t%b\t%b"
                + "\t%.2f\t%d\t%.8f",
            id, seed, frame.step(), pressing, frame.fishY(), frame.barY(), frame.barHeight(), frame.progress(),
            frame.remainingTicks(), frame.fishInside(), frame.behaviorStage(), frame.behavior(), frame.fleeing(),
            frame.landed(), frame.timedOut(), frame.phase(), frame.tension(), frame.slackened(), frame.lineSnapped(),
            frame.treasureVisible(), frame.treasureClaimed(), frame.stamina(), frame.exhaustionSteps(), frame.currentDrift()));
        trace.newLine();
    }

    private static YamlConfiguration load(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml;
    }

    private static String requiredText(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少 " + section.getCurrentPath() + " 的 " + key);
        }
        return value;
    }

    private static RodDefinition rod(FishingModuleConfiguration configuration, String id, int level) {
        RodDefinition rod = id.equals("default") ? RodDefinition.DEFAULT : configuration.findRod(id);
        if (rod == null) throw new IllegalArgumentException("未知鱼竿 " + id);
        if (level < rod.minPlayerLevel() || (rod.maxPlayerLevel() > 0 && level > rod.maxPlayerLevel())) {
            throw new IllegalArgumentException("等级 " + level + " 无法使用 " + id);
        }
        return rod;
    }

    private static BaitDefinition bait(FishingModuleConfiguration configuration, String id) {
        if (id.equals("default")) {
            return configuration.baits().stream().filter(BaitDefinition::isDefault).findFirst().orElse(null);
        }
        BaitDefinition bait = configuration.findBait(id);
        if (bait == null) throw new IllegalArgumentException("未知鱼饵 " + id);
        return bait;
    }

    private static int checkedFatigue(FatigueConfig config, int value) {
        if (value < 0 || value > config.maxFatigue()) {
            throw new IllegalArgumentException("疲惫值超出配置范围: " + value);
        }
        return value;
    }

    private record Profile(String name, int level, RodDefinition rod, BaitDefinition bait, int fatigue, TalentBonuses bonuses) {}

    private record Result(FishingFight.Setup setup, double encounter, boolean[] wins, int landed,
                          double finalProgress, double hitRate, double meanSeconds) {
        double successRate() {
            return (double) landed / wins.length;
        }
    }

    private record Scenario(FishDefinition fish, WaterArea water, WaterArea.CurrentZone current, List<FishDefinition> pool,
                            String season, String weather, String time, String biome, Set<String> permissions,
                            long seed, int trials, int reactionSteps, int minimumHoldSteps,
                            Map<String, Profile> profiles, String comparisonBase) {
        static Scenario load(Path path, FishingModuleConfiguration configuration,
                             FatigueConfig fatigue, TalentEngine talents) throws Exception {
            YamlConfiguration yaml = FishingSimulationCli.load(path);
            String fishId = requiredText(yaml, "fish");
            FishDefinition fish = configuration.findFish(fishId);
            if (fish == null) throw new IllegalArgumentException("未知鱼种 " + fishId);

            String waterId = requiredText(yaml, "water");
            WaterArea water = configuration.defaultWater().id().equals(waterId) ? configuration.defaultWater()
                : configuration.specifiedWaters().stream().filter(area -> area.id().equals(waterId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未知或未启用的水域 " + waterId));
            String currentId = yaml.getString("current-zone");
            var current = currentId == null ? null : water.currentZones().stream()
                .filter(zone -> zone.id().equals(currentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("水域 " + waterId + " 没有水流区 " + currentId));
            List<FishDefinition> pool = new ArrayList<>();
            for (String id : water.fishes()) {
                FishDefinition entry = configuration.findFish(id);
                if (entry != null) pool.add(entry);
            }
            if (pool.stream().distinct().count() != pool.size()) {
                throw new IllegalArgumentException("水域鱼池含重复鱼种，无法计算权重概率: " + water.id());
            }
            Set<String> permissions = Set.copyOf(yaml.getStringList("permissions"));
            if (water.requirePermission() != null && !water.requirePermission().isBlank()
                && !permissions.contains(water.requirePermission())) {
                throw new IllegalArgumentException("进入水域需要权限 " + water.requirePermission());
            }
            String time = requiredText(yaml, "time");
            LocalTime.parse(time);
            int trials = yaml.getInt("trials");
            int reaction = yaml.getInt("reaction-steps", 3);
            int hold = yaml.getInt("minimum-hold-steps", 2);
            if (trials <= 0 || reaction < 0 || hold <= 0) {
                throw new IllegalArgumentException("trials 和 minimum-hold-steps 必须为正数，reaction-steps 不能为负");
            }
            var profiles = readProfiles(yaml.getConfigurationSection("profiles"), configuration, fatigue,
                talents, fish, water, permissions);
            ConfigurationSection compare = yaml.getConfigurationSection("compare");
            String comparisonBase = compare == null ? "" : requiredText(compare, "profile");
            if (compare != null) addComparisons(profiles, comparisonBase, compare, configuration, fatigue);
            return new Scenario(fish, water, current, List.copyOf(pool), requiredText(yaml, "season"), requiredText(yaml, "weather"),
                time, requiredText(yaml, "biome"), permissions, yaml.getLong("seed"), trials, reaction, hold,
                profiles, comparisonBase);
        }

        private static Map<String, Profile> readProfiles(ConfigurationSection section, FishingModuleConfiguration configuration,
                                                        FatigueConfig fatigue, TalentEngine talents, FishDefinition fish,
                                                        WaterArea water, Set<String> permissions) {
            if (section == null || section.getKeys(false).isEmpty()) {
                throw new IllegalArgumentException("场景至少需要一个配装");
            }
            Map<String, Profile> profiles = new LinkedHashMap<>();
            for (String id : section.getKeys(false)) {
                if (id.contains("/")) throw new IllegalArgumentException("配装 ID 不能包含 /: " + id);
                ConfigurationSection data = section.getConfigurationSection(id);
                if (data == null || !data.isInt("level")) throw new IllegalArgumentException("缺少配装等级 " + id);
                int level = data.getInt("level");
                if (level < 0 || level > configuration.season().maxLevel()
                    || !FishingService.canCatch(fish, level, permissions::contains)
                    || level < water.minPlayerLevel() || (water.maxPlayerLevel() > 0 && level > water.maxPlayerLevel())) {
                    throw new IllegalArgumentException("配装 " + id + " 的等级或权限无法在此水域挑战 " + fish.id());
                }
                int fatigueValue = checkedFatigue(fatigue, data.getInt("fatigue"));
                var season = new FishingSeasonData(PLAYER, configuration.season().seasonId(), level, 0, level, level,
                    Map.of(), fatigueValue, false);
                for (String node : data.getStringList("talents")) {
                    if (!talents.canLevelUp(season, node)) {
                        throw new IllegalArgumentException("配装 " + id + " 无法按顺序点亮天赋 " + node);
                    }
                    var upgraded = talents.levelUp(season, node);
                    if (upgraded.equals(season)) throw new IllegalArgumentException("重复点亮天赋 " + node);
                    season = upgraded;
                }
                profiles.put(id, new Profile(data.getString("name", id), level, rod(configuration, requiredText(data, "rod"), level),
                    bait(configuration, data.getString("bait", "default")), fatigueValue,
                    new TalentBonuses(talents.computeBonuses(season))));
            }
            return profiles;
        }

        private static void addComparisons(Map<String, Profile> profiles, String baseline, ConfigurationSection section,
                                           FishingModuleConfiguration configuration, FatigueConfig fatigue) {
            Profile base = profiles.get(baseline);
            if (base == null) throw new IllegalArgumentException("未知对照档 " + baseline);
            for (String id : section.getStringList("rods")) {
                profiles.put("rod/" + id, new Profile("鱼竿 " + id, base.level(), rod(configuration, id, base.level()),
                    base.bait(), base.fatigue(), base.bonuses()));
            }
            for (String id : section.getStringList("baits")) {
                profiles.put("bait/" + id, new Profile("鱼饵 " + id, base.level(), base.rod(), bait(configuration, id),
                    base.fatigue(), base.bonuses()));
            }
            for (int value : section.getIntegerList("fatigue")) {
                profiles.put("fatigue/" + value, new Profile("疲惫 " + value, base.level(), base.rod(), base.bait(),
                    checkedFatigue(fatigue, value), base.bonuses()));
            }
        }
    }

    private static final class TrackingInput {
        private final int reactionSteps;
        private final int minimumHoldSteps;
        private final boolean pressMovesUp;
        private final ArrayDeque<Frame> observations = new ArrayDeque<>();
        private Frame previous;
        private boolean pressing;
        private long lastChange;

        TrackingInput(int reactionSteps, int minimumHoldSteps, boolean pressMovesUp) {
            this.reactionSteps = reactionSteps;
            this.minimumHoldSteps = minimumHoldSteps;
            this.pressMovesUp = pressMovesUp;
        }

        boolean shouldSlacken() {
            return previous != null && previous.tension() >= 80 && previous.slackCooldown() == 0;
        }

        boolean pressing(Frame frame) {
            observations.addLast(frame);
            if (observations.size() <= reactionSteps) return pressing;
            Frame seen = observations.removeFirst();
            double fishDelta = previous == null ? 0 : seen.fishY() - previous.fishY();
            double barDelta = previous == null ? 0 : seen.barY() - previous.barY();
            previous = seen;

            double fishTarget = clamp(seen.fishY() + fishDelta * reactionSteps, 0, 1);
            double halfBar = seen.barHeight() / 2;
            double barTarget = clamp(seen.barY() + barDelta * (reactionSteps + 2.0), halfBar, 1 - halfBar);
            double error = fishTarget - barTarget;
            if (frame.step() - lastChange >= minimumHoldSteps && Math.abs(error) > 0.01) {
                boolean desired = (error > 0) == pressMovesUp;
                if (desired != pressing) {
                    pressing = desired;
                    lastChange = frame.step();
                }
            }
            return pressing;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
