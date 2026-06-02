package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;

/**
 * 拍卖交易历史记录。
 */
public record AuctionHistory(
    long id,
    long listingId,
    UUID seller,
    UUID buyer,
    String itemData,
    String itemDisplayName,
    double price,
    String currency,
    double taxAmount,
    String transactionType,
    long timestamp
) {
    public enum TransactionType {
        BUY_NOW, BID_WIN, EXPIRED, CANCELLED
    }
}
