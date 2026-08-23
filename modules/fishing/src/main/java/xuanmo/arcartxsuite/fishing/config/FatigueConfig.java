package xuanmo.arcartxsuite.fishing.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 疲惫值系统配置，从 fatigue.yml 加载。
 * <p>
 * 疲惫值范围 [0, 100]，玩家每钓一条鱼 +1，每在线休息一分钟 -1。
 * 疲惫值影响钓鱼效率（gain/drain/鱼速度/逃离/绿条高度等全部参数）。
 *
 * @param enabled               是否启用疲惫值系统
 * @param maxFatigue            疲惫值上限
 * @param fatiguePerCatch       每钓一条鱼增加的疲惫值
 * @param restDecreasePerMinute 每在线休息一分钟减少的疲惫值
 * @param restCheckIntervalTicks 定时任务检查间隔（ticks）
 * @param efficiencyTiers      效率分段列表（按 maxFatigue 升序）
 * @param specialFishChance     疲惫值 > 90 时出现特殊鱼的概率
 * @param specialFishes         特殊鱼列表（fish-id + weight）
 * @param firstMaxFatigueReward 首次疲惫值达到 100 的奖励
 */
public record FatigueConfig(
    boolean enabled,
    int maxFatigue,
    int fatiguePerCatch,
    int restDecreasePerMinute,
    int restCheckIntervalTicks,
    @NotNull List<EfficiencyTier> efficiencyTiers,
    double specialFishChance,
    @NotNull List<SpecialFishEntry> specialFishes,
    @Nullable FirstMaxFatigueReward firstMaxFatigueReward
) {

    /**
     * 效率分段：疲惫值在 [上一段maxFatigue+1, 此段maxFatigue] 范围内时使用此段的 multiplier。
     *
     * @param maxFatigue   此段疲惫值上限
     * @param multiplier   效率系数（>1 提升，<1 降低）
     * @param description  描述文本
     */
    public record EfficiencyTier(int maxFatigue, double multiplier, @NotNull String description) {}

    /**
     * 特殊鱼条目。
     *
     * @param fishId 鱼 ID（对应 fishes/ 目录下的鱼定义）
     * @param weight 权重
     */
    public record SpecialFishEntry(@NotNull String fishId, double weight) {}

    /**
     * 首次疲惫值达到 100 的奖励。
     *
     * @param enabled 是否启用
     * @param rewards 奖励列表
     */
    public record FirstMaxFatigueReward(boolean enabled, @NotNull List<RewardEntry> rewards) {}

    /**
     * 单条奖励。
     *
     * @param type        类型：currency / item / command
     * @param currencyId  货币 ID（type=currency 时）
     * @param amount      货币数量（type=currency 时）
     * @param command     命令（type=command 时，{player} 占位符替换为玩家名）
     * @param item        物品定义（type=item 时）
     * @param description 描述
     */
    public record RewardEntry(
        @NotNull String type,
        @Nullable String currencyId,
        double amount,
        @Nullable String command,
        @Nullable IconDefinition item,
        @NotNull String description
    ) {}

    /**
     * 根据疲惫值计算效率系数。
     * <p>
     * 在分段点之间使用线性插值平滑过渡。
     *
     * @param fatigue 疲惫值 [0, maxFatigue]
     * @return 效率系数（>1 提升，<1 降低，=1 无影响）
     */
    public double efficiencyMultiplier(int fatigue) {
        if (efficiencyTiers.isEmpty()) return 1.0;
        if (fatigue <= 0) return efficiencyTiers.get(0).multiplier();
        // 找到 fatigue 所在的分段
        int prevMax = -1;
        double prevMul = efficiencyTiers.get(0).multiplier();
        for (EfficiencyTier tier : efficiencyTiers) {
            if (fatigue <= tier.maxFatigue()) {
                // 在此段范围内，线性插值
                if (fatigue <= prevMax + 1) return tier.multiplier();
                double range = tier.maxFatigue() - prevMax;
                if (range <= 0) return tier.multiplier();
                double t = (fatigue - prevMax) / range;
                return prevMul + (tier.multiplier() - prevMul) * t;
            }
            prevMax = tier.maxFatigue();
            prevMul = tier.multiplier();
        }
        // 超过所有分段，返回最后一段
        return efficiencyTiers.get(efficiencyTiers.size() - 1).multiplier();
    }

    /**
     * 判断疲惫值是否在特殊鱼出现范围（>maxFatigue*0.9）。
     * <p>
     * M8: 使用动态阈值而非硬编码 90，适配非 100 上限的配置。
     */
    public boolean isInSpecialFishRange(int fatigue) {
        int threshold = (int) (maxFatigue * 0.9);
        return fatigue > threshold && fatigue <= maxFatigue;
    }

    /**
     * 从特殊鱼列表中按权重随机选择一条鱼 ID。
     *
     * @return 随机选中的鱼 ID，列表为空时返回 null
     */
    public @Nullable String rollSpecialFish() {
        if (specialFishes.isEmpty()) return null;
        double totalWeight = specialFishes.stream().mapToDouble(SpecialFishEntry::weight).sum();
        if (totalWeight <= 0) return null;
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (SpecialFishEntry entry : specialFishes) {
            cumulative += entry.weight;
            if (roll <= cumulative) return entry.fishId;
        }
        return specialFishes.get(specialFishes.size() - 1).fishId();
    }

    /**
     * 从 yml 加载疲惫值配置。
     */
    public static @NotNull FatigueConfig load(@NotNull YamlConfiguration yaml) {
        boolean enabled = yaml.getBoolean("enabled", true);
        int maxFatigue = yaml.getInt("max-fatigue", 100);
        int fatiguePerCatch = yaml.getInt("fatigue-per-catch", 1);
        int restDecreasePerMinute = yaml.getInt("rest-decrease-per-minute", 1);
        int restCheckIntervalTicks = yaml.getInt("rest-check-interval-ticks", 1200);

        // 效率分段
        List<EfficiencyTier> tiers = new ArrayList<>();
        List<?> tierList = yaml.getMapList("efficiency-tiers");
        for (Object obj : tierList) {
            if (obj instanceof java.util.Map<?, ?> map) {
                int tierMax = map.containsKey("max-fatigue") ? ((Number) map.get("max-fatigue")).intValue() : 100;
                double mul = map.containsKey("multiplier") ? ((Number) map.get("multiplier")).doubleValue() : 1.0;
                String desc = map.containsKey("description") ? String.valueOf(map.get("description")) : "";
                tiers.add(new EfficiencyTier(tierMax, mul, desc));
            }
        }
        if (tiers.isEmpty()) {
            tiers.add(new EfficiencyTier(100, 1.0, "默认"));
        }

        // 特殊鱼
        double specialFishChance = yaml.getDouble("special-fish-chance", 0.15);
        List<SpecialFishEntry> specialFishes = new ArrayList<>();
        List<?> sfList = yaml.getMapList("special-fishes");
        for (Object obj : sfList) {
            if (obj instanceof java.util.Map<?, ?> map) {
                String fishId = map.containsKey("fish-id") ? String.valueOf(map.get("fish-id")) : "";
                double weight = map.containsKey("weight") ? ((Number) map.get("weight")).doubleValue() : 1.0;
                if (!fishId.isEmpty()) {
                    specialFishes.add(new SpecialFishEntry(fishId, weight));
                }
            }
        }

        // 首次达到100奖励
        FirstMaxFatigueReward firstReward = null;
        ConfigurationSection rewardSection = yaml.getConfigurationSection("first-max-fatigue-reward");
        if (rewardSection != null) {
            boolean rewardEnabled = rewardSection.getBoolean("enabled", true);
            List<RewardEntry> entries = new ArrayList<>();
            List<?> rewardList = rewardSection.getMapList("rewards");
            for (Object obj : rewardList) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    String type = map.containsKey("type") ? String.valueOf(map.get("type")) : "";
                    String currencyId = map.containsKey("currency-id") ? String.valueOf(map.get("currency-id")) : null;
                    double amount = map.containsKey("amount") ? ((Number) map.get("amount")).doubleValue() : 0;
                    String command = map.containsKey("command") ? String.valueOf(map.get("command")) : null;
                    String desc = map.containsKey("description") ? String.valueOf(map.get("description")) : "";
                    IconDefinition item = null;
                    Object itemObj = map.get("item");
                    if (itemObj instanceof String itemStr) {
                        item = IconDefinition.load(itemStr);
                    } else if (itemObj instanceof java.util.Map<?, ?> itemMap) {
                        // 转为 MemoryConfiguration 以复用 IconDefinition.load(ConfigurationSection)
                        org.bukkit.configuration.MemoryConfiguration itemSection = new org.bukkit.configuration.MemoryConfiguration();
                        for (java.util.Map.Entry<?, ?> e : itemMap.entrySet()) {
                            itemSection.set(String.valueOf(e.getKey()), e.getValue());
                        }
                        item = IconDefinition.load(itemSection);
                    }
                    if (!type.isEmpty()) {
                        entries.add(new RewardEntry(type, currencyId, amount, command, item, desc));
                    }
                }
            }
            firstReward = new FirstMaxFatigueReward(rewardEnabled, entries);
        }

        return new FatigueConfig(enabled, maxFatigue, fatiguePerCatch, restDecreasePerMinute,
            restCheckIntervalTicks, tiers, specialFishChance, specialFishes, firstReward);
    }

    /**
     * 禁用时的默认配置。
     */
    public static @NotNull FatigueConfig disabled() {
        return new FatigueConfig(false, 100, 0, 0, 1200,
            List.of(new EfficiencyTier(100, 1.0, "已禁用")),
            0.0, List.of(), null);
    }
}
