package xuanmo.arcartxsuite.market.recycle;

/**
 * 回收表条目。
 */
public record RecycleEntry(
    String key,
    String source,
    String itemId,
    double price,
    String currency
) {}
