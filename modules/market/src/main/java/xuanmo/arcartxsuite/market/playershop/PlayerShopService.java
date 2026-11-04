package xuanmo.arcartxsuite.market.playershop;

import java.math.BigDecimal;
import java.util.List;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;

import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.PlayerShopConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 玩家商店核心业务服务。
 * <p>
 * 与拍卖行不同，玩家商店仅支持固定价格直接购买，不支持竞价。
 * 复用 {@link AuctionItemSerializer}、{@link CurrencyBridgeAPI}、
 * {@link xuanmo.arcartxsuite.api.item.ItemRewardDispatcher} 等底层设施。
 */
public class PlayerShopService {

    private static final int BACKPACK_SLOTS = 36;
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    private static final ConcurrentHashMap<String, String[]> PINYIN_CACHE = new ConcurrentHashMap<>();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private final JavaPlugin plugin;
    private final PlayerShopConfiguration config;
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
    private BukkitTask schedulerTask;

    public PlayerShopService(JavaPlugin plugin, PlayerShopConfiguration config, MessagesConfiguration messages,
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
        logger.info("[PlayerShop] 玩家商店服务已启动，到期检查间隔: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    // ─── 上架 ───────────────────────────────────────────────

    public ListingResult createListing(Player seller, int slot, double price, String currency,
                                        long durationSeconds, String message) {
        if (!config.enabled()) {
            return ListingResult.fail("玩家商店未启用");
        }
        if (slot < 0 || slot >= BACKPACK_SLOTS) {
            return ListingResult.fail("invalid item slot");
        }
        ItemStack item = seller.getInventory().getItem(slot);
        return createListingInternal(seller, item, price, currency, durationSeconds, message, slot);
    }

    private ListingResult createListingInternal(Player seller, ItemStack item, double price,
                                                 String currency, long durationSeconds,
                                                 String message, int sourceSlot) {
        if (item == null || item.getType().isAir()) {
            return ListingResult.fail("没有可上架的物品");
        }
        item = item.clone();

        // 检查上架数量限制
        int currentCount = repository.countListingsByPlayerShopSeller(seller.getUniqueId());
        if (currentCount >= config.maxListingsPerPlayer()) {
            return ListingResult.fail("上架数量已达上限 (" + config.maxListingsPerPlayer() + ")");
        }

        // 检查物品黑名单
        if (isBlacklisted(item)) {
            return ListingResult.fail(messages.itemBlacklisted());
        }

        // 校验物品确实存在
        ItemStack inHand = sourceSlot >= 0
            ? seller.getInventory().getItem(sourceSlot)
            : seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()
                || !inHand.isSimilar(item) || inHand.getAmount() < item.getAmount()) {
            return ListingResult.fail("请手持要上架的物品");
        }

        // 价格校验
        if (price < config.minPrice() || price > config.maxPrice()) {
            return ListingResult.fail("价格超出允许范围");
        }

        // 时长限制
        long duration = Math.max(config.minDurationSeconds(), Math.min(config.maxDurationSeconds(), durationSeconds));
        if (message == null) message = "";
        if (message.length() > config.messageMaxLength()) {
            return ListingResult.fail("留言过长");
        }

        // 先扣除背包物品
        if (inHand.getAmount() == item.getAmount()) {
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), null);
        } else {
            inHand.setAmount(inHand.getAmount() - item.getAmount());
            seller.getInventory().setItem(sourceSlot >= 0 ? sourceSlot : heldSlot(seller), inHand);
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
        String[] pinyinTokens = toPinyinTokens(displayName);
        String pinyin = pinyinTokens[0];
        String initials = pinyinTokens[1];

        long now = System.currentTimeMillis();
        PlayerShopListing listing = new PlayerShopListing(
            0, seller.getUniqueId(), seller.getName(), itemData, displayName,
            pinyin, initials, message, price, currency,
            PlayerShopListing.ListingStatus.ACTIVE, now, now + duration * 1000L
        );

        if (!repository.insertPlayerShopListing(listing)) {
            if (feeCharged != null && feeBridge != null) {
                feeBridge.deposit(seller, feeCharged);
            }
            giveBack(seller, item);
            return ListingResult.fail("上架失败，请稍后重试");
        }

        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
            publishCrossServer("PSHOP_LISTING_CREATED:" + listing.getId());
        }

