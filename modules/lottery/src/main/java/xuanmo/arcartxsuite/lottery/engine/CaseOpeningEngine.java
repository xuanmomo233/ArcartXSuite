package xuanmo.arcartxsuite.lottery.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final Map<CasePoolConfig, PreparedCase> prepared;

    public CaseOpeningEngine() {
        this(List.of());
    }

    public CaseOpeningEngine(Iterable<CasePoolConfig> configurations) {
        var indexed = new IdentityHashMap<CasePoolConfig, PreparedCase>();
        for (var config : configurations) indexed.put(config, new PreparedCase(config));
        this.prepared = Collections.unmodifiableMap(indexed);
    }

    /**
     * 执行一次开箱抽奖。
     *
     * @param caseConfig CASE 奖池配置
     * @return 开箱结果，包含选中物品与稀有度
     */
    @NotNull
    public CaseResult openCase(@NotNull CasePoolConfig caseConfig) {
        return preparedCase(caseConfig).open();
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
        var table = preparedCase(caseConfig);
        List<CaseResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(table.open());
        }
        return results;
    }

    private PreparedCase preparedCase(CasePoolConfig config) {
        var table = prepared.get(config);
        return table != null ? table : new PreparedCase(config);
    }

    private record Slot(PoolItem item, int position) {}

    private static final class PreparedCase {
        private final WeightedPool<String> rarities;
        private final Map<String, WeightedPool<Slot>> pools;

        PreparedCase(CasePoolConfig config) {
            Map<String, RaritySetting> rules = config.raritySettings();
            rarities = rules == null ? new WeightedPool<>(List.of(), ignored -> 0)
                : new WeightedPool<>(new ArrayList<>(rules.keySet()), name -> rules.get(name).baseWeight());
            var slots = new HashMap<String, List<Slot>>();
            List<PoolItem> items = config.items();
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    PoolItem item = items.get(i);
                    if (item.rarity() != null) {
                        slots.computeIfAbsent(item.rarity().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>())
                            .add(new Slot(item, i + 1));
                    }
                }
            }
            var groups = new HashMap<String, WeightedPool<Slot>>();
            slots.forEach((name, list) -> groups.put(name, new WeightedPool<>(list, slot -> slot.item().weight())));
            pools = Map.copyOf(groups);
        }

        CaseResult open() {
            String rarity = rarities.roll(false);
            if (rarity == null) rarity = "COMMON";
            var pool = pools.get(rarity.toUpperCase(Locale.ROOT));
            Slot selected = pool == null ? null : pool.roll(true);
            return selected == null ? new CaseResult(null, rarity, -1)
                : new CaseResult(selected.item(), rarity, selected.position());
        }
    }
}
