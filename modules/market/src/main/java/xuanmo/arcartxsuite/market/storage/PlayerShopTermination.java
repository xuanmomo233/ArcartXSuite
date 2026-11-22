package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;

/** The listing snapshot and delivery records for a committed removal or expiration. */
public record PlayerShopTermination(PlayerShopListing expected, boolean expired, MarketOperation receipt,
                                    MarketRepository.PlayerShopHistory history, List<MarketOperation> deliveries) {
    public PlayerShopTermination {
        deliveries = List.copyOf(deliveries);
    }
}
