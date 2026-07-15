package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.shop.ShopLimitRecord;

/**
 * Ã¥Â¸ÂÃ¥ÂÂºÃ¦ÂÂ°Ã¦ÂÂ®Ã¦ÂÂÃ¤Â¹ÂÃ¥ÂÂÃ¤Â»ÂÃ¥ÂºÂÃ¦ÂÂ¥Ã¥ÂÂ£Ã£ÂÂ
 */
public interface MarketRepository {

    void initialize() throws Exception;

    void shutdown();

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã¦ÂÂÃ¥ÂÂÃ¨Â¡Â Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    /**
     * Ã¦ÂÂÃ¥ÂÂ¥Ã¤Â¸ÂÃ¦ÂÂ¶Ã¨Â®Â°Ã¥Â½ÂÃ£ÂÂ
     *
     * @return {@code true} Ã¨Â¡Â¨Ã§Â¤ÂºÃ¦ÂÂÃ¥ÂÂ¥Ã¦ÂÂÃ¥ÂÂÃ¯Â¼Âid Ã¥Â·Â²Ã¥ÂÂÃ¥Â¡Â«Ã¯Â¼ÂÃ£ÂÂÃ¨Â°ÂÃ§ÂÂ¨Ã¦ÂÂ¹Ã¦ÂÂ®Ã¦Â­Â¤Ã¥ÂÂ³Ã¥Â®ÂÃ¦ÂÂ¯Ã¥ÂÂ¦Ã¥Â½ÂÃ¨Â¿ÂÃ§ÂÂ©Ã¥ÂÂ/Ã©ÂÂÃ¨Â´Â¹Ã£ÂÂ
     */
    boolean insertListing(AuctionListing listing);

    void updateListing(AuctionListing listing);

    /**
     * Ã§ÂÂ¶Ã¦ÂÂ CASÃ¯Â¼ÂÃ¤Â»ÂÃ¥Â½ÂÃ¥Â½ÂÃ¥ÂÂÃ§ÂÂ¶Ã¦ÂÂÃ§Â­ÂÃ¤ÂºÂ {@code expect} Ã¦ÂÂ¶Ã¦ÂÂÃ¦ÂÂ´Ã¦ÂÂ°Ã¤Â¸Âº {@code update}Ã£ÂÂ
     *
     * @return {@code true} Ã¨Â¡Â¨Ã§Â¤ÂºÃ¦ÂÂ¬Ã¦Â¬Â¡Ã¨Â°ÂÃ§ÂÂ¨Ã¦ÂÂÃ¥ÂÂÃ¦ÂÂ¢Ã¥ÂÂ Ã¯Â¼ÂÃ¥Â½Â±Ã¥ÂÂÃ¨Â¡ÂÃ¦ÂÂ°Ã¤Â¸Âº 1Ã¯Â¼ÂÃ¯Â¼ÂÃ§ÂÂ¨Ã¤ÂºÂÃ©ÂÂ²Ã¦Â­Â¢Ã¥ÂÂ°Ã¦ÂÂ/Ã¨Â´Â­Ã¤Â¹Â°Ã¥Â¹Â¶Ã¥ÂÂÃ©ÂÂÃ¥Â¤ÂÃ§Â»ÂÃ§Â®ÂÃ£ÂÂ
     */
    boolean compareAndSetListingStatus(long listingId,
                                       AuctionListing.ListingStatus expect,
                                       AuctionListing.ListingStatus update);

    /** Update an active listing bid only when the new amount is strictly higher. */
    boolean updateListingBidIfHigher(long listingId, double amount, UUID bidder);

    void deleteListing(long listingId);

    @Nullable AuctionListing getListing(long listingId);

    List<AuctionListing> getActiveListings(int offset, int limit);

    List<AuctionListing> getActiveListingsByCategory(String category, int offset, int limit);

    List<AuctionListing> searchListings(String keyword, int offset, int limit);

    List<AuctionListing> getListingsBySeller(UUID seller);

    List<AuctionListing> getListingsBySeller(UUID seller, int offset, int limit);

