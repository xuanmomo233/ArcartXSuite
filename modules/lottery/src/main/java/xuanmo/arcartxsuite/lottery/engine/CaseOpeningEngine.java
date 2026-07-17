package xuanmo.arcartxsuite.lottery.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig.RaritySetting;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

public class CaseOpeningEngine {

    @NotNull
    public CaseResult openCase(@NotNull CasePoolConfig caseConfig) {
        String selectedRarity = weightedRarity(caseConfig.raritySettings());
        List<PoolItem> items = itemsByRarity(caseConfig.items(), selectedRarity);
        PoolItem item = randomFrom(items);

        if (item == null) {
            return new CaseResult(null, selectedRarity, false, "FACTORY_NEW", 0.0);
        }
        boolean isStattrak = item.stattrakEnabled() && ThreadLocalRandom.current().nextDouble() < caseConfig.stattrakChance();

        String wearTier = "FACTORY_NEW";
        double wearValue = 0.0;
        if (item.wearDistribution() != null && !item.wearDistribution().isEmpty()) {
            PoolItem.WearRange selectedWear = weightedWear(item.wearDistribution());
            if (selectedWear != null) {
                wearTier = findWearTierKey(item.wearDistribution(), selectedWear);
                wearValue = selectedWear.min() + ThreadLocalRandom.current().nextDouble() * (selectedWear.max() - selectedWear.min());
            }
        }

        return new CaseResult(item, selectedRarity, isStattrak, wearTier, wearValue);
    }

    private String weightedRarity(Map<String, RaritySetting> raritySettings) {
        if (raritySettings == null || raritySettings.isEmpty()) return "CONSUMER";
        int totalWeight = raritySettings.values().stream().mapToInt(RaritySetting::baseWeight).sum();
        if (totalWeight <= 0) return raritySettings.keySet().iterator().next();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (Map.Entry<String, RaritySetting> entry : raritySettings.entrySet()) {
            current += entry.getValue().baseWeight();
            if (roll < current) return entry.getKey();
        }
        return raritySettings.keySet().iterator().next();
    }

    private List<PoolItem> itemsByRarity(List<PoolItem> items, String rarity) {
        List<PoolItem> result = new ArrayList<>();
        if (items == null) return result;
        for (PoolItem item : items) {
            if (rarity.equalsIgnoreCase(item.rarity())) {
                result.add(item);
            }
        }
        return result;
    }

    private PoolItem randomFrom(List<PoolItem> items) {
        if (items == null || items.isEmpty()) return null;
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    }

    private PoolItem.WearRange weightedWear(Map<String, PoolItem.WearRange> distribution) {
        if (distribution == null || distribution.isEmpty()) return null;
        double totalWeight = distribution.values().stream().mapToDouble(PoolItem.WearRange::weight).sum();
        if (totalWeight <= 0) return distribution.values().iterator().next();
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double current = 0;
        for (PoolItem.WearRange range : distribution.values()) {
            current += range.weight();
            if (roll < current) return range;
        }
        return distribution.values().iterator().next();
    }

    private String findWearTierKey(Map<String, PoolItem.WearRange> distribution, PoolItem.WearRange target) {
        for (Map.Entry<String, PoolItem.WearRange> entry : distribution.entrySet()) {
            if (entry.getValue() == target) return entry.getKey();
        }
        return "FACTORY_NEW";
    }
}
