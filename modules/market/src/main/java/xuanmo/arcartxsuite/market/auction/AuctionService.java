package xuanmo.arcartxsuite.market.auction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
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
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.AuctionConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.storage.AuctionHistory;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 拍卖行核心业务服务。
 */
public class AuctionService {
    private xuanmo.arcartxsuite.market.MarketLedger ledger;

    public void setLedger(xuanmo.arcartxsuite.market.MarketLedger ledger) { this.ledger = ledger; }


    /** EventBus 发布回调（由 MarketService 注入）。 */
    public interface AuctionEventPublisher {
        void publish(String topic, @Nullable Player player, java.util.Map<String, String> payload);
    }

    private static final int AUCTION_BACKPACK_SLOTS = 36;
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    /** 拼音 token 缓存：displayName → [pinyin, initials]，避免同一物品名重复计算拼音 */
    private static final ConcurrentHashMap<String, String[]> PINYIN_CACHE = new ConcurrentHashMap<>();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private final JavaPlugin plugin;
    private final AuctionConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final RedisMarketCache redisCache;
    private final @Nullable Consumer<String> crossServerPublisher;
    private final CurrencyBridgeAPI currencyManager;
    private final AuctionItemSerializer itemSerializer;
    private final Logger logger;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher;
    private final ItemMatcherAPI itemMatcher;
    private volatile AuctionEventPublisher eventPublisher;
    private BukkitTask schedulerTask;

    public void setEventPublisher(AuctionEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    public AuctionService(JavaPlugin plugin, AuctionConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, RedisMarketCache redisCache,
                          @Nullable Consumer<String> crossServerPublisher,
                          CurrencyBridgeAPI currencyManager,
                          AuctionItemSerializer itemSerializer, Logger logger,
                          xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
                          xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher,
                          ItemMatcherAPI itemMatcher) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.redisCache = redisCache;
        this.crossServerPublisher = crossServerPublisher;
        this.currencyManager = currencyManager;
        this.itemSerializer = itemSerializer;
        this.logger = logger;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.itemMatcher = itemMatcher;
    }

