package xuanmo.arcartxsuite.market.playershop;

import java.util.List;

/** A stable read of the public storefront; no balances or private transaction history are exposed. */
public record PlayerShopView(PlayerShopCard shop,List<PlayerShopListing> listings,int page,int totalPages,
                             int totalListings,int completedSales,String keyword) {}
