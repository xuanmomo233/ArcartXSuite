package xuanmo.arcartxsuite.fishing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FishDefinition(
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
    @NotNull String item,
    int difficulty,
    @NotNull List<BehaviorEntry> behaviors,
    @Nullable CurrencyReward currencyReward
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

    public record TimeRange(String start, String end) {}

    public record BehaviorEntry(FishBehaviorType type, double weight) {}

    /**
     * 货币奖励定义。
     * currencyId 为空字符串时表示不发放货币奖励。
     */
    public record CurrencyReward(@NotNull String currencyId, double amount) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
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
        private String item = "minecraft:cod";
        private int difficulty = 10;
        private List<BehaviorEntry> behaviors = List.of();
        private CurrencyReward currencyReward = null;

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
        public Builder item(String item) { this.item = item; return this; }
        public Builder difficulty(int difficulty) { this.difficulty = difficulty; return this; }
        public Builder behaviors(List<BehaviorEntry> behaviors) { this.behaviors = List.copyOf(behaviors); return this; }
        public Builder currencyReward(CurrencyReward currencyReward) { this.currencyReward = currencyReward; return this; }

        public FishDefinition build() {
            return new FishDefinition(id, displayName, rarity, minSize, maxSize, basePrice, baseXp,
                seasons, weathers, waterTypes, timeRanges, item, difficulty, behaviors, currencyReward);
        }
    }
}