    public void start(long intervalTicks) {
        schedulerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processExpired, intervalTicks, intervalTicks);
        logger.info("[Auction] 拍卖行服务已启动，到期检查间隔: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    /**
     * 玩家上架物品（纯竞价，无一口价）。
     */
    public ListingResult createListing(Player seller, ItemStack item,
                                       double startingBid, String currency, long durationSeconds) {
        return createListingInternal(seller, item, startingBid, currency, durationSeconds, "", -1);
    }

    public ListingResult createListing(Player seller, int slot,
                                       double startingBid, String currency, long durationSeconds) {
        if (slot < 0 || slot >= AUCTION_BACKPACK_SLOTS) {
            return ListingResult.fail("invalid item slot");
        }
        ItemStack item = seller.getInventory().getItem(slot);
        return createListingInternal(seller, item, startingBid, currency, durationSeconds, "", slot);
    }

    public ListingResult createListing(Player seller, int slot, double startingBid, String currency, long durationSeconds, String message) {
        if (slot < 0 || slot >= AUCTION_BACKPACK_SLOTS) {
            return ListingResult.fail("invalid item slot");
        }
        return createListingInternal(seller, seller.getInventory().getItem(slot), startingBid, currency, durationSeconds, message, slot);
    }

    private ListingResult createListingInternal(Player seller, ItemStack item,
                                                 double startingBid, String currency, long durationSeconds,
                                                 String message, int sourceSlot) {
        return ledger.trade("AUCTION_LIST", seller.getUniqueId(), "slot=" + sourceSlot + ",bid=" + startingBid, item==null?"":itemSerializer.serialize(item),
            () -> createListingInternalWork(seller, item, startingBid, currency, durationSeconds, message, sourceSlot), result -> result.success());
    }

private ListingResult createListingInternalWork(Player seller, ItemStack item,
                                                 double startingBid, String currency, long durationSeconds,
                                                 String message, int sourceSlot) {
        if (!Double.isFinite(startingBid) || startingBid < config.minPrice() || startingBid > config.maxPrice()) {
            return ListingResult.fail("起拍价超出允许范围");
        }
        if (item == null || item.getType().isAir()) {
            return ListingResult.fail("没有可上架的物品");
        }
        // 防止与主手物品引用别名：使用副本作为上架物品
        item = item.clone();
        ledger.describe(getItemDisplayName(item),item.getType().name(),itemSerializer.serialize(item));

        // 检查上架数量限制
        int currentCount = repository.countListingsBySeller(seller.getUniqueId());
        if (currentCount >= config.maxListingsPerPlayer()) {
            return ListingResult.fail("上架数量已达上限 (" + config.maxListingsPerPlayer() + ")");
        }

        // 检查物品黑名单
        if (isBlacklisted(item)) {
            return ListingResult.fail(messages.itemBlacklisted());
        }

        // 校验主手确实持有要上架的物品（防止客户端伪造物品 / 数量不符导致复制）
        ItemStack inHand = sourceSlot >= 0
            ? seller.getInventory().getItem(sourceSlot)
            : seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()
                || !inHand.isSimilar(item) || inHand.getAmount() < item.getAmount()) {
            return ListingResult.fail("请手持要上架的物品");
        }

        // 限制时长
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));

        // 先扣除背包物品（占有），避免"先入库后扣除"在异常时造成物品复制
        if (inHand.getAmount() == item.getAmount()) {
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), null);
        } else {
            inHand.setAmount(inHand.getAmount() - item.getAmount());
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), inHand);
        }

        // 扣上架费（先验余额，避免不必要的扣款失败日志）
        BigDecimal feeCharged = null;
        CurrencyBridgeAPI.CurrencyBridge feeBridge = null;
        if (config.listingFee() > 0) {
            feeBridge = currencyManager.bridge(config.listingFeeCurrency());
            if (feeBridge == null || !feeBridge.available()) {
                giveBack(seller, item);
                return ListingResult.fail("上架费货币不可用");
            }
            BigDecimal fee = BigDecimal.valueOf(config.listingFee());
            if (feeBridge.balance(seller).compareTo(fee) < 0) {
                giveBack(seller, item);
                return ListingResult.fail(messages.insufficientFunds());
            }
            CurrencyTransactionResult feeResult = feeBridge.withdraw(seller, fee);
            if (!feeResult.success()) {
                giveBack(seller, item);
                return ListingResult.fail(messages.insufficientFunds());
            }
            feeCharged = fee;
        }

        // 序列化物品
        String itemData = itemSerializer.serialize(item);
        String displayName = getItemDisplayName(item);
        String category = classifyItem(item);
        // 生成拼音/首字母搜索 token（多音字遍历所有读音，字间空格分隔）
        String[] pinyinTokens = toPinyinTokens(displayName);
        String pinyin = pinyinTokens[0];
        String initials = pinyinTokens[1];

        // 纯竞价拍卖，始终为 AUCTION 类型
        AuctionListing.ListingType type = AuctionListing.ListingType.AUCTION;

        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(
            0, seller.getUniqueId(), seller.getName(), itemData, displayName,
            pinyin, initials, category, 0, startingBid, 0, null,
            currency, type, AuctionListing.ListingStatus.ACTIVE,
            now, now + duration * 1000L
        );
        listing.setMessage(message);

        // 入库失败则退费 + 归还物品，保证不丢
        if (!repository.insertListing(listing)) {
            if (feeCharged != null && feeBridge != null) {
                feeBridge.deposit(seller, feeCharged);
            }
            giveBack(seller, item);
            return ListingResult.fail("上架失败，请稍后重试");
        }

        // 使 Redis 缓存失效
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
        publishCrossServer("LISTING_CREATED:" + listing.getId());

        return ListingResult.success(listing);
    }

    /** 把物品归还给玩家，背包装不下的部分掉落在脚下（玩家在场，安全）。 */
    private void describeListing(AuctionListing listing){
        String material="";
        try{ItemStack item=itemSerializer.deserialize(listing.getItemData());if(item!=null)material=item.getType().name();}
        catch(RuntimeException ignored){}
        ledger.describe(listing.getItemDisplayName(),material,listing.getItemData());
    }

    private int heldSlot(Player player) {
        return player.getInventory().getHeldItemSlot();
    }

    private void giveBack(Player player, ItemStack item) {
        repository.addPendingItem(player.getUniqueId(), itemSerializer.serialize(item), "listing_giveback");
    }

    /**
     * 竞价。
     */
    public BidResult placeBid(Player bidder, long listingId, double amount) {
        return ledger.trade("AUCTION_BID", bidder.getUniqueId(), "listing=" + listingId + ",bid=" + amount,
            () -> placeBidWork(bidder, listingId, amount), result -> result.success());
    }

