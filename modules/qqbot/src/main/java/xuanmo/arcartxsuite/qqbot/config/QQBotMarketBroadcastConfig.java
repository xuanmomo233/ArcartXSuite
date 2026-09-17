package xuanmo.arcartxsuite.qqbot.config;

/**
 * 大额交易 QQ 群播报配置。
 * <p>
 * 订阅 market 模块的 EventBus 事件（topic: axs.market.auction_purchased），
 * 当成交价 &gt;= {@link #threshold()} 时推送到 QQ 群。
 *
 * @param enabled   是否启用大额交易播报
 * @param threshold 大额交易阈值，成交价 &gt;= 此值才播报
 */
public record QQBotMarketBroadcastConfig(
    boolean enabled,
    double threshold
) {
    public static QQBotMarketBroadcastConfig defaults() {
        return new QQBotMarketBroadcastConfig(true, 1000.0);
    }
}
