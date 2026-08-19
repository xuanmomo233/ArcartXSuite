package xuanmo.arcartxsuite.fishing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    @NotNull List<String> waterTypes,
    @NotNull List<TimeRange> timeRanges,
    @NotNull FishingItemRef itemRef,
    int difficulty,
    @NotNull List<BehaviorEntry> behaviors,
    @Nullable CurrencyReward currencyReward,

    // ─── 小游戏机制（可选，覆盖全局配置） ──────────────
    @Nullable Double progressDrainRate,
    @Nullable Double progressGainRate,
    @Nullable FleeParams fleeParams,
    @NotNull List<BehaviorStage> behaviorStages,

    // ─── 奖励（可选） ──────────────────────────────────
    @NotNull List<DropEntry> drops,
    @NotNull List<CommandReward> commandRewards,
    @NotNull List<CurrencyReward> currencyRewards,

    // ─── 出现条件（可选） ──────────────────────────────
    int minPlayerLevel,
    @Nullable String requirePermission,
    @NotNull List<BaitPreference> baitPreferences,
    @NotNull List<String> biomes,
    @Nullable DepthRange depthRange,

    // ─── 数据/展示（可选） ─────────────────────────────
    @Nullable String description,
    @Nullable String iconTexture,
    @Nullable String iconTextureUrl,
    double recordWeight
) {

    public int randomSize() {
        if (minSize >= maxSize) return minSize;
        return minSize + ThreadLocalRandom.current().nextInt(maxSize - minSize + 1);
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
        return (int) (basePrice * factor);
    }

    public @NotNull FishBehaviorType randomBehavior() {
        double totalWeight = behaviors.stream().mapToDouble(BehaviorEntry::weight).sum();
        if (totalWeight <= 0) return FishBehaviorType.SMOOTH;
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (BehaviorEntry entry : behaviors) {
            cumulative += entry.weight();
            if (roll <= cumulative) {
                return entry.type();
            }
        }
        return behaviors.get(behaviors.size() - 1).type();
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

    public record BehaviorEntry(FishBehaviorType type, double weight) {}

    /**
     * 行为阶段：进度达到 threshold 后切换行为模式。
     */
    public record BehaviorStage(double threshold, @NotNull List<BehaviorEntry> behaviors) {}

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
        @NotNull FishingItemRef itemRef,
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
        private List<String> waterTypes = List.of();
        private List<TimeRange> timeRanges = List.of();
        private FishingItemRef itemRef = FishingItemRef.fromMaterial("minecraft:cod", 1);
        private int difficulty = 10;
        private List<BehaviorEntry> behaviors = List.of();
        private CurrencyReward currencyReward = null;

        // 小游戏机制
        private Double progressDrainRate = null;
        private Double progressGainRate = null;
        private FleeParams fleeParams = null;
        private List<BehaviorStage> behaviorStages = List.of();

        // 奖励
        private List<DropEntry> drops = List.of();
        private List<CommandReward> commandRewards = List.of();
        private List<CurrencyReward> currencyRewards = List.of();

        // 出现条件
        private int minPlayerLevel = 0;
        private String requirePermission = null;
        private List<BaitPreference> baitPreferences = List.of();
        private List<String> biomes = List.of();
        private DepthRange depthRange = null;

        // 数据/展示
        private String description = null;
        private String iconTexture = null;
        private String iconTextureUrl = null;
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
        public Builder waterTypes(List<String> waterTypes) { this.waterTypes = List.copyOf(waterTypes); return this; }
        public Builder timeRanges(List<TimeRange> timeRanges) { this.timeRanges = List.copyOf(timeRanges); return this; }
        public Builder itemRef(FishingItemRef itemRef) { this.itemRef = itemRef; return this; }
        public Builder difficulty(int difficulty) { this.difficulty = difficulty; return this; }
        public Builder behaviors(List<BehaviorEntry> behaviors) { this.behaviors = List.copyOf(behaviors); return this; }
        public Builder currencyReward(CurrencyReward currencyReward) { this.currencyReward = currencyReward; return this; }
        public Builder progressDrainRate(Double progressDrainRate) { this.progressDrainRate = progressDrainRate; return this; }
        public Builder progressGainRate(Double progressGainRate) { this.progressGainRate = progressGainRate; return this; }
        public Builder fleeParams(FleeParams fleeParams) { this.fleeParams = fleeParams; return this; }
        public Builder behaviorStages(List<BehaviorStage> behaviorStages) { this.behaviorStages = List.copyOf(behaviorStages); return this; }
        public Builder drops(List<DropEntry> drops) { this.drops = List.copyOf(drops); return this; }
        public Builder commandRewards(List<CommandReward> commandRewards) { this.commandRewards = List.copyOf(commandRewards); return this; }
        public Builder currencyRewards(List<CurrencyReward> currencyRewards) { this.currencyRewards = List.copyOf(currencyRewards); return this; }
        public Builder minPlayerLevel(int minPlayerLevel) { this.minPlayerLevel = minPlayerLevel; return this; }
        public Builder requirePermission(String requirePermission) { this.requirePermission = requirePermission; return this; }
        public Builder baitPreferences(List<BaitPreference> baitPreferences) { this.baitPreferences = List.copyOf(baitPreferences); return this; }
        public Builder biomes(List<String> biomes) { this.biomes = List.copyOf(biomes); return this; }
        public Builder depthRange(DepthRange depthRange) { this.depthRange = depthRange; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder iconTexture(String iconTexture) { this.iconTexture = iconTexture; return this; }
        public Builder iconTextureUrl(String iconTextureUrl) { this.iconTextureUrl = iconTextureUrl; return this; }
        public Builder recordWeight(double recordWeight) { this.recordWeight = recordWeight; return this; }

        public FishDefinition build() {
            return new FishDefinition(
                id, displayName, rarity, minSize, maxSize, basePrice, baseXp,
                seasons, weathers, waterTypes, timeRanges, itemRef, difficulty, behaviors, currencyReward,
                progressDrainRate, progressGainRate, fleeParams, behaviorStages,
                drops, commandRewards, currencyRewards,
                minPlayerLevel, requirePermission, baitPreferences, biomes, depthRange,
                description, iconTexture, iconTextureUrl, recordWeight
            );
        }
    }
}