public BidResult placeBidWork(Player bidder, long listingId, double amount) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive() || listing.getType()==AuctionListing.ListingType.BUY_NOW) {
            return BidResult.fail("该物品已不可竞价");
        }
        if (listing.getSeller().equals(bidder.getUniqueId())) {
            return BidResult.fail("不能对自己的物品出价");
        }

        describeListing(listing);
        double minBid=minimumBid(listing).doubleValue();
        int scale=currencyScale(listing.getCurrency());
        if(!Double.isFinite(amount) || amount<=0 || amount>config.maxPrice()
            || BigDecimal.valueOf(amount).stripTrailingZeros().scale()>scale) return BidResult.fail("出价金额或货币精度无效");
        if(amount<minBid) return BidResult.fail("当前最低出价为 "+currencyManager.format(listing.getCurrency(),minimumBid(listing)));
        boolean ownRaise=bidder.getUniqueId().equals(listing.getHighestBidder());
        BigDecimal debit=BigDecimal.valueOf(amount).subtract(ownRaise?BigDecimal.valueOf(listing.getCurrentBid()):BigDecimal.ZERO);
        long newExpiry=extendedExpiry(listing,System.currentTimeMillis());

        // 冻结买家资金（扣款）
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(listing.getCurrency());
        if (bridge == null || !bridge.available()) {
            return BidResult.fail("货币系统不可用");
        }
        CurrencyTransactionResult result = bridge.withdraw(bidder, debit);
        if (!result.success()) {
            return BidResult.fail(messages.insufficientFunds());
        }

        // 退还上一位最高出价者押金（安全发放：在线即时入账并通知，离线入待发放队列，绝不丢钱）
        // 注：竞价使用数据库 CAS（updateListingBidIfHigher）保证原子性，防止并发覆盖；客户端包统一切主线程串行执行进一步消除竞态。
        UUID previousBidder = listing.getHighestBidder();
        double previousBid = listing.getCurrentBid();
        newExpiry=extendedExpiry(listing,System.currentTimeMillis());

        if (!repository.placeAuctionBid(listing,amount,bidder.getUniqueId(),bidder.getName(),newExpiry)) {
            depositSafe(bidder.getUniqueId(),listing.getCurrency(),debit.doubleValue(),"auction_bid_race_refund");
            return BidResult.fail("拍卖状态已变化，本次扣款已进入退款队列，请重新确认报价");
        }
        if(previousBidder!=null && previousBid>0 && !ownRaise) {
            depositSafe(previousBidder,listing.getCurrency(),previousBid,"auction_outbid_refund");
            if(config.notifyOutbid()) notice(previousBidder,messages.auctionOutbid().replace("{item}",listing.getItemDisplayName())
                .replace("{amount}",currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(amount)))+" &7原出价已进入退款队列。",config.outbidNotify(),"outbid:"+listingId);
        }
        String bidText="&e关注的拍卖 #"+listingId+" &f"+listing.getItemDisplayName()+" &7最新出价 &e"
            +currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(amount));
        if(config.notifyWatchBids() || newExpiry>listing.getExpiresAt()) {
            for(UUID watcher:repository.auctionWatchers(listingId)) {
                if(watcher.equals(bidder.getUniqueId()) || watcher.equals(previousBidder))continue;
                notice(watcher,bidText+(newExpiry>listing.getExpiresAt()?" &6已延时，请查看最新截止时间":""),"chat","watch-bid:"+listingId);
            }
        }
        if(newExpiry>listing.getExpiresAt()) bidder.sendMessage(ChatColor.GOLD+"本次有效出价触发临近结束延时，请查看最新截止时间。");

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
        publishCrossServer("BID_PLACED:" + listingId + ":" + amount + ":" + bidder.getUniqueId());

        return BidResult.success(amount);
    }
    /**
     * 取消上架。
     */
    public boolean cancelListing(Player seller, long listingId) {
        return ledger.trade("AUCTION_CANCEL", seller.getUniqueId(), "listing=" + listingId,
            () -> cancelListingWork(seller, listingId), result -> result);
    }

