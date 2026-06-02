package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.shop.ShopLimitRecord;

/**
 * 市场数据持久化仓库接口。
 */
public interface MarketRepository {

    void initialize() throws Exception;

    void shutdown();

    // ─── 拍卖行 ─────────────────────────────────────────────

    void insertListing(AuctionListing listing);

    void updateListing(AuctionListing listing);

    void deleteListing(long listingId);

    @Nullable AuctionListing getListing(long listingId);

    List<AuctionListing> getActiveListings(int offset, int limit);

    List<AuctionListing> getActiveListingsByCategory(String category, int offset, int limit);

    List<AuctionListing> searchListings(String keyword, int offset, int limit);

    List<AuctionListing> getListingsBySeller(UUID seller);

    int countActiveListings();

    int countListingsBySeller(UUID seller);

    List<AuctionListing> getExpiredListings();

    // ─── 竞价 ───────────────────────────────────────────────

    void insertBid(AuctionBid bid);

    List<AuctionBid> getBidsForListing(long listingId);

    @Nullable AuctionBid getHighestBid(long listingId);

    // ─── 交易历史 ────────────────────────────────────────────

    void insertHistory(AuctionHistory history);

    List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit);

    // ─── 收藏 ───────────────────────────────────────────────

    void addFavorite(UUID player, long listingId);

    void removeFavorite(UUID player, long listingId);

    List<Long> getFavorites(UUID player);

    boolean isFavorite(UUID player, long listingId);

    // ─── 系统商店限购 ────────────────────────────────────────

    @Nullable ShopLimitRecord getShopLimit(UUID player, String shopId, String itemId);

    void upsertShopLimit(ShopLimitRecord record);

    void resetExpiredShopLimits(String resetType);

    // ─── 回收统计 ───────────────────────────────────────────

    void addRecycleStats(UUID player, String currency, double amount, int itemCount);

    double getRecycleTotal(UUID player, String currency);
}
