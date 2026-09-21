package xuanmo.arcartxsuite.market.dynamic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import xuanmo.arcartxsuite.market.dynamic.DynamicStrategyConfig.PoolEntry;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItemType;
import xuanmo.arcartxsuite.market.shop.ShopService.ItemDefinition;

/**
 * 定时换货策略。
 * <p>
 * 按配置的多个商品集（item-sets）轮换，每次触发切换到下一个集。
 * 例如：周一上架 set_a，下周一上架 set_b，再下周一回到 set_a。
 *
 * @since 1.5.0
 */
public class ScheduledSwapStrategy implements DynamicShopStrategy {

    private DynamicStrategyConfig config;
    private final AtomicInteger currentSetIndex = new AtomicInteger(0);

    @Override
    public String type() { return "scheduled-swap"; }

    @Override
    public void onLoad(DynamicShopContext context) {
        String restored = ((DynamicShopContextImpl) context).getRestoredState();
        if (restored != null && !restored.isBlank()) {
            try {
                Map<String, Object> parsed = xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(restored);
                Object idxObj = parsed.get("currentSetIndex");
                if (idxObj instanceof Number num) {
                    currentSetIndex.set(num.intValue());
                }
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
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onTrigger(DynamicShopContext context) {
        if (config == null || config.itemSets().isEmpty()) return;

        // 轮换到下一个商品集（floorMod：计数器溢出为负时仍得到合法非负索引）
        int idx = Math.floorMod(currentSetIndex.getAndIncrement(), config.itemSets().size());
        String setName = new java.util.ArrayList<>(config.itemSets().keySet()).get(idx);
        List<PoolEntry> entries = config.itemSets().get(setName);

        // 清空旧动态商品，上架新商品集
        context.clearDynamicItems();
        Map<String, ShopItem> newItems = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            PoolEntry entry = entries.get(i);
            String itemKey = "swap_" + setName + "_" + i;
            newItems.put(itemKey, poolEntryToShopItem(itemKey, entry));
        }
        context.setDynamicItems(newItems);

        // 保存状态
        context.setState(serializeState(idx + 1, newItems));
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
            entry.limitPerPlayer(), entry.limitReset(), java.util.Map.of(),
            java.util.List.of()
        );
    }

    private String serializeState(int nextIndex, Map<String, ShopItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"currentSetIndex\":").append(nextIndex);
        sb.append(",\"items\":[");
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
            stockMode, stockAmount, limitPerPlayer, limitReset, java.util.Map.of(),
            java.util.List.of()
        );
    }
}
