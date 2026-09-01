package xuanmo.arcartxsuite.market.dynamic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.shop.ShopService;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopDefinition;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;
import xuanmo.arcartxsuite.market.shop.ShopService.ItemDefinition;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItemType;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * {@link DynamicShopContext} 的默认实现。
 * <p>
 * 每个策略实例持有一个 context，管理该策略对应的商店的动态商品层、价格乘数、成交量统计。
 * 状态以 JSON 字符串形式持久化。
 *
 * @since 1.5.0
 */
final class DynamicShopContextImpl implements DynamicShopContext {

    private final String strategyKey;
    private final String shopId;
    private final ShopService shopService;
    private final MarketRepository repository;

    // 动态商品层：itemId -> ShopItem
    private final Map<String, ShopItem> dynamicItems = new ConcurrentHashMap<>();
    // 价格乘数：itemId -> multiplier
    private final Map<String, Double> priceMultipliers = new ConcurrentHashMap<>();
    // 成交量统计：itemId -> count
    private final Map<String, Integer> purchaseCounts = new ConcurrentHashMap<>();

    // 持久化状态（JSON）
    private volatile String stateJson = "";
    // 从数据库恢复的状态（onLoad 时读取）
    private volatile String restoredState = null;
    // 上次触发时间
    private volatile long lastTriggerTime = 0;
    // 策略类型（由策略在 onLoad 时设置）
    private volatile String strategyType = "";

    DynamicShopContextImpl(String strategyKey, String shopId, ShopService shopService, MarketRepository repository) {
        this.strategyKey = strategyKey;
        this.shopId = shopId;
        this.shopService = shopService;
        this.repository = repository;
    }

    @Override
    public String shopId() { return shopId; }

    // ── 动态商品层 ─────────────────────────────────────────

    @Override
    public void setDynamicItems(Map<String, ShopItem> items) {
        dynamicItems.clear();
        dynamicItems.putAll(items);
    }

