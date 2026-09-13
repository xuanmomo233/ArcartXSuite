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

/**
 * CASE 开箱抽奖引擎。
 * <p>
 * 根据稀有度权重随机选择稀有度，再从该稀有度的奖品中随机选取一件。
 */
public class CaseOpeningEngine {

    /**
     * 执行一次开箱抽奖。
     *
     * @param caseConfig CASE 奖池配置
     * @return 开箱结果，包含选中物品与稀有度
     */
    @NotNull
    public CaseResult openCase(@NotNull CasePoolConfig caseConfig) {
        String selectedRarity = weightedRarity(caseConfig.raritySettings());
        List<PoolItem> items = caseConfig.items();
        List<Integer> indices = indicesByRarity(items, selectedRarity);
        if (indices.isEmpty()) {
            return new CaseResult(null, selectedRarity, -1);
        }
        int selectedSlot = weightedRandomIndex(items, indices);
        PoolItem item = items.get(selectedSlot);
        return new CaseResult(item, selectedRarity, selectedSlot + 1);
    }

    /**
     * 批量开箱抽奖。
     *
     * @param caseConfig CASE 奖池配置
     * @param count      开箱次数
     * @return 开箱结果列表
     */
    @NotNull
    public List<CaseResult> openCaseBatch(@NotNull CasePoolConfig caseConfig, int count) {
        List<CaseResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(openCase(caseConfig));
        }
        return results;
    }

    private String weightedRarity(Map<String, RaritySetting> raritySettings) {
        if (raritySettings == null || raritySettings.isEmpty()) return "COMMON";
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

    private List<Integer> indicesByRarity(List<PoolItem> items, String rarity) {
        List<Integer> result = new ArrayList<>();
        if (items == null) return result;
        for (int i = 0; i < items.size(); i++) {
            if (rarity.equalsIgnoreCase(items.get(i).rarity())) {
                result.add(i);
            }
        }
        return result;
    }

    private int weightedRandomIndex(List<PoolItem> items, List<Integer> indices) {
        if (indices.isEmpty()) return -1;
        if (indices.size() == 1) return indices.get(0);
        int totalWeight = 0;
        for (int idx : indices) {
            totalWeight += items.get(idx).weight();
        }
        if (totalWeight <= 0) return indices.get(ThreadLocalRandom.current().nextInt(indices.size()));
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (int idx : indices) {
            current += items.get(idx).weight();
            if (roll < current) return idx;
        }
        return indices.get(indices.size() - 1);
    }
}
