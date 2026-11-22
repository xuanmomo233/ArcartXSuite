package xuanmo.arcartxsuite.market.storage;

import java.math.BigDecimal;
import java.util.UUID;
import xuanmo.arcartxsuite.market.auction.AuctionListing;

public record AuctionListingDraft(AuctionListing listing, long durationMillis, String id, String worker,
                                  String sourceNode, long serverStart, String feeCurrency, BigDecimal fee,
                                  String itemName, String material) implements MarketListingDraft {
    @Override public Kind kind() { return Kind.AUCTION; }
    @Override public UUID seller() { return listing.getSeller(); }
    @Override public String sellerName() { return listing.getSellerName(); }
    @Override public long listingId() { return listing.getId(); }
    @Override public String itemData() { return listing.getItemData(); }
}