public boolean cancelListingWork(Player seller, long listingId) {
        AuctionListing listing=repository.claimAuctionCancellation(listingId,seller.getUniqueId(),config.allowCancelWithBids());
        if(listing==null)return false;
        describeListing(listing);

        // 如果有竞价者，退还押金（安全发放，离线不丢）
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            depositSafe(listing.getHighestBidder(), listing.getCurrency(), listing.getCurrentBid(), "auction_cancel_refund");
        }

        listing.setStatus(AuctionListing.ListingStatus.CANCELLED);
        repository.updateListing(listing);

        // 返还物品（安全发放：背包满或离线均入待发放队列）
        deliverItemSafe(seller.getUniqueId(), listing, "auction_cancel_return");

        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "CANCELLED", System.currentTimeMillis()
        ));

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
        publishCrossServer("LISTING_CANCELLED:" + listingId);

        java.util.Set<UUID> recipients=new java.util.HashSet<>(repository.auctionWatchers(listingId));
        if(listing.getHighestBidder()!=null)recipients.add(listing.getHighestBidder());
        if(config.notifySettlement()) for(UUID target:recipients) notice(target,"&7拍卖 #"+listingId+" "+listing.getItemDisplayName()+" 已取消，有效押金按记录退还。","chat","cancel:"+listingId);
        return true;
    }

    /**
     * 管理员强制下架：退还竞价押金与上架物品，写入历史。
     */
    public boolean adminForceCancelListing(long listingId) {
        return adminForceCancelListing(new UUID(0,0),listingId);
    }

    public boolean adminForceCancelListing(UUID actor,long listingId) {
        return ledger.trade("AUCTION_ADMIN_CANCEL", actor, "listing=" + listingId,
            () -> adminForceCancelListingWork(listingId), result -> result);
    }

