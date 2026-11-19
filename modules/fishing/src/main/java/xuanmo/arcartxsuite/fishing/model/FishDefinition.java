package xuanmo.arcartxsuite.fishing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

public record FishDefinition(
    // ─── 基础属性 ──────────────────────────────────────
    @NotNull String id,
    @NotNull String displayName,
    @NotNull FishRarity rarity,
    int minSize,
    int maxSize,
    int basePrice,
    int baseXp,
    @NotNull List<String> seasons,
    @NotNull List<String> weathers,
    @NotNull List<TimeRange> timeRanges,
    @NotNull IconDefinition itemRef,
    int difficulty,
    @NotNull BehaviorPlan behaviorPlan,
    @Nullable CurrencyReward currencyReward,

    // ─── 小游戏机制（可选，覆盖全局配置） ──────────────
    @Nullable Double progressDrainRate,
    @Nullable Double progressGainRate,
    @Nullable FleeParams fleeParams,
    @NotNull List<BehaviorStage> behaviorStages,
    boolean lineTension,
    @NotNull SizeDifficulty sizeDifficulty,
    @Nullable Stamina stamina,
    @NotNull Personalities personalities,
    @Nullable HookSet hookSet,
    boolean activeTreasure,

    // ─── 奖励（可选） ──────────────────────────────────
    @NotNull List<DropEntry> drops,
    @NotNull List<CommandReward> commandRewards,
    @NotNull List<CurrencyReward> currencyRewards,

    // ─── 出现条件（可选） ──────────────────────────────
    int minPlayerLevel,
    int maxPlayerLevel,
    @Nullable String requirePermission,
    @NotNull List<BaitPreference> baitPreferences,
    @NotNull List<String> biomes,
    @Nullable DepthRange depthRange,

    // ─── 数据/展示（可选） ─────────────────────────────
    @Nullable String description,
    @Nullable IconDefinition iconRef,
    double recordWeight
) {

    /**
     * 获取用于 UI 显示的图标定义，优先使用独立配置的 iconRef，未配置时回退到 itemRef。
     */
    public @NotNull IconDefinition displayIcon() {
        return iconRef != null ? iconRef : itemRef;
    }

    public int randomSize() {
        return randomSize(ThreadLocalRandom.current());
    }

    public int randomSize(RandomGenerator random) {
        if (minSize >= maxSize) return minSize;
        return (int) random.nextLong(minSize, (long) maxSize + 1);
    }

    public record Personality(String id, String displayName, double weight, boolean firstMissRush,
                              double staminaDrainMultiplier) {
        public static final Personality NORMAL = new Personality("normal", "普通", 1, false, 1);

        public Personality {
            if (!(weight > 0 && weight <= 1000) || !(staminaDrainMultiplier >= 0.25 && staminaDrainMultiplier <= 4)) {
                throw new IllegalArgumentException("鱼的性格权重或体力消耗倍率无效 " + id);
            }
        }
    }

    public record Personalities(double normalWeight, List<Personality> variants) {
        public static final Personalities DEFAULT = new Personalities(1, List.of());

        public Personalities {
            variants = List.copyOf(variants);
            if (!(normalWeight >= 0 && normalWeight <= 1000) || (variants.isEmpty() && normalWeight == 0)
                || variants.stream().anyMatch(personality -> personality.id().equals("normal"))) {
                throw new IllegalArgumentException("鱼的性格配置无效，normal 为普通性格保留");
            }
        }

        public Personality roll(RandomGenerator random) {
            if (variants.isEmpty()) return Personality.NORMAL;
            double ticket = random.nextDouble(normalWeight + variants.stream().mapToDouble(Personality::weight).sum());
            for (Personality variant : variants) {
                ticket -= variant.weight();
                if (ticket < 0) return variant;
            }
            return normalWeight > 0 ? Personality.NORMAL : variants.get(variants.size() - 1);
        }

        public @Nullable Personality find(String id) {
            if (id.equals("normal")) return Personality.NORMAL;
            return variants.stream().filter(variant -> variant.id().equals(id)).findFirst().orElse(null);
        }
    }

    public record HookSet(int tapTicks, int windowTicks, int lateTicks, int goodRecoveryBonus, int missRecoveryPenalty) {
        public static final HookSet DEFAULT = new HookSet(12, 8, 12, 8, 4);

        public HookSet {
            if (tapTicks < 1 || tapTicks > 200 || windowTicks < 1 || windowTicks > 200
                || lateTicks < 1 || lateTicks > 200 || goodRecoveryBonus < 0 || missRecoveryPenalty < 0) {
                throw new IllegalArgumentException("提竿窗口或回稳修正无效");
            }
        }
    }

    public record Stamina(double rushDrainPerTick, int exhaustedTicks, double progressGainMultiplier) {
        public Stamina {
            if (!(rushDrainPerTick > 0 && rushDrainPerTick <= 100) || exhaustedTicks <= 0
                || !(progressGainMultiplier >= 1 && progressGainMultiplier <= 3)) {
                throw new IllegalArgumentException("鱼的体力消耗、力竭时长或进度倍率无效");
            }
        }
    }

    public record SizeModifiers(double rushSpeed, double rushDuration, double pull) {
        public static final SizeModifiers NEUTRAL = new SizeModifiers(1, 1, 1);

        public SizeModifiers {
            if (!valid(rushSpeed) || !valid(rushDuration) || !valid(pull)) {
                throw new IllegalArgumentException("size-difficulty 倍率必须在 0.25 到 4.0 之间");
            }
        }

        private static boolean valid(double value) {
            return value >= 0.25 && value <= 4;
        }
    }

    public record SizeDifficulty(SizeModifiers small, SizeModifiers large) {
        public static final SizeDifficulty NEUTRAL = new SizeDifficulty(SizeModifiers.NEUTRAL, SizeModifiers.NEUTRAL);

        public SizeModifiers resolve(int caughtSize, int minSize, int maxSize) {
            if (small.equals(large)) return small;
            double fraction = maxSize <= minSize ? 0.5
                : Math.max(0, Math.min(1, (caughtSize - (double) minSize) / (maxSize - (double) minSize)));
            if (fraction == 0) return small;
            if (fraction == 1) return large;
            return new SizeModifiers(
                interpolate(small.rushSpeed(), large.rushSpeed(), fraction),
                interpolate(small.rushDuration(), large.rushDuration(), fraction),
                interpolate(small.pull(), large.pull(), fraction));
        }

        private static double interpolate(double small, double large, double fraction) {
            return small + (large - small) * fraction;
        }
    }

    public double sizeFactor(int caughtSize) {
        if (maxSize <= minSize) return 1.0;
        double factor = (double) (caughtSize - minSize) / (maxSize - minSize);
        return Math.max(0.5, Math.min(1.5, factor));
    }

    public int calculateXp(int caughtSize, boolean perfect) {
        double factor = sizeFactor(caughtSize);
        int xp = (int) (baseXp * factor);
        if (perfect) xp = (int) (xp * 1.5);
        return Math.max(1, xp);
    }

    public int calculatePrice(int caughtSize) {
        double factor = sizeFactor(caughtSize);
        // L5: 防止 basePrice 配置为负时返回负数
        return Math.max(0, (int) (basePrice * factor));
    }

    /**
     * 根据当前进度选择行为阶段。
     * behaviorStages 为空时返回 null，表示使用默认 behaviors。
     * 否则选取 threshold ≤ progress 的最后一个阶段。
     */
    public @Nullable BehaviorStage selectBehaviorStage(double progress) {
        if (behaviorStages.isEmpty()) return null;
        BehaviorStage selected = null;
        for (BehaviorStage stage : behaviorStages) {
            if (progress >= stage.threshold()) {
                selected = stage;
            } else {
                break;
            }
        }
        return selected;
    }

    // ─── 内嵌 record ──────────────────────────────────

    public record TimeRange(String start, String end) {}

    public enum BehaviorMode { RANDOM, SEQUENCE }

    public record BehaviorEntry(FishBehaviorType type, double weight, int durationTicks, int tellTicks, int recoveryTicks) {
        public BehaviorEntry {
            if (durationTicks < 0 || tellTicks < 0 || recoveryTicks < 0
                    || ((tellTicks > 0 || recoveryTicks > 0) && durationTicks == 0)) {
                throw new IllegalArgumentException("动作时长无效，预警和回稳要求正数 duration-ticks");
            }
        }
        public BehaviorEntry(FishBehaviorType type, double weight, int durationTicks) {
            this(type, weight, durationTicks, 0, 0);
        }
        public BehaviorEntry(FishBehaviorType type, double weight) {
            this(type, weight, 0);
        }
    }

    public record BehaviorPlan(BehaviorMode mode, List<BehaviorEntry> entries) {
        private static final BehaviorEntry FALLBACK = new BehaviorEntry(FishBehaviorType.SMOOTH, 1.0);

        public BehaviorPlan {
            entries = List.copyOf(entries);
            if (mode == BehaviorMode.SEQUENCE
                && (entries.isEmpty() || entries.stream().anyMatch(entry -> entry.durationTicks() <= 0))) {
                throw new IllegalArgumentException("sequence 需要至少一个动作，且每个动作的 duration-ticks 必须为正整数");
            }
        }

        public int nextIndex(int previous, RandomGenerator random) {
            if (mode == BehaviorMode.SEQUENCE) return (previous + 1) % entries.size();

            double totalWeight = entries.stream().mapToDouble(BehaviorEntry::weight).sum();
            if (totalWeight <= 0) return -1;
            double roll = random.nextDouble() * totalWeight;
            double cumulative = 0;
            for (int i = 0; i < entries.size(); i++) {
                cumulative += entries.get(i).weight();
                if (roll < cumulative) return i;
            }
            return entries.size() - 1;
        }

        public BehaviorEntry entryAt(int index) {
            return index < 0 ? FALLBACK : entries.get(index);
        }
    }

    /**
     * 行为阶段：进度达到 threshold 后切换行为模式。
     */
    public record BehaviorStage(double threshold, @NotNull BehaviorPlan behaviorPlan) {}

    /**
     * 货币奖励定义。
     * currencyId 为空字符串时表示不发放货币奖励。
     */
    public record CurrencyReward(@NotNull String currencyId, double amount) {}

    /**
     * 逃离参数覆盖（可选）。所有字段 nullable，null 表示用全局配置。
     */
    public record FleeParams(
        @Nullable Double baseChance,
        @Nullable Double chancePerDifficulty,
        @Nullable Double baseSpeed,
        @Nullable Double speedPerDifficulty,
        @Nullable Integer minDuration,
        @Nullable Integer maxDuration,
        @Nullable Integer checkMinInterval,
        @Nullable Integer checkMaxInterval,
        @Nullable Integer cooldownMin,
        @Nullable Integer cooldownMax
    ) {}

    /**
     * 额外掉落物品（多物品掉落）。
     * condition 为空表示无条件；支持 "perfect"、"size:N"（尺寸≥N）、"first_catch"。
     */
    public record DropEntry(
        @NotNull IconDefinition itemRef,
        double weight,
        @NotNull String condition
    ) {}

    /**
     * 命令奖励。condition 同 DropEntry。
     */
    public record CommandReward(@NotNull String command, @NotNull String condition) {}

    /**
     * 饵料偏好：指定饵料对该鱼的出现概率倍率。
     */
    public record BaitPreference(@NotNull String baitId, double multiplier) {}

    /**
     * 深度限制（方块深度，从水面往下计算）。
     */
    public record DepthRange(int minDepth, int maxDepth) {}

    // ─── Builder ──────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        // 基础
        private String id = "";
        private String displayName = "";
        private FishRarity rarity = FishRarity.COMMON;
        private int minSize = 1;
        private int maxSize = 10;
        private int basePrice = 10;
        private int baseXp = 5;
        private List<String> seasons = List.of();
        private List<String> weathers = List.of();
        private List<TimeRange> timeRanges = List.of();
        private IconDefinition itemRef = IconDefinition.load("minecraft:cod");
        private int difficulty = 10;
        private BehaviorPlan behaviorPlan = new BehaviorPlan(BehaviorMode.RANDOM, List.of());
        private CurrencyReward currencyReward = null;

        // 小游戏机制
        private Double progressDrainRate = null;
        private Double progressGainRate = null;
        private FleeParams fleeParams = null;
        private List<BehaviorStage> behaviorStages = List.of();
        private boolean lineTension;
        private SizeDifficulty sizeDifficulty = SizeDifficulty.NEUTRAL;
        private Stamina stamina;
        private Personalities personalities = Personalities.DEFAULT;
        private HookSet hookSet;
        private boolean activeTreasure;

        // 奖励
        private List<DropEntry> drops = List.of();
        private List<CommandReward> commandRewards = List.of();
        private List<CurrencyReward> currencyRewards = List.of();

        // 出现条件
        private int minPlayerLevel = 0;
        private int maxPlayerLevel = 0;
        private String requirePermission = null;
        private List<BaitPreference> baitPreferences = List.of();
        private List<String> biomes = List.of();
        private DepthRange depthRange = null;

        // 数据/展示
        private String description = null;
        private IconDefinition iconRef = null;
        private double recordWeight = 1.0;

        public Builder id(String id) { this.id = id; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder rarity(FishRarity rarity) { this.rarity = rarity; return this; }
        public Builder minSize(int minSize) { this.minSize = minSize; return this; }
        public Builder maxSize(int maxSize) { this.maxSize = maxSize; return this; }
        public Builder basePrice(int basePrice) { this.basePrice = basePrice; return this; }
        public Builder baseXp(int baseXp) { this.baseXp = baseXp; return this; }
        public Builder seasons(List<String> seasons) { this.seasons = List.copyOf(seasons); return this; }
        public Builder weathers(List<String> weathers) { this.weathers = List.copyOf(weathers); return this; }
        public Builder timeRanges(List<TimeRange> timeRanges) { this.timeRanges = List.copyOf(timeRanges); return this; }
        public Builder itemRef(IconDefinition itemRef) { this.itemRef = itemRef; return this; }
        public Builder difficulty(int difficulty) { this.difficulty = difficulty; return this; }
        public Builder behaviors(List<BehaviorEntry> behaviors) {
            return behaviorPlan(new BehaviorPlan(BehaviorMode.RANDOM, behaviors));
        }
        public Builder behaviorPlan(BehaviorPlan behaviorPlan) { this.behaviorPlan = behaviorPlan; return this; }
        public Builder currencyReward(CurrencyReward currencyReward) { this.currencyReward = currencyReward; return this; }
        public Builder progressDrainRate(Double progressDrainRate) { this.progressDrainRate = progressDrainRate; return this; }
        public Builder progressGainRate(Double progressGainRate) { this.progressGainRate = progressGainRate; return this; }
        public Builder fleeParams(FleeParams fleeParams) { this.fleeParams = fleeParams; return this; }
        public Builder behaviorStages(List<BehaviorStage> behaviorStages) { this.behaviorStages = List.copyOf(behaviorStages); return this; }
        public Builder lineTension(boolean enabled) { this.lineTension = enabled; return this; }
        public Builder sizeDifficulty(SizeDifficulty sizeDifficulty) { this.sizeDifficulty = sizeDifficulty; return this; }
        public Builder stamina(Stamina stamina) { this.stamina = stamina; return this; }
        public Builder personalities(Personalities personalities) { this.personalities = personalities; return this; }
        public Builder hookSet(HookSet hookSet) { this.hookSet = hookSet; return this; }
        public Builder activeTreasure(boolean enabled) { this.activeTreasure = enabled; return this; }
        public Builder drops(List<DropEntry> drops) { this.drops = List.copyOf(drops); return this; }
        public Builder commandRewards(List<CommandReward> commandRewards) { this.commandRewards = List.copyOf(commandRewards); return this; }
        public Builder currencyRewards(List<CurrencyReward> currencyRewards) { this.currencyRewards = List.copyOf(currencyRewards); return this; }
        public Builder minPlayerLevel(int minPlayerLevel) { this.minPlayerLevel = minPlayerLevel; return this; }
        public Builder maxPlayerLevel(int maxPlayerLevel) { this.maxPlayerLevel = maxPlayerLevel; return this; }
        public Builder requirePermission(String requirePermission) { this.requirePermission = requirePermission; return this; }
        public Builder baitPreferences(List<BaitPreference> baitPreferences) { this.baitPreferences = List.copyOf(baitPreferences); return this; }
        public Builder biomes(List<String> biomes) { this.biomes = List.copyOf(biomes); return this; }
        public Builder depthRange(DepthRange depthRange) { this.depthRange = depthRange; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder iconRef(IconDefinition iconRef) { this.iconRef = iconRef; return this; }
        public Builder recordWeight(double recordWeight) { this.recordWeight = recordWeight; return this; }

        public FishDefinition build() {
            return new FishDefinition(
                id, displayName, rarity, minSize, maxSize, basePrice, baseXp,
                seasons, weathers, timeRanges, itemRef, difficulty, behaviorPlan, currencyReward,
                progressDrainRate, progressGainRate, fleeParams, behaviorStages, lineTension, sizeDifficulty, stamina,
                personalities, hookSet, activeTreasure,
                drops, commandRewards, currencyRewards,
                minPlayerLevel, maxPlayerLevel, requirePermission, baitPreferences, biomes, depthRange,
                description, iconRef, recordWeight
            );
        }
    }
}
