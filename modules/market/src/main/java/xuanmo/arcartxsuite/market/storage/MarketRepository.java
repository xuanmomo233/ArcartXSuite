package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.dynamic.DynamicShopState;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;
import xuanmo.arcartxsuite.market.shop.ShopLimitRecord;

/**
 * 市场数据持久化仓库接口。
 */
public interface MarketRepository {

    void initialize() throws Exception;

    void shutdown();

    // ─── 拍卖行 ─────────────────────────────────────────────

    /**
     * 插入上架记录。
     *
     * @return {@code true} 表示插入成功（id 已回填）。调用方据此决定是否归还物品/退费。
     */
    boolean insertListing(AuctionListing listing);

    void updateListing(AuctionListing listing);

    /**
     * 状态 CAS：仅当当前状态等于 {@code expect} 时才更新为 {@code update}。
     *
     * @return {@code true} 表示本次调用成功抢占（影响行数为 1），用于防止到期/购买并发重复结算。
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

    // ─── 竞价 ───────────────────────────────────────────────

    void insertBid(AuctionBid bid);

    List<AuctionBid> getBidsForListing(long listingId);

    @Nullable AuctionBid getHighestBid(long listingId);

    // ─── 交易历史 ────────────────────────────────────────────

    void insertHistory(AuctionHistory history);

    List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit);

    int countHistoryByPlayer(UUID player);

    // ─── 收藏 ───────────────────────────────────────────────

    void addFavorite(UUID player, long listingId);

    void removeFavorite(UUID player, long listingId);

    List<Long> getFavorites(UUID player);

    boolean isFavorite(UUID player, long listingId);

    // ─── 系统商店限购 ────────────────────────────────────────

    @Nullable ShopLimitRecord getShopLimit(UUID player, String shopId, String itemId);

    void upsertShopLimit(ShopLimitRecord record);

    void resetExpiredShopLimits(String resetType);

    // ─── 系统商店全局库存（stock-mode: global）────────────────

    /** 当前剩余库存；若尚无记录则按 {@code defaultMax} 初始化。 */
    int getGlobalShopStock(String shopId, String itemId, int defaultMax);

    /**
     * 原子扣减全局库存。
     *
     * @return {@code true} 表示扣减成功
     */
    boolean tryConsumeGlobalShopStock(String shopId, String itemId, int amount, int defaultMax);

    /** 退还库存（购买失败或部分成交时调用）。remaining 不超过 defaultMax。 */
    void restoreGlobalShopStock(String shopId, String itemId, int amount, int defaultMax);

    // ─── 系统商店玩家独立库存（stock-mode: per-player）────────

    /** 玩家剩余库存；若尚无记录则按 {@code defaultMax} 初始化。 */
    int getPlayerShopStock(UUID player, String shopId, String itemId, int defaultMax);

    /**
     * 原子扣减玩家独立库存。
     *
     * @return {@code true} 表示扣减成功
     */
    boolean tryConsumePlayerShopStock(UUID player, String shopId, String itemId, int amount, int defaultMax);

    /** 退还玩家独立库存（购买失败或部分成交时调用）。remaining 不超过 defaultMax。 */
    void restorePlayerShopStock(UUID player, String shopId, String itemId, int amount, int defaultMax);

    // ─── 回收统计 ───────────────────────────────────────────

    void addRecycleStats(UUID player, String currency, double amount, int itemCount);

    double getRecycleTotal(UUID player, String currency);

    // ─── 待发放队列（离线补发 / 背包溢出补发）────────────────

    /** 入队一条待发放物品（itemData 为序列化后的物品）。 */
    void addPendingItem(UUID player, String itemData, String reason);

    /** 入队一条待发放货币。 */
    void addPendingCurrency(UUID player, String currency, double amount, String reason);

    /** 取出某玩家的全部待发放记录。 */
    List<PendingDelivery> getPendingDeliveries(UUID player);

    /** 删除一条已成功发放的记录。 */
    void deletePendingDelivery(long id);

    // ─── 动态商品策略状态 ───────────────────────────────────

    /** 获取动态策略持久化状态。 */
    @Nullable DynamicShopState getDynamicShopState(String strategyKey);

    /** 保存（UPSERT）动态策略持久化状态。 */
    void upsertDynamicShopState(DynamicShopState state);

    // ─── 玩家商店 ─────────────────────────────────────────────

    /** 插入玩家商店上架记录，成功后 id 回填。 */
    boolean insertPlayerShopListing(PlayerShopListing listing);

    void updatePlayerShopListing(PlayerShopListing listing);

    /** 状态 CAS：仅当当前状态等于 expect 时才更新为 update。 */
    boolean compareAndSetPlayerShopListingStatus(long listingId,
                                                  PlayerShopListing.ListingStatus expect,
                                                  PlayerShopListing.ListingStatus update);

    @Nullable PlayerShopListing getPlayerShopListing(long listingId);

    /** 获取所有活跃的玩家商店商品（分页）。 */
    List<PlayerShopListing> getActivePlayerShopListings(int offset, int limit);

    /** 按卖家获取活跃商品（分页）。 */
    List<PlayerShopListing> getActivePlayerShopListingsBySeller(UUID seller, int offset, int limit);

    /** 搜索玩家商店商品。 */
    List<PlayerShopListing> searchPlayerShopListings(String keyword, int offset, int limit);

    /** 获取有活跃商品的所有卖家（UUID + 名称），用于浏览玩家列表。 */
    List<PlayerShopSellerInfo> getPlayerShopSellers(int offset, int limit);

    int countActivePlayerShopListings();

    int countActivePlayerShopListingsBySeller(UUID seller);

    int countSearchPlayerShopListings(String keyword);

    int countPlayerShopSellers();

    int countListingsByPlayerShopSeller(UUID seller);

    List<PlayerShopListing> getExpiredPlayerShopListings();

    // ─── 玩家商店交易历史 ──────────────────────────────────────

    void insertPlayerShopHistory(PlayerShopHistory history);

    List<PlayerShopHistory> getPlayerShopHistoryByPlayer(UUID player, int offset, int limit);

    int countPlayerShopHistoryByPlayer(UUID player);

    // ─── 玩家商店卖家信息 ──────────────────────────────────────

    /** 玩家商店卖家摘要（UUID + 名称 + 在售数量）。 */
    record PlayerShopSellerInfo(UUID seller, String sellerName, int activeCount) {}

    /** 玩家商店交易历史记录。 */
    record PlayerShopHistory(
        long id, long listingId, UUID seller, UUID buyer,
        String itemData, String itemDisplayName,
        double price, String currency, double taxAmount,
        String transactionType, long timestamp
    ) {
        public enum TransactionType {
            PURCHASE, EXPIRED, CANCELLED
        }
    }
}
