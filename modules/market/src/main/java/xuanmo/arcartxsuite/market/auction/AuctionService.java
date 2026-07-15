package xuanmo.arcartxsuite.market.auction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.AuctionConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.storage.AuctionHistory;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 拍卖行核心业务服务。
 */
public class AuctionService {

    private final JavaPlugin plugin;
    private final AuctionConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final RedisMarketCache redisCache;
    private final @Nullable Consumer<String> crossServerPublisher;
    private final CurrencyBridgeAPI currencyManager;
    private final @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier;
    private final AuctionItemSerializer itemSerializer;
    private final Logger logger;
    private BukkitTask schedulerTask;

    public AuctionService(JavaPlugin plugin, AuctionConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, RedisMarketCache redisCache,
                          @Nullable Consumer<String> crossServerPublisher,
                          CurrencyBridgeAPI currencyManager,
                          @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier,
                          AuctionItemSerializer itemSerializer, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.redisCache = redisCache;
        this.crossServerPublisher = crossServerPublisher;
        this.currencyManager = currencyManager;
        this.mailSupplier = mailSupplier;
        this.itemSerializer = itemSerializer;
        this.logger = logger;
    }

    public void start(long intervalTicks) {
        schedulerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processExpired, intervalTicks, intervalTicks);
        logger.info("[Market-Auction] æåè¡æå¡å·²å¯å¨ï¼å°ææ£æ¥é´é: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    /**
     * 玩家上架物品。
     */
    public ListingResult createListing(Player seller, ItemStack item, double buyNowPrice,
                                       double startingBid, String currency, long durationSeconds) {
        return createListingInternal(seller, item, buyNowPrice, startingBid, currency, durationSeconds, "", -1);
    }

    public ListingResult createListing(Player seller, int slot, double buyNowPrice,
                                       double startingBid, String currency, long durationSeconds) {
        if (slot < 0 || slot >= seller.getInventory().getSize()) {
            return ListingResult.fail("invalid item slot");
        }
        ItemStack item = seller.getInventory().getItem(slot);
        return createListingInternal(seller, item, buyNowPrice, startingBid, currency, durationSeconds, "", slot);
    }

    public ListingResult createListing(Player seller, int slot, double buyNowPrice, double startingBid, String currency, long durationSeconds, String message) {
        if (slot < 0 || slot >= seller.getInventory().getSize()) {
            return ListingResult.fail("invalid item slot");
        }
        return createListingInternal(seller, seller.getInventory().getItem(slot), buyNowPrice, startingBid, currency, durationSeconds, message, slot);
    }

    private ListingResult createListingInternal(Player seller, ItemStack item, double buyNowPrice,
                                                 double startingBid, String currency, long durationSeconds,
                                                 String message, int sourceSlot) {
        if (item == null || item.getType().isAir()) {
            return ListingResult.fail("æ²¡æå¯ä¸æ¶çç©å");
        }
        // é²æ­¢ä¸ä¸»æç©åå¼ç¨å«åï¼ä½¿ç¨å¯æ¬ä½ä¸ºä¸æ¶ç©å
        item = item.clone();

        // æ£æ¥ä¸æ¶æ°ééå¶
        int currentCount = repository.countListingsBySeller(seller.getUniqueId());
        if (currentCount >= config.maxListingsPerPlayer()) {
            return ListingResult.fail("ä¸æ¶æ°éå·²è¾¾ä¸é (" + config.maxListingsPerPlayer() + ")");
        }

        // æ£æ¥ç©åé»åå
        if (isBlacklisted(item)) {
            return ListingResult.fail(messages.itemBlacklisted());
        }

        // æ ¡éªä¸»æç¡®å®ææè¦ä¸æ¶çç©åï¼é²æ­¢å®¢æ·ç«¯ä¼ªé ç©å / æ°éä¸ç¬¦å¯¼è´å¤å¶ï¼
        ItemStack inHand = sourceSlot >= 0
            ? seller.getInventory().getItem(sourceSlot)
            : seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()
                || !inHand.isSimilar(item) || inHand.getAmount() < item.getAmount()) {
            return ListingResult.fail("è¯·ææè¦ä¸æ¶çç©å");
        }

        // éå¶æ¶é¿
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));

        // åæ£é¤èåç©åï¼å æï¼ï¼é¿å"åå¥åºåæ£é¤"å¨å¼å¸¸æ¶é æç©åå¤å¶
        if (inHand.getAmount() == item.getAmount()) {
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), null);
        } else {
            inHand.setAmount(inHand.getAmount() - item.getAmount());
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), inHand);
        }

        // æ£ä¸æ¶è´¹
        BigDecimal feeCharged = null;
        CurrencyBridgeAPI.CurrencyBridge feeBridge = null;
        if (config.listingFee() > 0) {
            feeBridge = currencyManager.bridge(config.listingFeeCurrency());
            if (feeBridge == null || !feeBridge.available()) {
                giveBack(seller, item);
                return ListingResult.fail("ä¸æ¶è´¹è´§å¸ä¸å¯ç¨");
            }
            CurrencyTransactionResult feeResult = feeBridge.withdraw(seller, BigDecimal.valueOf(config.listingFee()));
            if (!feeResult.success()) {
                giveBack(seller, item);
                return ListingResult.fail(messages.insufficientFunds());
            }
            feeCharged = BigDecimal.valueOf(config.listingFee());
        }

        // åºååç©å
        String itemData = itemSerializer.serialize(item);
        String displayName = getItemDisplayName(item);
        String category = classifyItem(item);

        // ç¡®å®ä¸æ¶ç±»å
        AuctionListing.ListingType type;
        if (buyNowPrice > 0 && startingBid > 0) type = AuctionListing.ListingType.BOTH;
        else if (buyNowPrice > 0) type = AuctionListing.ListingType.BUY_NOW;
        else type = AuctionListing.ListingType.AUCTION;

        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(
            0, seller.getUniqueId(), seller.getName(), itemData, displayName,
            category, buyNowPrice, startingBid, 0, null,
            currency, type, AuctionListing.ListingStatus.ACTIVE,
            now, now + duration * 1000L
        );
        listing.setMessage(message);

        // å¥åºå¤±è´¥åéè´¹ + å½è¿ç©åï¼ä¿è¯ä¸ä¸¢
        if (!repository.insertListing(listing)) {
            if (feeCharged != null && feeBridge != null) {
                feeBridge.deposit(seller, feeCharged);
            }
            giveBack(seller, item);
            return ListingResult.fail("ä¸æ¶å¤±è´¥ï¼è¯·ç¨åéè¯");
        }

        // ä½¿ Redis ç¼å­å¤±æ
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_CREATED:" + listing.getId());
        }

        return ListingResult.success(listing);
    }

    /** 把物品归还给玩家，背包装不下的部分掉落在脚下（玩家在场，安全）。 */
    private int heldSlot(Player player) {
        return player.getInventory().getHeldItemSlot();
    }

    private void giveBack(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item.clone());
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /**
     * 一口价购买。
     */
    public PurchaseResult buyNow(Player buyer, long listingId) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return PurchaseResult.fail("è¯¥ç©åå·²ä¸å¯è´­ä¹°");
        }
        if (listing.getBuyNowPrice() <= 0) {
            return PurchaseResult.fail("è¯¥ç©åä¸æ¯æä¸å£ä»·");
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            return PurchaseResult.fail("ä¸è½è´­ä¹°èªå·±çç©å");
        }

        double price = listing.getBuyNowPrice();
        String currency = listing.getCurrency();

        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return PurchaseResult.fail("è´§å¸ç³»ç»ä¸å¯ç¨");
        }

        // åæ¢å ï¼ç¶æ CASï¼ï¼ä¿è¯åä¸ç©åä¸ä¼è¢«å¹¶åè´­ä¹° / å°æä»»å¡éå¤ç»ç®
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.SOLD)) {
            return PurchaseResult.fail("è¯¥ç©åå·²ä¸å¯è´­ä¹°");
        }

        // æ£ä¹°å®¶é±ï¼å¤±è´¥ååæ»æ¢å ï¼
        CurrencyTransactionResult withdrawResult = bridge.withdraw(buyer, BigDecimal.valueOf(price));
        if (!withdrawResult.success()) {
            repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.SOLD, AuctionListing.ListingStatus.ACTIVE);
            return PurchaseResult.fail(messages.insufficientFunds());
        }

        // BOTH ç±»åè¥å·²æç«ä»·èï¼ä¸å£ä»·æäº¤ééè¿å¶æ¼éï¼å®å¨åæ¾ï¼ç¦»çº¿ä¸ä¸¢ï¼
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0
                && !listing.getHighestBidder().equals(buyer.getUniqueId())) {
            depositSafe(listing.getHighestBidder(), currency, listing.getCurrentBid(), "auction_outbid_refund");
        }

        // è®¡ç®ç¨è´¹
        double taxRate = getEffectiveTaxRate(listing.getSeller());
        double tax = price * taxRate;
        double sellerIncome = price - tax;

        // ç»åå®¶æé±ï¼å¨çº¿å³æ¶ / ç¦»çº¿å¥å¾åæ¾éåï¼ç»ä¸ä¸¢é±ï¼
        depositSafe(listing.getSeller(), currency, sellerIncome, "auction_sold_income");

        // æä¹åå¶ä½å­æ®µï¼ç¶æå·²æ¯ SOLDï¼
        listing.setStatus(AuctionListing.ListingStatus.SOLD);
        repository.updateListing(listing);

        // ç»ä¹°å®¶ç©åï¼å¨çº¿å³æ¶ / ç¦»çº¿æèåæ»¡å¥éï¼ç»ä¸ä¸¢ç©åï¼
        deliverItemSafe(buyer.getUniqueId(), listing, "auction_buynow_item");
        ItemStack item = itemSerializer.deserialize(listing.getItemData());

        // è®°å½åå²
        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), buyer.getUniqueId(),
            listing.getItemData(), listing.getItemDisplayName(),
            price, currency, tax, "BUY_NOW", System.currentTimeMillis()
        ));

        // éç¥åå®¶ï¼ä»å¨çº¿æ¶ï¼ä¸»çº¿ç¨å®å¨ï¼
        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.auctionSold().replace("%item%", listing.getItemDisplayName())
                    .replace("%amount%", currencyManager.format(currency, BigDecimal.valueOf(sellerIncome)))));
        }

        // Redis å¹¿æ­
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_SOLD:" + listing.getId());
        }

        return PurchaseResult.success(item, price, tax, currency);
    }

    /**
     * 竞价。
     */
    public BidResult placeBid(Player bidder, long listingId, double amount) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return BidResult.fail("è¯¥ç©åå·²ä¸å¯ç«ä»·");
        }
        if (listing.getType() == AuctionListing.ListingType.BUY_NOW) {
            return BidResult.fail("è¯¥ç©åä¸æ¯æç«ä»·");
        }
        if (listing.getSeller().equals(bidder.getUniqueId())) {
            return BidResult.fail("ä¸è½å¯¹èªå·±çç©ååºä»·");
        }

        // è®¡ç®æä½åºä»·
        double currentHighest = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartingBid();
        double minIncrement = Math.max(
            currentHighest * config.minBidIncrementRatio(),
            config.minBidIncrementAbsolute()
        );
        double minBid = listing.getCurrentBid() > 0 ? currentHighest + minIncrement : listing.getStartingBid();

        if (amount < minBid) {
            return BidResult.fail("出价必须 ≥ " + currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(minBid)));
        }

        // å»ç»ä¹°å®¶èµéï¼æ£æ¬¾ï¼
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(listing.getCurrency());
        if (bridge == null || !bridge.available()) {
            return BidResult.fail("è´§å¸ç³»ç»ä¸å¯ç¨");
        }
        CurrencyTransactionResult result = bridge.withdraw(bidder, BigDecimal.valueOf(amount));
        if (!result.success()) {
            return BidResult.fail(messages.insufficientFunds());
        }

        // éè¿ä¸ä¸ä½æé«åºä»·èæ¼éï¼å®å¨åæ¾ï¼å¨çº¿å³æ¶å¥è´¦å¹¶éç¥ï¼ç¦»çº¿å¥å¾åæ¾éåï¼ç»ä¸ä¸¢é±ï¼
        // 注：竞价依赖客户端包已切主线程串行执行，单服内无并发覆盖问题。
        UUID previousBidder = listing.getHighestBidder();
        double previousBid = listing.getCurrentBid();

        if (!repository.updateListingBidIfHigher(
                listingId,
                amount,
                bidder.getUniqueId())) {
            depositSafe(
                bidder.getUniqueId(),
                listing.getCurrency(),
                amount,
                "auction_bid_race_refund"
            );
            return BidResult.fail("已有更高出价，已退还本次出价");
        }

        if (previousBidder != null
                && previousBid > 0
                && !previousBidder.equals(bidder.getUniqueId())) {
            depositSafe(
                previousBidder,
                listing.getCurrency(),
                previousBid,
                "auction_outbid_refund"
            );
            Player prevPlayer = Bukkit.getPlayer(previousBidder);
            if (prevPlayer != null) {
                prevPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    messages.auctionOutbid()
                        .replace("%item%", listing.getItemDisplayName())
                        .replace("%amount%", currencyManager.format(
                            listing.getCurrency(),
                            BigDecimal.valueOf(amount)
                        ))));
            }
        }

        repository.insertBid(new AuctionBid(
            0,
            listingId,
            bidder.getUniqueId(),
            bidder.getName(),
            amount,
            System.currentTimeMillis()
        ));

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer(
                "BID_PLACED:" + listingId + ":" + amount + ":"
                    + bidder.getUniqueId()
            );
        }

        return BidResult.success(amount);
    }
    /**
     * 取消上架。
     */
    public boolean cancelListing(Player seller, long listingId) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null) return false;
        if (!listing.getSeller().equals(seller.getUniqueId())) return false;
        if (listing.getStatus() != AuctionListing.ListingStatus.ACTIVE) return false;

        // æ¢å ï¼é¿åä¸å°æä»»å¡å¹¶åéå¤å¤çï¼éæ¬¾ + éç©åªåçä¸æ¬¡ï¼
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.CANCELLED)) {
            return false;
        }

        // å¦ææç«ä»·èï¼éè¿æ¼éï¼å®å¨åæ¾ï¼ç¦»çº¿ä¸ä¸¢ï¼
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            depositSafe(listing.getHighestBidder(), listing.getCurrency(), listing.getCurrentBid(), "auction_cancel_refund");
        }

        listing.setStatus(AuctionListing.ListingStatus.CANCELLED);
        repository.updateListing(listing);

        // è¿è¿ç©åï¼å®å¨åæ¾ï¼èåæ»¡æç¦»çº¿åå¥å¾åæ¾éåï¼
        deliverItemSafe(seller.getUniqueId(), listing, "auction_cancel_return");

        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "CANCELLED", System.currentTimeMillis()
        ));

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_CANCELLED:" + listingId);
        }

        return true;
    }

    /**
     * 管理员强制下架：退还竞价押金与上架物品，写入历史。
     */
    public boolean adminForceCancelListing(long listingId) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null) return false;
        if (listing.getStatus() != AuctionListing.ListingStatus.ACTIVE) return false;

        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.CANCELLED)) {
            return false;
        }

        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            depositSafe(listing.getHighestBidder(), listing.getCurrency(), listing.getCurrentBid(), "auction_admin_cancel_refund");
        }

        listing.setStatus(AuctionListing.ListingStatus.CANCELLED);
        repository.updateListing(listing);
        deliverItemSafe(listing.getSeller(), listing, "auction_admin_cancel_return");

        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "ADMIN_CANCELLED", System.currentTimeMillis()
        ));

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_CANCELLED:" + listingId);
        }
        return true;
    }

    /**
     * 切换收藏。
     */
    public boolean toggleFavorite(UUID player, long listingId) {
        if (repository.isFavorite(player, listingId)) {
            repository.removeFavorite(player, listingId);
            return false;
        } else {
            repository.addFavorite(player, listingId);
            return true;
        }
    }

    public List<AuctionListing> getActiveListings(int page, int pageSize) {
        return repository.getActiveListings(page * pageSize, pageSize);
    }

    public List<AuctionListing> getListingsByCategory(String category, int page, int pageSize) {
        return repository.getActiveListingsByCategory(category, page * pageSize, pageSize);
    }

    public List<AuctionListing> searchListings(String keyword, int page, int pageSize) {
        return repository.searchListings(keyword, page * pageSize, pageSize);
    }

    public List<AuctionListing> getMyListings(UUID seller) {
        return repository.getListingsBySeller(seller);
    }

    public List<AuctionListing> getMyListings(UUID seller, int page, int pageSize) {
        return repository.getListingsBySeller(seller, page * pageSize, pageSize);
    }

    public int countActive() {
        return repository.countActiveListings();
    }

    public int countCategory(String category) {
        return repository.countActiveListingsByCategory(category);
    }

    public int countSearch(String keyword) {
        return repository.countSearchListings(keyword);
    }

    public int countMy(UUID seller) {
        return repository.countListingsBySeller(seller);
    }

    /**
     * 手动触发到期处理，返回处理的条目数。
     */
    public int triggerExpiredProcessing() {
        List<AuctionListing> expired = repository.getExpiredListings();
        int count = expired.size();
        for (AuctionListing listing : expired) {
            Bukkit.getScheduler().runTask(plugin, () -> processExpiredListing(listing));
        }
        return count;
    }

    // ─── 定期处理 ───────────────────────────────────────────

    private void processExpired() {
        try {
            List<AuctionListing> expired = repository.getExpiredListings();
            for (AuctionListing listing : expired) {
                Bukkit.getScheduler().runTask(plugin, () -> processExpiredListing(listing));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Market-Auction] å°æå¤çå¼å¸¸", e);
        }
    }

    private void processExpiredListing(AuctionListing listing) {
        boolean hasBidder = listing.getHighestBidder() != null && listing.getCurrentBid() > 0;
        AuctionListing.ListingStatus target = hasBidder
            ? AuctionListing.ListingStatus.SOLD
            : AuctionListing.ListingStatus.EXPIRED;

        // æ¢å ï¼ä»å½ä»ä¸º ACTIVE æ¶æ¬æ¬¡æè´è´£ç»ç®ï¼æç»ä¸è´­ä¹°/æå¨è§¦å/ä¸ä¸è½®ä»»å¡éå¤ç»ç®ï¼éå¤åé±åç©åï¼
        if (!repository.compareAndSetListingStatus(listing.getId(),
                AuctionListing.ListingStatus.ACTIVE, target)) {
            return;
        }
        listing.setStatus(target);

        if (hasBidder) {
            // ç«ä»·æäº¤
            double taxRate = getEffectiveTaxRate(listing.getSeller());
            double tax = listing.getCurrentBid() * taxRate;
            double sellerIncome = listing.getCurrentBid() - tax;

            // åå®¶æ¶æ¬¾ + ä¹°å®¶å¾ç©åï¼å®å¨åæ¾ï¼ç¦»çº¿ä¸ä¸¢ï¼
            depositSafe(listing.getSeller(), listing.getCurrency(), sellerIncome, "auction_bidwin_income");
            deliverItemSafe(listing.getHighestBidder(), listing, "auction_bidwin_item");

            repository.updateListing(listing);
            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), listing.getHighestBidder(),
                listing.getItemData(), listing.getItemDisplayName(),
                listing.getCurrentBid(), listing.getCurrency(), tax,
                "BID_WIN", System.currentTimeMillis()
            ));
        } else {
            // æ äººç«ä»·ï¼éè¿ç©åç»åå®¶ï¼å®å¨åæ¾ï¼
            deliverItemSafe(listing.getSeller(), listing, "auction_expired_return");
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), null,
                listing.getItemData(), listing.getItemDisplayName(),
                0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
            ));

            // éç¥åå®¶ï¼å¨çº¿æ¶ï¼
            Player seller = Bukkit.getPlayer(listing.getSeller());
            if (seller != null) {
                seller.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    messages.auctionExpired().replace("%item%", listing.getItemDisplayName())));
            }
        }

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
    }

    /** 在主线程执行任务（已在主线程则直接执行）。 */
    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * å®å¨åæ¾ç©åï¼æ¶ä»¶äººå¨çº¿åå¨ä¸»çº¿ç¨æ¾å¥èåï¼è£ä¸ä¸çé¨åå¥å¾åæ¾éåï¼ï¼
     * 离线则整笔入待发放队列，玩家上线时补发。彻底避免物品丢失。
     */
    private void deliverItemSafe(UUID target, AuctionListing listing, String reason) {
        final ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item == null) {
            logger.warning("[Market-Auction] ç©åååºååå¤±è´¥ï¼å·²è½¬å¥å¾åæ¾éå listing=" + listing.getId());
            repository.addPendingItem(target, listing.getItemData(), reason);
            return;
        }
        Player online = Bukkit.getPlayer(target);
        if (online != null && online.isOnline()) {
            runOnMain(() -> {
                java.util.Map<Integer, ItemStack> overflow = online.getInventory().addItem(item);
                for (ItemStack left : overflow.values()) {
                    repository.addPendingItem(target, itemSerializer.serialize(left), reason);
                }
            });
        } else {
            repository.addPendingItem(target, listing.getItemData(), reason);
        }
    }

    /**
     * å®å¨åæ¾è´§å¸ï¼æ¶ä»¶äººå¨çº¿ä¸è´§å¸å¯ç¨åå¨ä¸»çº¿ç¨å¥è´¦ï¼
     * 否则入待发放队列，玩家上线时补发。彻底避免货款丢失。
     */
    private void depositSafe(UUID target, String currency, double amount, String reason) {
        if (amount <= 0) {
            return;
        }
        Player online = Bukkit.getPlayer(target);
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (online != null && online.isOnline() && bridge != null && bridge.available()) {
            CurrencyTransactionResult[] resultHolder = new CurrencyTransactionResult[1];
            runOnMain(() -> resultHolder[0] = bridge.deposit(online, BigDecimal.valueOf(amount)));
            if (resultHolder[0] != null && resultHolder[0].success()) {
                return;
            }
            logger.warning("[Market-Auction] å¨çº¿å¥è´¦å¤±è´¥ï¼è½¬å¥å¾åæ¾éå: player="
                + online.getName() + " currency=" + currency + " amount=" + amount);
        }
        repository.addPendingCurrency(target, currency, amount, reason);
    }

    // ─── 工具方法 ───────────────────────────────────────────

    private double getEffectiveTaxRate(UUID seller) {
        Player player = Bukkit.getPlayer(seller);
        if (player == null) return config.transactionTaxRate();
        for (var entry : config.taxDiscount().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                return Math.max(0, entry.getValue());
            }
        }
        return config.transactionTaxRate();
    }

    private boolean isBlacklisted(ItemStack item) {
        if (item == null) return true;
        String materialName = item.getType().name();
        if (config.blacklist().materialIds().contains(materialName)) return true;

        String displayName = getItemDisplayName(item);
        for (String keyword : config.blacklist().nameContains()) {
            if (displayName.contains(keyword)) return true;
        }

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            String lore = String.join(" ", item.getItemMeta().getLore());
            for (String keyword : config.blacklist().loreContains()) {
                if (lore.contains(keyword)) return true;
            }
        }

        return false;
    }

    private String classifyItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return defaultCategoryId();
        }
        List<java.util.Map.Entry<String, xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.CategoryDefinition>> categories =
            new ArrayList<>(config.categories().entrySet());
        categories.sort(Comparator.comparingInt(entry -> entry.getValue().priority()));
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (var entry : categories) {
            var category = entry.getValue();
            if (category.isDefault() || category.nbtPath().isBlank() || category.nbtValues().isEmpty()) continue;
            String value = readConfiguredValue(pdc, category.nbtPath());
            if (value != null && category.nbtValues().stream().anyMatch(value::equalsIgnoreCase)) {
                return entry.getKey();
            }
        }
        return defaultCategoryId();
    }

    private String defaultCategoryId() {
        return config.categories().entrySet().stream()
            .filter(entry -> entry.getValue().isDefault())
            .min(Comparator.comparingInt(entry -> entry.getValue().priority()))
            .map(java.util.Map.Entry::getKey)
            .orElse("other");
    }

    private String readConfiguredValue(PersistentDataContainer pdc, String path) {
        if (!path.regionMatches(true, 0, "pdc:", 0, 4)) return null;
        String keyText = path.substring(4);
        int separator = keyText.indexOf(58);
        if (separator <= 0 || separator == keyText.length() - 1) return null;
        try {
            NamespacedKey key = new NamespacedKey(keyText.substring(0, separator), keyText.substring(separator + 1));
            String stringValue = pdc.get(key, PersistentDataType.STRING);
            if (stringValue != null) return stringValue;
            Integer intValue = pdc.get(key, PersistentDataType.INTEGER);
            if (intValue != null) return String.valueOf(intValue);
            Long longValue = pdc.get(key, PersistentDataType.LONG);
            if (longValue != null) return String.valueOf(longValue);
            Byte byteValue = pdc.get(key, PersistentDataType.BYTE);
            return byteValue != null ? String.valueOf(byteValue) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    // ç¦»çº¿ / èåæº¢åºçåæ¾ç»ä¸ç± deliverItemSafe / depositSafe + å¾åæ¾éåå¤çï¼
    // 不再使用旧的 depositOffline / createOfflineDeposit（离线时会丢钱）。

    private void publishCrossServer(String message) {
        if (crossServerPublisher != null && message != null && !message.isBlank()) {
            crossServerPublisher.accept(message);
        }
    }

    // ─── 结果类 ─────────────────────────────────────────────

    public record ListingResult(boolean success, @Nullable String error, @Nullable AuctionListing listing) {
        public static ListingResult success(AuctionListing listing) { return new ListingResult(true, null, listing); }
        public static ListingResult fail(String error) { return new ListingResult(false, error, null); }
    }

    public record PurchaseResult(boolean success, @Nullable String error, @Nullable ItemStack item, double price, double tax, @Nullable String currency) {
        public static PurchaseResult success(ItemStack item, double price, double tax, String currency) { return new PurchaseResult(true, null, item, price, tax, currency); }
        public static PurchaseResult fail(String error) { return new PurchaseResult(false, error, null, 0, 0, null); }
    }

    public record BidResult(boolean success, @Nullable String error, double amount) {
        public static BidResult success(double amount) { return new BidResult(true, null, amount); }
        public static BidResult fail(String error) { return new BidResult(false, error, 0); }
    }
}
