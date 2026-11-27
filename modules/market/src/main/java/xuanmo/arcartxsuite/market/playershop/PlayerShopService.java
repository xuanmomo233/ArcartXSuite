package xuanmo.arcartxsuite.market.playershop;

import xuanmo.arcartxsuite.market.MarketWork;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;

import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.PlayerShopConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.PlayerShopTermination;
import xuanmo.arcartxsuite.market.storage.PlayerShopOrder;
import xuanmo.arcartxsuite.market.MarketDebits;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 玩家商店核心业务服务。
 * <p>
 * 与拍卖行不同，玩家商店仅支持固定价格直接购买，不支持竞价。
 * 复用 {@link AuctionItemSerializer}、{@link CurrencyBridgeAPI}、
 * {@link xuanmo.arcartxsuite.api.item.ItemRewardDispatcher} 等底层设施。
 */
public class PlayerShopService {
    private xuanmo.arcartxsuite.market.MarketLedger ledger;

    public void setLedger(xuanmo.arcartxsuite.market.MarketLedger ledger) { this.ledger = ledger; }


    private static final int EXPIRED_SCAN_LIMIT = 64;
    private static final int MAX_EXPIRATIONS_PER_TICK = 4;
    private static final int MAX_PENDING_PURCHASES = 32;
    private static final long EXPIRY_PREPARE_BUDGET_NANOS = 2_000_000L;
    private final JavaPlugin plugin;
    private final MarketWork work;
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
    private BukkitTask expiryPreparationTask;
    private volatile boolean stopped;
    private final Semaphore cancellationSlots = new Semaphore(32);
    private final Set<CompletableFuture<Boolean>> cancellationsAwaitingMain = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<PlayerShopListing> expiredListings = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scanningExpired = new AtomicBoolean();
    private final AtomicInteger expiredOutstanding = new AtomicInteger();
    private final AtomicInteger expiredBatchCommitted = new AtomicInteger();
    private volatile boolean fullExpiryBatch;
    private final Semaphore expiryWriteSlots = new Semaphore(4);
    private final Map<UUID, CompletableFuture<PurchaseResult>> pendingPurchases = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<PurchaseResult>> purchasesAwaitingMain = ConcurrentHashMap.newKeySet();
    private final MarketDebits debits;

    public PlayerShopService(MarketWork work, JavaPlugin plugin, PlayerShopConfiguration config, MessagesConfiguration messages,
                             MarketRepository repository, RedisMarketCache redisCache,
                             @Nullable Consumer<String> crossServerPublisher,
                             CurrencyBridgeAPI currencyManager,
                             AuctionItemSerializer itemSerializer, Logger logger,
                             xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
                             xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher,
                             ItemMatcherAPI itemMatcher, MarketDebits debits) {
        this.plugin = plugin;this.work = work;
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
        this.debits = debits;
    }

