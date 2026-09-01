package xuanmo.arcartxsuite.market.dynamic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import xuanmo.arcartxsuite.market.dynamic.DynamicStrategyConfig.PoolEntry;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItemType;
import xuanmo.arcartxsuite.market.shop.ShopService.ItemDefinition;

/**
 * 随机刷新策略（神秘商人）。
 * <p>
 * 触发时从候选商品池中按权重随机抽选 N 个商品上架，
 * 同时下架旧随机商品（removeOld=true 时）。
 *
 * @since 1.5.0
 */
public class RandomRefreshStrategy implements DynamicShopStrategy {

    private DynamicStrategyConfig config;

    @Override
    public String type() { return "random-refresh"; }

    @Override
    public void onLoad(DynamicShopContext context) {
        // 从持久化状态恢复动态商品
        String restored = ((DynamicShopContextImpl) context).getRestoredState();
        if (restored != null && !restored.isBlank()) {
            try {
                Map<String, Object> parsed = xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(restored);
                // 恢复动态商品
                Object itemsObj = parsed.get("items");
                if (itemsObj instanceof List<?> itemsList) {
                    Map<String, ShopItem> restoredItems = new LinkedHashMap<>();
                    for (Object itemObj : itemsList) {
                        if (itemObj instanceof Map<?, ?> itemMap) {
                            String key = String.valueOf(itemMap.get("key"));
                            restoredItems.put(key, deserializeShopItem(itemMap));
                        }
                    }
                    context.setDynamicItems(restoredItems);
                }
            } catch (Exception ignored) {
                // 恢复失败时清空，下次触发重新生成
            }
        }
        // 配置由 DynamicShopService 通过反射注入（简化处理：在 onTrigger 时从 context 读取）
        // 实际配置通过 DynamicShopService 传入
    }

    @Override
    public void onTrigger(DynamicShopContext context) {
        if (config == null) return;

        // 下架旧随机商品
        if (config.removeOld()) {
            context.clearDynamicItems();
        }

        // 从候选池按权重抽选
        List<PoolEntry> pool = config.pool();
        if (pool.isEmpty()) return;

        int maxItems = Math.min(config.maxItems(), pool.size());
        Map<String, ShopItem> newItems = new LinkedHashMap<>();

        // 加权随机抽选（不重复）
        List<PoolEntry> candidates = new ArrayList<>(pool);
        for (int i = 0; i < maxItems && !candidates.isEmpty(); i++) {
            int totalWeight = candidates.stream().mapToInt(PoolEntry::weight).sum();
            int roll = ThreadLocalRandom.current().nextInt(totalWeight);
            int cumulative = 0;
            int selectedIdx = 0;
            for (int j = 0; j < candidates.size(); j++) {
                cumulative += candidates.get(j).weight();
                if (roll < cumulative) {
                    selectedIdx = j;
                    break;
                }
            }
            PoolEntry selected = candidates.remove(selectedIdx);
            String itemKey = "random_" + i;
            newItems.put(itemKey, poolEntryToShopItem(itemKey, selected));
        }

        // 合并到现有动态商品（不覆盖已有的）
        Map<String, ShopItem> existing = context.getDynamicItems();
        Map<String, ShopItem> merged = new LinkedHashMap<>(existing);
        merged.putAll(newItems);
        context.setDynamicItems(merged);

        // 保存状态
        context.setState(serializeState(context.getDynamicItems()));
    }

    /** 由 DynamicShopService 注入配置。 */
    void setConfig(DynamicStrategyConfig config) {
        this.config = config;
    }

    private ShopItem poolEntryToShopItem(String key, PoolEntry entry) {
        return new ShopItem(
            key, ShopItemType.ITEM, null, java.util.List.of(),
            new ItemDefinition(entry.itemSource(), entry.itemId(), entry.itemNbt()),
            entry.displayName(), entry.buyPrice(), entry.sellPrice(), entry.currency(),
            entry.stockMode(), entry.stockAmount(),
            entry.limitPerPlayer(), entry.limitReset(), java.util.Map.of()
        );
    }

    private String serializeState(Map<String, ShopItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");
        boolean first = true;
        for (Map.Entry<String, ShopItem> entry : items.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            ShopItem item = entry.getValue();
            sb.append("{\"key\":\"").append(entry.getKey()).append("\"");
            sb.append(",\"itemSource\":\"").append(item.item().source()).append("\"");
            sb.append(",\"itemId\":\"").append(item.item().id()).append("\"");
            sb.append(",\"displayName\":\"").append(item.displayName()).append("\"");
            sb.append(",\"buyPrice\":").append(item.buyPrice());
            sb.append(",\"sellPrice\":").append(item.sellPrice());
            sb.append(",\"currency\":\"").append(item.currency()).append("\"");
            sb.append(",\"stockMode\":\"").append(item.stockMode()).append("\"");
            sb.append(",\"stockAmount\":").append(item.stockAmount());
            sb.append(",\"limitPerPlayer\":").append(item.limitPerPlayer());
            sb.append(",\"limitReset\":\"").append(item.limitReset()).append("\"");
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private ShopItem deserializeShopItem(Map<?, ?> rawMap) {
        Map<String, Object> itemMap = (Map<String, Object>) rawMap;
        String key = String.valueOf(itemMap.get("key"));
        String source = String.valueOf(itemMap.getOrDefault("itemSource", "minecraft"));
        String id = String.valueOf(itemMap.getOrDefault("itemId", ""));
        String display = String.valueOf(itemMap.getOrDefault("displayName", id));
        double buyPrice = ((Number) itemMap.getOrDefault("buyPrice", 0)).doubleValue();
        double sellPrice = ((Number) itemMap.getOrDefault("sellPrice", 0)).doubleValue();
        String currency = String.valueOf(itemMap.getOrDefault("currency", ""));
        String stockMode = String.valueOf(itemMap.getOrDefault("stockMode", "unlimited"));
        int stockAmount = ((Number) itemMap.getOrDefault("stockAmount", 0)).intValue();
        int limitPerPlayer = ((Number) itemMap.getOrDefault("limitPerPlayer", 0)).intValue();
        String limitReset = String.valueOf(itemMap.getOrDefault("limitReset", "never"));
        return new ShopItem(
            key, ShopItemType.ITEM, null, java.util.List.of(),
            new ItemDefinition(source, id, null),
            display, buyPrice, sellPrice, currency,
            stockMode, stockAmount, limitPerPlayer, limitReset, java.util.Map.of()
        );
    }
}
