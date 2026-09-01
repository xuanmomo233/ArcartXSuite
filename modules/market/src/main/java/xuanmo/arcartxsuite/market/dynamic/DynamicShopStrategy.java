package xuanmo.arcartxsuite.market.dynamic;

/**
 * 动态商品策略接口。
 * <p>
 * 每种策略（随机刷新 / 价格浮动 / 定时换货）实现此接口，
 * 由 {@link DynamicShopService} 统一调度和持久化。
 *
 * @since 1.5.0
 */
public interface DynamicShopStrategy {

    /** 策略类型标识（如 "random-refresh" / "price-float" / "scheduled-swap"）。 */
    String type();

    /**
     * 触发策略执行。
     * <p>
     * 由 {@link DynamicShopService} 在调度时机到达时调用。
     * 实现应通过 {@link DynamicShopContext} 读写动态状态、修改商品列表或价格。
     *
     * @param context 策略执行上下文
     */
    void onTrigger(DynamicShopContext context);

    /**
     * 从持久化状态恢复。
     * <p>
     * 服务启动时调用，实现应从 {@link DynamicShopContext#getState()} 读取 JSON 状态并恢复内存。
     *
     * @param context 策略执行上下文
     */
    void onLoad(DynamicShopContext context);
}
