package xuanmo.arcartxsuite.market.storage;

import java.math.BigDecimal;
import java.util.List;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;

/** Reservation and recoverable delivery snapshot written before a player-shop debit. */
public record PlayerShopOrder(PlayerShopListing listing, MarketOperation order, MarketOperation debit,
                              BigDecimal tax, List<MarketOperation> deliveries, boolean storefront) {
    public PlayerShopOrder {
        deliveries = List.copyOf(deliveries);
    }

    public static String reference(long listing, BigDecimal tax, int deliveries) {
        return "pshop-order:" + listing + ":" + tax.toPlainString() + ":" + deliveries;
    }
}