    public void start(long intervalTicks) {
        stopped = false;
        schedulerTask = work.repeatAsync(this::processExpired, intervalTicks, intervalTicks);
        expiryPreparationTask = work.repeat(this::prepareExpirations, 1L, 1L);
        logger.info("[PlayerShop] 玩家商店服务已启动，到期检查间隔: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        stopped = true;
        for (var future : List.copyOf(cancellationsAwaitingMain)) future.complete(false);
        for (var future : List.copyOf(purchasesAwaitingMain)) future.complete(PurchaseResult.fail("玩家商店已停用，本次未扣款"));
        expiredListings.clear();
        if (expiryPreparationTask != null) {
            expiryPreparationTask.cancel();
            expiryPreparationTask = null;
        }
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    // ─── 上架 ───────────────────────────────────────────────


    // ─── 购买 ───────────────────────────────────────────────

    public CompletableFuture<PurchaseResult> purchase(Player buyer, long listingId, @Nullable UUID expectedOwner,
                                                       CurrencyBridgeAPI payments, BooleanSupplier authorized) {
        UUID buyerId = buyer.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(buyerId);
        if (listingId <= 0 || !session.admitted() || stopped || work.isStopping() || !config.enabled()
                || pendingPurchases.size() >= MAX_PENDING_PURCHASES) {
            return CompletableFuture.completedFuture(PurchaseResult.fail("购买暂不可用，请稍后重试"));
        }
        var result = new CompletableFuture<PurchaseResult>();
        if (pendingPurchases.putIfAbsent(buyerId, result) != null) {
            return CompletableFuture.completedFuture(PurchaseResult.fail("已有购买正在处理，请稍后重试"));
        }
        purchasesAwaitingMain.add(result);
        result.whenComplete((reply, failure) -> {
            purchasesAwaitingMain.remove(result);
            pendingPurchases.remove(buyerId, result);
        });
        var request = new PurchaseRequest(buyer, session, expectedOwner, payments, authorized,
            System.nanoTime() + TimeUnit.SECONDS.toNanos(15), result);
        try {
            work.async(() -> {
                try {
                    var listing = repository.getPlayerShopListing(listingId);
                    if (listing == null || listing.getSeller() == null || !listing.isActive()
                            || (expectedOwner != null && !expectedOwner.equals(listing.getSeller()))
                            || buyerId.equals(listing.getSeller()) || !mayPay(request, listing)) {
                        result.complete(PurchaseResult.fail("该商品已不可购买，请刷新后重试"));
                        return;
                    }
                    work.main(() -> preparePurchasePayment(request, listing));
                } catch (RuntimeException failure) {
                    logger.log(Level.WARNING, "[PlayerShop] 读取待购商品失败 #" + listingId, failure);
                    result.complete(PurchaseResult.fail("无法读取商品，本次未扣款"));
                }
            });
        } catch (RuntimeException failure) {
            result.complete(PurchaseResult.fail("玩家商店正在停用，本次未扣款"));
        }
        return result.copy();
    }

    private void preparePurchasePayment(PurchaseRequest request, PlayerShopListing listing) {
        if (!purchasesAwaitingMain.remove(request.result())) return;
        try {
            if (!mayPay(request, listing) || !request.authorized().getAsBoolean()) {
                request.result().complete(PurchaseResult.fail("商品页面或玩家会话已失效，本次未扣款"));
                return;
            }
            if (!Double.isFinite(listing.getPrice()) || listing.getPrice() < 0) {
                request.result().complete(PurchaseResult.fail("商品价格异常，本次未扣款"));
                return;
            }
            ItemStack item = itemSerializer.deserialize(listing.getItemData());
            if (item == null || item.getType().isAir() || item.getAmount() < 1) {
                request.result().complete(PurchaseResult.fail("商品物品无效，本次未扣款"));
                return;
            }
            String material = item.getType().name();
            var bridge = request.payments().bridge(listing.getCurrency());
            if (bridge == null || !bridge.available()) {
                request.result().complete(PurchaseResult.fail("货币系统不可用"));
                return;
            }
            var price = BigDecimal.valueOf(listing.getPrice());
            if (price.signum() > 0 && bridge.debitRequiresReconciliation()) {
                request.result().complete(PurchaseResult.fail("该货币无法确认扣款"));
                return;
            }
            var backgroundDebit = price.signum() == 0
                ? (Supplier<CurrencyTransactionResult>) CurrencyTransactionResult::ok : bridge.prepareBackgroundDebit(request.buyer(), price);
            var seller = config.taxDiscount().isEmpty() ? null : Bukkit.getPlayer(listing.getSeller());
            boolean useOfflineTax = seller == null && !config.taxDiscount().isEmpty();
            double taxRate = seller == null ? config.transactionTaxRate() : getEffectiveTaxRateOnline(seller);
            work.async(() -> reservePurchase(request, listing, material, price, taxRate, useOfflineTax,
                backgroundDebit, () -> bridge.withdraw(request.buyer(), price)));
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "[PlayerShop] 购买准备失败 #" + listing.getId(), failure);
            request.result().complete(PurchaseResult.fail("无法准备购买，本次未扣款"));
        }
    }

    private void reservePurchase(PurchaseRequest request, PlayerShopListing listing, String material, BigDecimal price,
                                 double taxRate, boolean useOfflineTax, @Nullable Supplier<CurrencyTransactionResult> background,
                                 Supplier<CurrencyTransactionResult> mainDebit) {
        try {
            if (!mayPay(request, listing)) {
                request.result().complete(PurchaseResult.fail("页面或商品已失效，本次未扣款"));
                return;
            }
            double rate = useOfflineTax ? getEffectiveTaxRateOffline(listing.getSeller()) : taxRate;
            if (!Double.isFinite(rate) || rate < 0 || rate > 1) {
                request.result().complete(PurchaseResult.fail("税率配置异常，本次未扣款"));
                return;
            }
            var plan = buildPurchasePlan(request, listing, material, price, price.multiply(BigDecimal.valueOf(rate)));
            if (!mayPay(request, listing) || !repository.reservePlayerShopPurchase(plan)) {
                request.result().complete(PurchaseResult.fail("商品已被购买、预留或下架，本次未扣款"));
                return;
            }
            debits.enqueue(new MarketDebits.Job(plan.order().id(),
                () -> mayPay(request, listing) && request.authorized().getAsBoolean(), () -> mayPay(request, listing),
                background, mainDebit, payment -> finishPurchase(request, plan, payment)));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[PlayerShop] 商品预留未确认 #" + listing.getId(), failure);
            request.result().complete(PurchaseResult.fail("商品预留未确认，货币接口未调用"));
        }
    }

    private PlayerShopOrder buildPurchasePlan(PurchaseRequest request, PlayerShopListing listing,
                                               String material, BigDecimal price, BigDecimal tax) {
        String trade = UUID.randomUUID().toString();
        String owner = ledger.owner();
        long now = System.currentTimeMillis();
        UUID buyer = request.session().playerId();
        String currency = listing.getCurrency();
        String label = operationLabel(listing);
        var deliveries = new java.util.ArrayList<MarketOperation>();
        deliveries.add(new MarketOperation(trade + ":item", trade, buyer, "ITEM", "pshop_purchase_item:" + listing.getId(),
            "PREPARED", "", "0", listing.getItemData(), "等待扣款确认", owner, now, now, label, material));
        BigDecimal income = price.subtract(tax);
        if (income.signum() > 0) {
            deliveries.add(new MarketOperation(trade + ":income", trade, listing.getSeller(), "CURRENCY", "pshop_sold_income",
                "PREPARED", currency, income.toPlainString(), "", "等待扣款确认", owner, now, now, label, material));
        }
        String notification = messages.playerShopSold().replace("{item}", listing.getItemDisplayName())
            .replace("{amount}", currencyManager.format(currency, income));
        deliveries.add(new MarketOperation(trade + ":notice", trade, listing.getSeller(), "NOTICE", "notice-chat:pshop-sold:" + listing.getId(),
            "PREPARED", "", "0", notification, "等待扣款确认", owner, now, now, label, material));
        var order = new MarketOperation(trade, trade, buyer, "PSHOP_BUY", PlayerShopOrder.reference(listing.getId(), tax, deliveries.size()),
            "RUNNING", currency, price.toPlainString(), listing.getItemData(), "商品预留，等待扣款", owner, now, now, label, material);
        var debit = new MarketOperation(trade + ":debit", trade, buyer, "DEBIT", "currency", "RUNNING", currency,
            price.toPlainString(), "", "调用货币接口前记录", owner, now, now, label, material);
        return new PlayerShopOrder(listing, order, debit, tax, deliveries, request.expectedOwner() != null);
    }


    private boolean mayPay(PurchaseRequest request, PlayerShopListing listing) {
        return listing.isActive() && validDebitSession(request.session(), request.validUntil());
    }

    private boolean validDebitSession(AuthSnapshot session, long validUntil) {
        if (stopped || work.isStopping() || validUntil - System.nanoTime() <= 0) return false;
        var current = AuthAccess.snapshot(session.playerId());
        return current.admitted() && current.generation() == session.generation()
            && java.util.Objects.equals(current.connectionId(), session.connectionId());
    }


    private void finishPurchase(PurchaseRequest request, PlayerShopOrder plan, CurrencyTransactionResult payment) {
        String id = plan.order().id();
        try {
            repository.finishPlayerShopPurchase(plan, payment);
        } catch (RuntimeException failure) {
            try { repository.finishPlayerShopPurchase(plan, CurrencyTransactionResult.unknown("")); }
            catch (RuntimeException unresolved) { failure.addSuppressed(unresolved); }
            logger.log(Level.WARNING, "[PlayerShop] 成交提交结果未确认 " + id, failure);
            request.result().complete(PurchaseResult.fail("成交结果未确认，请核对订单 " + id));
            return;
        }
        if (payment.uncertain()) {
            request.result().complete(PurchaseResult.fail("扣款结果不明，商品已预留待核对。订单 " + id));
        } else if (payment.success()) {
            refreshPlayerShopPublication("PSHOP_LISTING_SOLD:" + plan.listing().getId());
            request.result().complete(PurchaseResult.success(plan.listing().getPrice(), plan.tax().doubleValue(), plan.listing().getCurrency()));
        } else {
            request.result().complete(PurchaseResult.fail(payment.message() == null || payment.message().isBlank()
                ? messages.insufficientFunds() : payment.message()));
        }
    }

    private record PurchaseRequest(Player buyer, AuthSnapshot session, UUID expectedOwner, CurrencyBridgeAPI payments,
                                   BooleanSupplier authorized, long validUntil, CompletableFuture<PurchaseResult> result) {}

    // ─── 下架 ───────────────────────────────────────────────

    public CompletableFuture<Boolean> cancelListing(UUID actor, long listingId, boolean forced, BooleanSupplier authorized) {
        if (listingId <= 0 || stopped || work.isStopping() || !cancellationSlots.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        cancellationsAwaitingMain.add(result);
        result.whenComplete((success, failure) -> {
            cancellationsAwaitingMain.remove(result);
            cancellationSlots.release();
        });
        try {
            work.async(() -> {
                try {
                    var listing = repository.getPlayerShopListing(listingId);
                    if (listing == null || listing.getSeller() == null || listing.getStatus() != PlayerShopListing.ListingStatus.ACTIVE
                            || (!forced && !actor.equals(listing.getSeller())) || stopped || work.isStopping() || !plugin.isEnabled()) {
                        result.complete(false);
                        return;
                    }
                    work.main(() -> startCancellation(actor, forced, authorized, listing, result));
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result.copy();
    }

    private void startCancellation(UUID actor, boolean forced, BooleanSupplier authorized, PlayerShopListing listing,
                                   CompletableFuture<Boolean> result) {
        if (!cancellationsAwaitingMain.remove(result)) return;
        try {
            if (stopped || work.isStopping() || !authorized.getAsBoolean()) {
                result.complete(false);
                return;
            }
            String material = listingMaterial(listing);
            work.async(() -> {
                try {
                    result.complete(terminateListing(listing, actor, forced, false, material));
                } catch (RuntimeException failure) {
                    logger.log(Level.WARNING, "[PlayerShop] 下架提交未确认 #" + listing.getId(), failure);
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            result.completeExceptionally(failure);
        }
    }

    /** Scans one page off-thread. The server thread prepares at most a few snapshots per tick. */
    public int triggerExpiredProcessing() {
        if (stopped || !config.enabled() || expiredOutstanding.get() != 0 || !scanningExpired.compareAndSet(false, true)) return 0;
        try {
            var listings = repository.getExpiredPlayerShopListings(EXPIRED_SCAN_LIMIT);
            if (stopped || listings.isEmpty()) return 0;
            fullExpiryBatch = listings.size() == EXPIRED_SCAN_LIMIT;
            expiredBatchCommitted.set(0);
            expiredOutstanding.set(listings.size());
            expiredListings.addAll(listings);
            if (stopped) expiredListings.clear();
            return listings.size();
        } finally {
            scanningExpired.set(false);
        }
    }

    private void processExpired() {
        try {
            triggerExpiredProcessing();
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[PlayerShop] 过期扫描失败", failure);
        }
    }

    private void prepareExpirations() {
        long start = System.nanoTime();
        for (int i = 0; i < MAX_EXPIRATIONS_PER_TICK && !stopped; i++) {
            if (!expiryWriteSlots.tryAcquire()) return;
            var listing = expiredListings.poll();
            if (listing == null) {
                expiryWriteSlots.release();
                return;
            }
            try {
                String material = listingMaterial(listing);
                work.async(() -> {
                    try {
                        if (terminateListing(listing, listing.getSeller(), false, true, material)) {
                            expiredBatchCommitted.incrementAndGet();
                        }
                    } catch (RuntimeException failure) {
                        logger.log(Level.WARNING, "[PlayerShop] 到期返还提交失败 #" + listing.getId(), failure);
                    } finally {
                        finishExpiryAttempt();
                    }
                });
            } catch (RuntimeException | LinkageError failure) {
                finishExpiryAttempt();
                logger.log(Level.WARNING, "[PlayerShop] 到期返还准备失败 #" + listing.getId(), failure);
            }
            if (System.nanoTime() - start >= EXPIRY_PREPARE_BUDGET_NANOS) return;
        }
    }

    private void finishExpiryAttempt() {
        expiryWriteSlots.release();
        boolean fetchNext = fullExpiryBatch && expiredBatchCommitted.get() > 0;
        if (expiredOutstanding.decrementAndGet() == 0 && fetchNext && !stopped && !work.isStopping()) {
            work.async(this::processExpired);
        }
    }

    private boolean terminateListing(PlayerShopListing listing, UUID actor, boolean forced, boolean expired, String material) {
        UUID owner = listing.getSeller();
        if (owner == null) return false;
        long id = listing.getId();
        long now = System.currentTimeMillis();
        String trade = "pshop-end:" + id;
        String kind = expired ? "PSHOP_EXPIRE" : forced ? "PSHOP_ADMIN_CANCEL" : "PSHOP_CANCEL";
        String reason = expired ? "pshop_expired_return" : forced ? "pshop_admin_cancel_return" : "pshop_cancel_return";
        String label = operationLabel(listing);
        var receipt = new MarketOperation(trade, trade, actor, kind, "listing=" + id,
            "DONE", "", "0", listing.getItemData(), "商品状态已提交，物品等待返还", "", now, now, label, material);
        var history = new MarketRepository.PlayerShopHistory(0, id, owner, null, listing.getItemData(),
            listing.getItemDisplayName(), 0, listing.getCurrency(), 0, expired ? "EXPIRED" : "CANCELLED", now);
        var item = new MarketOperation(trade + ":item", trade, owner, "ITEM", reason + ":" + id, "READY", "", "0",
            listing.getItemData(), "等待返还", "", now, now, label, material);
        List<MarketOperation> deliveries = List.of(item);
        if (expired) {
            String message = messages.playerShopExpired().replace("{item}", listing.getItemDisplayName());
            var notice = new MarketOperation(trade + ":notice", trade, owner, "NOTICE", "notice-chat:pshop-expired:" + id,
                "READY", "", "0", message, "等待通知", "", now, now, label, material);
            deliveries = List.of(item, notice);
        }
        if (!repository.terminatePlayerShopListing(new PlayerShopTermination(listing, expired, receipt, history, deliveries))) {
            return false;
        }
        refreshPlayerShopPublication((expired ? "PSHOP_LISTING_EXPIRED:" : "PSHOP_LISTING_CANCELLED:") + id);
        return true;
    }

    private static String operationLabel(PlayerShopListing listing) {
        String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', listing.getItemDisplayName()));
        return name.substring(0, Math.min(255, name.length()));
    }

    private void refreshPlayerShopPublication(String message) {
        try {
            if (redisCache != null && redisCache.isAvailable()) redisCache.invalidateByPrefix("market:pshop:");
            publishCrossServer(message);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[PlayerShop] 商品状态已提交，列表刷新失败 " + message, failure);
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

    public List<PlayerShopListing> searchListingsBySeller(UUID seller, String keyword, int page, int pageSize) {
        return repository.searchPlayerShopListingsBySeller(seller, keyword, page * pageSize, pageSize);
    }

    public int countSearchBySeller(UUID seller, String keyword) {
        return repository.countSearchPlayerShopListingsBySeller(seller, keyword);
    }

    // ─── 工具方法（复用拍卖行模式）───────────────────────────

    private String listingMaterial(PlayerShopListing listing) {
        String material="";
        try{ItemStack item=itemSerializer.deserialize(listing.getItemData());if(item!=null)material=item.getType().name();}
        catch(RuntimeException ignored){}
        return material;
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
                ? cached : userManager.loadUser(seller).get(3, TimeUnit.SECONDS);
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
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return config.transactionTaxRate();
        } catch (Exception | LinkageError e) {
            logger.fine("LuckPerms 不可用，离线卖家使用默认税率 seller=" + seller + ": " + e.getMessage());
            return config.transactionTaxRate();
        }
    }

    public boolean isBlacklisted(ItemStack item) {
        if (item == null) return true;
        if (itemMatcher == null) return false;
        return itemMatcher.matches(config.blacklist(), item);
    }

    public String getItemDisplayName(ItemStack item) {
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(item);
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }


    private void publishCrossServer(String message) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (crossServerPublisher != null && message != null && !message.isBlank()) {
            crossServerPublisher.accept(message);
        }
    }

    // ─── 结果类 ─────────────────────────────────────────────


    public record PurchaseResult(boolean success, @Nullable String error, double price, double tax, @Nullable String currency) {
        public static PurchaseResult success(double price, double tax, String currency) { return new PurchaseResult(true, null, price, tax, currency); }
        public static PurchaseResult fail(String error) { return new PurchaseResult(false, error, 0, 0, null); }
    }
}