    int countActiveListings();

    int countActiveListingsByCategory(String category);

    int countSearchListings(String keyword);

    int countListingsBySeller(UUID seller);

    List<AuctionListing> getExpiredListings();

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã§Â«ÂÃ¤Â»Â· Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    void insertBid(AuctionBid bid);

    List<AuctionBid> getBidsForListing(long listingId);

    @Nullable AuctionBid getHighestBid(long listingId);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã¤ÂºÂ¤Ã¦ÂÂÃ¥ÂÂÃ¥ÂÂ² Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    void insertHistory(AuctionHistory history);

    List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit);

    int countHistoryByPlayer(UUID player);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã¦ÂÂ¶Ã¨ÂÂ Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    void addFavorite(UUID player, long listingId);

    void removeFavorite(UUID player, long listingId);

    List<Long> getFavorites(UUID player);

    boolean isFavorite(UUID player, long listingId);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã§Â³Â»Ã§Â»ÂÃ¥ÂÂÃ¥ÂºÂÃ©ÂÂÃ¨Â´Â­ Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    @Nullable ShopLimitRecord getShopLimit(UUID player, String shopId, String itemId);

    void upsertShopLimit(ShopLimitRecord record);

    void resetExpiredShopLimits(String resetType);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã§Â³Â»Ã§Â»ÂÃ¥ÂÂÃ¥ÂºÂÃ¥ÂÂ¨Ã¥Â±ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼Âstock-mode: globalÃ¯Â¼ÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    /** Ã¥Â½ÂÃ¥ÂÂÃ¥ÂÂ©Ã¤Â½ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼ÂÃ¨ÂÂ¥Ã¥Â°ÂÃ¦ÂÂ Ã¨Â®Â°Ã¥Â½ÂÃ¥ÂÂÃ¦ÂÂ {@code defaultMax} Ã¥ÂÂÃ¥Â§ÂÃ¥ÂÂÃ£ÂÂ */
    int getGlobalShopStock(String shopId, String itemId, int defaultMax);

    /**
     * Ã¥ÂÂÃ¥Â­ÂÃ¦ÂÂ£Ã¥ÂÂÃ¥ÂÂ¨Ã¥Â±ÂÃ¥ÂºÂÃ¥Â­ÂÃ£ÂÂ
     *
     * @return {@code true} Ã¨Â¡Â¨Ã§Â¤ÂºÃ¦ÂÂ£Ã¥ÂÂÃ¦ÂÂÃ¥ÂÂ
     */
    boolean tryConsumeGlobalShopStock(String shopId, String itemId, int amount, int defaultMax);

    /** Ã©ÂÂÃ¨Â¿ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼ÂÃ¨Â´Â­Ã¤Â¹Â°Ã¥Â¤Â±Ã¨Â´Â¥Ã¦ÂÂÃ©ÂÂ¨Ã¥ÂÂÃ¦ÂÂÃ¤ÂºÂ¤Ã¦ÂÂ¶Ã¨Â°ÂÃ§ÂÂ¨Ã¯Â¼ÂÃ£ÂÂ */
    void restoreGlobalShopStock(String shopId, String itemId, int amount);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã§Â³Â»Ã§Â»ÂÃ¥ÂÂÃ¥ÂºÂÃ§ÂÂ©Ã¥Â®Â¶Ã§ÂÂ¬Ã§Â«ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼Âstock-mode: per-playerÃ¯Â¼ÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    /** Ã§ÂÂ©Ã¥Â®Â¶Ã¥ÂÂ©Ã¤Â½ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼ÂÃ¨ÂÂ¥Ã¥Â°ÂÃ¦ÂÂ Ã¨Â®Â°Ã¥Â½ÂÃ¥ÂÂÃ¦ÂÂ {@code defaultMax} Ã¥ÂÂÃ¥Â§ÂÃ¥ÂÂÃ£ÂÂ */
    int getPlayerShopStock(UUID player, String shopId, String itemId, int defaultMax);

    /**
     * Ã¥ÂÂÃ¥Â­ÂÃ¦ÂÂ£Ã¥ÂÂÃ§ÂÂ©Ã¥Â®Â¶Ã§ÂÂ¬Ã§Â«ÂÃ¥ÂºÂÃ¥Â­ÂÃ£ÂÂ
     *
     * @return {@code true} Ã¨Â¡Â¨Ã§Â¤ÂºÃ¦ÂÂ£Ã¥ÂÂÃ¦ÂÂÃ¥ÂÂ
     */
    boolean tryConsumePlayerShopStock(UUID player, String shopId, String itemId, int amount, int defaultMax);

    /** Ã©ÂÂÃ¨Â¿ÂÃ§ÂÂ©Ã¥Â®Â¶Ã§ÂÂ¬Ã§Â«ÂÃ¥ÂºÂÃ¥Â­ÂÃ¯Â¼ÂÃ¨Â´Â­Ã¤Â¹Â°Ã¥Â¤Â±Ã¨Â´Â¥Ã¦ÂÂÃ©ÂÂ¨Ã¥ÂÂÃ¦ÂÂÃ¤ÂºÂ¤Ã¦ÂÂ¶Ã¨Â°ÂÃ§ÂÂ¨Ã¯Â¼ÂÃ£ÂÂ */
    void restorePlayerShopStock(UUID player, String shopId, String itemId, int amount);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã¥ÂÂÃ¦ÂÂ¶Ã§Â»ÂÃ¨Â®Â¡ Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    void addRecycleStats(UUID player, String currency, double amount, int itemCount);

    double getRecycleTotal(UUID player, String currency);

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã¥Â¾ÂÃ¥ÂÂÃ¦ÂÂ¾Ã©ÂÂÃ¥ÂÂÃ¯Â¼ÂÃ§Â¦Â»Ã§ÂºÂ¿Ã¨Â¡Â¥Ã¥ÂÂ / Ã¨ÂÂÃ¥ÂÂÃ¦ÂºÂ¢Ã¥ÂÂºÃ¨Â¡Â¥Ã¥ÂÂÃ¯Â¼ÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    /** Ã¥ÂÂ¥Ã©ÂÂÃ¤Â¸ÂÃ¦ÂÂ¡Ã¥Â¾ÂÃ¥ÂÂÃ¦ÂÂ¾Ã§ÂÂ©Ã¥ÂÂÃ¯Â¼ÂitemData Ã¤Â¸ÂºÃ¥ÂºÂÃ¥ÂÂÃ¥ÂÂÃ¥ÂÂÃ§ÂÂÃ§ÂÂ©Ã¥ÂÂÃ¯Â¼ÂÃ£ÂÂ */
    void addPendingItem(UUID player, String itemData, String reason);

    /** Ã¥ÂÂ¥Ã©ÂÂÃ¤Â¸ÂÃ¦ÂÂ¡Ã¥Â¾ÂÃ¥ÂÂÃ¦ÂÂ¾Ã¨Â´Â§Ã¥Â¸ÂÃ£ÂÂ */
    void addPendingCurrency(UUID player, String currency, double amount, String reason);

    /** Ã¥ÂÂÃ¥ÂÂºÃ¦ÂÂÃ§ÂÂ©Ã¥Â®Â¶Ã§ÂÂÃ¥ÂÂ¨Ã©ÂÂ¨Ã¥Â¾ÂÃ¥ÂÂÃ¦ÂÂ¾Ã¨Â®Â°Ã¥Â½ÂÃ£ÂÂ */
    List<PendingDelivery> getPendingDeliveries(UUID player);

    /** Ã¥ÂÂ Ã©ÂÂ¤Ã¤Â¸ÂÃ¦ÂÂ¡Ã¥Â·Â²Ã¦ÂÂÃ¥ÂÂÃ¥ÂÂÃ¦ÂÂ¾Ã§ÂÂÃ¨Â®Â°Ã¥Â½ÂÃ£ÂÂ */
    void deletePendingDelivery(long id);
}
