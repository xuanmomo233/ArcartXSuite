package xuanmo.arcartxsuite.market.auction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private final CurrencyBridgeAPI currencyManager;
    private final @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier;
    private final AuctionItemSerializer itemSerializer;
    private final Logger logger;
    private BukkitTask schedulerTask;

    public AuctionService(JavaPlugin plugin, AuctionConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, RedisMarketCache redisCache,
                          CurrencyBridgeAPI currencyManager,
                          @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier,
                          AuctionItemSerializer itemSerializer, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.redisCache = redisCache;
        this.currencyManager = currencyManager;
        this.mailSupplier = mailSupplier;
        this.itemSerializer = itemSerializer;
        this.logger = logger;
    }

    public void start(long intervalTicks) {
        schedulerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processExpired, intervalTicks, intervalTicks);
        logger.info("[Market-Auction] 拍卖行服务已启动，到期检查间隔: " + intervalTicks + " ticks");
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
        if (item == null || item.getType().isAir()) {
            return ListingResult.fail("没有可上架的物品");
        }
        // 防止与主手物品引用别名：使用副本作为上架物品
        item = item.clone();

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
        ItemStack inHand = seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()
                || !inHand.isSimilar(item) || inHand.getAmount() < item.getAmount()) {
            return ListingResult.fail("请手持要上架的物品");
        }

        // 限制时长
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));

        // 先扣除背包物品（占有），避免"先入库后扣除"在异常时造成物品复制
        if (inHand.getAmount() == item.getAmount()) {
            seller.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - item.getAmount());
            seller.getInventory().setItemInMainHand(inHand);
        }

        // 扣上架费
        BigDecimal feeCharged = null;
        CurrencyBridgeAPI.CurrencyBridge feeBridge = null;
        if (config.listingFee() > 0) {
            feeBridge = currencyManager.bridge(config.listingFeeCurrency());
            if (feeBridge == null || !feeBridge.available()) {
                giveBack(seller, item);
                return ListingResult.fail("上架费货币不可用");
            }
            CurrencyTransactionResult feeResult = feeBridge.withdraw(seller, BigDecimal.valueOf(config.listingFee()));
            if (!feeResult.success()) {
                giveBack(seller, item);
                return ListingResult.fail(messages.insufficientFunds());
            }
            feeCharged = BigDecimal.valueOf(config.listingFee());
        }

        // 序列化物品
        String itemData = itemSerializer.serialize(item);
        String displayName = getItemDisplayName(item);
        String category = classifyItem(item);

        // 确定上架类型
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
            redisCache.publish("LISTING_CREATED:" + listing.getId());
        }

        return ListingResult.success(listing);
    }

    /** 把物品归还给玩家，背包装不下的部分掉落在脚下（玩家在场，安全）。 */
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
            return PurchaseResult.fail("该物品已不可购买");
        }
        if (listing.getBuyNowPrice() <= 0) {
            return PurchaseResult.fail("该物品不支持一口价");
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            return PurchaseResult.fail("不能购买自己的物品");
        }

        double price = listing.getBuyNowPrice();
        String currency = listing.getCurrency();

        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return PurchaseResult.fail("货币系统不可用");
        }

        // 先抢占（状态 CAS），保证同一物品不会被并发购买 / 到期任务重复结算
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.SOLD)) {
            return PurchaseResult.fail("该物品已不可购买");
        }

        // 扣买家钱（失败则回滚抢占）
        CurrencyTransactionResult withdrawResult = bridge.withdraw(buyer, BigDecimal.valueOf(price));
        if (!withdrawResult.success()) {
            repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.SOLD, AuctionListing.ListingStatus.ACTIVE);
            return PurchaseResult.fail(messages.insufficientFunds());
        }

        // BOTH 类型若已有竞价者，一口价成交需退还其押金（安全发放，离线不丢）
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0
                && !listing.getHighestBidder().equals(buyer.getUniqueId())) {
            depositSafe(listing.getHighestBidder(), currency, listing.getCurrentBid(), "auction_outbid_refund");
        }

        // 计算税费
        double taxRate = getEffectiveTaxRate(listing.getSeller());
        double tax = price * taxRate;
        double sellerIncome = price - tax;

        // 给卖家打钱（在线即时 / 离线入待发放队列，绝不丢钱）
        depositSafe(listing.getSeller(), currency, sellerIncome, "auction_sold_income");

        // 持久化其余字段（状态已是 SOLD）
        listing.setStatus(AuctionListing.ListingStatus.SOLD);
        repository.updateListing(listing);

        // 给买家物品（在线即时 / 离线或背包满入队，绝不丢物品）
        deliverItemSafe(buyer.getUniqueId(), listing, "auction_buynow_item");
        ItemStack item = itemSerializer.deserialize(listing.getItemData());

        // 记录历史
        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), buyer.getUniqueId(),
            listing.getItemData(), listing.getItemDisplayName(),
            price, currency, tax, "BUY_NOW", System.currentTimeMillis()
        ));

        // 通知卖家（仅在线时；主线程安全）
        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.auctionSold().replace("%item%", listing.getItemDisplayName())
                    .replace("%amount%", currencyManager.format(currency, BigDecimal.valueOf(sellerIncome)))));
        }

        // Redis 广播
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            redisCache.publish("LISTING_SOLD:" + listing.getId());
        }

        return PurchaseResult.success(item, price, tax);
    }

    /**
     * 竞价。
     */
    public BidResult placeBid(Player bidder, long listingId, double amount) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return BidResult.fail("该物品已不可竞价");
        }
        if (listing.getType() == AuctionListing.ListingType.BUY_NOW) {
            return BidResult.fail("该物品不支持竞价");
        }
        if (listing.getSeller().equals(bidder.getUniqueId())) {
            return BidResult.fail("不能对自己的物品出价");
        }

        // 计算最低出价
        double currentHighest = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartingBid();
        double minIncrement = Math.max(
            currentHighest * config.minBidIncrementRatio(),
            config.minBidIncrementAbsolute()
        );
        double minBid = listing.getCurrentBid() > 0 ? currentHighest + minIncrement : listing.getStartingBid();

        if (amount < minBid) {
            return BidResult.fail("出价必须 ≥ " + currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(minBid)));
        }

        // 冻结买家资金（扣款）
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(listing.getCurrency());
        if (bridge == null || !bridge.available()) {
            return BidResult.fail("货币系统不可用");
        }
        CurrencyTransactionResult result = bridge.withdraw(bidder, BigDecimal.valueOf(amount));
        if (!result.success()) {
            return BidResult.fail(messages.insufficientFunds());
        }

        // 退还上一位最高出价者押金（安全发放：在线即时入账并通知，离线入待发放队列，绝不丢钱）
        // 注：竞价依赖客户端包已切主线程串行执行，单服内无并发覆盖问题。
        UUID previousBidder = listing.getHighestBidder();
        double previousBid = listing.getCurrentBid();
        if (previousBidder != null && previousBid > 0 && !previousBidder.equals(bidder.getUniqueId())) {
            depositSafe(previousBidder, listing.getCurrency(), previousBid, "auction_outbid_refund");
            Player prevPlayer = Bukkit.getPlayer(previousBidder);
            if (prevPlayer != null) {
                prevPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    messages.auctionOutbid()
                        .replace("%item%", listing.getItemDisplayName())
                        .replace("%amount%", currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(amount)))));
            }
        }

        // 更新 listing
        listing.setCurrentBid(amount);
        listing.setHighestBidder(bidder.getUniqueId());
        repository.updateListing(listing);

        // 记录竞价
        repository.insertBid(new AuctionBid(0, listingId, bidder.getUniqueId(), bidder.getName(), amount, System.currentTimeMillis()));

        // Redis
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            redisCache.publish("BID_PLACED:" + listingId + ":" + amount);
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

        // 抢占，避免与到期任务并发重复处理（退款 + 退物只发生一次）
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.CANCELLED)) {
            return false;
        }

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
            redisCache.publish("LISTING_CANCELLED:" + listingId);
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
            redisCache.publish("LISTING_CANCELLED:" + listingId);
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

    public int countActive() {
        return repository.countActiveListings();
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
            logger.log(Level.SEVERE, "[Market-Auction] 到期处理异常", e);
        }
    }

    private void processExpiredListing(AuctionListing listing) {
        boolean hasBidder = listing.getHighestBidder() != null && listing.getCurrentBid() > 0;
        AuctionListing.ListingStatus target = hasBidder
            ? AuctionListing.ListingStatus.SOLD
            : AuctionListing.ListingStatus.EXPIRED;

        // 抢占：仅当仍为 ACTIVE 时本次才负责结算，杜绝与购买/手动触发/上一轮任务重复结算（重复发钱发物品）
        if (!repository.compareAndSetListingStatus(listing.getId(),
                AuctionListing.ListingStatus.ACTIVE, target)) {
            return;
        }
        listing.setStatus(target);

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
        } else {
            // 无人竞价，退还物品给卖家（安全发放）
            deliverItemSafe(listing.getSeller(), listing, "auction_expired_return");
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), null,
                listing.getItemData(), listing.getItemDisplayName(),
                0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
            ));

            // 通知卖家（在线时）
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
     * 安全发放物品：收件人在线则在主线程放入背包（装不下的部分入待发放队列），
     * 离线则整笔入待发放队列，玩家上线时补发。彻底避免物品丢失。
     */
    private void deliverItemSafe(UUID target, AuctionListing listing, String reason) {
        final ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item == null) {
            logger.warning("[Market-Auction] 物品反序列化失败，已转入待发放队列 listing=" + listing.getId());
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
     * 安全发放货币：收件人在线且货币可用则在主线程入账，
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
            logger.warning("[Market-Auction] 在线入账失败，转入待发放队列: player="
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
        // 基于配置的分类规则进行分类
        for (var entry : config.categories().entrySet()) {
            var cat = entry.getValue();
            if (cat.isDefault()) continue;
            // 简易分类：检查 PDC 或按材质
            // TODO: 完善 NBT path 检查
        }
        return "other";
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    // 离线 / 背包溢出的发放统一由 deliverItemSafe / depositSafe + 待发放队列处理，
    // 不再使用旧的 depositOffline / createOfflineDeposit（离线时会丢钱）。

    // ─── 结果类 ─────────────────────────────────────────────

    public record ListingResult(boolean success, @Nullable String error, @Nullable AuctionListing listing) {
        public static ListingResult success(AuctionListing listing) { return new ListingResult(true, null, listing); }
        public static ListingResult fail(String error) { return new ListingResult(false, error, null); }
    }

    public record PurchaseResult(boolean success, @Nullable String error, @Nullable ItemStack item, double price, double tax) {
        public static PurchaseResult success(ItemStack item, double price, double tax) { return new PurchaseResult(true, null, item, price, tax); }
        public static PurchaseResult fail(String error) { return new PurchaseResult(false, error, null, 0, 0); }
    }

    public record BidResult(boolean success, @Nullable String error, double amount) {
        public static BidResult success(double amount) { return new BidResult(true, null, amount); }
        public static BidResult fail(String error) { return new BidResult(false, error, 0); }
    }
}
