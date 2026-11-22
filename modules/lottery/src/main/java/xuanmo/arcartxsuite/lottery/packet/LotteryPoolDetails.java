package xuanmo.arcartxsuite.lottery.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/** The immutable, player-independent part of an odds overlay, prepared once per configuration. */
record LotteryPoolDetails(Map<String, Object> fields, List<PoolItem> icons) {
    static LotteryPoolDetails compile(PoolDefinition pool, Function<PoolItem, String> names) {
        var fields = new LinkedHashMap<String, Object>();
        var rows = new LinkedHashMap<String, Object>();
        var icons = new ArrayList<PoolItem>();
        fields.put("detailPoolName", pool.displayName());
        if (pool.type() == PoolType.CASE && pool.caseConfig() != null) {
            addCaseRows(pool.caseConfig(), names, rows, icons);
        } else if (pool.type() == PoolType.GACHA && pool.gacha() != null) {
            addGachaRows(pool.gacha(), names, fields, rows, icons);
        }
        fields.put("detailCount", icons.size());
        fields.put("detailRows", Collections.unmodifiableMap(rows));
        return new LotteryPoolDetails(Collections.unmodifiableMap(fields), List.copyOf(icons));
    }

    private static void addCaseRows(CasePoolConfig config, Function<PoolItem, String> names,
                                    Map<String, Object> rows, List<PoolItem> icons) {
        var rarityWeights = new HashMap<String, Long>();
        for (var item : config.items()) {
            if (item.rarity() != null) {
                rarityWeights.merge(item.rarity().toUpperCase(Locale.ROOT), (long) item.weight(), Long::sum);
            }
        }
        long total = config.raritySettings().values().stream().mapToLong(CasePoolConfig.RaritySetting::baseWeight).sum();
        for (var item : config.items()) {
            String rarity = item.rarity() == null ? "" : item.rarity();
            String key = rarity.toUpperCase(Locale.ROOT);
            var setting = config.raritySettings().get(key);
            long groupWeight = rarityWeights.getOrDefault(key, 0L);
            Double odds = setting != null && total > 0 && groupWeight > 0
                ? (double) setting.baseWeight() / total * item.weight() / groupWeight : null;
            addRow(item, rarity, odds, names, rows, icons);
        }
    }

    private static void addGachaRows(GachaPoolConfig config, Function<PoolItem, String> names,
                                     Map<String, Object> fields, Map<String, Object> rows, List<PoolItem> icons) {
        double five = chance(config.base5starRate());
        double conditionalFour = chance(config.base4starRate());
        double four = (1.0 - five) * conditionalFour;
        double three = (1.0 - five) * (1.0 - conditionalFour);
        double up = chance(config.upRate());
        fields.put("detailPity5", config.pity5star());
        fields.put("detailPity4", config.pity4star());
        fields.put("detailBase5Rate", five);
        fields.put("detailBase4Rate", four);
        fields.put("detailUpRate", up);

        addGroup(config.up5starItems(), "5", five * up, names, rows, icons);
        addGroup(config.standard5starItems(), "5", five * (1.0 - up), names, rows, icons);
        double upFour = config.standard4starItems().isEmpty() ? four : four * up;
        double standardFour = config.up4starItems().isEmpty() ? four : four * (1.0 - up);
        addGroup(config.up4starItems(), "4", upFour, names, rows, icons);
        addGroup(config.standard4starItems(), "4", standardFour, names, rows, icons);
        addGroup(config.star3Items(), "3", three, names, rows, icons);
    }

    private static void addGroup(List<PoolItem> items, String rarity, double odds, Function<PoolItem, String> names,
                                 Map<String, Object> rows, List<PoolItem> icons) {
        long weight = items.stream().mapToLong(PoolItem::weight).sum();
        for (var item : items) {
            addRow(item, rarity, weight > 0 ? odds * item.weight() / weight : null, names, rows, icons);
        }
    }

    private static void addRow(PoolItem item, String rarity, Double odds, Function<PoolItem, String> names,
                               Map<String, Object> rows, List<PoolItem> icons) {
        rows.put(Integer.toString(icons.size()), Map.of(
            "name", names.apply(item), "amount", item.amount(), "rarity", rarity, "weight", item.weight(),
            "probability", odds == null ? "" : String.format(Locale.ROOT, "%.2f%%", odds * 100)));
        icons.add(item);
    }

    private static double chance(double value) {
        return value > 0.0 ? Math.min(value, 1.0) : 0.0;
    }
}
