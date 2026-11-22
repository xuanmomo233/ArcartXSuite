package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.auction.AuctionListing;

/** The exact listing snapshot and all rows committed with its terminal status. */
public record AuctionSettlement(AuctionListing expected, MarketOperation receipt, AuctionHistory history,
                                List<MarketOperation> deliveries, @Nullable AuctionPublication publication) {
    public AuctionSettlement {
        deliveries = List.copyOf(deliveries);
    }

    public AuctionSettlement(AuctionListing expected, MarketOperation receipt, AuctionHistory history,
                             List<MarketOperation> deliveries) {
        this(expected, receipt, history, deliveries, null);
    }
}
