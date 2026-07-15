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
 * ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¨ÃÂ¡ÃÂÃÂ¦ÃÂ ÃÂ¸ÃÂ¥ÃÂ¿ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¡ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¡ÃÂ£ÃÂÃÂ
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
        logger.info("[Market-Auction] ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¨ÃÂ¡ÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¡ÃÂ¥ÃÂ·ÃÂ²ÃÂ¥ÃÂÃÂ¯ÃÂ¥ÃÂÃÂ¨ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ°ÃÂ¦ÃÂÃÂÃÂ¦ÃÂ£ÃÂÃÂ¦ÃÂÃÂ¥ÃÂ©ÃÂÃÂ´ÃÂ©ÃÂÃÂ: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    /**
     * ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ£ÃÂÃÂ
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
            return ListingResult.fail("ÃÂ¦ÃÂ²ÃÂ¡ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¯ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ");
        }
        // ÃÂ©ÃÂÃÂ²ÃÂ¦ÃÂ­ÃÂ¢ÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ»ÃÂ¦ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¼ÃÂÃÂ§ÃÂÃÂ¨ÃÂ¥ÃÂÃÂ«ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ½ÃÂ¿ÃÂ§ÃÂÃÂ¨ÃÂ¥ÃÂÃÂ¯ÃÂ¦ÃÂÃÂ¬ÃÂ¤ÃÂ½ÃÂÃÂ¤ÃÂ¸ÃÂºÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ
        item = item.clone();

        // ÃÂ¦ÃÂ£ÃÂÃÂ¦ÃÂÃÂ¥ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂ¶
        int currentCount = repository.countListingsBySeller(seller.getUniqueId());
        if (currentCount >= config.maxListingsPerPlayer()) {
            return ListingResult.fail("ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¨ÃÂ¾ÃÂ¾ÃÂ¤ÃÂ¸ÃÂÃÂ©ÃÂÃÂ (" + config.maxListingsPerPlayer() + ")");
        }

        // ÃÂ¦ÃÂ£ÃÂÃÂ¦ÃÂÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ©ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ
        if (isBlacklisted(item)) {
            return ListingResult.fail(messages.itemBlacklisted());
        }

        // ÃÂ¦ÃÂ ÃÂ¡ÃÂ©ÃÂªÃÂÃÂ¤ÃÂ¸ÃÂ»ÃÂ¦ÃÂÃÂÃÂ§ÃÂ¡ÃÂ®ÃÂ¥ÃÂ®ÃÂÃÂ¦ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¨ÃÂ¦ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ²ÃÂ¦ÃÂ­ÃÂ¢ÃÂ¥ÃÂ®ÃÂ¢ÃÂ¦ÃÂÃÂ·ÃÂ§ÃÂ«ÃÂ¯ÃÂ¤ÃÂ¼ÃÂªÃÂ©ÃÂÃÂ ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ / ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂ¬ÃÂ¦ÃÂ¥ÃÂ¯ÃÂ¼ÃÂ¨ÃÂÃÂ´ÃÂ¥ÃÂ¤ÃÂÃÂ¥ÃÂÃÂ¶ÃÂ¯ÃÂ¼ÃÂ
        ItemStack inHand = sourceSlot >= 0
            ? seller.getInventory().getItem(sourceSlot)
            : seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()
                || !inHand.isSimilar(item) || inHand.getAmount() < item.getAmount()) {
            return ListingResult.fail("ÃÂ¨ÃÂ¯ÃÂ·ÃÂ¦ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¨ÃÂ¦ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ");
        }

        // ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ¶ÃÂ©ÃÂÃÂ¿
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));

        // ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ£ÃÂ©ÃÂÃÂ¤ÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ ÃÂ¦ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂ"ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂºÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ£ÃÂ©ÃÂÃÂ¤"ÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂ¼ÃÂÃÂ¥ÃÂ¸ÃÂ¸ÃÂ¦ÃÂÃÂ¶ÃÂ©ÃÂÃÂ ÃÂ¦ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ¥ÃÂÃÂ¶
        if (inHand.getAmount() == item.getAmount()) {
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), null);
        } else {
            inHand.setAmount(inHand.getAmount() - item.getAmount());
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), inHand);
        }

        // ÃÂ¦ÃÂÃÂ£ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¨ÃÂ´ÃÂ¹
        BigDecimal feeCharged = null;
        CurrencyBridgeAPI.CurrencyBridge feeBridge = null;
        if (config.listingFee() > 0) {
            feeBridge = currencyManager.bridge(config.listingFeeCurrency());
            if (feeBridge == null || !feeBridge.available()) {
                giveBack(seller, item);
                return ListingResult.fail("ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¨ÃÂ´ÃÂ¹ÃÂ¨ÃÂ´ÃÂ§ÃÂ¥ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ§ÃÂÃÂ¨");
            }
            CurrencyTransactionResult feeResult = feeBridge.withdraw(seller, BigDecimal.valueOf(config.listingFee()));
            if (!feeResult.success()) {
                giveBack(seller, item);
                return ListingResult.fail(messages.insufficientFunds());
            }
            feeCharged = BigDecimal.valueOf(config.listingFee());
        }

        // ÃÂ¥ÃÂºÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ
        String itemData = itemSerializer.serialize(item);
        String displayName = getItemDisplayName(item);
        String category = classifyItem(item);

        // ÃÂ§ÃÂ¡ÃÂ®ÃÂ¥ÃÂ®ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂ±ÃÂ»ÃÂ¥ÃÂÃÂ
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

        // ÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂºÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥ÃÂ¥ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ´ÃÂ¹ + ÃÂ¥ÃÂ½ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ¿ÃÂÃÂ¨ÃÂ¯ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢
        if (!repository.insertListing(listing)) {
            if (feeCharged != null && feeBridge != null) {
                feeBridge.deposit(seller, feeCharged);
            }
            giveBack(seller, item);
            return ListingResult.fail("ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥ÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂ¯ÃÂ·ÃÂ§ÃÂ¨ÃÂÃÂ¥ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ¯ÃÂ");
        }

        // ÃÂ¤ÃÂ½ÃÂ¿ Redis ÃÂ§ÃÂ¼ÃÂÃÂ¥ÃÂ­ÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¦ÃÂÃÂ
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_CREATED:" + listing.getId());
        }

        return ListingResult.success(listing);
    }

    /** ÃÂ¦ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ½ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂ»ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¨ÃÂ£ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂÃÂÃÂ©ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¨ÃÂÃÂ½ÃÂ¥ÃÂÃÂ¨ÃÂ¨ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂºÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¯ÃÂ¼ÃÂÃÂ£ÃÂÃÂ */
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
     * ÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ£ÃÂ¤ÃÂ»ÃÂ·ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°ÃÂ£ÃÂÃÂ
     */
    public PurchaseResult buyNow(Player buyer, long listingId) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return PurchaseResult.fail("ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°");
        }
        if (listing.getBuyNowPrice() <= 0) {
            return PurchaseResult.fail("ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¯ÃÂ¦ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ£ÃÂ¤ÃÂ»ÃÂ·");
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            return PurchaseResult.fail("ÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂÃÂ½ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°ÃÂ¨ÃÂÃÂªÃÂ¥ÃÂ·ÃÂ±ÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ");
        }

        double price = listing.getBuyNowPrice();
        String currency = listing.getCurrency();

        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return PurchaseResult.fail("ÃÂ¨ÃÂ´ÃÂ§ÃÂ¥ÃÂ¸ÃÂÃÂ§ÃÂ³ÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ§ÃÂÃÂ¨");
        }

        // ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¢ÃÂ¥ÃÂÃÂ ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ CASÃÂ¯ÃÂ¼ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ¿ÃÂÃÂ¨ÃÂ¯ÃÂÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¼ÃÂÃÂ¨ÃÂ¢ÃÂ«ÃÂ¥ÃÂ¹ÃÂ¶ÃÂ¥ÃÂÃÂÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ° / ÃÂ¥ÃÂÃÂ°ÃÂ¦ÃÂÃÂÃÂ¤ÃÂ»ÃÂ»ÃÂ¥ÃÂÃÂ¡ÃÂ©ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂ»ÃÂÃÂ§ÃÂ®ÃÂ
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.SOLD)) {
            return PurchaseResult.fail("ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°");
        }

        // ÃÂ¦ÃÂÃÂ£ÃÂ¤ÃÂ¹ÃÂ°ÃÂ¥ÃÂ®ÃÂ¶ÃÂ©ÃÂÃÂ±ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂ»ÃÂÃÂ¦ÃÂÃÂ¢ÃÂ¥ÃÂÃÂ ÃÂ¯ÃÂ¼ÃÂ
        CurrencyTransactionResult withdrawResult = bridge.withdraw(buyer, BigDecimal.valueOf(price));
        if (!withdrawResult.success()) {
            repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.SOLD, AuctionListing.ListingStatus.ACTIVE);
            return PurchaseResult.fail(messages.insufficientFunds());
        }

        // BOTH ÃÂ§ÃÂ±ÃÂ»ÃÂ¥ÃÂÃÂÃÂ¨ÃÂÃÂ¥ÃÂ¥ÃÂ·ÃÂ²ÃÂ¦ÃÂÃÂÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¨ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ£ÃÂ¤ÃÂ»ÃÂ·ÃÂ¦ÃÂÃÂÃÂ¤ÃÂºÃÂ¤ÃÂ©ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂÃÂ¥ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ¼ÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ¯ÃÂ¼ÃÂ
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0
                && !listing.getHighestBidder().equals(buyer.getUniqueId())) {
            depositSafe(listing.getHighestBidder(), currency, listing.getCurrentBid(), "auction_outbid_refund");
        }

        // ÃÂ¨ÃÂ®ÃÂ¡ÃÂ§ÃÂ®ÃÂÃÂ§ÃÂ¨ÃÂÃÂ¨ÃÂ´ÃÂ¹
        double taxRate = getEffectiveTaxRate(listing.getSeller());
        double tax = price * taxRate;
        double sellerIncome = price - tax;

        // ÃÂ§ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ®ÃÂ¶ÃÂ¦ÃÂÃÂÃÂ©ÃÂÃÂ±ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ³ÃÂ¦ÃÂÃÂ¶ / ÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ©ÃÂÃÂ±ÃÂ¯ÃÂ¼ÃÂ
        depositSafe(listing.getSeller(), currency, sellerIncome, "auction_sold_income");

        // ÃÂ¦ÃÂÃÂÃÂ¤ÃÂ¹ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¶ÃÂ¤ÃÂ½ÃÂÃÂ¥ÃÂ­ÃÂÃÂ¦ÃÂ®ÃÂµÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ¶ÃÂ¦ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¦ÃÂÃÂ¯ SOLDÃÂ¯ÃÂ¼ÃÂ
        listing.setStatus(AuctionListing.ListingStatus.SOLD);
        repository.updateListing(listing);

        // ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¹ÃÂ°ÃÂ¥ÃÂ®ÃÂ¶ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ³ÃÂ¦ÃÂÃÂ¶ / ÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂ»ÃÂ¡ÃÂ¥ÃÂÃÂ¥ÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ
        deliverItemSafe(buyer.getUniqueId(), listing, "auction_buynow_item");
        ItemStack item = itemSerializer.deserialize(listing.getItemData());

        // ÃÂ¨ÃÂ®ÃÂ°ÃÂ¥ÃÂ½ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ²
        repository.insertHistory(new AuctionHistory(
            0, listing.getId(), listing.getSeller(), buyer.getUniqueId(),
            listing.getItemData(), listing.getItemDisplayName(),
            price, currency, tax, "BUY_NOW", System.currentTimeMillis()
        ));

        // ÃÂ©ÃÂÃÂÃÂ§ÃÂÃÂ¥ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ®ÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ»ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¯ÃÂ¼ÃÂ
        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.auctionSold().replace("%item%", listing.getItemDisplayName())
                    .replace("%amount%", currencyManager.format(currency, BigDecimal.valueOf(sellerIncome)))));
        }

        // Redis ÃÂ¥ÃÂ¹ÃÂ¿ÃÂ¦ÃÂÃÂ­
        if (redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("LISTING_SOLD:" + listing.getId());
        }

        return PurchaseResult.success(item, price, tax, currency);
    }

    /**
     * ÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ£ÃÂÃÂ
     */
    public BidResult placeBid(Player bidder, long listingId, double amount) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return BidResult.fail("ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·");
        }
        if (listing.getType() == AuctionListing.ListingType.BUY_NOW) {
            return BidResult.fail("ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¯ÃÂ¦ÃÂÃÂÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·");
        }
        if (listing.getSeller().equals(bidder.getUniqueId())) {
            return BidResult.fail("ÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂÃÂ½ÃÂ¥ÃÂ¯ÃÂ¹ÃÂ¨ÃÂÃÂªÃÂ¥ÃÂ·ÃÂ±ÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂºÃÂ¤ÃÂ»ÃÂ·");
        }

        // ÃÂ¨ÃÂ®ÃÂ¡ÃÂ§ÃÂ®ÃÂÃÂ¦ÃÂÃÂÃÂ¤ÃÂ½ÃÂÃÂ¥ÃÂÃÂºÃÂ¤ÃÂ»ÃÂ·
        double currentHighest = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartingBid();
        double minIncrement = Math.max(
            currentHighest * config.minBidIncrementRatio(),
            config.minBidIncrementAbsolute()
        );
        double minBid = listing.getCurrentBid() > 0 ? currentHighest + minIncrement : listing.getStartingBid();

        if (amount < minBid) {
            return BidResult.fail("ÃÂ¥ÃÂÃÂºÃÂ¤ÃÂ»ÃÂ·ÃÂ¥ÃÂ¿ÃÂÃÂ©ÃÂ¡ÃÂ» ÃÂ¢ÃÂÃÂ¥ " + currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(minBid)));
        }

        // ÃÂ¥ÃÂÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¹ÃÂ°ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¨ÃÂµÃÂÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂ£ÃÂ¦ÃÂ¬ÃÂ¾ÃÂ¯ÃÂ¼ÃÂ
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(listing.getCurrency());
        if (bridge == null || !bridge.available()) {
            return BidResult.fail("ÃÂ¨ÃÂ´ÃÂ§ÃÂ¥ÃÂ¸ÃÂÃÂ§ÃÂ³ÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ§ÃÂÃÂ¨");
        }
        CurrencyTransactionResult result = bridge.withdraw(bidder, BigDecimal.valueOf(amount));
        if (!result.success()) {
            return BidResult.fail(messages.insufficientFunds());
        }

        // ÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ½ÃÂÃÂ¦ÃÂÃÂÃÂ©ÃÂ«ÃÂÃÂ¥ÃÂÃÂºÃÂ¤ÃÂ»ÃÂ·ÃÂ¨ÃÂÃÂÃÂ¦ÃÂÃÂ¼ÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ³ÃÂ¦ÃÂÃÂ¶ÃÂ¥ÃÂÃÂ¥ÃÂ¨ÃÂ´ÃÂ¦ÃÂ¥ÃÂ¹ÃÂ¶ÃÂ©ÃÂÃÂÃÂ§ÃÂÃÂ¥ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ©ÃÂÃÂ±ÃÂ¯ÃÂ¼ÃÂ
        // ÃÂ¦ÃÂ³ÃÂ¨ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¤ÃÂ¾ÃÂÃÂ¨ÃÂµÃÂÃÂ¥ÃÂ®ÃÂ¢ÃÂ¦ÃÂÃÂ·ÃÂ§ÃÂ«ÃÂ¯ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¤ÃÂ¸ÃÂ²ÃÂ¨ÃÂ¡ÃÂÃÂ¦ÃÂÃÂ§ÃÂ¨ÃÂ¡ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ ÃÂ¥ÃÂ¹ÃÂ¶ÃÂ¥ÃÂÃÂÃÂ¨ÃÂ¦ÃÂÃÂ§ÃÂÃÂÃÂ©ÃÂÃÂ®ÃÂ©ÃÂ¢ÃÂÃÂ£ÃÂÃÂ
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
     * ÃÂ¥ÃÂÃÂÃÂ¦ÃÂ¶ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ£ÃÂÃÂ
     */
    public boolean cancelListing(Player seller, long listingId) {
        AuctionListing listing = repository.getListing(listingId);
        if (listing == null) return false;
        if (!listing.getSeller().equals(seller.getUniqueId())) return false;
        if (listing.getStatus() != AuctionListing.ListingStatus.ACTIVE) return false;

        // ÃÂ¦ÃÂÃÂ¢ÃÂ¥ÃÂÃÂ ÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ°ÃÂ¦ÃÂÃÂÃÂ¤ÃÂ»ÃÂ»ÃÂ¥ÃÂÃÂ¡ÃÂ¥ÃÂ¹ÃÂ¶ÃÂ¥ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂÃÂ¦ÃÂ¬ÃÂ¾ + ÃÂ©ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂªÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂ¬ÃÂ¡ÃÂ¯ÃÂ¼ÃÂ
        if (!repository.compareAndSetListingStatus(listingId,
                AuctionListing.ListingStatus.ACTIVE, AuctionListing.ListingStatus.CANCELLED)) {
            return false;
        }

        // ÃÂ¥ÃÂ¦ÃÂÃÂ¦ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¨ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂÃÂ¦ÃÂÃÂ¼ÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ¯ÃÂ¼ÃÂ
        if (listing.getHighestBidder() != null && listing.getCurrentBid() > 0) {
            depositSafe(listing.getHighestBidder(), listing.getCurrency(), listing.getCurrentBid(), "auction_cancel_refund");
        }

        listing.setStatus(AuctionListing.ListingStatus.CANCELLED);
        repository.updateListing(listing);

        // ÃÂ¨ÃÂ¿ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂ»ÃÂ¡ÃÂ¦ÃÂÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ
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
     * ÃÂ§ÃÂ®ÃÂ¡ÃÂ§ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¼ÃÂºÃÂ¥ÃÂÃÂ¶ÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¦ÃÂÃÂ¼ÃÂ©ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ²ÃÂ£ÃÂÃÂ
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
     * ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¢ÃÂ¦ÃÂÃÂ¶ÃÂ¨ÃÂÃÂÃÂ£ÃÂÃÂ
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
     * ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¨ÃÂ§ÃÂ¦ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ°ÃÂ¦ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂÃÂ¦ÃÂÃÂ¡ÃÂ§ÃÂÃÂ®ÃÂ¦ÃÂÃÂ°ÃÂ£ÃÂÃÂ
     */
    public int triggerExpiredProcessing() {
        List<AuctionListing> expired = repository.getExpiredListings();
        int count = expired.size();
        for (AuctionListing listing : expired) {
            Bukkit.getScheduler().runTask(plugin, () -> processExpiredListing(listing));
        }
        return count;
    }

    // ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ ÃÂ¥ÃÂ®ÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂ ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ

    private void processExpired() {
        try {
            List<AuctionListing> expired = repository.getExpiredListings();
            for (AuctionListing listing : expired) {
                Bukkit.getScheduler().runTask(plugin, () -> processExpiredListing(listing));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Market-Auction] ÃÂ¥ÃÂÃÂ°ÃÂ¦ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂÃÂ¥ÃÂ¼ÃÂÃÂ¥ÃÂ¸ÃÂ¸", e);
        }
    }

    private void processExpiredListing(AuctionListing listing) {
        boolean hasBidder = listing.getHighestBidder() != null && listing.getCurrentBid() > 0;
        AuctionListing.ListingStatus target = hasBidder
            ? AuctionListing.ListingStatus.SOLD
            : AuctionListing.ListingStatus.EXPIRED;

        // ÃÂ¦ÃÂÃÂ¢ÃÂ¥ÃÂÃÂ ÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ»ÃÂÃÂ¥ÃÂ½ÃÂÃÂ¤ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂº ACTIVE ÃÂ¦ÃÂÃÂ¶ÃÂ¦ÃÂÃÂ¬ÃÂ¦ÃÂ¬ÃÂ¡ÃÂ¦ÃÂÃÂÃÂ¨ÃÂ´ÃÂÃÂ¨ÃÂ´ÃÂ£ÃÂ§ÃÂ»ÃÂÃÂ§ÃÂ®ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°/ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¨ÃÂ§ÃÂ¦ÃÂ¥ÃÂÃÂ/ÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂ½ÃÂ®ÃÂ¤ÃÂ»ÃÂ»ÃÂ¥ÃÂÃÂ¡ÃÂ©ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂ»ÃÂÃÂ§ÃÂ®ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ¥ÃÂÃÂÃÂ©ÃÂÃÂ±ÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ
        if (!repository.compareAndSetListingStatus(listing.getId(),
                AuctionListing.ListingStatus.ACTIVE, target)) {
            return;
        }
        listing.setStatus(target);

        if (hasBidder) {
            // ÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¦ÃÂÃÂÃÂ¤ÃÂºÃÂ¤
            double taxRate = getEffectiveTaxRate(listing.getSeller());
            double tax = listing.getCurrentBid() * taxRate;
            double sellerIncome = listing.getCurrentBid() - tax;

            // ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ®ÃÂ¶ÃÂ¦ÃÂÃÂ¶ÃÂ¦ÃÂ¬ÃÂ¾ + ÃÂ¤ÃÂ¹ÃÂ°ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¥ÃÂ¾ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ¯ÃÂ¼ÃÂ
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
            // ÃÂ¦ÃÂÃÂ ÃÂ¤ÃÂºÃÂºÃÂ§ÃÂ«ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ§ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ®ÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¯ÃÂ¼ÃÂ
            deliverItemSafe(listing.getSeller(), listing, "auction_expired_return");
            repository.updateListing(listing);

            repository.insertHistory(new AuctionHistory(
                0, listing.getId(), listing.getSeller(), null,
                listing.getItemData(), listing.getItemDisplayName(),
                0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
            ));

            // ÃÂ©ÃÂÃÂÃÂ§ÃÂÃÂ¥ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ®ÃÂ¶ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂ¶ÃÂ¯ÃÂ¼ÃÂ
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

    /** ÃÂ¥ÃÂÃÂ¨ÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¦ÃÂÃÂ§ÃÂ¨ÃÂ¡ÃÂÃÂ¤ÃÂ»ÃÂ»ÃÂ¥ÃÂÃÂ¡ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¥ÃÂÃÂ¨ÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ´ÃÂ¦ÃÂÃÂ¥ÃÂ¦ÃÂÃÂ§ÃÂ¨ÃÂ¡ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ£ÃÂÃÂ */
    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * ÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¤ÃÂ»ÃÂ¶ÃÂ¤ÃÂºÃÂºÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¥ÃÂÃÂ¥ÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂ£ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂÃÂÃÂ©ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¯ÃÂ¼ÃÂ
     * ÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ´ÃÂ§ÃÂ¬ÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂ¶ÃÂ¨ÃÂ¡ÃÂ¥ÃÂ¥ÃÂÃÂÃÂ£ÃÂÃÂÃÂ¥ÃÂ½ÃÂ»ÃÂ¥ÃÂºÃÂÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ¥ÃÂ¤ÃÂ±ÃÂ£ÃÂÃÂ
     */
    private void deliverItemSafe(UUID target, AuctionListing listing, String reason) {
        final ItemStack item = itemSerializer.deserialize(listing.getItemData());
        if (item == null) {
            logger.warning("[Market-Auction] ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¨ÃÂ½ÃÂ¬ÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂ listing=" + listing.getId());
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
     * ÃÂ¥ÃÂ®ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ¨ÃÂ´ÃÂ§ÃÂ¥ÃÂ¸ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂ¶ÃÂ¤ÃÂ»ÃÂ¶ÃÂ¤ÃÂºÃÂºÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂ´ÃÂ§ÃÂ¥ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¯ÃÂ§ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¨ÃÂ¤ÃÂ¸ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ§ÃÂ¨ÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¨ÃÂ´ÃÂ¦ÃÂ¯ÃÂ¼ÃÂ
     * ÃÂ¥ÃÂÃÂ¦ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂ®ÃÂ¶ÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂ¶ÃÂ¨ÃÂ¡ÃÂ¥ÃÂ¥ÃÂÃÂÃÂ£ÃÂÃÂÃÂ¥ÃÂ½ÃÂ»ÃÂ¥ÃÂºÃÂÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¨ÃÂ´ÃÂ§ÃÂ¦ÃÂ¬ÃÂ¾ÃÂ¤ÃÂ¸ÃÂ¢ÃÂ¥ÃÂ¤ÃÂ±ÃÂ£ÃÂÃÂ
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
            logger.warning("[Market-Auction] ÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂºÃÂ¿ÃÂ¥ÃÂÃÂ¥ÃÂ¨ÃÂ´ÃÂ¦ÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥ÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂ½ÃÂ¬ÃÂ¥ÃÂÃÂ¥ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂ: player="
                + online.getName() + " currency=" + currency + " amount=" + amount);
        }
        repository.addPendingCurrency(target, currency, amount, reason);
    }

    // ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ ÃÂ¥ÃÂ·ÃÂ¥ÃÂ¥ÃÂÃÂ·ÃÂ¦ÃÂÃÂ¹ÃÂ¦ÃÂ³ÃÂ ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ

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

    // ÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ / ÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂºÃÂ¢ÃÂ¥ÃÂÃÂºÃÂ§ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂÃÂ± deliverItemSafe / depositSafe + ÃÂ¥ÃÂ¾ÃÂÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂ¾ÃÂ©ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¤ÃÂÃÂ§ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ
    // ÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂÃÂ¤ÃÂ½ÃÂ¿ÃÂ§ÃÂÃÂ¨ÃÂ¦ÃÂÃÂ§ÃÂ§ÃÂÃÂ depositOffline / createOfflineDepositÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂ¦ÃÂ»ÃÂ§ÃÂºÃÂ¿ÃÂ¦ÃÂÃÂ¶ÃÂ¤ÃÂ¼ÃÂÃÂ¤ÃÂ¸ÃÂ¢ÃÂ©ÃÂÃÂ±ÃÂ¯ÃÂ¼ÃÂÃÂ£ÃÂÃÂ

    private void publishCrossServer(String message) {
        if (crossServerPublisher != null && message != null && !message.isBlank()) {
            crossServerPublisher.accept(message);
        }
    }

    // ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ ÃÂ§ÃÂ»ÃÂÃÂ¦ÃÂÃÂÃÂ§ÃÂ±ÃÂ» ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ

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
