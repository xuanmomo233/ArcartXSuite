package xuanmo.arcartxsuite.market.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.auction.AuctionBid;
import xuanmo.arcartxsuite.market.auction.AuctionBidTerms;
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

    xuanmo.arcartxsuite.market.recycle.RecyclePreferences recyclePreferences(UUID player,boolean defaultAuto);
    long recyclePreferencesVersion(UUID player);
    boolean updateRecyclePreferences(UUID player,long version,String action,boolean enabled,xuanmo.arcartxsuite.market.recycle.RecycleProtectionRule rule,int maximum);
    void noteMarketPlayer(UUID player,String name);
    UUID resolveMarketPlayer(String name);
    void describeOperation(String id,String name,String material,String itemData);
    MarketAuditPage searchMarketAudit(MarketAuditFilter filter);
    MarketAuditRow marketAuditRow(String id);

    void ensurePlayerShopProfile(UUID owner,String ownerName,boolean publish);
    xuanmo.arcartxsuite.market.playershop.PlayerShopProfile playerShopProfile(UUID owner);
    boolean savePlayerShopProfile(xuanmo.arcartxsuite.market.playershop.PlayerShopProfile profile,long expectedVersion,boolean keepIcon);
    List<xuanmo.arcartxsuite.market.playershop.PlayerShopCard> playerShopDirectory(UUID viewer,String keyword,boolean favorites,int offset,int limit);
    int countPlayerShopDirectory(UUID viewer,String keyword,boolean favorites);
    xuanmo.arcartxsuite.market.playershop.PlayerShopView playerShopView(UUID owner,UUID viewer,String keyword,int page,int pageSize);
    int setPlayerShopFollow(UUID viewer,UUID owner,boolean follow,int maximum);
    boolean reservePlayerShopPurchase(PlayerShopOrder order);
    void finishPlayerShopPurchase(PlayerShopOrder order, xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment);
    String reviewPlayerShopPurchase(String id, String action, String audit);
    MarketListingDraft.Reservation reserveListingDraft(MarketListingDraft draft, int maximum);
    void recordListingFee(MarketListingDraft draft, xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment);
    boolean abortListingDraft(MarketListingDraft.Save save, String sourceNode, String note);
    List<MarketListingDraft.Save> listingSaves(String sourceNode, String after, int limit);
    boolean publishListingDraft(MarketListingDraft.Save save, String sourceNode);
    boolean isListingDraftPublished(MarketListingDraft.Save save, String sourceNode);

    void insertOperation(MarketOperation operation);
    boolean transitionOperation(String id, String expected, String state, String note, String owner);
    boolean transitionItemBatch(List<String> ids, String owner, boolean claim);
    List<MarketOperation> operations(String state, UUID player, int offset, int limit);
    List<MarketOperation> pendingDeliveries(UUID player, String inventoryNode, int limit);
    void heartbeatMarketOwner(String owner);
    void recoverAbandonedOperations();
    @Nullable MarketOperation shopOrder(String requestId);
    Map<String, ShopCheckout.Availability> shopAvailability(UUID player, String shop, List<ShopCheckout.Product> products);
    ShopCheckout.Reservation reserveShopOrder(MarketOperation order, ShopCheckout checkout, List<MarketOperation> rewards);
    void finishShopOrder(MarketOperation order, boolean paid, String note);
    String reviewShopOrder(String id, String action, String audit);
    void finishConfirmedShopRefunds();
    boolean resolveOperation(String id, String action, String note);

    boolean adjustShopStock(String shop, String item, String mode, int maximum, String action, int amount, UUID player);
    String shopStockSummary(String shop, String item, String mode, int maximum);
    void resetShopLimits(String shop, String item, UUID player);
    boolean restockShopIfDue(String shop, String item, String mode, int maximum, long intervalMillis);

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
    boolean reserveAuctionBid(AuctionBidOrder order);
    AuctionBidOrder.Outcome finishAuctionBid(AuctionBidOrder order,
        xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult payment, long newExpiresAt);
    xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot readAuctionRoom(long listingId,UUID viewer);
    List<AuctionListing> browseAuction(String mode,UUID viewer,String keyword,int offset,int limit);
    int countBrowseAuction(String mode,UUID viewer,String keyword);
    boolean settleExpiredAuction(AuctionSettlement settlement);
    long auctionSettlementRevision();
    default List<AuctionPublication> claimAuctionPublications(String token, long now, int limit) {
        return claimAuctionPublications(token, now, limit, true);
    }
    List<AuctionPublication> claimAuctionPublications(String token, long now, int limit, boolean includeRefreshOnly);
    boolean finishAuctionPublication(String id, String token, boolean event, boolean refresh, long retryAt);
    boolean cancelAuction(AuctionSettlement settlement, @Nullable UUID seller, boolean allowBids);
    List<AuctionListing> favoriteListings(UUID player, String keyword, int offset, int limit);
    int countFavoriteListings(UUID player, String keyword);
    List<UUID> auctionWatchers(long listingId);
    String latestAuctionBidderName(long listingId, UUID bidder);

    void deleteListing(long listingId);

    @Nullable AuctionListing getListing(long listingId);
    @Nullable AuctionBidTerms readAuctionBidTerms(long listingId);

    List<AuctionListing> getActiveListings(int offset, int limit);

    List<AuctionListing> getActiveListingsByCategory(String category, int offset, int limit);

    List<AuctionListing> searchListings(String keyword, int offset, int limit);

    List<AuctionListing> getListingsBySeller(UUID seller);

    List<AuctionListing> getListingsBySeller(UUID seller, int offset, int limit);

    int countActiveListings();

    int countActiveListingsByCategory(String category);

    int countSearchListings(String keyword);

    int countListingsBySeller(UUID seller);

    List<AuctionListing> getExpiredListings(int limit);

    // ─── 竞价 ───────────────────────────────────────────────

    void insertBid(AuctionBid bid);

    List<AuctionBid> getBidsForListing(long listingId);

    @Nullable AuctionBid getHighestBid(long listingId);

    // ─── 交易历史 ────────────────────────────────────────────

    void insertHistory(AuctionHistory history);

    List<AuctionHistory> getHistoryByPlayer(UUID player, int offset, int limit);

    int countHistoryByPlayer(UUID player);

    // ─── 收藏 ───────────────────────────────────────────────

    enum FavoriteChange { ADDED, REMOVED, LIMIT_REACHED, MISSING }

    FavoriteChange toggleAuctionFavorite(UUID player, long listingId);

    List<Long> getFavorites(UUID player);

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

    boolean terminatePlayerShopListing(PlayerShopTermination termination);
    @Nullable PlayerShopListing getPlayerShopListing(long listingId);

    @Nullable PlayerShopListing getPublishedPlayerShopListing(long listingId);

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

    List<PlayerShopListing> searchPlayerShopListingsBySeller(UUID seller, String keyword, int offset, int limit);

    int countSearchPlayerShopListingsBySeller(UUID seller, String keyword);

    List<PlayerShopListing> getExpiredPlayerShopListings(int limit);

    // ─── 玩家商店交易历史 ──────────────────────────────────────

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