public boolean adminForceCancelListingWork(long listingId) {
        AuctionListing listing=repository.claimAuctionCancellation(listingId,null,true);
        if(listing==null)return false;
        describeListing(listing);

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
        }
        publishCrossServer("LISTING_CANCELLED:" + listingId);
        java.util.Set<UUID> recipients=new java.util.HashSet<>(repository.auctionWatchers(listingId));
        if(listing.getHighestBidder()!=null)recipients.add(listing.getHighestBidder());
        if(config.notifySettlement()) for(UUID target:recipients) notice(target,"&7拍卖 #"+listingId+" "+listing.getItemDisplayName()+" 已取消，有效押金按记录退还。","chat","cancel:"+listingId);
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

    public BigDecimal minimumBid(AuctionListing listing) {
        BigDecimal current=BigDecimal.valueOf(listing.getCurrentBid());
        BigDecimal minimum=listing.getHighestBidder()==null?BigDecimal.valueOf(listing.getStartingBid())
            :current.add(current.multiply(BigDecimal.valueOf(config.minBidIncrementRatio()))
                .max(BigDecimal.valueOf(config.minBidIncrementAbsolute())));
        return minimum.setScale(currencyScale(listing.getCurrency()),java.math.RoundingMode.CEILING);
    }

    public int currencyScale(String currency) {
        var definition=currencyManager.definition(currency);
        return definition==null?2:Math.max(0,Math.min(16,definition.scale()));
    }

    public long extendedExpiry(AuctionListing listing,long now) {
        if(!config.endProtectionEnabled() || listing.getExpiresAt()<=now
            || listing.getExpiresAt()-now>config.endWindowSeconds()*1000L)return listing.getExpiresAt();
        long cap=listing.getOriginalExpiresAt()+config.endMaxExtensionSeconds()*1000L;
        return Math.max(listing.getExpiresAt(),Math.min(cap,now+config.endExtendSeconds()*1000L));
    }

    private void notice(UUID player,String text,String channel,String reference) {
        if(player==null)return;
        repository.insertOperation(xuanmo.arcartxsuite.market.MarketLedger.delivery(player,"NOTICE","","0",text,
            "notice-"+channel+":"+reference));
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
            logger.log(Level.SEVERE, "[Auction] 到期处理异常", e);
        }
    }

    private void processExpiredListing(AuctionListing listing) {
        ledger.trade("AUCTION_SETTLE", listing.getSeller(), "listing=" + listing.getId(),
            () -> { processExpiredListingWork(listing); return true; }, result -> result);
    }

