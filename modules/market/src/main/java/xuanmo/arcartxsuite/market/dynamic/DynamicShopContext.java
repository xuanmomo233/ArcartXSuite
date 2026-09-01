package xuanmo.arcartxsuite.market.dynamic;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopDefinition;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;

/**
 * 策略执行上下文，提供对动态商品层和价格覆盖的读写能力。
 * <p>
 * 每个策略实例在触发时获得一个 context，通过 context 修改对应商店的动态状态。
 *
 * @since 1.5.0
 */
public interface DynamicShopContext {

    /** 当前策略对应的商店 ID。 */
    String shopId();

    // ── 动态商品层 ─────────────────────────────────────────

    /**
     * 设置动态商品列表（完全替换随机层商品）。
     * <p>
     * 动态商品叠加在静态商品之上，key 相同时动态商品覆盖静态商品。
     *
     * @param items 动态商品映射（key -> ShopItem）
     */
    void setDynamicItems(Map<String, ShopItem> items);

    /** 获取当前动态商品列表。 */
    Map<String, ShopItem> getDynamicItems();

    /** 清空动态商品列表。 */
    void clearDynamicItems();

    // ── 价格覆盖 ───────────────────────────────────────────

    /**
     * 设置商品价格乘数（覆盖静态价格）。
     * <p>
     * 最终价格 = 静态价格 × 乘数。乘数为 1.0 表示无变化。
     *
     * @param itemId 商品 key
     * @param multiplier 价格乘数（如 0.8 = 八折，1.2 = 涨价 20%）
     */
    void setPriceMultiplier(String itemId, double multiplier);

    /** 获取商品价格乘数，未设置时返回 1.0。 */
    double getPriceMultiplier(String itemId);

    /** 获取所有价格乘数映射。 */
    Map<String, Double> getAllPriceMultipliers();

    /** 清空所有价格乘数。 */
    void clearPriceMultipliers();

    // ── 成交量统计（供需模式用）────────────────────────────

    /**
     * 记录一次购买成交（供需模式策略据此调价）。
     *
     * @param itemId 商品 key
     * @param amount 成交数量
     */
    void recordPurchase(String itemId, int amount);

    /** 获取商品累计成交量。 */
    int getPurchaseCount(String itemId);

    /** 清空成交量统计。 */
    void clearPurchaseCounts();

    // ── 持久化状态 ─────────────────────────────────────────

    /**
     * 获取策略持久化状态（JSON 字符串）。
     * <p>
     * 实现在 {@link DynamicShopStrategy#onLoad(DynamicShopContext)} 时读取此值恢复内存。
     *
     * @return 上次持久化的 JSON 状态，首次启动返回 null
     */
    @Nullable String getState();

    /**
     * 设置策略持久化状态（JSON 字符串）。
     * <p>
     * 实现在触发后调用此方法保存当前状态，由 {@link DynamicShopService} 定期持久化。
     *
     * @param json JSON 状态字符串
     */
    void setState(@Nullable String json);

    // ── 商店元数据 ─────────────────────────────────────────

    /** 获取静态商店定义（只读）。 */
    @Nullable ShopDefinition getStaticShop();

    /** 获取合并后的有效商店定义（静态 + 动态商品，价格已应用乘数）。 */
    @Nullable ShopDefinition getEffectiveShop();
}
