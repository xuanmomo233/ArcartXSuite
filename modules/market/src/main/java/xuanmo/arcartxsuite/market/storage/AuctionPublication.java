package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;
import xuanmo.arcartxsuite.api.BuildEdition;

/** Committed settlement notification. The two destinations acknowledge independently. */
public record AuctionPublication(String id, long listingId, UUID seller, UUID buyer, String item,
                                 double price, String currency, double tax, String buyerName,
                                 boolean eventPending, boolean refreshPending, long settledAt) {
    private static final String CANCELLATION_PREFIX = "auction-cancel:";

    public static String cancellationId(long listingId) {
        return CANCELLATION_PREFIX + listingId;
    }

    public String refreshPayload() {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return "";
        return (id.startsWith(CANCELLATION_PREFIX) ? "LISTING_CANCELLED:" : "LISTING_SETTLED:") + listingId;
    }
}
