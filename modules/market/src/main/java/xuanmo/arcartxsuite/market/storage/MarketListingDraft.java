package xuanmo.arcartxsuite.market.storage;

import java.math.BigDecimal;
import java.util.UUID;

/** Durable listing metadata shared by the auction and player-shop escrow flows. */
public sealed interface MarketListingDraft permits AuctionListingDraft, PlayerShopListingDraft {
    enum Kind {
        AUCTION("AUCTION_LIST", "auction-listing", "auction_listing"),
        PLAYER_SHOP("PSHOP_LIST", "pshop-listing", "pshop_listing");

        private final String operation;
        private final String prefix;
        private final String receipt;

        Kind(String operation, String prefix, String receipt) {
            this.operation = operation;
            this.prefix = prefix;
            this.receipt = receipt;
        }

        public String operation() { return operation; }
        public String prefix() { return prefix; }
        public String receipt() { return receipt; }
    }

    enum Reservation { RESERVED, LIMIT_REACHED, ALREADY_PENDING }

    record Save(Kind kind, String id, UUID seller, long listingId, long durationMillis, long serverStart) {
        public String reference() { return kind.prefix() + ":" + listingId + ":" + durationMillis + ":" + serverStart; }
    }

    Kind kind();
    UUID seller();
    String sellerName();
    long listingId();
    String itemData();
    long durationMillis();
    String id();
    String worker();
    String sourceNode();
    long serverStart();
    String feeCurrency();
    BigDecimal fee();
    String itemName();
    String material();

    default String reference() { return save().reference(); }
    default Save save() { return new Save(kind(), id(), seller(), listingId(), durationMillis(), serverStart()); }
}
