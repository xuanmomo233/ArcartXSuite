package xuanmo.arcartxsuite.market.auction;

import java.util.UUID;

/**
 * 拍卖竞价记录。
 */
public record AuctionBid(
    long id,
    long listingId,
    UUID bidder,
    String bidderName,
    double amount,
    long timestamp
) {}
