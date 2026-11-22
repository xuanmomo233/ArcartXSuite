package xuanmo.arcartxsuite.market.storage;

import xuanmo.arcartxsuite.market.auction.AuctionListing;

/** The quoted listing and the two durable records written before a bidder can be charged. */
public record AuctionBidOrder(AuctionListing expected, MarketOperation order, MarketOperation debit,
                              double amount, String bidderName) {
    public enum Outcome { PLACED, REFUND_QUEUED, DECLINED, REVIEW }
}