private void processExpiredListingWork(AuctionListing listing) {
        listing=repository.claimAuctionSettlement(listing.getId());
        if(listing==null)return;
        describeListing(listing);
        boolean hasBidder=listing.getHighestBidder()!=null && listing.getCurrentBid()>0;

        if (hasBidder) {
            // 竞价成交
            double taxRate = getEffectiveTaxRate(listing.getSeller());
            double tax = listing.getCurrentBid() * taxRate;
            double sellerIncome = listing.getCurrentBid() - tax;

            // 卖家收款 + 买家得物品（安全发放，离线不丢）
            depositSafe(listing.getSeller(), listing.getCurrency(), sellerIncome, "auction_bidwin_income");
            deliverItemSafe(listing.getHighestBidder(), listing, "auction_bidwin_item");

            repository.updateListing(listing);
            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), listing.getHighestBidder(),
                listing.getItemData(), listing.getItemDisplayName(),
                listing.getCurrentBid(), listing.getCurrency(), tax,
                "BID_WIN", System.currentTimeMillis()
            ));

            if(config.notifySettlement()) {
                notice(listing.getSeller(),messages.auctionSold().replace("{item}",listing.getItemDisplayName())
                    .replace("{amount}",currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(sellerIncome)))+" &7收入已进入交付队列。","chat","sold:"+listing.getId());
                notice(listing.getHighestBidder(),"&a你已拍得 #"+listing.getId()+" "+listing.getItemDisplayName()+"，成交价 "
                    +currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(listing.getCurrentBid()))+"，物品已进入交付队列。","chat","won:"+listing.getId());
            }
            publishAuctionPurchased(listing, tax);
        } else {
            // 无人竞价，退还物品给卖家（安全发放）
            deliverItemSafe(listing.getSeller(), listing, "auction_expired_return");
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), null,
                listing.getItemData(), listing.getItemDisplayName(),
                0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
            ));

            if(config.notifySettlement()) notice(listing.getSeller(),messages.auctionExpired().replace("{item}",listing.getItemDisplayName()),"chat","expired:"+listing.getId());
        }

        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
        if(config.notifySettlement()) for(UUID watcher:repository.auctionWatchers(listing.getId())) {
            if(watcher.equals(listing.getSeller()) || watcher.equals(listing.getHighestBidder()))continue;
            notice(watcher,"&7关注的拍卖 #"+listing.getId()+" "+listing.getItemDisplayName()
                +(hasBidder?" 已成交，成交价 "+currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(listing.getCurrentBid())):" 已流拍"),"chat","watch-ended:"+listing.getId());
        }
        publishCrossServer("LISTING_SETTLED:" + listing.getId());
    }

    /**
     * 发布 {@code axs.market.auction_purchased} 事件（竞价成交结算时触发）。
     * 买家此时通常已离线，事件 player 可能为 null，买家信息以 payload 字段为准。
     */
    private void publishAuctionPurchased(AuctionListing listing, double tax) {
        AuctionEventPublisher publisher = eventPublisher;
        if (publisher == null || listing.getHighestBidder() == null) {
            return;
        }
        String buyerName;
        var offlineBuyer = Bukkit.getOfflinePlayer(listing.getHighestBidder());
        buyerName = offlineBuyer.getName() != null ? offlineBuyer.getName() : "未知";
        String currency = listing.getCurrency() != null ? listing.getCurrency() : "money";
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("listing_id", String.valueOf(listing.getId()));
        payload.put("item", listing.getItemDisplayName() != null ? listing.getItemDisplayName() : "");
        payload.put("price", String.valueOf(listing.getCurrentBid()));
        payload.put("formatted_price",
            java.util.Objects.requireNonNullElse(
                currencyManager.format(currency, BigDecimal.valueOf(listing.getCurrentBid())),
                String.valueOf(listing.getCurrentBid())));
        payload.put("currency", currency);
        payload.put("tax", String.valueOf(tax));
        payload.put("seller_uuid", listing.getSeller().toString());
        payload.put("buyer_uuid", listing.getHighestBidder().toString());
        payload.put("buyer", buyerName);
        publisher.publish("axs.market.auction_purchased",
            Bukkit.getPlayer(listing.getHighestBidder()), payload);
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
     * 安全发放物品：收件人在线则在主线程放入背包（装不下的部分通过邮件返还），
     * 离线则通过邮件发送，邮件不可用时降级入待发放队列。彻底避免物品丢失。
     */
    private void deliverItemSafe(UUID target, AuctionListing listing, String reason) {
        repository.addPendingItem(target, listing.getItemData(), reason + ":" + listing.getId());
    }

    /**
     * 安全发放货币：收件人在线且货币可用则在主线程入账，
     * 否则入待发放队列，玩家上线时补发。彻底避免货款丢失。
     */
    private void depositSafe(UUID target, String currency, double amount, String reason) {
        if (amount > 0) repository.addPendingCurrency(target, currency, amount, reason);
    }

    // ─── 工具方法 ───────────────────────────────────────────

    private double getEffectiveTaxRate(UUID seller) {
        Player player = Bukkit.getPlayer(seller);
        if (player != null) {
            return getEffectiveTaxRateOnline(player);
        }
        // 离线玩家：尝试用 LuckPerms 加载用户数据查询权限
        return getEffectiveTaxRateOffline(seller);
    }

    /** 在线玩家权限检查 */
    private double getEffectiveTaxRateOnline(Player player) {
        double bestTaxRate = Double.POSITIVE_INFINITY;
        boolean matched = false;
        for (var entry : config.taxDiscount().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                double taxRate = Math.max(0, entry.getValue());
                if (!matched || taxRate < bestTaxRate) {
                    bestTaxRate = taxRate;
                }
                matched = true;
            }
        }
        return matched ? bestTaxRate : config.transactionTaxRate();
    }

    /**
     * 离线玩家权限检查：通过 LuckPerms API 异步加载用户数据后同步查询权限。
     * <p>
     * LuckPerms 未安装或加载失败时回退到默认税率。
     * 定时任务（过期处理）在主线程调用，短暂阻塞可接受（loadUser 通常 <50ms）。
     */
    private double getEffectiveTaxRateOffline(UUID seller) {
        if (config.taxDiscount().isEmpty()) {
            return config.transactionTaxRate();
        }
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.UserManager userManager = luckPerms.getUserManager();
            // 先查缓存（在线但 Bukkit.getPlayer 返回 null 的边缘情况）
            net.luckperms.api.model.user.User cached = userManager.getUser(seller);
            net.luckperms.api.model.user.User user = cached != null
                ? cached : userManager.loadUser(seller).join();
            if (user == null) {
                logger.fine("LuckPerms 加载离线用户失败，使用默认税率 seller=" + seller);
                return config.transactionTaxRate();
            }
            // 查询权限：user.getCachedData().getPermissionData().checkPermission(permission)
            net.luckperms.api.cacheddata.CachedPermissionData permData = user.getCachedData().getPermissionData();
            double bestTaxRate = Double.POSITIVE_INFINITY;
            boolean matched = false;
            for (var entry : config.taxDiscount().entrySet()) {
                if (permData.checkPermission(entry.getKey()).asBoolean()) {
                    double taxRate = Math.max(0, entry.getValue());
                    if (!matched || taxRate < bestTaxRate) {
                        bestTaxRate = taxRate;
                    }
                    matched = true;
                }
            }
            return matched ? bestTaxRate : config.transactionTaxRate();
        } catch (Exception | LinkageError e) {
            logger.fine("LuckPerms 不可用，离线卖家使用默认税率 seller=" + seller + ": " + e.getMessage());
            return config.transactionTaxRate();
        }
    }

    private boolean isBlacklisted(ItemStack item) {
        if (item == null) return true;
        if (itemMatcher == null) return false;
        return itemMatcher.matches(config.blacklist(), item);
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
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(item);
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    /**
     * 生成 displayName 的拼音和首字母搜索 token。
     * 多音字遍历所有读音，同一字的多读音连续拼接；字之间用空格分隔。
     * 返回 [pinyin, initials]，如 "钻石剑" → ["zuan shi jian", "z s j"]。
     */
    private static String[] toPinyinTokens(String rawValue) {
        String stripped = ChatColor.stripColor(rawValue == null ? "" : value(rawValue));
        return PINYIN_CACHE.computeIfAbsent(stripped, AuctionService::computePinyinTokens);
    }

    private static String[] computePinyinTokens(String stripped) {
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) {
                continue;
            }
            String[] values;
            try {
                values = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                values = null;
            }
            if (values != null && values.length > 0) {
                StringBuilder charPinyin = new StringBuilder();
                StringBuilder charInitials = new StringBuilder();
                for (String value : values) {
                    String candidate = value.replaceAll("[^a-z0-9]", "");
                    if (!candidate.isBlank()) {
                        charPinyin.append(candidate);
                        charInitials.append(candidate.charAt(0));
                    }
                }
                if (charPinyin.length() > 0) {
                    if (pinyin.length() > 0) pinyin.append(' ');
                    if (initials.length() > 0) initials.append(' ');
                    pinyin.append(charPinyin);
                    initials.append(charInitials);
                    continue;
                }
            }
            if (Character.isLetterOrDigit(character)) {
                if (pinyin.length() > 0) pinyin.append(' ');
                if (initials.length() > 0) initials.append(' ');
                pinyin.append(Character.toLowerCase(character));
                initials.append(Character.toLowerCase(character));
            }
        }
        return new String[]{pinyin.toString(), initials.toString()};
    }

    private static String value(String s) {
        return s == null ? "" : s.trim();
    }

    // 离线 / 背包溢出的发放统一由 deliverItemSafe / depositSafe + 待发放队列处理，
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

    public record BidResult(boolean success, @Nullable String error, double amount) {
        public static BidResult success(double amount) { return new BidResult(true, null, amount); }
        public static BidResult fail(String error) { return new BidResult(false, error, 0); }
    }
}
