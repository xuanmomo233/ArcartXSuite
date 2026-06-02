package xuanmo.arcartxsuite.market.shop;

import java.util.UUID;

/**
 * 系统商店限购记录。
 */
public record ShopLimitRecord(
    UUID player,
    String shopId,
    String itemId,
    int purchasedCount,
    long lastPurchaseTime,
    long resetTime
) {}