    @Override
    public Map<String, ShopItem> getDynamicItems() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dynamicItems));
    }

    @Override
    public void clearDynamicItems() {
        dynamicItems.clear();
    }

    // ── 价格覆盖 ───────────────────────────────────────────

    @Override
    public void setPriceMultiplier(String itemId, double multiplier) {
        if (multiplier == 1.0) {
            priceMultipliers.remove(itemId);
        } else {
            priceMultipliers.put(itemId, multiplier);
        }
    }

    @Override
    public double getPriceMultiplier(String itemId) {
        return priceMultipliers.getOrDefault(itemId, 1.0);
    }

    @Override
    public Map<String, Double> getAllPriceMultipliers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(priceMultipliers));
    }

    @Override
    public void clearPriceMultipliers() {
        priceMultipliers.clear();
    }

    // ── 成交量统计 ─────────────────────────────────────────

    @Override
    public void recordPurchase(String itemId, int amount) {
        purchaseCounts.merge(itemId, amount, Integer::sum);
    }

    @Override
    public int getPurchaseCount(String itemId) {
        return purchaseCounts.getOrDefault(itemId, 0);
    }

    @Override
    public void clearPurchaseCounts() {
        purchaseCounts.clear();
    }

    // ── 持久化状态 ─────────────────────────────────────────

    @Override
    public @Nullable String getState() { return stateJson; }

    @Override
    public void setState(@Nullable String json) { this.stateJson = json == null ? "" : json; }

    /** 设置从数据库恢复的状态（供策略 onLoad 读取）。 */
    void setRestoredState(@Nullable String json) { this.restoredState = json; }

    /** 获取从数据库恢复的状态。 */
    @Nullable String getRestoredState() { return restoredState; }

    /** 设置策略类型。 */
    void setStrategyType(String type) { this.strategyType = type; }

    /** 获取策略类型。 */
    String getStrategyType() { return strategyType; }

    /** 设置上次触发时间。 */
    void setLastTriggerTime(long time) { this.lastTriggerTime = time; }

    /** 获取上次触发时间。 */
    long getLastTriggerTime() { return lastTriggerTime; }

    // ── 商店元数据 ─────────────────────────────────────────

    @Override
    public @Nullable ShopDefinition getStaticShop() {
        return shopService.getShop(shopId);
    }

    @Override
    public @Nullable ShopDefinition getEffectiveShop() {
        ShopDefinition staticShop = shopService.getShop(shopId);
        if (staticShop == null) return null;

        // 合并静态商品 + 动态商品，应用价格乘数
        Map<String, ShopItem> effectiveItems = new LinkedHashMap<>(staticShop.items());
        effectiveItems.putAll(dynamicItems);

        // 应用价格乘数
        Map<String, ShopItem> pricedItems = new LinkedHashMap<>();
        for (Map.Entry<String, ShopItem> entry : effectiveItems.entrySet()) {
            String key = entry.getKey();
            ShopItem item = entry.getValue();
            double multiplier = priceMultipliers.getOrDefault(key, 1.0);
            if (multiplier != 1.0) {
                item = new ShopItem(
                    item.key(), item.type(), item.icon(), item.rewardCommands(),
                    item.item(), item.displayName(),
                    item.buyPrice() * multiplier,
                    item.sellPrice() * multiplier,
                    item.currency(), item.stockMode(), item.stockAmount(),
                    item.limitPerPlayer(), item.limitReset(), item.discount()
                );
            }
            pricedItems.put(key, item);
        }

        return new ShopDefinition(
            staticShop.id(), staticShop.displayName(), staticShop.icon(),
            staticShop.permission(), staticShop.tags(), pricedItems
        );
    }

    // ── 序列化（用于跨服同步）──────────────────────────────

    /** 序列化为跨服同步数据（简化 JSON）。 */
    String serializeForSync() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        // 动态商品
        sb.append("\"dynamicItems\":[");
        boolean first = true;
        for (Map.Entry<String, ShopItem> entry : dynamicItems.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"key\":\"").append(escape(entry.getKey())).append("\"");
            sb.append(",\"buyPrice\":").append(entry.getValue().buyPrice());
            sb.append(",\"sellPrice\":").append(entry.getValue().sellPrice());
            sb.append(",\"displayName\":\"").append(escape(entry.getValue().displayName())).append("\"");
            sb.append(",\"currency\":\"").append(escape(entry.getValue().currency())).append("\"");
            sb.append(",\"itemSource\":\"").append(escape(entry.getValue().item().source())).append("\"");
            sb.append(",\"itemId\":\"").append(escape(entry.getValue().item().id())).append("\"");
            sb.append(",\"stockMode\":\"").append(escape(entry.getValue().stockMode())).append("\"");
            sb.append(",\"stockAmount\":").append(entry.getValue().stockAmount());
            sb.append(",\"limitPerPlayer\":").append(entry.getValue().limitPerPlayer());
            sb.append(",\"limitReset\":\"").append(escape(entry.getValue().limitReset())).append("\"");
            sb.append("}");
        }
        sb.append("]");
        // 价格乘数
        sb.append(",\"priceMultipliers\":{");
        first = true;
        for (Map.Entry<String, Double> entry : priceMultipliers.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");
        // 成交量
        sb.append(",\"purchaseCounts\":{");
        first = true;
        for (Map.Entry<String, Integer> entry : purchaseCounts.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");
        // 状态 JSON
        sb.append(",\"stateJson\":\"").append(escape(stateJson)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    /** 从跨服同步数据反序列化。 */
    @SuppressWarnings("unchecked")
    void deserializeFromSync(String json) {
        try {
            Map<String, Object> parsed = xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(json);
            // 动态商品
            dynamicItems.clear();
            Object itemsObj = parsed.get("dynamicItems");
            if (itemsObj instanceof java.util.List<?> itemsList) {
                for (Object itemObj : itemsList) {
                    if (itemObj instanceof Map<?, ?> rawMap) {
                        Map<String, Object> itemMap = (Map<String, Object>) rawMap;
                        String key = String.valueOf(itemMap.get("key"));
                        String itemSource = String.valueOf(itemMap.getOrDefault("itemSource", "minecraft"));
                        String itemId = String.valueOf(itemMap.getOrDefault("itemId", ""));
                        String display = String.valueOf(itemMap.getOrDefault("displayName", itemId));
                        double buyPrice = ((Number) itemMap.getOrDefault("buyPrice", 0)).doubleValue();
                        double sellPrice = ((Number) itemMap.getOrDefault("sellPrice", 0)).doubleValue();
                        String currency = String.valueOf(itemMap.getOrDefault("currency", ""));
                        String stockMode = String.valueOf(itemMap.getOrDefault("stockMode", "unlimited"));
                        int stockAmount = ((Number) itemMap.getOrDefault("stockAmount", 0)).intValue();
                        int limitPerPlayer = ((Number) itemMap.getOrDefault("limitPerPlayer", 0)).intValue();
                        String limitReset = String.valueOf(itemMap.getOrDefault("limitReset", "never"));
                        dynamicItems.put(key, new ShopItem(
                            key, ShopItemType.ITEM, null, java.util.List.of(),
                            new ItemDefinition(itemSource, itemId, null),
                            display, buyPrice, sellPrice, currency,
                            stockMode, stockAmount, limitPerPlayer, limitReset, java.util.Map.of()
                        ));
                    }
                }
            }
            // 价格乘数
            priceMultipliers.clear();
            Object pmObj = parsed.get("priceMultipliers");
            if (pmObj instanceof Map<?, ?> pmMap) {
                for (Map.Entry<?, ?> entry : pmMap.entrySet()) {
                    priceMultipliers.put(String.valueOf(entry.getKey()), ((Number) entry.getValue()).doubleValue());
                }
            }
            // 成交量
            purchaseCounts.clear();
            Object pcObj = parsed.get("purchaseCounts");
            if (pcObj instanceof Map<?, ?> pcMap) {
                for (Map.Entry<?, ?> entry : pcMap.entrySet()) {
                    purchaseCounts.put(String.valueOf(entry.getKey()), ((Number) entry.getValue()).intValue());
                }
            }
            // 状态 JSON
            Object stateObj = parsed.get("stateJson");
            if (stateObj != null) {
                stateJson = String.valueOf(stateObj);
            }
        } catch (Exception e) {
            // 静默失败，跨服同步不应崩溃本服
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
