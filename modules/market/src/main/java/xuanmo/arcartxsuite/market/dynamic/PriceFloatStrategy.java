package xuanmo.arcartxsuite.market.dynamic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopDefinition;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;

/**
 * 价格浮动策略。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>random-walk</b>：每次触发时在基准价上下随机波动（±volatility），
 *       乘数限制在 [minMultiplier, maxMultiplier] 范围内。</li>
 *   <li><b>supply-demand</b>：根据成交量动态调价，
 *       买的人多则涨价、少则降价。成交量越高，乘数越接近 maxMultiplier。</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class PriceFloatStrategy implements DynamicShopStrategy {

    private DynamicStrategyConfig config;
    private double[] lastMultipliers; // 上次各商品的乘数（random-walk 用）

    @Override
    public String type() { return "price-float"; }

    @Override
    public void onLoad(DynamicShopContext context) {
        String restored = ((DynamicShopContextImpl) context).getRestoredState();
        if (restored != null && !restored.isBlank()) {
            try {
                Map<String, Object> parsed = xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(restored);
                Object pmObj = parsed.get("multipliers");
                if (pmObj instanceof Map<?, ?> pmMap) {
                    for (Map.Entry<?, ?> entry : pmMap.entrySet()) {
                        context.setPriceMultiplier(String.valueOf(entry.getKey()),
                            ((Number) entry.getValue()).doubleValue());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onTrigger(DynamicShopContext context) {
        if (config == null) return;

        ShopDefinition shop = context.getStaticShop();
        if (shop == null) return;

        String mode = config.floatMode();
        if ("supply-demand".equalsIgnoreCase(mode)) {
            applySupplyDemand(context, shop);
        } else {
            applyRandomWalk(context, shop);
        }

        // 保存状态
        context.setState(serializeState(context.getAllPriceMultipliers()));

        // 清空成交量统计（每次调价后重置）
        context.clearPurchaseCounts();
    }

    /** 由 DynamicShopService 注入配置。 */
    void setConfig(DynamicStrategyConfig config) {
        this.config = config;
    }

    // ── 随机游走模式 ──────────────────────────────────────

    private void applyRandomWalk(DynamicShopContext context, ShopDefinition shop) {
        double volatility = config.volatility();
        double min = config.minMultiplier();
        double max = config.maxMultiplier();

        for (String itemId : shop.items().keySet()) {
            double current = context.getPriceMultiplier(itemId);
            // 随机游走：current ± volatility
            double delta = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * volatility;
            double next = current + delta;
            // 钳制到 [min, max]
            next = Math.max(min, Math.min(max, next));
            // 四舍五入到 2 位小数
            next = Math.round(next * 100.0) / 100.0;
            context.setPriceMultiplier(itemId, next);
        }
    }

    // ── 供需驱动模式 ──────────────────────────────────────

    private void applySupplyDemand(DynamicShopContext context, ShopDefinition shop) {
        double min = config.minMultiplier();
        double max = config.maxMultiplier();
        double volatility = config.volatility();

        // 计算总成交量
        int totalPurchases = 0;
        for (String itemId : shop.items().keySet()) {
            totalPurchases += context.getPurchaseCount(itemId);
        }

        if (totalPurchases == 0) {
            // 无成交，所有乘数向 1.0 回归
            for (String itemId : shop.items().keySet()) {
                double current = context.getPriceMultiplier(itemId);
                double next = current + (1.0 - current) * volatility * 0.5;
                next = Math.max(min, Math.min(max, next));
                next = Math.round(next * 100.0) / 100.0;
                context.setPriceMultiplier(itemId, next);
            }
            return;
        }

        // 有成交：按成交量占比调价
        for (String itemId : shop.items().keySet()) {
            int count = context.getPurchaseCount(itemId);
            double ratio = (double) count / totalPurchases; // 0-1
            // 成交占比越高，乘数越接近 max；越低越接近 min
            double target = min + (max - min) * ratio;
            // 平滑过渡（避免价格跳变）
            double current = context.getPriceMultiplier(itemId);
            double next = current + (target - current) * volatility;
            next = Math.max(min, Math.min(max, next));
            next = Math.round(next * 100.0) / 100.0;
            context.setPriceMultiplier(itemId, next);
        }
    }

    private String serializeState(Map<String, Double> multipliers) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"multipliers\":{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : multipliers.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        sb.append("}}");
        return sb.toString();
    }
}
