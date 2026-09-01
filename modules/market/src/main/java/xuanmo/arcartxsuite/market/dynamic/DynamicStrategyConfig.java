package xuanmo.arcartxsuite.market.dynamic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

/**
 * 动态策略配置记录（从 yml 的 shop.dynamic.strategies.<key> 节解析）。
 *
 * @param strategyKey    策略唯一键
 * @param type           策略类型（"random-refresh" / "price-float" / "scheduled-swap"）
 * @param shopId         目标商店 ID
 * @param schedule       调度规格
 * @param probability    触发概率（0-1，仅 random-refresh 用）
 * @param maxItems       每次刷新上架商品数（仅 random-refresh 用）
 * @param removeOld      刷新时是否下架旧随机商品（仅 random-refresh 用）
 * @param pool           候选商品池（仅 random-refresh 用）
 * @param floatMode      价格浮动模式（"random-walk" / "supply-demand"，仅 price-float 用）
 * @param volatility     波动幅度（仅 price-float 用，如 0.2 = ±20%）
 * @param minMultiplier  最低价格乘数（仅 price-float 用）
 * @param maxMultiplier  最高价格乘数（仅 price-float 用）
 * @param itemSets       定时换货的商品集（仅 scheduled-swap 用，setName -> 商品列表配置）
 * @param rawSection     原始配置节（策略实现可按需读取额外字段）
 *
 * @since 1.5.0
 */
public record DynamicStrategyConfig(
    String strategyKey,
    String type,
    String shopId,
    ScheduleSpec schedule,
    double probability,
    int maxItems,
    boolean removeOld,
    List<PoolEntry> pool,
    String floatMode,
    double volatility,
    double minMultiplier,
    double maxMultiplier,
    Map<String, List<PoolEntry>> itemSets,
    ConfigurationSection rawSection
) {

    /** 候选商品池条目。 */
    public record PoolEntry(
        String itemSource, String itemId, String itemNbt,
        String displayName, double buyPrice, double sellPrice,
        String currency, int weight, String stockMode, int stockAmount,
        int limitPerPlayer, String limitReset,
        ConfigurationSection rawSection
    ) {}

    /**
     * 从 yml 配置节解析策略配置。
     */
    public static DynamicStrategyConfig load(String key, ConfigurationSection sec) {
        String type = sec.getString("type", "");
        String shopId = sec.getString("shop-id", "");
        ScheduleSpec schedule = ScheduleSpec.load(sec.getConfigurationSection("schedule"));

        double probability = sec.getDouble("probability", 1.0);
        int maxItems = sec.getInt("max-items", 5);
        boolean removeOld = sec.getBoolean("remove-old", true);

        List<PoolEntry> pool = parsePool(sec.getConfigurationSection("pool"));

        String floatMode = sec.getString("float-mode", "random-walk");
        double volatility = sec.getDouble("volatility", 0.2);
        double minMultiplier = sec.getDouble("min-multiplier", 0.5);
        double maxMultiplier = sec.getDouble("max-multiplier", 2.0);

        Map<String, List<PoolEntry>> itemSets = new LinkedHashMap<>();
        ConfigurationSection setsSec = sec.getConfigurationSection("item-sets");
        if (setsSec != null) {
            for (String setName : setsSec.getKeys(false)) {
                ConfigurationSection setSec = setsSec.getConfigurationSection(setName);
                if (setSec != null) {
                    itemSets.put(setName, parsePool(setSec));
                }
            }
        }

        return new DynamicStrategyConfig(
            key, type, shopId, schedule,
            probability, maxItems, removeOld, pool,
            floatMode, volatility, minMultiplier, maxMultiplier,
            itemSets, sec
        );
    }

    private static List<PoolEntry> parsePool(ConfigurationSection poolSec) {
        java.util.List<PoolEntry> entries = new java.util.ArrayList<>();
        if (poolSec == null) return entries;
        for (String entryKey : poolSec.getKeys(false)) {
            ConfigurationSection entrySec = poolSec.getConfigurationSection(entryKey);
            if (entrySec == null) continue;
            ConfigurationSection itemSec = entrySec.getConfigurationSection("item");
            String source = "minecraft", id = "", nbt = null;
            if (itemSec != null) {
                source = itemSec.getString("source", "minecraft");
                id = itemSec.getString("id", "");
                nbt = itemSec.getString("nbt", null);
            }
            entries.add(new PoolEntry(
                source, id, nbt,
                entrySec.getString("display-name", id),
                entrySec.getDouble("buy-price", 0),
                entrySec.getDouble("sell-price", 0),
                entrySec.getString("currency", ""),
                entrySec.getInt("weight", 1),
                entrySec.getString("stock-mode", "unlimited"),
                entrySec.getInt("stock-amount", 0),
                entrySec.getInt("limit-per-player", 0),
                entrySec.getString("limit-reset", "never"),
                entrySec
            ));
        }
        return entries;
    }
}
