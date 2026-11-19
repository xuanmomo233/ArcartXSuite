package xuanmo.arcartxsuite.market.auction;

import java.util.UUID;

/** The stored bidding terms needed to quote and confirm an auction bid. */
public record AuctionBidTerms(long id, UUID seller, double startingBid, double currentBid, UUID highestBidder,
                              String highestBidderName, String currency, AuctionListing.ListingType type,
                              AuctionListing.ListingStatus status, long expiresAt) {
    public boolean canBid(UUID viewer) {
        return status == AuctionListing.ListingStatus.ACTIVE && type != AuctionListing.ListingType.BUY_NOW
            && seller != null && System.currentTimeMillis() < expiresAt && !viewer.equals(seller);
    }

    public String revision() {
        return revision(id, currentBid, highestBidder, expiresAt, status);
    }

    public static String revision(AuctionListing listing) {
        return revision(listing.getId(), listing.getCurrentBid(), listing.getHighestBidder(),
            listing.getExpiresAt(), listing.getStatus());
    }

    private static String revision(long id, double currentBid, UUID bidder, long expiresAt,
                                   AuctionListing.ListingStatus status) {
        return id + "|" + currentBid + "|" + bidder + "|" + expiresAt + "|" + status;
    }
}