        return ListingResult.success(listing);
    }

    // ─── 购买 ───────────────────────────────────────────────

    public PurchaseResult purchase(Player buyer, long listingId) {
        if (!config.enabled()) {
            return PurchaseResult.fail("玩家商店未启用");
        }
        PlayerShopListing listing = repository.getPlayerShopListing(listingId);
        if (listing == null || !listing.isActive()) {
            return PurchaseResult.fail("该商品已不可购买");
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            return PurchaseResult.fail("不能购买自己的商品");
        }

        double price = listing.getPrice();
        String currency = listing.getCurrency();

        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return PurchaseResult.fail("货币系统不可用");
        }

        // CAS 抢占
        if (!repository.compareAndSetPlayerShopListingStatus(listingId,
                PlayerShopListing.ListingStatus.ACTIVE, PlayerShopListing.ListingStatus.SOLD)) {
            return PurchaseResult.fail("该商品已不可购买");
        }

        // 扣买家钱
        CurrencyTransactionResult withdrawResult = bridge.withdraw(buyer, BigDecimal.valueOf(price));
        if (!withdrawResult.success()) {
            repository.compareAndSetPlayerShopListingStatus(listingId,
                PlayerShopListing.ListingStatus.SOLD, PlayerShopListing.ListingStatus.ACTIVE);
            return PurchaseResult.fail(messages.insufficientFunds());
        }

        // 计算税费
        double taxRate = getEffectiveTaxRate(listing.getSeller());
        double tax = price * taxRate;
        double sellerIncome = price - tax;

        // 给卖家打钱
        depositSafe(listing.getSeller(), currency, sellerIncome, "pshop_sold_income");

        // 持久化状态
        listing.setStatus(PlayerShopListing.ListingStatus.SOLD);
        repository.updatePlayerShopListing(listing);

        // 给买家物品
        deliverItemSafe(buyer.getUniqueId(), listing, "pshop_purchase_item");
        ItemStack item = itemSerializer.deserialize(listing.getItemData());

        // 记录历史
        repository.insertPlayerShopHistory(new MarketRepository.PlayerShopHistory(
            0, listing.getId(), listing.getSeller(), buyer.getUniqueId(),
            listing.getItemData(), listing.getItemDisplayName(),
            price, currency, tax, "PURCHASE", System.currentTimeMillis()
        ));

        // 通知卖家
        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.playerShopSold().replace("{item}", listing.getItemDisplayName())
                    .replace("{amount}", currencyManager.format(currency, BigDecimal.valueOf(sellerIncome)))));
        }

        // 跨服广播
        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
            publishCrossServer("PSHOP_LISTING_SOLD:" + listing.getId());
        }

        return PurchaseResult.success(item, price, tax, currency);
    }

    // ─── 下架 ───────────────────────────────────────────────

    public boolean cancelListing(Player seller, long listingId) {
        PlayerShopListing listing = repository.getPlayerShopListing(listingId);
        if (listing == null || listing.getSeller() == null
                || !listing.getSeller().equals(seller.getUniqueId())) {
            return false;
        }
        if (!repository.compareAndSetPlayerShopListingStatus(listingId,
                PlayerShopListing.ListingStatus.ACTIVE, PlayerShopListing.ListingStatus.CANCELLED)) {
            return false;
        }
        listing.setStatus(PlayerShopListing.ListingStatus.CANCELLED);
        repository.updatePlayerShopListing(listing);
        deliverItemSafe(seller.getUniqueId(), listing, "pshop_cancel_return");

        repository.insertPlayerShopHistory(new MarketRepository.PlayerShopHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "CANCELLED", System.currentTimeMillis()
        ));

        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
            publishCrossServer("PSHOP_LISTING_CANCELLED:" + listing.getId());
        }
        return true;
    }

    public boolean adminForceCancel(long listingId) {
        PlayerShopListing listing = repository.getPlayerShopListing(listingId);
        if (listing == null) return false;
        if (!repository.compareAndSetPlayerShopListingStatus(listingId,
                PlayerShopListing.ListingStatus.ACTIVE, PlayerShopListing.ListingStatus.CANCELLED)) {
            return false;
        }
        listing.setStatus(PlayerShopListing.ListingStatus.CANCELLED);
        repository.updatePlayerShopListing(listing);
        deliverItemSafe(listing.getSeller(), listing, "pshop_admin_cancel_return");

        repository.insertPlayerShopHistory(new MarketRepository.PlayerShopHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "CANCELLED", System.currentTimeMillis()
        ));

        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
            publishCrossServer("PSHOP_LISTING_CANCELLED:" + listing.getId());
        }
        return true;
    }

    // ─── 过期处理 ───────────────────────────────────────────

    public int triggerExpiredProcessing() {
        if (!config.enabled()) return 0;
        List<PlayerShopListing> expired = repository.getExpiredPlayerShopListings();
        for (PlayerShopListing listing : expired) {
            processExpiredListing(listing);
        }
        return expired.size();
    }

    private void processExpired() {
        try {
            triggerExpiredProcessing();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[PlayerShop] 过期处理异常", e);
        }
    }

    private void processExpiredListing(PlayerShopListing listing) {
        if (!repository.compareAndSetPlayerShopListingStatus(listing.getId(),
                PlayerShopListing.ListingStatus.ACTIVE, PlayerShopListing.ListingStatus.EXPIRED)) {
            return;
        }
        listing.setStatus(PlayerShopListing.ListingStatus.EXPIRED);
        repository.updatePlayerShopListing(listing);
        deliverItemSafe(listing.getSeller(), listing, "pshop_expired_return");

        repository.insertPlayerShopHistory(new MarketRepository.PlayerShopHistory(
            0, listing.getId(), listing.getSeller(), null,
            listing.getItemData(), listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "EXPIRED", System.currentTimeMillis()
        ));

        Player sellerOnline = Bukkit.getPlayer(listing.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.playerShopExpired().replace("{item}", listing.getItemDisplayName())));
        }

        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
        }
    }

    // ─── 查询 ───────────────────────────────────────────────

    public List<PlayerShopListing> getActiveListings(int page, int pageSize) {
        int offset = page * pageSize;
        return repository.getActivePlayerShopListings(offset, pageSize);
    }

    public List<PlayerShopListing> getListingsBySeller(UUID seller, int page, int pageSize) {
        int offset = page * pageSize;
        return repository.getActivePlayerShopListingsBySeller(seller, offset, pageSize);
    }

    public List<PlayerShopListing> searchListings(String keyword, int page, int pageSize) {
        int offset = page * pageSize;
        return repository.searchPlayerShopListings(keyword, offset, pageSize);
    }

    public List<MarketRepository.PlayerShopSellerInfo> getSellers(int page, int pageSize) {
        int offset = page * pageSize;
        return repository.getPlayerShopSellers(offset, pageSize);
    }

    public int countActive() {
        return repository.countActivePlayerShopListings();
    }

    public int countBySeller(UUID seller) {
        return repository.countActivePlayerShopListingsBySeller(seller);
    }

    public int countSearch(String keyword) {
        return repository.countSearchPlayerShopListings(keyword);
    }

    public int countSellers() {
        return repository.countPlayerShopSellers();
    }

    // ─── 工具方法（复用拍卖行模式）───────────────────────────

    private int heldSlot(Player player) {
        return player.getInventory().getHeldItemSlot();
    }

    private void giveBack(Player player, ItemStack item) {
        xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions options =
            xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions.builder("market")
                .sourceDetail("pshop-giveback")
                .overflowMailSubject("玩家商店物品归还")
                .overflowMailBody("您的背包已满，下架/过期物品通过邮件归还。")
                .overflowMailSenderName("全球市场")
                .build();
        itemRewardDispatcher.dispatchItem(player, item, options);
    }

    private void deliverItemSafe(UUID target, PlayerShopListing listing, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final ItemStack item = itemSerializer.deserialize(listing.getItemData());
            if (item == null) {
                logger.warning("[PlayerShop] 物品反序列化失败，已转入待发放队列 listing=" + listing.getId());
                repository.addPendingItem(target, listing.getItemData(), reason);
                return;
            }
            Player online = Bukkit.getPlayer(target);
            if (online != null && online.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions options =
                        xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions.builder("market")
                            .sourceDetail("pshop-settlement:" + listing.getId())
                            .overflowMailSubject("玩家商店结算物品")
                            .overflowMailBody("您购买/退回的物品已通过邮件发放，请查收。")
                            .overflowMailSenderName("全球市场")
                            .build();
                    xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchResult result =
                        itemRewardDispatcher.dispatchItem(online, item, options);
                    if (!result.success()) {
                        repository.addPendingItem(target, listing.getItemData(), reason);
                    }
                });
            } else {
                repository.addPendingItem(target, listing.getItemData(), reason);
            }
        });
    }

    private void depositSafe(UUID target, String currency, double amount, String reason) {
        if (amount <= 0) return;
        Player online = Bukkit.getPlayer(target);
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (online != null && online.isOnline() && bridge != null && bridge.available()) {
            CurrencyTransactionResult[] resultHolder = new CurrencyTransactionResult[1];
            runOnMain(() -> resultHolder[0] = bridge.deposit(online, BigDecimal.valueOf(amount)));
            if (resultHolder[0] != null && resultHolder[0].success()) {
                return;
            }
            logger.warning("[PlayerShop] 在线入账失败，转入待发放队列: player="
                + online.getName() + " currency=" + currency + " amount=" + amount);
        }
        repository.addPendingCurrency(target, currency, amount, reason);
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private double getEffectiveTaxRate(UUID seller) {
        Player player = Bukkit.getPlayer(seller);
        if (player != null) {
            return getEffectiveTaxRateOnline(player);
        }
        return getEffectiveTaxRateOffline(seller);
    }

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

    private double getEffectiveTaxRateOffline(UUID seller) {
        if (config.taxDiscount().isEmpty()) {
            return config.transactionTaxRate();
        }
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.UserManager userManager = luckPerms.getUserManager();
            net.luckperms.api.model.user.User cached = userManager.getUser(seller);
            net.luckperms.api.model.user.User user = cached != null
                ? cached : userManager.loadUser(seller).join();
            if (user == null) {
                return config.transactionTaxRate();
            }
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

    private String getItemDisplayName(ItemStack item) {
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(item);
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    private static String[] toPinyinTokens(String rawValue) {
        String stripped = ChatColor.stripColor(rawValue == null ? "" : value(rawValue));
        return PINYIN_CACHE.computeIfAbsent(stripped, PlayerShopService::computePinyinTokens);
    }

    private static String[] computePinyinTokens(String stripped) {
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) continue;
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

    private void publishCrossServer(String message) {
        if (crossServerPublisher != null && message != null && !message.isBlank()) {
            crossServerPublisher.accept(message);
        }
    }

    // ─── 结果类 ─────────────────────────────────────────────

    public record ListingResult(boolean success, @Nullable String error, @Nullable PlayerShopListing listing) {
        public static ListingResult success(PlayerShopListing listing) { return new ListingResult(true, null, listing); }
        public static ListingResult fail(String error) { return new ListingResult(false, error, null); }
    }

    public record PurchaseResult(boolean success, @Nullable String error, @Nullable ItemStack item, double price, double tax, @Nullable String currency) {
        public static PurchaseResult success(ItemStack item, double price, double tax, String currency) { return new PurchaseResult(true, null, item, price, tax, currency); }
        public static PurchaseResult fail(String error) { return new PurchaseResult(false, error, null, 0, 0, null); }
    }
}
