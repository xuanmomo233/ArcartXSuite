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
    long timestamp,
    long previousExpiresAt,
    long expiresAt
) {
    public AuctionBid(long id,long listingId,UUID bidder,String bidderName,double amount,long timestamp) {
        this(id,listingId,bidder,bidderName,amount,timestamp,0,0);
    }
}
