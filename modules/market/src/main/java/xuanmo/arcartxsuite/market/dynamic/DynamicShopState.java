package xuanmo.arcartxsuite.market.dynamic;

/**
 * 动态策略持久化状态记录。
 * <p>
 * 每个策略实例对应一条记录，存储策略特定的 JSON 状态。
 *
 * @param strategyKey    策略唯一键（配置中的 key，如 "mystery_merchant"）
 * @param shopId         目标商店 ID
 * @param strategyType   策略类型（"random-refresh" / "price-float" / "scheduled-swap"）
 * @param stateJson      策略特定的 JSON 状态
 * @param lastTriggerTime 上次触发时间戳（毫秒）
 * @param nextTriggerTime 下次预计触发时间戳（毫秒，0 表示未计算）
 *
 * @since 1.5.0
 */
public record DynamicShopState(
    String strategyKey,
    String shopId,
    String strategyType,
    String stateJson,
    long lastTriggerTime,
    long nextTriggerTime
) {
    public DynamicShopState {
        if (strategyKey == null || strategyKey.isBlank()) {
            throw new IllegalArgumentException("strategyKey 不能为空");
        }
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalArgumentException("shopId 不能为空");
        }
        if (strategyType == null || strategyType.isBlank()) {
            throw new IllegalArgumentException("strategyType 不能为空");
        }
        stateJson = stateJson == null ? "" : stateJson;
    }
}
