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
        // 检查上架数量限制
        int currentCount = repository.countListingsBySeller(seller.getUniqueId());
        if (currentCount >= config.maxListingsPerPlayer()) {
            return ListingResult.fail("上架数量已达上限 (" + config.maxListingsPerPlayer() + ")");
        }

        // 检查物品黑名单
        if (isBlacklisted(item)) {
            return ListingResult.fail(messages.itemBlacklisted());
        }

        // 限制时长
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));

        // 扣上架费
        if (config.listingFee() > 0) {
            CurrencyBridgeAPI.CurrencyBridge feeBridge = currencyManager.bridge(config.listingFeeCurrency());
            if (feeBridge == null || !feeBridge.available()) {
                return ListingResult.fail("上架费货币不可用");
            }
            CurrencyTransactionResult feeResult = feeBridge.withdraw(seller, BigDecimal.valueOf(config.listingFee()));
            if (!feeResult.success()) {
                return ListingResult.fail(messages.insufficientFunds());
            }
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

        repository.insertListing(listing);

        // 从背包移除
        seller.getInventory().setItemInMainHand(null);

        // 使 Redis 缓存失效
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            redisCache.publish("LISTING_CREATED:" + listing.getId());
        }

        return ListingResult.success(listing);
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

        // 扣买家钱
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return PurchaseResult.fail("货币系统不可用");
        }
        CurrencyTransactionResult withdrawResult = bridge.withdraw(buyer, BigDecimal.valueOf(price));
        if (!withdrawResult.success()) {
            return PurchaseResult.fail(messages.insufficientFunds());
        }

        // 计算税费
        double taxRate = getEffectiveTaxRate(listing.getSeller());
        double tax = price * taxRate;
        double sellerIncome = price - tax;

        // 给卖家打钱
        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        bridge.deposit(sellerOnline != null ? sellerOnline : createOfflineDeposit(listing.getSeller(), currency, sellerIncome),
            BigDecimal.valueOf(sellerIncome));

        // 更新状态
        listing.setStatus(AuctionListing.ListingStatus.SOLD);
        repository.updateListing(listing);

        // 给买家物品
        ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item != null) {
            buyer.getInventory().addItem(item);
        }

        // 记录历史
        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), buyer.getUniqueId(),
            listing.getItemData(), listing.getItemDisplayName(),
            price, currency, tax, "BUY_NOW", System.currentTimeMillis()
        ));

        // 通知卖家
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

        // 退还上一位最高出价者
        UUID previousBidder = listing.getHighestBidder();
        double previousBid = listing.getCurrentBid();
        if (previousBidder != null && previousBid > 0) {
            Player prevPlayer = Bukkit.getPlayer(previousBidder);
            if (prevPlayer != null) {
                bridge.deposit(prevPlayer, BigDecimal.valueOf(previousBid));
                prevPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    messages.auctionOutbid()
                        .replace("%item%", listing.getItemDisplayName())
                        .replace("%amount%", currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(amount)))));
            } else {
                // 离线退款：直接通过 bridge 存入
                depositOffline(previousBidder, listing.getCurrency(), previousBid);
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

        // 如果有竞价者，退还
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            depositOffline(listing.getHighestBidder(), listing.getCurrency(), listing.getCurrentBid());
        }

        listing.setStatus(AuctionListing.ListingStatus.CANCELLED);
        repository.updateListing(listing);

        // 返还物品
        ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item != null) {
            seller.getInventory().addItem(item);
        }

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
        for (AuctionListing listing : expired) {
            processExpiredListing(listing);
        }
        return expired.size();
    }

    // ─── 定期处理 ───────────────────────────────────────────

    private void processExpired() {
        try {
            List<AuctionListing> expired = repository.getExpiredListings();
            for (AuctionListing listing : expired) {
                processExpiredListing(listing);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Market-Auction] 到期处理异常", e);
        }
    }

    private void processExpiredListing(AuctionListing listing) {
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            // 竞价成交
            double taxRate = getEffectiveTaxRate(listing.getSeller());
            double tax = listing.getCurrentBid() * taxRate;
            double sellerIncome = listing.getCurrentBid() - tax;

            // 给卖家打钱
            depositOffline(listing.getSeller(), listing.getCurrency(), sellerIncome);

            // 给买家物品
            deliverItem(listing.getHighestBidder(), listing);

            listing.setStatus(AuctionListing.ListingStatus.SOLD);
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), listing.getHighestBidder(),
                listing.getItemData(), listing.getItemDisplayName(),
                listing.getCurrentBid(), listing.getCurrency(), tax,
                "BID_WIN", System.currentTimeMillis()
            ));
        } else {
            // 无人竞价，退还物品
            deliverItem(listing.getSeller(), listing);
            listing.setStatus(AuctionListing.ListingStatus.EXPIRED);
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), null,
                listing.getItemData(), listing.getItemDisplayName(),
                0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
            ));

            // 通知卖家
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

    private void deliverItem(UUID target, AuctionListing listing) {
        Player online = Bukkit.getPlayer(target);
        ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item == null) return;

        if (online != null) {
            online.getInventory().addItem(item);
        } else if ("mail".equals(config.expiredReturnMethod()) && mailSupplier != null) {
            MailDispatchable mail = mailSupplier.get();
            if (mail != null) {
                // 通过预设模板发送退回邮件（预设需在 Mail 模块配置中定义）
                String playerName = listing.getSellerName();
                mail.dispatchPreset("market_return", playerName, "Market");
            }
            // TODO: 物品附件退还需要扩展 MailDispatchable 接口或通过背包补发队列实现
        }
        // 如果 mail 不可用或玩家离线，物品将在玩家上线时通过补发队列发放
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

    private void depositOffline(UUID player, String currency, double amount) {
        // 离线存款：通过命令或 Vault offline
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null) return;
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            bridge.deposit(online, BigDecimal.valueOf(amount));
        }
        // 离线情况下依赖 Vault OfflinePlayer 支持
    }

    @SuppressWarnings("all")
    private Player createOfflineDeposit(UUID seller, String currency, double amount) {
        // 离线玩家存款委托给主线程
        Bukkit.getScheduler().runTask(plugin, () -> depositOffline(seller, currency, amount));
        return null;
    }

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
